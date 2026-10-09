---
title: "Authentication and authorization"
description: "Integrate a real application Security chain, capture public DTOs, rotate sessions and authorize every visit."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/DemoAuth.java
  - inertia-java/examples/spring-react/frontend/src/auth.ts
  - inertia-java/examples/spring-react/frontend/src/pages/Login.tsx
verification:
  - inertia-java/examples/spring-react/frontend/e2e/auth.spec.ts
  - inertia-java/examples/spring-react/frontend/scripts/verify-browser-matrix.mjs
---

# Authentication and authorization

Inertia Java does not authenticate users. Use the host application's security chain and identity provider, then expose only data authorized for the current request. Inertia headers and frontend routing grant no permissions.

## Run the opt-in identity example

Build the example first. From `examples/spring-react/`, set `INERTIA_DEMO_PASSWORD` to a local password of at least 12 characters, then start the jar with `--inertia.demo-auth=true`. Node SSR runs separately as in the quick start. The local username is `demo`; no default password is supplied.

Visit `/login`, sign in, then open `/account`. POST `/logout` ends the session. Public `/users`, `/feed` and asset routes remain public. This in-memory demo is an integration reference, not an identity service or a production account store.

## Identity boundaries

Spring Security owns credential verification, BCrypt hashing, authentication filters, session fixation protection, CSRF and logout cleanup. The sample uses `newSession` at successful login, discards anonymous application state, queues `clearHistory` in the new configured namespace and redirects to fixed destinations.

An unauthenticated ordinary account visit redirects to login. An Inertia visit receives 409 with `X-Inertia-Location`, forcing a fresh login document. Account Pages use encrypted history and private/no-store caching. Login Pages clear history. The login form uses multipart form data because the standard authentication filter reads servlet form parameters.

The demo also sends a same-origin logout revision hint so other open tabs can replace their documents. Local storage may be unavailable; every protected server request must still enforce authorization. Client history cleanup is not revocation.

## Adapt to real identity

Replace the in-memory account with your chosen identity provider. Define record/tenant permissions before prop sources are scheduled. Capture explicit immutable authorized data rather than expecting security ThreadLocals to follow an executor callback. Decide production session storage, cookie/HTTPS/proxy policy and logout behavior in the application.

Never replay a rejected stale-token logout: the current session stays authenticated until the user explicitly submits again. Test login failure, successful rotation, unauthorized navigation, logout across tabs and actual servlet idle expiry. The browser matrix covers both SSR and CSR identity flows and a real one-minute session expiry. See [CSRF](csrf.md) and [history](history-bigint.md) for the related client state boundaries.

## Upstream references

- [Spring Security CSRF and SPA integration](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [session fixation protection](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [logout handling](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
