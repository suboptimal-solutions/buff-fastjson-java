package io.suboptimal.buffjson.internal.typed;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.Message;

/**
 * Creates {@link TypedFieldAccessor} instances for protobuf fields using
 * {@link LambdaMetafactory}. Each accessor calls the typed getter directly
 * (e.g., {@code message.getAge()}) without reflection or boxing.
 */
public final class TypedFieldAccessorFactory {

	private TypedFieldAccessorFactory() {
	}

	/**
	 * Creates a typed accessor for a non-oneof field. Returns {@code null} if the
	 * getter cannot be resolved (e.g., for DynamicMessage).
	 */
	public static TypedFieldAccessor create(FieldDescriptor fd, Class<? extends Message> messageClass) {
		try {
			if (fd.isMapField()) {
				return createMapAccessor(fd, messageClass);
			} else if (fd.isRepeated()) {
				return createRepeatedAccessor(fd, messageClass);
			} else if (fd.hasPresence()) {
				return createPresenceAccessor(fd, messageClass);
			} else {
				return createImplicitPresenceAccessor(fd, messageClass);
			}
		} catch (Throwable e) {
			return null;
		}
	}

	/**
	 * Creates typed accessors for all fields in a oneof group. Returns an array
	 * where each element corresponds to a field in the oneof. Returns {@code null}
	 * if resolution fails.
	 */
	public static TypedFieldAccessor[] createOneofAccessors(OneofDescriptor oneof,
			Class<? extends Message> messageClass) {
		try {
			var fields = oneof.getFields();
			var accessors = new TypedFieldAccessor[fields.size()];
			for (int i = 0; i < fields.size(); i++) {
				FieldDescriptor fd = fields.get(i);
				accessors[i] = createPresenceAccessor(fd, messageClass);
				if (accessors[i] == null)
					return null;
			}
			return accessors;
		} catch (Throwable e) {
			return null;
		}
	}

	// --- Implicit presence (proto3 scalar default-value check) ---

	private static TypedFieldAccessor createImplicitPresenceAccessor(FieldDescriptor fd,
			Class<? extends Message> messageClass) throws Throwable {
		FieldName name = fieldName(fd.getJsonName());
		String getterName = "get" + toCamelCase(fd.getName());

		return switch (fd.getJavaType()) {
			case INT -> {
				var getter = createIntGetter(messageClass, getterName);
				yield new TypedFieldAccessor.IntAccessor(getter, isUnsigned32(fd), name);
			}
			case LONG -> {
				var getter = createLongGetter(messageClass, getterName);
				yield new TypedFieldAccessor.LongAccessor(getter, isUnsigned64(fd), name);
			}
			case FLOAT -> {
				var getter = createFloatAsDoubleGetter(messageClass, getterName);
				yield new TypedFieldAccessor.FloatAccessor(getter, name);
			}
			case DOUBLE -> {
				var getter = createDoubleGetter(messageClass, getterName);
				yield new TypedFieldAccessor.DoubleAccessor(getter, name);
			}
			case BOOLEAN -> {
				var getter = createPredicate(messageClass, getterName);
				yield new TypedFieldAccessor.BoolAccessor(getter, name);
			}
			case STRING -> {
				var getter = createObjectGetter(messageClass, getterName);
				yield new TypedFieldAccessor.StringAccessor(castFunction(getter), name);
			}
			case BYTE_STRING -> {
				var getter = createObjectGetter(messageClass, getterName);
				yield new TypedFieldAccessor.ByteStringAccessor(castFunction(getter), name);
			}
			case ENUM -> {
				var valueGetter = createIntGetter(messageClass, getterName + "Value");
				String[] names = buildEnumNames(fd.getEnumType());
				yield new TypedFieldAccessor.EnumAccessor(valueGetter, names, name);
			}
			case MESSAGE -> createPresenceAccessor(fd, messageClass);
		};
	}

	// --- Explicit presence (has-getter) ---

