package io.suboptimal.buffjson;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.google.protobuf.ByteString;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

import org.junit.jupiter.api.Test;

import io.suboptimal.buffjson.proto.*;

class BuffJsonReferenceTest {

	private static final JsonFormat.Printer REFERENCE = JsonFormat.printer().omittingInsignificantWhitespace();
	private static final BuffJsonEncoder ENCODER = BuffJson.encoder();

	@Test
	void encodeScalarMessage() throws Exception {
		TestAllScalars message = TestAllScalars.newBuilder().setOptionalString("test").setOptionalInt32(42)
				.setOptionalInt64(1234567890L).setOptionalDouble(3.14).setOptionalBool(true).build();

		assertEquals(REFERENCE.print(message), ENCODER.encode(message));
	}

	@Test
	void encodeDefaultMessage() throws Exception {
		assertEquals("{}", ENCODER.encode(TestAllScalars.getDefaultInstance()));
	}

	@Test
	void encodeComplexMessage() throws Exception {
		NestedMessage nested = NestedMessage.newBuilder().setValue(1).setName("nested").build();

		TestNesting message = TestNesting.newBuilder().setNested(nested).addRepeatedNested(nested)
				.setEnumValue(TestEnum.TEST_ENUM_FOO).addRepeatedEnum(TestEnum.TEST_ENUM_BAR).build();

		assertEquals(REFERENCE.print(message), ENCODER.encode(message));
	}

	/**
	 * DynamicMessage cannot use codegen or LambdaMetafactory-bound typed accessors
	 * (no compiled getter methods), so it always exercises the pure-reflection
	 * MessageSchema + getField path. This test guards that fallback.
	 */
	@Test
	void encodeDynamicMessage() throws Exception {
		TestAllScalars source = TestAllScalars.newBuilder().setOptionalString("dynamic").setOptionalInt32(7)
				.setOptionalInt64(42L).setOptionalDouble(1.5).setOptionalBool(true).build();

		DynamicMessage dynamic = DynamicMessage.parseFrom(TestAllScalars.getDescriptor(), source.toByteArray());

		assertEquals(REFERENCE.print(source), ENCODER.encode(dynamic));
	}

	/**
	 * DynamicMessage with nested messages, enums, and repeated fields on the UTF-16
	 * path — exercises recursive writeMessage dispatch for DynamicMessage.
	 */
	@Test
	void encodeDynamicMessageNesting() throws Exception {
		TestNesting source = TestNesting.newBuilder()
				.setNested(NestedMessage.newBuilder().setValue(42).setName("nested"))
				.addRepeatedNested(NestedMessage.newBuilder().setValue(1).setName("a"))
				.addRepeatedNested(NestedMessage.newBuilder().setValue(2).setName("b"))
				.setEnumValue(TestEnum.TEST_ENUM_FOO).addRepeatedEnum(TestEnum.TEST_ENUM_BAR)
				.addRepeatedEnum(TestEnum.TEST_ENUM_BAZ).build();

		DynamicMessage dynamic = DynamicMessage.parseFrom(TestNesting.getDescriptor(), source.toByteArray());

		assertEquals(REFERENCE.print(source), ENCODER.encode(dynamic));
	}

	/**
	 * DynamicMessage with map fields on the UTF-16 path — guards that
	 * FieldWriter.writeMap handles DynamicMessage map entries (not MapEntry).
	 */
	@Test
	void encodeDynamicMessageMaps() throws Exception {
		TestMaps source = TestMaps.newBuilder().putStringToString("key", "value").putStringToInt32("count", 42)
				.putStringToMessage("msg", NestedMessage.newBuilder().setValue(1).build())
				.putStringToEnum("status", TestEnum.TEST_ENUM_FOO).putInt32ToString(1, "one")
				.putBoolToString(true, "yes").build();

		DynamicMessage dynamic = DynamicMessage.parseFrom(TestMaps.getDescriptor(), source.toByteArray());

		assertEquals(REFERENCE.print(source), ENCODER.encode(dynamic));
	}

	/**
	 * Same DynamicMessage check on the UTF-8 path (encodeToBytes), guarding the
	 * MessageSchema.FieldInfo.nameWithColonUtf8 dispatch.
	 */
	@Test
	void encodeDynamicMessageUtf8() throws Exception {
		TestAllScalars source = TestAllScalars.newBuilder().setOptionalString("dynamic").setOptionalInt32(7).build();

		DynamicMessage dynamic = DynamicMessage.parseFrom(TestAllScalars.getDescriptor(), source.toByteArray());

		assertEquals(REFERENCE.print(source), new String(ENCODER.encodeToBytes(dynamic)));
	}

