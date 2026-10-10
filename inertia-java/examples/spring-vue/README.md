# Spring MVC + Vue SSR example

Build and run from the [Vue guide](../../docs/getting-started/vue.md) or its [Chinese translation](../../docs/zh/getting-started/vue.md). This example owns business routes and Vue pages, and consumes the same public Java libraries as React. Form data is demonstrative and is not persisted.

After building Java/frontend outputs, run `INERTIA_BROWSER_CHANNEL=chromium npm run test:browser` from `frontend/`. It starts and cleans up owned Java/Node processes, exercises real SSR and hydration, partial/optional/deferred props, navigation, validation/CSRF/flash, scroll/once and Node failure/recovery, and writes a local summary/screenshot.

For an outside-reactor build using a new private Maven cache, run from `inertia-java/`:

```sh
INERTIA_VERIFY_VUE=true INERTIA_BROWSER_CHANNEL=chromium python3 scripts/verify-maven-consumer.py
```

An optional `INERTIA_CONSUMER_DEPENDENCY_CACHE` seeds third-party dependencies only. The verifier stages candidate libraries, builds a copied Vue application without a relative parent, and supplies its JAR using `INERTIA_VUE_CONSUMER_JAR`. The same real browser gate then consumes that executable. Frontend dependencies and outputs must already be built.

The production-build path is qualified separately from Vite development, the React release launcher, Redis host-session deployment, other browsers, Svelte and WebFlux. Shared build receipt/asset publication code lives in `examples/shared/build-frontend.mjs`; the existing asset publisher keeps its tested implementation in the React tooling directory. Neither tool is needed by an already built Java/Node deployment.

For the complete local regression gate (default Java/React plus Vue build and isolated consumption), set `INERTIA_VERIFY_VUE=true` when running `node scripts/verify.mjs` from `inertia-java/`. Redis remains separately selected with `INERTIA_REDIS_SERVER`.

To inventory this example’s declared dependencies and observed license files, run `INERTIA_INVENTORY_EXAMPLE=spring-vue python3 scripts/dependency-inventory.py` from `inertia-java/`. Production scopes use the installed npm dependency graph; all-platform lock metadata and the npm production SBOM are retained separately.
