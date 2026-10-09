---
title: "Rust compatibility fixtures"
description: "Regenerate actual Rust oracles and live TTL without overwriting accepted expectations."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustParityTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustHttpParityTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/OnceTtlContractTest.java
  - inertia-java/pom.xml
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/AdvancedPropsTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CancellationContractTest.java
---

# Rust compatibility fixtures

Use checked Rust-generated expectations as an external behavior oracle. Editing the Java implementation and its expected result together can hide a protocol regression.

## Fixture layers

The Java suite consumes 37 Page fixtures and 45 HTTP cases. Of the HTTP cases, 41 follow the Rust oracle directly and four document intentional Java differences. Nine live once-TTL cases additionally verify time-dependent behavior rather than relying only on static JSON.

`RustParityTest` compares resolved Page structure. `RustHttpParityTest` covers outcomes, headers and navigation policy. `OnceTtlContractTest` verifies expiry boundaries. The parent build also consumes root Rust fixture inputs; copying only the Java subdirectory is insufficient for a clean standalone reactor build from source. The published Maven consumer is a separate artifact-consumption boundary.

## Change the oracle deliberately

When changing compatibility, identify the upstream behavior and the application's intended contract first. Regenerate or update the upstream fixture through its actual producer, preserve provenance, and explain any Java-specific difference. Do not overwrite expected JSON with current Java output merely to make tests green.

The four intentional differences and their rationale are recorded in the retained [compatibility documentation](../getting-started/compatibility.md). Dot-path declaration collisions, cancellation/rescue boundaries and session ownership also have Java-specific contract tests; wire fixture parity alone does not cover those behaviors.

## Validate and review

From `inertia-java/`, run the reactor tests to include resource preparation and related contracts. Review both expected Page/HTTP output and source changes. For a new client metadata feature, add or extend the actual browser interaction, because a correct metadata array does not prove official-client merging/loading.

These fixtures establish the checked cases against the recorded Rust implementation. They do not promise universal compatibility with future Inertia clients or every Rust application configuration. Version the compatibility claim with the client/JDK/Boot matrix and release notes.
