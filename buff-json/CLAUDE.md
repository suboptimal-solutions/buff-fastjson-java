# CLAUDE.md - buff-json

## Module Purpose

The core library. Contains the public API (`BuffJson`, `BuffJsonEncoder`, `BuffJsonDecoder`) and all internal serialization logic.
No dependency on specific `.proto` definitions — works with any `com.google.protobuf.Message`.

## Package Layout

```
io.suboptimal.buffjson/
  BuffJson.java                    # Static entry point + factory: BuffJson.encoder(), BuffJson.decoder()
  BuffJsonEncoder.java             # Configurable encoder — creates JSONWriter directly, caches a single
                                   #   ProtobufMessageWriter (volatile, invalidated on setters), exposes writerModule()
  BuffJsonDecoder.java             # Configurable decoder — creates JSONReader directly, exposes readerModule()
  BuffJsonGeneratedEncoder.java    # Interface for protoc-plugin-generated encoders
  BuffJsonGeneratedDecoder.java    # Interface for protoc-plugin-generated decoders
  BuffJsonCodecHolder.java         # Interface injected into message classes via protoc insertion points
                                   #   provides buffJsonEncoder()/buffJsonDecoder() for instanceof-based discovery

io.suboptimal.buffjson.internal/
  ProtobufWriterModule.java        # fastjson2 ObjectWriterModule (intercepts Message types) — for mixed pojo+proto usage
  ProtobufReaderModule.java        # fastjson2 ObjectReaderModule (intercepts Message types) — for mixed pojo+proto usage
  ProtobufMessageWriter.java       # Three-tier dispatch in writeFields(): codec-holder → typed-accessor → reflection
  ProtobufMessageReader.java       # Stateful instance holding TypeRegistry + useGenerated. Main deserialization logic.
  GeneratedDecoderRegistry.java    # Simple ConcurrentHashMap<Descriptor, Decoder> cache for descriptor-only decode path
  MessageSchema.java               # Cached FieldInfo[] per Descriptor (reflection path); each FieldInfo carries
                                   #   both char[] nameWithColon and byte[] nameWithColonUtf8 for UTF-8 dispatch
  FieldWriter.java                 # Type-dispatched value writing (scalars, maps, repeated) for the reflection path.
                                   #   Float/double NaN/Inf branches reordered isFinite-first.
  FieldReader.java                 # Type-dispatched value reading (scalars, maps, repeated)
  WellKnownTypes.java              # Special handling for 16 well-known protobuf types.
                                   #   writeTimestampDirect()/writeDurationDirect() accept primitives directly —
                                   #   used by codegen to bypass descriptor lookup and getField() reflection.
                                   #   writeUnsignedLongString() — zero-alloc unsigned int64 formatting.

io.suboptimal.buffjson.internal.typed/
  FieldName.java                   # Record (char[] chars, byte[] utf8). writeTo(JSONWriter) dispatches on isUTF8().
  TypedFieldAccessor.java          # Sealed interface, ~20 record variants (IntAccessor, LongAccessor, ...,
                                   #   PresenceMessageAccessor, RepeatedIntAccessor, RepeatedEnumAccessor,
                                   #   MapAccessor, TypedMapAccessor, etc.). Each variant holds pre-bound
                                   #   typed lambdas (ToIntFunction<Message> etc.) and writes one field.
  TypedFieldAccessorFactory.java   # Builds accessors via LambdaMetafactory. Discovers protoc-generated
                                   #   getters (getXxx, hasXxx, getXxxValue, getXxxList, getXxxValueList,
                                   #   getXxxMap, getXxxValueMap) by name. Returns null on any failure.
  TypedMessageSchema.java          # ConcurrentHashMap<Descriptor, TypedMessageSchema> cache. FAILED sentinel
                                   #   marks descriptors where lambda binding failed (silent fallback to reflection).
                                   #   Holds TypedFieldAccessor[] + OneofGroup[] per message type.
```

## Serialization Flow (hot path)

1. `BuffJsonEncoder.encode(message)`:
   - Creates `JSONWriter` directly via `JSONWriter.of()` (bypasses fastjson2 module dispatch).
   - Reuses cached `ProtobufMessageWriter(typeRegistry, useGenerated, useTyped)` (volatile field on encoder; invalidated on setters).
   - Calls `writer.writeMessage(jsonWriter, message)`.
