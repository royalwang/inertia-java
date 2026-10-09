---
title: "Spring integration tests"
description: "Test typed controllers, validation/advice, flash recovery, non-Inertia routes and bean overrides."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/MvcContractTest.java
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/MvcOutcomeAdviceContractTest.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaHandlerValidator.java
verification:
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaOverridesTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/HttpSessionStoreTest.java
---

# Spring integration tests

Use Spring tests to prove adapter registration, HTTP semantics and session/error ownership. Browser behavior requires a separate real-client check.

## Choose the boundary

| Change | Focused contracts |
| --- | --- |
| Default/override beans or budgets | `InertiaAutoConfigurationTest`, `InertiaOverridesTest` |
| Handler declarations | `InertiaHandlerValidatorTest` |
| MVC Page/redirect behavior | `MvcContractTest`, `InertiaRequestLifecycleTest` |
| Delivery reserve/abort/identity | `HttpSessionStoreTest`, `MvcSessionFailureTest` |
| Exception advice | `MvcAdviceContractTest`, `MvcOutcomeAdviceContractTest` |
| Validation presentation | `ValidationBridgeTest`, `JakartaValidationBridgeTest`, `MvcAllErrorsTest` |
| Observer/metrics | `MvcObservationTest`, `InertiaMetricsTest` |

From `inertia-java/`, a focused example is `./mvnw --batch-mode -pl examples/spring-react -am -Dtest=MvcContractTest -Dsurefire.failIfNoSpecifiedTests=false test`. Run the broader reactor checks when changes cross module contracts; do not treat this one class as the whole compatibility suite.

## Assert the actual lifecycle

Snapshot the original session, send a request and assert status/representation/headers plus Page data. For flash/errors, verify redirect merge, consumption on a successful Page and restoration after a failure before completion. Separately assert that a transport write failure after core completion cannot roll back that delivery. For advice that invalidates a session, assert effects remain owned by the original identity/store and are not copied to a newly created session.

Check rejected handler shapes at startup rather than waiting for accidental runtime handling. Verify ordinary REST/download/SSE routes still use Spring's native path. When overriding a bean, assert the override is used and optional facilities remain absent when prerequisites are missing.

## Know what remains

MockMvc does not launch the production Node renderer or execute React. Test CSRF token rotation, official-client forms, merge/once/scroll and logout/history in the [browser matrix](browser-tests.md). Actual proxy/TLS, distributed storage and host service behavior need their own integration environment.

Keep tests tied to behavior and failure feedback. Pure documentation edits can reuse unchanged runtime evidence; changed Java snippets/configuration need compilation and the affected contracts.
