package io.suboptimal.buffjson.internal;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

import java.lang.reflect.Type;
import java.util.List;

public final class ProtobufMessageWriter implements ObjectWriter<Message> {

    public static final ProtobufMessageWriter INSTANCE = new ProtobufMessageWriter();

    private ProtobufMessageWriter() {}

    @Override
    public void write(JSONWriter jsonWriter, Object object, Object fieldName, Type fieldType, long features) {
        if (object == null) {
            jsonWriter.writeNull();
            return;
        }
        writeMessage(jsonWriter, (Message) object);
    }

    public void writeMessage(JSONWriter jsonWriter, Message message) {
        var schema = MessageSchema.forDescriptor(message.getDescriptorForType());
        var fields = schema.fields();

        jsonWriter.startObject();

        for (var fieldInfo : fields) {
            FieldDescriptor fd = fieldInfo.descriptor();

            if (fieldInfo.isMapField()) {
                List<?> entries = (List<?>) message.getField(fd);
                if (entries.isEmpty()) continue;
                jsonWriter.writeName(fieldInfo.jsonName());
                jsonWriter.writeColon();
                FieldWriter.writeMap(jsonWriter, fd, entries);
            } else if (fieldInfo.isRepeated()) {
                List<?> values = (List<?>) message.getField(fd);
                if (values.isEmpty()) continue;
                jsonWriter.writeName(fieldInfo.jsonName());
                jsonWriter.writeColon();
                FieldWriter.writeRepeated(jsonWriter, fd, values);
            } else {
                if (fieldInfo.hasPresence()) {
                    if (!message.hasField(fd)) continue;
                } else {
                    Object value = message.getField(fd);
                    if (isDefaultValue(fieldInfo, value)) continue;
                    jsonWriter.writeName(fieldInfo.jsonName());
                    jsonWriter.writeColon();
                    FieldWriter.writeValue(jsonWriter, fd, value);
                    continue;
                }
                jsonWriter.writeName(fieldInfo.jsonName());
                jsonWriter.writeColon();
                FieldWriter.writeValue(jsonWriter, fd, message.getField(fd));
            }
        }

        jsonWriter.endObject();
    }

    private static boolean isDefaultValue(MessageSchema.FieldInfo fieldInfo, Object value) {
        return switch (fieldInfo.javaType()) {
            case INT -> (int) value == 0;
            case LONG -> (long) value == 0L;
            case FLOAT -> Float.floatToRawIntBits((float) value) == 0;
            case DOUBLE -> Double.doubleToRawLongBits((double) value) == 0;
            case BOOLEAN -> !(boolean) value;
            case STRING -> ((String) value).isEmpty();
            case BYTE_STRING -> value.equals(com.google.protobuf.ByteString.EMPTY);
            case ENUM -> ((com.google.protobuf.Descriptors.EnumValueDescriptor) value).getNumber() == 0;
            case MESSAGE -> false;
        };
    }
}
