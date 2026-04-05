package io.suboptimal.buffjson;

import com.alibaba.fastjson2.JSON;
import com.google.protobuf.Message;
import com.google.protobuf.TypeRegistry;

/**
 * Configurable decoder for JSON-to-protobuf deserialization.
 *
 * <p>
 * Instances are immutable and thread-safe — safe to cache and reuse:
 *
 * <pre>{@code
 * private static final BuffJsonDecoder DECODER = BuffJson.decoder().withTypeRegistry(registry);
 *
 * MyMessage msg = DECODER.decode(json, MyMessage.class);
 * }</pre>
 *
 * @see BuffJson#decoder()
 */
public final class BuffJsonDecoder {

	private final TypeRegistry typeRegistry;
	private final boolean useGeneratedDecoders;

	BuffJsonDecoder(TypeRegistry typeRegistry) {
		this(typeRegistry, true);
	}

	private BuffJsonDecoder(TypeRegistry typeRegistry, boolean useGeneratedDecoders) {
		this.typeRegistry = typeRegistry;
		this.useGeneratedDecoders = useGeneratedDecoders;
	}

	/**
	 * Sets the {@link TypeRegistry} for resolving {@code google.protobuf.Any}
	 * fields. Required when the JSON (or any nested content) contains Any fields.
	 *
	 * @param registry
	 *            the type registry containing descriptors for types packed in Any
	 * @return a new BuffJsonDecoder with the registry configured
	 */
	public BuffJsonDecoder withTypeRegistry(TypeRegistry registry) {
		return new BuffJsonDecoder(registry, useGeneratedDecoders);
	}

	/**
	 * Controls whether generated decoders (from {@code buff-json-protoc-plugin})
	 * are used when available. Defaults to {@code true}.
	 *
	 * <p>
	 * Setting to {@code false} forces the runtime reflection-based path, useful for
	 * benchmarking or testing both paths independently.
	 *
	 * @return a new BuffJsonDecoder with the setting applied
	 */
	public BuffJsonDecoder withGeneratedDecoders(boolean enabled) {
		return new BuffJsonDecoder(typeRegistry, enabled);
	}

	/**
	 * Decodes a proto3 JSON string to a Protocol Buffer message.
	 *
	 * @param json
	 *            the JSON string to decode
	 * @param messageClass
	 *            the target protobuf message class
	 * @return the decoded protobuf message
	 */
	public <T extends Message> T decode(String json, Class<T> messageClass) {
		setupThreadLocals();
		try {
			return JSON.parseObject(json, messageClass);
		} finally {
			clearThreadLocals();
		}
	}

	/**
	 * Decodes a proto3 JSON substring to a Protocol Buffer message without
	 * allocating a new String. FastJson2 reads the backing storage of the original
	 * String directly.
	 *
	 * @param json
	 *            the JSON string containing the message
	 * @param offset
	 *            start position within the string
	 * @param length
	 *            number of characters to parse
	 * @param messageClass
	 *            the target protobuf message class
	 * @return the decoded protobuf message
	 */
	public <T extends Message> T decode(String json, int offset, int length, Class<T> messageClass) {
		setupThreadLocals();
		try {
			return JSON.parseObject(json, offset, length, messageClass);
		} finally {
			clearThreadLocals();
		}
	}

	/**
	 * Decodes proto3 JSON from a byte array slice to a Protocol Buffer message.
	 * Zero-copy — FastJson2 reads directly from the provided array.
	 *
	 * @param json
	 *            the byte array containing UTF-8 JSON
	 * @param offset
	 *            start position within the array
	 * @param length
	 *            number of bytes to parse
	 * @param messageClass
	 *            the target protobuf message class
	 * @return the decoded protobuf message
	 */
	public <T extends Message> T decode(byte[] json, int offset, int length, Class<T> messageClass) {
		setupThreadLocals();
		try {
			return JSON.parseObject(json, offset, length, messageClass);
		} finally {
			clearThreadLocals();
		}
	}

	private void setupThreadLocals() {
		if (typeRegistry != null) {
			BuffJson.ACTIVE_REGISTRY.set(typeRegistry);
		}
		if (!useGeneratedDecoders) {
			BuffJson.SKIP_GENERATED_DECODERS.set(Boolean.TRUE);
		}
	}

	private void clearThreadLocals() {
		if (typeRegistry != null) {
			BuffJson.ACTIVE_REGISTRY.remove();
		}
		if (!useGeneratedDecoders) {
			BuffJson.SKIP_GENERATED_DECODERS.remove();
		}
	}
}
