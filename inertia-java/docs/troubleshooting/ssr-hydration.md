---
title: "Missing SSR and hydration mismatch"
description: "Inspect receipt/root/endpoint, actual HTML and logs, then distinguish fallback from hydration errors."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/HttpSsrGateway.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/SsrHealthMonitor.java
  - inertia-java/examples/spring-react/frontend/src/ssr.tsx
verification:
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-failures.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-health.mjs
---

# Missing SSR and hydration mismatch

Distinguish server HTML, renderer health and client hydration before changing code. A working hydrated page may still have fallen back to CSR.

## Identify the symptom

Disable JavaScript and request a known SSR page. Inspect the document body and Page script. If content appears only after JavaScript, inspect the SSR decision/fallback reason. If HTML is present but hydration warns, compare server/client component output and identities.

Check [fallback reasons](../reference/errors.md) for disabled/excluded, overload, transport/status, invalid response and build/root mismatches. `UNKNOWN` health means no result yet; `UP` is a cached health shape check and does not prove this Page renders. Do not equate those independent signals.

## Correct the owning input

For connection errors, confirm the internal Node address/port and that the renderer is actually running. For build mismatch, rebuild Java-facing receipt and client/SSR outputs coherently and restart the matching renderer. For root mismatch, align Java root, renderer root and browser mounting code. For exclusions, verify whether the selected route intentionally uses CSR.

For hydration mismatch, inspect browser-only globals, nondeterministic time/random values, locale and data that differs between server and client. Keep component registrations aligned. The Java transport cannot repair a React tree that produces different output in each environment.

## Verify recovery and failure policy

Request the same page with JavaScript disabled, then hydrate and navigate with JavaScript enabled. Confirm no console/network failure and the expected component data. Stop Node deliberately in an isolated rehearsal: ordinary pages should use CSR, while required-SSR pages should take the documented error path. Restore Node and confirm actual SSR again.

The renderer-health verifier checks stop/recovery separately from application browser flows. Follow [health setup](../ssr/health.md) and [required/fallback policy](../ssr/fallback.md) rather than making cached health a synchronous request dependency.
