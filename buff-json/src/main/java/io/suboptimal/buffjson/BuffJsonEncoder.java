package io.suboptimal.buffjson;

import com.alibaba.fastjson2.JSON;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TypeRegistry;

/**
 * Configurable encoder for protobuf-to-JSON serialization.
 *
 * <p>
 * Instances are immutable and thread-safe — safe to cache and reuse:
 *
 * <pre>{@code
 * private static final BuffJsonEncoder ENCODER = BuffJson.encoder().withTypeRegistry(registry);
 *
 * String json = ENCODER.encode(message);
 * }</pre>
 *
 * @see BuffJson#encoder()
 */
public final class BuffJsonEncoder {

	private final TypeRegistry typeRegistry;
	private final boolean useGeneratedEncoders;

	BuffJsonEncoder(TypeRegistry typeRegistry) {
		this(typeRegistry, true);
	}

	private BuffJsonEncoder(TypeRegistry typeRegistry, boolean useGeneratedEncoders) {
		this.typeRegistry = typeRegistry;
		this.useGeneratedEncoders = useGeneratedEncoders;
	}

	/**
	 * Sets the {@link TypeRegistry} for resolving {@code google.protobuf.Any}
	 * fields. Required when the message (or any nested message) contains Any
	 * fields.
	 *
	 * @param registry
	 *            the type registry containing descriptors for types packed in Any
	 * @return a new BuffJsonEncoder with the registry configured
	 */
	public BuffJsonEncoder withTypeRegistry(TypeRegistry registry) {
		return new BuffJsonEncoder(registry, useGeneratedEncoders);
	}

	/**
	 * Controls whether generated encoders (from {@code buff-json-protoc-plugin})
	 * are used when available. Defaults to {@code true}.
	 *
	 * <p>
	 * Setting to {@code false} forces the runtime reflection-based path, useful for
	 * benchmarking or testing both paths independently.
	 *
	 * @return a new BuffJsonEncoder with the setting applied
	 */
	public BuffJsonEncoder withGeneratedEncoders(boolean enabled) {
		return new BuffJsonEncoder(typeRegistry, enabled);
	}

	/**
	 * Encodes a Protocol Buffer message to its proto3 JSON representation.
	 *
	 * @param message
	 *            the protobuf message or builder to encode
	 * @return compact JSON string (no insignificant whitespace)
	 */
	public String encode(MessageOrBuilder message) {
		Message msg;
		if (message instanceof Message m) {
			msg = m;
		} else {
			msg = ((Message.Builder) message).buildPartial();
		}
		if (typeRegistry != null) {
			BuffJson.ACTIVE_REGISTRY.set(typeRegistry);
		}
		if (!useGeneratedEncoders) {
			BuffJson.SKIP_GENERATED_ENCODERS.set(Boolean.TRUE);
		}
		try {
			return JSON.toJSONString(msg);
		} finally {
			if (typeRegistry != null) {
				BuffJson.ACTIVE_REGISTRY.remove();
			}
			if (!useGeneratedEncoders) {
				BuffJson.SKIP_GENERATED_ENCODERS.remove();
			}
		}
	}
}
