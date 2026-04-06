# AGENTS.md - buff-json (root)

## Project Overview

Fast protobuf-to-JSON serializer for Java using fastjson2 as the JSON writing engine.
Up to ~10x faster than `JsonFormat.printer()` with the optional protoc plugin (~4-5x without).
Proto3 JSON spec compliant, including all 16 well-known types.
Includes JSON Schema generation from protobuf descriptors (separate module, no fastjson2 dependency).

## Architecture

Two encoding paths — codegen (fast) with fallback to runtime (reflection):

```
BuffJsonEncoder.encode(message)
  -> creates JSONWriter directly (bypasses fastjson2 module dispatch)
  -> ProtobufMessageWriter(typeRegistry, useGenerated).writeMessage(jsonWriter, message)
    -> writeFields(jsonWriter, message)           # instance method, carries settings
      -> GeneratedEncoderRegistry.get()           # check for codegen encoder (ServiceLoader)
         -> if found: BuffJsonGeneratedEncoder.writeFields(jw, msg, writer)  # direct typed accessors
            -> nested messages: OtherEncoder.INSTANCE.writeFields(jw, nested, writer)  # direct
            -> WKT Timestamp/Duration: writeTimestampDirect(seconds, nanos)  # no reflection
         -> if not:   runtime path (MessageSchema + FieldWriter)  # reflection-style getField()
```

**Codegen path** (optional, ~2-3x faster): protoc plugin generates `*JsonEncoder` per message.
Each encoder calls typed getters directly (`msg.getId()` → `int`), eliminating:
- `message.getField(fd)` reflection + boxing
- `switch (fd.getJavaType())` runtime dispatch
- `ConcurrentHashMap.get()` for MessageSchema lookup

Additional codegen optimizations:
- **Direct nested encoder calls** — `AddressJsonEncoder.INSTANCE.writeFields(jw, msg, writer)` instead of routing through `ProtobufMessageWriter` (avoids ConcurrentHashMap lookup + instanceof check per nested message)
- **Inline WKT Timestamp/Duration** — `WellKnownTypes.writeTimestampDirect(jsonWriter, ts.getSeconds(), ts.getNanos())` bypasses descriptor string switch, field cache lookup, and `getField()` reflection+boxing
- **Pre-cached enum name arrays** — static `String[]` built at class init from enum descriptor values, replaces `forNumber()` + `getValueDescriptor().getName()` per write
- **String map key optimization** — avoids redundant `toString()` for String-typed map keys

**Runtime path** (always available): iterates cached `FieldInfo[]`, dispatches by `JavaType`.
Still ~5-6x faster than `JsonFormat` due to schema caching and fastjson2 buffer reuse.

**Fallback**: `DynamicMessage` instances (e.g., from Any unpacking) always use the runtime path.

fastjson2 handles: buffer pooling, number formatting, string escaping, UTF-8 encoding, Base64 encoding (`writeBase64(byte[])`).
We handle: protobuf field extraction, proto3 JSON spec compliance, well-known types, epoch→calendar arithmetic for timestamps.

## Public API

```java
// Simple usage (no Any fields)
BuffJsonEncoder encoder = BuffJson.encoder();
String json = encoder.encode(message);

// With TypeRegistry (for Any fields)
BuffJsonEncoder encoder = BuffJson.encoder()
    .setTypeRegistry(TypeRegistry.newBuilder()
        .add(MyMessage.getDescriptor())
        .build());
String json = encoder.encode(message);

// Force runtime path (skip generated encoders, for benchmarking/testing)
BuffJsonEncoder runtimeEncoder = BuffJson.encoder().setGeneratedEncoders(false);
String json = runtimeEncoder.encode(message);

// Mixed pojo + protobuf: register fastjson2 module from encoder/decoder
JSONFactory.getDefaultObjectWriterProvider().register(encoder.writerModule());
JSONFactory.getDefaultObjectReaderProvider().register(decoder.readerModule());
```

- `BuffJson` — static entry point + factory for `BuffJsonEncoder` and `BuffJsonDecoder`
- `BuffJsonEncoder` — configurable encoder. Holds optional `TypeRegistry` and `useGeneratedEncoders` flag. Creates `JSONWriter` directly (no fastjson2 module dispatch). Exposes `writerModule()` for fastjson2 registration.
- `BuffJsonDecoder` — configurable decoder. Creates `JSONReader` directly. Exposes `readerModule()` for fastjson2 registration.
- `BuffJsonGeneratedEncoder<T>` — interface implemented by protoc-plugin-generated encoders. Discovered via `ServiceLoader`.
- `BuffJsonGeneratedDecoder<T>` — interface implemented by protoc-plugin-generated decoders. Discovered via `ServiceLoader`.

## Key Design Decisions

