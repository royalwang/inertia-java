---
title: "Official-client acceptance"
description: "Run development/SSR/CSR, no-JS, auth, merge/history and fault harnesses with owned peers."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/package.json
  - inertia-java/examples/spring-react/frontend/scripts/verify-browser-matrix.mjs
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
  - inertia-java/examples/spring-react/frontend/e2e/auth.spec.ts
verification:
  - inertia-java/examples/spring-react/frontend/playwright.config.ts
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-health.mjs
---

# Official-client acceptance

Exercise the pinned official Inertia React client against the actual Java application and Node renderer. A fake gateway or JSON-only test cannot establish hydration or browser merge behavior.

## Run the matrix

Build the Maven application and frontend first using the [development workflow](../getting-started/development.md). From `inertia-java/examples/spring-react/frontend/`:

```sh
npm run test:browser-matrix
```

Set `INERTIA_MATRIX_OUTPUT` to a new absolute evidence directory when retaining results. The runner owns its processes and isolated ports and writes per-scenario logs/screenshots. Install the paired Playwright browser if using CI's Chromium channel; local default uses Chrome.

The eight scenarios are SSR, all-errors, failures, alternate namespace, authentication, real authentication expiry, CSR failures and authentication with CSR. Cases cover list/form navigation, deferred/partial/merge/once/scroll, named/multiple errors, failed session/render/write recovery, CSRF, login/logout and history. Scenario-dependent skips are intentional; record passed and skipped counts separately instead of claiming all cases ran in every mode.

## Read evidence

Inspect actual initial HTML with JavaScript disabled for SSR and the hydrated browser for subsequent navigation. A heading visible only after JavaScript runs does not prove SSR. When Node is stopped, ordinary pages should still hydrate from CSR; required-SSR policy has a distinct failure path.

Real idle-expiry cases take time by design and should not be replaced with a fabricated clock result when accepting actual cookies/session expiry. Keep console/network failures and relevant screenshots with the scenario log, and confirm owned processes exit.

## Run a focused acceptance command

The commands below run from `inertia-java/examples/spring-react/frontend/` after the Maven jar, locked frontend dependencies and both frontend bundles have been built. Use Java 21 and the pinned Node/Playwright setup. The process harnesses start their own loopback peers; do not start a second manual application for them. `test:assets` is a filesystem contract and starts no browser.

| Command | Boundary it checks |
| --- | --- |
| `npm run test:development` | Real Vite development SSR, hot assets and official-client flows; refuses an existing hot file |
| `npm run test:build-integrity` | Client/SSR receipt and tampered/mixed build refusal |
| `npm run test:ssr-failures` | HTTP error, stalled body and oversized response followed by usable CSR/navigation/forms |
| `npm run test:ssr-health` | Health state, renderer watch/restart, invalid decoded Page refusal and subsequent valid rendering |
| `npm run test:csp` | Production SSR and disconnected CSR nonce integration; trusted script allowed and untrusted script blocked |
| `npm run test:custom-root` | Matching `portal` Java/Node/client root, hydration, fallback and recovery |
| `npm run test:history` | Actual encrypted browser history, clear-key behavior and per-page SSR opt-out |
| `npm run test:assets` | Immutable asset publication, idempotence and refusal to overwrite corrupted content |
| `npm run test:release-switch` | A→B routing, old asset bytes retained, official-client refresh and coherent new rendering |

Inspect the selected script's printed evidence location and final summary, including cleanup failures. These are focused gates; a pass does not replace other affected contracts. Release-switch B is a controlled changed-client-byte fixture, not a second source revision. Health checks do not qualify every component's renderability, and nonce acceptance does not establish a universally suitable CSP for an application.

For the complete established runtime gate, from the repository root:

```sh
node inertia-java/scripts/verify.mjs
```

This performs its own clean Maven/frontend builds and the 18-stage acceptance sequence. `INERTIA_VERIFY_OUTPUT=/absolute/path` selects an evidence parent. The gate does not format or commit sources. Read stage outcomes and the source/build identity in `summary.json`; a local result does not establish the remote GitHub workflow result. Windows process-tree cleanup and production host qualification remain separate.

The independent Maven consumer is an additional boundary. From `inertia-java/` after the normal builds, run `python3 scripts/verify-maven-consumer.py`. It resolves the packaged starter/testing, runtime jars and classifiers through an isolated fixture repository/cache, compiles the distributed Java examples and checks real Servlet HTTP/session behavior. It also requires build refusal when core is missing. It does not publish remotely or exercise React/Node SSR. `INERTIA_CONSUMER_OUTPUT=/absolute/new-or-empty-directory` retains stable evidence; a nonempty output is refused. An optional `INERTIA_CONSUMER_DEPENDENCY_CACHE` seed excludes owned `io.inertia` artifacts, so those must resolve afresh.

## Separate application and site checks

`npm --prefix inertia-java/docs run docs:smoke` (repository root) tests documentation routes/search/mobile/downloads. It does not substitute for the application's matrix. Frontend scripts also separately verify renderer health/restart, build inventory, CSP and release switching; choose those when changing their respective behavior.

The verified local browser/platform is part of the evidence. Do not infer real Linux systemd, public TLS or distributed session correctness from a local matrix pass.

## Upstream references

- [Playwright browser documentation](https://playwright.dev/docs/browsers)
