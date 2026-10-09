---
title: "Lost flash and validation errors"
description: "Diagnose namespace/bag/session/CSRF, delivery restoration and duplicate effects."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ErrorBags.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/BrowserCsrfRecoveryTest.java
---

# Lost flash and validation errors

Trace the redirect and subsequent Page delivery using the same identity/store/namespace. Flash/errors are request-owned session effects, not a persistent prop cache.

## Check the request path

Confirm that the mutation passed authentication/CSRF and reached validation. Inspect the actual redirect status/location and the next request's cookies. A CSRF rejection is not a validation bag, and JSON-only assertions cannot establish cookie/token rotation.

Queue errors before rendering and use the intended default/named bag. Check `X-Inertia-Error-Bag` and `all-errors` presentation. The library preserves ordered messages and excludes rejected values from bridges; changing a UI to read another bag can look like lost server errors.

## Check ownership and consumption

A redirect with pending effects needs a session. Verify the configured namespace is the same on both requests. A successful Page consumes its reserved delivery; refresh should no longer show one-time flash. Prefetch and rendering failures before completion must not consume another request's reserved snapshot. Core rendering completes its delivery before the adapter writes; a later write failure cannot restore an already completed delivery. Do not describe that boundary as exactly-once browser receipt.

Do not share an `InertiaContext` across requests or write after completion. Duplicate flash keys within a request fail deliberately. If advice invalidates identity, do not transfer the original reserved effects into a new session. A custom backend must implement atomic reservation/complete/abort semantics rather than blind read/delete.

## Recover and verify

Correct the bag/namespace/cookie/ownership source, then run mutation → redirect → Page → refresh. Include overlapping requests and a failed delivery when changing a store. An unknown storage result requires reconciliation; replaying the mutation may duplicate business writes.

See [flash/session delivery](../guide/flash-session.md), [forms](../guide/forms-validation.md), [custom session stores](../integrations/custom-session.md) and [Spring tests](../testing/spring-tests.md).
