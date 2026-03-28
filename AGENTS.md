# AGENTS.md - buff-fastjson-java (root)

## Project Overview

Fast protobuf-to-JSON serializer for Java using fastjson2 as the JSON writing engine.
~4x faster than `JsonFormat.printer()`. Serialization only (no deserialization yet).
Proto3 JSON spec compliant, including all 16 well-known types.

## Architecture

```
BuffJSON.encode(message)              # convenience static method
  -> DEFAULT_ENCODER.encode(message)  # delegates to Encoder

Encoder.encode(message)               # configurable (TypeRegistry for Any)
  -> sets ThreadLocal TypeRegistry (if configured)
  -> JSON.toJSONString(message)       # fastjson2 entry point
    -> ProtobufWriterModule.getObjectWriter()  # intercepts Message types
      -> ProtobufMessageWriter.write()         # iterates fields via cached schema
        -> FieldWriter.writeValue()            # type-dispatched field writing
        -> WellKnownTypes.write()              # special types (Timestamp, Any, wrappers, etc.)
```

fastjson2 handles: buffer pooling, number formatting, string escaping, UTF-8 encoding.
We handle: protobuf field extraction, proto3 JSON spec compliance, well-known types.

## Public API

```java
// Simple usage (no Any fields)
String json = BuffJSON.encode(message);

// Builder pattern with TypeRegistry (for Any fields)
Encoder encoder = BuffJSON.encoder()
    .withTypeRegistry(TypeRegistry.newBuilder()
        .add(MyMessage.getDescriptor())
        .build());
String json = encoder.encode(message);
```

- `BuffJSON` — static entry point + factory for `Encoder`
- `Encoder` — immutable, thread-safe, cacheable. Holds optional `TypeRegistry`.

## Key Design Decisions

- **fastjson2 `ObjectWriterModule`**: Chose the public plugin API over depending on fastjson2 internals.
- **`MessageSchema` caching**: One-time cost per Descriptor. Avoids `getAllFields()` TreeMap allocation.
- **`message.getField(descriptor)`** for field access (generic, involves boxing for primitives).
- **`Float.floatToRawIntBits() == 0`** for default value checks (correctly handles `-0.0`).
- **`Long.toUnsignedString()`** for uint64, **`Integer.toUnsignedLong()`** for uint32.
- **ThreadLocal `TypeRegistry`** for Any support — set/cleared per `Encoder.encode()` call.
- **Builder pattern** (`Encoder`) mirrors `JsonFormat.printer()` style, extensible for future options.

## Build Notes

- **Java 21** target via `<maven.compiler.release>21</maven.compiler.release>`.
- **`-Xlint:all,-processing -Werror`** enabled globally — any new warning fails the build.
- **Spotless** auto-formats Java (Eclipse JDT), Markdown (Flexmark), POM XML (sortPom) on every build.
- **Eclipse/JDT in VSCode** auto-builds into `target/classes` and can overwrite Maven's output
  with broken stubs. If you see `Unresolved compilation problem` or `NoSuchMethodError` at runtime,
  delete `.project`/`.classpath`/`.settings` files from all modules and rebuild with `mvn clean install`.
- **JMH annotation processor** needs `<annotationProcessorPaths>` in maven-compiler-plugin.
- **ascopes protobuf-maven-plugin** config: uses `<protoc kind="binary-maven">` (not `<protocVersion>`).

## Module Layout

- **buff-fastjson-core** — public API (`BuffJSON`, `Encoder`) + internal serialization (no proto dependency)
- **buff-fastjson-tests** — 84 conformance tests + own .proto definitions (`conformance_test.proto`)
- **buff-fastjson-benchmarks** — JMH benchmarks + own .proto definitions (`simple_message.proto`, `complex_message.proto`)

Each module owns its protos. Tests validate correctness, benchmarks validate performance.

## Not Yet Implemented

- Deserialization (JSON -> protobuf)
- Streaming / Appendable output
- Proto2 support

