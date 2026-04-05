package io.suboptimal.buffjson.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.google.protobuf.Message;

import io.suboptimal.buffjson.BuffJsonDecoder;

/**
 * Jackson deserializer for protobuf {@link Message} types.
 *
 * <p>
 * Extracts the raw JSON slice from Jackson's parser and delegates to
 * {@link BuffJsonDecoder} for actual proto3 JSON parsing — no intermediate
 * String or JsonNode allocation. Jackson only identifies the object boundaries
 * via {@link JsonParser#skipChildren()}, and buff-json's decoder handles
 * parsing in a single pass.
 *
 * <p>
 * <b>Required:</b> The owning {@code ObjectMapper} must be created with
 * {@link StreamReadFeature#INCLUDE_SOURCE_IN_LOCATION} enabled. This allows the
 * deserializer to access the raw input (String or byte[]) and pass
 * offset/length directly to FastJson2 without allocating intermediate objects.
 *
 * <pre>{@code
 * ObjectMapper mapper = JsonMapper.builder().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
 * 		.addModule(new BuffJsonJacksonModule()).build();
 * }</pre>
 *
 * <p>
 * A separate instance is created per target {@code Message} class (by
 * {@link BuffJsonJacksonModule.ProtobufDeserializers}) because the deserializer
 * needs to know the concrete class to call {@link BuffJsonDecoder#decode}.
 * Jackson caches resolved deserializers, so this is a one-time cost per message
 * type.
 *
 * @see BuffJsonJacksonModule.ProtobufDeserializers
 */
final class ProtobufMessageDeserializer extends JsonDeserializer<Message> {

	private final BuffJsonDecoder decoder;
	private final Class<? extends Message> messageClass;

	ProtobufMessageDeserializer(BuffJsonDecoder decoder, Class<? extends Message> messageClass) {
		this.decoder = decoder;
		this.messageClass = messageClass;
	}

	/**
	 * Deserializes a proto3 JSON object from the Jackson parser.
	 *
	 * <p>
	 * Supports two fast paths depending on the input source:
	 * <ul>
	 * <li><b>String input</b> ({@code mapper.readValue(String, Class)}): extracts
	 * char offsets and calls
	 * {@link BuffJsonDecoder#decode(String, int, int, Class)} — no substring
	 * allocation.
	 * <li><b>byte[] input</b> ({@code mapper.readValue(byte[], Class)}): extracts
	 * byte offsets and calls
	 * {@link BuffJsonDecoder#decode(byte[], int, int, Class)} — zero-copy.
	 * </ul>
	 *
	 * <p>
	 * Requires {@link StreamReadFeature#INCLUDE_SOURCE_IN_LOCATION} to be enabled
	 * on the ObjectMapper. Throws {@link IOException} if the feature is not
	 * enabled.
	 */
	@Override
	public Message deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		Object source = parser.currentTokenLocation().contentReference().getRawContent();

		if (source instanceof String rawJson) {
			int start = (int) parser.currentTokenLocation().getCharOffset();
			parser.skipChildren();
			int end = (int) parser.currentLocation().getCharOffset();
			try {
				return decoder.decode(rawJson, start, end - start, messageClass);
			} catch (Exception e) {
				throw new IOException(
						"Failed to decode protobuf " + messageClass.getSimpleName() + ": " + e.getMessage(), e);
			}
		}

		if (source instanceof byte[] rawBytes) {
			int start = (int) parser.currentTokenLocation().getByteOffset();
			parser.skipChildren();
			int end = (int) parser.currentLocation().getByteOffset();
			try {
				return decoder.decode(rawBytes, start, end - start, messageClass);
			} catch (Exception e) {
				throw new IOException(
						"Failed to decode protobuf " + messageClass.getSimpleName() + ": " + e.getMessage(), e);
			}
		}

		throw new IOException("Protobuf deserialization requires StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION enabled "
				+ "and String or byte[] input. Configure via: JsonMapper.builder()"
				+ ".enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)");
	}

	@Override
	public Class<? extends Message> handledType() {
		return messageClass;
	}
}
