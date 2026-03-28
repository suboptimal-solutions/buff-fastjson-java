# AGENTS.md - buff-fastjson-benchmarks

## Module Purpose

Contains proto definitions, JMH benchmarks, and conformance tests.
This module depends on both `buff-fastjson-core` and `protobuf-java-util`
(the latter only for the `JsonFormat.printer()` baseline in benchmarks and tests).

## Proto Files

- `simple_message.proto` — Flat message with basic scalar types + enum
- `complex_message.proto` — Nested messages, repeated fields, maps, oneof, bytes, Timestamp
- `conformance_test.proto` — Comprehensive proto3 test messages covering all field types,
  well-known types, wrappers, maps with various key types, oneof, recursive, explicit presence,
  custom json_name

## Benchmarks

`BenchmarkData.java` creates deterministic test messages (no `System.currentTimeMillis()`).
Two benchmark classes compare `BuffJSON.encode()` vs `JsonFormat.printer().print()`.

Run: `java -jar target/benchmarks.jar -wi 3 -i 5 -f 2`

## Conformance Tests

`Proto3JsonConformanceTest.java` — 74 tests across 14 nested test classes.
`BuffJSONTest.java` — 3 original integration tests.

Every test follows the pattern:
```java
String expected = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
String actual = BuffJSON.encode(message);
assertEquals(expected, actual);
```

## Build Gotchas

- **JMH annotation processor**: Must be in `<annotationProcessorPaths>` of maven-compiler-plugin,
  not just as a dependency. Otherwise `META-INF/BenchmarkList` won't be generated and the
  benchmark JAR won't work.
- **maven-shade-plugin**: Needs `AppendingTransformer` for `META-INF/BenchmarkList` and
  `META-INF/CompilerHints`. Also needs `ServicesResourceTransformer` for JMH's ServiceLoader.
- **protobuf-maven-plugin + JMH**: Both generate files during compilation. The ascopes protobuf
  plugin's `embedSourcesInClassOutputs` should be `false` (set in parent POM) to avoid conflicts.
- **Eclipse JDT**: Can overwrite Maven-compiled classes with broken stubs. Delete `.project`,
  `.classpath`, `.settings` if you see `Unresolved compilation problem` errors.
