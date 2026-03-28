package io.suboptimal.buffjson.internal;

import com.alibaba.fastjson2.JSONWriter;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;

import java.util.Base64;
import java.util.List;

public final class FieldWriter {

    private FieldWriter() {}

    public static void writeValue(JSONWriter jsonWriter, FieldDescriptor fd, Object value) {
        switch (fd.getJavaType()) {
            case INT -> {
                // Handle unsigned types: uint32/fixed32 need unsigned representation
                var type = fd.getType();
                if (type == FieldDescriptor.Type.UINT32 || type == FieldDescriptor.Type.FIXED32) {
                    jsonWriter.writeInt64(Integer.toUnsignedLong((int) value));
                } else {
                    jsonWriter.writeInt32((int) value);
                }
            }
            case LONG -> {
                // Proto3 JSON spec: int64/uint64/sint64/sfixed64/fixed64 are quoted
                var type = fd.getType();
                if (type == FieldDescriptor.Type.UINT64 || type == FieldDescriptor.Type.FIXED64) {
                    jsonWriter.writeString(Long.toUnsignedString((long) value));
                } else {
                    jsonWriter.writeString(Long.toString((long) value));
                }
            }
            case FLOAT -> writeFloatValue(jsonWriter, (float) value);
            case DOUBLE -> writeDoubleValue(jsonWriter, (double) value);
            case BOOLEAN -> jsonWriter.writeBool((boolean) value);
            case STRING -> jsonWriter.writeString((String) value);
            case BYTE_STRING -> {
                ByteString bytes = (ByteString) value;
                jsonWriter.writeString(Base64.getEncoder().encodeToString(bytes.toByteArray()));
            }
            case ENUM -> {
                if (value instanceof EnumValueDescriptor enumValue) {
                    jsonWriter.writeString(enumValue.getName());
                } else {
                    // Map values may return Integer for enum fields
                    int enumNumber = (int) value;
                    var enumDesc = fd.getEnumType().findValueByNumber(enumNumber);
                    jsonWriter.writeString(enumDesc != null ? enumDesc.getName() : String.valueOf(enumNumber));
                }
            }
            case MESSAGE -> {
                Message msg = (Message) value;
                if (WellKnownTypes.isWellKnownType(msg.getDescriptorForType())) {
                    WellKnownTypes.write(jsonWriter, msg);
                } else {
                    ProtobufMessageWriter.INSTANCE.writeMessage(jsonWriter, msg);
                }
            }
        }
    }

    static void writeFloatValue(JSONWriter jsonWriter, float value) {
        if (Float.isNaN(value)) {
            jsonWriter.writeString("NaN");
        } else if (Float.isInfinite(value)) {
            jsonWriter.writeString(value > 0 ? "Infinity" : "-Infinity");
        } else {
            jsonWriter.writeFloat(value);
        }
    }

    static void writeDoubleValue(JSONWriter jsonWriter, double value) {
        if (Double.isNaN(value)) {
            jsonWriter.writeString("NaN");
        } else if (Double.isInfinite(value)) {
            jsonWriter.writeString(value > 0 ? "Infinity" : "-Infinity");
        } else {
            jsonWriter.writeDouble(value);
        }
    }

    public static void writeRepeated(JSONWriter jsonWriter, FieldDescriptor fd, List<?> values) {
        jsonWriter.startArray();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) jsonWriter.writeComma();
            writeValue(jsonWriter, fd, values.get(i));
        }
        jsonWriter.endArray();
    }

    public static void writeMap(JSONWriter jsonWriter, FieldDescriptor fd, List<?> entries) {
        var valueDescriptor = fd.getMessageType().findFieldByName("value");
        jsonWriter.startObject();
        for (Object entry : entries) {
            MapEntry<?, ?> mapEntry = (MapEntry<?, ?>) entry;
            // writeName handles comma + colon automatically in fastjson2
            jsonWriter.writeName(mapEntry.getKey().toString());
            jsonWriter.writeColon();
            writeValue(jsonWriter, valueDescriptor, mapEntry.getValue());
        }
        jsonWriter.endObject();
    }
}
