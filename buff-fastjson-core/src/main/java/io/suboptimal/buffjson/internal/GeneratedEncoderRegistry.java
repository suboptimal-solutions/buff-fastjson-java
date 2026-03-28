package io.suboptimal.buffjson.internal;

import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;

import io.suboptimal.buffjson.GeneratedEncoder;

/**
 * Registry of generated per-message-type encoders, discovered via
 * {@link ServiceLoader}.
 *
 * <p>
 * When no generated encoders are on the classpath (the plugin is not used), the
 * registry is empty and {@link #get} always returns {@code null}, causing the
 * generic reflection-based path to be used instead.
 */
public final class GeneratedEncoderRegistry {

	private static final ConcurrentHashMap<String, GeneratedEncoder<?>> ENCODERS = new ConcurrentHashMap<>();

	static {
		ServiceLoader.load(GeneratedEncoder.class).forEach(enc -> ENCODERS.put(enc.descriptorFullName(), enc));
	}

	private GeneratedEncoderRegistry() {
	}

	/**
	 * Returns the generated encoder for the given message type, or {@code null} if
	 * none is registered.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Message> GeneratedEncoder<T> get(Descriptor descriptor) {
		return (GeneratedEncoder<T>) ENCODERS.get(descriptor.getFullName());
	}
}
