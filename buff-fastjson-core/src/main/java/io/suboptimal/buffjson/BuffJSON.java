package io.suboptimal.buffjson;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;

import io.suboptimal.buffjson.internal.ProtobufWriterModule;

/**
 * Fast JSON serialization for Protocol Buffer messages.
 *
 * <p>
 * Main entry point for the buff-fastjson library. Produces JSON output
 * compliant with the
 * <a href="https://protobuf.dev/programming-guides/proto3/#json">Proto3 JSON
 * spec</a>, matching {@code
 * JsonFormat.printer().omittingInsignificantWhitespace().print()} output
 * exactly.
 *
 * <p>
 * Internally uses <a href="https://github.com/alibaba/fastjson2">fastjson2</a>
 * for JSON writing (buffer management, number formatting, string escaping) with
 * a custom {@link io.suboptimal.buffjson.internal.ProtobufWriterModule} that
 * handles protobuf field extraction.
 *
 * <p>
 * Thread-safe. The fastjson2 module is registered once via a static
 * initializer.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * String json = BuffJSON.encode(myProtoMessage);
 * }</pre>
 */
public final class BuffJSON {

	static {
		JSONFactory.getDefaultObjectWriterProvider().register(ProtobufWriterModule.INSTANCE);
	}

	private BuffJSON() {
	}

	/**
	 * Encodes a Protocol Buffer message to its proto3 JSON representation.
	 *
	 * <p>
	 * Accepts both built {@link Message} instances and {@link Message.Builder}
	 * instances. Builders are converted via {@code buildPartial()} before
	 * serialization.
	 *
	 * @param message
	 *            the protobuf message or builder to encode
	 * @return compact JSON string (no insignificant whitespace)
	 */
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
