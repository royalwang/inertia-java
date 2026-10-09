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

A maintainer-approved private security reporting address or entry has not yet been recorded for this Java module. GitHub's private vulnerability reporting capability must be confirmed enabled before it can be advertised as the project's report channel. The documentation therefore does not invent an email address, a private endpoint or a response SLA.

Until an approved private channel is published, keep sensitive details private. The module `SECURITY.md` records this limitation. This is an explicit open-source launch prerequisite, not a claim that reports submitted elsewhere will reach a monitored security team.

## Prepare a private report

When a verified channel becomes available, include affected source/artifact versions, the trust boundary, prerequisites, impact and a minimal sanitized reproduction. Describe any mitigation and whether information is already public. Do not attach production credentials or user datasets; use synthetic data and privately coordinate disclosure with maintainers.

## Application responsibilities

The library does not replace authentication, authorization, CSRF policy, trusted-proxy configuration, cookie/TLS policy or a secure session backend. SSR receives resolved Page data and must remain a trusted internal service. Review [authentication](../guide/authentication.md), [proxy boundaries](../deployment/proxy-security.md), [root/CSP handling](../ssr/root-template.md) and third-party dependency inventory.

The project currently has snapshot documentation rather than a declared stable support window. A future release policy must identify maintained versions and fixes before promising backports. Public dependency notices and LICENSE/NOTICE do not constitute a security audit or vulnerability-free guarantee.
