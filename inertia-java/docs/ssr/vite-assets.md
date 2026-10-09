---
title: "Vite assets and manifest"
description: "Load recursive imports/css, configure entry and base, and protect against traversal/mixed receipts."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteAssets.java
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteManifest.java
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteBuild.java
  - inertia-java/examples/spring-react/frontend/scripts/build.mjs
verification:
  - inertia-java/examples/spring-react/frontend/scripts/verify-build.mjs
  - inertia-java/examples/spring-react/frontend/scripts/release-assets.test.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-release-switch.mjs
---

# Vite assets and manifest

Generate asset tags from a verified build or an explicitly trusted development origin. Keep the output directory, public URL prefix and resource handler aligned; a valid manifest does not serve its files automatically.

## Production build

The example builds `dist/client` and `dist/ssr`, hashes both into `dist/build.json`, then publishes client files under `.inertia/assets/<buildId>/`. Java's `ViteBuild` checks the receipt, file inventory and referenced client assets. Its build ID becomes the Page version and the public prefix `/build/<buildId>/`.

`ViteAssets` reads the production manifest and generates tags for `src/app.tsx`, including imports/styles/preloads supported by the manifest implementation. The application mounts the asset store through its resource handler. Keep paths safe and relative within the selected asset base; do not hand-edit receipt hashes to accept a mixed output.

Production retains its startup snapshot and ignores hot files. Replacing files under a live release can invalidate those assumptions. Publish a new immutable build and keep old assets for active documents and rollback.

## Development hot mode

Development is explicit. The sample Vite plugin writes its actual loopback origin to `.inertia/hot`. `ViteAssets` uses that trusted HTTP(S) origin for the Vite client, React refresh preamble and entry tags. The SSR resolver uses the same origin with `/__inertia_ssr`.

Hot values cannot contain credentials, application paths, query or fragment. Malformed values fail asset generation or make development SSR unavailable. If no hot file exists, development manifest refresh uses the filesystem modification timestamp. A hot file is application configuration, not an incoming user-controlled URL.

## Diagnose a failed page

Check working directory/`inertia.frontend`, receipt existence, manifest entry, resource mount, actual asset URL and browser response status. Then check Java/Node build/root identity. A 200 HTML document with 404 script chunks is not a healthy browser integration.

Run `npm run test:build-integrity` after the Maven/frontend builds to exercise mixed/missing/unrecorded output rejection; asset and release-switch checks cover retained URLs. See [versioning](../concepts/versioning.md) and the existing deployment runbook for retention policy.

## Upstream references

- [Vite's backend integration manifest](https://vite.dev/guide/backend-integration)
