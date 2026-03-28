# buff-fastjson-java

Fast JSON serialization for Protocol Buffer messages in Java, compliant with the [Proto3 JSON spec](https://protobuf.dev/programming-guides/proto3/#json).

## Performance

~4x faster than `JsonFormat.printer().print()` from protobuf-java-util:

| Message type | BuffJSON (ops/s) | JsonFormat (ops/s) | Speedup |
|---|---|---|---|
| SimpleMessage (6 fields) | ~5.8M | ~1.5M | **~4x** |
| ComplexMessage (nested, maps, repeated) | ~666K | ~158K | **~4.2x** |

Benchmarked on JDK 21 (Corretto) with JMH (2 forks, 5 warmup + 5 measurement iterations).

## How it works

Uses [Alibaba fastjson2](https://github.com/alibaba/fastjson2) as the JSON writing engine via its `ObjectWriterModule` extension point. We register a custom module that handles `com.google.protobuf.Message` types, extracting fields via protobuf Descriptors and delegating all JSON formatting (buffering, number encoding, string escaping) to fastjson2's optimized infrastructure.

Key design:
- **No `getAllFields()` / TreeMap allocation** per call (unlike `JsonFormat`)
- **No Gson dependency** for string escaping (unlike `JsonFormat`)
- **Cached `MessageSchema`** per message Descriptor (one-time cost)
- **fastjson2 ThreadLocal buffer reuse** eliminates per-call allocations

Inspired by [fastjson2](https://github.com/alibaba/fastjson2) and [buffa](https://github.com/anthropics/buffa).

## Usage

```java
import io.suboptimal.buffjson.BuffJSON;

String json = BuffJSON.encode(myProtoMessage);
```

The output matches `JsonFormat.printer().omittingInsignificantWhitespace().print()` exactly.

## Proto3 JSON Spec Compliance

| Feature | Status |
|---|---|
| All scalar types (int32, int64, uint32, uint64, sint32, sint64, fixed32, fixed64, sfixed32, sfixed64, float, double, bool, string, bytes) | Supported |
| Unsigned integer formatting (uint32, uint64) | Supported |
| int64/uint64 as quoted strings | Supported |
| NaN, Infinity, -Infinity as quoted strings | Supported |
| Nested messages | Supported |
| Repeated fields | Supported |
| Map fields (all key types: string, int, bool, etc.) | Supported |
| Oneof fields | Supported |
| Enums as string names | Supported |
| Proto3 default value omission | Supported |
| Proto3 explicit presence (`optional` keyword) | Supported |
| Custom `json_name` | Supported |
| `google.protobuf.Timestamp` (RFC 3339) | Supported |
| `google.protobuf.Duration` | Supported |
| `google.protobuf.FieldMask` (camelCase paths) | Supported |
| `google.protobuf.Struct` / `Value` / `ListValue` | Supported |
| All 9 wrapper types (`Int32Value`, `StringValue`, etc.) | Supported |
| `google.protobuf.Any` | Not yet |
| Deserialization (JSON to protobuf) | Not yet |

## Building

Requires Java 21+ and Maven 3.9+.

```bash
mvn clean install
```

## Running Benchmarks

```bash
# Full benchmark run (2 forks, 5 warmup + 5 measurement iterations)
java -jar buff-fastjson-benchmarks/target/benchmarks.jar

# Quick sanity check
java -jar buff-fastjson-benchmarks/target/benchmarks.jar -wi 1 -i 1 -f 1

# Specific benchmark
java -jar buff-fastjson-benchmarks/target/benchmarks.jar SimpleMessageBenchmark

# With GC profiling
java -jar buff-fastjson-benchmarks/target/benchmarks.jar -prof gc
```

## Running Tests

```bash
mvn test
```

77 conformance tests compare `BuffJSON.encode()` output against `JsonFormat.printer().omittingInsignificantWhitespace().print()` for all supported proto3 JSON features.

## Project Structure

```
buff-fastjson-java/
  buff-fastjson-core/           # Library: BuffJSON.encode() API + internal serialization
  buff-fastjson-tests/          # Conformance tests + own .proto definitions
  buff-fastjson-benchmarks/     # JMH benchmarks + own .proto definitions (uber-jar)
```

## Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `com.google.protobuf:protobuf-java` | 4.34.1 | Protobuf runtime (Message, Descriptor) |
| `com.alibaba.fastjson2:fastjson2` | 2.0.61 | JSON writing engine |

## License

Apache License 2.0
