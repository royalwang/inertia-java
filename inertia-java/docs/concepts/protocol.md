---
title: "The Inertia protocol"
description: "Explain HTML/JSON, version409, mutation303, Vary, headers and deliberate Rust differences."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ProtocolPolicy.java
  - inertia-java/compatibility/README.md
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# The Inertia protocol

The protocol lets the same Java route serve a browser document and subsequent Page updates. Its headers control transport and loading, not authentication.

## HTML and JSON visits

An ordinary initial GET receives a document from the application's `RootView`. It includes client assets, a root element and serialized Page data. When SSR succeeds, the root already contains rendered content.

An official client visit sends `X-Inertia: true`. The response is Page JSON and includes `X-Inertia: true`. A Page carries a registered component name, props, URL and version, plus optional metadata such as flash, deferred groups, merge paths and presentation flags.

`Vary: X-Inertia` prevents caches from confusing those representations. Authenticated/user-specific responses should also follow the application's private/no-store policy. A CDN configuration which ignores those distinctions can leak or mis-serve content despite correct Java serialization.

## Request controls

| Header family | Meaning |
| --- | --- |
| `X-Inertia-Version` | Client build identity checked against the current version |
| `X-Inertia-Partial-Component` | Component whose partial selection is requested |
| `X-Inertia-Partial-Data` / `X-Inertia-Partial-Except` | Include/exclude prop paths for a matching component |
| `X-Inertia-Error-Bag` | Scope default validation delivery to a named form |
| `X-Inertia-Reset` | Suppress merge instructions for replacement paths |
| `X-Inertia-Except-Once-Props` | Indicate once keys already held by the client |

A partial request naming another component does not apply that component's selection. Dot-path selection includes related ancestors/descendants; except removes its path and descendants. `always` can survive explicit exclusion. The [API guide](../api-guide.md#prop-selection-and-asynchronous-work) documents the important except-only optional-query rule.

## Redirect and refresh semantics

Return `ProtocolPolicy.redirect(target)` for an application-controlled redirect. It creates 302; the post-policy converts Inertia PUT/PATCH/DELETE to 303. Use `context.location(target)` for full-document/external navigation, which uses the Inertia location response for Inertia requests.

A stale version on an applicable GET produces a refresh outcome before the controller executes. The client can then load the new document/assets. Fragment, prefetch and empty-response rules are centralized in `ProtocolPolicy`; independent adapters should call it instead of reproducing a subset in controllers.

`context.back()` checks origin and falls back to `/` for an unsafe Referer. These helpers do not make arbitrary application-supplied redirect targets safe; select targets deliberately.

## Security and interoperability

Clients can forge every request header above. Authorize the request before exposing data, including optional/deferred queries. CSRF protection, cookie policy, trusted proxy reconstruction and identity storage remain host-application responsibilities.

The [compatibility matrix](https://github.com/royalwang/inertia-java/blob/main/inertia-java/compatibility/README.md#http-policy-contracts) describes exact Rust-derived contracts and explicit Java policy differences. Use actual HTTP/browser checks for header serialization and client behavior; pure fixture parity covers a narrower boundary.
