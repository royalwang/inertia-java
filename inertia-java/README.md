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

- `inertia-core`: immutable request/Page, protocol policy, ordered prop definitions/resolver (including merge/once/scroll metadata), immutable validation errors/error bags, request context and session SPI.
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
  session-namespace: default
```

Budgets must be positive, max threads must cover core threads, and response timeout must cover props timeout. Applications can supply an `ExecutorService` named `inertiaPropsExecutor`; unrelated executor beans do not interfere. Page registration, assets and SSR configuration currently remain in the application `InertiaConfig` bean.

A response wait timeout aborts the request context and restores its reserved flash. Completion arriving after abort cannot consume the snapshot. Cancellation does not guarantee external work stops, so JDBC/HTTP operations still need their own deadlines. The starter registers `HttpSessionMutexListener`; standalone Spring MVC integrations should register this listener too, so different session facades share the same initialization lock.

## Current boundaries

Production build SSR is verified for the default `app` root. Custom SSR roots, structured observations, deployment gates and distributed sessions remain open. The Feed example exercises append/prepend/reset and once reuse: once props used across different pages must be declared on both pages, such as shared props; a page-local once prop is not an application-wide cache. The demo routes are public, with Spring Security cookie/header CSRF protection on unsafe requests. Authentication policy and production deployment remain application responsibilities. Session delivery promises atomic reservation on one node, not exactly-once delivery to a browser. Future cancellation does not guarantee JDBC or external work has stopped.

## Validation and CSRF

The example uses Jakarta Bean Validation on its request DTO and `ValidationBridge.errors(BindingResult)` to preserve all messages across redirects. The bridge copies messages only; it never serializes rejected values or the form target. Applications must avoid embedding sensitive values in custom validation messages themselves.

`ValidationErrors` stores immutable ordered message lists. `ErrorBags` appends repeated fields rather than replacing them. If a default bag exists, only that bag is delivered, scoped under `X-Inertia-Error-Bag` when present; otherwise named bags are delivered by name. This matches the Rust adapter, including mixed default/named bags. Legacy single-string session values are readable.

Rendering defaults to the first message per field. Core applications can use `config.withAllErrors(true)`. Spring applications can set `inertia.all-errors=true` or `false`; when omitted, the application's `InertiaConfig` controls presentation. The choice is made at render time, so saving only `firstErrors` would discard information prematurely. The React example accepts both strings and lists.

```java
context.withErrors(ValidationErrors.empty()
    .with("name", "Required")
    .with("name", "Too short"));
