package io.suboptimal.buffjson.benchmarks;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import io.suboptimal.buffjson.BuffJson;
import io.suboptimal.buffjson.proto.ComplexMessage;
import io.suboptimal.buffjson.proto.SimpleMessage;

/**
 * Compares BuffJson compiled JSON ser-de against protobuf native binary
 * encoding to quantify the JSON overhead vs the binary "speed of light".
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class ProtoBinaryBenchmark {

	private static final int POOL_SIZE = 1024;
	private static final int MASK = POOL_SIZE - 1;

	private SimpleMessage[] simpleMessages;
	private ComplexMessage[] complexMessages;

	private String[] simpleJsonStrings;
	private byte[][] simpleBinaryBytes;

	private String[] complexJsonStrings;
	private byte[][] complexBinaryBytes;

	private int index;

	@Setup
	public void setup() {
		Random rng = new Random(42);
		simpleMessages = BenchmarkData.createRandomSimpleMessages(rng, POOL_SIZE);
		complexMessages = BenchmarkData.createRandomComplexMessages(new Random(42), POOL_SIZE);

		simpleJsonStrings = new String[POOL_SIZE];
		simpleBinaryBytes = new byte[POOL_SIZE][];
		complexJsonStrings = new String[POOL_SIZE];
		complexBinaryBytes = new byte[POOL_SIZE][];

		for (int i = 0; i < POOL_SIZE; i++) {
			simpleJsonStrings[i] = BuffJson.encode(simpleMessages[i]);
			simpleBinaryBytes[i] = simpleMessages[i].toByteArray();
			complexJsonStrings[i] = BuffJson.encode(complexMessages[i]);
			complexBinaryBytes[i] = complexMessages[i].toByteArray();
		}
	}

	// — Simple message encode —

	@Benchmark
	public String simpleJsonEncode() {
		return BuffJson.encode(simpleMessages[index++ & MASK]);
	}

	@Benchmark
	public byte[] simpleBinaryEncode() {
		return simpleMessages[index++ & MASK].toByteArray();
	}

	// — Complex message encode —

	@Benchmark
	public String complexJsonEncode() {
		return BuffJson.encode(complexMessages[index++ & MASK]);
	}

	@Benchmark
	public byte[] complexBinaryEncode() {
		return complexMessages[index++ & MASK].toByteArray();
	}

	// — Simple message decode —

	@Benchmark
	public SimpleMessage simpleJsonDecode() {
		return BuffJson.decode(simpleJsonStrings[index++ & MASK], SimpleMessage.class);
	}

	@Benchmark
	public SimpleMessage simpleBinaryDecode() throws Exception {
		return SimpleMessage.parseFrom(simpleBinaryBytes[index++ & MASK]);
	}

	// — Complex message decode —

	@Benchmark
	public ComplexMessage complexJsonDecode() {
		return BuffJson.decode(complexJsonStrings[index++ & MASK], ComplexMessage.class);
	}

	@Benchmark
	public ComplexMessage complexBinaryDecode() throws Exception {
		return ComplexMessage.parseFrom(complexBinaryBytes[index++ & MASK]);
	}
}