2. `ProtobufMessageWriter.writeFields(jsonWriter, message)` — three-tier dispatch:
   - **Tier 1 — Codegen** (if `useGenerated && message instanceof BuffJsonCodecHolder`):
     - `holder.buffJsonEncoder().writeFields(jw, msg, this)` → typed getters, no reflection.
     - Nested messages call other encoders directly via `INSTANCE.writeFields(jw, msg, writer)` (no registry, no instanceof per nested).
     - Timestamp/Duration fields call `writeTimestampDirect()`/`writeDurationDirect()`.
     - Enum fields use pre-cached `String[]` name arrays.
     - Field-name writes dispatch on `boolean utf8 = jsonWriter.isUTF8()` hoisted at the top of `writeFields`.
     - Returns — never falls through.
   - **Tier 2 — Typed-accessor** (if `useTyped && !(message instanceof DynamicMessage)`):
     - `TypedMessageSchema.forMessage(descriptor, msg.getClass()).writeFields(jw, msg, this)` → LambdaMetafactory-bound typed getters.
     - First call per Descriptor: `TypedFieldAccessorFactory.create(...)` discovers `getXxx`/`hasXxx`/`getXxxList`/`getXxxValueList`/`getXxxMap`/`getXxxValueMap` by name reflection, then binds via `LambdaMetafactory.metafactory(...)` to `ToIntFunction<Message>`, `ToLongFunction<Message>`, `Predicate<Message>`, `Function<Message, Object>`, etc. Builds `TypedFieldAccessor[]` + `OneofGroup[]`. Cached.
     - On any failure (e.g., `DynamicMessage`, custom protoc, missing accessor), returns `null` — schema goes to `FAILED` sentinel; falls through to Tier 3.
     - Returns once schema runs successfully.
   - **Tier 3 — Pure reflection** (fallback):
     - Iterates cached `MessageSchema.FieldInfo[]` (no `getAllFields()` TreeMap).
     - `Object value = message.getField(fd)` (boxes primitives).
     - `FieldWriter.writeValue(jw, fd, value, this)` dispatches on `JavaType`.
     - Field-name writes use `nameWithColon` (UTF-16) or `nameWithColonUtf8` (UTF-8) per the hoisted `utf8` local.
3. For MESSAGE fields in any path: `WellKnownTypes.isWellKnownType()` check first, then recurses via `writer.writeMessage()` (which re-enters the three-tier dispatch).

## Settings Flow (no ThreadLocals)

- `ProtobufMessageWriter` holds settings as instance fields: `TypeRegistry typeRegistry`, `boolean useGenerated`, `boolean useTyped`. `ProtobufMessageReader` mirrors with `typeRegistry` + `useGenerated`.
- Settings propagate through the call chain by passing the writer/reader instance (`this`) to all methods that need them.
- `FieldWriter` / `FieldReader` receive the writer/reader as a parameter for recursive nested message writes/reads.
- `WellKnownTypes.write(jw, msg, writer)` / `readWkt(reader, desc, msgReader)` receive the writer/reader for Any type support.
- Generated encoders/decoders receive the writer/reader: `writeFields(jw, msg, writer)` / `readMessage(reader, msgReader)`.
- `TypedMessageSchema.writeFields(jw, msg, writer)` receives the writer for nested message recursion (which re-enters the three-tier dispatch).

## Module Path (mixed pojo + protobuf)

For projects using `JSON.toJSONString()` with both POJOs and protobuf messages:

```java
// Register modules from configured encoder/decoder
JSONFactory.getDefaultObjectWriterProvider().register(encoder.writerModule());
JSONFactory.getDefaultObjectReaderProvider().register(decoder.readerModule());

// Now fastjson2 handles both POJOs and protobuf messages
JSON.toJSONString(myProtoMessage);  // uses the writer's settings
JSON.parseObject(json, MyMessage.class);  // uses the reader's settings
```

- `ProtobufWriterModule` holds a configured `ProtobufMessageWriter` instance
- `ProtobufReaderModule` holds a configured `ProtobufMessageReader` instance
- fastjson2 caches the ObjectWriter/ObjectReader per type after first lookup

## Proto3 JSON Spec: Key Gotchas

- **uint32/fixed32**: `Integer.toUnsignedLong()` for unsigned representation
- **uint64/fixed64**: `Long.toUnsignedString()` for unsigned quoted strings
- **int64 and all 64-bit types**: Must be quoted strings in JSON
- **NaN/Infinity**: fastjson2 writes `null` — we intercept and write quoted strings
- **-0.0**: Use `floatToRawIntBits()`/`doubleToRawLongBits()` (not `==`) for default checks
- **Enum in map values**: `message.getField()` returns `Integer` (not `EnumValueDescriptor`) for map entries
- **Wrapper types**: Serialize as unwrapped primitive values, not objects
- **FieldMask**: `snake_case` → `lowerCamelCase` conversion, comma-joined
- **Struct/Value/ListValue**: Serialize as native JSON objects/arrays/values
- **Duration nanos**: Format to 3, 6, or 9 digits (not arbitrary precision)
- **Any**: Requires TypeRegistry. Regular messages: `{"@type":..., ...fields}`. WKTs: `{"@type":..., "value":...}`

## Dependencies

- `com.google.protobuf:protobuf-java` — Message, Descriptor, TypeRegistry, DynamicMessage
- `com.alibaba.fastjson2:fastjson2` — JSONWriter, JSONReader, ObjectWriterModule, ObjectReaderModule

