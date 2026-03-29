package io.suboptimal.buffjson.benchmarks;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson2.JSON;
import com.google.protobuf.util.JsonFormat;

import org.openjdk.jmh.annotations.*;

import io.suboptimal.buffjson.BuffJSON;
import io.suboptimal.buffjson.Encoder;
import io.suboptimal.buffjson.benchmarks.pojo.SimpleMessagePojo;
import io.suboptimal.buffjson.benchmarks.pojo.SimpleMessagePojoCompiled;
import io.suboptimal.buffjson.proto.SimpleMessage;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class SimpleMessageBenchmark {

	private static final int POOL_SIZE = 1024;
	private static final int MASK = POOL_SIZE - 1;
	private static final JsonFormat.Printer PROTO_PRINTER = JsonFormat.printer();
	private static final Encoder GENERIC_ENCODER = BuffJSON.encoder().withGeneratedEncoders(false);

	private SimpleMessage message;
	private SimpleMessage[] randomMessages;
	private SimpleMessagePojo pojo;
	private SimpleMessagePojoCompiled pojoCompiled;
	private int index;

	@Setup
	public void setup() {
		message = BenchmarkData.createSimpleMessage();
		randomMessages = BenchmarkData.createRandomSimpleMessages(new Random(42), POOL_SIZE);
		pojo = BenchmarkData.createSimpleMessagePojo();
		pojoCompiled = BenchmarkData.createSimpleMessagePojoCompiled();
	}

	@Benchmark
	public String buffJsonCodegen() throws Exception {
		return BuffJSON.encode(message);
	}

	@Benchmark
	public String buffJsonCodegenRandom() throws Exception {
		return BuffJSON.encode(randomMessages[index++ & MASK]);
	}

	@Benchmark
	public String buffJson() throws Exception {
		return GENERIC_ENCODER.encode(message);
	}

	@Benchmark
	public String buffJsonRandom() throws Exception {
		return GENERIC_ENCODER.encode(randomMessages[index++ & MASK]);
	}

	@Benchmark
	public String protoJsonFormat() throws Exception {
		return PROTO_PRINTER.print(message);
	}

	@Benchmark
	public String protoJsonFormatRandom() throws Exception {
		return PROTO_PRINTER.print(randomMessages[index++ & MASK]);
	}

	@Benchmark
	public String fastjson2Pojo() {
		return JSON.toJSONString(pojo);
	}

	@Benchmark
	public String fastjson2PojoCompiled() {
		return JSON.toJSONString(pojoCompiled);
	}
}
