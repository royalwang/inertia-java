---
title: "Redis capacity and recovery"
description: "Size delivery bounds, distinguish failures, and recover trusted session identity."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionBackend.java
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionStore.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/RedisHttpSessionStoreFactory.java
verification:
  - inertia-java/inertia-session-redis/src/test/java/io/inertia/redis/RedisObservationIT.java
  - inertia-java/inertia-session-redis/src/test/java/io/inertia/redis/RedisSessionStoreIT.java
  - inertia-java/inertia-session-redis/src/test/java/io/inertia/redis/RedisTlsIT.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/RedisHostLifecycleIT.java
---

# Redis capacity and recovery

The optional delivery backend supports one standalone Redis transaction domain. Configure [TLS and credentials](../reference/configuration.md#redis-tls) for this dedicated connection and configure host Spring Session storage separately. Boot closes the backend at application shutdown; manual wiring should share one backend per application and close it once.

## Size the bounds together

| Bound | Sizing rule |
| --- | --- |
| `max-commands` | Limits simultaneous transport operations, not HTTP requests. One Page uses several reads/CAS commands. Inspect `OVERLOADED` before increasing request concurrency. |
| `command-timeout` | Bounds connect, TLS handshake and each command. Increasing it can keep command slots occupied longer. |
| `lease` | Must exceed response timeout plus command timeout, and remain shorter than idle TTL. Allow for the actual request workload. |
| `max-reservations` | Bounds overlapping deliveries **per domain**. A hot session can hit it even when command slots are available. |
| `terminal-retention` / `max-terminals` | Budget retained tokens for completions, aborts and lease recoveries, including bursts. |
| `max-bytes` | Includes available values, reserved snapshots and retained terminal records. More entry capacity also needs byte capacity. |
| `idle-ttl` | Must cover the host session's real use pattern; successful CAS refreshes it. Expiry fences the old epoch instead of creating empty state. |
| `max-attempts` | Bounds recalculation after explicit no-write conflicts. The separate one-second dispatch budget still applies. |

For example, 100 terminal records per second with five-minute retention require about 30,000 records before burst margin; the default 4,096 would be insufficient for that domain. Check the serialized envelope budget too. Retention is part of fencing old tokens, so do not remove terminal records merely to make a pressure test pass.

An example with 16 command slots and 16 reservations can reject traffic at 32 concurrent requests: distinct sessions compete for command slots, while one hot session can exhaust its reservation bound. Increasing servlet threads alone does not increase either storage limit. In one JVM the native host-session mutex serializes same-session operations; multiple JVMs may also incur CAS conflicts.

Measure your actual TLS, payload, host-session and node topology. Keep successful-request latency/throughput separate from fast rejection rates. Inspect HTTP/server p95/p99, command durations, bounded failure reasons, connections, Redis memory, eviction, domain occupancy and TTL. Sampled memory or occupancy is not an exact peak. A short synthetic run is not a production capacity guarantee.

## Diagnose before changing traffic

Use the [Redis metrics](../reference/metrics.md#redis-delivery-diagnostics). `REDIS_STATE_FAILURE` counts state refusals with zero-duration samples; command timers measure a different scope. Avoid adding keys, identities, tokens or payloads to diagnostic labels.

Using a trusted operations account, inject the password through your secret mechanism as `REDISCLI_AUTH`, then check the actual TLS endpoint:

```sh
redis-cli --tls --cacert /run/secrets/redis-ca.pem \
  --user "$REDIS_USER" -h "$REDIS_HOST" -p "$REDIS_PORT" PING
redis-cli --tls --cacert /run/secrets/redis-ca.pem \
  --user "$REDIS_USER" -h "$REDIS_HOST" -p "$REDIS_PORT" INFO memory
```

Repeat the same connection options with `INFO stats` and `INFO clients`. Inspect `used_memory`, `used_memory_peak`, `evicted_keys`, `expired_keys`, `connected_clients` and `total_connections_received`. The delivery client opens dedicated command connections; connection churn can dominate TLS cost even with low Redis memory. Do not switch off peer verification or enable replay to reduce that cost. If trust configuration changed, rebuild the backend or restart the application, then verify a real Page and one-time delivery.

## Recover by failure kind

| Failure | Recovery |
| --- | --- |
| `READ_FAILED` | Restore endpoint/authentication/trust and verify a new Page read. This classifies Redis work; it does not prove application business work never ran. |
| `UNKNOWN_WRITE` | Stop automatic replay. Reconcile application business status using its own transaction/idempotency reference; treat the delivery outcome as uncertain. |
| `CAPACITY` | Distinguish command `OVERLOADED` from state `REDIS_CAPACITY`. Reduce admission, wait for active work to drain, then verify a new request in the affected session. |
| `CONTENTION` | Reduce concurrent writes to the hot domain; inspect CAS conflict counts. A later explicit operation has a new budget, but must not replay an uncertain business request. |
| `BUDGET_EXHAUSTED` | Investigate connection/handshake duration, conflict count and slow observers. The expired dispatch budget cannot be repaired by increasing `max-attempts` alone. |
| `INVALID_TOKEN` | Reject the late/reused token; continue with a fresh Page. Do not force an old complete/abort into another reservation. |
| `STALE_DOMAIN` | Use trusted host logout/new-session recovery. Old handles stay fenced; do not delete attachment metadata or reuse an old epoch to manufacture success. |
| `INVALID_STATE` / `CLOCK_REVERSED` | Stop affected delivery, correct incompatible data or server time, then reconcile trusted identity. Do not silently reset state. |

After an interrupted delivery, a later operation or `recoverExpired()` on a known live domain can recover expired leases atomically. Redis TTL alone cannot run the Java merge that restores reserved effects. A recovered lease invalidates its old token; a late complete/abort remains rejected. Terminal-capacity recovery may need to wait for retention expiry before a transition can succeed.

If Redis restarts without the delivery state, surviving host metadata is stale. Restore Redis first. The application's lifecycle path can call `RedisHttpSessionStoreFactory.beforeHostChange(trustedSession)` before invalidating/rotating that host identity; a fresh read of a missing/revoked epoch reconciles revocation. If revocation still fails, keep the host operation failed and its pending metadata, fix storage, and repeat the deliberate lifecycle reconciliation. Then create a fresh host identity and authenticate according to application policy. Do not recreate the old domain or copy its reservations. External host-repository deletion needs an equivalent application hook.

Finish recovery with an actual request through the configured adapter: verify authentication, HTML/JSON, one-time flash/errors and a subsequent visit without replay. A successful Redis `PING` alone does not establish that session delivery works. Persistence, asynchronous replication, Cluster/Sentinel and failover durability need their own deployment qualification.
