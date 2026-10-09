---
title: "Page assertions"
description: "Use AssertablePage JSON Pointer assertions with separate status/header and browser checks."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-testing/src/main/java/io/inertia/testing/AssertablePage.java
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/MvcContractTest.java
verification:
  - inertia-java/scripts/verify-maven-consumer.py
---

# Page assertions

Use `AssertablePage` from `inertia-testing` to inspect Page payloads in server adapter tests. Assert HTTP status, headers and session transitions with your HTTP test framework as well.

## Parse a response body

`AssertablePage.fromBody(body)` accepts raw Page JSON or the standard HTML document's double-quoted `data-page` script. Its fluent `component(expected)`, `equals(jsonPointer, expected)` and `missing(jsonPointer)` methods throw `AssertionError` when the contract differs. JSON Pointer uses `/props/user/name`, not prop declaration dot paths.

The helper uses `PageCodec` values for comparison, so expected collections/maps become JSON structures. `data()` returns a deep copy; modifying it does not modify the helper's stored assertion state.

## Select meaningful assertions

Assert the component and user-visible prop values. For partial requests, assert omitted provider output as well as included output. For session delivery, compare the initial Page and a subsequent reload: a single response cannot establish one-time consumption. For a version conflict, assert 409 and its location header before trying to parse a Page body.

Do not use this helper to infer hydration, client-side merge, CSRF or cookie behavior. Its HTML extraction recognizes the standard script shape; arbitrary custom root templates may require a DOM parser or direct Page JSON assertions.

## Compile and run

The canonical API guide includes the compiled core/Spring examples and independent consumer verification. Existing MVC tests show the assertion helper alongside MockMvc status/header checks. From `inertia-java/`, run `./mvnw --batch-mode test` for the reactor's unit/integration contracts, or select the affected test with Maven's standard test selector while allowing modules without that test.

A passing JSON assertion is one layer of evidence. Follow [browser acceptance](browser-tests.md) for real official-client navigation and [fixtures](fixtures.md) for compatibility oracles.
