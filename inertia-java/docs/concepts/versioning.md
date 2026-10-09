---
title: "Build and asset versions"
description: "Bind Java version, manifest, client and renderer; explain refreshing an old client."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteBuild.java
  - inertia-java/examples/spring-react/frontend/scripts/build.mjs
  - inertia-java/deploy/release.mjs
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# Build and asset versions

A protocol version identifies the client-facing build. In the example, one SHA-256 build ID covers the client and SSR file inventory, so a change to either side changes the Page version.

## Three identities

| Identity | Purpose |
| --- | --- |
| Maven version, currently `0.1.0-SNAPSHOT` | Coordinates for Java library/application artifacts |
| Frontend build ID | Coherent client/SSR receipt and Page version |
| Deployment release ID | Complete immutable deployment payload, including Java, docs and Maven artifacts |

These identities are related but not interchangeable. A documentation-only payload change can produce a new deployment release ID without changing the frontend build ID. SHA-256 integrity verifies bytes; it does not establish publisher authenticity or remote authorization.

## Build together

The example's build script creates client and SSR outputs, hashes them into `dist/build.json`, then publishes client assets under `.inertia/assets/<buildId>/`. Production Java loads `ViteBuild`, verifies the client asset store and uses the build ID as its version supplier. Node verifies its own SSR bundle against the same receipt.

Do not combine a Java instance configured with one receipt and an unrelated Node/client bundle. The production gateway checks the returned build/root identity. Mixed outputs fail verification or SSR acceptance instead of silently presenting a claimed coherent release.

## What happens to an old browser

An Inertia GET carries the client's current version. If it differs from the server's current version, preflight returns the refresh outcome before business work. The official client reloads the document to acquire the new build.

Keep old immutable assets long enough for in-flight and previously loaded documents during deployment. A page opened before a release switch can still refer to its old chunk URLs. Deleting those files immediately can break that browser even though the new version is internally valid.

The [deployment runbook](https://github.com/royalwang/inertia-java/blob/main/inertia-java/deploy/README.md) describes asset retention and release switching. Retention policy belongs to operations; the library does not discover active browser tabs or garbage-collect their dependencies.

## Development and documentation versions

Development hot mode has its own version derived from asset state. Production ignores the hot file and relies on immutable receipts. Do not use a development origin as a production build identity.

This documentation describes the current source snapshot. A future version selector should point to documentation generated from actual release tags; an untagged snapshot must not be relabeled as a stable published release. See [distribution status](../getting-started/compatibility.md).
