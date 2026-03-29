package io.suboptimal.buffjson.benchmarks;

import java.util.Random;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;

import io.suboptimal.buffjson.proto.*;

public final class BenchmarkData {

	private BenchmarkData() {
	}

	// ---- Existing deterministic factories ----

	public static SimpleMessage createSimpleMessage() {
		return SimpleMessage.newBuilder().setName("benchmark-user").setId(42).setTimestampMillis(1711627200000L)
				.setScore(99.95).setActive(true).setStatus(Status.STATUS_ACTIVE).build();
	}

	public static ComplexMessage createComplexMessage() {
		Timestamp now = Timestamp.newBuilder().setSeconds(1711627200L).setNanos(0).build();

		Address primaryAddr = Address.newBuilder().setStreet("123 Main St").setCity("Springfield").setState("IL")
				.setZipCode("62704").setCountry("US").build();

		Address secondaryAddr = Address.newBuilder().setStreet("456 Oak Ave").setCity("Shelbyville").setState("IL")
				.setZipCode("62565").setCountry("US").build();

		return ComplexMessage.newBuilder().setId("msg-001").setName("complex-benchmark").setVersion(1)
				.setPrimaryAddress(primaryAddr).addTagsList("java").addTagsList("protobuf").addTagsList("benchmark")
				.addAddresses(primaryAddr).addAddresses(secondaryAddr)
				.addTags(Tag.newBuilder().setKey("env").setValue("prod").build())
				.addTags(Tag.newBuilder().setKey("region").setValue("us-east").build()).putMetadata("version", "1.0")
				.putMetadata("format", "json").putMetadata("encoding", "utf-8").putAddressBook(1, primaryAddr)
				.setEmail("user@example.com").setPayload(ByteString.copyFromUtf8("binary-payload-data"))
				.setCreatedAt(now).setUpdatedAt(now).setStatus(Status.STATUS_ACTIVE).build();
	}

	// ---- New deterministic factories ----

	public static BenchAllScalars createBenchAllScalars() {
		return BenchAllScalars.newBuilder().setFInt32(42).setFInt64(123456789012345L).setFUint32((int) 4000000000L)
				.setFUint64(9000000000000000000L).setFSint32(-42).setFSint64(-123456789012345L).setFFixed32(100000)
				.setFFixed64(200000000000L).setFSfixed32(-100000).setFSfixed64(-200000000000L).setFFloat(3.14f)
				.setFDouble(2.718281828459045).setFBool(true).setFString("hello-scalars")
				.setFBytes(ByteString.copyFromUtf8("binary-data")).setFEnum(BenchEnum.BENCH_ENUM_TWO).build();
	}

	public static BenchRepeatedHeavy createBenchRepeatedHeavy() {
		BenchRepeatedHeavy.Builder builder = BenchRepeatedHeavy.newBuilder();
		for (int i = 0; i < 100; i++) {
			builder.addInts(i * 7 + 13);
			builder.addStrings("item-" + i);
		}
		for (int i = 0; i < 20; i++) {
			builder.addMessages(BenchAllScalars.newBuilder().setFInt32(i).setFString("msg-" + i).setFBool(i % 2 == 0)
					.setFDouble(i * 1.1).build());
		}
		return builder.build();
	}

	public static BenchMapHeavy createBenchMapHeavy() {
		BenchMapHeavy.Builder builder = BenchMapHeavy.newBuilder();
		for (int i = 0; i < 50; i++) {
			builder.putStringMap("key-" + i, "value-" + i);
			builder.putIntKeyMap(i * 1000L + 1, "val-" + i);
		}
		for (int i = 0; i < 20; i++) {
			builder.putMessageMap("msg-" + i, BenchAllScalars.newBuilder().setFInt32(i).setFString("map-" + i).build());
		}
		return builder.build();
	}

	public static BenchDeepNesting createBenchDeepNesting() {
		BenchDeepNesting current = BenchDeepNesting.newBuilder().setName("leaf").setValue(5).build();
		for (int i = 4; i >= 1; i--) {
			current = BenchDeepNesting.newBuilder().setName("level-" + i).setValue(i).setChild(current).build();
		}
		return current;
	}

