---
title: "SSR, hydration and CSR"
description: "Describe first HTML versus JSON visits and actual client mount/hydrate behavior."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/src/app.tsx
  - inertia-java/examples/spring-react/frontend/src/ssr.tsx
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# SSR, hydration and CSR

Server-side rendering creates initial page HTML on Node. Hydration connects React to that existing HTML. Client-side rendering mounts React into an empty shell. These are separate outcomes and need separate evidence.

## Rendering choices

| Request / policy | Node called? | Result |
| --- | --- | --- |
| Ordinary HTML, default, healthy renderer | Yes | Root view with rendered body and Page script |
| Ordinary HTML, default, renderer unavailable | Attempt or classified unavailable | CSR shell with Page data |
| Ordinary HTML, `withoutSsr()` | No | CSR shell |
| Ordinary HTML, `requireSsr()`, renderer failure | Attempt or classified unavailable | Safe 503 |
| Inertia JSON visit under either Page policy | No | Page JSON |

`requireSsr()` and `withoutSsr()` are ordered choices: the last builder call wins. Required SSR affects HTML rendering, not a later JSON visit.

## Application wiring

The full example owns one Java component allowlist and one frontend registry used by both entries. It aligns root ID, client assets, SSR bundle and build version. Java resolves props first, then sends the Page to the trusted SSR endpoint. The gateway does not forward browser authentication headers or cookies.

`RootView.View` supplies trusted SSR head/body and template data. In the locked rendering contract, the supplied body already contains the expected root/Page script: insert it once. Escape other template values and use `PageCodec.htmlJson` for a custom Page script boundary.

`InertiaConfig.basic(...)` has a minimal root with no application asset tags or Node gateway. It is useful for a protocol example, but cannot by itself hydrate a React application. Use the [complete example](../getting-started/quick-start.md) for browser wiring.

## Browser entry and Node entry

The example's browser entry uses `hydrateRoot` when the root has content and `createRoot` otherwise. Both entries resolve names through the same explicit registry. The Node entry validates the decoded Page envelope, rejects inherited/unregistered names and verifies the production bundle/build identity.

The HTTP gateway bounds connect/render time, response bytes and concurrent work. It does not retry rendering or follow redirects. A malformed/empty result, root/build mismatch or transport failure becomes a classified SSR fallback. Prop failures occur earlier and remain failures; they are not disguised as a healthy CSR fallback.

## Verify the distinction

Disable JavaScript and inspect initial HTML to prove SSR content. Enable JavaScript and use the form/navigation to prove hydration. Stop the renderer you own, reload and verify CSR mounting separately. A browser screenshot with JavaScript enabled can look correct in both modes, so it cannot establish SSR alone.

SSR health is an optional cached peer probe. An UP state does not prove a particular component can render, nor should renderer failure automatically make Java liveness fail. Applications choose whether SSR is required for readiness.

Continue with [build versions](versioning.md) to understand why client assets and Node outputs must be released together.
