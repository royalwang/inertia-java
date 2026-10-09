---
title: "Reporting security issues"
description: "Describe supported scopes and a maintainer-configured private reporting channel, without inventing an address."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/README.md
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/SecurityConfiguration.java
  - inertia-java/scripts/dependency-inventory.py
verification:
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/BrowserCsrfRecoveryTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CspNonceTest.java
---

# Reporting security issues

Do not publish credentials, cookies, personal Page data or a sensitive exploit reproduction in a public issue or pull request.

## Current reporting status

A private security reporting channel has not yet been published for this module. Refer to [the security policy](https://github.com/royalwang/inertia-java/blob/main/inertia-java/SECURITY.md) for reporting information.

Until a private reporting channel is available, keep sensitive details private.

## Prepare a private report

When a verified channel becomes available, include affected source/artifact versions, the trust boundary, prerequisites, impact and a minimal sanitized reproduction. Describe any mitigation and whether information is already public. Do not attach production credentials or user datasets; use synthetic data and privately coordinate disclosure with maintainers.

## Application responsibilities

The library does not replace authentication, authorization, CSRF policy, trusted-proxy configuration, cookie/TLS policy or a secure session backend. SSR receives resolved Page data and must remain a trusted internal service. Review [authentication](../guide/authentication.md), [proxy boundaries](../deployment/proxy-security.md), [root/CSP handling](../ssr/root-template.md) and third-party dependency inventory.

A stable security support and backport window has not yet been declared.
