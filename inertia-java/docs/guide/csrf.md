---
title: "CSRF and cookie handling"
description: "Wire cookie/header tokens and recover from rejection without replaying mutations."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/SecurityConfiguration.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/BrowserCsrfFailureHandler.java
verification:
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
  - inertia-java/examples/spring-react/frontend/e2e/auth.spec.ts
---

# CSRF and cookie handling

Keep CSRF protection in the application's security chain. The starter does not supply a universal security configuration, and Page headers do not authorize a write.

## Follow the example's cookie/header flow

The sample uses Spring Security's `CookieCsrfTokenRepository.withHttpOnlyFalse()`. Its browser request handler eagerly obtains the deferred token, accepts the plain token header used by the browser client, and retains the masked request-attribute behavior for form handling.

The readable CSRF cookie is specifically for that token transport; it is not a recommendation to make authentication/session cookies readable. Production HTTPS, Secure/SameSite/path/domain and trusted proxy settings belong to the application. Confirm the actual origin, cookie scope and browser behavior when changing them.

An initial page request obtains a token. The official client's same-origin form submission sends the matching header. After authentication/logout or cookie clearing, a previously held form token can be stale.

## Recover without replaying the write

For a recognized Inertia POST to a configured demo recovery route, `BrowserCsrfFailureHandler` queues a safe `_csrf` message and redirects to a fixed review Page. It does not run the rejected operation or copy incoming tokens, Referer or form values into the recovery destination. Other denied requests remain 403; storage failure produces a safe server error.

The Page tells the user to review the form and submit again. Preserving visible input does not mean the server accepted the first request. During a stale logout, the authenticated session remains active until a new explicit successful logout.

## Integrate your application

1. Configure the host security chain and token repository for the actual client transport.
2. Ensure initial/redirected Pages can obtain the current token.
3. Use fixed application-controlled recovery destinations and the same Inertia session namespace as normal handlers.
4. Show safe feedback and allow explicit resubmission; do not silently retry a mutation.
5. Test missing/stale cookies, token rotation at login/logout and non-Inertia requests.

The demo recovery map does not cover every application route. Extend it deliberately or provide your own handler. Never disable CSRF globally just to make a form succeed. See the source security configuration and browser tests for the exact implemented transport and review [forms](forms-validation.md) for error presentation.

## Upstream references

- [Spring Security's SPA CSRF integration](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)
- [Inertia CSRF handling](https://inertiajs.com/docs/v3/security/csrf-protection)
