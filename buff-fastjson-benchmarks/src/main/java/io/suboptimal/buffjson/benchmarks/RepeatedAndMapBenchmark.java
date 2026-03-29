package io.suboptimal.buffjson.benchmarks;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.util.JsonFormat;

import org.openjdk.jmh.annotations.*;

import io.suboptimal.buffjson.BuffJSON;
import io.suboptimal.buffjson.Encoder;
import io.suboptimal.buffjson.proto.BenchMapHeavy;
import io.suboptimal.buffjson.proto.BenchRepeatedHeavy;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class RepeatedAndMapBenchmark {

	private static final int POOL_SIZE = 1024;
	private static final int MASK = POOL_SIZE - 1;
	private static final JsonFormat.Printer PROTO_PRINTER = JsonFormat.printer();
	private static final Encoder GENERIC_ENCODER = BuffJSON.encoder().withGeneratedEncoders(false);

	private BenchRepeatedHeavy repeated;
	private BenchRepeatedHeavy[] randomRepeated;
	private BenchMapHeavy map;
	private BenchMapHeavy[] randomMaps;
	private int index;

	@Setup
	public void setup() {
		repeated = BenchmarkData.createBenchRepeatedHeavy();
		randomRepeated = BenchmarkData.createRandomBenchRepeatedHeavy(new Random(42), POOL_SIZE);
		map = BenchmarkData.createBenchMapHeavy();
		randomMaps = BenchmarkData.createRandomBenchMapHeavy(new Random(43), POOL_SIZE);
	}

	// ---- Repeated ----

	@Benchmark
	public String repeatedCodegen() throws Exception {
		return BuffJSON.encode(repeated);
	}

	@Benchmark
	public String repeatedCodegenRandom() throws Exception {
		return BuffJSON.encode(randomRepeated[index++ & MASK]);
	}

	@Benchmark
	public String repeatedGeneric() throws Exception {
		return GENERIC_ENCODER.encode(repeated);
	}

	@Benchmark
	public String repeatedGenericRandom() throws Exception {
		return GENERIC_ENCODER.encode(randomRepeated[index++ & MASK]);
	}

	@Benchmark
	public String repeatedJsonFormat() throws Exception {
		return PROTO_PRINTER.print(repeated);
	}

	@Benchmark
	public String repeatedJsonFormatRandom() throws Exception {
		return PROTO_PRINTER.print(randomRepeated[index++ & MASK]);
	}

	// ---- Map ----

	@Benchmark
	public String mapCodegen() throws Exception {
		return BuffJSON.encode(map);
	}

	@Benchmark
	public String mapCodegenRandom() throws Exception {
		return BuffJSON.encode(randomMaps[index++ & MASK]);
	}

	@Benchmark
	public String mapGeneric() throws Exception {
		return GENERIC_ENCODER.encode(map);
	}

	@Benchmark
	public String mapGenericRandom() throws Exception {
		return GENERIC_ENCODER.encode(randomMaps[index++ & MASK]);
	}

	@Benchmark
	public String mapJsonFormat() throws Exception {
		return PROTO_PRINTER.print(map);
	}

	@Benchmark
	public String mapJsonFormatRandom() throws Exception {
		return PROTO_PRINTER.print(randomMaps[index++ & MASK]);
	}
}
