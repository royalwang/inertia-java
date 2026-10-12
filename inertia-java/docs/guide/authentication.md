---
title: "Authentication and authorization"
description: "Integrate a real application Security chain, capture public DTOs, rotate sessions and authorize every visit."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/DemoAuth.java
  - inertia-java/examples/spring-react/frontend/src/auth.ts
  - inertia-java/examples/spring-react/frontend/src/pages/Login.tsx
  - inertia-java/examples/spring-vue/src/main/java/io/inertia/example/vue/DemoAuth.java
  - inertia-java/examples/spring-vue/frontend/src/pages/Login.vue
verification:
  - inertia-java/examples/spring-react/frontend/e2e/auth.spec.ts
  - inertia-java/examples/spring-react/frontend/scripts/verify-browser-matrix.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-auth.mjs
---

# Authentication and authorization

Inertia Java does not authenticate users. Use the host application's security chain and identity provider, then expose only data authorized for the current request. Inertia headers and frontend routing grant no permissions.

## Run the opt-in identity example

Build the example first. From `examples/spring-react/`, set `INERTIA_DEMO_PASSWORD` to a local password of at least 12 characters, then start the jar with `--inertia.demo-auth=true`. Node SSR runs separately as in the quick start. The local username is `demo`; no default password is supplied.

The Vue example accepts the same environment variable and startup flag from `examples/spring-vue/`. Build its frontend and run its own Node renderer as described in the Vue guide.

Visit `/login`, sign in, then open `/account`. POST `/logout` ends the session. Public `/users`, `/feed` and asset routes remain public. This in-memory demo is an integration reference, not an identity service or a production account store.

## Identity boundaries

Spring Security owns credential verification, BCrypt hashing, authentication filters, session fixation protection, CSRF and logout cleanup. The sample uses `newSession` at successful login, discards anonymous application state, queues `clearHistory` in the new configured namespace and redirects to fixed destinations.

An unauthenticated ordinary account visit redirects to login. An Inertia visit receives 409 with `X-Inertia-Location`, forcing a fresh login document. Account Pages use encrypted history and private/no-store caching. Login Pages clear history. The login form uses multipart form data because the standard authentication filter reads servlet form parameters.

The demo also sends a same-origin logout revision hint so other open tabs can replace their documents. Local storage may be unavailable; every protected server request must still enforce authorization. Client history cleanup is not revocation.

## Delivery storage and authentication failures

Authentication success, failure and CSRF recovery use the application's configured `InertiaSessionStoreFactory`. A Redis delivery configuration does not replace the application's host session repository: sharing authentication across nodes also requires a shared Spring Session repository and lifecycle integration.

If delivery storage fails in the login success handler, the example clears the saved SecurityContext and propagates the failure instead of redirecting to the account. Do not silently fall back to local storage. Distributed host-repository failures require application-specific reconciliation; a browser redirect cannot establish atomic revocation across repositories.

## Shared sessions across application nodes

Build the selected example with its optional Redis profile from the Java project root:

```sh
./mvnw -pl examples/spring-react -am -Predis-session package
./mvnw -pl examples/spring-vue -am -Predis-session package
```

Run each application with `--inertia.demo-auth=true`, the same operator-supplied `INERTIA_DEMO_PASSWORD`, and matching host/session settings. Configure `spring.data.redis.host`, `spring.data.redis.port` and `spring.session.redis.namespace` for shared authentication. Separately select `inertia.session.store=redis` and configure `inertia.session.redis.host`, `inertia.session.redis.port` and `inertia.session-namespace` for Inertia delivery. Separate namespaces isolate responsibilities; every node serving the same application must agree on both. See [Redis sessions](../integrations/custom-session.md).

The default `inertia.demo-fixation=new-session` discards anonymous application attributes. The optional `change-session-id` example retains ordinary host attributes, requires the built-in Redis lifecycle factory and revokes the previous delivery domain before rotating the ID. Choose the strategy deliberately; copying an authentication session must not copy old flash/errors into the new identity.

Request handles reject the idle deadline reported by their trusted host session. A cached request cannot discover external repository deletion or a timeout shortened on another node. Arrange equivalent delivery revocation in application administration or identity-provider hooks. Already admitted business operations need the application's own authorization/revocation policy.

When an administrative operation has a trusted `HttpSession`, invoke the registered `RedisHttpSessionStoreFactory.beforeHostChange(session)` before invalidating/deleting that host session, and propagate failure. A repository ID alone is not an exposed factory revocation API. For offline revocation, supply a host adapter that propagates the same lifecycle boundary; do not decode the factory's private metadata or report delivery revocation merely because repository deletion succeeded.

Failed delivery revocation is an error, not successful logout. Restore connectivity and explicitly reconcile/retry the host operation; do not replay an unknown Redis mutation. After a nonpersistent Redis restart, sessions are lost and users must authenticate again. This integration does not claim Cluster/Sentinel or failover durability.

## Adapt to real identity

Replace the in-memory account with your chosen identity provider. Define record/tenant permissions before prop sources are scheduled. Capture explicit immutable authorized data rather than expecting security ThreadLocals to follow an executor callback. Decide production session storage, cookie/HTTPS/proxy policy and logout behavior in the application.

Never replay a rejected stale-token logout: the current session stays authenticated until the user explicitly submits again. Test login failure, successful rotation, unauthorized navigation, logout across tabs and actual servlet idle expiry. The browser matrix covers both SSR and CSR identity flows and a real one-minute session expiry. See [CSRF](csrf.md) and [history](history-bigint.md) for the related client state boundaries.

## Upstream references

- [Spring Security CSRF and SPA integration](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [session fixation protection](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [logout handling](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
