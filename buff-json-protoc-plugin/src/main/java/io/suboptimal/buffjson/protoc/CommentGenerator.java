package io.suboptimal.buffjson.protoc;

import java.util.*;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;

/**
 * Extracts leading comments from protobuf {@code SourceCodeInfo} and produces
 * entries keyed by protobuf full name. The entries are embedded as a static
 * registration block in the first generated encoder class per {@code .proto}
 * file.
 */
final class CommentGenerator {

	private CommentGenerator() {
	}

	/**
	 * Extracts comment entries from a {@code .proto} file descriptor. Returns an
	 * empty list if no comments are present.
	 */
	static List<Map.Entry<String, String>> extractComments(FileDescriptorProto fileProto) {
		Map<List<Integer>, String> commentIndex = buildCommentIndex(fileProto);
		if (commentIndex.isEmpty()) {
			return List.of();
		}

		String protoPackage = fileProto.getPackage();
		List<Map.Entry<String, String>> entries = new ArrayList<>();

		for (int i = 0; i < fileProto.getMessageTypeCount(); i++) {
			collectMessageComments(fileProto.getMessageType(i),
					List.of(FileDescriptorProto.MESSAGE_TYPE_FIELD_NUMBER, i), protoPackage, commentIndex, entries);
		}
		for (int i = 0; i < fileProto.getEnumTypeCount(); i++) {
			String fullName = protoPackage.isEmpty()
					? fileProto.getEnumType(i).getName()
					: protoPackage + "." + fileProto.getEnumType(i).getName();
			addComment(commentIndex, List.of(FileDescriptorProto.ENUM_TYPE_FIELD_NUMBER, i), fullName, entries);
		}

		return entries;
	}

	/**
	 * Generates a static initializer block that registers comments with
	 * {@code GeneratedCommentRegistry} via reflection. When
	 * {@code buff-json-schema} is not on the classpath, {@code Class.forName} fails
	 * immediately and the {@code Map.ofEntries()} allocation is never reached.
	 */
	static String generateRegistrationBlock(List<Map.Entry<String, String>> entries) {
		if (entries.isEmpty()) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("static {\n");
		sb.append("  try {\n");
		sb.append("    Class.forName(\"io.suboptimal.buffjson.schema.GeneratedCommentRegistry\")\n");
		sb.append("      .getMethod(\"register\", java.util.Map.class)\n");
		sb.append("      .invoke(null, java.util.Map.ofEntries(\n");
		for (int i = 0; i < entries.size(); i++) {
			var entry = entries.get(i);
			sb.append("        java.util.Map.entry(\"").append(entry.getKey()).append("\", \"")
					.append(escapeJava(entry.getValue())).append("\")");
			if (i < entries.size() - 1) {
				sb.append(",");
			}
			sb.append("\n");
		}
		sb.append("      ));\n");
		sb.append("  } catch (ReflectiveOperationException ignored) {}\n");
		sb.append("}\n");
		return sb.toString();
	}

	private static void collectMessageComments(DescriptorProto msg, List<Integer> path, String parentFullName,
			Map<List<Integer>, String> commentIndex, List<Map.Entry<String, String>> entries) {

		String fullName = parentFullName.isEmpty() ? msg.getName() : parentFullName + "." + msg.getName();
		addComment(commentIndex, path, fullName, entries);

		for (int i = 0; i < msg.getFieldCount(); i++) {
			String fieldFullName = fullName + "." + msg.getField(i).getName();
			addComment(commentIndex, appendPath(path, DescriptorProto.FIELD_FIELD_NUMBER, i), fieldFullName, entries);
		}

		for (int i = 0; i < msg.getEnumTypeCount(); i++) {
			String enumFullName = fullName + "." + msg.getEnumType(i).getName();
			addComment(commentIndex, appendPath(path, DescriptorProto.ENUM_TYPE_FIELD_NUMBER, i), enumFullName,
					entries);
		}

		for (int i = 0; i < msg.getNestedTypeCount(); i++) {
			if (msg.getNestedType(i).getOptions().getMapEntry()) {
				continue;
			}
			collectMessageComments(msg.getNestedType(i), appendPath(path, DescriptorProto.NESTED_TYPE_FIELD_NUMBER, i),
					fullName, commentIndex, entries);
		}
	}

	private static void addComment(Map<List<Integer>, String> commentIndex, List<Integer> path, String fullName,
			List<Map.Entry<String, String>> entries) {
		String comment = commentIndex.get(path);
		if (comment != null) {
			entries.add(Map.entry(fullName, comment));
		}
	}

	private static List<Integer> appendPath(List<Integer> base, int fieldNumber, int index) {
		List<Integer> path = new ArrayList<>(base.size() + 2);
		path.addAll(base);
		path.add(fieldNumber);
		path.add(index);
		return path;
	}

	private static Map<List<Integer>, String> buildCommentIndex(FileDescriptorProto fileProto) {
		if (!fileProto.hasSourceCodeInfo()) {
			return Map.of();
		}
		SourceCodeInfo info = fileProto.getSourceCodeInfo();
		Map<List<Integer>, String> index = new HashMap<>();
		for (SourceCodeInfo.Location loc : info.getLocationList()) {
			if (loc.hasLeadingComments()) {
				String comment = stripLines(loc.getLeadingComments());
				if (!comment.isEmpty()) {
					index.put(loc.getPathList(), comment);
				}
			}
		}
		return index;
	}

	private static String stripLines(String comment) {
		String[] lines = comment.split("\n");
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			String stripped = line.strip();
			// Remove leading "* " or "*" from block comments (/** ... */)
			if (stripped.startsWith("* ")) {
				stripped = stripped.substring(2);
			} else if (stripped.equals("*")) {
				stripped = "";
			}
			if (!stripped.isEmpty()) {
				if (!sb.isEmpty())
					sb.append('\n');
				sb.append(stripped);
			}
		}
		return sb.toString();
	}

	static String escapeJava(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t",
				"\\t");
	}
}
