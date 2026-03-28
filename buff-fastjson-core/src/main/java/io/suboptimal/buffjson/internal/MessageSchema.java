package io.suboptimal.buffjson.internal;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

import java.util.concurrent.ConcurrentHashMap;

public final class MessageSchema {

    private static final ConcurrentHashMap<Descriptor, MessageSchema> CACHE = new ConcurrentHashMap<>();

    private final FieldInfo[] fields;

    private MessageSchema(Descriptor descriptor) {
        var fieldDescriptors = descriptor.getFields();
        this.fields = new FieldInfo[fieldDescriptors.size()];
        for (int i = 0; i < fieldDescriptors.size(); i++) {
            this.fields[i] = new FieldInfo(fieldDescriptors.get(i));
        }
    }

    public static MessageSchema forDescriptor(Descriptor descriptor) {
        return CACHE.computeIfAbsent(descriptor, MessageSchema::new);
    }

    public FieldInfo[] fields() {
        return fields;
    }

    public static final class FieldInfo {
        private final FieldDescriptor descriptor;
        private final String jsonName;
        private final FieldDescriptor.JavaType javaType;
        private final boolean isRepeated;
        private final boolean isMapField;
        private final boolean hasPresence;

        FieldInfo(FieldDescriptor fd) {
            this.descriptor = fd;
            this.jsonName = fd.getJsonName();
            this.javaType = fd.getJavaType();
            this.isRepeated = fd.isRepeated();
            this.isMapField = fd.isMapField();
            this.hasPresence = fd.hasPresence();
        }

        public FieldDescriptor descriptor() { return descriptor; }
        public String jsonName() { return jsonName; }
        public FieldDescriptor.JavaType javaType() { return javaType; }
        public boolean isRepeated() { return isRepeated; }
        public boolean isMapField() { return isMapField; }
        public boolean hasPresence() { return hasPresence; }
    }
}
