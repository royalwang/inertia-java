---
title: "Run the React example"
description: "Build and run the checkout, observe HTML then JSON, and stop SSR to see CSR."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
  - inertia-java/examples/spring-react/frontend/package.json
verification:
  - inertia-java/docs/scripts/verify-quick-start.mjs
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# Run the React example

This walkthrough runs the checked-in Spring Boot + React application with actual Node SSR. Run commands from the stated directories; Java's default frontend path is relative to its working directory.

## Prerequisites

Use Java 21, Node 22.22.2 for the documented verification baseline, npm and a fresh checkout. Node packages require at least 22.12. Check `java -version` and `node --version` before building. You need the public Maven/npm registries on a cold cache.

## Build Java and frontend outputs

From `inertia-java/`:

```sh
./mvnw verify
```

From `inertia-java/examples/spring-react/frontend/`:

```sh
npm ci
npm run typecheck
npm run build
```

The frontend command builds both client and SSR bundles, writes `dist/build.json`, and publishes client assets under `.inertia/assets/<buildId>/`. The Java build does not implicitly run npm. Keep all those outputs from the same frontend build.

## Start the two processes

Terminal 1, from the frontend directory:

```sh
npm run ssr
```

Terminal 2, from `inertia-java/examples/spring-react/`:

```sh
java -jar target/spring-react-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18080
```

Visit [the users page](http://127.0.0.1:18080/users). Node listens on loopback port 13714; the Java application listens on 18080. Resolve port conflicts before starting, or supply matching explicit endpoint/port settings.

## Verify what is working

1. Reload `/users` with JavaScript disabled. The user names should already appear in the HTML. This checks server rendering rather than hydration.
2. Enable JavaScript and reload. Follow **About this app**; the official Inertia Link should navigate without a full document load.
3. Submit an empty name. The page should show validation feedback after a redirect.
4. Submit `Ada`. A flash message appears; the demo does not persist a new user.
5. Reload again. The flash delivery should not repeat.

A quick transport inspection:

```sh
curl -i http://127.0.0.1:18080/users
# From the example frontend directory:
BUILD_ID=$(node -p "require('./dist/build.json').buildId")
curl -i -H 'X-Inertia: true' -H "X-Inertia-Version: $BUILD_ID" http://127.0.0.1:18080/users
```

The first response is HTML containing the Page script and rendered content. The second supplies the current build version and receives Page JSON with `X-Inertia: true`; it does not ask Node to render HTML. Omitting the version on an Inertia GET requests a refresh (409), rather than Page JSON. Browser-based form tests also require the application's CSRF cookie/header flow.

## Exercise fallback

Stop the Node process you started, keep Java running, and reload `/users` with JavaScript enabled. Default pages return a CSR shell and React mounts it. With JavaScript disabled the same shell has no rendered page content. This is expected fallback, not SSR success.

Stop the owned Java process with Ctrl-C when finished. For local source changes use [development mode](development.md); for an application outside the checkout use [the first-application tutorial](first-application.md).

The repository also has owned browser/deployment verification commands. Their evidence and supported scenarios are documented in the [project README](https://github.com/royalwang/inertia-java/blob/main/inertia-java/README.md#browser-verification). A manual walkthrough does not replace those release gates.
