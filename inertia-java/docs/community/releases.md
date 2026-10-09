---
title: "Releases and upgrades"
description: "Define snapshot/release distinctions, support windows, release notes and upgrade/migration guides."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/pom.xml
  - inertia-java/deploy/release.mjs
  - inertia-java/scripts/verify-library-artifacts.py
  - inertia-java/docs/scripts/versions.mjs
  - inertia-java/docs/scripts/release-snapshot.mjs
  - inertia-java/docs/scripts/version-artifacts.mjs
  - inertia-java/docs/scripts/site-archive.py
verification:
  - inertia-java/scripts/verify-maven-consumer.py
  - inertia-java/deploy/verify-release.mjs
  - inertia-java/docs/scripts/versions.test.mjs
  - inertia-java/docs/scripts/site-archive-test.py
  - inertia-java/docs/scripts/smoke-versions.mjs
---

# Releases and upgrades

The current documented version is `0.1.0-SNAPSHOT / next`. Local artifacts and a buildable documentation site are not proof of a public Maven release, Git tag or live Pages deployment.

## Prepare a release

Choose a real version/tag, review API/configuration/protocol changes, complete attribution/dependency review and build all library binary/source/Javadoc classifiers from that source. Run the artifact/independent consumer checks and affected runtime/browser contracts. Package the example coherently if distributing its Java/Node launcher.

Release notes should identify added/changed/deprecated behavior, breaking defaults/signatures, migrations, the verified compatibility matrix and known limits. Distinguish library version, Inertia protocol, pinned client, JDK/Boot and Node versions. Do not infer a support/backport window from an unversioned README.

## Documentation versions

Maintain one canonical `next` content set until a real release tag exists. Generate an immutable documentation/API snapshot from each actual tag and add a version mapping/navigation entry after successful build. A VitePress navigation menu alone is not version management. Never relabel current snapshot prose or Javadoc as an older release.

### Build a tagged snapshot

Before tagging, update the Maven reactor, catalog version, reviewed English pages and translated revisions together. The builder rejects `SNAPSHOT` versions and mismatched metadata; it does not replace version strings in prose. Verify the release compatibility matrix and prepare a source tag containing the documentation tools.

From `inertia-java/docs/`, replace the placeholders with an existing origin tag and a new output directory:

```sh
git fetch --tags origin
RELEASE_TAG='your-existing-release-tag'
SNAPSHOT_OUTPUT='/absolute/path/new-documentation-bundle'
npm ci
npm run docs:snapshot -- --tag "$RELEASE_TAG" --output "$SNAPSHOT_OUTPUT"
```

The command resolves the exact tag, checks the same commit on origin and exports its source into an isolated directory. It builds matching Maven classifiers and the tagged locked documentation tools, then checks/builds/browser-verifies the version path. For Chromium it installs the browser required by the tagged Playwright package. It emits `inertia-java-docs-<version>.tar.gz` and a registration receipt. The manual read-only snapshot workflow performs this build without creating tags, releases or a deployment.

### Register and publish retained versions

After reviewing the bundle and evidence, attach it to the corresponding existing GitHub release without replacing an asset. With maintainer authentication configured:

```sh
gh release upload "$RELEASE_TAG" "$SNAPSHOT_OUTPUT"/inertia-java-docs-*.tar.gz
npm run docs:register-version -- "$SNAPSHOT_OUTPUT/registry-entry.json"
git diff -- versions.json
```

Registration checks the origin tag and public canonical release asset, its SHA-256, source identity and exact file inventory before appending the mapping. SHA-256 detects content changes; source/registry review remains the publisher trust boundary. Do not replace an existing version, tag or digest. Review and commit the mapping through the normal contribution process.

Build the current site and retain registered release bundles before publishing:

```sh
npm run docs:check
npm run docs:build
npm run docs:assemble-versions
npm run docs:smoke-versions
```

`next` remains at the repository site root; immutable releases live under `/inertia-java/versions/<version>/`. Each archive keeps its own assets, API, source links and available navigation. The current version menu lists registered releases; historical HTML is not rewritten whenever a new version appears. Missing archives, changed digests/identity, traversal, links, invalid inventory or altered installed snapshots stop publication. The Pages workflow assembles and verifies all registered versions before upload, so a later next-site build does not discard them.

No real release is registered yet. Fixture tests validate tooling boundaries; they are not proof of an actual tagged release, byte-identical future rebuild across toolchains, or public hosting. Retain original bundles by digest and compare any rebuild against them before accepting it; never overwrite an archive to conceal a difference.

Chinese translations track their English content revision. When changing a source page, mark translations needing review and preserve an English fallback. Do not publish empty translated pages as coverage.

## Upgrade an application

Read the change notes and compare configuration ownership/defaults, public APIs and metadata with the previous version. Rebuild Java/client/SSR coherently and preserve old assets. Exercise forms/session/authentication, current/stale version handling, actual SSR/CSR and rollback under the target deployment policy.

The local Maven staging subtree is useful for independent consumption but is not a signed public snapshot repository with complete remote metadata. Credentials/signing/publication and GitHub Pages activation require the real release infrastructure and approved settings. See [build/release](../deployment/build-release.md) and [switching](../deployment/rolling-upgrades.md).
