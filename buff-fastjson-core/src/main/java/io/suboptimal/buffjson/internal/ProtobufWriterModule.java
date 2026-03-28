package io.suboptimal.buffjson.internal;

import com.alibaba.fastjson2.modules.ObjectWriterModule;
import com.alibaba.fastjson2.writer.ObjectWriter;
import com.google.protobuf.Message;

import java.lang.reflect.Type;

public final class ProtobufWriterModule implements ObjectWriterModule {

    public static final ProtobufWriterModule INSTANCE = new ProtobufWriterModule();

    private ProtobufWriterModule() {}

    @Override
    public ObjectWriter<?> getObjectWriter(Type objectType, Class objectClass) {
        if (objectClass != null && Message.class.isAssignableFrom(objectClass)) {
            return ProtobufMessageWriter.INSTANCE;
        }
        return null;
    }
}
