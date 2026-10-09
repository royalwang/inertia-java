# Java API guide

This guide describes the implemented `0.1.0-SNAPSHOT` API with Java 21, Spring Boot 3.5.7 and Jackson 2. It is a source/local-distribution version, not a claim that these coordinates have been published to Maven Central. The React/Node example locks its own frontend versions. See the repository's compatibility matrix for verified client behavior and deliberate Rust differences.

## Choose dependencies

Use `io.inertia:inertia-core` for an application-owned HTTP adapter. Use `io.inertia:inertia-spring-boot-starter` for Servlet Spring MVC; it supplies MVC and auto-configuration dependencies. Add `io.inertia:inertia-testing` in test scope for Page assertions. All use version `0.1.0-SNAPSHOT` in this checkout. The starter transitively includes the optional SSR/Vite integration libraries, but does not configure or launch a JavaScript renderer.

```xml
<dependency>
  <groupId>io.inertia</groupId>
  <artifactId>inertia-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Import Spring Boot's 3.5.7 dependency BOM in the consuming project. Build/install the reactor locally (`./mvnw install` from `inertia-java`) or consume the packaged Maven subtree from your configured internal repository. Repository hosting, snapshot metadata, signing and namespace authorization are separate release tasks. Do not point a production consumer at an unreviewed local fixture repository.

The complete [CoreApiExample.java](examples/CoreApiExample.java) and [SpringApiExample.java](examples/SpringApiExample.java) compile against library artifacts. `python3 scripts/verify-maven-consumer.py` from `inertia-java` copies these files unchanged to an external consumer, resolves the packaged jars through a private Maven repository/cache, and verifies their behavior. It requires the normal Maven/frontend builds first. These small examples demonstrate protocol integration without a browser application; use `examples/spring-react` for assets, security, hydration and actual Node SSR.

## Core integration and ownership

`InertiaRequest` snapshots method, absolute URI and headers. Header names are normalized; `url()` retains the raw path/query. Generate request IDs on the server. Proxy reconstruction belongs to your HTTP adapter. Never treat an Inertia header as authentication.

Create immutable application configuration with `InertiaConfig.basic(version, components)` for the minimal root template, or the full constructor for a custom `RootView`, `SsrGateway`, shared props and presentation settings. The component set is an allowlist; rendering an unregistered component fails. The version supplier must represent the deployed client/SSR/assets build. `withAllErrors`, `withSharedPropKeys` and `withUrlResolver` return new configuration values. The URL resolver changes Page presentation, not routing or redirect policy.

Reuse `PageCodec`, `PropsResolver`, `ResponseRenderer` and application configuration. Keep `InertiaContext` and `InertiaResponse` request-owned and single-use. Supply a bounded executor to `PropsResolver`; the application closes it. The Boot starter owns its default executor and lifecycle. Capture authorized, immutable data before scheduling callbacks: security context, servlet request and transaction ThreadLocals are not propagated automatically.

An independent adapter follows this sequence:

1. Construct the request snapshot and call `ProtocolPolicy.before(request, currentVersion)` before business/controller/query work. If present, write that outcome immediately; do not reserve session delivery.
2. For a Page, create a fresh context with the request's session store (or `null` for sessionless rendering). Call `renderer.render(context, response)` and await it within the HTTP transport budget. The renderer resolves props, renders HTML/JSON, applies Page policy and completes delivery before returning an `HttpOutcome`.
3. For a controller `HttpOutcome` such as a redirect, call `context.commitRedirect()` first, then `ProtocolPolicy.after(request, outcome)` before writing bytes. Do not call `commitRedirect()` after rendering a Page. Spring MVC performs these steps for typed handlers.
4. On transport timeout, cancel owned pending work and call `context.abort()` to restore a reservation that has not committed. Set limits on underlying database/HTTP work too. Rendering success does not prove delivery to a browser; a completed session transaction cannot be rolled back after a network write failure.

`HttpOutcome` contains status, immutable multivalue headers and a text body. It is not a binary streaming abstraction. Keep downloads, uploads, REST and SSE in the host framework. `ProtocolPolicy.redirect` creates a 302; the post-policy converts Inertia PUT/PATCH/DELETE to 303 and handles fragment/prefetch rules. `context.location` creates an external/full-document navigation outcome. Redirect URLs are application-owned; `context.back()` limits Referer navigation to the current origin and otherwise uses `/`.

## Page and context calls

| API | Purpose and lifetime |
| --- | --- |
| `context.render(component, props)` | Create a request-owned Page response; no rendering occurs yet |
| `context.share(key, value)` | Define shared props before resolution starts |
| `context.flash(key, value)` | Queue effects; duplicate keys within the same context fail |
| `context.withErrors(...)` | Queue default/named/multimessage errors before rendering |
| `context.clearHistory()` / `preserveFragment()` | Queue client instructions; redirects can carry them to the next page |
| `context.encryptHistory(boolean)` | Override this request's Page setting; does not persist through redirect |
| `response.status(int)` / `withHeader(name, value)` | Set business status/headers; protocol-owned headers are rejected |
| `response.withViewData(name, value)` | Root-template data; not automatically Page props |
| `response.flash(key, value)` | Current Page flash, rather than a cross-request queue |
| `response.encryptHistory(boolean)` / `preserveBigIntegers(boolean)` | Override application/Page presentation defaults |
| `response.clearHistory(boolean)` | Set the current response's client instruction |
| `response.withoutSsr()` | Render a CSR shell for HTML; JSON visits are unaffected |
| `response.requireSsr()` | Require SSR for HTML; gateway fallback becomes a safe 503, while JSON remains available |

`requireSsr` and `withoutSsr` are ordered builder choices: the last call wins. Response history settings override context settings, which override config. Shared prop precedence is internal `errors`, config shared, request shares, then Page props. Exact keys keep the first declaration position and the last definition; only the winning supplier executes. `errors` can be overridden, so applications relying on built-in validation should reserve that key. Parent/child path conflicts fail before execution; ordinary object deep merging is a separate client instruction.

## Prop selection and asynchronous work

```java
Props props = Props.builder()
    .put("title", "Users")
    .put("rows", Prop.lazy(() -> java.util.List.of("Ada", "Linus")))
    .put("details", Prop.optional(() -> java.util.Map.of("enabled", true)))
    .put("statistics", Prop.defer(() -> 42).group("statistics"))
    .put("status", Prop.always("ready"))
    .build();