return context.back();
```

`JakartaValidationBridge.errors(violations)` provides optional direct `ConstraintViolation` integration. It reads messages and [Jakarta Path nodes](https://jakarta.ee/specifications/bean-validation/3.0/apidocs/jakarta/validation/path.node), without using provider-specific `Path.toString()` formatting. Indexed and keyed paths become `items.0.name` and `byKey.primary.name`, bean-level errors use `_form`, and unindexed iterable elements use `*`. Non-string/numeric/enum map keys are rejected. Violations are sorted by path and message because the API returns a Set; BindingResult retains its own message order. To use this optional bridge, add `spring-boot-starter-validation` or your chosen Jakarta Validation provider; the adapter's API dependency is optional.

To run the verified all-errors browser flow, start Java with `--server.port=18083 --inertia.all-errors=true`, then run from the frontend directory:

```sh
INERTIA_BASE_URL=http://127.0.0.1:18083 INERTIA_EXPECT_ALL_ERRORS=true npx playwright test
```

The multi-message flow submits a name that violates length and angle-bracket constraints, checks both messages, then verifies that a valid submission clears the errors and shows flash.

The sample security configuration issues an `XSRF-TOKEN` cookie and accepts Inertia's `X-XSRF-TOKEN` header while keeping masked request attributes. Missing or incorrect tokens return HTTP 403. This policy belongs to the example; the starter does not override an application's security chain. See [Spring Security's SPA CSRF integration](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html).

## MVC diagnostics and error pages

The starter validates registered controller mappings at startup. Typed `InertiaResponse` / `HttpOutcome` endpoints must use `@Controller` without `@ResponseBody`, including composed type/method and interface annotations. Wrapped or async Inertia return types (`ResponseEntity`, `Callable`, futures, etc.) and Inertia context parameters on ordinary return types fail startup with a controller/method diagnostic. Ordinary REST DTOs remain supported. Standalone Spring MVC integrations should register `InertiaHandlerValidator` in addition to `InertiaMvcConfigurer`.

A request reuses its initial immutable snapshot and context on reentry; it cannot render or commit twice. This does not enable asynchronous MVC controllers or ASYNC redispatch.

Applications can provide a safe page factory:

```java
@Bean
InertiaErrorPage errorPage() {
  return (request, status) -> new InertiaResponse(
      "Error", Props.builder().put("status", status).build());
}
```

Register the component in `InertiaConfig`. Factories must define props quickly and queue expensive work through prop suppliers. The resolver runs after Spring's application exception handlers and applies only to typed endpoints with uncommitted responses. It preserves [Spring MVC error status semantics](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/support/DefaultHandlerExceptionResolver.html), including `ErrorResponse`, `@ResponseStatus`, type mismatch and unreadable request bodies. Exception reason text and stack traces are not supplied to the page factory. Logs record exception class/status without message text. The adapter enforces the failure status and `private, no-store` even if the factory returns status 200.

The failed request context is aborted before rendering an error page. Its reservation is restored; the error page uses a separate sessionless context and does not consume that data. Application advice can supply its ordinary response first. Already committed output and ordinary REST handlers retain their existing Spring behavior.

At most one error page is rendered. Factory, props, template or SSR-stage failure, or error-page timeout, produces fixed plain text `Internal Server Error` with HTTP 500 and no `X-Inertia`. Without a factory, a fixed plain-text response retains the classified failure status. Each page attempt has its configured response wait budget; a failed attempt followed by an error page can take up to two such budgets. Async work still needs its own transport deadlines. Fatal JVM errors and unusable client connections are not guaranteed recoverable.

The demonstration has opt-in failure routes, disabled by default. Start the example with `--server.port=18084 --inertia.demo-failures=true`, then from the frontend directory:

```sh
INERTIA_BASE_URL=http://127.0.0.1:18084 INERTIA_EXPECT_FAILURES=true npx playwright test
```

For error-page CSR acceptance, point Java's SSR endpoint at an unused loopback port, use port 18085, and set both `INERTIA_EXPECT_CSR=true` and `INERTIA_EXPECT_FAILURES=true`. Verified flows cover an HTTP 500 Error page, browser hydration/mount, navigation back to Users, and HTTP 403 JSON without internal reasons.

## Session namespaces and failures

`inertia.session-namespace` selects an application-owned namespace inside HttpSession. The default retains the original `io.inertia.session.state` attribute; other namespaces use a suffix. Canonical keys such as `inertia.flash_data` remain unchanged inside each namespace. Names are case sensitive, 1–64 characters, begin with an ASCII letter/digit, and contain only letters/digits/dot/underscore/hyphen. Configure the value from trusted application settings, not request headers or arbitrary tenant input. Standalone integrations can pass the namespace to `HttpSessionStore` and the five-argument `InertiaMvcConfigurer` constructor.

Every store operation checks that its state is still bound to the same active HttpSession before and after operating under the session mutex. [Servlet invalidation unbinds session attributes](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/http/httpsession); an invalidated session or removed/replaced namespace causes failure instead of writing to a detached state object. Data from an expired/invalidated session is never copied into a newly created session. This detects invalidation during an operation; it cannot make arbitrary container invalidation atomic with rendering or network delivery.

Session failures use a fixed fail-closed policy. A failed begin does not run callbacks; a failed redirect merge cannot return a successful redirect. A failed completion causes rendering to fail before MVC writes the prepared body. Context enters FAILED before restoration and rejects late writes. Restoration is attempted at most once, and cleanup failures are attached to the original cause; no automatic replay is attempted when a backend outcome is unknown. Invalidated sessions cannot be restored.

Custom SessionStore implementations must provide atomic begin/merge and at-most-once complete/abort token transitions in their own storage domain. Delivery snapshots are defensive copies. MemorySessionStore prepares a whole merged state before replacement, including reservation restoration, so malformed data cannot partially update flash. Unknown storage outcomes require backend-specific reconciliation; silently dropping errors/flash and reporting success is unsupported. Clustered Spring Session/Redis storage still needs a separate implementation.

MockMvc verifies namespace binding, initialization-write failure, invalidation before rendering, invalidation during a supplier, and invalidation before redirect commit. The concurrent-principal contract captures public DTOs on request threads before asynchronous resolution and verifies that props and flash do not cross requests. Its principals are synthetic; applications own real login/authorization and must pass explicit DTOs to callbacks instead of relying on security ThreadLocals.