	/**
	 * DynamicMessage with all scalar types on the UTF-8 path — covers every
	 * FieldWriter.writeValue branch via pure-reflection.
	 */
	@Test
	void encodeDynamicMessageAllScalarsUtf8() throws Exception {
		TestAllScalars source = TestAllScalars.newBuilder().setOptionalInt32(42).setOptionalInt64(123456789012345L)
				.setOptionalUint32(100).setOptionalUint64(999999999999L).setOptionalSint32(-42)
				.setOptionalSint64(-123456789012345L).setOptionalFixed32(100).setOptionalFixed64(999999999999L)
				.setOptionalSfixed32(-100).setOptionalSfixed64(-999999999999L).setOptionalFloat(3.14f)
				.setOptionalDouble(2.718281828).setOptionalBool(true).setOptionalString("hello world")
				.setOptionalBytes(ByteString.copyFromUtf8("binary data")).build();

		DynamicMessage dynamic = DynamicMessage.parseFrom(TestAllScalars.getDescriptor(), source.toByteArray());

		assertEquals(REFERENCE.print(source), new String(ENCODER.encodeToBytes(dynamic), StandardCharsets.UTF_8));
	}

	/**
	 * DynamicMessage with nested messages, enums, and repeated fields on the UTF-8
	 * path — exercises recursive writeMessage dispatch and repeated field writing
	 * in the pure-reflection tier.
	 */
	@Test
	void encodeDynamicMessageNestingUtf8() throws Exception {
		TestNesting source = TestNesting.newBuilder()
				.setNested(NestedMessage.newBuilder().setValue(42).setName("nested"))
				.addRepeatedNested(NestedMessage.newBuilder().setValue(1).setName("a"))
				.addRepeatedNested(NestedMessage.newBuilder().setValue(2).setName("b"))
				.setEnumValue(TestEnum.TEST_ENUM_FOO).addRepeatedEnum(TestEnum.TEST_ENUM_BAR)
				.addRepeatedEnum(TestEnum.TEST_ENUM_BAZ).build();

		DynamicMessage dynamic = DynamicMessage.parseFrom(TestNesting.getDescriptor(), source.toByteArray());

		assertEquals(REFERENCE.print(source), new String(ENCODER.encodeToBytes(dynamic), StandardCharsets.UTF_8));
	}

	/**
	 * DynamicMessage with map fields on the UTF-8 path — exercises
	 * FieldWriter.writeMap with various key and value types via pure-reflection.
	 */
	@Test
	void encodeDynamicMessageMapsUtf8() throws Exception {
		TestMaps source = TestMaps.newBuilder().putStringToString("key", "value").putStringToInt32("count", 42)
				.putStringToMessage("msg", NestedMessage.newBuilder().setValue(1).build())
				.putStringToEnum("status", TestEnum.TEST_ENUM_FOO).putInt32ToString(1, "one")
				.putBoolToString(true, "yes").build();

		DynamicMessage dynamic = DynamicMessage.parseFrom(TestMaps.getDescriptor(), source.toByteArray());

		assertEquals(REFERENCE.print(source), new String(ENCODER.encodeToBytes(dynamic), StandardCharsets.UTF_8));
	}

	/**
	 * DynamicMessage on the OutputStream path — verifies encode(message,
	 * OutputStream) works for DynamicMessage, which was the original crash scenario
	 * (writeNameRaw(char[]) on JSONWriterUTF8).
	 */
	@Test
	void encodeDynamicMessageOutputStream() throws Exception {
		TestNesting source = TestNesting.newBuilder()
				.setNested(NestedMessage.newBuilder().setValue(42).setName("stream"))
				.addRepeatedNested(NestedMessage.newBuilder().setValue(1).setName("a"))
				.setEnumValue(TestEnum.TEST_ENUM_BAR).build();

		DynamicMessage dynamic = DynamicMessage.parseFrom(TestNesting.getDescriptor(), source.toByteArray());

		var out = new ByteArrayOutputStream();
		ENCODER.encode(dynamic, out);
		assertEquals(REFERENCE.print(source), out.toString(StandardCharsets.UTF_8));
	}
}
