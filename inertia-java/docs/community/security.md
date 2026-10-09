---
title: "Reporting security issues"
description: "Report vulnerabilities privately through GitHub and prepare a sanitized security report."
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

## Report a vulnerability

Use [GitHub private vulnerability reporting](https://github.com/royalwang/inertia-java/security/advisories/new) to send a report to the repository maintainers. Sign in to GitHub, complete the report form and submit it privately. Refer to [the security policy](https://github.com/royalwang/inertia-java/blob/main/inertia-java/SECURITY.md) for reporting information.

Keep sensitive details in the private report rather than a public issue or pull request.

## Prepare a private report

Include affected source/artifact versions, the trust boundary, prerequisites, impact and a minimal sanitized reproduction. Describe any mitigation and whether information is already public. Do not attach production credentials or user datasets; use synthetic data and privately coordinate disclosure with maintainers.

## Application responsibilities

The library does not replace authentication, authorization, CSRF policy, trusted-proxy configuration, cookie/TLS policy or a secure session backend. SSR receives resolved Page data and must remain a trusted internal service. Review [authentication](../guide/authentication.md), [proxy boundaries](../deployment/proxy-security.md), [root/CSP handling](../ssr/root-template.md) and third-party dependency inventory.

A stable security support and backport window has not yet been declared.
