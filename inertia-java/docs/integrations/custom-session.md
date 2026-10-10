---
title: "Custom session stores"
description: "Implement atomic delivery SPI and scope guarantees; clustered Redis remains a future adapter."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SessionStore.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/MemorySessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaSessionStoreFactory.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/HttpSessionStoreTest.java
---

# Custom session stores

Implement `SessionStore` for one user's storage domain when servlet-local or in-memory storage does not meet your deployment needs. A distributed implementation must preserve the transaction contract under concurrency and storage failure; a key/value wrapper alone is insufficient.

## SPI invariants

| Operation | Required behavior |
| --- | --- |
| `get` / `put` / `pull` | Store-domain key/value access with host-defined safe isolation |
| `beginPageDelivery` | Atomically reserve a snapshot under a nonnull delivery token |
| `completePageDelivery` | Consume the token at most once |
| `abortPageDelivery` | Restore reserved delivery and consume the token at most once |
| `merge` | Atomically merge pending effects or report failure |

`Delivery` copies its JSON snapshot on construction and access. Preserve that isolation rather than exposing mutable shared state. Begin/merge must be atomic on failure; unknown outcomes must be reported, not treated as a successful empty delivery.

The context does not blindly retry unknown storage outcomes. After completion failure it attempts the defined restoration path once. Design token idempotence and failure observability with the actual storage primitive, including process death or network uncertainty if those are in your deployment scope.

## Isolate identity and namespace

Never attach all users to one global `MemorySessionStore`. The servlet implementation uses a namespaced state domain and coordinated session mutex. Namespaces must be consistent through MVC, advice and security redirect handlers.

Identity rotation/invalidation must follow the host application's policy. Typed redirect advice retains its original store and does not create a replacement for an invalidated session. A sessionless Page is valid, but cross-redirect flash/errors cannot commit without a store.

## Qualify the implementation

Use the existing memory/servlet implementations and session contract/failure tests as reference behavior. Add actual backend tests for overlapping reservations, complete versus abort races, newly merged effects, token reuse, detached domains and unknown failures. A successful single-node test is not cluster failover or exactly-once browser delivery evidence.

No Redis/database-backed distributed store is provided or qualified by this documentation. Decide storage serialization, retention, access controls and consistency in your application. See [flash/session delivery](../guide/flash-session.md) for the user-visible lifecycle and [custom adapters](custom-adapter.md) for transport finalization order.

## Select a request-owned store

Spring Boot accepts an application `InertiaSessionStoreFactory` bean. The MVC adapter calls `create` once per participating request and retains that exact store for redirect/outcome advice. Version-conflict responses finish before creating the store; error Pages use a sessionless context. Existing configurer constructors retain their `HttpSessionStore` default; plain MVC can use the constructor that accepts the factory.

The factory must obtain identity from trusted host session state and return a nonnull handle. Backend connections belong to the application and are closed at shutdown. The application must arrange invalidation, identity rotation and distributed fencing for its backend; replacing the factory alone does not provide those guarantees. Never choose a global per-application store or a key taken directly from a request header/parameter.