	public static BenchStringHeavy createBenchStringHeavy() {
		StringBuilder longAscii = new StringBuilder(1024);
		for (int i = 0; i < 1024; i++) {
			longAscii.append((char) ('a' + (i % 26)));
		}
		StringBuilder escapeHeavy = new StringBuilder(256);
		for (int i = 0; i < 64; i++) {
			escapeHeavy.append("line\t").append(i).append("\n\"quoted\\path\"");
		}
		byte[] largePayload = new byte[4096];
		for (int i = 0; i < largePayload.length; i++) {
			largePayload[i] = (byte) (i & 0xFF);
		}
		return BenchStringHeavy.newBuilder().setShortAscii("hello").setLongAscii(longAscii.toString()).setUnicodeText(
				"\u4f60\u597d\u4e16\u754c \ud83d\ude80 \u00e9\u00e8\u00ea \u03b1\u03b2\u03b3 \u0410\u0411\u0412")
				.setEscapeHeavy(escapeHeavy.toString())
				.setSmallPayload(ByteString.copyFrom(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}))
				.setLargePayload(ByteString.copyFrom(largePayload)).build();
	}

	public static BenchTimestamps createBenchTimestamps() {
		return BenchTimestamps.newBuilder()
				.setTsSecondsOnly(Timestamp.newBuilder().setSeconds(1711627200L).setNanos(0).build())
				.setTsMillis(Timestamp.newBuilder().setSeconds(1711627200L).setNanos(123000000).build())
				.setTsNanos(Timestamp.newBuilder().setSeconds(1711627200L).setNanos(123456789).build()).build();
	}

	public static BenchDurations createBenchDurations() {
		return BenchDurations.newBuilder().setPositive(Duration.newBuilder().setSeconds(3600).setNanos(0).build())
				.setNegative(Duration.newBuilder().setSeconds(-3600).setNanos(-500000000).build())
				.setWithNanos(Duration.newBuilder().setSeconds(1).setNanos(123456789).build()).build();
	}

	public static BenchWrappers createBenchWrappers() {
		return BenchWrappers.newBuilder().setInt32Val(com.google.protobuf.Int32Value.of(42))
				.setInt64Val(com.google.protobuf.Int64Value.of(123456789012345L))
				.setUint32Val(com.google.protobuf.UInt32Value.of((int) 4000000000L))
				.setUint64Val(com.google.protobuf.UInt64Value.of(9000000000000000000L))
				.setFloatVal(com.google.protobuf.FloatValue.of(3.14f))
				.setDoubleVal(com.google.protobuf.DoubleValue.of(2.718281828459045))
				.setBoolVal(com.google.protobuf.BoolValue.of(true))
				.setStringVal(com.google.protobuf.StringValue.of("wrapped-string"))
				.setBytesVal(com.google.protobuf.BytesValue.of(ByteString.copyFromUtf8("wrapped-bytes"))).build();
	}

	public static BenchStruct createBenchStruct() {
		Struct nested = Struct.newBuilder()
				.putFields("inner_key", Value.newBuilder().setStringValue("inner_val").build())
				.putFields("inner_num", Value.newBuilder().setNumberValue(99.9).build()).build();
		ListValue list = ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(1).build())
				.addValues(Value.newBuilder().setStringValue("two").build())
				.addValues(Value.newBuilder().setBoolValue(true).build()).build();
		Struct data = Struct.newBuilder().putFields("string_field", Value.newBuilder().setStringValue("hello").build())
				.putFields("number_field", Value.newBuilder().setNumberValue(42.5).build())
				.putFields("bool_field", Value.newBuilder().setBoolValue(true).build())
				.putFields("null_field", Value.newBuilder().setNullValueValue(0).build())
				.putFields("nested_struct", Value.newBuilder().setStructValue(nested).build())
				.putFields("list_field", Value.newBuilder().setListValue(list).build())
				.putFields("pi", Value.newBuilder().setNumberValue(3.14159265358979).build())
				.putFields("empty_string", Value.newBuilder().setStringValue("").build())
				.putFields("negative", Value.newBuilder().setNumberValue(-273.15).build())
				.putFields("big_number", Value.newBuilder().setNumberValue(1e18).build()).build();
		return BenchStruct.newBuilder().setData(data).build();
	}

	public static BenchAny createBenchAnyWithScalars() {
		BenchAllScalars inner = createBenchAllScalars();
		return BenchAny.newBuilder().setValue(Any.pack(inner)).build();
	}

	public static BenchAny createBenchAnyWithTimestamp() {
		Timestamp inner = Timestamp.newBuilder().setSeconds(1711627200L).setNanos(123456789).build();
		return BenchAny.newBuilder().setValue(Any.pack(inner)).build();
	}

	// ---- Random factories ----

	private static final int BENCH_ENUM_COUNT = BenchEnum.values().length - 1; // exclude UNRECOGNIZED
	private static final int STATUS_COUNT = Status.values().length - 1; // exclude UNRECOGNIZED

	public static SimpleMessage[] createRandomSimpleMessages(Random rng, int n) {
		SimpleMessage[] result = new SimpleMessage[n];
		for (int i = 0; i < n; i++) {
			SimpleMessage.Builder b = SimpleMessage.newBuilder();
			if (rng.nextBoolean())
				b.setName(randomAscii(rng, 5 + rng.nextInt(20)));
			if (rng.nextBoolean())
				b.setId(rng.nextInt());
			if (rng.nextBoolean())
				b.setTimestampMillis(rng.nextLong());
			if (rng.nextBoolean())
				b.setScore(rng.nextDouble() * 1000);
			if (rng.nextBoolean())
				b.setActive(rng.nextBoolean());
			if (rng.nextBoolean())
				b.setStatus(Status.forNumber(rng.nextInt(STATUS_COUNT)));
			result[i] = b.build();
		}
		return result;
	}

	public static ComplexMessage[] createRandomComplexMessages(Random rng, int n) {
		ComplexMessage[] result = new ComplexMessage[n];
		for (int i = 0; i < n; i++) {
			ComplexMessage.Builder b = ComplexMessage.newBuilder();
			b.setId(randomAscii(rng, 5 + rng.nextInt(10)));
			b.setName(randomAscii(rng, 5 + rng.nextInt(20)));
			b.setVersion(rng.nextInt(100));
			b.setPrimaryAddress(randomAddress(rng));
			int tagCount = 1 + rng.nextInt(5);
			for (int j = 0; j < tagCount; j++) {
				b.addTagsList(randomAscii(rng, 3 + rng.nextInt(10)));
			}
			int addrCount = 1 + rng.nextInt(3);
			for (int j = 0; j < addrCount; j++) {
				b.addAddresses(randomAddress(rng));
			}
			b.addTags(Tag.newBuilder().setKey(randomAscii(rng, 3)).setValue(randomAscii(rng, 5)).build());
			b.putMetadata(randomAscii(rng, 5), randomAscii(rng, 10));
			b.putAddressBook(rng.nextInt(100), randomAddress(rng));
			if (rng.nextBoolean()) {
				b.setEmail(randomAscii(rng, 8) + "@example.com");
			} else {
				b.setPhone("+1" + (1000000000L + rng.nextInt(900000000)));
			}
			b.setPayload(randomBytes(rng, 10 + rng.nextInt(50)));
			b.setCreatedAt(randomTimestamp(rng));
			b.setUpdatedAt(randomTimestamp(rng));
			b.setStatus(Status.forNumber(rng.nextInt(STATUS_COUNT)));
			result[i] = b.build();
		}
		return result;
	}

	public static BenchAllScalars[] createRandomBenchAllScalars(Random rng, int n) {
		BenchAllScalars[] result = new BenchAllScalars[n];
		for (int i = 0; i < n; i++) {
			BenchAllScalars.Builder b = BenchAllScalars.newBuilder();
			// Randomly leave some fields at default to exercise both branches
			if (rng.nextBoolean())
				b.setFInt32(rng.nextInt());
			if (rng.nextBoolean())
				b.setFInt64(rng.nextLong());
			if (rng.nextBoolean())
				b.setFUint32(rng.nextInt());
			if (rng.nextBoolean())
				b.setFUint64(rng.nextLong());
			if (rng.nextBoolean())
				b.setFSint32(rng.nextInt());
			if (rng.nextBoolean())
				b.setFSint64(rng.nextLong());
			if (rng.nextBoolean())
				b.setFFixed32(rng.nextInt());
			if (rng.nextBoolean())
				b.setFFixed64(rng.nextLong());
			if (rng.nextBoolean())
				b.setFSfixed32(rng.nextInt());
			if (rng.nextBoolean())
				b.setFSfixed64(rng.nextLong());
			if (rng.nextBoolean())
				b.setFFloat(rng.nextFloat() * 1000 - 500);
			if (rng.nextBoolean())
				b.setFDouble(rng.nextDouble() * 1e10 - 5e9);
			if (rng.nextBoolean())
				b.setFBool(true);
			if (rng.nextBoolean())
				b.setFString(randomAscii(rng, 5 + rng.nextInt(20)));
			if (rng.nextBoolean())
				b.setFBytes(randomBytes(rng, 4 + rng.nextInt(32)));
			if (rng.nextBoolean())
				b.setFEnum(BenchEnum.forNumber(rng.nextInt(BENCH_ENUM_COUNT)));
			result[i] = b.build();
		}
		return result;
	}

	public static BenchTimestamps[] createRandomBenchTimestamps(Random rng, int n) {
		BenchTimestamps[] result = new BenchTimestamps[n];
		for (int i = 0; i < n; i++) {
			result[i] = BenchTimestamps.newBuilder().setTsSecondsOnly(randomTimestamp(rng, 0))
					.setTsMillis(randomTimestamp(rng, 3)).setTsNanos(randomTimestamp(rng, 9)).build();
		}
		return result;
	}

	public static BenchDurations[] createRandomBenchDurations(Random rng, int n) {
		BenchDurations[] result = new BenchDurations[n];
		for (int i = 0; i < n; i++) {
			long posSec = rng.nextInt(100000);
			long negSec = -(1 + rng.nextInt(100000));
			int negNanos = -(rng.nextInt(999999999));
			int nanos = rng.nextInt(999999999);
			result[i] = BenchDurations.newBuilder().setPositive(Duration.newBuilder().setSeconds(posSec).build())
					.setNegative(Duration.newBuilder().setSeconds(negSec).setNanos(negNanos).build())
					.setWithNanos(Duration.newBuilder().setSeconds(rng.nextInt(10000)).setNanos(nanos).build()).build();
		}
		return result;
	}

	public static BenchWrappers[] createRandomBenchWrappers(Random rng, int n) {
		BenchWrappers[] result = new BenchWrappers[n];
		for (int i = 0; i < n; i++) {
			result[i] = BenchWrappers.newBuilder().setInt32Val(com.google.protobuf.Int32Value.of(rng.nextInt()))
					.setInt64Val(com.google.protobuf.Int64Value.of(rng.nextLong()))
					.setUint32Val(com.google.protobuf.UInt32Value.of(rng.nextInt()))
					.setUint64Val(com.google.protobuf.UInt64Value.of(rng.nextLong()))
					.setFloatVal(com.google.protobuf.FloatValue.of(rng.nextFloat() * 1000))
					.setDoubleVal(com.google.protobuf.DoubleValue.of(rng.nextDouble() * 1e10))
					.setBoolVal(com.google.protobuf.BoolValue.of(rng.nextBoolean()))
					.setStringVal(com.google.protobuf.StringValue.of(randomAscii(rng, 5 + rng.nextInt(20))))
					.setBytesVal(com.google.protobuf.BytesValue.of(randomBytes(rng, 4 + rng.nextInt(32)))).build();
		}
		return result;
	}

	public static BenchStruct[] createRandomBenchStructs(Random rng, int n) {
		BenchStruct[] result = new BenchStruct[n];
		for (int i = 0; i < n; i++) {
			result[i] = BenchStruct.newBuilder().setData(randomStruct(rng, 3 + rng.nextInt(13), 2)).build();
		}
		return result;
	}

	public static BenchRepeatedHeavy[] createRandomBenchRepeatedHeavy(Random rng, int n) {
		BenchRepeatedHeavy[] result = new BenchRepeatedHeavy[n];
		for (int i = 0; i < n; i++) {
			BenchRepeatedHeavy.Builder b = BenchRepeatedHeavy.newBuilder();
			int intCount = 50 + rng.nextInt(151);
			for (int j = 0; j < intCount; j++) {
				b.addInts(rng.nextInt());
			}
			int strCount = 50 + rng.nextInt(151);
			for (int j = 0; j < strCount; j++) {
				b.addStrings(randomAscii(rng, 3 + rng.nextInt(15)));
			}
			int msgCount = 10 + rng.nextInt(21);
			for (int j = 0; j < msgCount; j++) {
				b.addMessages(BenchAllScalars.newBuilder().setFInt32(rng.nextInt()).setFString(randomAscii(rng, 5))
						.setFBool(rng.nextBoolean()).setFDouble(rng.nextDouble() * 100).build());
			}
			result[i] = b.build();
		}
		return result;
	}

	public static BenchMapHeavy[] createRandomBenchMapHeavy(Random rng, int n) {
		BenchMapHeavy[] result = new BenchMapHeavy[n];
		for (int i = 0; i < n; i++) {
			BenchMapHeavy.Builder b = BenchMapHeavy.newBuilder();
			int strMapSize = 20 + rng.nextInt(61);
			for (int j = 0; j < strMapSize; j++) {
				b.putStringMap(randomAscii(rng, 5 + rng.nextInt(10)), randomAscii(rng, 5 + rng.nextInt(20)));
			}
			int intMapSize = 20 + rng.nextInt(61);
			for (int j = 0; j < intMapSize; j++) {
				b.putIntKeyMap(rng.nextLong(), randomAscii(rng, 5 + rng.nextInt(15)));
			}
			int msgMapSize = 10 + rng.nextInt(21);
			for (int j = 0; j < msgMapSize; j++) {
				b.putMessageMap(randomAscii(rng, 5),
						BenchAllScalars.newBuilder().setFInt32(rng.nextInt()).setFString(randomAscii(rng, 5)).build());
			}
			result[i] = b.build();
		}
		return result;
	}

	public static BenchDeepNesting[] createRandomBenchDeepNesting(Random rng, int n) {
		BenchDeepNesting[] result = new BenchDeepNesting[n];
		for (int i = 0; i < n; i++) {
			int depth = 3 + rng.nextInt(5);
			BenchDeepNesting current = BenchDeepNesting.newBuilder().setName(randomAscii(rng, 5))
					.setValue(rng.nextInt(1000)).build();
			for (int d = depth - 1; d >= 1; d--) {
				current = BenchDeepNesting.newBuilder().setName(randomAscii(rng, 3 + rng.nextInt(8)))
						.setValue(rng.nextInt(1000)).setChild(current).build();
			}
			result[i] = current;
		}
		return result;
	}

	public static BenchStringHeavy[] createRandomBenchStringHeavy(Random rng, int n) {
		BenchStringHeavy[] result = new BenchStringHeavy[n];
		for (int i = 0; i < n; i++) {
			int longLen = 100 + rng.nextInt(1901);
			StringBuilder longStr = new StringBuilder(longLen);
			for (int j = 0; j < longLen; j++) {
				longStr.append((char) ('a' + rng.nextInt(26)));
			}
			int escapeLen = 50 + rng.nextInt(201);
			StringBuilder escapeStr = new StringBuilder(escapeLen * 4);
			for (int j = 0; j < escapeLen; j++) {
				switch (rng.nextInt(5)) {
					case 0 -> escapeStr.append('\n');
					case 1 -> escapeStr.append('\t');
					case 2 -> escapeStr.append('"');
					case 3 -> escapeStr.append('\\');
					default -> escapeStr.append(randomAscii(rng, 3));
				}
			}
			String[] unicodeFragments = {"\u4f60\u597d", "\u4e16\u754c", "\ud83d\ude80", "\ud83c\udf1f",
					"\u00e9\u00e8\u00ea", "\u03b1\u03b2\u03b3", "\u0410\u0411\u0412", "\u2603", "\u2764",
					"\ud83c\udf89"};
			StringBuilder unicode = new StringBuilder();
			int uniCount = 5 + rng.nextInt(16);
			for (int j = 0; j < uniCount; j++) {
				unicode.append(unicodeFragments[rng.nextInt(unicodeFragments.length)]);
				unicode.append(' ');
			}
			result[i] = BenchStringHeavy.newBuilder().setShortAscii(randomAscii(rng, 3 + rng.nextInt(15)))
					.setLongAscii(longStr.toString()).setUnicodeText(unicode.toString())
					.setEscapeHeavy(escapeStr.toString()).setSmallPayload(randomBytes(rng, 8 + rng.nextInt(24)))
					.setLargePayload(randomBytes(rng, 1024 + rng.nextInt(4096))).build();
		}
		return result;
	}

	public static BenchAny[] createRandomBenchAnyWithScalars(Random rng, int n) {
		BenchAllScalars[] scalars = createRandomBenchAllScalars(rng, n);
		BenchAny[] result = new BenchAny[n];
		for (int i = 0; i < n; i++) {
			result[i] = BenchAny.newBuilder().setValue(Any.pack(scalars[i])).build();
		}
		return result;
	}

	public static BenchAny[] createRandomBenchAnyWithTimestamp(Random rng, int n) {
		BenchAny[] result = new BenchAny[n];
		for (int i = 0; i < n; i++) {
			Timestamp ts = randomTimestamp(rng);
			result[i] = BenchAny.newBuilder().setValue(Any.pack(ts)).build();
		}
		return result;
	}

	// ---- Helpers ----

	private static String randomAscii(Random rng, int len) {
		char[] chars = new char[len];
		for (int i = 0; i < len; i++) {
			chars[i] = (char) ('a' + rng.nextInt(26));
		}
		return new String(chars);
	}

	private static ByteString randomBytes(Random rng, int len) {
		byte[] bytes = new byte[len];
		rng.nextBytes(bytes);
		return ByteString.copyFrom(bytes);
	}

	private static Timestamp randomTimestamp(Random rng) {
		long seconds = (long) (rng.nextDouble() * 253402300799L);
		int nanos = rng.nextInt(1000000000);
		return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
	}

	private static Timestamp randomTimestamp(Random rng, int nanoPrecisionDigits) {
		long seconds = (long) (rng.nextDouble() * 253402300799L);
		int nanos;
		if (nanoPrecisionDigits == 0) {
			nanos = 0;
		} else if (nanoPrecisionDigits == 3) {
			nanos = rng.nextInt(1000) * 1000000;
		} else {
			nanos = rng.nextInt(1000000000);
		}
		return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
	}

	private static Address randomAddress(Random rng) {
		return Address.newBuilder().setStreet(rng.nextInt(9999) + " " + randomAscii(rng, 5) + " St")
				.setCity(randomAscii(rng, 8)).setState(randomAscii(rng, 2).toUpperCase())
				.setZipCode(String.valueOf(10000 + rng.nextInt(90000))).setCountry("US").build();
	}

	private static Struct randomStruct(Random rng, int fieldCount, int maxDepth) {
		Struct.Builder sb = Struct.newBuilder();
		for (int i = 0; i < fieldCount; i++) {
			sb.putFields("f_" + i, randomValue(rng, maxDepth));
		}
		return sb.build();
	}

	private static Value randomValue(Random rng, int maxDepth) {
		int kind = (maxDepth > 0) ? rng.nextInt(6) : rng.nextInt(4);
		return switch (kind) {
			case 0 -> Value.newBuilder().setStringValue(randomAscii(rng, 3 + rng.nextInt(15))).build();
			case 1 -> Value.newBuilder().setNumberValue(rng.nextDouble() * 1000 - 500).build();
			case 2 -> Value.newBuilder().setBoolValue(rng.nextBoolean()).build();
			case 3 -> Value.newBuilder().setNullValueValue(0).build();
			case 4 -> Value.newBuilder().setStructValue(randomStruct(rng, 2 + rng.nextInt(4), maxDepth - 1)).build();
			default -> {
				ListValue.Builder lb = ListValue.newBuilder();
				int listSize = 1 + rng.nextInt(4);
				for (int j = 0; j < listSize; j++) {
					lb.addValues(randomValue(rng, maxDepth - 1));
				}
				yield Value.newBuilder().setListValue(lb).build();
			}
		};
	}
}
