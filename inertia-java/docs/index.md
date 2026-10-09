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

This documentation covers the development version **`0.1.0-SNAPSHOT`**. See [Installation](getting-started/installation.md) for requirements and build instructions.

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

## Documentation languages

English is the canonical documentation. The [Chinese documentation](zh/index.md) covers getting started and selected application guides. Pages without a Chinese translation link to the English version and are marked accordingly in the Chinese sidebar.

Inertia Java is licensed under [Apache-2.0](community/license.md).
