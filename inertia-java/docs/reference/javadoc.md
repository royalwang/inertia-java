---
title: "Generated Javadoc"
description: "Link versioned generated API output for seven libraries and the dependency-only starter guide."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/pom.xml
  - inertia-java/inertia-spring-boot-starter/src/main/javadoc/index.html
  - inertia-java/scripts/verify-library-artifacts.py
verification:
  - inertia-java/scripts/verify-library-artifacts-test.py
---

# Generated Javadoc

Javadoc describes exact public types, constructors and method signatures for `0.1.0-SNAPSHOT`. Handwritten [core](core-api.md), [Spring](spring-api.md) and [SSR/Vite](ssr-vite-api.md) maps explain ownership and intended use.

## Build and browse

From `inertia-java/`, build the library artifacts with `./mvnw --batch-mode install`. The build generates one `-javadoc.jar` classifier per published module. Run the documentation preparation/build commands after this Maven build so the site can include the matching generated output.

The API reference covers seven libraries with generated symbol indices: core, HTTP SSR, Vite, Redis delivery, MVC, Boot auto-configuration and testing. The starter's classifier contains a module guide rather than a generated facade class. Its dependency purpose is documented in [Spring APIs](spring-api.md).

## Module entries

These links open the actual generated HTML from the current Maven classifiers:

- [Core API](javadoc/0.1.0-SNAPSHOT/inertia-core/index.html)
- [HTTP SSR API](javadoc/0.1.0-SNAPSHOT/inertia-ssr-http/index.html)
- [Vite API](javadoc/0.1.0-SNAPSHOT/inertia-vite/index.html)
- [Redis delivery API](javadoc/0.1.0-SNAPSHOT/inertia-session-redis/index.html)
- [Spring MVC API](javadoc/0.1.0-SNAPSHOT/inertia-spring-webmvc/index.html)
- [Boot auto-configuration API](javadoc/0.1.0-SNAPSHOT/inertia-spring-boot-autoconfigure/index.html)
- [Starter module guide](javadoc/0.1.0-SNAPSHOT/inertia-spring-boot-starter/index.html)
- [Testing API](javadoc/0.1.0-SNAPSHOT/inertia-testing/index.html)

## Read the contract

Use the generated signature to check overloads and nested records, then read the owning guide for request/resource lifetime, defaults and failure policy. The [API guide](../api-guide.md) includes complete Java integration examples; [First application](../getting-started/first-application.md) combines a Spring controller with a React page.

## Version and attribution

Use the Javadoc for the same library version as your application. This reference describes `0.1.0-SNAPSHOT`. The site and classifiers retain Apache-2.0 licensing and attribution.

## Upstream references

- [Maven Source 3.3.1 lifecycle goal](https://maven.apache.org/plugins-archives/maven-source-plugin-3.3.1/usage.html)
- [Maven Javadoc 3.7.0 jar goal](https://maven.apache.org/plugins-archives/maven-javadoc-plugin-3.7.0/jar-mojo.html)
