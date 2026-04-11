package io.suboptimal.buffjson;

import com.google.protobuf.Message;

/**
 * Interface injected into protobuf message classes via protoc
 * {@code message_implements} and {@code class_scope} insertion points. Provides
 * direct access to the generated encoder and decoder for a message type.
 *
 * <p>
 * At runtime, {@code message instanceof BuffJsonCodecHolder} is used by
 * {@link io.suboptimal.buffjson.internal.ProtobufMessageWriter} and
 * {@link io.suboptimal.buffjson.internal.ProtobufMessageReader} to discover
 * generated codecs with zero overhead — no reflection, no ServiceLoader.
 *
 * <p>
 * The protoc plugin generates the implementation: each message class gets
 * {@code buffJsonEncoder()} returning its {@code *JsonEncoder.INSTANCE} and
 * {@code buffJsonDecoder()} returning its {@code *JsonDecoder.INSTANCE}.
 */
public interface BuffJsonCodecHolder {

	/**
	 * Returns the generated JSON encoder for this message type.
	 */
	BuffJsonGeneratedEncoder<? extends Message> buffJsonEncoder();

	/**
	 * Returns the generated JSON decoder for this message type.
	 */
	BuffJsonGeneratedDecoder<? extends Message> buffJsonDecoder();
}
