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
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaDiagnosticsTest.java
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

## Diagnose a failed visit

Start with the failing stage rather than a single aggregate timer:

| Signal | Interpretation and next check |
| --- | --- |
| `inertia.props` with `reason=timeout` | A selected provider exceeded its budget; inspect query duration and cancellation. Raising the SSR timeout does not fix it. |
| `inertia.props` with `reason=overloaded` | Provider execution or its concurrency limit rejected work; inspect active tasks, bounded queue and fan-out before increasing capacity. |
| `inertia.ssr_http` with `reason=connection` or `reason=timeout` | Renderer connection or response failed; inspect Node health and the transport budget. Optional SSR can return CSR; required SSR fails the render. |
| `inertia.ssr_http` with `reason=response_limit` | The renderer response exceeded its byte limit; inspect payload size and the endpoint contract. |
| `inertia.session_merge` or `inertia.session_complete` with `reason=error` | Session storage failed; inspect the backend and transaction outcome. Do not silently switch stores or retry an unknown write. |

A failed provider does not become a successful CSR response. After repairing the cause, verify another visit succeeds and reserved flash/errors remain available under the documented abort contract. Keep response-writing failures separate from render preparation and browser receipt.

## Prometheus examples

Applications may add Spring Boot Actuator and a compatible Prometheus registry through their Boot BOM. Configure endpoint exposure and access in the application; the Inertia starter does not expose management endpoints. See [Boot endpoint configuration](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html).

With a standard Micrometer Prometheus registry, timer counts and sums use seconds-based names. Inspect your actual scrape before installing queries. These examples describe stage events, not independent requests or a production SLO.

Selected-provider failures per second, grouped by bounded reason:

```promql
sum by (reason) (
  rate(inertia_props_seconds_count{reason=~"timeout|overloaded|error"}[5m])
)
```

SSR transport outcomes per second:

```promql
sum by (reason) (rate(inertia_ssr_http_seconds_count[5m]))
```

Mean successful render preparation time in seconds; the zero-traffic case has no meaningful average:

```promql
sum(rate(inertia_render_seconds_sum{outcome="success"}[5m]))
/
sum(rate(inertia_render_seconds_count{outcome="success"}[5m]))
```

Do not sum props, SSR and render durations to estimate request time. Histogram quantiles require application-configured histogram buckets and a suitable exporter; the library does not enable histograms or invent p95 values from timer sums. See [Micrometer Prometheus timers](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html).

## Upstream references

- [Micrometer timers](https://docs.micrometer.io/micrometer/reference/concepts/timers.html)
- [Creating your own auto-configuration](https://docs.spring.io/spring-boot/3.5/reference/features/developing-auto-configuration.html)
