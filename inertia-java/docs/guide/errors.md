---
title: "Exception handling and error pages"
description: "Explain safe error pages, typed Page/outcome advice, ordering and recursion limits."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaExceptionResolver.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaMvcConfigurer.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/DemoFailures.java
verification:
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaExceptionResolverTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
---

# Exception handling and error pages

Show a safe registered component or a deliberate redirect when a Page request fails. Keep exception details in authorized diagnostics, rather than returning raw exception text in props.

## Choose application handling

Ordinary application `@ExceptionHandler` methods retain precedence. Typed Page handling requires a synchronous unwrapped `InertiaResponse` from an ordinary controller/advice without `@ResponseBody`. REST/`ResponseEntity` advice retains Spring's native semantics.

| Advice result | Context behavior |
| --- | --- |
| Typed Page | Abort original context; create a fresh sessionless error context |
| Typed `HttpOutcome` | Abort original context; create a fresh context retaining the original store/namespace for the advice's own redirect effects |
| Ordinary REST result | Native Spring behavior |

The error context does not copy failed-request shares or pending effects. Stored delivery reserved by the original Page is restored for a later successful business Page. A typed redirect advice can queue its own safe feedback, but it cannot replace an invalidated session or silently rebind removed namespace state.

Local, global and inherited generic typed handlers are supported. Scope global advice to the intended Page controllers so unrelated API errors keep their own contract.

## Provide a final safe Page

An `InertiaErrorPage` resolves a registered error component from request/status. The example supplies a status-only `Error` Page. Choose cache policy and SSR policy deliberately. An error Page requiring a failed renderer cannot create a successful recovery by itself.

If application advice fails, the library has a bounded final error path. Failure of that safe Page does not recursively render it forever. Props failures remain failures; they are not silently converted into a healthy SSR fallback.

## Verify recovery

Exercise a direct controller exception, an asynchronous prop failure, a failing advice Page and a redirect advice with queued feedback. Check HTTP status and headers separately from Page content. Then navigate to a healthy route and confirm old stored delivery survives while failed-request effects do not leak.

The opt-in example failure routes and MVC tests cover those distinct boundaries. Browser tests verify safe Error Pages hydrate or mount and recover through navigation. Use [SSR fallback](../ssr/fallback.md) for renderer-specific failure policy and [observability](../integrations/observability.md) for bounded diagnostics.

## Upstream references

- [Spring MVC error status semantics](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/support/DefaultHandlerExceptionResolver.html)
