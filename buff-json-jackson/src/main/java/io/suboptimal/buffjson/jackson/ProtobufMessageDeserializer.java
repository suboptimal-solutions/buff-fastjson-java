package io.suboptimal.buffjson.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.Message;

import io.suboptimal.buffjson.BuffJsonDecoder;

/**
 * Jackson deserializer for protobuf {@link Message} types.
 *
 * <p>
 * Reads the JSON subtree from Jackson's parser as a {@link JsonNode}, converts
 * it back to a JSON string via {@link JsonNode#toString()}, then delegates to
 * {@link BuffJsonDecoder#decode(String, Class)} for actual proto3 JSON parsing.
 *
 * <p>
 * The tree→string→parse round-trip is intentional: it keeps this module as a
 * thin wrapper without reimplementing proto3 JSON parsing for Jackson's
 * streaming API. The overhead is minimal for typical message sizes and avoids
 * duplicating the complex type dispatch logic in
 * {@link io.suboptimal.buffjson.internal.FieldReader}.
 *
 * <p>
 * A separate instance is created per target {@code Message} class (by
 * {@link ProtobufJacksonModule.ProtobufDeserializers}) because the deserializer
 * needs to know the concrete class to call {@link BuffJsonDecoder#decode}.
 * Jackson caches resolved deserializers, so this is a one-time cost per message
 * type.
 *
 * @see ProtobufJacksonModule.ProtobufDeserializers
 */
final class ProtobufMessageDeserializer extends JsonDeserializer<Message> {

	/**
	 * BuffJsonDecoder instance, potentially configured with a TypeRegistry for Any.
	 */
	private final BuffJsonDecoder decoder;

	/** The concrete Message subclass to deserialize into. */
	private final Class<? extends Message> messageClass;

	ProtobufMessageDeserializer(BuffJsonDecoder decoder, Class<? extends Message> messageClass) {
		this.decoder = decoder;
		this.messageClass = messageClass;
	}

	/**
	 * Deserializes a proto3 JSON object from the Jackson parser.
	 *
	 * <p>
	 * Reads the entire JSON subtree into a {@link JsonNode} (letting Jackson handle
	 * token management), converts to string, then delegates to buff-json's
	 * {@link BuffJsonDecoder} for the actual proto3 JSON → protobuf conversion.
	 *
	 * @param parser
	 *            the Jackson JSON parser positioned at the start of the message
	 *            object
	 * @param ctxt
	 *            the deserialization context (unused — all work is delegated to
	 *            buff-json)
	 * @return the deserialized protobuf message
	 */
	@Override
	public Message deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		// Read the full JSON subtree — handles nested objects, arrays, etc.
		JsonNode tree = parser.readValueAsTree();
		// Convert back to string for buff-json's decoder
		String json = tree.toString();
		try {
			return decoder.decode(json, messageClass);
		} catch (Exception e) {
			// BuffJson.decode() can throw RuntimeExceptions (e.g., fastjson2's
			// JSONException) on malformed input. Wrap them as IOException so Jackson's
			// error handling (e.g., DeserializationProblemHandler) can intercept them.
			throw new IOException("Failed to decode protobuf " + messageClass.getSimpleName() + ": " + e.getMessage(),
					e);
		}
	}

	@Override
	public Class<? extends Message> handledType() {
		return messageClass;
	}
}
