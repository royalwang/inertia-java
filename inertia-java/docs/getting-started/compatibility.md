---
title: "Supported versions and distribution status"
description: "Read verified Java/Boot/React/Node/browser combinations and distinguish source snapshots from released artifacts."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/pom.xml
  - inertia-java/examples/spring-react/frontend/package-lock.json
  - inertia-java/compatibility/README.md
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# Supported versions and distribution status

This page describes the repository's verification baseline, not a promise that every newer or older dependency combination works.

## Current baseline

| Layer | Version / boundary |
| --- | --- |
| Java library | `0.1.0-SNAPSHOT`, source/local distribution |
| Java | 21 |
| Maven Wrapper | 3.9.16 |
| Spring Boot | 3.5.7; Servlet Spring MVC |
| JSON | Jackson 2, managed with the Boot BOM |
| Node verification baseline | 22.22.2; frontend requires >=22.12 |
| Official React/Vite Inertia packages | 3.8.0, locked by the example |
| React / React DOM | 19.3.0 |
| Vite | 8.3.3 |
| TypeScript | 7.0.2 |
| Playwright | 1.64.0, with its paired Chromium in CI |

Read the POMs and frontend lockfile when reproducing a build. The documentation has its own private lockfile and VitePress toolchain; those dependencies are not application runtime dependencies.

## What the verification establishes

The [compatibility matrix](https://github.com/royalwang/inertia-java/blob/main/inertia-java/compatibility/README.md) records 37 deterministic Page cases, 45 HTTP-policy contracts and a separate live once/TTL gate derived from the repository's Rust implementation. Java tests compare values, omissions and metadata rather than only checking a few selected fields.

The owned browser and deployment gates exercise the locked official React client, actual Node SSR, hydration, forms, sessions and fallback. Independent Maven consumption checks jars and classifiers from a packaged Maven subtree in an external project. These are different boundaries: a JSON fixture match alone does not establish browser compatibility or production qualification.

## Deliberate differences and limits

Java restricts back navigation to the current origin, canonicalizes accepted absolute back URLs to path/query, removes Referer fragments and treats `Vary: *` as sufficient. Those four HTTP differences are explicit expected policies in the compatibility inputs.

The except-only partial selection rule follows this Rust implementation: non-excluded optional props can execute. Once/TTL expiry representation and Java scroll DTO ergonomics have their own documented boundaries. Consult the matrix before assuming another Inertia adapter's behavior.

The current evidence does not establish WebFlux integration, a distributed session implementation, every official client adapter, Windows process supervision or arbitrary dependency upgrades. The servlet/session and Node process behavior documented here should be requalified when those boundaries change.

## Distribution status

The repository creates binary, source and Javadoc artifacts and an independent release payload. No Maven Central publication, signed public release or hosted documentation URL is asserted by those outputs. Snapshot documentation follows this source tree; versioned documentation should be created from actual release tags when such releases exist.

For installation today, use [local artifacts](installation.md) or your authorized private repository. For deployment details, use the existing [runbook](https://github.com/royalwang/inertia-java/blob/main/inertia-java/deploy/README.md).
