package io.suboptimal.buffjson.schema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of proto source comments, populated via reflection from static
 * initializers in generated protobuf outer classes.
 *
 * <p>
 * When {@code buff-json-schema} is not on the classpath, the generated
 * registration code silently skips — no comments are loaded, no overhead.
 */
public final class GeneratedCommentRegistry {

	private static final ConcurrentHashMap<String, String> COMMENTS = new ConcurrentHashMap<>();

	private GeneratedCommentRegistry() {
	}

	/**
	 * Registers comments extracted from a {@code .proto} file. Called via
	 * reflection from generated protobuf outer classes.
	 */
	public static void register(Map<String, String> comments) {
		COMMENTS.putAll(comments);
	}

	/**
	 * Returns the proto source comment for the given full name, or {@code null}.
	 */
	static String getComment(String fullName) {
		return COMMENTS.get(fullName);
	}
}
