package io.suboptimal.buffjson;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import io.suboptimal.buffjson.internal.ProtobufWriterModule;

public final class BuffJSON {

    static {
        JSONFactory.getDefaultObjectWriterProvider().register(ProtobufWriterModule.INSTANCE);
    }

    private BuffJSON() {}

    public static String encode(MessageOrBuilder message) {
        Message msg;
        if (message instanceof Message m) {
            msg = m;
        } else {
            msg = ((Message.Builder) message).buildPartial();
        }
        return JSON.toJSONString(msg);
    }
}
