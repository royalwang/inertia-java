# Inertia Java

Build Inertia v3 applications with Java 21 and Spring MVC. The framework-independent core handles Page data and request effects; optional modules provide Node SSR, Vite assets, Spring Boot wiring and test assertions. The runnable example uses the official React client.

The current version is **0.1.0-SNAPSHOT / next**. Install from source or consume locally built Maven artifacts; public Maven publication and the documentation website have not been verified.

## Documentation

Start with [the documentation home](docs/index.md), [the React quick start](docs/getting-started/quick-start.md), or [your first independent Spring application](docs/getting-started/first-application.md). The library contains 70 English pages and [11 priority Chinese pages](docs/zh/index.md); untranslated topics explicitly link to English.

Use [the API guide](docs/api-guide.md) and [configuration reference](docs/reference/configuration.md) for integration details. Seven generated Javadoc classifiers are linked from the [API documentation page](docs/reference/javadoc.md); all six runtime libraries enforce full doclint and warnings-as-errors. [Authoring commands](docs/README.md) build and verify the separate static site.

## Build

Use Java 21, Python 3 and Node 22.22.2 (minimum 22.12). Maven Wrapper pins Maven 3.9.16. From `inertia-java/`:

```sh
./mvnw verify
```

The Java build does not run npm. Follow [the quick start](docs/getting-started/quick-start.md#build-java-and-frontend-outputs) to install the locked frontend dependencies and build matching client/SSR bundles. For consuming libraries in your own project, start with [installation](docs/getting-started/installation.md).

## Run the example

[Run the React example](docs/getting-started/quick-start.md) gives the two terminal commands, working directories, HTML/JSON checks and renderer-stop fallback. The form uses in-memory demonstration data. For frontend editing use [Vite development](docs/getting-started/development.md); for your own application use [the independent tutorial](docs/getting-started/first-application.md).

## Modules and ownership

| Module | Responsibility |
| --- | --- |
| `inertia-core` | Request/Page model, protocol, props, rendering and session SPI |
| `inertia-ssr-http` | Bounded pooled renderer transport and health sampling |
| `inertia-vite` | Manifest, asset tags and build identity |
| `inertia-spring-webmvc` | Typed controllers, validation, request lifecycle and single-node HttpSession |
| `inertia-spring-boot-autoconfigure` / `inertia-spring-boot-starter` | Conditional default beans and dependency entry |
| `inertia-testing` | Page assertions |
| `examples/spring-react` | Application/React demonstration and acceptance harnesses |

See [project structure](docs/getting-started/project-structure.md) and [ownership](docs/concepts/ownership.md). Controllers return synchronous unwrapped Inertia responses; ordinary REST handlers keep Spring behavior. Applications own configuration, authorization, persistence, proxy trust and deployment policy.

## Current boundaries

The [compatibility matrix](docs/getting-started/compatibility.md) distinguishes checked Rust protocol behavior and intentional Java policies. Local acceptance covers SSR/CSR, forms, advanced props, authentication demonstration, history, build integrity and failure recovery. It does not qualify your production environment, business load or distributed session backend. Session reservation is atomic on one node; it does not promise exactly-once delivery to a browser. Cancellation is best effort for underlying work.

Use [browser acceptance](docs/testing/browser-tests.md), [deployment](docs/deployment/build-release.md), [contributing](CONTRIBUTING.md) and [security reporting](SECURITY.md) for the corresponding work. Public deployment, real release-tag documentation snapshots and a confirmed private reporting channel remain open. Historical implementation evidence stays in the [acceptance audit](../docs/inertia-java/07-acceptance-audit.md).

<details>
<summary>Earlier README topic links</summary>

Existing section anchors remain available below. Each points to its current guide or reference. The [migration record](../docs/inertia-java/10-readme-topic-migration.md) preserves the complete earlier text as historical engineering evidence.

## API guide

[Api guide](docs/api-guide.md)

## Observation SPI

[Observability](docs/integrations/observability.md) · [Metrics](docs/reference/metrics.md)

## Prop definitions and overrides

[Basics](docs/props/basics.md) · [Diagnostics](docs/props/diagnostics.md)

## Development mode

[Development](docs/getting-started/development.md)

## Browser verification

[Browser tests](docs/testing/browser-tests.md)

## Execution configuration

[Configuration](docs/reference/configuration.md) · [Async concurrency](docs/props/async-concurrency.md)

## Validation and CSRF

[Forms validation](docs/guide/forms-validation.md) · [Csrf](docs/guide/csrf.md)

## MVC diagnostics and error pages

[Errors](docs/guide/errors.md) · [Spring api](docs/reference/spring-api.md) · [Startup](docs/troubleshooting/startup.md)

## Session namespaces and failures

[Flash session](docs/guide/flash-session.md) · [Custom session](docs/integrations/custom-session.md)

## Vite and SSR endpoint contracts

[Vite assets](docs/ssr/vite-assets.md) · [Gateway](docs/ssr/gateway.md)

## Renderer failure acceptance

[Fallback](docs/ssr/fallback.md) · [Browser tests](docs/testing/browser-tests.md)

## Background renderer health and watch

[Health](docs/ssr/health.md)

## Release build integrity

[Build release](docs/deployment/build-release.md) · [Assets versions](docs/troubleshooting/assets-versions.md) · [Browser tests](docs/testing/browser-tests.md)

## Renderer release verification

[Build release](docs/deployment/build-release.md) · [Gateway](docs/ssr/gateway.md) · [Browser tests](docs/testing/browser-tests.md)

## Request CSP nonces

[Root template](docs/ssr/root-template.md) · [Proxy security](docs/deployment/proxy-security.md) · [Browser tests](docs/testing/browser-tests.md)

## Versioned assets and release switching

[Rolling upgrades](docs/deployment/rolling-upgrades.md) · [Vite assets](docs/ssr/vite-assets.md)

## Custom mount root

[Root template](docs/ssr/root-template.md) · [Configuration](docs/reference/configuration.md)

## Browser history and per-page SSR

[History bigint](docs/guide/history-bigint.md) · [Rendering](docs/concepts/rendering.md)

## Aggregate verification and CI

[Browser tests](docs/testing/browser-tests.md) · [Contributing](docs/community/contributing.md)

## Cancellation and worker ownership

[Async concurrency](docs/props/async-concurrency.md) · [Ownership](docs/concepts/ownership.md)

## Local HTTP performance baseline

[Performance](docs/troubleshooting/performance.md)

## Independent release directory

[Build release](docs/deployment/build-release.md) · [Processes](docs/deployment/processes.md) · [Operations](docs/deployment/operations.md)

## Example CSRF recovery

[Csrf](docs/guide/csrf.md)

## Page URL and shared-key presentation

[Shared data](docs/guide/shared-data.md) · [Core api](docs/reference/core-api.md)

### HTTP policy compatibility

[Protocol](docs/concepts/protocol.md) · [Compatibility](docs/getting-started/compatibility.md)

### Once expiry and invalidation

[Once](docs/props/once.md) · [Fixtures](docs/testing/fixtures.md)

### Library documentation artifacts

[Javadoc](docs/reference/javadoc.md) · [Releases](docs/community/releases.md)

### Required SSR pages

[Fallback](docs/ssr/fallback.md) · [Errors](docs/reference/errors.md)

### Independent Maven consumer rehearsal

[Browser tests](docs/testing/browser-tests.md) · [Releases](docs/community/releases.md)

### Application exception pages

[Errors](docs/guide/errors.md) · [Spring api](docs/reference/spring-api.md)

### Application exception redirects

[Errors](docs/guide/errors.md) · [Flash session](docs/guide/flash-session.md)

### Custom codec and adapter overrides

[Spring boot](docs/integrations/spring-boot.md) · [Custom adapter](docs/integrations/custom-adapter.md)

### Opt-in local identity demonstration

[Authentication](docs/guide/authentication.md) · [History bigint](docs/guide/history-bigint.md)

### Dependency and license declaration inventory

[License](docs/community/license.md) · [Releases](docs/community/releases.md)

### Advanced props and named forms

[Merging](docs/props/merging.md) · [Forms validation](docs/guide/forms-validation.md)

### API guide verification in CI

[Browser tests](docs/testing/browser-tests.md) · [Contributing](docs/community/contributing.md)

## SSR input contract

[Gateway](docs/ssr/gateway.md) · [Ssr vite api](docs/reference/ssr-vite-api.md)

## Development acceptance

[Development](docs/getting-started/development.md) · [Browser tests](docs/testing/browser-tests.md)

</details>

## License

Original Java code, documentation and examples use [Apache-2.0](LICENSE). Third-party software retains its own terms and notices; the parent Rust package has its own license. Preserve [NOTICE](NOTICE) when redistributing. See [license and attribution](docs/community/license.md) for distribution and dependency-review boundaries.

Copyright (c) 2026 royalwang
