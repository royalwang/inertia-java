# Inertia Java

Framework-independent Inertia v3 server adapter with Spring MVC integration, a pooled Node SSR gateway, and a React example. Implementation is in progress; see the [implementation ledger](../docs/inertia-java/05-implementation-status.md) for verified capabilities and remaining work.

## Observation SPI

Core provides an opt-in `InertiaObserver` for props, render, SSR success/fallback, and session begin/complete/abort/merge. Pass it to both the `PropsResolver` and `ResponseRenderer` constructors; standalone redirect contexts accept it in their constructor. Existing constructors use a no-op observer. `LoggingInertiaObserver` emits JSON through `System.Logger`; `InertiaObserver.combine` composes observers. Observers run inline and must be fast and nonblocking. Runtime exceptions from an observer are isolated from the business result; fatal JVM errors are not swallowed.

Events correlate with a server-generated request ID and contain operation/outcome/reason enums, elapsed nanoseconds, response status/kind, component and a configured endpoint ID. They do not contain URL, headers, props, flash, exception text, or renderer response bodies. Treat component and endpoint IDs as trusted application configuration. Do not use request IDs or component names as metric tags. Render success describes producing an outcome, not delivery to a browser.

Spring Boot automatically passes an application `InertiaObserver` bean to its default props resolver and renderer. With Micrometer on the application classpath and one `MeterRegistry` (or a primary registry), the starter supplies a `MicrometerInertiaObserver` unless an application observer is already defined. With no registry, ambiguous registries, or no Micrometer library, it keeps the no-op default. Micrometer is optional; the starter does not install Actuator, create a registry, expose HTTP metrics, or change application security. Applications can add their usual Boot Actuator/registry setup, or provide a registry explicitly. Micrometer's version is managed by the project's Boot BOM.

