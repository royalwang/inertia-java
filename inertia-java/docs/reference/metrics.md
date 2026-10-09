---
title: "Metrics reference"
description: "List names, tags, units and counts; distinguish SSR HTTP, rendering and response writes."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/MicrometerInertiaObserver.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaMetricsAutoConfiguration.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaObserver.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Observations.java
verification:
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaMetricsTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/ObservationContractTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/MvcObservationTest.java
---

# Metrics reference

Micrometer support is optional. The integration publishes bounded stage timers; it does not install an exporter, management endpoint or request tracing system.

## Timers

`MicrometerInertiaObserver` maps each operation to `inertia.` plus its lowercase enum name:

| Timer | Operation |
| --- | --- |
| `inertia.props` | Selected prop resolution |
| `inertia.prop_override` | Prop override diagnostics |
| `inertia.ssr` | Renderer decision/result |
| `inertia.ssr_http` | HTTP renderer transport |
| `inertia.render` | Page/render preparation |
| `inertia.session_begin` | Reserve a delivery snapshot |
| `inertia.session_complete` | Complete a successful delivery |
| `inertia.session_abort` | Restore an aborted delivery |
| `inertia.session_merge` | Merge redirect effects |
| `inertia.version_conflict` | Stale version handling |
| `inertia.response` | Adapter response write |

Timers record nonnegative nanoseconds, converted by the registry backend. Tags are `outcome`, `reason`, `response` and `status`. Outcomes are SUCCESS/FAILURE/TIMEOUT/CANCELLED/FALLBACK/CONFLICT. Response kinds are NONE/HTML/JSON/REDIRECT/LOCATION/OTHER. The actual metric tag tokens are lowercase (for example `success`, `html` and `build_mismatch`); the uppercase forms above identify enum constants. Status is bounded to 100–599 or 0 when unavailable. See [reason definitions](errors.md).

## Activation and interpretation

With Micrometer present and one unambiguous registry, auto-configuration supplies an observer unless overridden. A custom observer may combine logging/metrics through `InertiaObserver.combine(...)`. The example's `inertia.ssr-endpoint-id` defaults to `renderer` for event identity; endpoint URL is never a metric tag.

Event request ID, component and endpoint ID support trusted diagnostics but are excluded from timer tags. Events omit URL, headers, props, flash and exception text. Keep custom tags bounded and avoid user/session IDs. An observer runs inline; offload slow sinks with a deliberate bounded policy. Ordinary observer runtime failures are isolated, while fatal JVM errors are not swallowed as successful instrumentation.

A successful response write can deliver an application 500; it does not imply business success. A renderer fallback timer can accompany a valid CSR response. Props/SSR/render/response are different stages and should not be summed as independent request durations. Use your HTTP/application metrics to correlate the overall outcome.

## Verify instrumentation

Run the metrics auto-configuration tests after changing tags/conditions and the core/MVC/HTTP observation contracts after changing event placement. Start your real exporter separately and inspect sample series for bounded labels. The local tests prove event and registry behavior, not availability of your telemetry backend.
