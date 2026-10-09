---
title: "License and attribution"
description: "Describe Java Apache-2.0 and royalwang/2026, Rust scope and retained third-party terms."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/LICENSE
  - inertia-java/NOTICE
  - inertia-java/scripts/dependency-inventory.py
  - inertia-java/docs/scripts/dependency-inventory.py
verification:
  - inertia-java/scripts/verify-library-artifacts.py
  - inertia-java/deploy/verify-release.mjs
  - inertia-java/docs/scripts/dependency-inventory-test.py
---

# License and attribution

The Java module is licensed under Apache License 2.0. Its copyright declaration is:

```text
Copyright (c) 2026 royalwang
```

## Read the distribution files

Use the Java subtree's `LICENSE` and `NOTICE` as the distribution documents. Preserve them when redistributing the Java library, source/Javadoc classifiers, example release or adapted documentation. The parent Rust project and third-party dependencies have their own licensing and attribution; the Java declaration does not change their ownership.

Binary/source/Javadoc classifiers and the release verifier check the Java licensing files at their intended boundaries. Generated website API resources preserve attribution separately from source pages. A successful packaging check proves file presence/content, not a legal determination for every downstream use.

## Third-party software

Review the dependency inventory and dependency licenses before publication. Maven and npm graphs cover different runtime/build dependencies. Documentation tooling is a private build package and is excluded from the Java release payload; that exclusion does not eliminate responsibilities for assets actually distributed in the static site.

For the Java/React example inventory, first build the Maven reactor and install/build the example frontend. From `inertia-java/`, run `python3 scripts/dependency-inventory.py` with an optional new-or-empty output directory. It uses the pinned CycloneDX Maven plugin and npm's SBOM command, covering Maven test/provided entries plus full/production npm lock graphs. It compares actual executable `BOOT-INF/lib` jars with reactor/dependency hashes, records packaging-only entries and copies observed runtime/npm license files unchanged. Read its review table and `summary.json`; missing optional platform files remain unobserved, and build plugins/JDK/OS/browser/bundle attribution remain separate. Collection success retains `publicationQualified=false`.

For documentation tools specifically, from `inertia-java/docs/` after `npm ci`:

```sh
npm run docs:dependencies
```

The command prints a unique evidence location. It retains npm's raw CycloneDX graph, checks every all-platform lock path and installed identity, and copies observed license/notice files unchanged with SHA-256. Nested identical package instances retain their individual lock paths; conflicting declarations fail. Optional platform packages absent on this host are recorded without invented license-file evidence.

Read `inventory.json`, `summary.json` and `license-texts/` in that location. Collection success does not approve publication: `publicationQualified` stays false, and missing declarations/texts or reciprocal/multiple terms remain review items. This graph is separate from the Java/React application inventory. It does not identify which build dependencies were actually bundled into static HTML/JavaScript, or cover Node/npm/Python/browser/OS licensing. Review the actual distributed assets and keep their required notices before publication.

Keep notices when incorporating code, illustrations or examples from upstream. Do not replace an upstream copyright with the Java module's copyright. Link the original source and record modifications when its license requires them.

## Contributions and publication

Submit contributions under the project's license and identify third-party material. Publication should include the applicable license/notice files alongside artifacts. The maintained release process must also review transitive dependencies and generated outputs for the particular release, rather than treating a previous inventory as permanent approval.

For buildable sources and distribution layout see [contributing](contributing.md) and [release policy](releases.md). Exact legal terms are in the Apache-2.0 license supplied with the Java module.

## Upstream references

- [npm SBOM documentation](https://docs.npmjs.com/cli/commands/npm-sbom/)
- [CycloneDX Maven plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin)
