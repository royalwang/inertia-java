---
title: "Startup and dependency errors"
description: "Diagnose unsupported return types, absent config/components, ambiguous beans and consuming snapshots."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaProperties.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaHandlerValidator.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
  - inertia-java/pom.xml
verification:
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaAutoConfigurationTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaHandlerValidatorTest.java
---

# Startup and dependency errors

Start with the first actionable error and the exact input versions. Repeatedly changing timeouts will not repair missing classes, invalid bean configuration or incompatible build inputs.

## Missing artifacts or classes

For source development, run the Java reactor from `inertia-java/` using its wrapper and JDK21. Keep root Rust fixture inputs present. The independent tutorial first installs local snapshot artifacts, then builds a separate application without the reactor parent. A `0.1.0-SNAPSHOT` coordinate in this documentation does not imply an artifact is available from Maven Central.

Use the dependency tree and the canonical POM to diagnose unresolved or duplicate dependencies. Avoid mixing a source checkout's classes with a previous snapshot's classifiers. The [installation guide](../getting-started/installation.md) describes the supported paths.

## Bean and handler failures

Supply an intentional `InertiaConfig` with a known component set/version. Review [configuration defaults](../reference/configuration.md), especially positive budgets, response>=props timeout, executor max>=core and safe session namespace. Explicit false differs from unset `all-errors`.

For handler validation errors, replace unsupported REST/response-body/async Page declarations with the supported typed synchronous controller path, or keep that route outside the adapter. Do not bypass the validator to make an ambiguous handler boot.

## Frontend and process failures

Use locked npm inputs and Node>=22.12. Rebuild client and SSR together before loading the production Vite receipt. Confirm free ports, correct frontend directory and absolute paths where required. Development hot-server settings must refer to a real trusted server; production requires verified outputs.

## Prove recovery

Restart once with corrected inputs. Confirm Java liveness, an initial HTML Page, current-version JSON and actual same-build SSR when enabled. Run the focused startup/override/handler tests after changing integration code. If reporting a bug, include a sanitized first error, versions, minimal configuration and reproduction commands; follow [support policy](../community/support.md).
