---
title: "Installation"
description: "Install from source/local artifacts and select core or starter dependencies without implying Maven Central publication."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/pom.xml
  - inertia-java/inertia-spring-boot-starter/pom.xml
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# Installation

Use Java 21 and the artifacts built from this checkout. The current coordinates are `io.inertia:*:0.1.0-SNAPSHOT`; this documentation does not claim a Maven Central release.

## Install the local libraries

From `inertia-java/`:

```sh
./mvnw install
```

The wrapper pins Maven 3.9.16. This compiles/tests the reactor, creates binary/source/Javadoc artifacts and installs them in your Maven local repository. It does not build the React frontend. Public Maven dependencies and wrapper downloads require network access on a cold cache.

For an isolated distribution rehearsal, build the frontend as described in the [quick start](quick-start.md), then run `python3 scripts/verify-maven-consumer.py`. That command uses a temporary Maven repository and a project outside the checkout; it does not install into your ordinary local cache or publish remotely.

## Choose dependencies

| Artifact | Responsibility |
| --- | --- |
| `inertia-core` | Protocol, props, Page codec, context, renderer and session SPI |
| `inertia-spring-boot-starter` | Servlet MVC dependencies and conditional Boot wiring |
| `inertia-spring-webmvc` | Explicit Spring MVC integration without Boot defaults |
| `inertia-ssr-http` | Trusted HTTP SSR gateway and optional health monitor |
| `inertia-vite` | Manifest/hot assets and coherent build verification |
| `inertia-testing` | Page assertions; use test scope |

A Boot application imports the Spring Boot 3.5.7 dependency BOM and adds:

```xml
<dependency>
  <groupId>io.inertia</groupId>
  <artifactId>inertia-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The starter includes the optional SSR/Vite libraries transitively. It does not start Node, generate React pages or choose the application's root view. Declare an `InertiaConfig` bean with your component allowlist and rendering configuration.

For assertions, add `io.inertia:inertia-testing:0.1.0-SNAPSHOT` with `<scope>test</scope>`. The [complete independent POM](https://github.com/royalwang/inertia-java/blob/main/inertia-java/docs/examples/first-application/pom.xml) demonstrates a consumer without the reactor parent.

## Configure execution separately from presentation

The starter binds execution limits and session settings under `inertia.*`, including props/response deadlines, executor sizes and `session-namespace`. Components, root templates, Vite assets and SSR endpoints belong to your configuration beans. The example's JVM flags such as `-Dinertia.frontend` are example application settings, not universal starter properties.

See the [API guide configuration table](../api-guide.md#boot-configuration-and-replacement-beans) for all formal properties and replacement-bean ownership.

## Troubleshoot dependency resolution

If Maven cannot resolve an `io.inertia` artifact, check that `./mvnw install` completed and that the consumer uses the same Maven local repository and exact version. Do not resolve the problem by adding source directories from the library checkout to your application classpath.

For a private repository, publish the full parent/module POM and jar set using your repository's authorized snapshot workflow. The packaged Maven subtree is release input; its existence does not provide hosted snapshot metadata, credentials, signatures or namespace authorization.

Continue with [your first application](first-application.md) for a working browser application, or the [API guide](../api-guide.md) for a minimal protocol integration.
