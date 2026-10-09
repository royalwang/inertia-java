---
title: "HTTP SSR gateway"
description: "Configure connect/render timeouts, bytes, concurrency, exclusions and Page envelope checks."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/HttpSsrGateway.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/SsrEndpointResolver.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ConfiguredHttpUrl.java
  - inertia-java/examples/spring-react/frontend/src/pages.ts
  - inertia-java/examples/spring-react/frontend/src/ssr.tsx
verification:
  - inertia-java/inertia-ssr-http/src/test/java/io/inertia/ssr/HttpSsrGatewayTest.java
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-failures.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-health.mjs
---

# HTTP SSR gateway

Reuse a bounded `HttpSsrGateway` for a trusted renderer. It sends already-resolved Page JSON; browser credentials are not forwarded and rendering is not retried or redirected.

## Configure the peer

Choose a configured URI or `SsrEndpointResolver`, connect/render budgets, maximum response bytes, concurrency, shared Page codec and optional build/root verification. The sample explicitly chooses 200ms connect, 1s render, 2MiB and 16 concurrent renders. These are sample constructor values, not universal starter YAML defaults.

The resolver can select the production endpoint, an opt-in development hot origin or no endpoint. Exclusion rules compare request paths without the leading slash: exact matches or a trailing `*` prefix. They are not arbitrary regular expressions. A missing configured bundle or invalid hot origin yields unavailable selection.

Never derive the peer URL from an incoming request header. Place Node behind appropriate internal access controls; it receives user Page data even though no cookies are forwarded.

## Response checks

The gateway bounds actual streamed bytes, checks HTTP/JSON response structure and requires usable rendered output. When enabled, returned build identity must match Page version and returned root ID must match configuration. A valid HTTP 200 alone does not establish a valid render.

| Failure class | Expected policy |
| --- | --- |
| No endpoint / exclusion | Classified unavailable fallback |
| Concurrency exhausted | Classified overload fallback |
| Timeout/connection/transport | Classified transport fallback |
| Empty/malformed/oversize result | Invalid/limited response fallback |
| Build/root mismatch | Rejected SSR result |

The renderer later applies the Page's default/required SSR policy. Props failures happen before this stage. Caller cancellation releases owned transport work and reports cancellation rather than fabricating a successful fallback.

## Diagnose safely

The example additionally validates the decoded Page before invoking React. It requires a registered own component name, object props, a string URL of at most 8192 characters, a string/null version, object flash when present and boolean presentation flags when present. Names inherited from JavaScript prototypes, including `toString`, `constructor` and `__proto__`, are rejected.

Invalid decoded envelopes produce empty output with `invalidPage: true`; the Java gateway rejects the unusable body and applies its normal fallback policy. This guard does not replace the locked official server's HTTP parsing or bigint revival, and does not authorize the Page or its component. The health verifier sends 14 invalid decoded inputs, then renders a valid Error page to check recovery. Run `npm run test:ssr-health` from the built example frontend when changing this callback.

Pass the same observer and a safe configured endpoint ID if you want correlated gateway observations. Do not log raw Page bodies or use raw peer URLs as metric tags. Public fallback strings remain stable categories; structured reason enums distinguish more detailed outcomes.

Gateway tests and the failure/browser verifier cover bounded failure cases. Use [fallback policy](fallback.md) to decide what the user sees and [health](health.md) to distinguish peer monitoring from per-Page success.
