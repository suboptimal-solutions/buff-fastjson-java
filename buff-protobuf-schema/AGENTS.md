# AGENTS.md - buff-protobuf-schema

## Module Purpose

Generates JSON Schema (draft 2020-12) from protobuf message Descriptors.
No fastjson2 dependency — only `protobuf-java` (provided scope).
Returns `Map<String, Object>` for maximum portability with OpenAPI 3.1+, AsyncAPI 3.0+, MCP, and any JSON library.

## Package Layout

```
io.suboptimal.buffjson.schema/
  ProtobufSchema.java       # Single public class — ProtobufSchema.generate(Descriptor) / generate(Class)
```

## How It Works

Walks the protobuf `Descriptor` tree and maps each field to its Proto3 JSON Schema equivalent:

1. **Entry point**: `ProtobufSchema.generate(Descriptor)` creates an internal `ProtobufSchema` instance
   to track `$defs` and cycle detection state, then calls `schemaForMessage()`.
2. **Message → object**: Each message becomes `{"type": "object", "properties": {...}}`.
   Properties are keyed by `FieldDescriptor.getJsonName()` (camelCase / custom json_name).
3. **Field dispatch**: `schemaForField()` handles map → repeated → single value branching.
   `schemaForSingleValue()` switches on `JavaType` (INT, LONG, FLOAT, BOOLEAN, STRING, BYTE_STRING, ENUM, MESSAGE).
4. **Recursive types**: Tracked via `inProgress` set. When a cycle is detected, the type
   is placed in `$defs` and referenced via `{"$ref": "#/$defs/full.type.name"}`.
5. **Well-known types**: Detected by full name (same set of 16 as WellKnownTypes.java in core).
   Each maps to its canonical JSON Schema form (e.g., Timestamp → `{"type": "string", "format": "date-time"}`).
6. **Root schema**: Gets `"$schema": "https://json-schema.org/draft/2020-12/schema"` and
   `"$defs"` if any recursive types were encountered.

## Proto3 JSON → JSON Schema Type Mapping

### Scalars

- int32, sint32, sfixed32 → `{"type": "integer"}`
- uint32, fixed32 → `{"type": "integer", "minimum": 0}`
- int64, sint64, sfixed64, uint64, fixed64 → `{"type": "string"}` (proto3 JSON quotes 64-bit)
- float, double → `{"oneOf": [{"type": "number"}, {"type": "string", "enum": ["NaN", "Infinity", "-Infinity"]}]}`
- bool → `{"type": "boolean"}`
- string → `{"type": "string"}`
- bytes → `{"type": "string"}` (base64 in proto3 JSON)
- enum → `{"type": "string", "enum": ["VALUE1", ...]}`

### Composites

- repeated → `{"type": "array", "items": <element-schema>}`
- map<K,V> → `{"type": "object", "additionalProperties": <value-schema>}`
- oneof → all variants listed in properties (at most one present at runtime)

### Well-Known Types

- Timestamp → `{"type": "string", "format": "date-time"}`
- Duration, FieldMask → `{"type": "string"}`
- Struct → `{"type": "object"}`
- Value → `{}` (any)
- ListValue → `{"type": "array"}`
- Any → `{"type": "object", "properties": {"@type": {"type": "string"}}, "required": ["@type"]}`
- Wrappers (Int32Value, etc.) → unwrapped to their JSON primitive schema
- Empty → `{"type": "object"}`

## Dependencies

- `com.google.protobuf:protobuf-java` (provided) — Descriptor, FieldDescriptor, EnumDescriptor, Message

