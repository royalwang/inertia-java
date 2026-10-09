---
title: "Build an immutable release"
description: "Package libraries, classifiers, docs, LICENSE/NOTICE and same-build Java/client/SSR payload."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/deploy/release.mjs
  - inertia-java/deploy/documentation.mjs
  - inertia-java/deploy/runtime.mjs
verification:
  - inertia-java/deploy/verify-release.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# Build an immutable release

Package a coherent Java/example release outside the source checkout. This creates local artifacts; Maven repository publication and production deployment are separate operations.

## Build inputs

From `inertia-java/`, run `./mvnw --batch-mode spotless:check verify`. From the example frontend, run `npm ci`, `npm run typecheck` and `npm run build`. Return to `inertia-java/`, then run:

```sh
node deploy/release.mjs /absolute/release-store
```

The command prints the published directory named by its release ID. It requires the executable application, all seven library binary/source/Javadoc classifiers, POMs and a complete frontend receipt. It rejects a mixed/missing frontend inventory before publishing.

## Payload and identity

The directory contains `app.jar`, client/SSR outputs, production package inputs, immutable client assets, a release-local launcher, runbook, documentation/examples, systemd templates and a Maven staging subtree. Private docs tooling, node_modules and generated documentation HTML are excluded; the site is a separate artifact.

The frontend build ID covers client/SSR bytes. The release ID covers the full payload's canonical inventory. Identical built inputs produce the same payload ID; this does not promise bit-reproducible Maven/Vite compilation. Publication stages then renames on the same filesystem. An identical existing release is reused; corrupt existing bytes are rejected without overwrite.

## Check before starting

In the printed release directory, run `node runtime.mjs check`. Install locked production dependencies from `frontend/` using `npm ci --omit=dev --ignore-scripts --no-audit --no-fund`, then apply your trusted read-only deployment policy.

Do not put logs or mutable application files in the release directory: the exact inventory rejects extras. Installed frontend dependencies are excluded from that inventory, so preflight is not third-party byte attestation. Hashes establish content integrity, not publisher signatures or authorization.

The Maven subtree includes parent/module POMs and classifiers, but is not a signed, metadata-complete public snapshot repository. Review [release policy](../community/releases.md) before publication. Run the independent deployment/consumer verifiers for the stated local boundaries, then follow [process setup](processes.md).
