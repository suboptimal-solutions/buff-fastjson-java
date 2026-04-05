package io.suboptimal.buffjson.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import com.google.protobuf.TypeRegistry;

/**
 * Convenience entry point for proto3 JSON serialization via Jackson.
 *
 * <p>
 * Provides static methods mirroring {@link io.suboptimal.buffjson.BuffJson} but
 * routing through Jackson's {@link ObjectMapper}. The output is identical to
 * {@code BuffJson.encode()} — this class exists for projects that want a
 * Jackson-flavored API or need to create module instances for their own
 * ObjectMapper.
 *
 * <h3>Quick start</h3>
 *
 * <pre>{@code
 * // Encode/decode via pre-configured ObjectMapper
 * String json = BuffJackson.encode(myMessage);
 * MyMessage msg = BuffJackson.decode(json, MyMessage.class);
 *
 * // Or register with your own ObjectMapper
 * ObjectMapper mapper = new ObjectMapper();
 * mapper.registerModule(BuffJackson.module());
 * }</pre>
 *
 * <h3>Thread safety</h3>
 *
 * <p>
 * The internal {@link ObjectMapper} is created once and shared — safe for
 * concurrent use (Jackson's ObjectMapper is thread-safe after configuration).
 * Does not support {@code google.protobuf.Any} — use
 * {@link #module(TypeRegistry)} for that.
 *
 * @see ProtobufJacksonModule
 * @see io.suboptimal.buffjson.BuffJson
 */
public final class BuffJackson {

	/**
	 * Pre-configured ObjectMapper with {@link ProtobufJacksonModule} registered (no
	 * TypeRegistry). Thread-safe, shared across all static method calls.
	 */
	private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper().registerModule(new ProtobufJacksonModule());

	private BuffJackson() {
	}

	/**
	 * Encodes a protobuf message to proto3 JSON via Jackson.
	 *
	 * <p>
	 * Equivalent to {@code BuffJson.encode(message)} in terms of output. The
	 * difference is that serialization passes through Jackson's ObjectMapper,
	 * making this compatible with Jackson-based frameworks.
	 *
	 * @param message
	 *            the protobuf message to encode
	 * @return compact JSON string (no insignificant whitespace)
	 * @throws IllegalStateException
	 *             if serialization fails
	 */
	public static String encode(Message message) {
		try {
			return DEFAULT_MAPPER.writeValueAsString(message);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to encode protobuf message", e);
		}
	}

	/**
	 * Decodes proto3 JSON to a protobuf message via Jackson.
	 *
	 * <p>
	 * Equivalent to {@code BuffJson.decode(json, messageClass)} in terms of parsing
	 * behavior. Accepts the same JSON formats: quoted int64, NaN/Infinity strings,
	 * base64 bytes, RFC 3339 timestamps, etc.
	 *
	 * @param json
	 *            the JSON string to decode
	 * @param messageClass
	 *            the target protobuf message class
	 * @param <T>
	 *            the message type
	 * @return the decoded protobuf message
	 * @throws IllegalStateException
	 *             if deserialization fails
	 */
	public static <T extends Message> T decode(String json, Class<T> messageClass) {
		try {
			return DEFAULT_MAPPER.readValue(json, messageClass);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to decode protobuf message", e);
		}
	}

	/**
	 * Creates a new {@link ProtobufJacksonModule} without TypeRegistry. Register
	 * with your own ObjectMapper via
	 * {@code mapper.registerModule(BuffJackson.module())}.
	 *
	 * @return a new module instance
	 */
	public static ProtobufJacksonModule module() {
		return new ProtobufJacksonModule();
	}

	/**
	 * Creates a new {@link ProtobufJacksonModule} with the given TypeRegistry for
	 * {@code google.protobuf.Any} support. Required when messages contain Any
	 * fields.
	 *
	 * @param registry
	 *            TypeRegistry with descriptors for types packed in Any
	 * @return a new module instance configured with the registry
	 */
	public static ProtobufJacksonModule module(TypeRegistry registry) {
		return new ProtobufJacksonModule(registry);
	}
}
