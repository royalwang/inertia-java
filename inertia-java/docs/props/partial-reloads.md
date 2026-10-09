---
title: "Partial reloads"
description: "Use only/except, same-component matching, ancestor filtering and unselected zero queries."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaRequest.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/examples/spring-react/frontend/src/pages/Advanced.tsx
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustParityTest.java
  - inertia-java/examples/spring-react/frontend/e2e/advanced.spec.ts
---

# Partial reloads

A partial reload asks the same component for a selected subset of definitions. It reduces query work and payload size when sources are defined lazily; it does not reduce the authorization required for the request.

## Request and selection

Use the official client's reload/visit options rather than manually constructing a second Page model. Requests carry `X-Inertia-Partial-Component`, include paths in `X-Inertia-Partial-Data`, and exclusions in `X-Inertia-Partial-Except`.

The named component must match the response component. Otherwise the adapter resolves the normal full visit. Include selection matches related ancestor/descendant definition paths; except removes its path and descendants. The `always` policy can survive exclusion. Nested `Props` follows the parent's selected context.

An empty include list with exclusions means all non-excluded definitions, including optional callbacks. That rule is inherited from this Rust implementation. It is easy to accidentally run a query when assuming optional means omitted from every except visit.

## Work through Advanced

1. Open `/advanced` with the example running.
2. Request the nested profile delta and inspect both request headers and the returned merge metadata.
3. Use the except action to skip the expensive/profile definitions while observing always status.
4. Fetch optional explicitly and compare its counter with the previous except visit.
5. Use reset and inspect replacement behavior rather than expecting old client state to disappear merely because a field was absent.

A missing prop on a partial response normally lets the client retain existing state. Omission is not an instruction to erase a previously rendered secret. Handle identity/permission transitions through deliberate navigation/history policy and server authorization.

## Verify boundaries

Check callback counts, Page omissions, matching versus different component, nested paths and combined include/exclude rules. HTTP fixtures alone do not establish the browser's retained state; the Advanced browser tests assert actual resulting objects and rendered values. See [merging](merging.md) for replacement versus append behavior and [loading](loading.md) for optional sources.
