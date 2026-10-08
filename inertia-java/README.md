# Inertia Java

Framework-independent Inertia v3 server adapter with Spring MVC integration, a pooled Node SSR gateway, and a React example. Implementation is in progress; see the [implementation ledger](../docs/inertia-java/05-implementation-status.md) for verified capabilities and remaining work.

## Observation SPI

Core provides an opt-in `InertiaObserver` for props, render, SSR success/fallback, and session begin/complete/abort/merge. Pass it to both the `PropsResolver` and `ResponseRenderer` constructors; standalone redirect contexts accept it in their constructor. Existing constructors use a no-op observer. `LoggingInertiaObserver` emits JSON through `System.Logger`; `InertiaObserver.combine` composes observers. Observers run inline and must be fast and nonblocking. Runtime exceptions from an observer are isolated from the business result; fatal JVM errors are not swallowed.

Events correlate with a server-generated request ID and contain operation/outcome/reason enums, elapsed nanoseconds, response status/kind, component and a configured endpoint ID. They do not contain URL, headers, props, flash, exception text, or renderer response bodies. Treat component and endpoint IDs as trusted application configuration. Do not use request IDs or component names as metric tags. Render success describes producing an outcome, not delivery to a browser. The remaining operation enum values reserve adapter integration points; automatic Boot registration, Micrometer metrics, MVC response/version-conflict observations, and detailed HTTP transport classification remain pending.

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

Production SSR and CSR are verified for both the default `app` root and a custom `portal` root. Structured observations, deployment gates and distributed sessions remain open. The Feed example exercises append/prepend/reset and once reuse: once props used across different pages must be declared on both pages, such as shared props; a page-local once prop is not an application-wide cache. The demo routes are public, with Spring Security cookie/header CSRF protection on unsafe requests. Authentication policy and production deployment remain application responsibilities. Session delivery promises atomic reservation on one node, not exactly-once delivery to a browser. Future cancellation does not guarantee JDBC or external work has stopped.

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

This POSIX-host entrypoint runs a clean Maven reactor build with formatting checks, a fresh `npm ci`, TypeScript checking, both frontend builds and publication, publisher contracts, an owned browser matrix, build-integrity failures, SSR faults, CSP, custom-root/history and A→B release switching. It fails at the first failed stage. It starts its own Java/Node peers on ephemeral loopback ports; no existing app process is required. The aggregate runner does not format or commit sources. A full run rebuilds the example frontend and adds its immutable client archive. If you separately run the example while rebuilding, align its Java/Node release afterwards.

The browser matrix covers normal SSR/Feed, all-errors validation, safe error pages/session invalidation, namespaced sessions, and disconnected-renderer CSR/error recovery. Individual `npm run test:browser-matrix` requires the Maven jar and frontend build already present.

Evidence goes to a unique temporary directory by default. Set `INERTIA_VERIFY_OUTPUT=/absolute/path` to choose one. `summary.json` records source HEAD/dirty state, Node/browser channel, per-stage exit status/timing and the overall result; logs, browser diagnostics and a copy of the successful build receipt are retained. A receipt is referenced only after it was copied successfully in that run; a pre-existing file is not attested after an earlier-stage failure. This is validation evidence, not signed build provenance. Scenario flags are reset in the aggregate environment; opt-in wrappers set their own modes.

`.github/workflows/inertia-java.yml` runs the same command on Ubuntu 24.04, Java 21/Temurin and Node 22.22.2, with Playwright's paired Chromium. Action references are pinned to verified commit ids; Maven and npm caches are dependency-keyed. Push/PR triggers are limited to Java/design/workflow paths, with manual dispatch available. It uploads logs/receipt/Surefire reports even on failure and has read-only repository permission. No library publishing or deployment happens in this workflow.

Local validation of the command and workflow linting does not prove the remote GitHub job has passed. That remains a separate release gate until this workflow is committed and actually runs. The aggregate command is qualified on macOS/POSIX; Windows process-tree cleanup and a full Windows run remain unverified. Browser install/configuration follows the [Playwright browser documentation](https://playwright.dev/docs/browsers).

## Cancellation and worker ownership

Cancel the direct Future returned by `ResponseRenderer.render(...).toCompletableFuture()` to abort the request's render. It propagates to the current props operation and original SSR future, restores the reserved session snapshot, and prevents a late result from consuming that reservation. Finalization and cancellation have one winner; cancellation cannot undo a completion that already began its session commit. Spring MVC cancels this same handle on deadline/interruption for both normal and error-page renders.

Selected computed callbacks and async factories run on the configured props executor through interruptible `FutureTask` handles. The resolver tracks the async factory's original `CompletionStage.toCompletableFuture()` as well as its own result. Deadline/caller cancellation removes queued tasks from a supplied `ThreadPoolExecutor`, requests interruption of running work when requested, cancels late-registered stages, and stops late value serialization/Page publication. Cancellation cleanup failures are attached to the primary failure, while the remaining handles still receive cancellation. Budgets use nanoseconds so positive sub-millisecond durations are not truncated to zero.

Async factories now use the same worker budget as computed callbacks; in the default bounded worker setup they no longer execute inline on the request thread. Capture immutable request identity/data before scheduling. Thread-local/request-scoped context is not an automatic propagation contract; applications own context propagation in their chosen executor. Async stages must be owned by the request. For intentionally shared work, supply an isolated stage and define whether/how cancelling it affects its provider.

Cancellation requests are best effort. Ignoring interrupts, an uncancellable provider, synchronous template/controller work, or blocking cancellation/session hooks can outlive a deadline. A plain dependent CompletableFuture does not automatically propagate cancellation to its parent, so retain and cancel the direct operation handle. The Java adapter does not claim that cancellation rolls back JDBC/remote effects or that a client disconnect is automatically detected by blocking Servlet MVC. See the JDK [CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html) and [FutureTask](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/FutureTask.html) contracts.
