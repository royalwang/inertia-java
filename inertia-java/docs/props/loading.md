---
title: "Lazy, optional and always props"
description: "Show full/partial selection with callback counts, including except-only optional behavior."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/AdvancedDemo.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CoreContractTest.java
  - inertia-java/examples/spring-react/frontend/e2e/advanced.spec.ts
---

# Lazy, optional and always props

Choose loading behavior based on when a value is needed, and define expensive work inside its callback so skipped definitions do not perform the query.

## Selection table

| Source | Full visit | Matching partial visit |
| --- | --- | --- |
| Plain value / `Prop.value` | Included | Included when selected |
| `Prop.lazy(Task)` | Callback executes | Executes when selected |
| `Prop.async(factory)` | Factory schedules | Schedules when selected |
| `Prop.optional(Task)` | Omitted without callback | Executes when selected |
| `Prop.always(value)` | Included | Included despite explicit exclusion |

Lazy means execution is delayed until resolver selection, not that the full visit omits it. Optional means omission from full loading. Deferred adds a later client fetch/group contract; see [deferred props](deferred.md).

`always` changes selection. It cannot make a value safe for every user. Authorize before creating it, and avoid putting expensive work into a precomputed value simply to mark it always.

## Observe the example

At `/advanced`, the sample has an expensive lazy counter, an optional counter and an always status. A full visit omits optional. An explicit `only` can fetch it. An except-only visit selects every remaining definition, including optional, following this repository's Rust behavior. Add optional to `except` when you need to skip its callback.

The displayed counters are diagnostic demo state, not a cache or persisted business record. They make it possible to inspect which queries executed rather than infer execution only from missing fields.

## Failure behavior

Callbacks run under the same total deadline and concurrency cap. Optional sources still fail if selected and unrescued. A value missing because it was not requested is different from a failed query. Do not catch every exception into null and present it as successful omission.

Verify full, matching partial and different-component visits. Assert callback counts as well as Page output. Continue with [partial reloads](partial-reloads.md) before tuning callback costs.
