package io.suboptimal.buffjson.benchmarks;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.util.JsonFormat;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;

import org.openjdk.jmh.annotations.*;

import io.suboptimal.buffjson.BuffJson;
import io.suboptimal.buffjson.BuffJsonEncoder;
import io.suboptimal.buffjson.jackson.ProtobufJacksonModule;
import io.suboptimal.buffjson.proto.ComplexMessage;

/**
 * Core regression benchmark: nested messages, maps, repeated fields, oneof,
 * bytes, timestamps.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class ComplexMessageBenchmark {

	private static final int POOL_SIZE = 1024;
	private static final int MASK = POOL_SIZE - 1;
	private static final JsonFormat.Printer PROTO_PRINTER = JsonFormat.printer();
	private static final BuffJsonEncoder RUNTIME_ENCODER = BuffJson.encoder().withGeneratedEncoders(false);
	private static final ObjectMapper JACKSON_MAPPER = new ObjectMapper().registerModule(new ProtobufModule());
	/**
	 * buff-json via Jackson ObjectMapper (wraps BuffJson.encode through Jackson's
	 * Module API).
	 */
	private static final ObjectMapper BUFF_JACKSON_MAPPER = new ObjectMapper()
			.registerModule(new ProtobufJacksonModule());

	private ComplexMessage[] randomMessages;
	private int index;

	@Setup
	public void setup() {
		randomMessages = BenchmarkData.createRandomComplexMessages(new Random(42), POOL_SIZE);
	}

	@Benchmark
	public String buffJsonCompiled() {
		return BuffJson.encode(randomMessages[index++ & MASK]);
	}

	@Benchmark
	public String buffJsonRuntime() {
		return RUNTIME_ENCODER.encode(randomMessages[index++ & MASK]);
	}

	@Benchmark
	public String protoJsonFormat() throws Exception {
		return PROTO_PRINTER.print(randomMessages[index++ & MASK]);
	}

	@Benchmark
	public String jacksonProtobuf() throws JsonProcessingException {
		return JACKSON_MAPPER.writeValueAsString(randomMessages[index++ & MASK]);
	}

	/** Measures buff-json encoding through Jackson's ObjectMapper wrapper. */
	@Benchmark
	public String buffJsonJackson() throws JsonProcessingException {
		return BUFF_JACKSON_MAPPER.writeValueAsString(randomMessages[index++ & MASK]);
	}
}
