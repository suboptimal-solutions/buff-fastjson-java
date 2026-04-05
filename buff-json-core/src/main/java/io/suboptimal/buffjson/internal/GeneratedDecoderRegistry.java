package io.suboptimal.buffjson.internal;

import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;

import io.suboptimal.buffjson.BuffJsonGeneratedDecoder;

/**
 * Registry of generated per-message-type decoders, discovered via
 * {@link ServiceLoader}.
 *
 * <p>
 * When no generated decoders are on the classpath (the plugin is not used), the
 * registry is empty and {@link #get} always returns {@code null}, causing the
 * runtime reflection-based path to be used instead.
 */
public final class GeneratedDecoderRegistry {

	private static final ConcurrentHashMap<String, BuffJsonGeneratedDecoder<?>> DECODERS_BY_NAME = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Descriptor, BuffJsonGeneratedDecoder<?>> DECODERS_BY_DESC = new ConcurrentHashMap<>();
	private static final boolean HAS_DECODERS;

	static {
		ServiceLoader.load(BuffJsonGeneratedDecoder.class)
				.forEach(dec -> DECODERS_BY_NAME.put(dec.descriptorFullName(), dec));
		HAS_DECODERS = !DECODERS_BY_NAME.isEmpty();
	}

	private GeneratedDecoderRegistry() {
	}

	/** Returns {@code true} if any generated decoders are registered. */
	public static boolean hasDecoders() {
		return HAS_DECODERS;
	}

	/**
	 * Returns the generated decoder for the given message type, or {@code null} if
	 * none is registered. Uses Descriptor identity for fast lookups after the first
	 * call per type.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Message> BuffJsonGeneratedDecoder<T> get(Descriptor descriptor) {
		var cached = DECODERS_BY_DESC.get(descriptor);
		if (cached != null) {
			return (BuffJsonGeneratedDecoder<T>) cached;
		}
		var decoder = DECODERS_BY_NAME.get(descriptor.getFullName());
		if (decoder != null) {
			DECODERS_BY_DESC.put(descriptor, decoder);
		}
		return (BuffJsonGeneratedDecoder<T>) decoder;
	}
}
