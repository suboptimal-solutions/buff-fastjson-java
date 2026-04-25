package io.suboptimal.buffjson;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;

import com.google.protobuf.ByteString;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import org.junit.jupiter.api.Test;

import io.suboptimal.buffjson.proto.*;

/**
 * Reachability tests guarding against the encoder retaining references to
 * encoded {@link Message} instances or to itself once results have been handed
 * back to the caller. Uses {@link WeakReference} + {@link System#gc()}, which
 * is deterministic enough for these checks given a few retry cycles.
 *
 * <p>
 * For per-call <em>allocation rate</em> regression detection (a much stronger
 * signal than a single-loop heap delta), see {@code allocation-check.sh} which
 * runs JMH with {@code -prof gc} on a representative subset of benchmarks and
 * asserts {@code gc.alloc.rate.norm} budgets. That script runs in CI as a
 * separate job.
 */
class BuffJsonMemoryTest {

	private static TestAllScalars sampleMessage() {
		return TestAllScalars.newBuilder().setOptionalInt32(42).setOptionalInt64(123456789012345L).setOptionalUint32(7)
				.setOptionalUint64(99999999L).setOptionalFloat(3.14f).setOptionalDouble(2.718281828)
				.setOptionalBool(true).setOptionalString("memory-leak-test")
				.setOptionalBytes(ByteString.copyFromUtf8("payload")).build();
	}

	private static TestNesting sampleNested() {
		var nested = NestedMessage.newBuilder().setValue(1).setName("inner").build();
		return TestNesting.newBuilder().setNested(nested).addRepeatedNested(nested).addRepeatedNested(nested)
				.setEnumValue(TestEnum.TEST_ENUM_FOO).addRepeatedEnum(TestEnum.TEST_ENUM_BAR).build();
	}

	@Test
	void encoderDoesNotRetainMessageReference() throws Exception {
		var encoder = BuffJson.encoder();
		Message message = sampleMessage();
		WeakReference<Message> ref = new WeakReference<>(message);

		encoder.encode(message);
		message = null;

		assertReclaimed(ref, "BuffJsonEncoder retains a reference to the encoded Message");
	}

	@Test
	void encoderDoesNotRetainMessageReferenceOnUtf8Path() throws Exception {
		var encoder = BuffJson.encoder();
		Message message = sampleMessage();
		WeakReference<Message> ref = new WeakReference<>(message);

		encoder.encodeToBytes(message);
		message = null;

		assertReclaimed(ref, "encodeToBytes retains a reference to the encoded Message");
	}

	@Test
	void encoderDoesNotRetainMessageReferenceOnStreamPath() throws Exception {
		var encoder = BuffJson.encoder();
		Message message = sampleMessage();
		WeakReference<Message> ref = new WeakReference<>(message);

		var out = new ByteArrayOutputStream();
		encoder.encode(message, out);
		message = null;

		assertReclaimed(ref, "encode(Message, OutputStream) retains a reference to the encoded Message");
	}

	@Test
	void encoderDoesNotRetainNestedMessageReferences() throws Exception {
		var encoder = BuffJson.encoder();
		Message message = sampleNested();
		WeakReference<Message> ref = new WeakReference<>(message);

		encoder.encode(message);
		message = null;

		assertReclaimed(ref, "Nested-message encoding retains references");
	}

	@Test
	void runtimeReflectionPathDoesNotRetainMessage() throws Exception {
		// Forces the pure-reflection MessageSchema + getField path
		var encoder = BuffJson.encoder().setGeneratedEncoders(false).setTypedAccessors(false);
		Message message = sampleMessage();
		WeakReference<Message> ref = new WeakReference<>(message);

		encoder.encode(message);
		message = null;

		assertReclaimed(ref, "Reflection path retains a reference to the encoded Message");
	}

	@Test
	void typedAccessorPathDoesNotRetainMessage() throws Exception {
		// Forces the LambdaMetafactory typed-accessor path
		var encoder = BuffJson.encoder().setGeneratedEncoders(false);
		Message message = sampleMessage();
		WeakReference<Message> ref = new WeakReference<>(message);

		encoder.encode(message);
		message = null;

		assertReclaimed(ref, "Typed-accessor path retains a reference to the encoded Message");
	}

	@Test
	void dynamicMessagePathDoesNotRetainMessage() throws Exception {
		var encoder = BuffJson.encoder();
		Message dynamic = DynamicMessage.parseFrom(TestAllScalars.getDescriptor(), sampleMessage().toByteArray());
		WeakReference<Message> ref = new WeakReference<>(dynamic);

		encoder.encode(dynamic);
		dynamic = null;

		assertReclaimed(ref, "DynamicMessage path retains a reference");
	}

	@Test
	void encoderItselfIsCollectableAfterUse() throws Exception {
		var encoder = BuffJson.encoder();
		var message = sampleMessage();
		encoder.encode(message);
		byte[] bytes = encoder.encodeToBytes(message);

		WeakReference<BuffJsonEncoder> encRef = new WeakReference<>(encoder);
		encoder = null;

		assertReclaimed(encRef, "Encoded byte[] keeps the encoder alive");
		assertNotNull(bytes);
	}

	private static void assertReclaimed(WeakReference<?> ref, String message) throws InterruptedException {
		// System.gc() is a hint; loop with backoff to give the GC time to run.
		for (int i = 0; i < 10; i++) {
			System.gc();
			if (ref.get() == null)
				return;
			Thread.sleep(50);
		}
		fail(message);
	}
}
