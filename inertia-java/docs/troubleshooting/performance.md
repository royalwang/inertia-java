---
title: "Slow requests and overload"
description: "Separate props/database/SSR/response timings and use bounded budgets and the existing benchmark."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/scripts/benchmark.mjs
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/HttpSsrGateway.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaProperties.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/PropsOverloadTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CancellationContractTest.java
  - inertia-java/examples/spring-react/frontend/scripts/benchmark.mjs
---

# Slow requests and overload

Locate the slow stage before expanding concurrency. Props, renderer transport, response delivery and session storage have separate budgets and failure modes.

## Measure the boundary

Inspect bounded [stage metrics](../reference/metrics.md) alongside application request latency and provider/database metrics. A response timer records write behavior, not the full business operation. Look for prop timeouts, executor rejection, SSR in-flight overload and backend session latency.

Check which providers were selected. Optional/deferred/partial/once metadata changes when providers run; eagerly executing expensive work before wrapping it in a lazy provider defeats selection. Confirm real provider cancellation/timeouts rather than assuming a cancelled future stops arbitrary blocking database work.

## Adjust deliberately

The default per-request props capacity is 8 and the shared executor is bounded. A per-request capacity failure is not an unlimited queue. Fix provider cost and upstream limits, then tune budgets/concurrency against measured demand. Keep response timeout >= props timeout. More queue space can amplify latency and memory pressure without increasing sustainable throughput.

Gateway capacity/response-byte limits are independent from props settings. A renderer CPU bottleneck needs Node/process capacity analysis; widening a Java timeout can simply retain more waiting requests. Preserve required-SSR behavior when deciding whether fallback is acceptable.

## Prove improvement

Repeat a representative workload with the same input/build/environment and compare stage distributions, failures, resource usage and cancellation. Include overload and shutdown, not only a successful single request. Keep sensitive Page data and high-cardinality identities out of metric labels/logs.

The example benchmark is a local measurement tool, not a capacity guarantee for production or another hardware/ingress/session backend. Record its conditions and retain actual browser correctness checks after optimization. See [async ownership](../props/async-concurrency.md) and [gateway settings](../ssr/gateway.md).

## Run the local HTTP baseline

First run `./mvnw verify` from `inertia-java/`, then `npm ci` and `npm run build` from `inertia-java/examples/spring-react/frontend/`. From that frontend directory:

```sh
npm run benchmark
```

The command starts owned Java/Node peers on isolated loopback ports and measures complete initial `/users` HTML responses. It retains a unique temporary evidence directory; `INERTIA_BENCH_OUTPUT=/absolute/parent` selects its parent. Existing application processes are not used or stopped.

| Environment input | Default | Accepted values |
| --- | --- | --- |
| `INERTIA_BENCH_CONCURRENCY` | `1,8,32` | Distinct comma-separated integers, each 1–64 |
| `INERTIA_BENCH_REQUESTS` | `128` | 1–10000 measured visits per regular profile |
| `INERTIA_BENCH_SLOW_REQUESTS` | `24` | 1–1000 measured visits per stalled-body profile |

Regular phases warm up with 8 sequential visits; stalled-body phases use 2. Effective concurrency cannot exceed the phase's request count. Workers wait for each full response before sending another request, so throughput is achieved closed-loop throughput rather than open-loop arrival capacity.

The four modes are a real renderer, deliberate route exclusion, a refused renderer connection and a peer that stalls after headers/partial body. Logging observations are enabled by the harness. The local settings are props 3s, response 5s, renderer 1s/16 permits and the example's bounded executor. These are benchmark conditions, not recommended production sizing. At higher concurrency, overload fallback can reduce aggregate latency; compare SSR/CSR counts and fallback reasons with the latency distribution.

Read `summary.json` and the per-phase JSON for jar/build/source identity, P50/P95/P99, response sizes/statuses, SSR ratio, renderer timeout ratio, client errors and individual samples. Bad status, missing expected fallback reasons or absent sequential SSR fail the measurement. A failed run retains `success=false` with completed phases; do not reuse a prior success receipt.

Resource samples use `ps` about every 200ms. RSS is a sampled maximum, and CPU is the OS-reported process-lifetime percentage, which can exceed 100 across cores. Neither is an exact peak or interval utilization measure. The workload retains no session cookies and uses two in-memory users with zero database queries. It does not measure same-session contention, browser paint/hydration, real database work or deployed ingress/storage capacity. JIT/GC, logging, shared-host load and mode ordering affect the result; repeat comparable conditions and retain the [browser correctness gates](../testing/browser-tests.md).
