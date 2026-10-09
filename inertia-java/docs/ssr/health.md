---
title: "Renderer health and recovery"
description: "Probe in the background, supervise independently and verify stop/watch/recover behavior."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/SsrHealthMonitor.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
verification:
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-health.mjs
---

# Renderer health and recovery

Monitor renderer availability separately from Java liveness and per-Page rendering. A cached UP state means the peer answered its health protocol; it does not prove every component or build can render.

## Enable the sample monitor

The example leaves health monitoring disabled. Start Java with `--inertia.ssr-health-enabled=true` to create a closeable `SsrHealthMonitor`. The default target is `/health` on the configured renderer origin; the example JVM property `-Dinertia.ssr-health=...` overrides it before `-jar`.

The sample uses 200ms connect, 1s probe and a 5s delay after each check. `/api/ssr-health` returns cached state/reason/timestamp; reading it does not issue another probe. `/api/health` remains independent Java liveness. The Vite development plugin does not automatically provide the standalone health protocol.

## State and lifecycle

| State | Meaning |
| --- | --- |
| `UNKNOWN` | No completed check yet |
| `UP` | HTTP 200 with the expected healthy response |
| `DOWN` | Bounded probe failed or returned an invalid response |
| `STOPPED` | Monitor closed |

The application owns `start()` and `close()`. Close cancels pending work and shuts down the scheduler; a late result must not resurrect a stopped monitor. Responses are bounded and redirects are not followed.

Decide readiness based on your Page policy. A service whose pages support CSR can remain live while Node is down. A service requiring SSR may choose a stricter readiness policy, but should still expose Java liveness for diagnosis.

## Recover processes deliberately

`npm run ssr:watch` uses Node watch mode on built output; it restarts Node but does not compile TypeScript. Use Vite for source development and a process supervisor for production. Keep coherent build inputs during restart.

`npm run test:ssr-health` starts owned Java/Node watch processes, checks a bundle-triggered restart and UP→DOWN→UP, confirms Java/CSR during downtime and restored SSR afterward. Its input-rejection exercise also proves valid rendering survives invalid decoded Page envelopes. The local verifier targets macOS/Linux; Windows child-tree behavior needs separate qualification. See [deployment runbook](https://github.com/royalwang/inertia-java/blob/main/inertia-java/deploy/README.md) for supervision.

## Upstream references

- [Node's native watch mode](https://nodejs.org/api/cli.html#--watch)
