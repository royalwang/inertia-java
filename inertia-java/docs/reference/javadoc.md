---
title: "Generated Javadoc"
description: "Link versioned generated API output for six runtime modules and the dependency-only starter guide."
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

The site preparation step reads actual classifiers, copies their HTML/resources/attribution into the generated public tree and records the input hashes. Generated files remain outside Git. If an actual classifier references an absent optional DejaVu font stylesheet, site preparation removes only that import and uses the stylesheet’s existing system-font fallbacks. Generated HTML also receives a data favicon to avoid an implicit missing favicon request. The API manifest records input/output hashes for these site-only transformations; classifier archives themselves remain unchanged. A complete API-enabled site must fail if a required classifier is absent; it must not silently serve an older cached tree as current documentation.

The API reference covers six libraries with generated symbol indices: core, HTTP SSR, Vite, MVC, Boot auto-configuration and testing. The starter's classifier contains a module guide rather than a generated facade class. Its dependency purpose is documented in [Spring APIs](spring-api.md).

## Module entries

These links open the actual generated HTML from the current Maven classifiers:

- [Core API](javadoc/0.1.0-SNAPSHOT/inertia-core/index.html)
- [HTTP SSR API](javadoc/0.1.0-SNAPSHOT/inertia-ssr-http/index.html)
- [Vite API](javadoc/0.1.0-SNAPSHOT/inertia-vite/index.html)
- [Spring MVC API](javadoc/0.1.0-SNAPSHOT/inertia-spring-webmvc/index.html)
- [Boot auto-configuration API](javadoc/0.1.0-SNAPSHOT/inertia-spring-boot-autoconfigure/index.html)
- [Starter module guide](javadoc/0.1.0-SNAPSHOT/inertia-spring-boot-starter/index.html)
- [Testing API](javadoc/0.1.0-SNAPSHOT/inertia-testing/index.html)

Preparation verifies every generated type page and member anchor. `docs:check` also checks public types against the handwritten owner maps, all 19 core/Boot configuration fields and all 11 timer names. These checks detect index/map drift; they do not prove complete member prose.

## Read the contract

Use the generated signature to check overloads and nested records, then read the owning guide for request/resource lifetime, defaults and failure policy. A method's existence does not prove a compatible usage example or production readiness. API examples retained in [api-guide](../api-guide.md) are compiled independently; the full application tutorial is verified separately.

All six runtime libraries enable full Maven doclint with warnings treated as errors, including missing comments and parameter descriptions. The parent build also defaults to this strict policy. Public source contracts describe purpose, inputs, ownership, results and important failure behavior; record component descriptions also document their generated accessors. Passing doclint establishes structural comment coverage, while source review and executable examples establish the described behavior. The dependency-only starter retains its module guide rather than inventing a Java facade.

## Version and attribution

These artifacts describe the local snapshot, not a released Maven Central version. The site and classifiers carry Apache-2.0/LICENSE/NOTICE. When a real release tag exists, generate its immutable API tree from that tag and preserve the matching handwritten reference. Never relabel a snapshot tree as a stable release.

## Upstream references

- [Maven Source 3.3.1 lifecycle goal](https://maven.apache.org/plugins-archives/maven-source-plugin-3.3.1/usage.html)
- [Maven Javadoc 3.7.0 jar goal](https://maven.apache.org/plugins-archives/maven-javadoc-plugin-3.7.0/jar-mojo.html)
