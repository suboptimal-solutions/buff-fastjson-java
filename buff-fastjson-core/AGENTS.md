# AGENTS.md - buff-fastjson-core

## Module Purpose

The core library. Contains the public API (`BuffJSON`) and all internal serialization logic.
No dependency on specific `.proto` definitions — works with any `com.google.protobuf.Message`.

## Package Layout

```
io.suboptimal.buffjson/
  BuffJSON.java                    # Public API: BuffJSON.encode(MessageOrBuilder) -> String

io.suboptimal.buffjson.internal/
  ProtobufWriterModule.java        # fastjson2 ObjectWriterModule (intercepts Message types)
  ProtobufMessageWriter.java       # Main serialization loop (iterates schema fields)
  MessageSchema.java               # Cached field metadata per Descriptor
  FieldWriter.java                 # Type-dispatched value writing (scalars, maps, repeated)
  WellKnownTypes.java              # Special handling for 15 well-known protobuf types
```

## Serialization Flow (hot path)

1. `BuffJSON.encode()` calls `JSON.toJSONString(message)`.
2. fastjson2 calls `ProtobufWriterModule.getObjectWriter()` which returns `ProtobufMessageWriter`.
3. `ProtobufMessageWriter.writeMessage()`:
   - Gets cached `MessageSchema` for the message's `Descriptor`
   - Iterates the `FieldInfo[]` array (not `getAllFields()` — no TreeMap)
   - For each field: checks presence/default, then calls `FieldWriter.writeValue()`
4. `FieldWriter.writeValue()` dispatches on `JavaType` (INT, LONG, FLOAT, ..., MESSAGE)
5. For MESSAGE fields: checks `WellKnownTypes.isWellKnownType()` first, then recurses.

## Proto3 JSON Spec: Key Gotchas

- **uint32/fixed32**: Java stores as `int` but JSON needs unsigned representation.
  Use `Integer.toUnsignedLong()` to widen before writing.
- **uint64/fixed64**: Java stores as `long` but JSON needs unsigned quoted string.
  Use `Long.toUnsignedString()`.
- **int64 and all 64-bit types**: Must be quoted strings in JSON (not raw numbers).
- **NaN/Infinity**: fastjson2 writes `null` for these. We intercept and write quoted strings.
- **-0.0**: `0.0 == -0.0` is `true` in Java. Use `floatToRawIntBits()`/`doubleToRawLongBits()`
  to distinguish. `-0.0` is NOT a default value and must be serialized.
- **Enum in map values**: `message.getField()` returns `Integer` (not `EnumValueDescriptor`)
  for enum fields inside map entries. Must handle both types.
- **Wrapper types** (Int32Value, etc.): Serialize as unwrapped primitive values, not objects.
- **FieldMask**: Paths in `snake_case` must be converted to `lowerCamelCase` and comma-joined.
- **Struct/Value/ListValue**: Serialize as native JSON objects/arrays/values (not protobuf objects).
- **Duration nanos**: Format to 3, 6, or 9 digits (not arbitrary precision).

## Dependencies

- `com.google.protobuf:protobuf-java` — Message, Descriptor, FieldDescriptor APIs
- `com.alibaba.fastjson2:fastjson2` — JSONWriter, ObjectWriterModule, ObjectWriter

