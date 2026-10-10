---
title: "Custom session stores"
description: "Select optional standalone Redis delivery or implement a request-owned atomic storage adapter."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SessionStore.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/MemorySessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaSessionStoreFactory.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionBackend.java
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionStore.java
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionOptions.java
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionException.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaRedisAutoConfiguration.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaRedisProperties.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/RedisHttpSessionStoreFactory.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/RedisSessionLifecycleFilter.java
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

Use the optional standalone Redis adapter below, or qualify another backend for your deployment. See [custom adapters](custom-adapter.md) for transport finalization order.

## Select a request-owned store

Spring Boot accepts an application `InertiaSessionStoreFactory` bean. The MVC adapter calls `create` once per participating request and retains that exact store for redirect/outcome advice. Version-conflict responses finish before creating the store; error Pages use a sessionless context. Existing configurer constructors retain their `HttpSessionStore` default; plain MVC can use the constructor that accepts the factory.

The factory must obtain identity from trusted host session state and return a nonnull handle. Backend connections belong to the application and are closed at shutdown. The application must arrange invalidation, identity rotation and distributed fencing for its backend; replacing the factory alone does not provide those guarantees. Never choose a global per-application store or a key taken directly from a request header/parameter.

## Optional standalone Redis delivery

Add `inertia-session-redis` at the same version as the starter, then select it explicitly. The default remains Servlet-local storage. Selecting Redis without its module fails startup instead of silently falling back.

```xml
<dependency>
  <groupId>io.inertia</groupId>
  <artifactId>inertia-session-redis</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
inertia:
  session-namespace: my-application
  session:
    store: redis
    redis:
      host: 127.0.0.1
      port: 6379
      command-timeout: 200ms
      idle-ttl: 30m
      lease: 30s
      terminal-retention: 5m
```

This stores one-time delivery state; it does not share or authenticate the host `HttpSession`. Multi-instance applications also need a qualified host-session store, such as their configured Spring Session Redis repository. Use the same host-session configuration and delivery namespace on both instances. Host identity comes from `HttpSession`, never a request-selected Redis key. The delivery transport is a separate owned client because a general-purpose Redis client's reconnect/replay policy is unsuitable for uncertain mutations.

### Identity and lifecycle

`RedisHttpSessionStoreFactory` persists a domain epoch with its trusted host identity in session metadata. Later requests must match it. If delivery state expires or is lost while host metadata remains, the operation fails; it does not turn the stale identity into an empty new domain. Arrange host logout/new-session recovery through application policy. Set the idle lifetime to cover the host session's actual usage pattern.

Boot registers `RedisSessionLifecycleFilter` after Spring Session's standard filter and before Security/MVC. It revokes delivery state before synchronous `changeSessionId` or `invalidate`; native Servlet listeners also use the same factory. If revocation fails, the host operation fails and pending epoch metadata remains for reconciliation. Copying host attributes during session migration does not authorize copying an old reservation into a new identity. Keep this filter after any custom host-session filter and before authentication. External repository deletion, administrative logout and asynchronous host operations must arrange equivalent revocation hooks; they are not inferred from a cached session.

### Failure, lease and capacity

`beginPageDelivery` reserves available effects under a token and lease. `completePageDelivery` consumes only that reservation; `abortPageDelivery` restores it while preserving later writes and independent error bags. Lease recovery restores expired data and invalidates its old token in the same atomic transition. Regular operations and `recoverExpired()` clean up known domains; Redis TTL alone cannot run the Java merge needed for recovery.

Use a lease longer than the response and storage command budgets. Boot validates that ordering. Increase capacity deliberately rather than discarding errors: envelope bytes, outstanding reservations and retained terminal tokens all have limits. Reaching a bound fails the operation. JSON values preserve decimal precision, large integers and empty arrays/objects; POJO/binary nodes, nonfinite floats, more than 48 nested levels, more than 65,536 nodes per write or numeric literals longer than 1,000 characters are rejected.

| Type | Responsibility |
| --- | --- |
| `RedisSessionBackend` | Application-scoped Spring Data Redis/Lettuce transport; close at shutdown |
| `RedisSessionStore` | Epoch-bound request handle and atomic delivery operations |
| `RedisSessionOptions` | Namespace, idle/lease/retention and capacity bounds |
| `RedisSessionException` | Bounded `Reason` classification without business data |

`READ_FAILED` means a read failed before mutation submission. `UNKNOWN_WRITE` means the mutation result is uncertain: do not replay it, claim success, return empty state or fall back to local storage. Automatic reconnect/replay and disconnected queuing are disabled. Only an explicit no-write CAS conflict or stale read deadline permits bounded recalculation. A one-second dispatch/recalculation budget does not cancel a command already submitted; that command remains subject to its configured timeout. `STALE_DOMAIN`, `INVALID_TOKEN`, `CAPACITY`, `CONTENTION`, `BUDGET_EXHAUSTED`, `CLOCK_REVERSED` and `INVALID_STATE` describe other fail-closed conditions. Avoid exposing underlying storage details to end users.

The supported storage topology is one standalone Redis transaction domain. Configure Redis network access/authentication and persistence for your deployment; the current backend constructor uses a plain connection. Redis asynchronous replication, failover or data loss can discard acknowledged writes. This adapter does not guarantee cross-failover durability, exactly-once browser receipt or cross-language session sharing. See [configuration](../reference/configuration.md) for bounds and [flash/session delivery](../guide/flash-session.md) for the render/HTTP-write boundary.
