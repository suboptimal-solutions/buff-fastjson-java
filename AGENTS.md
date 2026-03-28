# AGENTS.md - buff-fastjson-java (root)

## Project Overview

Fast protobuf-to-JSON serializer for Java using fastjson2 as the JSON writing engine.
Serialization only (no deserialization yet). Proto3 JSON spec compliant.

## Architecture

```
BuffJSON.encode(message)
  -> JSON.toJSONString(message)               # fastjson2 entry point
    -> ProtobufWriterModule.getObjectWriter()  # intercepts Message types
      -> ProtobufMessageWriter.write()         # iterates fields via cached schema
        -> FieldWriter.writeValue()            # type-dispatched field writing
        -> WellKnownTypes.write()              # special types (Timestamp, wrappers, etc.)
```

fastjson2 handles: buffer pooling, number formatting, string escaping, UTF-8 encoding.
We handle: protobuf field extraction, proto3 JSON spec compliance, well-known types.

## Key Design Decisions

- **fastjson2 `ObjectWriterModule`**: Chose the public plugin API over depending on fastjson2 internals.
  Clean extension point, survives fastjson2 version upgrades.
- **`MessageSchema` caching**: One-time cost per Descriptor. Avoids `getAllFields()` TreeMap allocation
  on every serialize call (the main bottleneck of `JsonFormat.printer()`).
- **`message.getField(descriptor)`** for field access (not direct getters). Generic but involves boxing
  for primitives. Future optimization: code-generated or MethodHandle-based direct getters.
- **`Float.floatToRawIntBits() == 0`** for default value checks (not `==`), to correctly handle `-0.0`.
- **`Long.toUnsignedString()`** for uint64, **`Integer.toUnsignedLong()`** for uint32.

## Build Notes

- **Maven uses JDK 25** (Homebrew) but compiles with `--release 21` (cross-compilation).
  `JAVA_HOME` only matters for runtime (benchmarks fork JVMs).
- **Eclipse/JDT in VSCode** auto-builds into `target/classes` and can overwrite Maven's output
  with broken stubs. If you see `Unresolved compilation problem` at runtime, delete
  `.project`/`.classpath`/`.settings` files from all modules and rebuild.
- **JMH annotation processor** needs `<annotationProcessorPaths>` in maven-compiler-plugin
  (not just classpath dependency). Without this, `META-INF/BenchmarkList` won't be generated.
- **ascopes protobuf-maven-plugin** config: uses `<protoc kind="binary-maven">` (not `<protocVersion>`).
  Version 5.1.0 changed the API.

## Module Layout

- **buff-fastjson-core** — public API + internal serialization (no proto dependency)
- **buff-fastjson-tests** — conformance tests + own .proto definitions (conformance_test.proto)
- **buff-fastjson-benchmarks** — JMH benchmarks + own .proto definitions (simple_message.proto, complex_message.proto)

Each module owns its protos — no shared proto module. Tests validate correctness, benchmarks validate performance.

## Not Yet Implemented

- `google.protobuf.Any` (needs TypeRegistry)
- Deserialization (JSON -> protobuf)
- Streaming / Appendable output
- Proto2 support
