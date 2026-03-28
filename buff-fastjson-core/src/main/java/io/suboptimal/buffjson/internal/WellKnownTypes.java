package io.suboptimal.buffjson.internal;

import com.alibaba.fastjson2.JSONWriter;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Specialized JSON serialization for protobuf
 * <a href="https://protobuf.dev/reference/protobuf/google.protobuf/">well-known types</a>.
 *
 * <p>These types have special JSON representations defined by the proto3 spec that differ
 * from their standard message serialization. Supports 15 types:
 *
 * <ul>
 *   <li><b>Timestamp</b>: RFC 3339 string ({@code "2024-01-01T00:00:00Z"})</li>
 *   <li><b>Duration</b>: seconds string with 's' suffix ({@code "3600.500s"})</li>
 *   <li><b>FieldMask</b>: comma-separated camelCase paths ({@code "foo,barBaz"})</li>
 *   <li><b>Struct</b>: native JSON object</li>
 *   <li><b>Value</b>: native JSON value (string, number, bool, null, object, or array)</li>
 *   <li><b>ListValue</b>: native JSON array</li>
 *   <li><b>Wrappers</b> (Int32Value, Int64Value, UInt32Value, UInt64Value, FloatValue,
 *       DoubleValue, BoolValue, StringValue, BytesValue): unwrapped to primitive JSON values</li>
 * </ul>
 *
 * <p>Nanos formatting uses 3, 6, or 9 digits (matching protobuf's convention) — never
 * arbitrary precision.
 */
public final class WellKnownTypes {

    private static final Set<String> WELL_KNOWN_TYPE_NAMES = Set.of(
            "google.protobuf.Timestamp",
            "google.protobuf.Duration",
            "google.protobuf.FieldMask",
            "google.protobuf.Struct",
            "google.protobuf.Value",
            "google.protobuf.ListValue",
            "google.protobuf.DoubleValue",
            "google.protobuf.FloatValue",
            "google.protobuf.Int64Value",
            "google.protobuf.UInt64Value",
            "google.protobuf.Int32Value",
            "google.protobuf.UInt32Value",
            "google.protobuf.BoolValue",
            "google.protobuf.StringValue",
            "google.protobuf.BytesValue"
    );

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private WellKnownTypes() {}

    public static boolean isWellKnownType(Descriptor descriptor) {
        return WELL_KNOWN_TYPE_NAMES.contains(descriptor.getFullName());
    }

    public static void write(JSONWriter jsonWriter, Message message) {
        var descriptor = message.getDescriptorForType();
        switch (descriptor.getFullName()) {
            case "google.protobuf.Timestamp" -> writeTimestamp(jsonWriter, message);
            case "google.protobuf.Duration" -> writeDuration(jsonWriter, message);
            case "google.protobuf.FieldMask" -> writeFieldMask(jsonWriter, message);
            case "google.protobuf.Struct" -> writeStruct(jsonWriter, message);
            case "google.protobuf.Value" -> writeValue(jsonWriter, message);
            case "google.protobuf.ListValue" -> writeListValue(jsonWriter, message);
            case "google.protobuf.DoubleValue", "google.protobuf.FloatValue",
                 "google.protobuf.Int64Value", "google.protobuf.UInt64Value",
                 "google.protobuf.Int32Value", "google.protobuf.UInt32Value",
                 "google.protobuf.BoolValue", "google.protobuf.StringValue",
                 "google.protobuf.BytesValue" -> writeWrapper(jsonWriter, message);
            default -> throw new IllegalArgumentException("Unknown well-known type: " + descriptor.getFullName());
        }
    }

    private static void writeTimestamp(JSONWriter jsonWriter, Message message) {
        var desc = message.getDescriptorForType();
        long seconds = (long) message.getField(desc.findFieldByName("seconds"));
        int nanos = (int) message.getField(desc.findFieldByName("nanos"));

        Instant instant = Instant.ofEpochSecond(seconds, nanos);
        StringBuilder sb = new StringBuilder(30);
        RFC3339.formatTo(instant, sb);
        if (nanos == 0) {
            sb.append('Z');
        } else {
            sb.append('.').append(formatNanos(nanos)).append('Z');
        }
        jsonWriter.writeString(sb.toString());
    }

    private static void writeDuration(JSONWriter jsonWriter, Message message) {
        var desc = message.getDescriptorForType();
        long seconds = (long) message.getField(desc.findFieldByName("seconds"));
        int nanos = (int) message.getField(desc.findFieldByName("nanos"));

        StringBuilder sb = new StringBuilder(20);
        if (seconds < 0 || nanos < 0) {
            // Handle negative durations
            if (seconds < 0 && nanos < 0) {
                sb.append('-');
                seconds = -seconds;
                nanos = -nanos;
            } else if (seconds < 0) {
                sb.append('-');
                seconds = -seconds;
            } else {
                sb.append('-');
                nanos = -nanos;
            }
        }
        sb.append(seconds);
        if (nanos != 0) {
            sb.append('.').append(formatNanos(nanos));
        }
        sb.append('s');
        jsonWriter.writeString(sb.toString());
    }

    private static void writeFieldMask(JSONWriter jsonWriter, Message message) {
        var desc = message.getDescriptorForType();
        @SuppressWarnings("unchecked")
        List<String> paths = (List<String>) message.getField(desc.findFieldByName("paths"));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(snakeToCamel(paths.get(i)));
        }
        jsonWriter.writeString(sb.toString());
    }

    private static void writeStruct(JSONWriter jsonWriter, Message message) {
        var desc = message.getDescriptorForType();
        var fieldsField = desc.findFieldByName("fields");
        @SuppressWarnings("unchecked")
        List<Message> entries = (List<Message>) message.getField(fieldsField);

        jsonWriter.startObject();
        for (var entry : entries) {
            var entryDesc = entry.getDescriptorForType();
            String key = (String) entry.getField(entryDesc.findFieldByName("key"));
            Message value = (Message) entry.getField(entryDesc.findFieldByName("value"));
            jsonWriter.writeName(key);
            jsonWriter.writeColon();
            writeValue(jsonWriter, value);
        }
        jsonWriter.endObject();
    }

    private static void writeValue(JSONWriter jsonWriter, Message message) {
        var desc = message.getDescriptorForType();
        var numberField = desc.findFieldByName("number_value");
        var stringField = desc.findFieldByName("string_value");
        var boolField = desc.findFieldByName("bool_value");
        var structField = desc.findFieldByName("struct_value");
        var listField = desc.findFieldByName("list_value");

        // Check which oneof field is set via the "kind" oneof
        var kindOneof = desc.getOneofs().get(0);
        var activeField = message.getOneofFieldDescriptor(kindOneof);

        if (activeField == null) {
            jsonWriter.writeNull();
            return;
        }

        switch (activeField.getName()) {
            case "null_value" -> jsonWriter.writeNull();
            case "number_value" -> jsonWriter.writeDouble((double) message.getField(numberField));
            case "string_value" -> jsonWriter.writeString((String) message.getField(stringField));
            case "bool_value" -> jsonWriter.writeBool((boolean) message.getField(boolField));
            case "struct_value" -> writeStruct(jsonWriter, (Message) message.getField(structField));
            case "list_value" -> writeListValue(jsonWriter, (Message) message.getField(listField));
        }
    }

    private static void writeListValue(JSONWriter jsonWriter, Message message) {
        var desc = message.getDescriptorForType();
        var valuesField = desc.findFieldByName("values");
        @SuppressWarnings("unchecked")
        List<Message> values = (List<Message>) message.getField(valuesField);

        jsonWriter.startArray();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) jsonWriter.writeComma();
            writeValue(jsonWriter, values.get(i));
        }
        jsonWriter.endArray();
    }

    private static void writeWrapper(JSONWriter jsonWriter, Message message) {
        var desc = message.getDescriptorForType();
        var valueField = desc.findFieldByName("value");
        Object value = message.getField(valueField);

        switch (desc.getFullName()) {
            case "google.protobuf.DoubleValue" -> FieldWriter.writeDoubleValue(jsonWriter, (double) value);
            case "google.protobuf.FloatValue" -> FieldWriter.writeFloatValue(jsonWriter, (float) value);
            case "google.protobuf.Int64Value" -> jsonWriter.writeString(Long.toString((long) value));
            case "google.protobuf.UInt64Value" -> jsonWriter.writeString(Long.toUnsignedString((long) value));
            case "google.protobuf.Int32Value" -> jsonWriter.writeInt32((int) value);
            case "google.protobuf.UInt32Value" -> jsonWriter.writeInt64(Integer.toUnsignedLong((int) value));
            case "google.protobuf.BoolValue" -> jsonWriter.writeBool((boolean) value);
            case "google.protobuf.StringValue" -> jsonWriter.writeString((String) value);
            case "google.protobuf.BytesValue" -> {
                ByteString bytes = (ByteString) value;
                jsonWriter.writeString(Base64.getEncoder().encodeToString(bytes.toByteArray()));
            }
        }
    }

    /**
     * Format nanos to 3, 6, or 9 digits (matching protobuf's convention).
     */
    private static String formatNanos(int nanos) {
        if (nanos % 1_000_000 == 0) {
            return String.format("%03d", nanos / 1_000_000);
        } else if (nanos % 1_000 == 0) {
            return String.format("%06d", nanos / 1_000);
        } else {
            return String.format("%09d", nanos);
        }
    }

    /**
     * Convert snake_case to lowerCamelCase for FieldMask paths.
     */
    private static String snakeToCamel(String snake) {
        StringBuilder sb = new StringBuilder(snake.length());
        boolean upperNext = false;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else {
                sb.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        return sb.toString();
    }
}
