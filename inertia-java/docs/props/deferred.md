---
title: "Deferred props and rescue"
description: "Group and load optional data after first paint and use explicit rescue metadata."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/examples/spring-react/frontend/src/pages/Users.tsx
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/AdvancedPropsTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CancellationContractTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
---

# Deferred props and rescue

Deferred props let the first Page omit selected work and announce groups that the official client can fetch afterward. They are useful for secondary information whose delay should not block initial content.

## Define and render

Use `Prop.defer(task)` and optionally `.group(name)`. The default group is `default`; a custom group must be nonblank and is valid only for deferred loading. On a full visit the source does not execute and deferred-group metadata names the missing definitions. A matching partial request can execute them.

The example's `/users` Page defines `stats` in group `dashboard`. Its React component wraps that content in the official `Deferred` component with a loading fallback. This makes the initial omission visible as intentional loading rather than an unexplained missing value.

Keep user authorization and essential business state outside a deferred shortcut. A late request is still a new authorized request, with its own session/context/deadline.

## Rescue explicitly optional failures

`.rescue()` is allowed only on deferred props. When that source fails under the supported resolution contract, the field is omitted and `rescuedProps` metadata records it while healthy siblings continue. It is not a general exception handler for every Page or a way to turn authorization failures into successful data.

The client should distinguish loading, a usable value and intentionally unavailable secondary information. If the UI needs a recovery action, issue a deliberate reload after the cause is addressed. Do not retry permanent failures indefinitely.

## Verify two requests

Inspect initial HTML/JSON for omission and group metadata, then the client's deferred request and resulting rendered statistics. Test grouping, selected-child behavior, healthy siblings and explicit rescue. Unrescued fatal failures still terminate resolution and cancel owned siblings.

Core contracts cover group order/metadata and rescue; browser tests prove the locked official client fetches and displays `stats` in SSR and CSR flows. The documentation does not claim every possible backend failure is demonstrated by that one UI. See [async budgets](async-concurrency.md) for cancellation and [error handling](../guide/errors.md) for whole-Page failures.
