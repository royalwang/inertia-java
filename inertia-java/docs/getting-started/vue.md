---
title: "Run the Vue example"
description: "Build Spring MVC with Vue, Vite and Node SSR, then try forms, deferred props and scrolling."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-vue/src/main/java/io/inertia/example/vue/Application.java
  - inertia-java/examples/spring-vue/frontend/package.json
  - inertia-java/examples/spring-vue/frontend/src/app.ts
  - inertia-java/examples/spring-vue/frontend/src/ssr.ts
verification:
  - inertia-java/examples/spring-vue/frontend/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# Run the Vue example

The Vue example uses the same Java core, Spring MVC starter, Vite manifest reader and HTTP SSR gateway as React. Java owns routes and props; the official Vue adapter renders the pages. The form uses demonstration data and does not write to a database.

## Build

Use Java 21 and Node 22.22.2 (minimum 22.12). From `inertia-java/`:

```sh
./mvnw --batch-mode --no-transfer-progress -pl examples/spring-vue -am package
cd examples/spring-vue/frontend
npm ci
npm run typecheck
npm run build
```

The lockfile pins `@inertiajs/vue3` and `@inertiajs/vite` 3.8.0, Vue and `@vue/server-renderer` 3.5.43, and Vite 8.3.3. This example uses TypeScript 5.9.3 with `vue-tsc` 3.3.12. React has a separate TypeScript toolchain; changing one lockfile does not upgrade the other.

Client and SSR entries share an explicit page registry. The build writes matching client/SSR outputs and `dist/build.json`; the Java application and Node renderer reject inconsistent build identities. Rebuild both outputs together.

## Run two processes

In the first terminal, from `inertia-java/examples/spring-vue/frontend/`:

```sh
npm run ssr
```

In the second terminal, from `inertia-java/examples/spring-vue/`:

```sh
java -jar target/spring-vue-0.1.0-SNAPSHOT.jar --server.port=18083
```

Open `http://127.0.0.1:18083/users`. The Node renderer listens on loopback port 13715. Change it with `SSR_PORT` and set the Java `--inertia.ssr` endpoint to match. `--inertia.frontend` accepts an absolute frontend directory when launching Java from another working directory. The Java library does not start Node automatically.

## Try the application

- View the initial HTML: Ada is already rendered before JavaScript runs. Hydration activates the existing markup; a deferred request fills the statistics.
- Use partial reload and load the optional value. The Vue adapter preserves unrequested props while applying the response.
- Submit an empty name to see validation, then a valid name to see flash. Navigation to About and back consumes the flash; Spring Security rejects a POST without its CSRF token.
- Open Feed, append/prepend pages, reset the list and refresh the once catalog. The server supplies scroll/once metadata; the official client manages the accumulated list and reuse.
- Stop Node while keeping Java running, then reload. Java returns a CSR shell and the Vue client mounts it. Restart Node and reload to return to SSR.

The client chooses hydration when the root has `data-server-rendered`, and normal mounting for a CSR shell. SSR creates a fresh Vue application for each request through the official adapter. Do not keep user-specific reactive state in a process-wide singleton. See [Vue SSR](https://vuejs.org/guide/scaling-up/ssr) and [Inertia SSR](https://inertiajs.com/docs/v3/advanced/server-side-rendering).

## Scope and deployment

This example covers a local production build with Chromium, not every Vue/client/browser version. The [compatibility page](compatibility.md) records the locked combinations. The existing packaged release launcher targets the React example; use these explicit Java/Node commands for Vue. A Vue release launcher, Vite development integration, Svelte and WebFlux need separate qualification.

Use [CSRF guidance](../guide/csrf.md), [session delivery](../guide/flash-session.md) and [process supervision](../deployment/processes.md) when adapting this example to an application. Preserve build pairing, loopback renderer isolation and application-owned authentication policy.
