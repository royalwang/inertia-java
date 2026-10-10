---
title: "Spring API map"
description: "Index MVC, validation, session, Boot conditions and replacement-bean behavior."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaMvcConfigurer.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaHandlerValidator.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaSessionStoreFactory.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/JakartaValidationBridge.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaAutoConfiguration.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaMetricsAutoConfiguration.java
verification:
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaHandlerValidatorTest.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaOverridesTest.java
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/MvcOutcomeAdviceContractTest.java
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/MvcAdviceContractTest.java
---

# Spring API map

The MVC adapter integrates synchronous typed `InertiaResponse`/`HttpOutcome` responses and request-owned effects with Spring MVC. The starter supplies defaults around an application-owned `InertiaConfig`; it does not provide business routes, identity or an SSR process.

## MVC module

| Type | Purpose and boundary |
| --- | --- |
| `InertiaMvcConfigurer` | Register argument/return handlers, interceptors and lifecycle integration |
| `InertiaHandlerValidator` | Reject ambiguous/unsupported handler declarations during startup |
| `InertiaErrorPage` | Application hook for an error Page |
| `HttpSessionStore` | Namespaced session reservation, completion, abort and redirect merge |
| `InertiaSessionStoreFactory` | Create one request-owned store handle from trusted host session identity |
| `ValidationBridge` | Turn supported binding errors into validation bags |
| `JakartaValidationBridge` | Adapt Jakarta constraint violations without rejected values |

Use a regular `@Controller` with an unwrapped synchronous `InertiaResponse` or `HttpOutcome` declaration. `@RestController`, `@ResponseBody`, async Inertia response wrappers and incompatible generic/container return declarations cannot establish the same adapter contract and are rejected when detected. Inherited generic advice is supported when Spring resolves its return type to a concrete supported Page/outcome type. Ordinary REST/file/SSE routes remain Spring's responsibility. See [MVC integration](../integrations/spring-mvc.md).

Exception advice is evaluated with care: rendering a replacement Page uses a fresh sessionless error context while restoring the original delivery. Redirect/outcome advice uses a fresh context for its own new pending effects against the original store and namespace; it does not copy the failed request's shares or pending effects. Invalidating a session and creating another does not authorize moving an already reserved snapshot to the new identity.

The final `InertiaExceptionResolver` is package-private and registered by the configurer. Supply application advice or `InertiaErrorPage` to configure error behavior; it is not a public extension constructor.

## Boot modules

| Type / artifact | Purpose |
| --- | --- |
| `InertiaAutoConfiguration` | Default codec, executor/resolver, session/MVC wiring conditional on application inputs |
| `InertiaProperties` | Eight validated execution/presentation/session settings |
| `InertiaMetricsAutoConfiguration` | Optional Micrometer integration when its prerequisites exist |
| `MicrometerInertiaObserver` | Translate bounded events into timers |
| `inertia-spring-boot-starter` | Dependency entry with a module guide, no runtime facade class |

Supply one intentional configuration/observer/store where replacing defaults. The standard override tests cover custom beans, explicit false and absent optional metrics. A single/primary registry is required for unambiguous metrics integration. No management endpoint or authentication policy is exposed automatically.

## Validation and lifecycle proof

`ValidationBridge` copies messages from binding errors without serializing rejected values or the form target. `JakartaValidationBridge` adapts constraint violations through [Jakarta Path nodes](https://jakarta.ee/specifications/bean-validation/3.0/apidocs/jakarta/validation/Path.Node.html), not provider-specific `Path.toString()` output. Add `spring-boot-starter-validation` or your chosen provider when using the optional Jakarta integration. Custom messages must themselves avoid sensitive values.

Indexed/keyed fields become paths such as `items.0.name` and `byKey.primary.name`; bean-level errors use `_form`, and unindexed iterable elements use `*`. Non-string/numeric/enum map keys are rejected. Constraint violations are sorted by path/message because their input is a Set; Spring binding errors retain their own message order. Keep every message until render-time first/all-errors presentation, rather than discarding messages when saving the delivery.

`HttpSessionStore` verifies its state is still attached to the original active session before and after an operation under the session mutex. [Servlet invalidation removes session bindings](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/http/HttpSession.html). Invalidation or namespace replacement fails instead of reattaching old flash to a new identity. A completed core delivery cannot be rolled back because a later Servlet/network write fails; see [session ownership](../guide/flash-session.md).

Use MockMvc for handler validation, Page metadata, redirect/session ownership and error paths. Use a real browser for CSRF, cookie behavior, official-client forms and hydration. `AssertablePage` assists payload assertions; it does not replace status/header checks. Follow [Spring tests](../testing/spring-tests.md) for the separation.

Exact public constructors and methods are in [Javadoc](javadoc.md). Authentication examples are demonstrations; integrate an actual identity/session backend under your application policy.
