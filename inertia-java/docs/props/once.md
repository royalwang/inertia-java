---
title: "Once props, expiry and refresh"
description: "Explain keys, expiry seconds, fresh/partial refresh and authorization independence."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/OnceTtlContractTest.java
  - inertia-java/compatibility/verify-once-ttl.mjs
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
---

# Once props, expiry and refresh

Once props let the official client reuse a previously loaded value. They do not create a server cache or eliminate authorization on the next request.

## Declare reuse

Use `.once()` for the default prop-path key, or `.onceAs(key)` for an explicit nonblank key. `.until(Duration)` adds a nonnegative TTL; no TTL means the protocol does not announce an expiry. `.fresh()` forces a value even if the client says it has the key.

The server reads loaded keys from `X-Inertia-Except-Once-Props`. Full Inertia visits can omit an already loaded once value. Explicit matching partial selection can fetch it again. Initial document visits still need the data to render a complete page.

## Choose key scope

Declare a shared once prop on every page where it is needed. A Page-local definition does not automatically become shared/global. Use keys that represent the same authorized value; do not reuse one identity's key as an application-wide permission cache.

The example's shared catalog has key `feed-catalog` and a 60-second TTL. Its load counter lets the browser tests distinguish reuse from another query. The refresh action explicitly requests a new value.

## Time and invalidation

Expiry metadata uses the resolver's server Clock. The Java/Rust semantic gate records the representation differences and measured expiry boundaries; do not infer cross-adapter equality from a volatile timestamp fixture. The official client decides when its held value is expired and should be requested again.

Changing authorization, identity or the data contract may require an explicit fresh request, history cleanup or a new build/navigation policy. A TTL is not a security boundary. Negative durations and blank custom keys fail definition validation.

## Verify reuse and refresh

Check first delivery, a repeat visit, explicit partial refresh, fresh override and the exact client expiry boundary. Inspect callback counts and retained browser value. Fixed-clock Java contracts and a live Rust semantic check cover server metadata, while the browser scenario covers the locked client's expiry decision. See [shared data](../guide/shared-data.md) for placement and [versioning](../concepts/versioning.md) for build changes.
