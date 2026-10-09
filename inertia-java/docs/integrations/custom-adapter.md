---
title: "Custom HTTP adapters"
description: "Implement before/render/redirect/abort/write with core, keeping binaries and streams in the host."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaRequest.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ProtocolPolicy.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
  - inertia-java/docs/examples/CoreApiExample.java
verification:
  - inertia-java/scripts/verify-maven-consumer.py
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustHttpParityTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
---

# Custom HTTP adapters

The core has no Servlet dependency. An application-owned adapter can integrate another Java HTTP stack if it preserves protocol ordering, request ownership, cancellation and session transaction semantics.

## Use the complete core example

[CoreApiExample.java](../examples/CoreApiExample.java) is a compiling minimal integration. It demonstrates core wiring and outcomes without claiming a complete browser application or production HTTP server. Add host-specific header/cookie writing, trusted proxy reconstruction, security, resource serving and lifecycle handling for your actual transport.

## Adapter algorithm

1. Snapshot method, absolute URI, normalized headers and a server-generated safe request ID. Any nonce is trusted application state. Decide forwarded-proxy trust in the host.
2. Run `ProtocolPolicy.before(request, currentVersion)` before controller/query work. Write an early outcome immediately without reserving session delivery.
3. Create a fresh context with the appropriate user's store or null for a sessionless Page.
4. For a Page, await `ResponseRenderer.render(context, response)` inside the transport budget. It resolves, renders, finalizes Page policy and completes delivery.
5. For a controller `HttpOutcome`, commit redirect effects with `context.commitRedirect()`, then apply `ProtocolPolicy.after` before writing. Do not commit again after Page rendering.
6. On timeout/cancellation, cancel owned pending work and abort an uncommitted context. Bound provider operations too.

Preserve multivalue headers such as separate Set-Cookie entries. `HttpOutcome` is text, not a streaming abstraction. Retain the host's file/stream responses. Never share mutable contexts or response builders across requests.

## Test failure order

A stale version should skip business work. Prop failures should cancel owned siblings. Failed rendering should restore reserved stored effects. A write failure after session completion cannot roll back delivery and should not be represented as exactly-once network success.

Compare Page/HTTP policy fixtures, then use the actual host server to verify serialization, cancellation and headers. Core parity alone cannot qualify proxy behavior or network writes. Existing Spring MVC integration is a concrete reference for those additional boundaries, rather than a claim that another stack is already supported. See [ownership](../concepts/ownership.md) and [custom stores](custom-session.md).
