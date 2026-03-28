# AGENTS.md - buff-fastjson-tests

## Module Purpose

Conformance tests verifying that `BuffJSON.encode()` produces output identical to
`JsonFormat.printer().omittingInsignificantWhitespace().print()` for all proto3 JSON features.

## Test Structure

- `BuffJSONTest.java` — 3 integration tests (simple, default, complex messages)
- `Proto3JsonConformanceTest.java` — 74 tests in 14 nested classes:
  - ScalarTypes (13): all types, boundaries, NaN, Infinity, -0.0, unicode, escapes, bytes
  - RepeatedFields (3): all scalar types, empty, single element
  - Enums (3): values, default omission, repeated
  - NestedMessages (4): nested, repeated, empty, recursive (3 levels)
  - OneofFields (7): int/string/bool/message/enum, not set, default with presence
  - MapFields (5): string/int/bool keys, all value types, empty
  - TimestampTests (5): basic, nanos, full nanos, epoch, pre-epoch
  - DurationTests (4): basic, nanos, negative, zero
  - FieldMaskTests (2): camelCase path joining, empty
  - StructTests (8): struct, nested struct, all Value kinds, list, empty
  - WrapperTests (11): all 9 types, zero with presence, all combined
  - ExplicitPresence (3): set, set-to-default, not set
  - CustomJsonName (1): json_name annotation
  - EmptyMessages (5): all message types empty

## Test Pattern

Every test follows:
```java
String expected = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
String actual = BuffJSON.encode(message);
assertEquals(expected, actual);
```

## Proto Files

- `conformance_test.proto` — comprehensive proto3 test messages: all scalar types, all well-known types
  (wrappers, Timestamp, Duration, FieldMask, Struct/Value/ListValue), maps with all key types,
  oneof, recursive messages, explicit presence, custom json_name

## Dependencies

- `buff-fastjson-core` — the library under test
- `protobuf-java-util` — reference `JsonFormat.printer()` for comparison
- `junit-jupiter` — test framework
