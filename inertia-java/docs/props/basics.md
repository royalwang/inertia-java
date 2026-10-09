---
title: "Values, nested props and paths"
description: "Define literals/nested paths, reject conflicts and distinguish omission from null."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Props.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CoreContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustParityTest.java
---

# Values, nested props and paths

A `Props` value is an ordered set of named definitions. Build it from explicit serializable application DTOs and `Prop` sources. Resolving those definitions produces Page props; the builder itself is not a JSON Page.

## Define values

`Props.builder().put(path, value).build()` wraps an ordinary value as `Prop.value`. Scalars, lists, maps and DTOs use the configured Page codec. Passing another `Props` creates nested definitions with their own loading/selection behavior.

Dot paths create nested output, for example `auth.user.name`. A path cannot contain whitespace or empty segments, and its depth is limited to 32. The builder rejects invalid paths. Defining both a parent and descendant conflicts even if one would later be filtered from the visit.

| Definition | Interpretation |
| --- | --- |
| Plain map/DTO value | One serializable value |
| Nested `Props` | Child prop definitions planned recursively |
| `Prop.lazy(...)` | Deferred execution of a source, but eager loading policy |
| `Prop.optional(...)` | Source omitted from full visits |

Selection works on definitions and paths, not as a general authorization-aware JSON projection over every field of an arbitrary DTO. Construct safe DTOs before defining them. Do not put a complete private entity in a map and expect a client's `only` request to protect its other fields.

## Order and composition

Exact duplicate keys use the last definition and retain the first declaration position. Cross-source composition has the same winning-source behavior; only that source runs. Nested/object client merging is a separate protocol feature described in [merging](merging.md).

Keep expensive work inside a source if selection should skip it. Computing a query before calling `put` has already incurred its cost even when the definition is omitted later.

## Check the output

Assert resolved values, omission versus null and nested paths. Test parent/child conflicts before introducing query work. `CoreApiExample.java` is a complete compiling integration; core contract/parity tests cover nested definitions and exact Page trees. Continue with [loading policies](loading.md) and [partial reloads](partial-reloads.md).
