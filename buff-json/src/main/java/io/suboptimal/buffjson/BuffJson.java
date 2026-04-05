package io.suboptimal.buffjson;

import com.alibaba.fastjson2.JSONFactory;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TypeRegistry;

import io.suboptimal.buffjson.internal.ProtobufReaderModule;
import io.suboptimal.buffjson.internal.ProtobufWriterModule;

/**
 * Fast JSON serialization and deserialization for Protocol Buffer messages.
 *
 * <p>
 * Produces JSON output compliant with the
 * <a href="https://protobuf.dev/programming-guides/proto3/#json">Proto3 JSON
 * spec</a>, matching
 * {@code JsonFormat.printer().omittingInsignificantWhitespace().print()} output
 * exactly.
 *
 * <h3>Serialization</h3>
 *
 * <pre>{@code
 * String json = BuffJson.encode(myProtoMessage);
 * }</pre>
 *
 * <h3>Deserialization</h3>
 *
 * <pre>{@code
 * MyMessage msg = BuffJson.decode(json, MyMessage.class);
 * }</pre>
 *
 * <h3>With Any type support</h3>
 *
 * <pre>{@code
 * BuffJsonEncoder encoder = BuffJson.encoder()
 * 		.withTypeRegistry(TypeRegistry.newBuilder().add(MyMessage.getDescriptor()).build());
 *
 * String json = encoder.encode(messageContainingAny);
 * }</pre>
 *
 * <p>
 * Thread-safe. {@link BuffJsonEncoder} and {@link BuffJsonDecoder} instances
 * are immutable and can be cached.
 */
public final class BuffJson {

	static {
		JSONFactory.getDefaultObjectWriterProvider().register(ProtobufWriterModule.INSTANCE);
		JSONFactory.getDefaultObjectReaderProvider().register(ProtobufReaderModule.INSTANCE);
	}

	/** ThreadLocal holding the active TypeRegistry for the current encode call. */
	public static final ThreadLocal<TypeRegistry> ACTIVE_REGISTRY = new ThreadLocal<>();

	/**
	 * When set to {@code true}, generated encoders are bypassed and the runtime
	 * reflection path is used. Used by
	 * {@link BuffJsonEncoder#withGeneratedEncoders(boolean)} for benchmarking both
	 * paths.
	 */
	public static final ThreadLocal<Boolean> SKIP_GENERATED_ENCODERS = new ThreadLocal<>();

	/**
	 * When set to {@code true}, generated decoders are bypassed and the runtime
	 * reflection path is used. Used by
	 * {@link BuffJsonDecoder#withGeneratedDecoders(boolean)} for benchmarking both
	 * paths.
	 */
	public static final ThreadLocal<Boolean> SKIP_GENERATED_DECODERS = new ThreadLocal<>();

	private static final BuffJsonEncoder DEFAULT_ENCODER = new BuffJsonEncoder(null);
	private static final BuffJsonDecoder DEFAULT_DECODER = new BuffJsonDecoder(null);

	private BuffJson() {
	}

	/**
	 * Convenience method — encodes a message without a TypeRegistry. Equivalent to
	 * {@code BuffJson.encoder().encode(message)}.
	 *
	 * <p>
	 * Throws if the message contains {@code google.protobuf.Any} fields. Use
	 * {@link #encoder()} with {@link BuffJsonEncoder#withTypeRegistry} for Any
	 * support.
	 */
	public static String encode(MessageOrBuilder message) {
		return DEFAULT_ENCODER.encode(message);
	}

	/**
	 * Creates a new {@link BuffJsonEncoder} for configuring serialization options.
	 */
	public static BuffJsonEncoder encoder() {
		return DEFAULT_ENCODER;
	}

	/**
	 * Convenience method — decodes JSON to a message without a TypeRegistry.
	 * Equivalent to {@code BuffJson.decoder().decode(json, messageClass)}.
	 *
	 * <p>
	 * Throws if the JSON contains {@code google.protobuf.Any} fields. Use
	 * {@link #decoder()} with {@link BuffJsonDecoder#withTypeRegistry} for Any
	 * support.
	 */
	public static <T extends Message> T decode(String json, Class<T> messageClass) {
		return DEFAULT_DECODER.decode(json, messageClass);
	}

	/**
	 * Creates a new {@link BuffJsonDecoder} for configuring deserialization
	 * options.
	 */
	public static BuffJsonDecoder decoder() {
		return DEFAULT_DECODER;
	}
}
