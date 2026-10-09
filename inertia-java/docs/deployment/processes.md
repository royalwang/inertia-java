---
title: "Run Java and Node"
description: "Run the release outside the checkout, install production dependencies and supervise readiness/shutdown."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/deploy/runtime.mjs
  - inertia-java/deploy/systemd/inertia-java@.service
  - inertia-java/deploy/systemd/inertia-ssr@.service
verification:
  - inertia-java/deploy/verify-release.mjs
---

# Run Java and Node

Supervise Java and the renderer as separate processes using the same immutable release and root/build identity. Renderer failure may permit CSR; it should not automatically terminate Java.

## Local pair rehearsal

After preflight and production dependency installation, from the release directory:

```sh
APP_PORT=18080 SSR_PORT=13714 node runtime.mjs pair
```

The launcher starts Node, then Java, verifies real same-build `/users` SSR and prints `INERTIA_READY` with release/build IDs and ports. This is a console readiness record, not systemd notification or continuous readiness.

Pair mode permits both ports to be zero for isolated local rehearsal. Once ready, a renderer exit leaves Java available and reports that separate recovery is needed; pair mode does not restart Node.

## Independent services

Use `node runtime.mjs ssr` and `node runtime.mjs java` under your supervisor. Choose the same fixed `SSR_PORT`, release and `INERTIA_ROOT_ID` for both. `SSR_PORT=0` is rejected outside pair mode. Java can start with Node absent and serve default CSR pages.

| Runtime environment | Default |
| --- | --- |
| `APP_HOST` | `127.0.0.1`; explicit `0.0.0.0` supported |
| `APP_PORT` | 8080 |
| `SSR_PORT` | 13714 |
| `INERTIA_ROOT_ID` | `app` |
| `INERTIA_ASSET_STORE` | Release-local `assets` |

Java21 and Node>=22.12 must be on PATH. Node binds loopback. SIGINT/SIGTERM reaches owned children; the Java shutdown-phase budget is 5s and the launcher waits 8s before escalation. Work beyond the budget is not guaranteed to drain.

## Host qualification

Systemd templates under `operations/systemd/` are starting points for Linux. Provision the service account, paths and environment, validate unit syntax on the actual host, then test restart, shutdown and actual readiness. `Type=exec` confirms execution, not readiness. Linux units, TLS/proxy and Windows process cleanup are not qualified by the macOS local rehearsal.

See [health monitoring](../ssr/health.md) and [operations](operations.md) for checks after startup. Keep application logs/state outside immutable releases.
