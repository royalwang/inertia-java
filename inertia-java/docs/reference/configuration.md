---
title: "Configuration reference"
description: "List property name/type/default/owner/validation/effect with library versus example configuration clearly separated."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaProperties.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaAutoConfiguration.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaRedisProperties.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaRedisAutoConfiguration.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaConfig.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/RedisHttpSessionStoreFactory.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/RedisSessionLifecycleFilter.java
verification:
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaOverridesTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/ConfigPresentationTest.java
---

# Configuration reference

Choose the configuration owner before setting a value. The starter binds execution budgets; it does not turn every core constructor argument into a Spring property.

## Boot-bound properties

`InertiaProperties` binds the following eight fields under `inertia`. The auto-configuration consumes them when constructing its default beans; a replacement bean owns its own settings.

| Property | Type / default | Constraint and effect |
| --- | --- | --- |
| `inertia.props-timeout` | Duration / `3s` | Positive; selected-prop resolution budget |
| `inertia.response-timeout` | Duration / `5s` | Positive and >= props timeout; MVC response budget |
| `inertia.props-concurrency` | int / `8` | >=1; per-request asynchronous capacity |
| `inertia.executor-core-size` | int / `8` | >=1; default shared executor |
| `inertia.executor-max-size` | int / `32` | >= core size |
| `inertia.executor-queue-capacity` | int / `256` | >=1; bounded shared queue |
| `inertia.all-errors` | Boolean / unset | Unset preserves the supplied core configuration; true/false overrides it |
| `inertia.session-namespace` | String / `default` | 1–64 characters; `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` |

`inertia.ssr-endpoint-id` is read separately by auto-configuration, default `renderer`. It must start with a letter and contain at most 64 letters, digits, underscores, dots or hyphens. It labels diagnostics; it is not an endpoint URL or an additional record field.

This `application.yml` fragment explicitly sets three default execution values. Other settings retain their defaults; leaving `inertia.all-errors` unset preserves the application-owned core configuration.

```yaml
inertia:
  props-timeout: 3s
  response-timeout: 5s
  props-concurrency: 8
```

## Application-owned core configuration

Provide an `InertiaConfig` bean with a version supplier and component registry. `basic(version, components)` selects root `app`, the minimal root view, no gateway, empty shared data, disabled big-integer preservation/history encryption/all-errors, visible shared-prop keys and the request URL resolver.

| Record field | Meaning / contract |
| --- | --- |
| `version` | Current client asset version, stable for this build |
| `rootId` | Root identifier; `[A-Za-z][A-Za-z0-9_-]*` |
| `components` | Allowed component set |
| `rootView` | Owns complete HTML assembly and trusted asset markup |
| `gateway` | Optional renderer; null permits CSR |
| `shared` | Shared props function for this request |
| `preserveBigIntegers` | Serialization presentation for large integers |
| `encryptHistory` | Default official-client history policy |
| `allErrors` | Default validation presentation |
| `exposeSharedPropKeys` | Whether metadata lists shared keys; does not remove values |
| `urlResolver` | Page URL; nonblank, <=8192 characters, no controls |

Immutable `withAllErrors`, `withSharedPropKeys` and `withUrlResolver` copies support overrides. Request `encryptHistory(...)` overrides the default for that response and is not persisted by redirects. See [configuration ownership](../integrations/spring-boot.md).

## Component parameters and lifetime

`HttpSsrGateway` constructors accept endpoint, connect/render durations, codec, build verification and optional root verification/byte/concurrency limits. These are constructor settings, not generic Boot properties. The example uses 200ms connect, 1s render, 2MiB response and 16 simultaneous transports. Reuse the gateway for the application lifetime; it exposes no public `close()` method. The separately owned health monitor has an explicit close contract.

`SsrHealthMonitor` independently accepts endpoint/connect/timeout/interval/codec. The example uses 200ms/1s/5s and closes the monitor. `ViteBuild`/`ViteAssets` consume actual frontend outputs and development hot-file information; they do not guess a compatible build from a URL. See [SSR/Vite APIs](ssr-vite-api.md).

