package io.suboptimal.buffjson.schema;

import java.util.*;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Message;

/**
 * Generates JSON Schema (draft 2020-12) from Protocol Buffer message
 * descriptors.
 *
 * <p>
 * The generated schema reflects the
 * <a href="https://protobuf.dev/programming-guides/proto3/#json">Proto3 JSON
 * mapping</a>, matching the JSON representation produced by protobuf's standard
 * JSON serialization. This makes the schemas suitable for OpenAPI 3.1+,
 * AsyncAPI 3.0+, and MCP tool definitions.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // From a Descriptor
 * Map<String, Object> schema = ProtobufSchema.generate(MyMessage.getDescriptor());
 *
 * // From a Message class
 * Map<String, Object> schema = ProtobufSchema.generate(MyMessage.class);
 * }</pre>
 *
 * <p>
 * Returns a {@code Map<String, Object>} that can be serialized to JSON with any
 * library (Jackson, Gson, fastjson2) or passed directly to OpenAPI/MCP tooling.
 *
 * <p>
 * Handles all proto3 scalar types, enums, nested messages, repeated fields,
 * maps, oneofs, and all 16 protobuf
 * <a href="https://protobuf.dev/reference/protobuf/google.protobuf/">well-known
 * types</a> (Timestamp, Duration, Struct, Value, Any, wrappers, etc.).
 *
 * <p>
 * Recursive and shared message types use {@code $defs}/{@code $ref} to avoid
 * infinite expansion.
 *
 * <p>
 * Proto comments are included as {@code description} fields when source code
 * info is available (requires protoc {@code --include_source_info} flag).
 * Well-known types always include format descriptions regardless.
 */
public final class ProtobufSchema {

	private static final String SCHEMA_DRAFT = "https://json-schema.org/draft/2020-12/schema";

	// Includes google.protobuf.Empty (unlike the serialization modules in
	// buff-fastjson-core) because schema generation treats it as a WKT with a
	// fixed schema rather than expanding its fields.
	private static final Set<String> WELL_KNOWN_TYPE_NAMES = Set.of("google.protobuf.Any", "google.protobuf.Timestamp",
			"google.protobuf.Duration", "google.protobuf.FieldMask", "google.protobuf.Struct", "google.protobuf.Value",
			"google.protobuf.ListValue", "google.protobuf.DoubleValue", "google.protobuf.FloatValue",
			"google.protobuf.Int64Value", "google.protobuf.UInt64Value", "google.protobuf.Int32Value",
			"google.protobuf.UInt32Value", "google.protobuf.BoolValue", "google.protobuf.StringValue",
			"google.protobuf.BytesValue", "google.protobuf.Empty");

	private final Map<FileDescriptor, Map<List<Integer>, String>> commentCache = new IdentityHashMap<>();
	private final Map<String, Map<String, Object>> defs = new LinkedHashMap<>();
	private final Set<String> inProgress = new HashSet<>();

	private ProtobufSchema() {
	}

	/**
	 * Generates a JSON Schema from a protobuf {@link Descriptor}.
	 *
	 * @param descriptor
	 *            the message descriptor
	 * @return a JSON Schema as a {@code Map<String, Object>}
	 */
	public static Map<String, Object> generate(Descriptor descriptor) {
		ProtobufSchema generator = new ProtobufSchema();
		Map<String, Object> schema = generator.schemaForMessage(descriptor);
		schema.put("$schema", SCHEMA_DRAFT);
		if (!generator.defs.isEmpty()) {
			schema.put("$defs", new LinkedHashMap<>(generator.defs));
		}
		return schema;
	}

	/**
	 * Generates a JSON Schema from a protobuf {@link Message} class.
	 *
	 * @param messageClass
	 *            the message class
	 * @return a JSON Schema as a {@code Map<String, Object>}
	 */
	public static <T extends Message> Map<String, Object> generate(Class<T> messageClass) {
		try {
			Message defaultInstance = (Message) messageClass.getMethod("getDefaultInstance").invoke(null);
			return generate(defaultInstance.getDescriptorForType());
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("Cannot get descriptor for " + messageClass.getName(), e);
		}
	}

	private Map<String, Object> schemaForMessage(Descriptor descriptor) {
		if (WELL_KNOWN_TYPE_NAMES.contains(descriptor.getFullName())) {
			return schemaForWellKnownType(descriptor);
		}

		String fullName = descriptor.getFullName();

		// Cycle detection: if we're already processing this type, emit a $ref
		if (inProgress.contains(fullName)) {
			defs.putIfAbsent(fullName, new LinkedHashMap<>());
			return ref(fullName);
		}

		inProgress.add(fullName);

		Map<String, Object> properties = new LinkedHashMap<>();
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("title", descriptor.getName());

		String messageComment = getComment(descriptor);
		if (messageComment != null) {
			schema.put("description", messageComment);
		}

		for (FieldDescriptor fd : descriptor.getFields()) {
			Map<String, Object> fieldSchema = schemaForField(fd);
			String fieldComment = getComment(fd);
			if (fieldComment != null) {
				fieldSchema.put("description", fieldComment);
			}
			properties.put(fd.getJsonName(), fieldSchema);
		}

		if (!properties.isEmpty()) {
			schema.put("properties", properties);
		}

		inProgress.remove(fullName);

		// If this type was referenced recursively, store it in $defs
		if (defs.containsKey(fullName)) {
			defs.put(fullName, schema);
			return ref(fullName);
		}

		return schema;
	}

