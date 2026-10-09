---
title: "Proxy, TLS and private pages"
description: "Provide deployment-owned proxy trust, cookies/CSRF, private caching and internal renderer routing."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaRequest.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ConfiguredHttpUrl.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/SecurityConfiguration.java
  - inertia-java/deploy/README.md
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustHttpParityTest.java
  - inertia-java/examples/spring-react/frontend/e2e/auth.spec.ts
---

# Proxy, TLS and private pages

Configure the ingress for the actual HTTP and identity boundaries. The sample launcher binds Java to loopback by default and never publishes the renderer through ingress.

## Preserve representation and origin

Route browser traffic to Java and keep immutable asset URLs available. Preserve the request's intended external origin using a deliberately trusted proxy-header policy. `InertiaRequest` requires an absolute URI, and back-navigation origin checks use the adapter's snapshot; the library cannot decide which upstream headers your infrastructure may trust.

Preserve `Vary: X-Inertia` and application cache headers so HTML and JSON are not confused. Authenticated/user-specific responses should have deliberate private/no-store policy. Check behavior through the actual proxy/CDN rather than only hitting Java directly.

## Protect the renderer and cookies

The renderer receives resolved Page data and is a trusted internal peer. Keep its endpoint off public routing and apply your network/process access controls. The gateway does not forward browser cookies, authentication or arbitrary headers.

Terminate TLS according to your host policy and qualify session/CSRF cookie Secure, SameSite, domain and path behavior under the final browser origin. A readable CSRF token cookie is distinct from a session/authentication cookie. The sample's local settings do not establish production cookie correctness.

## Acceptance through ingress

Verify initial HTML, versioned Page JSON, a stale-version refresh, mutation redirects, CSRF token rotation and logout. Check old/new immutable assets, private caching, absolute navigation destinations and the absence of a public Node route. Include a browser with an existing session during release switching.

No reverse-proxy/TLS/DNS changes are performed by the local verification scripts. There is no generic nginx snippet here claiming to encode your trust model. Record target-host evidence and rollback before enabling production traffic. Use [authentication](../guide/authentication.md), [CSRF](../guide/csrf.md) and [release switching](rolling-upgrades.md) for the connected application behavior.
