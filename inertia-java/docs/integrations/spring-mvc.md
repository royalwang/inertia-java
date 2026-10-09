---
title: "Standalone Spring MVC"
description: "Register configurer, validator, session mutex listener and synchronous typed handlers."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaMvcConfigurer.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaHandlerValidator.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
verification:
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaRequestLifecycleTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaHandlerValidatorTest.java
---

# Standalone Spring MVC

Without Boot, register the Page infrastructure and servlet lifecycle explicitly. Use the same typed handler rules as the starter; ordinary REST and transfer handlers should remain native MVC behavior.

## Assemble one graph

1. Add `inertia-spring-webmvc` and required core/optional SSR/Vite dependencies at the same library version, with your host's managed Spring/Jackson dependencies.
2. Construct one application `InertiaConfig`, `PageCodec`, bounded executor, `PropsResolver` and `ResponseRenderer`.
3. Register `InertiaMvcConfigurer` as a `WebMvcConfigurer` bean, using the constructor accepting config, renderer, response deadline, optional error-page resolver, namespace and the shared codec.
4. Register `InertiaHandlerValidator` with the host's mapping provider for startup inspection of controller/advice contracts.
5. Register Spring's `HttpSessionMutexListener` with the servlet container, and close/shut down application-owned resources on application stop.

The codec-aware constructor keeps Page serialization and request/advice flash/error effects consistent. Older shorter constructors create their original default codec; they are not a substitute when your application deliberately customizes serialization.

## Controller contract

Use synchronous unwrapped `InertiaResponse` or `HttpOutcome` from ordinary controllers. Inject `InertiaRequest` and `InertiaContext` through handler arguments. `@ResponseBody`, REST controllers and async/generic Page wrappers are rejected where incompatible. Put downloads, uploads, SSE and ordinary APIs on their existing framework paths.

The configurer owns preflight before business execution, request context creation, return handling, deadlines and typed exception integration. Do not also commit/render the same context inside the controller. Custom security handlers that queue effects need the same session namespace as MVC.

## Validate the host integration

Start the actual servlet application and inspect HTML, versioned JSON, a stale-version refresh before controller work, a mutation redirect, named validation delivery and a failed Page's recovery. Assert status/headers in addition to Page content. Include ordinary REST and streaming routes to ensure the typed adapter does not intercept them.

The MVC lifecycle/validator/session tests specify these behaviors; a consumer must still validate its own servlet registration and security/proxy configuration. Use [custom adapters](custom-adapter.md) for another HTTP stack and [error handling](../guide/errors.md) for advice precedence.
