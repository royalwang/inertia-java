---
title: "Set up React and Node SSR"
description: "Configure app/SSR entries, official renderer, trusted endpoint and build/root IDs."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/src/app.tsx
  - inertia-java/examples/spring-react/frontend/src/ssr.tsx
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
verification:
  - inertia-java/docs/scripts/verify-first-application.mjs
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
---

# Set up React and Node SSR

Use one browser entry, one Node SSR entry and one shared component registry. Java resolves the Page, sends it to a trusted renderer and places the returned body into the application root view.

## Start from a complete integration

Follow [the quick start](../getting-started/quick-start.md) for the checked-in application or [the independent application tutorial](../getting-started/first-application.md) for an external Maven project. Both include real asset mounting, build verification, CSRF and browser entries. `InertiaConfig.basic(...)` is only a minimal protocol root; it cannot supply these browser features by itself.

For an existing application, wire the following as one coherent unit:

1. An explicit Java component allowlist and a shared frontend resolver.
2. Browser `createInertiaApp` setup using `hydrateRoot` for existing content and `createRoot` for an empty shell.
3. Node `createInertiaApp`/`createServer` setup rendering the same components with `renderToString`.
4. A `ViteBuild`/asset configuration, mounted immutable asset directory and root view.
5. A reused `HttpSsrGateway` with trusted endpoint, budgets, codec and root/build checks.

The example's Node entry validates the decoded Page envelope and registered own component names. In production it checks the build receipt and its own SSR bundle. Invalid inputs produce an invalid/empty result and fallback, not arbitrary component dispatch.

## Build and run

From the example frontend, run `npm ci`, `npm run typecheck` and `npm run build`. Then `npm run ssr` starts the built renderer on loopback 13714. Start Java from the example application directory as shown in the quick start. If changing Node's `SSR_PORT`, also set Java's `-Dinertia.ssr=http://127.0.0.1:<port>/render` before `-jar`.

For source development, use Vite `npm run dev` and the example's opt-in JVM development flag instead. The Vite plugin serves `/__inertia_ssr`; it is a different endpoint from the built standalone `/render` process.

## Prove SSR and hydration separately

Disable JavaScript to confirm document content, then enable it to verify interactive navigation/forms. Inspect a versioned Inertia GET to confirm JSON visits do not call Node. Stop only your renderer to verify fallback or required-SSR policy. See [assets](vite-assets.md), [root templates](root-template.md) and [gateway limits](gateway.md) before moving the integration to deployment.
