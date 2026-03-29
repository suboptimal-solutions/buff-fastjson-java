# AGENTS.md - buff-fastjson-protoc-plugin

## Module Purpose

Protoc plugin that generates optimized `*JsonEncoder` classes per protobuf message type.
Generated encoders use typed accessors directly (`msg.getId()` returns `int`) instead of
`message.getField(fd)` (which returns `Object` and boxes primitives), eliminating boxing,
runtime type dispatch, and schema cache lookups.

## How It Works

Standard protoc plugin protocol: reads `CodeGeneratorRequest` from stdin, writes
`CodeGeneratorResponse` to stdout. Invoked by protoc via the ascopes protobuf-maven-plugin
`<jvmPlugin>` configuration.

## Key Classes

- `BuffJsonProtocPlugin.java` — main entry point, builds `FileDescriptor` graph, orchestrates generation
- `EncoderGenerator.java` — generates one Java source file per message type

## What Gets Generated

For each non-WKT, non-map-entry message type:

1. A `FooJsonEncoder.java` class implementing `GeneratedEncoder<Foo>`
2. Pre-computed `char[] NAME_*` constants for each field (format: `"fieldName":`)
3. A `writeFields()` method with inlined per-field encoding logic
4. A `META-INF/services/io.suboptimal.buffjson.GeneratedEncoder` file listing all encoders

## Field Handling

|         Category         |                                 Generated pattern                                  |
|--------------------------|------------------------------------------------------------------------------------|
| Scalar (no presence)     | `int v = msg.getId(); if (v != 0) { writeNameRaw; writeInt32(v); }`                |
| Scalar (optional)        | `if (msg.hasId()) { writeNameRaw; writeInt32(msg.getId()); }`                      |
| uint32/fixed32           | `writeInt64(Integer.toUnsignedLong(...))`                                          |
| int64 variants           | `writeString(Long.toString(...))`                                                  |
| uint64/fixed64           | `writeString(Long.toUnsignedString(...))`                                          |
| float/double             | Inline NaN/Infinity check                                                          |
| Enum                     | `msg.getStatusValue()` + `Status.forNumber(ev)` + `getValueDescriptor().getName()` |
| bytes                    | `Base64.getEncoder().encodeToString(v.toByteArray())`                              |
| Repeated                 | `msg.getFooList()`, check isEmpty, iterate                                         |
| Map                      | `msg.getFooMap()`, check isEmpty, iterate entries                                  |
| Oneof                    | `switch (msg.getFooCase())` with per-case typed accessor                           |
| Nested message (non-WKT) | `ProtobufMessageWriter.INSTANCE.writeMessage(jsonWriter, nested)`                  |
| Nested message (WKT)     | `WellKnownTypes.write(jsonWriter, nested)`                                         |

## Name Resolution

- Proto full name → Java class name mapping built from `FileDescriptorProto` options
- Respects `java_package`, `java_multiple_files`, `java_outer_classname`
- Nested messages use parent class as prefix: `Outer.Inner`
- Encoder class names flatten nesting: `Outer_InnerJsonEncoder`

## Important Edge Cases

- **`google.protobuf.Empty`** is NOT in the WKT set — it serializes as a regular empty message `{}`
- **`DynamicMessage`** cannot use generated encoders (would fail cast) — guarded in `ProtobufMessageWriter`
- **Map entry types** (`options.map_entry = true`) are skipped — they're synthetic
- **`writeNameRaw(char[])`** must be used (not `byte[]`) — `JSONWriterUTF16.writeNameRaw(byte[])` throws `UnsupportedOperation`

## Build

- Depends only on `protobuf-java` (for `CodeGeneratorRequest`/`CodeGeneratorResponse` and descriptor APIs)
- No shading needed — ascopes plugin resolves classpath automatically
- Must be built before consumer modules (listed before tests/benchmarks in parent POM)

## Dependencies

- `com.google.protobuf:protobuf-java` — CodeGeneratorRequest, FileDescriptor, FieldDescriptor

