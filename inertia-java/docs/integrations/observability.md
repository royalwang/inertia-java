---
title: "Logging and Micrometer"
description: "Wire observers, bounded tags, HTTP/render distinctions and sensitive-data exclusion."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaObserver.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/LoggingInertiaObserver.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/MicrometerInertiaObserver.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaMetricsAutoConfiguration.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/ObservationContractTest.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaMetricsTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/MvcObservationTest.java
---

# Logging and Micrometer

Observe production stages without copying user Page data into diagnostics. Rendering, SSR transport, session delivery and HTTP writing have different success boundaries; keep them distinct when interpreting counts and latency.

## Wire one observer

Core constructors accept an optional `InertiaObserver`; older constructors use a no-op. Pass the same observer to the props resolver and renderer, and explicitly to an application-created HTTP SSR gateway or redirect context. `LoggingInertiaObserver` emits structured JSON through `System.Logger`; `InertiaObserver.combine` composes observers.

Observers execute inline and must be fast/nonblocking. Runtime exceptions in an observer are isolated from business results; fatal JVM errors are not swallowed. Avoid performing network export directly inside the callback.

Boot passes an application observer bean to its default resolver/renderer. With Micrometer on the classpath and a single candidate/primary `MeterRegistry`, it supplies `MicrometerInertiaObserver` unless your own observer exists. No registry, ambiguous registries or no Micrometer library leaves the no-op path. The starter does not install Actuator, create a registry, expose endpoints or change security.

## Safe event fields

Events correlate by server-generated request ID and include bounded operation/outcome/reason/status/response information, elapsed time, component and configured safe endpoint ID. They omit URLs, headers, props, flash, exception text and renderer bodies. Keep component/endpoint IDs trusted configuration. These exclusions describe library publishers: the Event constructor does not redact custom strings, and the logging observer writes the supplied event fields without additional redaction.

Micrometer tags are only bounded `outcome`, `reason`, `response` and `status`. Request IDs, component names and endpoint IDs do not become metric tags. Application common tags/MeterFilters remain your responsibility. `inertia.ssr-endpoint-id` labels events; it is read from Environment, not a ninth field on `InertiaProperties`.

## Interpret stages

- Props and SSR timers describe separate stages of one Page; do not add counts as separate user requests.
- `inertia.ssr_http` distinguishes transport/timeout/cancellation/overload and decoded fallback reasons.
- A render success means an outcome was produced, not delivered to the browser.
- `inertia.response` measures adapter write attempts; a successfully written safe 500 is still a success for that write stage.
- Prop-override diagnostics describe planning, even if a partial visit later excludes the key.

The [existing timer inventory](https://github.com/royalwang/inertia-java/blob/main/inertia-java/README.md#observation-spi) lists names and detailed event semantics. Metrics/observation tests check wiring, privacy and bounded tags. When replacing default beans, maintain observer injection yourself and verify the resulting events under both success and failure.

## Upstream references

- [Micrometer timers](https://docs.micrometer.io/micrometer/reference/concepts/timers.html)
- [Creating your own auto-configuration](https://docs.spring.io/spring-boot/3.5/reference/features/developing-auto-configuration.html)