```

| Factory | Full visit | Matching partial visit |
| --- | --- | --- |
| Plain value / `Prop.value` | Included | Included if selected |
| `Prop.lazy(Task)` | Executes | Executes if selected |
| `Prop.async(Supplier<CompletionStage<?>>)` | Schedules its factory | Schedules if selected |
| `Prop.optional(Task)` | Omitted without callback | Executes if selected |
| `Prop.defer(Task)` | Omitted; deferred-group metadata | Executes if selected |
| `Prop.always(value)` | Included | Included even when explicitly excluded |

A partial request must name the same component. Dot-path selection matches ancestors and descendants; except excludes its path and descendants. An except-only partial selects all remaining props, including optional callbacks, following this repository's Rust implementation. To avoid that query, explicitly include it in `except`. Nested resolved values follow their parent's selection. Selection does not replace authorization: authorize before defining data, and do not rely on an optional/deferred flag to protect secrets.

The resolver runs sibling work concurrently within one total deadline and a per-request concurrency cap. Executor rejection, overload, deadline and unrescued failures fail the operation; owned sibling work is cancelled on fatal failure. Successful values/metadata retain declaration order, but parallel failure choice is not ordered. An async supplier should return a request-owned stage, not a shared future whose cancellation would affect other requests. Underlying providers must implement their own timeout/cancellation.

`.rescue()` is available only on deferred props. A rescued failure omits that field with `rescuedProps` metadata; it does not turn authorization errors into a generic SSR fallback. Use it only for explicitly optional business information.

## Merge, once, scroll and large integers

These APIs emit protocol metadata for the official client; the server does not maintain a browser-side merged collection.

| Definition | Behavior |
| --- | --- |
| `Prop.value(rows).merge()` | Append root collection on matching partial visits |
| `Prop.value(rows).prepend()` | Prepend root collection |
| `Prop.value(profile).deepMerge().matchOn("members.id")` | Recursive object merge; match nested collection members by ID |
| `.appendAt("data")` / `.prependAt("data")` | Merge at an inner path; choose these instead of combining incompatible merge modes |
| `.matchOn("id")` | Match items by a relative path; requires merge options first |
| `Prop.lazy(task).onceAs("catalog").until(Duration.ofMinutes(5))` | Announce a client reuse key/TTL |
| `.fresh()` | Force a fresh once value even when the client has its key |
| `Prop.scrollWith(task)` / `Prop.scroll(page)` | Emit pagination metadata and direction-specific merge instructions |

Paths are relative to the prop. Composing another merge mode can replace previous options; do not assume every modifier accumulates. `X-Inertia-Reset` suppresses merge metadata for the reset path so the client replaces state. A `ScrollPage` carries data, page name, previous/next/current page and wrapper (`data` by default). Page numbers and cursors are application data; the library does not query a database.

Once is client reuse, not a server cache or authorization policy. Declare a shared once prop on every page that needs it; a Page-local once value does not become global. TTL is nonnegative and uses server Clock metadata; request/response differences are documented in the compatibility matrix. See Feed and Advanced in the runnable example for real client append/prepend/reset, nested matching and repeat visits.

Enable `preserveBigIntegers` on config or a response when IDs exceed JavaScript's safe integer range. The codec emits the locked client's bigint representation; the actual Node/client entries must use the matching revive path. Do not convert identifiers through a JavaScript Number before revival. `PageCodec.htmlJson` protects the Page script boundary; raw user strings must still be escaped by custom root templates.

## Sessions, validation and MVC exceptions

`MemorySessionStore` is a single-node implementation. Associate it with one user's storage domain; never put all users in one global store. `HttpSessionStore` supplies namespaced servlet integration, and Boot registers the session mutex listener. Standalone MVC integrations must also register `HttpSessionMutexListener`. Namespaces are 1–64 safe identifier characters and must match throughout a request/advice chain.

The SPI atomically begins a delivery reservation, completes or aborts each token at most once, and merges newly queued effects. On render failure, reserved delivery is restored while failed-request pending effects are discarded. Storage errors are reported; there is no blind retry of unknown outcomes. It is not a distributed/exactly-once network guarantee. A sessionless Page may render; committing cross-redirect flash/errors without a session fails.

`ValidationErrors` retains ordered messages. `ErrorBags` combines default/named bags. If default errors exist they take precedence and are scoped under the requested `X-Inertia-Error-Bag`; otherwise named bags are delivered by name. Default presentation uses the first message; `withAllErrors(true)` preserves every message in the Page. `ValidationBridge.errors(BindingResult)` and optional `JakartaValidationBridge.errors(violations)` copy messages/paths without rejected values. Add a Jakarta validation provider separately if using it. Validation redirects to a Page; ordinary REST validation retains Spring semantics.

Controllers use ordinary `@Controller` with synchronous, unwrapped `InertiaResponse` or `HttpOutcome` return types. Inject `InertiaContext`/`InertiaRequest` through handler arguments. `@ResponseBody`, `@RestController`, `ResponseEntity<Page>`, asynchronous Page wrappers and incompatible generic wrappers are rejected for typed Page handlers at startup. Ordinary REST/async transfer handlers remain host-owned.

Application `@ExceptionHandler` has precedence. Typed Page advice starts a fresh sessionless error context and leaves original delivery for a later successful page. Typed outcome advice starts a fresh context retaining the original session store/namespace for its own redirect effects; it does not replace an invalidated session. Local, global and inherited generic typed handlers are supported. `InertiaErrorPage` provides the final safe Page when application handling fails; no exception text should be included in its props, and failure of that Page does not recursively render it again.

## Boot configuration and replacement beans

Define an application `InertiaConfig` bean. The starter does not derive component registration, templates, assets or SSR endpoint from generic properties. Its validated execution properties are:

| Property (`inertia.` prefix) | Default |
| --- | --- |
| `props-timeout` / `response-timeout` | `3s` / `5s` |
| `props-concurrency` | `8` |
| `executor-core-size` / `executor-max-size` | `8` / `32` |
| `executor-queue-capacity` | `256` |
| `session-namespace` | `default` |
| `all-errors` | Unset; use the config value |

Timeouts/limits must be positive, max threads must cover core threads, and response timeout must cover props timeout. Supply an `ExecutorService` bean named `inertiaPropsExecutor` to replace the executor. A user `PageCodec` is shared across default props/render/MVC/advice; it copies its supplied ObjectMapper and does not alter REST converters. Ambiguous beans need `@Primary`.

User `PropsResolver`, `ResponseRenderer` and `InertiaMvcConfigurer` beans replace their defaults. The application then owns consistent wiring. A custom MVC configurer can use `(config, renderer, deadline, errorPage, namespace, codec)` to preserve the configured codec. Old constructors remain available with their default-codec behavior. See the README for observer/Micrometer wiring and current event semantics.

## SSR, Vite and root templates

Reuse one `HttpSsrGateway`; configure trusted endpoint, connect/render timeouts, maximum response bytes, concurrency, codec, build verification and root ID. It POSTs the Page JSON itself without credentials and does not follow redirects or retry rendering. A `SsrEndpointResolver` selects the production `/render` endpoint or opt-in trusted development hot endpoint and applies configured exclusions. A missing bundle, null/malformed/oversize renderer response or transport failure produces a classified fallback; props failures remain failures before this stage. `SsrHealthMonitor` is an optional application-owned, closeable probe; health is not proof a particular Page can render.

`ViteBuild` verifies one client/SSR/build receipt. `ViteAssets` creates tags from the production manifest or explicitly enabled development hot file. The application must mount the immutable asset directory and align version, Node bundle, entry, root ID and build ID. Do not read a hot file in production. `RootView.View` exposes Page, trusted SSR head/body, template data and CSP nonce. Insert the supplied body once; it already contains the expected Page script/root in the locked rendering contract. Escape other template data. The minimal root has no application client asset tags, which is why the standalone API examples are not a complete browser app.

See the distribution's README and RUNBOOK for full React/Node startup, immutable releases, process supervision, resource retention and failure exercises. Authentication, CSRF, HTTPS/cookies, proxy trust and production storage policy remain application responsibilities; the starter does not replace a security chain.

## Assertions and verification limits

`AssertablePage.fromBody(body)` accepts raw Page JSON or the standard HTML Page script, then supports `.component("Home")`, `.equals("/props/message", "Hello")` and `.missing("/props/details")`. Paths are JSON Pointers. `.data()` returns a copy. This helper does not verify HTTP status/headers, arbitrary custom templates, browser hydration or delivery to a client; assert those at their respective boundaries.

Generated source/Javadoc classifiers supply the symbol index for all runtime modules. This guide explains usage/ownership, while the compatibility matrix and implementation ledger record verified scenarios and remaining qualifications. Local verification and dependency declarations do not grant publication or licensing authorization.