Timers are named `inertia.props`, `inertia.ssr`, `inertia.ssr_http`, `inertia.render`, `inertia.session_begin`, `inertia.session_complete`, `inertia.session_abort`, `inertia.session_merge`, `inertia.version_conflict`, and `inertia.response`. Timer count measures observations and duration uses nanoseconds, converted by the metrics backend. Tags are only `outcome`, `reason`, `response`, and `status`: enum values and status 100–599/0. Request IDs, components, endpoint IDs and exception messages never become metric tags. Application MeterFilters/common tags remain application-owned. See [Micrometer timers](https://docs.micrometer.io/micrometer/reference/concepts/timers.html).

`HttpSsrGateway` has URI/resolver constructors accepting an `InertiaObserver` and safe endpoint ID after the existing `verifyBuild`/`rootId` parameters. Older constructors remain no-op. The sample injects the same application observer and endpoint ID into its gateway. Applications constructing their own gateway must do this explicitly. `inertia.ssr_http` distinguishes timeout, connection failure, response limit, other transport failure, cancellation, overload, exclusion and decoded fallback reasons. Public fallback strings remain compatible (`transport-or-timeout` for transport failures). A handled timeout has `outcome=fallback,reason=timeout`; caller cancellation has `outcome=cancelled,reason=cancelled`. HTTP status is recorded when a complete response is decoded, otherwise 0; it is never inferred from exception text. Cancellation releases transport ownership before diagnostics run. Each request emits at most one HTTP observation, including late transport completion. SSR and HTTP timers describe separate stages; do not add their durations or counts as if they were separate user requests.

MVC observes a version conflict before controller execution and each adapter response write attempt, including redirects and safe error output; ordinary REST handlers remain separate. `inertia.response` measures adapter writing, not full request latency. `outcome=success,status=500` means writing the safe error response succeeded. A failed write followed by a plaintext retry has two write observations. Invalid request snapshots still receive safe plaintext without emitting a fabricated correlation event. The original failed page and its error page use the same server-generated request ID.

Set `inertia.ssr-endpoint-id=primary` to label SSR events in logs; the default is `renderer`. It accepts a 1–64 character safe identifier and is never the raw renderer URL. To combine JSON logging with metrics, define one observer bean in application configuration (this replaces the automatic metrics observer):

```java
@Bean
InertiaObserver inertiaObserver(MeterRegistry registry) {
  return InertiaObserver.combine(
      new MicrometerInertiaObserver(registry), new LoggingInertiaObserver());
}
```

The classes above are `io.inertia.core.InertiaObserver`, `io.inertia.core.LoggingInertiaObserver`, `io.inertia.boot.MicrometerInertiaObserver`, `io.micrometer.core.instrument.MeterRegistry`, and Spring's `Bean`. Replacing the default `PropsResolver`/`ResponseRenderer` beans makes their observer injection application-owned. Spring's conditional auto-configuration behavior is documented in [Creating your own auto-configuration](https://docs.spring.io/spring-boot/3.5/reference/features/developing-auto-configuration.html).

## Prop definitions and overrides

Exact keys retain their first declaration position and use the last definition: internal `errors` < config shared props < request `share` < page props. Only the winning supplier runs. This includes `errors`: replacing it is permitted, may replace validation data and its `always` loading behavior, and emits an `errors_override` diagnostic. Keep `errors` reserved in applications that rely on built-in validation; an observer is a diagnostic hook, not an authorization policy.

`Props.from(Props.Source, props)` labels definition provenance. `Props.overlay(...)` preserves it and exposes an immutable `overrides()` report with schema path/previous/replacement sources; it contains no values and is never inserted into Page JSON. The renderer labels internal/config/request/page sources automatically. Builder definitions start as `DECLARED`. Reports describe overlay operations (including earlier composition), not query execution or a deep merge of objects. Duplicate `put` calls within one builder keep the existing last-definition behavior; the overlay report covers composition across Props sources.

Invalid paths and parent/child conflicts throw `PropDefinitionException`, still an `IllegalArgumentException`, with `kind`, paths and source accessors. Conflicts are rejected before suppliers execute. Exception paths and reports are developer schema diagnostics; do not expose or log them if keys contain application-sensitive information. Telemetry emits `inertia.prop_override` with only `reason=prop_override` or `errors_override`, and classifies definition failures as `prop_definition`; it does not include property names or source payloads. Override observations are produced during root definition planning, even when partial filtering later excludes the key, and use duration/status 0. They are not additional user requests. Nested definitions can be inspected through their own Props reports.

An unrescued supplier failure terminates resolution immediately and cancels owned sibling work rather than waiting for a never-completing sibling. Invalid scroll results also terminate immediately. The first observed fatal failure wins; parallel failure selection is not promised to follow declaration order. Successful values and metadata still follow declaration order. Explicit `rescue` remains limited to deferred props: a rescued source failure is omitted with `rescuedProps` metadata while healthy siblings continue. Cancellation of underlying work remains subject to the documented provider limits.

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

Open [the user page](http://127.0.0.1:18080/users). The form demonstrates redirect validation and flash; it does not persist users. The SSR service binds to loopback port 13714. Stopping it exercises CSR fallback. Production static assets are served at `/build/<build-id>/` from an immutable release asset store.

If running the jar elsewhere, pass `-Dinertia.frontend=/absolute/path/to/frontend` before `-jar`. The SSR endpoint may be configured with `-Dinertia.ssr=http://127.0.0.1:13714/render`.

## Development mode

From the frontend directory, run `npm run dev`. Vite binds to `127.0.0.1:15173` and writes `.inertia/hot`. Start the Java example from `examples/spring-react`:

```sh
java -Dinertia.development=true -jar target/spring-react-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18082
```

Java uses Vite's `/__inertia_ssr` endpoint and hot asset URLs in this mode. A standalone Node SSR process is unnecessary. Production ignores the hot file. The sample CORS origins are limited to the documented local Java ports; customize them if changing the ports.

## Browser verification

The Playwright setup defaults to installed Google Chrome. To use the browser shipped for the locked Playwright version, run `npx --no-install playwright install --no-shell chromium` and set `INERTIA_BROWSER_CHANNEL=chromium`. On a Linux CI host, add `--with-deps` to install its runtime dependencies. From the frontend directory, with Java and SSR running:

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

Production SSR and CSR are verified for both the default `app` root and a custom `portal` root. Broader business-load/deployment gates and distributed sessions remain open. The Feed example exercises append/prepend/reset and once reuse: once props used across different pages must be declared on both pages, such as shared props; a page-local once prop is not an application-wide cache. The demo routes are public, with Spring Security cookie/header CSRF protection on unsafe requests. Authentication policy and production deployment remain application responsibilities. Session delivery promises atomic reservation on one node, not exactly-once delivery to a browser. Future cancellation does not guarantee JDBC or external work has stopped.

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

The starter validates registered controller mappings and controller-local/global exception handlers at startup, including inherited generic methods and parent-context advice. Typed `InertiaResponse` / `HttpOutcome` endpoints must use `@Controller` without `@ResponseBody`, including composed type/method and interface annotations. Wrapped or async Inertia return types (`ResponseEntity`, `Callable`, futures, etc.) and Inertia context parameters on ordinary return types fail startup with a controller/method diagnostic. Ordinary REST DTOs remain supported. Typed exception pages must likewise use ordinary `@ControllerAdvice` without `@ResponseBody` or `@RestControllerAdvice`; validation reads advice types without resolving request-scoped instances. Standalone Spring MVC integrations should register `InertiaHandlerValidator` in addition to `InertiaMvcConfigurer`.

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

## Vite and SSR endpoint contracts

Manifest loading validates every record's `file`, optional `css` / `imports` arrays, and referenced static imports before serving HTML. Output paths must be relative paths with nonempty segments; absolute paths, traversal, dot segments and unsafe tag characters fail configuration. Recursive imports deduplicate CSS/preloads, including cycles; the entry itself is never preloaded as an import. These fields follow [Vite's backend integration manifest](https://vite.dev/guide/backend-integration). Dynamic imports remain managed by the client bundle rather than eagerly preloaded.

Production retains its startup manifest/hash snapshot and ignores hot files. Development refreshes the manifest using the full filesystem modification timestamp when no hot file exists. Hot values must be HTTP(S) origins, with no credentials, path, query or fragment and a valid port. A single trailing slash is accepted. Assets fail on an invalid hot value; the SSR resolver returns an unavailable fallback rather than contacting the production renderer through a malformed development URL.

SSR route exclusions apply to the request path without query parameters: an optional leading slash is ignored, exact rules match exactly, and a trailing `*` matches a prefix. Production ignores hot files; a configured missing bundle disables rendering. HTTP renderer redirects are not followed and request Cookie/Authorization headers are not forwarded. Target URLs remain trusted application configuration; syntactic validation is not an internal-network allowlist.

## Renderer failure acceptance

The SSR budget covers headers and the entire response body. A separate deadline future requests cancellation of the underlying HTTP exchange on timeout; caller cancellation of the returned future also requests transport cancellation. Both paths release the logical concurrency permit exactly once. Overload returns a fallback without dispatching HTTP work. Synchronous request preparation failure also releases the permit. JDK cancellation is best effort: [HttpClient cancellation](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html#sendAsync(java.net.http.HttpRequest,java.net.http.HttpResponse.BodyHandler)) can close HTTP/1.1 connections asynchronously and cannot undo renderer work already started.

After Maven `verify` and frontend `npm run build`, run from the frontend directory:

```sh
npm run test:ssr-failures
```

This harness starts one Java example and a loopback renderer peer on ephemeral ports. Chrome exercises HTTP 503, a response body stalled after headers, and a response exceeding the example's 2 MiB limit. Each scenario verifies HTTP 200 CSR HTML, client mount, deferred data, navigation, validation, successful CSRF-protected submission and flash. It checks that the peer was actually called and did not receive credentials. Logs default to `/tmp/inertia-java-ssr-failures`; `INERTIA_FAILURE_OUTPUT` overrides this location. Only processes created by the harness are stopped on exit. Socket-level Java contracts separately prove cancellation/overflow close the peer connection and allow the next request, and overload never dispatches.

## Background renderer health and watch

`SsrHealthMonitor` is an optional, independently managed background sampler. Supply a trusted `/health` URI, connect/whole-response budgets, a delay between completed probes, and `PageCodec`; call `start()` once and `close()` on shutdown. The monitor owns its pooled HttpClient and daemon scheduler. Probes do not overlap, follow redirects or forward credentials, and response bodies are limited to 4 KiB. A 200 JSON object with `status: "OK"` is healthy, matching the locked Inertia server. Timeout, unavailable transport, bad status/schema or oversized health bodies publish DOWN with a fixed reason. Snapshot reads send no HTTP request. Lifecycle states are UNKNOWN before checking, UP/DOWN after a check, and STOPPED after close; late completion cannot overwrite STOPPED.

The example leaves monitoring disabled by default. For a standalone Node renderer, start Java with `--inertia.ssr-health-enabled=true`. The health target defaults to `/health` on the configured renderer origin; `-Dinertia.ssr-health=http://internal-renderer:13714/health` overrides it. Probe connect/render budgets are 200 ms / 1 s, with a 5 s delay after each check. `/api/ssr-health` exposes only the cached state/reason/check timestamp; it contains no endpoint, props or renderer response. `/api/health` remains Java liveness and does not depend on SSR. A healthy endpoint proves the peer answered its health protocol, not that every component can render; snapshot state may be stale until the next check. Applications decide whether SSR is required for readiness. The Vite development plugin does not provide this standalone health protocol automatically.

For development against built bundles, `npm run ssr:watch` uses [Node's native watch mode](https://nodejs.org/api/cli.html#--watch). It restarts Node when the built SSR bundle changes; it does not compile TypeScript. Use Vite `npm run dev` for source development or rebuild the bundle in a separate terminal. Use a container/process manager for production supervision.

After Maven verify and the frontend build, `npm run test:ssr-health` creates its own loopback Node watch process and Java instance. It copies the SSR bundle under an ignored temporary dist directory, verifies a bundle change really restarts the renderer, observes UP→DOWN→UP, checks Java liveness and CSR fallback while Node is stopped, then verifies restored SSR without restarting Java. Owned process groups and the temporary copy are cleaned on exit. Logs are written to `/tmp/inertia-java-ssr-health` (`INERTIA_HEALTH_OUTPUT` overrides it). This smoke command currently targets macOS/Linux; the Windows child-tree termination path needs separate qualification.

## Release build integrity

`npm run build` runs both locked Vite builds and then writes `dist/build.json`. The receipt inventories every client/SSR output, including manifest and source maps, with SHA-256 digests. Its build id is the SHA-256 of canonical sorted file hashes. The previous receipt is removed before building; a failed build cannot leave an old success receipt in place. Vite's output directories still need an isolated release workspace; this command is not an atomic in-place deployment tool.

`ViteBuild` verifies receipt format/id, required outputs, content digests, manifest asset references and the complete client/SSR file inventory. Traversal and links escaping the release root fail. The example requires this receipt at production startup and uses its build id as Page version, so an SSR-only output change also changes the protocol version. Development hot mode keeps its separate development version. General `ViteAssets` remains usable without adopting this sample release layout.

Run `npm run test:build-integrity` after the frontend build and Maven verify. It copies owned release fixtures to temporary directories, starts the actual Java jar, checks the valid Page version and proves startup rejects mixed SSR, missing client assets and unrecorded extra client files. Logs default to `/tmp/inertia-java-build-integrity` (`INERTIA_BUILD_OUTPUT` overrides it). It does not mutate the real release.

Treat a verified release directory as immutable after startup. SHA-256 integrity does not establish artifact authenticity or verify which build a remote Node service is running. Renderer verification and release asset switching are described below; production orchestration and storage retention still require application deployment policy. Old resources should be retained in a separate release/CDN arrangement; appending them to a verified client directory is rejected as an unrecorded mutation.

## Renderer release verification

Both HttpSsrGateway constructor families accept a final `boolean verifyBuild` option. It defaults to false for existing integrations. With verification enabled, a successful renderer response must carry a textual `buildId` equal to the outgoing Page version. Missing, wrong-type or mismatched ids return `build-mismatch` and never inject the returned HTML. Normal response schema and size checks still apply. The production example enables this option; Vite development keeps it disabled because its module graph has a different lifecycle.

The production React SSR entry reads the adjacent build receipt, verifies its canonical build id and the SHA-256 of the executing SSR entry file before opening the listener. It does not validate external node_modules contents or artifact signatures; deploy locked dependencies and trusted immutable releases. Before component resolution, it compares the Page version with its verified id. A mismatched Page produces an empty body plus its actual id, allowing Java to classify the mismatch. Successful responses include that same id with the normal head/body. This binds Java's verified client/manifest to the renderer's verified entry instead of relying only on a healthy TCP endpoint.

`test:ssr-failures` now includes a wrong-build renderer scenario verified through Chrome CSR mount/navigation/form/flash. `test:ssr-health` checks that the real Node rejects a wrong-version Page before resolving an intentionally unregistered component. Its watch test rewrites the exact same verified bytes and observes two startup markers; changing bytes without producing a matching receipt correctly prevents restart. `test:build-integrity` also proves a changed Node entry fails before listening. Node watch does not make rolling upgrades atomic: switch verified release instances together and retain old client assets through the serving layer.

## Request CSP nonces

Applications may attach a trusted nonce to the immutable `InertiaRequest` using its four-argument constructor; existing three-argument calls retain null nonce. Spring MVC reads only `InertiaMvcConfigurer.CSP_NONCE_ATTRIBUTE` from server request attributes. Incoming nonce headers are not promoted to this metadata. Nonces are validated as bounded base64/base64url tokens, never placed in Page props/history or JSON visits, and remain fixed in a request snapshot.

`RootView.View.nonce()` exposes the value to the root template. `ViteAssets.tags(entry, nonce)` / `ViteManifest.tags(entry, nonce)` apply it to asset tags and the development React refresh preamble. CSR's nonexecutable Page script also receives it. The adapter does not rewrite trusted SSR fragments or automatically grant arbitrary SSR component scripts permission. The sample SSR Page script is nonexecutable JSON; any application-provided executable head/body script must follow that application's template policy.

The opt-in example filter (`--inertia.csp.enabled=true`) generates 32 random bytes per request, applies a nonce + strict-dynamic script policy, and adds a root nonce meta value. The React bootstrap passes that value to the locked official client's `nonce` option. JSON navigation does not replace the active document policy or nonce. The demo allows inline styles and the documented Vite connection origin in development; it is a script nonce integration example, not a universally strict policy preset. Applications own their CSP and source lists. See the [W3C CSP3 working draft](https://www.w3.org/TR/CSP3/#strict-dynamic-usage) for policy semantics.

After the normal builds, run `npm run test:csp`. It runs real Node + Java + Chrome in production SSR and disconnected CSR modes, checking matching header/meta/module nonces, deferred data, navigation, CSRF form/flash, fresh values on full requests and no application policy violations. A separate parser fixture using the real policy permits a correctly nonced inline script and blocks an untrusted inline script. This fixture avoids DevTools script injection and synthetic-response loopback module restrictions.

For development, start Vite normally, then Java with both `-Dinertia.development=true` and `--inertia.csp.enabled=true` on port 18082. From the frontend directory:

```sh
INERTIA_BASE_URL=http://127.0.0.1:18082 INERTIA_EXPECT_CSP=true npx playwright test --grep 'content security policy'
```

The development check includes all three Vite/refresh/app module nonce attributes. Current evidence is Chrome on macOS; additional browser/platform qualification remains a release check.

## Versioned assets and release switching

The example uses `/build/<build-id>/` for production resources. Vite builds use a relative base so bundle-relative assets resolve inside that release. Generic ViteAssets/ViteManifest constructors retain `/build/`; an additional asset-base parameter accepts a safe origin-relative directory prefix. Development hot origins keep their own behavior; when no hot file exists, the sample retains its direct `/build/` mapping to the built client directory for CSR fallback.

`npm run build` also publishes the verified client files into `.inertia/assets/<build-id>/`. Each release is staged, checked and renamed into place on the same filesystem. Repeated publication verifies an existing release rather than overwriting it; malformed source/receipts or a corrupted archive fail. Old releases are preserved. Java verifies its own release's archived client files before startup and serves the asset store at `/build/**`. It never modifies earlier releases or mixes extra assets into its verified dist/client inventory.

Use `-Dinertia.asset-store=/absolute/shared/asset-store` to serve a separately managed store. Publish the built release to that store with `npm run publish:assets -- /absolute/shared/asset-store`, before starting the matching Java/Node pair. The local publisher requires trusted storage and same-filesystem rename semantics; it is not a remote CDN uploader or a signature verifier. Retention is explicit: no automatic deletion is performed. Keep every release still reachable by active clients, cached documents or rollback policy. CDN/object storage needs its own immutable upload and retention implementation.

`npm run test:assets` verifies two-release preservation, idempotent publication and rejection without overwrite. `npm run test:release-switch` creates isolated real Node/Java A and B releases, a shared store and a loopback test router. B is a controlled changed-client-byte fixture with a recomputed complete receipt, not a second source revision. Chrome loads A, switches the router to B, reads A's old asset byte-for-byte through B, then follows an official Inertia link: B returns 409/new version/location, the client performs a full refresh, and new resources, SSR/hydration/navigation/CSRF/flash work. Owned processes/temporary releases are removed; logs remain in `/tmp/inertia-java-release-switch` (`INERTIA_SWITCH_OUTPUT` overrides it).

For production, prepare/publish assets before readiness, start Java and Node from the same immutable receipt, then change ingress routing to the ready pair. Rolling sessions remain application-owned: this public sample does not prove authenticated session continuity between instances or clustered session semantics. The loopback router is an acceptance fixture, not an operational deployment control plane. Migrating an existing unversioned `/build/` deployment also requires preserving its legacy resource URLs explicitly during transition.

## Custom mount root

Set Java `-Dinertia.root-id=portal` before `-jar`, and set `SSR_ROOT_ID=portal` when starting Node. The server template publishes the configured id through the `inertia-root` meta element; the React client reads it for the official `createInertiaApp` id option. The Node renderer uses the official automatic SSR factory with that same id. Root ids must match `[A-Za-z][A-Za-z0-9_-]*`.

The sample gateway verifies renderer `rootId` metadata as well as production `buildId`; missing or mismatched root metadata causes CSR fallback. Existing gateway constructors retain their opt-in compatibility behavior. This is configuration consistency checking, not authentication of a renderer.

`npm run test:custom-root` starts its own Java/Node pair with `portal` and CSP enabled, checks SSR hydration and CSR after Node disconnect, navigation, deferred data, CSRF/flash, nonce enforcement, watch restart and recovery.

## Browser history and per-page SSR

`InertiaContext.encryptHistory(boolean)` applies to the current request. Encryption precedence is response override, request override, then the global `InertiaConfig.encryptHistory` default. Explicit false suppresses the optional Page field. A props callback can set the request override before preparation; late writes fail. Encryption overrides are not persisted across redirects: configure the target route as needed. `clearHistory` combines response/request/reserved-session flags with OR; redirect-delivered clear flags retain the session reservation lifecycle.

`InertiaResponse.withoutSsr()` disables the renderer for that HTML response and emits the normal CSR shell. Inertia JSON navigation never calls SSR, regardless of this flag. It does not disable props resolution or error handling.

The opt-in `--inertia.demo-history-enabled=true` example exposes `/demo-history/encrypted`, `/plain`, `/clear`, and `/csr`. It displays only a demo marker and session visit counter. `npm run test:history` owns its Java/Node processes and verifies actual encrypted History API state, response opt-out, Back/Forward reuse, key clearing followed by fresh server retrieval, and a page that opts out of SSR. These routes demonstrate history settings, not logout or authorization.

History encryption uses the client's Web Crypto and session-storage keys; production requires a secure browser context. Clearing keys affects encrypted entries and does not make plaintext history entries unreadable. Applications must re-authorize every fresh request. See the [official history encryption documentation](https://inertiajs.com/docs/v3/security/history-encryption).

## Aggregate verification and CI

From the repository root, run:

```sh
node inertia-java/scripts/verify.mjs
```

This POSIX-host entrypoint runs a clean Maven reactor build with formatting checks, a fresh `npm ci`, TypeScript checking, both frontend builds and publication, publisher contracts, an owned browser matrix, build-integrity failures, SSR faults, CSP, custom-root/history, A→B release switching and an independent production-dependency deployment rehearsal. It fails at the first failed stage. It starts its own Java/Node peers on ephemeral loopback ports; no existing app process is required. The aggregate runner does not format or commit sources. A full run rebuilds the example frontend and adds its immutable client archive. If you separately run the example while rebuilding, align its Java/Node release afterwards.

The browser matrix covers normal SSR/Feed, all-errors validation, safe error pages/session invalidation, namespaced sessions, and disconnected-renderer CSR/error recovery. Individual `npm run test:browser-matrix` requires the Maven jar and frontend build already present.

Evidence goes to a unique temporary directory by default. Set `INERTIA_VERIFY_OUTPUT=/absolute/path` to choose one. `summary.json` records source HEAD/dirty state, Node/browser channel, per-stage exit status/timing and the overall result; logs, browser diagnostics and a copy of the successful build receipt are retained. A receipt is referenced only after it was copied successfully in that run; a pre-existing file is not attested after an earlier-stage failure. This is validation evidence, not signed build provenance. Scenario flags are reset in the aggregate environment; opt-in wrappers set their own modes.

`.github/workflows/inertia-java.yml` runs the same command on Ubuntu 24.04, Java 21/Temurin and Node 22.22.2, with Playwright's paired Chromium. Action references are pinned to verified commit ids; Maven and npm caches are dependency-keyed. Push/PR triggers are limited to Java/design/workflow paths, with manual dispatch available. It uploads logs/receipt/Surefire reports even on failure and has read-only repository permission. Library publication and rollout to external hosts remain separate; the deployment stage is an isolated CI-host rehearsal.

Local validation of the command and workflow linting does not prove the remote GitHub job has passed. That remains a separate release gate until this workflow is committed and actually runs. The aggregate command is qualified on macOS/POSIX; Windows process-tree cleanup and a full Windows run remain unverified. Browser install/configuration follows the [Playwright browser documentation](https://playwright.dev/docs/browsers).

## Cancellation and worker ownership

Cancel the direct Future returned by `ResponseRenderer.render(...).toCompletableFuture()` to abort the request's render. It propagates to the current props operation and original SSR future, restores the reserved session snapshot, and prevents a late result from consuming that reservation. Finalization and cancellation have one winner; cancellation cannot undo a completion that already began its session commit. Spring MVC cancels this same handle on deadline/interruption for both normal and error-page renders.

Selected computed callbacks and async factories run on the configured props executor through interruptible `FutureTask` handles. The resolver tracks the async factory's original `CompletionStage.toCompletableFuture()` as well as its own result. Deadline/caller cancellation removes queued tasks from a supplied `ThreadPoolExecutor`, requests interruption of running work when requested, cancels late-registered stages, and stops late value serialization/Page publication. Cancellation cleanup failures are attached to the primary failure, while the remaining handles still receive cancellation. Budgets use nanoseconds so positive sub-millisecond durations are not truncated to zero.

Async factories now use the same worker budget as computed callbacks; in the default bounded worker setup they no longer execute inline on the request thread. Capture immutable request identity/data before scheduling. Thread-local/request-scoped context is not an automatic propagation contract; applications own context propagation in their chosen executor. Async stages must be owned by the request. For intentionally shared work, supply an isolated stage and define whether/how cancelling it affects its provider.

Cancellation requests are best effort. Ignoring interrupts, an uncancellable provider, synchronous template/controller work, or blocking cancellation/session hooks can outlive a deadline. A plain dependent CompletableFuture does not automatically propagate cancellation to its parent, so retain and cancel the direct operation handle. The Java adapter does not claim that cancellation rolls back JDBC/remote effects or that a client disconnect is automatically detected by blocking Servlet MVC. See the JDK [CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html) and [FutureTask](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/FutureTask.html) contracts.

## Local HTTP performance baseline

After `./mvnw verify` in `inertia-java/` and `npm ci && npm run build` in the example frontend, run `npm run benchmark` there. The command owns isolated loopback Java/Node processes and ports, measures the existing `/users` initial HTML route, and retains evidence in a fresh temporary `inertia-java-benchmark-*` directory. `INERTIA_BENCH_OUTPUT=/path` creates a unique run beneath that directory. It does not require or stop an existing application instance.

Default profiles use concurrency 1, 8 and 32 with 128 measured requests each, after 8 sequential warmup visits. The stalled-body profile uses 24 requests and 2 warmups, so its effective concurrency at 32 is 24. Override using `INERTIA_BENCH_CONCURRENCY=1,8,32`, `INERTIA_BENCH_REQUESTS=128` and `INERTIA_BENCH_SLOW_REQUESTS=32`. Values are bounded (concurrency 1–64, regular requests 1–10000, stalled requests 1–1000); high counts with low concurrency increase runtime. Requests use closed-loop workers: each waits for the full body before starting another. This measures achieved throughput at that concurrency, not open-loop arrival capacity.

The four modes are real verified Node SSR, explicit route exclusion, an absent renderer (connection refused), and a peer that sends HTTP headers/a partial body but never completes. The example accepts `--inertia.ssr-except=/users` for exclusion and opt-in `--inertia.benchmark-observations=true` for JSON logging. These settings are example configuration, not starter properties; both are disabled by default. Benchmark budgets are props 3s/response 5s, renderer 1s/16 permits, executor 8–32 workers/256 queue, request props concurrency 8. Other example settings and dependency versions are those of the built jar/receipt. Keep application environment overrides consistent between runs.

`summary.json` records source revision/dirty status, jar hash, frontend receipt, runtimes/host, props keys/JSON bytes, database queries (0, two in-memory users), HTTP sizes/statuses, SSR/CSR counts and separate latency groups, nearest-rank P50/P95/P99, explicit SSR/renderer-timeout/client-timeout ratios and transport reasons including timeout/overload. Each phase's JSON contains every request sample, resource sample and redacted observer event. Divide TIMEOUT observations by measured requests for renderer timeout proportion; client errors are counted separately. The runner fails on bad status, observation-count mismatch, missing sequential SSR, unexpected SSR in fallback profiles or a missing expected failure reason. After preflight succeeds, a failed measurement retains `success=false` and its completed phases; it never reuses a previous success receipt.

Resources are sampled with `ps` about every 200ms: max sampled RSS and the process-lifetime CPU percentage reported by the OS for Java, Node and driver (CPU percentages may exceed 100 on multiple cores). These are approximate samples, not exact peaks or interval CPU utilization; an absent process has null measurements. A short phase may have few samples. Logging overhead, JIT/GC, connection reuse, mode ordering and shared-host contention affect results. Sequential warmup does not establish steady-state production performance. At concurrency above the 16-permit SSR budget, overload fallback can reduce aggregate latency; compare the SSR ratio/reasons alongside latency. The benchmark sends no session cookies and measures independent initial visits, not same-session contention. No database, browser resource loads, deferred follow-up fetch, hydration, FCP/LCP, deployment routing, or production capacity is qualified by this command. The regular aggregate verifier remains separate from this manual measurement.


## Independent release directory

After building Maven and both frontend bundles, run `node deploy/release.mjs /absolute/release-store` from this directory. The resulting payload includes the executable example, seven library jars/POMs, client/SSR/receipt, production lock files, current versioned assets, a manifest and a release-local runtime. Install only production npm dependencies inside its `frontend/`, then use `node runtime.mjs check|ssr|java|pair` from the release directory. Payload verification precedes either service launch. Repeated publication does not overwrite existing content; corruption fails closed.

See [deployment runbook](deploy/README.md) for same-host independent supervision, readiness, graceful stop, retained shared assets, release switching and rollback. `node deploy/verify-release.mjs` rehearses the actual payload outside the checkout using production-only dependencies, real SSR/CSR browser flows and tamper rejection. It is included as a deployment stage in the aggregate verifier. Linux systemd units are supplied as target-host templates; macOS rehearsal does not qualify them or a production reverse proxy/session/storage environment. Binary/source/Javadoc artifacts are built and content-checked; public Maven publication, signatures and dependency/license review remain separate release gates.


## Example CSRF recovery

The public example retains Spring Security's cookie/header CSRF validation. Its example-only `BrowserCsrfFailureHandler` handles only CSRF failures for `POST /users` with `X-Inertia: true`: the rejected write stops before the controller, queues a namespaced one-shot `_csrf` validation error, and returns a no-store 303 to the fixed same-application `/users` path. It never uses an incoming Referer as the target, copies tokens/form values into the error, or retries the mutation. Inertia follows with a GET and keeps the form's input; the Users page displays the error and the user submits again explicitly. Ordinary requests, other paths/methods and permission denials retain 403. Failure to store the error returns 500 without pretending recovery succeeded. This filter-stage response is not a MVC render/write observation.

With `CookieCsrfTokenRepository`, a missing cookie causes a fresh token to be issued, and a header that differs from the cookie is rejected. HttpSession expiry alone does not expire this cookie token; this example does not introduce a session-backed CSRF expiry policy, login/logout integration, authentication or automatic write replay. Applications own those policies and can adapt the recovery handler to their own registered form routes/error bags. The example's handler is not installed by the reusable starter. API requests do not receive this form recovery redirect.

The Java recovery contracts exercise the actual Security filter chain, namespaced/one-shot errors, fresh cookie recovery and storage failure. Two real browser cases in every configuration of `test:browser-matrix` remove the cookie or inject a stale header, observe one rejected POST, confirm input and feedback, then explicitly submit once more and receive the normal flash. They run in SSR, all-errors, failure-page, namespaced and disconnected-renderer configurations. Configuration references: [Spring Security 6.5 CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html), [Inertia CSRF handling](https://inertiajs.com/docs/v3/security/csrf-protection).

## Page URL and shared-key presentation

`InertiaConfig.withUrlResolver(request -> "/users")` supplies a trusted synchronous callback for `Page.url`; the default is `InertiaRequest::url`. It runs once per render attempt after delivery reservation and before shared/prop suppliers, using the immutable request snapshot. Config callbacks must be fast and nonblocking. Null, blank, control-character or >8192-character results fail resolution; the reserved session delivery is restored and no prop/SSR/root work is dispatched. A safe error-page render is a separate attempt using the same config: if this callback also fails there, MVC performs its existing one-time plaintext fallback.

This changes presentation only. It does not rewrite the immutable request, routing, authorization, version-conflict location, safe-back origin policy, headers or request ID. The application must serve its chosen browser-visible canonical URL and reconstruct trusted proxy origins in its request adapter; the callback is not a proxy trust policy or a sanitizer for other response fields.

`withSharedPropKeys(false)` omits only the `sharedProps` metadata array. Shared values and built-in errors still resolve and remain in props under the normal priority/loading rules. Client shared-key classification therefore follows the metadata you choose to publish; the option does not hide data or grant authorization. The default is true. Direct `PropsResolver.resolve` retains its old four-argument default and adds a final exposure flag. Config copy methods such as `withAllErrors` preserve both settings; the original eight/nine-argument config constructors keep their prior defaults. Record components have expanded, so reflective/record-component introspection must account for the new fields.

```java
@Bean
InertiaConfig config() {
  return InertiaConfig.basic("v1", Set.of("Users/Index", "Error"))
      .withUrlResolver(request -> request.fullUrl().getRawPath())
      .withSharedPropKeys(false);
}
```

The snippet is application configuration, not a Spring property binding. The example function drops query parameters; use an application-specific rule if pagination/filter state must be retained. Core and real MVC contracts cover shared values/errors, Boot's all-errors override, safe error pages, callback failure restoration and unchanged REST/version-conflict behavior. Three additional Rust exports compare canonical URL/shared-key suppression Pages exactly.


### HTTP policy compatibility

`ProtocolPolicy.redirect(url)` prepares a 302 with Location and `Vary: X-Inertia`, including standalone helper use. `ProtocolPolicy.after` remains responsible for mutation 303 conversion and fragment redirects; existing Vary entries and business/multiple-cookie headers survive unless the protocol replaces the entire response. Apply these rules only within enabled Inertia routes. Back URLs use Java's same-origin policy.

The [compatibility matrix](compatibility/README.md#http-policy-contracts) describes 45 real Rust policy exports: 41 direct comparisons and four named Java policy differences. Run `node compatibility/verify-fixtures.mjs` from this directory to check both Page and HTTP exports (requires the Rust toolchain); ordinary Maven consumers only need stored JSON. These are pure HTTP policy contracts, separate from MVC dispatch and actual browser/network qualification.


### Once expiry and invalidation

`Prop.onceAs(key).until(duration)` sends a client reuse deadline: `(epochSecond + ttlSeconds) * 1000`, with wall-clock and Duration fractional seconds truncated independently. Zero or subsecond TTL may be expired immediately; no TTL sends null. The injected `PropsResolver` Clock supports deterministic application tests. Negative durations fail construction; excessive TTL arithmetic fails the render transaction rather than wrapping, restoring reserved delivery state.

Full visits whose official client already holds a key skip its supplier and omit its value, but retain once metadata. The official client preserves the original cached expiry across warm visits and stops sending the key at that expiry. Explicit selected partial reload and `.fresh()` bypass reuse. Run the [live TTL gate](compatibility/README.md#time-dependent-oncettl-semantics) with `node compatibility/verify-once-ttl.mjs`; `verify-fixtures.mjs` includes it. The example browser matrix exercises the exact expiry boundary in SSR and CSR modes.

Once remains a client hint; an arbitrary requester can send a loaded-key header after expiry. It must never skip route authorization or act as a server cache. Applications own business invalidation and clock synchronization.


### Library documentation artifacts

Normal `./mvnw verify` attaches `-sources.jar` and `-javadoc.jar` to all seven reusable library modules. Six modules use generated public API Javadoc; the dependency-only starter packages an English module guide under that classifier because it has no public Java API. The executable example intentionally skips these classifiers. Source archives include owned main Java/resources and exclude tests and Node/browser bundles. Project name, description, source URL and SCM connections are declared in the parent POM; child SCM connections retain the repository URL. The current version/tag are explicitly snapshot/HEAD.

Run `python3 scripts/verify-library-artifacts.py /absolute/path/report.json` after Maven to compare actual source bytes, public class/API-page inventory and all 21 archive structures. This gate is included in the aggregate verifier and requires Python3's standard library. Run `python3 scripts/verify-library-artifacts-test.py` for six isolated positive/tamper/missing-artifact checks; it copies inputs and preserves the checkout outputs. The independent release packager requires and includes all classifiers.

Javadoc fails on syntax/link/doclint errors while missing-comment warnings are excluded: artifact generation proves API-page availability, not comprehensive prose documentation. The starter's HTML is maintained module documentation, not generated API coverage. Plugins are pinned: [Maven Source 3.3.1 lifecycle goal](https://maven.apache.org/plugins-archives/maven-source-plugin-3.3.1/usage.html), [Maven Javadoc 3.7.0 jar goal](https://maven.apache.org/plugins-archives/maven-javadoc-plugin-3.7.0/jar-mojo.html).

This is artifact staging, not public publication. The repository's Rust manifest declares MIT, but no license text/attribution review for the Java distribution has been completed. No developer identity, copyright holder or licensing approval is fabricated in the POM. Release-version/tag policy, legal attribution, namespace ownership, signing and repository credentials remain publication gates; SHA256 payload integrity is not publisher authentication.


### Required SSR pages

Use `new InertiaResponse("About", props).requireSsr()` when the initial HTML document must contain a successful server render. The default still allows CSR fallback. A required HTML response fails with `SsrRequiredException` when the gateway is missing/disabled/excluded or returns fallback/invalid output; synchronous and asynchronous gateway exceptions also fail rather than mounting the intended component through CSR. The typed exception has a fixed safe message and a bounded observation reason. Required rendering uses the same gateway budgets and does not retry or start a renderer. Props/authorization failures keep their existing error semantics.

Servlet MVC maps this typed failure to 503 with private/no-store. Its one-time error-page attempt uses `withoutSsr()` so it can display the safe Error page without a second gateway call; a missing factory produces safe plaintext. Failure of the error page itself retains the existing final 500 fallback. The business page's session reservation is restored, and the error page uses a sessionless context, so reserved flash/errors remain for a later successful ordinary page. Application exception advice still precedes this resolver.

This policy applies to document rendering only: official-client JSON visits continue to receive Page JSON and do not dispatch SSR. `.requireSsr()` overrides an earlier `.withoutSsr()`; `.withoutSsr()` clears a prior requirement. Cancellation preserves cancellation classification and propagates to the owned gateway future, not a synthetic service-unavailable error. Unknown fallback strings are classified as UNKNOWN and are not included in the exception message.

The opt-in `--inertia.demo-failures=true` route `/failures/required-ssr` demonstrates successful About SSR or a safe 503 Error page when Node is disconnected. The browser matrix checks both states and recovery navigation. This is a Java adapter policy extension, not a claim of identical Rust behavior (Rust continues to allow CSR fallback). Applications own whether required pages should affect readiness/routing; Java liveness remains independent of renderer health.


### Independent Maven consumer rehearsal

After the normal Maven/frontend builds, run `python3 scripts/verify-maven-consumer.py`. It packages the current immutable release, copies its Maven subtree into a temporary fixture repository, adds isolated untimestamped SNAPSHOT metadata/checksums, and creates a new consumer outside the checkout. The consumer uses an independent POM (no reactor parent), empty user settings and a private empty Maven cache; initial public dependency/plugin downloads can take longer than warm builds. It requires Python3, Node, Java21 and Maven-wrapper network access. The command does not install into your normal Maven cache or publish remotely. For repeated rehearsals, `INERTIA_CONSUMER_DEPENDENCY_CACHE=/absolute/previous/cache` optionally copies third-party/plugin cache entries; all `io.inertia` coordinates are explicitly excluded and must resolve again from the new fixture repository. The summary records that seed when used.

The consumer resolves starter/testing and all seven runtime library jars, compares each resolved jar with the packaged bytes and rejects checkout paths in its runtime classpath. It starts a real loopback Servlet MVC app and checks HTML/JSON, protocol redirects, required-SSR 503 and one-shot session flash. Source/Javadoc classifiers are resolved through Maven rather than copied into the cache. The command also removes core from its isolated repository/cache to require build refusal, then restores it and rebuilds. Unique output retains the consumer POM/source, logs, private caches and final success/failure summary. Each owned process has a bounded timeout.

The generated fixture metadata/checksums qualify local artifact consumption, not a public repository release format, cryptographic signature or namespace ownership. This example deliberately uses CSR with no Node gateway; actual React/Node SSR is exercised by the separate deployment/browser gates. The consumer rehearsal is a separate release command, so routine aggregate verification retains its current scope.


### Application exception pages

Application exception handlers retain precedence over the library error-page resolver. A synchronous, unwrapped `InertiaResponse` from an ordinary `@ControllerAdvice` or a controller-local `@ExceptionHandler` uses the page adapter. For an existing typed Inertia request, the adapter aborts the original context and creates one fresh sessionless error context before resolving Inertia arguments or rendering the advice's return value. This also handles a props failure after the original context has already closed. Inherited generic handlers resolve their return type against the concrete advice class before choosing this context. Advice can set its own safe props via the new context; failed request shares/pending effects and reserved session deliveries are not copied into it. Stored flash/errors remain available to the next successful business page.

The application chooses the error component, status, cache policy and SSR policy. Use registered components and normal `@ControllerAdvice` without `@ResponseBody` for typed Page returns; ordinary `ResponseEntity`/REST advice keeps Spring's native behavior and does not gain the Inertia wire protocol. Scope advice appropriately to page controllers. Typed Page advice uses this sessionless error context; typed `HttpOutcome` advice uses the separate session-backed redirect contract described below.

If the advice's Page fails, Spring falls through to the existing one-time safe library error page, with no recursive advice loop. Contracts exercise direct controller and asynchronous prop exceptions, shared advice context, controller-local advice without context arguments, HTML/JSON, plain ResponseEntity handling, original flash recovery, a failed advice Page and unrelated REST advice.


### Application exception redirects

A synchronous, unwrapped `HttpOutcome` from application `@ExceptionHandler` uses a fresh context before resolving Inertia arguments or handling the return value. This applies to local, global and inherited generic handlers on an existing typed Inertia request. The original context is aborted: its reserved delivery is restored and its pending effects are discarded. Advice can queue its own flash/errors and return `back()` or another outcome; protocol policy still runs before writing, including PUT/PATCH/DELETE 302→303 conversion.

Unlike exception pages, outcome advice retains the request's original `HttpSessionStore` and configured namespace, so its own effects can be merged for the next page without consuming existing delivery. It does not call `getSession()` to replace an invalidated session or rebind removed namespace state. An invalidated/detached original store fails through the normal safe error resolver. Session state is committed before response writing; servlet/network failure cannot guarantee delivery or roll back a completed merge. Ordinary REST advice remains outside this path.


### Custom codec and adapter overrides

A user `PageCodec` bean is shared by the default props resolver, renderer, MVC request contexts, typed exception advice and library error-page contexts. Register its serialization rules before constructing the codec; `PageCodec` copies the supplied `ObjectMapper`. This private codec does not replace Spring's REST message converters. Multiple codec beans need one `@Primary`; ambiguous candidates fail startup instead of choosing a serializer.

Custom `PropsResolver`, `ResponseRenderer` and `InertiaMvcConfigurer` beans replace the matching defaults by type. Applications replacing the graph own consistent codec/config/budget wiring. For standalone or custom MVC integration with a configured codec, use `new InertiaMvcConfigurer(config, renderer, deadline, errorPage, namespace, codec)`. Existing constructors remain available and use their original default codec behavior. Properties used only by a replaced component do not reconfigure an application-owned instance.

### Opt-in local identity demonstration

The default sample keeps public routes. Enable the separate Spring Security identity demo with `--inertia.demo-auth=true` and set `INERTIA_DEMO_PASSWORD` (at least 12 characters) before starting the example jar. The local username is `demo`; no default password is supplied. `/login` renders an Inertia form, `/account` requires authentication, and POST `/logout` signs out. Existing `/users`, `/feed` and assets remain public. This in-memory account is an integration example; applications supply their own identity provider and deployment/session policy.

Spring Security performs credential checking, BCrypt hashing, login/logout processing and CSRF validation. Login submits multipart form data because the standard authentication filter reads servlet form parameters. Targets are fixed `/account` and `/login`; request caching is disabled. Successful authentication uses the `newSession` fixation policy, discarding anonymous application state, and queues `clearHistory` in the new configured namespace. Account pages use encrypted history; login pages clear history and all identity pages are private/no-store. Unauthorized Inertia navigation receives 409 + X-Inertia-Location to force a fresh login document; ordinary visits redirect.

The existing cookie/header CSRF handler issues a fresh token after authentication/logout. A rejected Inertia POST to `/login` or `/logout` redirects to a fixed review page with a safe `_csrf` message. It never replays the rejected operation; logout with a stale token preserves the current authenticated session until the user explicitly submits again. Failed credentials produce a generic error without reflecting passwords. Logout uses Spring's session invalidation/context clearing/CSRF cleanup. Cookie transport settings, HTTPS, real identity storage, multi-node sessions and production access policy remain application deployment responsibilities.

References: [Spring Security CSRF and SPA integration](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [session fixation protection](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html), [logout handling](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html). The example uses the pinned dependency versions, not a claim of the latest release.


The opt-in identity demo emits a same-origin local-storage invalidation hint only after a successful logout visit. Other open demo tabs replace their document with `/login`; login's `clearHistory` uses the official client to clear protected history. A `pageshow` check also handles a restored document whose logout revision changed. The hint grants no permissions and does not replace server authorization. Tabs with local storage disabled cannot receive this notification; they still encounter server authorization on their next protected request. Public mode does not install this listener.

The browser matrix includes an `auth-expiry` scenario with `server.servlet.session.timeout=1m`. It waits 65 real seconds without requests from that session, verifies the old identity is gone, checks a protected official Link visit is denied, and signs in again. This is actual servlet idle expiry, independent of the browser clock used by the separate once-TTL tests.


### Dependency and license declaration inventory

After Maven/frontend builds, run `python3 scripts/dependency-inventory.py [new-or-empty-output-directory]`. The aggregate verifier runs it as a dependency-inventory stage. It uses pinned CycloneDX Maven plugin 2.9.1 and the installed npm CLI's `sbom` command, not a custom dependency resolver. Maven covers the reactor graph including test/provided dependencies; npm emits both the all-platform lock graph and the production lock graph. Environment `NODE_ENV` is explicitly reset for inventory so an inherited production mode cannot hide development dependencies.

The command validates graph references/duplicate identities and matches the full npm graph against every locked dependency. It also inspects every actual `BOOT-INF/lib/*.jar`: third-party hashes must match graph components, owned jars must match reactor outputs, and archive-only components are explicitly reported (Boot injects jarmode tools outside the normal dependency graph). LICENSE/NOTICE/COPYING/copyright files from runtime jars and installed npm packages are copied unchanged under content hashes with original source paths. Missing platform-specific optional packages are represented by lock metadata, without pretending their license files were observed on this host.

Outputs include original JSON SBOMs, `inventory.json`, `summary.json`, a review table and `license-texts/`. `success=true` means collection/integrity checks passed; `publicationQualified` remains false. Missing declarations, reciprocal/multiple terms and missing observed files require review. Maven plugin/tool/JDK/OS/browser dependencies and bundle-level attribution are outside this graph. The command does not infer legal permission, choose a license branch, scan vulnerabilities, rewrite attribution or publish artifacts. Source HEAD/dirty state, POM/lock hashes and executable jar hash are recorded.

The frontend now declares private version `0.1.0-snapshot.0` so npm can generate a valid package identity; dependency versions and resolved integrity values remain unchanged. See [npm SBOM documentation](https://docs.npmjs.com/cli/commands/npm-sbom/) and [CycloneDX Maven plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin).


### Advanced props and named forms

Visit `/advanced` from the About page. The sample sends a nested profile delta with `deepMerge` and `matchOn("members.id")`; repeated reloads retain untouched fields, update matching members and avoid duplicates. The reset action replaces the profile and suppresses merge metadata. An except-only reload omits the expensive query/profile while `always` status survives its explicit exclusion.

This adapter preserves the repository's Rust selection rules: an except-only partial visit selects every non-excluded prop, including optional callbacks. The demo's optional query therefore runs during the except visit and again when explicitly requested with `only`. It remains absent on a full visit. Applications that want to skip it in an except visit must include it in `except`.

Two demo forms share the field name `name` and send distinct `profile`/`team` error bags. Each form displays its own errors; a successful submission clears that form's errors, delivers one flash message and leaves the other form's local errors intact. These forms validate demo input without persisting business data. Browser contracts check request headers, response metadata and actual rendered state in SSR and CSR modes.
