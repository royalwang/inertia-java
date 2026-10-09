---
title: "SSR and Vite API map"
description: "Index gateway/resolver/health/build/assets constructor contracts and closeable resources."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/HttpSsrGateway.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/SsrEndpointResolver.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/SsrHealthMonitor.java
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteManifest.java
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteBuild.java
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteAssets.java
verification:
  - inertia-java/inertia-ssr-http/src/test/java/io/inertia/ssr/HttpSsrGatewayTest.java
  - inertia-java/inertia-vite/src/test/java/io/inertia/vite/ViteBuildTest.java
---

# SSR and Vite API map

Use the HTTP gateway and Vite inventory from the same release. A reachable renderer is insufficient if it serves a different build or root.

## Renderer types

| Type | Contract |
| --- | --- |
| `SsrGateway` (core) | Resolve a Page to trusted head/body output or a structured fallback |
| `HttpSsrGateway` | HTTP transport with bounded bytes, in-flight capacity, budgets and optional identity verification |
| `SsrEndpointResolver` | Resolve trusted configured renderer URLs and exclusion patterns |
| `SsrHealthMonitor` | Independent scheduled health sampling with a cached snapshot |

The gateway posts resolved Page data to an internal trusted peer. It does not forward browser authentication or arbitrary request headers. Configured URLs must satisfy the HTTP(S) URL policy; redirects are not a mechanism for escaping that trust boundary.

Check constructor overloads before relying on defaults. The example passes explicit 200ms connect/1s render, 2MiB and 16 transport slots and verifies build/root. Do not describe these example values as all applications' Boot defaults. Gateway failures map to [fallback reasons](errors.md); required SSR can promote fallback into a typed failure.

`SsrHealthMonitor.start()` schedules checks and returns itself; repeated start is harmless while open. `snapshot()` sends no HTTP request. It starts `UNKNOWN`, records `UP`/`DOWN` and time, and becomes `STOPPED` on `close()`. A successful cached `status: OK` response does not prove current build, component rendering or hydration. The application owns and closes the monitor. `HttpSsrGateway` has no public close method; reuse its pooled client for the application lifetime.

## Vite types

| Type | Contract |
| --- | --- |
| `ViteManifest` | Parse manifest entries/imports/CSS metadata |
| `ViteBuild` | Read and verify one coherent build receipt and client/SSR inventory |
| `ViteAssets` | Produce trusted asset tags for development/production using actual metadata |

Use the Java root view to place asset tags, SSR head and the Page data script. Development hot-server URLs and production versioned assets have different lifecycles. Root ID must agree across Java, browser and renderer; preserving assets across releases is independent from the new Page version.

The retained [API guide](../api-guide.md) provides compiled Java examples. Follow [Vite setup](../ssr/vite-assets.md) for actual build commands and [release switching](../deployment/rolling-upgrades.md) for retained-asset behavior. Invalid receipts/roots/builds should fail or fall back according to policy; copying an unrelated manifest to suppress an error is not recovery.