## Example-only controls

JVM system properties below belong to the example `Application`, supplied **before** `-jar`:

| Property | Default |
| --- | --- |
| `inertia.frontend` | Absolute `frontend` directory |
| `inertia.development` | false |
| `inertia.root-id` | `app` |
| `inertia.ssr` | `http://127.0.0.1:13714/render` |
| `inertia.ssr-health` | Renderer origin `/health` |
| `inertia.asset-store` | Frontend `.inertia/assets` |

Additional example environment flags are `inertia.ssr-except` (empty exact/trailing-`*` patterns), `inertia.ssr-health-enabled`, `inertia.benchmark-observations`, `inertia.demo-auth`, `inertia.demo-failures`, `inertia.demo-history-enabled` and `inertia.csp.enabled` (all false unless enabled). `inertia.demo-password` has no safe built-in password; demo authentication requires at least 12 characters. `INERTIA_DEMO_PASSWORD` maps through Spring's environment.

Node uses `SSR_PORT` (13714) and `SSR_ROOT_ID` (`app`). `INERTIA_DEV_APP_ORIGIN` controls the sample Vite development CORS origin. Release-launcher environment is documented in [process setup](../deployment/processes.md); documentation tooling uses `INERTIA_DOCS_BASE`, `INERTIA_DOCS_OUTPUT` and `INERTIA_BROWSER_CHANNEL`. Do not apply those tooling variables to your business application.

Invalid budgets/names fail startup. A missing custom bean or mismatched renderer build needs a corrected configuration, not a wider timeout. The override tests prove that unset and explicit false remain distinct.

## Optional Redis properties

`inertia.session.store` selects `servlet` (default) or explicit `redis`. An application `InertiaSessionStoreFactory` bean owns a custom selection. The Redis module must be on the classpath for Redis selection; unavailable storage is never replaced silently. `InertiaRedisProperties` binds the following fields only when Redis defaults are selected. These configure delivery storage, separate from `spring.data.redis.*` and Spring Session host settings.

| Property | Type / default | Constraint and effect |
| --- | --- | --- |
| `inertia.session.redis.host` | String / `127.0.0.1` | Nonblank standalone host |
| `inertia.session.redis.port` | int / `6379` | 1–65535 |
| `inertia.session.redis.database` | int / `0` | Nonnegative Redis database |
| `inertia.session.redis.username` | String / unset | Optional Redis ACL username |
| `inertia.session.redis.password` | String / unset | Optional credential, excluded from configuration toString |
| `inertia.session.redis.command-timeout` | Duration / `200ms` | 1ms–5s; connect and command bound |
| `inertia.session.redis.max-commands` | int / `32` | 1–1024 concurrent transport operations |
| `inertia.session.redis.idle-ttl` | Duration / `30m` | At least 2ms, at most one day; refreshed on successful CAS |
| `inertia.session.redis.lease` | Duration / `30s` | Positive, shorter than idle TTL; greater than response timeout plus command timeout |
| `inertia.session.redis.terminal-retention` | Duration / `5m` | Positive, no longer than idle TTL |
| `inertia.session.redis.max-bytes` | int / `1048576` | 1024–16777216 UTF-8 envelope bytes |
| `inertia.session.redis.max-reservations` | int / `16` | 1–1024 concurrent reserved deliveries |
| `inertia.session.redis.max-terminals` | int / `4096` | At least max reservations, at most 100000 retained terminal records |
| `inertia.session.redis.max-attempts` | int / `16` | 1–100 explicit no-write conflict/deadline recalculations; one-second dispatch budget also applies |

The lifecycle filter runs after Spring Session and before Security/MVC. Preserve that ordering with a custom host filter. Backend ownership, unknown outcomes and deployment boundaries are described in [custom session stores](../integrations/custom-session.md).
