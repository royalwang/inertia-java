---
title: "Headers and Page metadata"
description: "Document fields, omission/null, merge/once/scroll/history/bigint and Java differences."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaRequest.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ProtocolPolicy.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Page.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/HttpOutcome.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustHttpParityTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustParityTest.java
---

# Headers and Page metadata

The adapter translates the same resolved Page into HTML for a document visit or JSON for an Inertia visit. Preserve representation headers through proxies and use the current asset version in client requests.

## Request headers

| Header | Meaning |
| --- | --- |
| `X-Inertia` | Select Inertia request handling |
| `X-Inertia-Version` | Current browser asset version; stale/missing on an Inertia GET can trigger 409 |
| `X-Inertia-Partial-Component` | Component that partial selection applies to |
| `X-Inertia-Partial-Data` | Included dot paths |
| `X-Inertia-Partial-Except` | Excluded paths |
| `X-Inertia-Error-Bag` | Selected validation bag |
| `X-Inertia-Reset` | Props whose merge state should reset |
| `X-Inertia-Except-Once-Props` | Reusable once keys the client already has |
| `X-Inertia-Infinite-Scroll-Merge-Intent` | Client scroll merge intent |

Prefetch detection also checks purpose headers (`Purpose`, `Sec-Purpose`, `X-Moz`). Partial component mismatch resolves a normal Page instead of applying another component's selection. Selection and [once metadata](../props/once.md) must never be used for authorization.

## Response headers and outcomes

JSON Page responses carry `X-Inertia` and JSON content type; HTML uses its document content type. `Vary: X-Inertia` separates the representations. Version conflict uses 409 and `X-Inertia-Location` before controller execution. Location/redirect helpers select the appropriate protocol response; PUT/PATCH/DELETE 302 redirects are normalized to 303.

The implementation also handles `X-Inertia-Redirect` and its version-related response metadata. Application headers are validated: `InertiaResponse.withHeader` reserves `x-inertia*`, content type/length and transfer encoding. `HttpOutcome` validates header names/values and preserves multi-value headers and `Vary: *`. Do not override protocol headers to disguise stale assets.

## Page fields

| Field | Value / omission |
| --- | --- |
| `component`, `props`, `url`, `version` | Required Java Page values; props is an object and version is text |
| `preserveBigIntegers`, `encryptHistory` | Presentation/history flags when applicable |
| `flash`, `clearHistory`, `preserveFragment` | Pending delivery effects when present |
| `sharedProps` | Shared top-level key list when exposed; hiding metadata keeps actual values |
| `deferredProps` | Group → deferred prop paths |
| `rescuedProps` | Paths with resolved rescue output |
| `deepMergeProps`, `prependProps`, `mergeProps`, `matchPropsOn` | Arrays of prop paths describing client merge behavior |
| `scrollProps` | Path → pageName/previousPage/nextPage/currentPage/reset |
| `onceProps` | Reuse key → `{ prop, expiresAt }`; expiry is null or epoch milliseconds |

`props.errors` is normally installed by the framework and can be deliberately overridden with diagnostics. Client metadata describes merge/loading behavior; the server does not retain the browser's merged Page. The Node input guard's permissive version shape is not the Java `Page` constructor contract.

The checked Rust HTTP/Page fixtures are the compatibility oracle, with four intentional Java HTTP differences. See [fixture policy](../testing/fixtures.md) before changing wire semantics.
