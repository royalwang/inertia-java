---
title: "Missing assets and repeated refresh"
description: "Check hot URL, CORS, manifest/asset archive, build ID and old-client409 loops."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteBuild.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ProtocolPolicy.java
  - inertia-java/examples/spring-react/frontend/scripts/build.mjs
verification:
  - inertia-java/examples/spring-react/frontend/scripts/verify-release-switch.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-build.mjs
---

# Missing assets and repeated refresh

A 409 refresh is normal when the browser's asset version is stale. Repeated refresh or missing chunks usually indicates inconsistent versions, cache representation or asset retention.

## Inspect identities and URLs

Compare the document's Page version with the Java-facing build receipt, renderer build and client output. Send current-version Inertia JSON explicitly when diagnosing. A missing version on an Inertia GET may also produce 409 before the controller runs.

Check browser network URLs, configured base and proxy/CDN behavior. Preserve `Vary: X-Inertia` so cached HTML is not served as Page JSON. Avoid a version supplier that changes on every request; it should identify the intended current client build.

## Repair a coherent release

Rebuild client and SSR in one build process and use its verified receipt. Do not copy a manifest from another build or mix old SSR output with new Java/client settings. Publish current immutable assets while retaining old versions required by already-open documents and rollback.

If a stale document references an old chunk, restore the trusted old asset archive or direct the browser through a deliberate version refresh policy. Deleting old files immediately after switching Java can break lazy-loaded chunks even when the new home page works.

## Prove recovery

Request the new document, verify its current-version JSON and fetch actual asset bytes through the serving layer. Keep an old browser document open during the switch and exercise navigation/lazy paths. Verify both release A and B assets and a rollback in the release-switch rehearsal.

For documentation-site 404s, build and preview with the same `INERTIA_DOCS_BASE`; this site's chosen repository base is `/inertia-java/`. Application asset configuration and documentation base are separate settings. See [asset setup](../ssr/vite-assets.md) and [rolling upgrades](../deployment/rolling-upgrades.md).