- **Direct JSONWriter/JSONReader** — encoder/decoder create fastjson2 writers/readers directly, bypassing the module dispatch and provider lookup. This eliminates per-call overhead from fastjson2's `JSON.toJSONString()`/`JSON.parseObject()`.
- **Instance-based settings** — `ProtobufMessageWriter` and `ProtobufMessageReader` hold `TypeRegistry` and `useGenerated` as instance fields. Settings flow through the call chain via `this` — no ThreadLocals.
- **Module exposure** — `encoder.writerModule()` and `decoder.readerModule()` return fastjson2 modules backed by configured writer/reader instances, for mixed pojo+protobuf projects using `JSON.toJSONString()`.
- **`MessageSchema` caching**: One-time cost per Descriptor. Avoids `getAllFields()` TreeMap allocation.
- **Pre-computed `char[] nameWithColon`**: Field names pre-encoded as `"name":` for `writeNameRaw(char[])`. Must use `char[]` (not `byte[]`) because `JSONWriterUTF16.writeNameRaw(byte[])` throws `UnsupportedOperation`.
- **`message.getField(descriptor)`** for field access in runtime path (involves boxing for primitives).
- **`Float.floatToRawIntBits() == 0`** for default value checks (correctly handles `-0.0`).
- **Native fastjson2 methods** for zero-allocation writes: `writeString(long)` for signed int64 (no `Long.toString()` allocation), `writeBase64(byte[])` for bytes fields (no intermediate Base64 String), `writeNameRaw(byte[])` for field names on UTF-8 path (direct `arraycopy`).
- **`WellKnownTypes.writeUnsignedLongString()`** for uint64/fixed64: delegates to `writeString(long)` when value fits in signed range, formats to `byte[]` + `writeStringLatin1()` for large unsigned values.
- **`Integer.toUnsignedLong()`** for uint32.
- **Zero-allocation timestamps**: `writeTimestampDirect()` uses Howard Hinnant's civil_from_days algorithm to convert epoch seconds to year/month/day/hour/minute/second using pure integer arithmetic — no `Instant` or `OffsetDateTime` allocation. Exact-size byte buffers (20/24/27/30 bytes) eliminate `Arrays.copyOf()`.
- **Exact-size duration buffers**: `writeDurationDirect()` computes buffer size from `longDigitCount(seconds)` + `nanosDigitCount(nanos)` to avoid over-allocation and `Arrays.copyOf()`.
- **Builder pattern** (`BuffJsonEncoder`) mirrors `JsonFormat.printer()` style, extensible for future options.
- **`GeneratedEncoderRegistry`** uses `ServiceLoader` — zero-config discovery, no registration needed.
- **`DynamicMessage` guard**: Generated encoders are skipped for `DynamicMessage` instances (e.g., from Any unpacking) because they'd fail the cast to the concrete message type.
- **Protoc plugin generates to same package** as protobuf messages. `META-INF/services` file is also generated but needs a `<resources>` POM entry to be copied to `target/classes`.

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

- **buff-json** — public API (`BuffJson`, `BuffJsonEncoder`, `BuffJsonDecoder`, `BuffJsonGeneratedEncoder`, `BuffJsonGeneratedDecoder`, `BuffJsonGeneratedComments`) + internal serialization/deserialization
- **buff-json-protoc-plugin** — protoc plugin that generates `*JsonEncoder`, `*JsonDecoder`, and `*Comments` per message/proto file. Depends only on `protobuf-java`. Reads `CodeGeneratorRequest` from stdin, writes `CodeGeneratorResponse` to stdout. The `*Comments` classes extract proto source comments from `SourceCodeInfo` (which protoc always sends to plugins) and make them available at runtime via `ServiceLoader`.
- **buff-json-schema** — JSON Schema (draft 2020-12) generation from protobuf Descriptors. Depends on `protobuf-java` and `buff-json` (both provided scope), with optional `build.buf:protovalidate` for buf.validate constraint mapping. `ProtobufSchema.generate(Descriptor)` returns `Map<String, Object>`. Includes `title`, `description` (from proto comments via `BuffJsonGeneratedComments` or `SourceCodeInfo`), `format` hints, `contentEncoding`, and buf.validate constraints as JSON Schema keywords (minLength, pattern, format, minimum/maximum, minItems, required, etc.) when protovalidate is on the classpath.
- **buff-json-jackson** — Jackson `Module` wrapping `BuffJson.encode()`/`decode()` for `ObjectMapper` integration. Thin adapter (~3 classes), no reimplementation. Depends on `buff-json`, `jackson-databind`, `fastjson2`, `protobuf-java` (all provided). Provides `BuffJsonJacksonModule` (register with ObjectMapper). Protobuf messages work alongside POJOs/records in Jackson serialization. 38 tests including conformance, POJO/record integration, tree model, and roundtrip.
- **buff-json-tests** — conformance tests (each validates both codegen and runtime paths) + JSON Schema tests + buf.validate constraint tests + own .proto definitions
- **buff-json-benchmarks** — JMH benchmarks (codegen vs runtime vs JsonFormat vs Jackson-HubSpot vs BuffJsonJackson) + own .proto definitions

Build order in reactor: core → protoc-plugin → schema → jackson → tests → benchmarks.
Each consumer module (tests, benchmarks) configures the protoc plugin via ascopes `protobuf-maven-plugin` `<jvmPlugin>`.

## Not Yet Implemented

- Streaming / Appendable output
- Proto2 support

