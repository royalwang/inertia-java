---
title: "State, identity and ownership"
description: "Separate immutable app config, per-request state, business transactions and explicit captured identity."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# State, identity and ownership

Reuse immutable configuration and bounded infrastructure. Keep mutable Page construction and delivery state within one request. This distinction prevents cross-user data leaks, duplicate effects and accidental cancellation of another request's work.

## Object lifetimes

| Object | Lifetime / owner |
| --- | --- |
| `InertiaConfig` | Reusable application configuration; methods return new settings values |
| `PageCodec` | Reusable private serialization configuration; copies its supplied ObjectMapper |
| `PropsResolver`, `ResponseRenderer` | Reusable application services |
| Executor | Application-owned for explicit core wiring; Boot owns its default bean lifecycle |
| `HttpSsrGateway` | Reusable trusted-peer client with bounded concurrency |
| `InertiaRequest` | Immutable snapshot of one request |
| `InertiaContext`, `InertiaResponse` | Mutable, request-owned and single-use |
| Session store | One user's storage domain with explicit namespace and atomic delivery SPI |
| Async source stage | Request-owned work whose cancellation does not harm another request |

Do not retain a context/response in a singleton controller field. Do not attach every user to one global `MemorySessionStore`. Do not return a shared future from a source if cancellation of one Page could cancel all its consumers.

## Authorization before scheduling

Prop suppliers can execute on a bounded worker executor. Security, servlet, transaction and request ThreadLocals are not automatically propagated. Capture already-authorized immutable DTOs or explicit identifiers before scheduling; use the application's transaction/security boundaries for any work done later.

Lazy, optional, deferred and once control loading/client reuse. None replaces authorization. A hidden frontend component or a partial-selection flag must not be the only check protecting a value.

## Shared props are definitions, not global mutable values

Definitions overlay in this order: internal `errors`, config shared props, request shares, Page props. The last exact-key definition wins; its supplier alone executes. Position retains the first declaration order. Parent/child path conflicts fail before source execution.

Keep `errors` reserved when relying on built-in validation. The library permits overriding it and emits diagnostics, so applications must impose that convention themselves. Overlay diagnostics are schema information, not public Page data.

## Session delivery

The session SPI atomically reserves stored delivery, then completes or aborts that reservation once. Newly queued effects merge according to the SPI contract. Failed rendering restores stored delivery and discards failed-request pending effects.

`HttpSessionStore` isolates namespace state and uses servlet session coordination. Boot registers the mutex listener; explicit MVC integrations must register it. `MemorySessionStore` is a single-node implementation. Neither establishes cluster coordination, failover semantics or exactly-once network delivery.

A sessionless Page is valid. Cross-redirect flash/errors require a store; attempting to commit them without one fails. Namespace settings must stay consistent across request, advice and custom security handlers.

See [the lifecycle](request-lifecycle.md) for failure order and [the API guide](../api-guide.md#sessions-validation-and-mvc-exceptions) for exact integration rules.
