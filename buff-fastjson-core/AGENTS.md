# AGENTS.md - buff-fastjson-core

## Module Purpose

The core library. Contains the public API (`BuffJSON`, `Encoder`) and all internal serialization logic.
No dependency on specific `.proto` definitions — works with any `com.google.protobuf.Message`.

## Package Layout

```
io.suboptimal.buffjson/
  BuffJSON.java                    # Static entry point + factory: BuffJSON.encode(), BuffJSON.encoder()
  Encoder.java                     # Immutable, thread-safe encoder with optional TypeRegistry

io.suboptimal.buffjson.internal/
  ProtobufWriterModule.java        # fastjson2 ObjectWriterModule (intercepts Message types)
  ProtobufMessageWriter.java       # Main serialization loop (iterates schema fields)
  MessageSchema.java               # Cached field metadata per Descriptor
  FieldWriter.java                 # Type-dispatched value writing (scalars, maps, repeated)
  WellKnownTypes.java              # Special handling for 16 well-known protobuf types
```

## Serialization Flow (hot path)

1. `BuffJSON.encode()` → `DEFAULT_ENCODER.encode()` → `JSON.toJSONString(message)`
2. fastjson2 calls `ProtobufWriterModule.getObjectWriter()` → returns `ProtobufMessageWriter`
3. `ProtobufMessageWriter.writeMessage()`:
   - `startObject()` + `writeFields()` + `endObject()`
   - `writeFields()` iterates cached `FieldInfo[]` array (no `getAllFields()` TreeMap)
   - For each field: checks presence/default, then calls `FieldWriter.writeValue()`
4. `FieldWriter.writeValue()` dispatches on `JavaType` (INT, LONG, FLOAT, ..., MESSAGE)
5. For MESSAGE fields: checks `WellKnownTypes.isWellKnownType()` first, then recurses

## TypeRegistry Threading (for Any)

- `Encoder.encode()` sets `BuffJSON.ACTIVE_REGISTRY` ThreadLocal before calling fastjson2
- `WellKnownTypes.writeAny()` reads the ThreadLocal to resolve type URLs
- ThreadLocal is cleared in a `finally` block after serialization
- Users who never use Any never pay for it (ThreadLocal is never set)

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
- `com.alibaba.fastjson2:fastjson2` — JSONWriter, ObjectWriterModule, ObjectWriter

