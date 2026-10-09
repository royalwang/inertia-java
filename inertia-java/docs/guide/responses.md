---
title: "Responses, status and headers"
description: "Set status/business headers, use HttpOutcome redirects/location/back, and preserve protocol ownership."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaResponse.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/HttpOutcome.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ProtocolPolicy.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustHttpParityTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
---

# Responses, status and headers

Return a Page for content and an `HttpOutcome` for a protocol redirect/location or another small text outcome. Spring's typed adapter performs the context and protocol finalization before writing bytes.

## Choose the result

| Need | API |
| --- | --- |
| Render a registered page | `new InertiaResponse(component, props)` or `context.render(...)` |
| Business status, such as a safe missing page | `response.status(404)` |
| Application cache/security header | `response.withHeader(name, value)` |
| Redirect after a form | `ProtocolPolicy.redirect(applicationTarget)` |
| Force a full-document navigation | `context.location(applicationTarget)` |
| Return to a safe referring page | `context.back()` / `backWithErrors(...)` |
| Data only for the root view | `response.withViewData(name, value)` |

An `InertiaResponse` is a mutable, single-use builder. It is not the resolved JSON Page. Its `withHeader` rejects `X-Inertia*`, content type/length and transfer encoding because those belong to the transport. Each builder header key holds one value; use the host framework for responses requiring other transfer semantics.

`HttpOutcome` carries a text body and immutable multivalue headers. It is not a file/stream API. Use Spring's ordinary file and streaming abstractions for those responses.

## Redirect deliberately

The redirect helper creates 302. Post-policy converts Inertia PUT/PATCH/DELETE redirects to 303. Fragment/prefetch and empty-body policy also run centrally. Do not duplicate those rules inside each controller, and do not manually call `commitRedirect()` in an ordinary typed Spring handler: the adapter owns it.

`back()` accepts only the current origin and falls back to `/`. Other redirect targets remain application-owned; choose known destinations rather than reflecting arbitrary input.

## Cache correctly

Authenticated or user-specific pages normally need a deliberate private/no-store policy. Protocol responses vary on Inertia representation. A proxy/CDN must preserve that distinction. `withViewData` does not insert a value into Page props and does not make a custom template value safe to interpolate without escaping.

Inspect status, `Location`, `Vary`, Page JSON and HTML separately. `AssertablePage` checks Page content only. The example's `/missing`, form redirect and ordinary `/api/health` demonstrate different response paths. See [error handling](errors.md) for exceptions and [the protocol](../concepts/protocol.md) for version refreshes.