	private Map<String, Object> schemaForField(FieldDescriptor fd) {
		if (fd.isMapField()) {
			return schemaForMap(fd);
		}
		if (fd.isRepeated()) {
			Map<String, Object> items = schemaForSingleValue(fd);
			Map<String, Object> schema = new LinkedHashMap<>();
			schema.put("type", "array");
			schema.put("items", items);
			return schema;
		}
		return schemaForSingleValue(fd);
	}

	private Map<String, Object> schemaForSingleValue(FieldDescriptor fd) {
		return switch (fd.getJavaType()) {
			case INT -> schemaForIntField(fd);
			case LONG -> schemaForLongField(fd);
			case FLOAT, DOUBLE -> floatSchema();
			case BOOLEAN -> mapOf("type", "boolean");
			case STRING -> mapOf("type", "string");
			case BYTE_STRING -> mapOf("type", "string", "contentEncoding", "base64");
			case ENUM -> schemaForEnum(fd.getEnumType());
			case MESSAGE -> schemaForMessage(fd.getMessageType());
		};
	}

	private static Map<String, Object> schemaForIntField(FieldDescriptor fd) {
		var type = fd.getType();
		if (type == FieldDescriptor.Type.UINT32 || type == FieldDescriptor.Type.FIXED32) {
			Map<String, Object> schema = new LinkedHashMap<>();
			schema.put("type", "integer");
			schema.put("minimum", 0);
			return schema;
		}
		return mapOf("type", "integer");
	}

