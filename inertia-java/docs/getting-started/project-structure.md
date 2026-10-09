---
title: "Project and module structure"
description: "Separate reusable modules, business services, browser code and the renderer."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/pom.xml
  - inertia-java/deploy/release.mjs
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# Project and module structure

The Rust crate remains at the repository root. The Java reactor, examples and public documentation live under `inertia-java/`. Historical architecture and implementation planning live under `docs/inertia-java/`.

## Reusable modules

| Directory | Owns | Does not own |
| --- | --- | --- |
| `inertia-core/` | Request snapshots, protocol, props, Page serialization, context/session SPI, renderer and observation SPI | Servlet routing, a database, Node lifecycle |
| `inertia-ssr-http/` | Bounded HTTP SSR transport, endpoint selection and optional health probing | Public renderer routing, process supervision |
| `inertia-vite/` | Asset tags, hot origins, manifests and build receipts | Running npm or serving files |
| `inertia-spring-webmvc/` | Typed MVC handling, session adapter, validation bridge and exception integration | Application authentication and REST behavior |
| `inertia-spring-boot-autoconfigure/` | Conditional beans, validated execution settings and optional metrics adapter | Application component/root registration |
| `inertia-spring-boot-starter/` | Dependency entrypoint | Public Java classes of its own |
| `inertia-testing/` | Page assertion helpers | HTTP/header/browser acceptance |

The core depends on Jackson 2, rather than on Spring. Your application can use an independent host adapter if it follows the protocol/context lifecycle.

## Example application

`examples/spring-react/src/main/java/` contains Boot configuration, page controllers, Spring Security integration, demo identity/history/failure cases and health routes. `frontend/src/` contains the browser entry, Node SSR entry, shared page registry and React components.

`frontend/scripts/build.mjs` builds both sides and creates the receipt. `release-assets.mjs` publishes immutable client assets. `frontend/dist/`, `.inertia/` and `node_modules/` are generated, ignored outputs. They must not be hand-edited to fix a source problem.

`deploy/` packages independent immutable releases and supplies a runtime launcher, runbook and supervision examples. It is a deployment pattern for this example, not a general Java process manager embedded in the library.

## Documentation and verification

- `docs/` contains this reader-facing library and canonical API examples. Its Node toolchain is private and excluded from Java deployment payloads.
- `compatibility/` contains Rust-generated fixtures and the explicit qualification matrix.
- `scripts/` contains artifact, consumer, dependency-inventory and aggregate verification commands.
- `target/` directories contain Maven outputs, including source/Javadoc classifiers for reusable modules.

The existing [API guide](../api-guide.md) remains the detailed integration reference while the rest of the public library is written. Generated Javadoc is a symbol index; it complements usage/lifecycle documentation.

## Organize your own application

Keep application controllers, authorized data services and configuration in your application. Keep React pages and a single shared component registry in its frontend. Maintain one coherent client/SSR build and an explicit deployment asset store. Avoid depending on repository sibling paths at runtime; the [first-application tutorial](first-application.md) demonstrates an independent Maven consumer.
