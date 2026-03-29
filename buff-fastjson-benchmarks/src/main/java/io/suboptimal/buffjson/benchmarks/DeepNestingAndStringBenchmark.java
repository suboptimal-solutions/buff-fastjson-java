package io.suboptimal.buffjson.benchmarks;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.util.JsonFormat;

import org.openjdk.jmh.annotations.*;

import io.suboptimal.buffjson.BuffJSON;
import io.suboptimal.buffjson.Encoder;
import io.suboptimal.buffjson.proto.BenchDeepNesting;
import io.suboptimal.buffjson.proto.BenchStringHeavy;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class DeepNestingAndStringBenchmark {

	private static final int POOL_SIZE = 1024;
	private static final int MASK = POOL_SIZE - 1;
	private static final JsonFormat.Printer PROTO_PRINTER = JsonFormat.printer();
	private static final Encoder GENERIC_ENCODER = BuffJSON.encoder().withGeneratedEncoders(false);

	private BenchDeepNesting deepNesting;
	private BenchDeepNesting[] randomDeepNesting;
	private BenchStringHeavy stringHeavy;
	private BenchStringHeavy[] randomStringHeavy;
	private int index;

	@Setup
	public void setup() {
		deepNesting = BenchmarkData.createBenchDeepNesting();
		randomDeepNesting = BenchmarkData.createRandomBenchDeepNesting(new Random(42), POOL_SIZE);
		stringHeavy = BenchmarkData.createBenchStringHeavy();
		randomStringHeavy = BenchmarkData.createRandomBenchStringHeavy(new Random(43), POOL_SIZE);
	}

	// ---- Deep nesting ----

	@Benchmark
	public String deepNestingCodegen() throws Exception {
		return BuffJSON.encode(deepNesting);
	}

	@Benchmark
	public String deepNestingCodegenRandom() throws Exception {
		return BuffJSON.encode(randomDeepNesting[index++ & MASK]);
	}

	@Benchmark
	public String deepNestingGeneric() throws Exception {
		return GENERIC_ENCODER.encode(deepNesting);
	}

	@Benchmark
	public String deepNestingGenericRandom() throws Exception {
		return GENERIC_ENCODER.encode(randomDeepNesting[index++ & MASK]);
	}

	@Benchmark
	public String deepNestingJsonFormat() throws Exception {
		return PROTO_PRINTER.print(deepNesting);
	}

	@Benchmark
	public String deepNestingJsonFormatRandom() throws Exception {
		return PROTO_PRINTER.print(randomDeepNesting[index++ & MASK]);
	}

	// ---- String heavy ----

	@Benchmark
	public String stringHeavyCodegen() throws Exception {
		return BuffJSON.encode(stringHeavy);
	}

	@Benchmark
	public String stringHeavyCodegenRandom() throws Exception {
		return BuffJSON.encode(randomStringHeavy[index++ & MASK]);
	}

	@Benchmark
	public String stringHeavyGeneric() throws Exception {
		return GENERIC_ENCODER.encode(stringHeavy);
	}

	@Benchmark
	public String stringHeavyGenericRandom() throws Exception {
		return GENERIC_ENCODER.encode(randomStringHeavy[index++ & MASK]);
	}

	@Benchmark
	public String stringHeavyJsonFormat() throws Exception {
		return PROTO_PRINTER.print(stringHeavy);
	}

	@Benchmark
	public String stringHeavyJsonFormatRandom() throws Exception {
		return PROTO_PRINTER.print(randomStringHeavy[index++ & MASK]);
	}
}