	private static TypedFieldAccessor createPresenceAccessor(FieldDescriptor fd, Class<? extends Message> messageClass)
			throws Throwable {
		FieldName name = fieldName(fd.getJsonName());
		String getterName = "get" + toCamelCase(fd.getName());
		String hasName = "has" + toCamelCase(fd.getName());

		return switch (fd.getJavaType()) {
			case INT -> {
				var getter = createIntGetter(messageClass, getterName);
				var has = createPredicate(messageClass, hasName);
				yield new TypedFieldAccessor.PresenceIntAccessor(getter, has, isUnsigned32(fd), name);
			}
			case LONG -> {
				var getter = createLongGetter(messageClass, getterName);
				var has = createPredicate(messageClass, hasName);
				yield new TypedFieldAccessor.PresenceLongAccessor(getter, has, isUnsigned64(fd), name);
			}
			case FLOAT -> {
				var getter = createFloatAsDoubleGetter(messageClass, getterName);
				var has = createPredicate(messageClass, hasName);
				yield new TypedFieldAccessor.PresenceFloatAccessor(getter, has, name);
			}
			case DOUBLE -> {
				var getter = createDoubleGetter(messageClass, getterName);
				var has = createPredicate(messageClass, hasName);
				yield new TypedFieldAccessor.PresenceDoubleAccessor(getter, has, name);
			}
			case BOOLEAN -> {
				var getter = createPredicate(messageClass, getterName);
				var has = createPredicate(messageClass, hasName);
				yield new TypedFieldAccessor.PresenceBoolAccessor(getter, has, name);
			}
			case STRING -> {
				var getter = createObjectGetter(messageClass, getterName);
				var has = createPredicate(messageClass, hasName);
				yield new TypedFieldAccessor.PresenceStringAccessor(castFunction(getter), has, name);
			}
			case BYTE_STRING -> {
				var getter = createObjectGetter(messageClass, getterName);
				var has = createPredicate(messageClass, hasName);
				yield new TypedFieldAccessor.PresenceByteStringAccessor(castFunction(getter), has, name);
			}
			case ENUM -> {
				var valueGetter = createIntGetter(messageClass, getterName + "Value");
				var has = createPredicate(messageClass, hasName);
				String[] names = buildEnumNames(fd.getEnumType());
				yield new TypedFieldAccessor.PresenceEnumAccessor(valueGetter, has, names, name);
			}
			case MESSAGE -> {
				var getter = createObjectGetter(messageClass, getterName);
				var has = createPredicate(messageClass, hasName);
				yield new TypedFieldAccessor.PresenceMessageAccessor(castFunction(getter), has, name);
			}
		};
	}

	// --- Repeated ---

	@SuppressWarnings("unchecked")
	private static TypedFieldAccessor createRepeatedAccessor(FieldDescriptor fd, Class<? extends Message> messageClass)
			throws Throwable {
		FieldName name = fieldName(fd.getJsonName());
		if (fd.getJavaType() == FieldDescriptor.JavaType.ENUM) {
			// Use ValueList getter (returns List<Integer>) to handle UNRECOGNIZED enums
			String valueListGetterName = "get" + toCamelCase(fd.getName()) + "ValueList";
			var listGetter = (Function<Message, List<?>>) (Function<?, ?>) createObjectGetter(messageClass,
					valueListGetterName);
			String[] names = buildEnumNames(fd.getEnumType());
			return new TypedFieldAccessor.RepeatedEnumAccessor(listGetter, names, name);
		}
		String listGetterName = "get" + toCamelCase(fd.getName()) + "List";
		var listGetter = (Function<Message, List<?>>) (Function<?, ?>) createObjectGetter(messageClass, listGetterName);
		return new TypedFieldAccessor.RepeatedAccessor(listGetter, fd, name);
	}

	// --- Map ---
	// Tier 4a uses getField(fd) reflection for map entries — Tier 4b will add
	// a typed map accessor backed by getXxxMap()/getXxxValueMap().

	private static TypedFieldAccessor createMapAccessor(FieldDescriptor fd, Class<? extends Message> messageClass) {
		FieldName name = fieldName(fd.getJsonName());
		FieldDescriptor valueFd = fd.getMessageType().findFieldByName("value");
		Function<Message, List<?>> entriesGetter = msg -> (List<?>) msg.getField(fd);
		return new TypedFieldAccessor.MapAccessor(entriesGetter, valueFd, name);
	}

	// --- LambdaMetafactory helpers ---

	private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

	@SuppressWarnings("unchecked")
	private static ToIntFunction<Message> createIntGetter(Class<? extends Message> msgClass, String methodName)
			throws Throwable {
		Method method = msgClass.getMethod(methodName);
		MethodHandle handle = LOOKUP.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(LOOKUP, "applyAsInt", MethodType.methodType(ToIntFunction.class),
				MethodType.methodType(int.class, Object.class), handle, MethodType.methodType(int.class, msgClass));
		return (ToIntFunction<Message>) site.getTarget().invoke();
	}

	@SuppressWarnings("unchecked")
	private static ToLongFunction<Message> createLongGetter(Class<? extends Message> msgClass, String methodName)
			throws Throwable {
		Method method = msgClass.getMethod(methodName);
		MethodHandle handle = LOOKUP.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(LOOKUP, "applyAsLong",
				MethodType.methodType(ToLongFunction.class), MethodType.methodType(long.class, Object.class), handle,
				MethodType.methodType(long.class, msgClass));
		return (ToLongFunction<Message>) site.getTarget().invoke();
	}