	private static Map<String, Object> schemaForLongField(FieldDescriptor fd) {
		var type = fd.getType();
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "string");
		if (type == FieldDescriptor.Type.UINT64 || type == FieldDescriptor.Type.FIXED64) {
			schema.put("format", "uint64");
		} else {
			schema.put("format", "int64");
		}
		return schema;
	}

	private static Map<String, Object> floatSchema() {
		Map<String, Object> number = mapOf("type", "number");
		Map<String, Object> special = new LinkedHashMap<>();
		special.put("type", "string");
		special.put("enum", List.of("NaN", "Infinity", "-Infinity"));
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("oneOf", List.of(number, special));
		return schema;
	}

	private Map<String, Object> schemaForEnum(EnumDescriptor enumDesc) {
		List<String> names = new ArrayList<>();
		for (EnumValueDescriptor value : enumDesc.getValues()) {
			names.add(value.getName());
		}
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "string");
		schema.put("title", enumDesc.getName());
		schema.put("enum", names);

		String enumComment = getComment(enumDesc);
		if (enumComment != null) {
			schema.put("description", enumComment);
		}

		return schema;
	}

	private Map<String, Object> schemaForMap(FieldDescriptor fd) {
		Descriptor entryDesc = fd.getMessageType();
		FieldDescriptor valueDesc = entryDesc.findFieldByName("value");
		Map<String, Object> valueSchema = schemaForSingleValue(valueDesc);
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("additionalProperties", valueSchema);
		return schema;
	}

	private static Map<String, Object> schemaForWellKnownType(Descriptor descriptor) {
		return switch (descriptor.getFullName()) {
			case "google.protobuf.Timestamp" -> {
				Map<String, Object> s = new LinkedHashMap<>();
				s.put("type", "string");
				s.put("format", "date-time");
				s.put("description", "RFC 3339 date-time format.");
				yield s;
			}
			case "google.protobuf.Duration" -> {
				Map<String, Object> s = new LinkedHashMap<>();
				s.put("type", "string");
				s.put("description", "Signed seconds with up to 9 fractional digits, suffixed with 's'.");
				yield s;
			}
			case "google.protobuf.FieldMask" -> {
				Map<String, Object> s = new LinkedHashMap<>();
				s.put("type", "string");
				s.put("description", "Comma-separated camelCase field paths.");
				yield s;
			}
			case "google.protobuf.Struct" -> {
				Map<String, Object> s = new LinkedHashMap<>();
				s.put("type", "object");
				s.put("description", "Arbitrary JSON object.");
				yield s;
			}
			case "google.protobuf.Value" -> {
				Map<String, Object> s = new LinkedHashMap<>();
				s.put("description", "Arbitrary JSON value.");
				yield s;
			}
			case "google.protobuf.ListValue" -> {
				Map<String, Object> s = new LinkedHashMap<>();
				s.put("type", "array");
				s.put("description", "JSON array of arbitrary values.");
				yield s;
			}
			case "google.protobuf.Empty" -> mapOf("type", "object");
			case "google.protobuf.Any" -> {
				Map<String, Object> s = new LinkedHashMap<>();
				s.put("type", "object");
				s.put("properties", Map.of("@type", mapOf("type", "string")));
				s.put("required", List.of("@type"));
				s.put("description", "Arbitrary message identified by a type URL in @type.");
				yield s;
			}
			case "google.protobuf.Int32Value" -> mapOf("type", "integer");
			case "google.protobuf.UInt32Value" -> {
				Map<String, Object> s = new LinkedHashMap<>();
				s.put("type", "integer");
				s.put("minimum", 0);
				yield s;
			}
			case "google.protobuf.Int64Value" -> mapOf("type", "string", "format", "int64");
			case "google.protobuf.UInt64Value" -> mapOf("type", "string", "format", "uint64");
			case "google.protobuf.FloatValue", "google.protobuf.DoubleValue" -> floatSchema();
			case "google.protobuf.BoolValue" -> mapOf("type", "boolean");
			case "google.protobuf.StringValue" -> mapOf("type", "string");
			case "google.protobuf.BytesValue" -> mapOf("type", "string", "contentEncoding", "base64");
			default -> throw new IllegalArgumentException("Unknown well-known type: " + descriptor.getFullName());
		};
	}

	// =========================================================================
	// Source code info / comment extraction
	// =========================================================================

	/**
	 * Extracts the leading comment for a message descriptor from the proto file's
	 * source code info. Returns null if source code info is not available (requires
	 * protoc {@code --include_source_info} flag).
	 */
	private String getComment(Descriptor descriptor) {
		List<Integer> path = getMessagePath(descriptor);
		return lookupComment(descriptor.getFile(), path);
	}

	private String getComment(FieldDescriptor fd) {
		Descriptor parent = fd.getContainingType();
		List<Integer> parentPath = getMessagePath(parent);
		List<Integer> path = new ArrayList<>(parentPath);
		// DescriptorProto.FIELD_FIELD_NUMBER = 2
		path.add(2);
		path.add(fd.getIndex());
		return lookupComment(fd.getFile(), path);
	}

	private String getComment(EnumDescriptor enumDesc) {
		if (enumDesc.getContainingType() != null) {
			List<Integer> parentPath = getMessagePath(enumDesc.getContainingType());
			List<Integer> path = new ArrayList<>(parentPath);
			// DescriptorProto.ENUM_TYPE_FIELD_NUMBER = 4
			path.add(4);
			path.add(enumDesc.getIndex());
			return lookupComment(enumDesc.getFile(), path);
		}
		// Top-level enum: FileDescriptorProto.ENUM_TYPE_FIELD_NUMBER = 5
		return lookupComment(enumDesc.getFile(), List.of(5, enumDesc.getIndex()));
	}

	/**
	 * Computes the source code info path for a message descriptor, handling
	 * nesting.
	 */
	private static List<Integer> getMessagePath(Descriptor descriptor) {
		Deque<Integer> indices = new ArrayDeque<>();
		Descriptor current = descriptor;
		while (current != null) {
			indices.addFirst(current.getIndex());
			if (current.getContainingType() != null) {
				// DescriptorProto.NESTED_TYPE_FIELD_NUMBER = 3
				indices.addFirst(3);
			} else {
				// FileDescriptorProto.MESSAGE_TYPE_FIELD_NUMBER = 4
				indices.addFirst(4);
			}
			current = current.getContainingType();
		}
		return new ArrayList<>(indices);
	}

	/**
	 * Looks up a comment by path in the file's source code info. Builds and caches
	 * a path→comment index per file on first access.
	 */
	private String lookupComment(FileDescriptor file, List<Integer> path) {
		Map<List<Integer>, String> index = commentCache.computeIfAbsent(file, ProtobufSchema::buildCommentIndex);
		return index.get(path);
	}

	/**
	 * Builds a path→comment index from a file's source code info. Returns an empty
	 * map if source code info is not available.
	 */
	private static Map<List<Integer>, String> buildCommentIndex(FileDescriptor file) {
		FileDescriptorProto proto = file.toProto();
		if (!proto.hasSourceCodeInfo()) {
			return Map.of();
		}
		SourceCodeInfo info = proto.getSourceCodeInfo();
		Map<List<Integer>, String> index = new HashMap<>();
		for (SourceCodeInfo.Location loc : info.getLocationList()) {
			if (loc.hasLeadingComments()) {
				String comment = loc.getLeadingComments().strip();
				if (!comment.isEmpty()) {
					index.put(loc.getPathList(), comment);
				}
			}
		}
		return index;
	}

	// =========================================================================
	// Map construction helpers
	// =========================================================================

	private static Map<String, Object> ref(String fullName) {
		return mapOf("$ref", "#/$defs/" + fullName);
	}

	private static Map<String, Object> mapOf(String key, Object value) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put(key, value);
		return map;
	}

	private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put(k1, v1);
		map.put(k2, v2);
		return map;
	}
}
