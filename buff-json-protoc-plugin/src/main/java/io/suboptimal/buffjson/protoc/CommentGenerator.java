package io.suboptimal.buffjson.protoc;

import java.util.*;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;

/**
 * Generates a {@code *ProtoComments} class per {@code .proto} file that
 * implements {@code BuffJsonGeneratedComments}. Extracts leading comments from
 * {@code SourceCodeInfo}, which protoc always sends to plugins even without
 * {@code --include_source_info}.
 */
final class CommentGenerator {

	private CommentGenerator() {
	}

	static String generate(FileDescriptorProto fileProto, String javaPackage, String className) {
		Map<List<Integer>, String> commentIndex = buildCommentIndex(fileProto);
		if (commentIndex.isEmpty()) {
			return null;
		}

		// Collect comments keyed by protobuf full name
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

		if (entries.isEmpty()) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("package ").append(javaPackage).append(";\n\n");
		sb.append("import java.util.Map;\n\n");
		sb.append("import io.suboptimal.buffjson.BuffJsonGeneratedComments;\n\n");
		sb.append("public final class ").append(className).append(" implements BuffJsonGeneratedComments {\n\n");
		sb.append("    public static final ").append(className).append(" INSTANCE = new ").append(className)
				.append("();\n\n");

		sb.append("    private static final Map<String, String> COMMENTS = Map.ofEntries(\n");
		for (int i = 0; i < entries.size(); i++) {
			var entry = entries.get(i);
			sb.append("        Map.entry(\"").append(entry.getKey()).append("\", \"")
					.append(escapeJava(entry.getValue())).append("\")");
			if (i < entries.size() - 1) {
				sb.append(",");
			}
			sb.append("\n");
		}
		sb.append("    );\n\n");

		sb.append("    @Override\n");
		sb.append("    public Map<String, String> getComments() {\n");
		sb.append("        return COMMENTS;\n");
		sb.append("    }\n");
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
				String comment = loc.getLeadingComments().strip();
				if (!comment.isEmpty()) {
					index.put(loc.getPathList(), comment);
				}
			}
		}
		return index;
	}

	private static String escapeJava(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t",
				"\\t");
	}
}
