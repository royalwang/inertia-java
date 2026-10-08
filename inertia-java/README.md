# Inertia Java

Framework-independent Inertia v3 server adapter with Spring MVC integration, a pooled Node SSR gateway, and a React example. Implementation is in progress; see the [implementation ledger](../docs/inertia-java/05-implementation-status.md) for verified capabilities and remaining work.

## Build

Requires Java 21, Node >=22.12, and npm. Maven Wrapper pins Maven 3.9.16; frontend dependencies are pinned in `package-lock.json`.

From this directory:

```sh
./mvnw verify
cd examples/spring-react/frontend
npm ci
npm run typecheck
npm run build
```

The Java build does not implicitly run npm. The example expects a built frontend manifest when it starts.

## Run the example

Terminal 1, from `inertia-java/examples/spring-react/frontend`:

```sh
npm run ssr
```

Terminal 2, from `inertia-java/examples/spring-react`:

```sh
java -jar target/spring-react-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18080
```

Open [the user page](http://127.0.0.1:18080/users). The form demonstrates redirect validation and flash; it does not persist users. The SSR service binds to loopback port 13714. Stopping it exercises CSR fallback. Static assets are served at `/build/`.

If running the jar elsewhere, pass `-Dinertia.frontend=/absolute/path/to/frontend` before `-jar`. The SSR endpoint may be configured with `-Dinertia.ssr=http://127.0.0.1:13714/render`.

## Development mode

From the frontend directory, run `npm run dev`. Vite binds to `127.0.0.1:15173` and writes `.inertia/hot`. Start the Java example from `examples/spring-react`:

```sh
java -Dinertia.development=true -jar target/spring-react-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18082
```

Java uses Vite's `/__inertia_ssr` endpoint and hot asset URLs in this mode. A standalone Node SSR process is unnecessary. Production ignores the hot file. The sample CORS origins are limited to the documented local Java ports; customize them if changing the ports.

## Browser verification

The current Playwright setup uses an installed Google Chrome through `channel: 'chrome'`; alternatively install a compatible Playwright browser and change the channel. From the frontend directory, with Java and SSR running:

```sh
npx playwright test
```

To test fallback, start a second Java process pointing at an unused local SSR port:

```sh
java -Dinertia.ssr=http://127.0.0.1:13715/render -jar target/spring-react-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18081
```

Then, from the frontend directory:

```sh
INERTIA_BASE_URL=http://127.0.0.1:18081 INERTIA_EXPECT_CSR=true npx playwright test
```

Each mode selects its corresponding browser test and skips the other. Screenshots and Playwright output are saved outside the repository under `/tmp/`.

## Modules and ownership

- `inertia-core`: immutable request/Page, protocol policy, ordered prop definitions/resolver (including merge/once/scroll metadata), request context and session SPI.
- `inertia-ssr-http`: pooled JDK HTTP client, bounded SSR response, timeouts and explicit fallback reasons.
- `inertia-vite`: production manifest snapshot, recursive CSS/import tags and build hash.
- `inertia-spring-webmvc`: typed MVC request/response integration and single-node HttpSession adapter.
- `inertia-spring-boot-autoconfigure` / `starter`: bean wiring and servlet integration. Applications currently provide an `InertiaConfig` bean.
- `inertia-testing`: Page assertions for JSON and HTML script bodies.
- `examples/spring-react`: runnable demonstration and MockMvc/Playwright tests.

Controllers return `InertiaResponse` or `HttpOutcome` and use `@Controller` without `@ResponseBody`. An `InertiaContext` argument provides `render`, `share`, `flash`, error bags, redirects and history flags. REST controllers keep their ordinary Spring behavior. Callback suppliers receive explicit captured DTOs/services; no security/transaction ThreadLocal propagation is implied.

## Execution configuration

The starter binds and validates these properties at startup:

```yaml
inertia:
  props-timeout: 3s
  response-timeout: 5s
  props-concurrency: 8
  executor-core-size: 8
  executor-max-size: 32
  executor-queue-capacity: 256
```

Budgets must be positive, max threads must cover core threads, and response timeout must cover props timeout. Applications can supply an `ExecutorService` named `inertiaPropsExecutor`; unrelated executor beans do not interfere. Page registration, assets and SSR configuration currently remain in the application `InertiaConfig` bean.

A response wait timeout aborts the request context and restores its reserved flash. Completion arriving after abort cannot consume the snapshot. Cancellation does not guarantee external work stops, so JDBC/HTTP operations still need their own deadlines. The starter registers `HttpSessionMutexListener`; standalone Spring MVC integrations should register this listener too, so different session facades share the same initialization lock.

## Current boundaries

Production build SSR is verified for the default `app` root. Custom SSR roots, structured observations, full all-errors Page integration and distributed sessions remain open. The Feed example exercises append/prepend/reset and once reuse: once props used across different pages must be declared on both pages, such as shared props; a page-local once prop is not an application-wide cache. The demo routes are public, with Spring Security cookie/header CSRF protection on unsafe requests. Authentication policy and production deployment remain application responsibilities. Session delivery promises atomic reservation on one node, not exactly-once delivery to a browser. Future cancellation does not guarantee JDBC or external work has stopped.

## Validation and CSRF

The example uses Jakarta Bean Validation on its request DTO and `ValidationBridge.firstErrors(BindingResult)` to redirect messages into the next Page. The bridge copies messages only; it never serializes rejected values or the form target. `allErrors` exposes immutable message lists, but core Page all-errors mode remains an implementation item.

The sample security configuration issues an `XSRF-TOKEN` cookie and accepts Inertia's `X-XSRF-TOKEN` header while keeping masked request attributes. Missing or incorrect tokens return HTTP 403. This policy belongs to the example; the starter does not override an application's security chain. See [Spring Security's SPA CSRF integration](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html).