	/**
	 * Creates a ToDoubleFunction that calls a float-returning getter, widening the
	 * result to double. Java has no ToFloatFunction, so we use ToDoubleFunction and
	 * cast back to float in the accessor.
	 */
	@SuppressWarnings("unchecked")
	private static ToDoubleFunction<Message> createFloatAsDoubleGetter(Class<? extends Message> msgClass,
			String methodName) throws Throwable {
		Method method = msgClass.getMethod(methodName);
		MethodHandle handle = LOOKUP.unreflect(method);
		MethodHandle adapted = MethodHandles.explicitCastArguments(handle,
				MethodType.methodType(double.class, msgClass));
		CallSite site = LambdaMetafactory.metafactory(LOOKUP, "applyAsDouble",
				MethodType.methodType(ToDoubleFunction.class), MethodType.methodType(double.class, Object.class),
				adapted, MethodType.methodType(double.class, msgClass));
		return (ToDoubleFunction<Message>) site.getTarget().invoke();
	}

	@SuppressWarnings("unchecked")
	private static ToDoubleFunction<Message> createDoubleGetter(Class<? extends Message> msgClass, String methodName)
			throws Throwable {
		Method method = msgClass.getMethod(methodName);
		MethodHandle handle = LOOKUP.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(LOOKUP, "applyAsDouble",
				MethodType.methodType(ToDoubleFunction.class), MethodType.methodType(double.class, Object.class),
				handle, MethodType.methodType(double.class, msgClass));
		return (ToDoubleFunction<Message>) site.getTarget().invoke();
	}

	@SuppressWarnings("unchecked")
	private static Predicate<Message> createPredicate(Class<? extends Message> msgClass, String methodName)
			throws Throwable {
		Method method = msgClass.getMethod(methodName);
		MethodHandle handle = LOOKUP.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(LOOKUP, "test", MethodType.methodType(Predicate.class),
				MethodType.methodType(boolean.class, Object.class), handle,
				MethodType.methodType(boolean.class, msgClass));
		return (Predicate<Message>) site.getTarget().invoke();
	}

	@SuppressWarnings("unchecked")
	private static Function<Message, Object> createObjectGetter(Class<? extends Message> msgClass, String methodName)
			throws Throwable {
		Method method = msgClass.getMethod(methodName);
		MethodHandle handle = LOOKUP.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(LOOKUP, "apply", MethodType.methodType(Function.class),
				MethodType.methodType(Object.class, Object.class), handle,
				MethodType.methodType(method.getReturnType(), msgClass));
		return (Function<Message, Object>) site.getTarget().invoke();
	}

	// --- Utility ---

	@SuppressWarnings("unchecked")
	private static <T> Function<Message, T> castFunction(Function<Message, Object> fn) {
		return (Function<Message, T>) (Function<?, ?>) fn;
	}

	static String toCamelCase(String name) {
		StringBuilder sb = new StringBuilder();
		boolean capitalizeNext = true;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '_') {
				capitalizeNext = true;
			} else {
				sb.append(capitalizeNext ? Character.toUpperCase(c) : c);
				capitalizeNext = false;
			}
		}
		return sb.toString();
	}

	static FieldName fieldName(String jsonName) {
		char[] chars = new char[jsonName.length() + 3];
		chars[0] = '"';
		jsonName.getChars(0, jsonName.length(), chars, 1);
		chars[jsonName.length() + 1] = '"';
		chars[jsonName.length() + 2] = ':';
		// Proto field names are always ASCII, so UTF-8 encoding is trivial
		byte[] utf8 = new byte[jsonName.length() + 3];
		utf8[0] = '"';
		for (int i = 0; i < jsonName.length(); i++)
			utf8[i + 1] = (byte) jsonName.charAt(i);
		utf8[jsonName.length() + 1] = '"';
		utf8[jsonName.length() + 2] = ':';
		return new FieldName(chars, utf8);
	}

	private static boolean isUnsigned32(FieldDescriptor fd) {
		var type = fd.getType();
		return type == FieldDescriptor.Type.UINT32 || type == FieldDescriptor.Type.FIXED32;
	}

	private static boolean isUnsigned64(FieldDescriptor fd) {
		var type = fd.getType();
		return type == FieldDescriptor.Type.UINT64 || type == FieldDescriptor.Type.FIXED64;
	}

	static String[] buildEnumNames(EnumDescriptor enumType) {
		int max = 0;
		for (EnumValueDescriptor v : enumType.getValues()) {
			if (v.getNumber() > max)
				max = v.getNumber();
		}
		String[] names = new String[max + 1];
		for (EnumValueDescriptor v : enumType.getValues()) {
			names[v.getNumber()] = v.getName();
		}
		return names;
	}
}
