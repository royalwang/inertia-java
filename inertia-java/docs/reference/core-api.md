---
title: "Core API map"
description: "Index request/config/context/response/page/codec/props/session/root and link exact generated symbols."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SessionStore.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CoreContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CancellationContractTest.java
---

# Core API map

Use `inertia-core` without a framework when your adapter can snapshot a request, own a response lifecycle and provide session delivery semantics. Exact signatures and nested types are in [Javadoc](javadoc.md); this map explains which contract each type owns.

## Request, response and rendering

| Public type | Responsibility |
| --- | --- |
| `InertiaRequest` | Immutable request inputs, normalized header access, version/partial selection and safe back navigation |
| `InertiaConfig` | Application version, root, components, shared data, SSR and presentation policy |
| `InertiaContext` | One request's queued shared/flash/errors/history effects and session reservation |
| `InertiaResponse` | Component/props response plus status, allowed headers and required-SSR policy |
| `Page` | Resolved immutable Page data; defensive JSON copies |
| `PageCodec` | Jackson-backed JSON values, parsing and HTML-safe Page serialization |
| `ResponseRenderer` | Select HTML/JSON, resolve props and prepare a response delivery |
| `HttpOutcome` | Validated status, multi-value headers and text body |
| `ProtocolPolicy` | Version conflict, redirect/location and representation policy |
| `RootView` | Trusted complete HTML assembly |
| `ConfiguredHttpUrl` | Validate configured HTTP(S) endpoint/origin inputs |
| `CspNonce` | Validate and carry a trusted nonce for root assembly |

Create a fresh context for every request. `share` and errors must be queued before resolving; some flash/history effects permit the resolving state. `render` constructs an `InertiaResponse`; it does not write transport bytes. Successful core rendering completes the reserved session snapshot before the adapter writes transport bytes. Aborting or failing before completion restores the reservation. A later write failure cannot roll back completed delivery or prove what the browser received. `commitRedirect` merges pending effects for the next request, requires a session when effects exist, and cannot run after rendering has consumed the context.

Do not call `abort` as a general retry operation after uncertain external writes. See [adapter ownership](../integrations/custom-adapter.md) for the preparation/commit boundary.

## Props and validation

| Public type | Responsibility |
| --- | --- |
| `Props`, `Prop` | Immutable declared dot paths and selection/loading/metadata modifiers |
| `PropsResolver` | Resolve selected providers with deadlines, bounded concurrency and cancellation |
| `ScrollPage` | Pagination metadata and optional wrapped item path |
| `ValidationErrors`, `ErrorBags` | Immutable ordered messages and named-bag presentation |
| `PropDefinitionException` | Invalid path or parent/child collision at declaration |
| `PropResolutionException` | Provider failure with its path and cause |

Declaration order controls successful output ordering. A fatal provider failure can terminate promptly and cancel siblings; it does not wait for declaration order. `rescue` is per-prop fallback and cannot fix an invalid declaration. Merge/once/scroll modifiers instruct the client; they do not create server-side persistence or authorization. Read [props loading](../props/loading.md) before choosing eager, lazy, optional or deferred behavior.

## Extension and observation

`SessionStore` defines reservation/complete/abort/merge contracts; `MemorySessionStore` is a local implementation, not distributed durable storage. `SsrGateway` returns renderer success/fallback; `SsrRequiredException` prevents silently accepting fallback for required pages. Request-owned cancellation is managed internally by the resolver; its implementation is not a public extension type.

`InertiaObserver` defines structured bounded diagnostics. `Observations` isolates ordinary observer failures and classifies events; `LoggingInertiaObserver` supplies a logging implementation. Observer callbacks run inline and must remain fast. See [metrics](metrics.md) for safe tags.

For executable imports and initialization use the retained [canonical API examples](../api-guide.md). Framework-specific transport behavior belongs in [Spring APIs](spring-api.md), not a second core implementation.
