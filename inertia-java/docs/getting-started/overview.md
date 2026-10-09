---
title: "What is Inertia Java?"
description: "Choose the adapter and understand Java/Node/browser ownership."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaMvcConfigurer.java
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# What is Inertia Java?

Inertia connects server routes to browser page components without requiring a separate REST API for each page. In this project, Java chooses a registered component and its props. The first document visit receives HTML; subsequent Inertia visits receive a JSON Page which the official client uses to update the browser.

Inertia Java implements the server side of that contract. It follows this repository's Rust implementation and records deliberate differences rather than assuming every adapter has identical behavior.

## Follow one visit

1. Spring routes `GET /users` to an ordinary `@Controller` method.
2. The method returns an `InertiaResponse` naming `Users/Index` and defining props.
3. The renderer selects and resolves those props under a bounded deadline.
4. For an initial document request, it asks the configured Node renderer for HTML, then embeds the Page and asset tags in the application's root view.
5. React hydrates that HTML. A later official `Link` visit sends Inertia headers and receives Page JSON instead of another document.

SSR is optional for an HTML visit. If it fails, the default policy produces a client-rendered shell. A response using `requireSsr()` instead returns a safe 503 when SSR cannot be supplied. JSON visits do not call Node. See [rendering](../concepts/rendering.md) for the full distinction.

## Choose an integration

| Host application | Starting point |
| --- | --- |
| Servlet Spring MVC with Boot | `inertia-spring-boot-starter` and an application `InertiaConfig` bean |
| Spring MVC without Boot | `inertia-spring-webmvc`, explicit configurer, executor and session listener |
| Another Java HTTP stack | `inertia-core` and an adapter implementing the lifecycle in the [API guide](../api-guide.md#core-integration-and-ownership) |

The example uses React and Node. The reusable core does not depend on React, Spring, Node or a database. Other client/framework combinations require their own integration and qualification; the current React evidence is not a blanket claim about every Inertia client.

## Boundaries to understand first

Return Page responses from ordinary `@Controller` methods, synchronously and without wrappers. `@RestController`, `@ResponseBody`, `ResponseEntity<InertiaResponse>` and asynchronous Page wrappers are not supported typed Page entrypoints; startup validation rejects them. Ordinary REST, files and streaming remain with Spring.

Authorization belongs before data definition and query scheduling. Optional and deferred props control when data is loaded, not who can read it. A Node renderer is a trusted internal peer receiving resolved Page data, so avoid exposing its endpoint directly to untrusted traffic.

## Next step

Use the [quick start](quick-start.md) to run the exact example, or [installation](installation.md) to select dependencies for your own application. Read [supported versions](compatibility.md) before changing the locked client or runtime versions.
