package io.suboptimal.buffjson.internal;

import java.lang.reflect.Type;
import java.util.List;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.TypeRegistry;

import io.suboptimal.buffjson.BuffJsonCodecHolder;
import io.suboptimal.buffjson.BuffJsonGeneratedEncoder;
import io.suboptimal.buffjson.internal.typed.TypedMessageSchema;
/**
 * Core serialization logic for protobuf messages. Implements fastjson2's
 * {@link ObjectWriter} to produce proto3-spec-compliant JSON.
 *
 * <p>
 * Holds settings as instance fields ({@code typeRegistry},
 * {@code useGenerated}) and passes {@code this} through the call chain — no
 * ThreadLocals.
 *
 * <p>
 * For each message:
 *
 * <ol>
 * <li>Checks {@code message instanceof BuffJsonCodecHolder} for a generated
 * encoder (injected via protoc insertion points)
 * <li>If found: delegates to the generated encoder's
 * {@code writeFields(jw, msg, writer)} — direct typed accessors, no reflection
 * <li>If not: falls back to the runtime path — looks up the cached
 * {@link MessageSchema}, iterates pre-computed {@link MessageSchema.FieldInfo}
 * array (no {@code getAllFields()} TreeMap allocation), skips default values,
 * delegates value writing to {@link FieldWriter}
 * </ol>
 *
 * <p>
 * Default value detection uses raw bit comparison for float/double (to
 * correctly handle {@code
 * -0.0}).
 */
public final class ProtobufMessageWriter implements ObjectWriter<Message> {

	public static final ProtobufMessageWriter INSTANCE = new ProtobufMessageWriter(null, true);

	private final TypeRegistry typeRegistry;
	private final boolean useGenerated;

	public ProtobufMessageWriter(TypeRegistry typeRegistry, boolean useGenerated) {
		this.typeRegistry = typeRegistry;
		this.useGenerated = useGenerated;
	}

	public TypeRegistry typeRegistry() {
		return typeRegistry;
	}

	@Override
	public void write(JSONWriter jsonWriter, Object object, Object fieldName, Type fieldType, long features) {
		if (object == null) {
			jsonWriter.writeNull();
			return;
		}
		writeMessage(jsonWriter, (Message) object);
	}

	public void writeMessage(JSONWriter jsonWriter, Message message) {
		jsonWriter.startObject();
		writeFields(jsonWriter, message);
		jsonWriter.endObject();
	}

	/**
	 * Writes all non-default fields of a message without the surrounding braces.
	 * Uses a generated encoder if the message implements
	 * {@link BuffJsonCodecHolder}.
	 */
	@SuppressWarnings("unchecked")
	void writeFields(JSONWriter jsonWriter, Message message) {
		if (useGenerated && message instanceof BuffJsonCodecHolder holder) {
			((BuffJsonGeneratedEncoder<Message>) holder.buffJsonEncoder()).writeFields(jsonWriter, message, this);
			return;
		}

		if (!(message instanceof DynamicMessage)) {
			var typed = TypedMessageSchema.forMessage(message.getDescriptorForType(), message.getClass());
			if (typed != null) {
				typed.writeFields(jsonWriter, message, this);
				return;
			}
		}

		var schema = MessageSchema.forDescriptor(message.getDescriptorForType());
		var fields = schema.fields();
		boolean utf8 = jsonWriter.isUTF8();

		for (var fieldInfo : fields) {
			FieldDescriptor fd = fieldInfo.descriptor();

			if (fieldInfo.isMapField()) {
				List<?> entries = (List<?>) message.getField(fd);
				if (entries.isEmpty())
					continue;
				writeName(jsonWriter, fieldInfo, utf8);
				FieldWriter.writeMap(jsonWriter, fieldInfo.mapValueDescriptor(), entries, this);
			} else if (fieldInfo.isRepeated()) {
				List<?> values = (List<?>) message.getField(fd);
				if (values.isEmpty())
					continue;
				writeName(jsonWriter, fieldInfo, utf8);
				FieldWriter.writeRepeated(jsonWriter, fd, values, this);
			} else {
				Object value = message.getField(fd);
				if (fieldInfo.hasPresence()) {
					if (!message.hasField(fd))
						continue;
				} else if (isDefaultValue(fieldInfo, value)) {
					continue;
				}
				writeName(jsonWriter, fieldInfo, utf8);
				FieldWriter.writeValue(jsonWriter, fd, value, this);
			}
		}
	}

	private static void writeName(JSONWriter jsonWriter, MessageSchema.FieldInfo fieldInfo, boolean utf8) {
		if (utf8)
			jsonWriter.writeNameRaw(fieldInfo.nameWithColonUtf8());
		else
			jsonWriter.writeNameRaw(fieldInfo.nameWithColon());
	}

	private static boolean isDefaultValue(MessageSchema.FieldInfo fieldInfo, Object value) {
		return switch (fieldInfo.javaType()) {
			case INT -> (int) value == 0;
			case LONG -> (long) value == 0L;
			case FLOAT -> Float.floatToRawIntBits((float) value) == 0;
			case DOUBLE -> Double.doubleToRawLongBits((double) value) == 0;
			case BOOLEAN -> !(boolean) value;
			case STRING -> ((String) value).isEmpty();
			case BYTE_STRING -> ((com.google.protobuf.ByteString) value).isEmpty();
			case ENUM -> ((com.google.protobuf.Descriptors.EnumValueDescriptor) value).getNumber() == 0;
			case MESSAGE -> false;
		};
	}
}
