package io.suboptimal.buffjson.benchmarks;

import java.util.concurrent.TimeUnit;

import com.google.protobuf.util.JsonFormat;

import org.openjdk.jmh.annotations.*;

import io.suboptimal.buffjson.BuffJSON;
import io.suboptimal.buffjson.Encoder;
import io.suboptimal.buffjson.proto.ComplexMessage;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class ComplexMessageBenchmark {

	private static final JsonFormat.Printer PROTO_PRINTER = JsonFormat.printer();
	private static final Encoder GENERIC_ENCODER = BuffJSON.encoder().withGeneratedEncoders(false);

	private ComplexMessage message;

	@Setup
	public void setup() {
		message = BenchmarkData.createComplexMessage();
	}

	@Benchmark
	public String buffJsonCodegen() throws Exception {
		return BuffJSON.encode(message);
	}

	@Benchmark
	public String buffJson() throws Exception {
		return GENERIC_ENCODER.encode(message);
	}

	@Benchmark
	public String protoJsonFormat() throws Exception {
		return PROTO_PRINTER.print(message);
	}
}
