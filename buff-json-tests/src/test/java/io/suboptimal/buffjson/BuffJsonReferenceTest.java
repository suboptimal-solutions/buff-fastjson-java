package io.suboptimal.buffjson;

import static org.junit.jupiter.api.Assertions.*;

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
	 * Same DynamicMessage check on the UTF-8 path (encodeToBytes), guarding the
	 * MessageSchema.FieldInfo.nameWithColonUtf8 dispatch.
	 */
	@Test
	void encodeDynamicMessageUtf8() throws Exception {
		TestAllScalars source = TestAllScalars.newBuilder().setOptionalString("dynamic").setOptionalInt32(7).build();

		DynamicMessage dynamic = DynamicMessage.parseFrom(TestAllScalars.getDescriptor(), source.toByteArray());

		assertEquals(REFERENCE.print(source), new String(ENCODER.encodeToBytes(dynamic)));
	}
}
