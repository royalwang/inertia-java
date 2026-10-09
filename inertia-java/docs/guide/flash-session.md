---
title: "Flash and session delivery"
description: "Queue flash, reserve one delivery, restore failures and namespace applications."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
---

# Flash and session delivery

Flash is short-lived Page feedback. Cross-request delivery uses a session transaction rather than an application-global mutable map.

## Choose the flash scope

`context.flash(key, value)` queues an effect. A typed redirect commits it into the request's session domain for the next Page. Reusing a key in that same context fails, helping expose accidental double writes. `response.flash(key, value)` instead supplies flash on the current Page; it is not a cross-redirect queue.

Read flash through the official client's Page flash field. It is separate from ordinary props. In the example, a successful form queues `toast`; the redirected page displays it, and a fresh reload does not deliver it again.

Validation errors, clear-history and fragment instructions share the context's delivery lifecycle. A request-local history-encryption override does not persist through a redirect.

## Delivery transaction

| Stage | Session behavior |
| --- | --- |
| Start rendering a Page | Reserve stored delivery under a token |
| Successfully finalize a Page | Complete the token once |
| Fail/cancel before commit | Restore reserved stored delivery; discard failed-request pending effects |
| Commit a redirect | Merge queued effects without consuming an unrelated Page's stored delivery |

Newly queued effects and reservations need atomic store semantics. Unknown storage outcomes are reported; the context does not blindly retry them. Completion precedes HTTP writing, so a later network failure cannot prove delivery or roll back a completed transaction.

## Configure a storage domain

Boot uses namespaced `HttpSessionStore` and registers the session mutex listener. Explicit MVC integration must register that listener itself. The namespace is a 1–64 character safe identifier and must match controllers, advice and custom security handlers.

`MemorySessionStore` is single-node. Associate each store with one user's storage domain; a singleton global store would mix users. A sessionless Page can render, but cross-redirect effects cannot commit without a session store. Distributed stores require a real implementation of the SPI's reservation/merge invariants.

## Verify failures

Check successful delivery, a second reload, overlapping Page requests and a failed Page followed by a successful recovery. Stored feedback should remain available after the failed Page, while that failed request's pending effects must not leak. Identity changes may deliberately discard anonymous session state; see [authentication](authentication.md). For a new backend, follow [custom session stores](../integrations/custom-session.md).
