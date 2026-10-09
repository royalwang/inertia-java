---
title: "Release switching and rollback"
description: "Retain old hashed assets, switch A/B and explain single-node session continuity limits."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/scripts/release-assets.mjs
  - inertia-java/deploy/README.md
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteBuild.java
verification:
  - inertia-java/examples/spring-react/frontend/scripts/verify-release-switch.mjs
  - inertia-java/examples/spring-react/frontend/scripts/release-assets.test.mjs
---

# Release switching and rollback

Prepare the next Java/Node pair before changing ingress, and retain both builds' client assets. A browser opened before the switch may still request its old chunks.

## Prepare A and B

1. Keep immutable release A and its verified port mapping available.
2. Build/package B coherently. Publish its client files into a trusted shared asset archive that also retains A.
3. Set `INERTIA_ASSET_STORE` to that archive for each service. From a source frontend, `npm run publish:assets -- /absolute/shared-store` stages and validates its build without pruning old ones.
4. Start B on unused fixed ports with matching root/SSR endpoint settings.
5. Verify Java liveness, actual B SSR/version, B client URLs and old A URLs through the serving layer before switching traffic.

An archive may also be provisioned byte-for-byte from versioned payload assets. Java verifies its current build before serving; do not merge files into an unversioned directory.

## Switch and retain

Change the trusted proxy upstream only after readiness. Drain ingress using the host's policy. A stale Inertia version can force a new document, but it does not guarantee every old chunk is no longer needed. Keep retention long enough for active documents, caches and rollback.

The library does not track every browser tab or garbage-collect assets. Identical roots/builds do not guarantee authenticated session continuity across independent Java processes. Decide session affinity/migration, identity continuity and database compatibility as separate deployment requirements.

## Roll back deliberately

Select the retained immutable A pair and its known port mapping, reverify actual SSR/assets and switch ingress back. Check that any business/database changes remain backward-compatible; restoring jars cannot reverse an incompatible data migration.

The local release-switch verifier covers retained immutable resources and client version behavior. It does not perform real proxy, TLS, session migration or database rollback. Run those on your intended host and record the boundary explicitly. See [build identities](../concepts/versioning.md) and [operations](operations.md).
