---
title: "Contributing"
description: "Define setup, design-first changes, focused checks and documentation/API change expectations."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/README.md
  - inertia-java/pom.xml
  - inertia-java/scripts/verify.mjs
verification:
  - inertia-java/docs/scripts/check.mjs
  - inertia-java/deploy/documentation.test.mjs
---

# Contributing

Start with the user-visible behavior, owning module and failure/recovery policy. Small coherent changes with executable evidence are easier to review than a broad rewrite.

## Prepare a change

Use the Java21/Node toolchain in [development](../getting-started/development.md). Explain the problem, intended behavior and compatibility impact before changing a shared abstraction. Keep core framework-independent; MVC owns Spring transport and the example owns demonstration routes/authentication.

For API/configuration/metadata changes, update the relevant guide/reference, canonical examples and compatibility notes together. Keep public documentation English as its canonical source. Chinese translations identify their English revision and leave untranslated content linked to English.

## Verify the changed boundary

Run Markdown/catalog/link checks, a strict site build and browser smoke for docs. Compile changed Java examples independently and run affected contracts for runtime changes. Reuse the established fixtures and actual browser matrix for protocol/client behavior; do not replace an external oracle with the current implementation's output.

Run publication selection checks when changing documentation packaging. Include the commands/results and scope limitations in the pull request. Generated site files, target outputs, node_modules, temporary logs and secrets do not belong in source commits.

## Submit and maintain

Use a pull request to the [canonical repository](https://github.com/royalwang/inertia-java). Describe the final problem/result, relevant tests and any migration steps. Do not assume a public issue tracker or response-time guarantee is available. See [support](support.md) for safe bug information and [security](security.md) for sensitive reports.

Contributions are under the project's Apache-2.0 license. Preserve third-party licenses/attribution and disclose copied/generated assets as appropriate. The module-level contribution file provides the same repository workflow. Maintainers review module behavior, documentation and release notes together; no invented team or CODEOWNERS assignment is implied.
