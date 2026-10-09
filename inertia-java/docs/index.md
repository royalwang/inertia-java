---
title: "Inertia Java"
description: "Choose quick start, integration, reference or help; show current distribution status."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/pom.xml
  - inertia-java/README.md
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# Inertia Java

Build an Inertia application with Java routes, React pages and optional Node server rendering. Inertia Java supplies a framework-independent protocol core, Servlet Spring MVC integration and a verified Spring Boot + React example.

This documentation describes **`0.1.0-SNAPSHOT` from this repository**. The modules can be built locally or consumed from a packaged Maven subtree. They are not currently advertised as available on Maven Central. The documentation site can be built locally; a public deployment has not yet been verified.

## Start here

| Your goal | Read next |
| --- | --- |
| Understand the architecture | [What is Inertia Java?](getting-started/overview.md) |
| See SSR and browser navigation working | [Run the React example](getting-started/quick-start.md) |
| Create an application outside this checkout | [First Spring application](getting-started/first-application.md) |
| Integrate an existing Java application | [Installation](getting-started/installation.md), then the [API guide](api-guide.md) |
| Build forms and protect mutations | [Forms](guide/forms-validation.md), [authentication](guide/authentication.md) and [CSRF](guide/csrf.md) |
| Control loading and client state | [Loading](props/loading.md), [partial visits](props/partial-reloads.md), [merging](props/merging.md) and [once](props/once.md) |
| Configure rendering and diagnostics | [SSR setup](ssr/setup.md), [fallback](ssr/fallback.md) and [observability](integrations/observability.md) |
| Find defaults and signatures | [Configuration](reference/configuration.md), [API maps](reference/core-api.md) and [Javadoc](reference/javadoc.md) |
| Release or diagnose an application | [Deployment](deployment/build-release.md), [testing](testing/browser-tests.md) and [troubleshooting](troubleshooting/startup.md) |
| Contribute or request help | [Contributing](community/contributing.md), [support](community/support.md) and [security reporting status](community/security.md) |
| Learn the lifecycle and ownership rules | [Request lifecycle](concepts/request-lifecycle.md), then [ownership](concepts/ownership.md) |

## What you get

- A Java 21 core for Page creation, prop selection, protocol responses and transactional session effects.
- Spring MVC argument/return handling and Boot auto-configuration with bounded asynchronous prop resolution.
- HTTP SSR and Vite integration with explicit rendering budgets and build verification.
- A complete React example covering forms, validation, flash, partial visits, deferred props, merging, once, scroll and failure recovery.
- Test assertions, source/Javadoc artifacts and independent deployment/consumer checks.

The library handles the Inertia transport and rendering contract. Your application supplies routing, authorization, business data, a component registry, a root view and deployment policy. Inertia headers do not establish a user's identity.

## Documentation scope

The available navigation contains written pages only. All 70 English catalog pages are written, including deployment, reference, testing, troubleshooting and community. Generated Javadoc is available for all seven modules. The [priority Chinese documentation](zh/index.md) covers 11 selected journeys with English revision tracking; remaining pages explicitly link to English. Public source contracts and strict doclint now cover all six runtime libraries. Version snapshots and launch prerequisites remain tracked in the [open-source documentation plan](https://github.com/royalwang/inertia-java/blob/main/docs/inertia-java/08-open-source-documentation-plan.md). Existing API-guide and source-example paths remain available during the migration.

Original Java code, examples and documentation use Apache-2.0. Copyright (c) 2026 royalwang. See [LICENSE](https://github.com/royalwang/inertia-java/blob/main/inertia-java/LICENSE) and [NOTICE](https://github.com/royalwang/inertia-java/blob/main/inertia-java/NOTICE).
