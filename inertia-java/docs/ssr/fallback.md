---
title: "CSR fallback and required SSR"
description: "Separate renderer failure from props/authorization failure and choose per-page fail policy."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaResponse.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RequiredSsrTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
  - inertia-java/deploy/verify-release.mjs
---

# CSR fallback and required SSR

Decide whether a page remains useful when the renderer is unavailable. The default supplies a client-rendered shell; selected pages can require server HTML and fail safely instead.

## Page policy

| Choice | HTML behavior when Node fails | Inertia JSON behavior |
| --- | --- | --- |
| Default | CSR shell with Page data | Normal Page JSON |
| `withoutSsr()` | CSR shell without attempting Node | Normal Page JSON |
| `requireSsr()` | Safe 503 when SSR cannot succeed | Normal Page JSON |

`withoutSsr()` and `requireSsr()` are ordered builder choices; the last call wins. Required SSR is a presentation policy for document requests, not a dependency that blocks every JSON navigation.

Default fallback still requires valid client assets and JavaScript. With JavaScript disabled, the empty shell has no rendered page content. Do not label that result SSR success merely because an enabled browser eventually displays a page.

## Distinguish data and renderer failures

Only the SSR stage can choose this fallback. An unrescued prop query failure, schema conflict or session failure remains a Page failure and follows the error path. Masking those into CSR would not repair missing/unauthorized business data.

A required-SSR failure should not reflect exception text, Page payloads or peer response bodies. Use safe status/content and structured server diagnostics. Decide whether clients can recover by normal navigation or whether the application needs an explicit retry/review action.

## Exercise the policy

Start a healthy example, verify JavaScript-disabled HTML and interactive hydration, then stop the renderer you own. Check default HTML still returns a shell and mounts with JavaScript. Check required HTML returns 503 while a valid versioned JSON visit remains available. Restore the renderer and verify actual content returns without assuming a health probe alone proves it.

The browser failure scenarios and core required-SSR tests cover these boundaries. Deployment verification kills only its owned renderer and confirms Java liveness plus CSR navigation/forms. Use [error handling](../guide/errors.md) for earlier failures and [gateway diagnostics](gateway.md) for the cause of a renderer fallback.

## Upstream references

- [HttpClient cancellation](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html#sendAsync(java.net.http.HttpRequest,java.net.http.HttpResponse.BodyHandler))
