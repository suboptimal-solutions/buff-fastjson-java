package io.suboptimal.buffjson.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.protobuf.Message;

import io.suboptimal.buffjson.BuffJsonEncoder;

/**
 * Jackson serializer for protobuf {@link Message} types.
 *
 * <p>
 * Delegates to
 * {@link BuffJsonEncoder#encode(com.google.protobuf.MessageOrBuilder)} to
 * produce the proto3 JSON string, then injects it into Jackson's output via
 * {@link JsonGenerator#writeRawValue(String)}. This avoids re-implementing
 * proto3 JSON formatting — the output is identical to
 * {@code BuffJson.encode()}.
 *
 * <p>
 * A single instance handles all {@code Message} subtypes because
 * {@link BuffJsonEncoder#encode} is type-agnostic (it inspects the message's
 * {@link com.google.protobuf.Descriptors.Descriptor} at runtime).
 *
 * @see BuffJsonJacksonModule.ProtobufSerializers
 */
final class ProtobufMessageSerializer extends JsonSerializer<Message> {

	/**
	 * BuffJsonEncoder instance, potentially configured with a TypeRegistry for Any.
	 */
	private final BuffJsonEncoder encoder;

	ProtobufMessageSerializer(BuffJsonEncoder encoder) {
		this.encoder = encoder;
	}

	@Override
	public void serialize(Message message, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		gen.writeRawValue(encoder.encode(message));
	}

	@Override
	public Class<Message> handledType() {
		return Message.class;
	}
}
