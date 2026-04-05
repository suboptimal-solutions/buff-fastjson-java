package io.suboptimal.buffjson.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;
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
 * <h3>Limitation: {@code writeRawValue}</h3>
 *
 * <p>
 * Because the serialized JSON is written as a raw string,
 * {@code ObjectMapper.valueToTree(message)} does not produce a structured
 * {@link com.fasterxml.jackson.databind.JsonNode} tree for proto messages. Use
 * {@code writeValueAsString()} followed by {@code readTree()} as a workaround.
 *
 * @see ProtobufJacksonModule.ProtobufSerializers
 */
final class ProtobufMessageSerializer extends JsonSerializer<Message> {

	/**
	 * BuffJsonEncoder instance, potentially configured with a TypeRegistry for Any.
	 */
	private final BuffJsonEncoder encoder;

	ProtobufMessageSerializer(BuffJsonEncoder encoder) {
		this.encoder = encoder;
	}

	/**
	 * Serializes a protobuf message by encoding it to proto3 JSON via
	 * {@link BuffJsonEncoder#encode} and writing the result into the generator
	 * output.
	 *
	 * <p>
	 * When the generator is a {@link TokenBuffer} (used internally by
	 * {@code ObjectMapper.convertValue()} and {@code valueToTree()}),
	 * {@code writeRawValue()} would store the JSON as a single text token instead
	 * of structured tokens, corrupting the output. In that case, the JSON string is
	 * re-parsed and copied token-by-token via
	 * {@link JsonGenerator#copyCurrentStructure(JsonParser)}.
	 *
	 * @param message
	 *            the protobuf message to serialize
	 * @param gen
	 *            the Jackson JSON generator (may be a {@link TokenBuffer})
	 * @param serializers
	 *            the serializer provider (unused — all work is delegated to
	 *            buff-json)
	 */
	@Override
	public void serialize(Message message, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		String json = encoder.encode(message);

		if (gen instanceof TokenBuffer) {
			// TokenBuffer cannot store raw values as structured tokens.
			// Parse the JSON and copy the structure token-by-token.
			try (JsonParser parser = gen.getCodec().getFactory().createParser(json)) {
				parser.nextToken();
				gen.copyCurrentStructure(parser);
			}
		} else {
			gen.writeRawValue(json);
		}
	}

	@Override
	public Class<Message> handledType() {
		return Message.class;
	}
}
