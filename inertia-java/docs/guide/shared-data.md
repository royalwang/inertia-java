---
title: "Shared data and overrides"
description: "Explain internal/config/request/page precedence, errors overrides and dot-path conflicts."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Props.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaConfig.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/PropDefinitionDiagnosticTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/ConfigPresentationTest.java
---

# Shared data and overrides

Define a small, authorized set of common props for pages that need it. Shared data is still sent to the browser and Node renderer; it is not private server context.

## Choose a definition scope

| Scope | Definition | Lifetime |
| --- | --- | --- |
| Application configuration | `InertiaConfig.shared` request-to-Props callback | Reusable callback, invoked for the current request |
| Request | `context.share(key, value)` | Before resolution starts on one context |
| Page | The response's `Props` | One Page |

The example's configuration shares `appName` and a once-loaded catalog. The canonical Spring API example shares a request-level application label. Keep identity-specific data in explicit authorized DTOs; do not retain a servlet/security object in a globally shared value.

## Understand precedence

Definitions overlay from internal `errors` to config shared, request shares and finally Page props. The last exact-key definition wins while position retains its first declaration order. Only the winning supplier executes. This is definition replacement, not recursive merging of object values.

Reserve `errors` when using the built-in validation flow. The library permits overriding it, including its normal loading behavior, and emits an `errors_override` diagnostic. Parent/child path collisions such as defining both a scalar `auth` and `auth.name` are rejected before suppliers run.

For explicit composition, `Props.from(Source, props)` and `Props.overlay(...)` retain provenance. `overrides()` exposes an immutable schema report without values; it is not inserted into Page JSON.

## Avoid accidental exposure

`withSharedPropKeys(false)` omits shared-key metadata, not shared values. Hiding metadata is not authorization or a way to remove sensitive props. A custom `withUrlResolver(...)` changes Page presentation only; it does not rewrite routing, redirects or the actual request URL.

Keep common payloads small. Use partial/deferred/once behavior deliberately, remembering that once is browser reuse rather than a server cache. Shared suppliers follow the same selection, concurrency and cancellation rules as Page suppliers.

Verify the final Page and supplier counts for duplicate keys, plus both full and partial visits. Review [prop diagnostics](../props/diagnostics.md) when an override or schema conflict appears, and [ownership](../concepts/ownership.md) before moving shared callbacks onto worker threads.
