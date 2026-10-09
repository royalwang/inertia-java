---
title: "Spring Boot starter"
description: "Explain auto-configuration, required InertiaConfig, optional metrics and bean replacement."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaAutoConfiguration.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaProperties.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaMetricsAutoConfiguration.java
verification:
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaAutoConfigurationTest.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaOverridesTest.java
---

# Spring Boot starter

Use the starter for Servlet Spring MVC and supply the application's Page configuration. It reduces infrastructure wiring; it does not create a frontend, choose your pages or launch Node.

## Install and configure

Add `io.inertia:inertia-spring-boot-starter:0.1.0-SNAPSHOT` using the [installation](../getting-started/installation.md) procedure and Boot 3.5.7 BOM. Define an `InertiaConfig` bean with the component registry and root/asset/SSR policy. Keep typed Page handlers on ordinary `@Controller` methods.

The default graph supplies a Page codec, bounded executor, props resolver, renderer, MVC configurer, handler validator and servlet session mutex listener. Auto-configuration is conditional on a Servlet application; it is not a WebFlux adapter.

## Bound execution

| Formal property (`inertia.`) | Default |
| --- | --- |
| `props-timeout` / `response-timeout` | 3s / 5s |
| `props-concurrency` | 8 |
| `executor-core-size` / `executor-max-size` | 8 / 32 |
| `executor-queue-capacity` | 256 |
| `all-errors` | Unset; keep config value |
| `session-namespace` | `default` |

Timeouts and limits must be positive, max threads must cover core threads, and response timeout must cover props timeout. Invalid configuration fails startup. Components, root IDs, Node endpoints and hot asset paths are configured through application beans; the example's similarly prefixed JVM flags are not additional universal properties on `InertiaProperties`.

## Replace defaults consistently

An `ExecutorService` bean named `inertiaPropsExecutor` replaces the executor. User beans of `PageCodec`, `PropsResolver`, `ResponseRenderer` and `InertiaMvcConfigurer` replace their corresponding defaults. Multiple codec candidates need one primary; the library does not arbitrarily choose among them.

The private Page codec copies its supplied ObjectMapper and does not replace Spring REST message converters. The default resolver/renderer/MVC/advice use the same codec. If replacing part of that graph, preserve codec/config/deadline/observer consistency yourself; properties do not reconfigure an application-owned instance automatically.

The default executor has a managed shutdown. Application replacements own their lifecycle. Optional Micrometer wiring needs an application registry; it does not install Actuator or expose a public metrics endpoint.

Verify startup rejection, replacement-bean precedence, codec consistency and normal Page/REST requests. The Boot tests cover those boundaries. Continue with [observability](observability.md) or [standalone MVC](spring-mvc.md) if Boot is not your host.
