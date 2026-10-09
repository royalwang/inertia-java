---
title: "Prop errors and override diagnostics"
description: "Trace definition/resolution failures and observe override provenance without leaking values."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Props.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropDefinitionException.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/PropDefinitionDiagnosticTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/ObservationContractTest.java
---

# Prop errors and override diagnostics

Use schema diagnostics and bounded observations to explain why a prop was replaced, rejected, skipped or failed. Keep diagnostic values separate from public Page data and high-cardinality metric tags.

## Definition errors

`PropDefinitionException` is an `IllegalArgumentException` with a kind, paths and definition-source accessors. Invalid path syntax/depth and parent/child conflicts fail before supplier execution. Fix the schema; retrying the same definitions cannot repair it.

Exact-key replacement is allowed. `Props.overrides()` reports path, previous source and replacement source without values. `Props.from(...)` labels explicit compositions; the renderer labels internal/config/request/Page sources. Duplicate builder puts retain last-definition behavior, while cross-source overlay reports composition.

The special `errors_override` diagnostic warns that application definitions replaced built-in validation. Keep `errors` reserved if that behavior is required by your forms.

## Runtime observations

Observers can receive prop planning/resolution and failure events. Override events use reasons `prop_override` / `errors_override`; definition failures classify as `prop_definition`. Override observations can occur before partial filtering excludes a key, and they are not additional HTTP requests.

Paths and provenance are developer schema information, not automatically safe user output. If keys contain sensitive application information, do not expose them in public errors or indiscriminate logs. Normal observer events avoid payloads and schema paths.

## Diagnose in order

1. Inspect safe definition/provenance information for a collision or override.
2. Check visit selection and loading flags before assuming a missing value means a failed query.
3. Check executor/deadline/overload observations for selected work.
4. Distinguish a rescued deferred failure from an unrescued whole-Page failure.
5. Correlate server-generated request IDs in logs, without using them as metric tags.

Tests verify winning-supplier execution, schema failures before queries and privacy boundaries. See [observability](../integrations/observability.md) for wiring and [async budgets](async-concurrency.md) before changing capacity in response to an overload.
