# AGENTS.md - buff-fastjson-tests

## Module Purpose

Conformance tests verifying that `BuffJSON.encode()` produces output identical to
`JsonFormat.printer().omittingInsignificantWhitespace().print()` for all proto3 JSON features.

## Test Structure — 84 tests total

- `BuffJSONTest.java` — 3 smoke tests (scalar, default, complex messages)
- `Proto3JsonConformanceTest.java` — 81 tests in 16 nested classes:
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
  - AnyTests (6): regular message, Duration, Timestamp, nested Any, empty, default inner
  - EmptyTests (1): google.protobuf.Empty serialization
  - EmptyMessages (5): all message types empty

## Test Pattern

Most tests:

```java
String expected = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
String actual = BuffJSON.encode(message);
assertEquals(expected, actual);
```

Any tests use `Encoder` with `TypeRegistry`:

```java
Encoder encoder = BuffJSON.encoder().withTypeRegistry(registry);
String actual = encoder.encode(message);
```

## Proto Files

- `conformance_test.proto` — comprehensive proto3 test messages including TestAny, TestEmpty

## Dependencies

- `buff-fastjson-core` — the library under test
- `protobuf-java-util` — reference `JsonFormat.printer()` for comparison
- `junit-jupiter` — test framework

