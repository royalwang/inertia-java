---
title: "Development workflow"
description: "Start Vite hot SSR, explain ports/CORS/HMR, and shut down owned services."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/vite.config.ts
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# Development workflow

Use Vite source development for frontend changes and rebuild/restart Java for backend changes. Keep development SSR and production bundles as distinct modes.

## Start Vite and Java

After Maven packaging and `npm ci`, start `npm run dev` from `examples/spring-react/frontend/`. Vite binds `127.0.0.1:15173`, writes the actual origin to `.inertia/hot`, and exposes development SSR through the locked Inertia Vite plugin.

From `examples/spring-react/`:

```sh
java -Dinertia.development=true -jar target/spring-react-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18082
```

Visit [the development users page](http://127.0.0.1:18082/users). In this mode Java uses hot asset URLs and Vite's `/__inertia_ssr` endpoint. You do not need a separate production Node renderer.

`-Dinertia.development=true` is a JVM system property read by the example and must appear **before** `-jar`. `--server.port=18082` is a Spring command-line property after the jar. The starter does not provide a universal development-mode switch for every application.

## Change source deliberately

| Change | Required action |
| --- | --- |
| Existing React component/style | Let Vite update the browser; confirm server HTML too |
| Java controller/configuration | Repackage Java and restart the owned Java process |
| New page component | Register it in Java and `frontend/src/pages.ts`, then restart Java |
| Dependency or lockfile | Reinstall through the project's package manager and recheck build compatibility |
| Production client/SSR entry | Rebuild both outputs and their receipt before testing production |

Vite's configured CORS origins include the documented local Java ports 18080 and 18082. If choosing another Java origin, start Vite with `INERTIA_DEV_APP_ORIGIN=http://127.0.0.1:<port>` matching that origin. Do not widen this to arbitrary origins to mask a port mismatch.

## Diagnose stale development state

Stop Vite cleanly to remove its owned hot file. If a process crashed, inspect `.inertia/hot` and the actual listener before removing a stale file. Do not delete a file owned by another running development session.

A hot value must be a trusted HTTP(S) origin, without credentials, query, fragment or application path. A malformed hot value causes asset failure and an unavailable SSR endpoint; it does not silently select a production renderer.

Production ignores the hot file and verifies immutable bundle inputs. A successful development render does not validate the production receipt, manifest mount or standalone Node process.

## Verify before delivery

Run Java checks for backend changes and `npm run typecheck` / `npm run build` for frontend changes. For the owned end-to-end development scenario, run `npm run test:development` from the example frontend after Maven packaging and browser setup. It starts its own loopback processes, verifies JavaScript-disabled SSR and official-client interactions, and cleans its own hot file/processes.

For editing these documents, use the separate tools in `inertia-java/docs/`; they have their own lockfile and do not modify the application's frontend dependencies. Read [the documentation README](https://github.com/royalwang/inertia-java/blob/main/inertia-java/docs/README.md) for commands.
