package io.suboptimal.buffjson;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;

import com.google.protobuf.ByteString;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import org.junit.jupiter.api.Test;

import io.suboptimal.buffjson.proto.*;

/**
 * Memory-leak guards. Two flavors:
 *
 * <ul>
 * <li><b>Reachability tests</b> — use {@link WeakReference} to verify that
 * encoder/decoder don't retain references after a call returns. These are
 * deterministic given enough GC cycles and reliable as long as the test JVM
 * isn't exotic.
 * <li><b>Steady-state heap tests</b> — run a tight loop and assert heap delta
 * stays under a generous threshold. These catch per-call allocation
 * accumulation (e.g., a forgotten JSONWriter close, a growing internal
 * collection). Inherently noisier; thresholds intentionally loose.
 * </ul>
 */
class BuffJsonMemoryTest {

	private static final int WARMUP = 20_000;
	private static final int LOOP_ITERATIONS = 200_000;

	// Loose, intentional. JIT and minor GC can shift things by a few MB.
	private static final long HEAP_GROWTH_BUDGET_BYTES = 10L * 1024 * 1024;

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

	// ---------- Reachability tests ----------

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

		// The byte[] result must NOT keep the encoder alive
		assertReclaimed(encRef, "Encoded byte[] keeps the encoder alive");
		assertNotNull(bytes);
	}

	// ---------- Steady-state heap tests ----------

	@Test
	void codegenSteadyStateHeapBounded() throws Exception {
		assertSteadyState(BuffJson.encoder(), sampleMessage(), "codegen");
	}

	@Test
	void typedAccessorSteadyStateHeapBounded() throws Exception {
		assertSteadyState(BuffJson.encoder().setGeneratedEncoders(false), sampleMessage(), "typed");
	}

	@Test
	void reflectionSteadyStateHeapBounded() throws Exception {
		assertSteadyState(BuffJson.encoder().setGeneratedEncoders(false).setTypedAccessors(false), sampleMessage(),
				"reflection");
	}

	@Test
	void utf8SteadyStateHeapBounded() throws Exception {
		var encoder = BuffJson.encoder();
		var message = sampleMessage();

		for (int i = 0; i < WARMUP; i++)
			encoder.encodeToBytes(message);

		long before = usedHeapAfterGc();
		for (int i = 0; i < LOOP_ITERATIONS; i++)
			encoder.encodeToBytes(message);
		long after = usedHeapAfterGc();

		long delta = after - before;
		assertTrue(delta <= HEAP_GROWTH_BUDGET_BYTES, "UTF-8 path heap grew by " + delta + " bytes over "
				+ LOOP_ITERATIONS + " iterations (budget: " + HEAP_GROWTH_BUDGET_BYTES + ")");
	}

	@Test
	void nestedSteadyStateHeapBounded() throws Exception {
		assertSteadyState(BuffJson.encoder(), sampleNested(), "nested-codegen");
	}

	private static void assertSteadyState(BuffJsonEncoder encoder, Message message, String label) throws Exception {
		for (int i = 0; i < WARMUP; i++)
			encoder.encode(message);

		long before = usedHeapAfterGc();
		for (int i = 0; i < LOOP_ITERATIONS; i++)
			encoder.encode(message);
		long after = usedHeapAfterGc();

		long delta = after - before;
		assertTrue(delta <= HEAP_GROWTH_BUDGET_BYTES, label + " heap grew by " + delta + " bytes over "
				+ LOOP_ITERATIONS + " iterations (budget: " + HEAP_GROWTH_BUDGET_BYTES + ")");
	}

	// ---------- Helpers ----------

	private static void assertReclaimed(WeakReference<?> ref, String message) throws InterruptedException {
		// System.gc() is a hint, not a guarantee. Loop with backoff to give the GC
		// time to run. The reference object is small, so mark-sweep should reach
		// it quickly once the strong reference is dropped.
		for (int i = 0; i < 10; i++) {
			System.gc();
			if (ref.get() == null)
				return;
			Thread.sleep(50);
		}
		fail(message);
	}

	private static long usedHeapAfterGc() throws InterruptedException {
		for (int i = 0; i < 3; i++) {
			System.gc();
			Thread.sleep(50);
		}
		return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
	}
}
