# Redis delivery state prototype

This optional module is under R3 implementation. The standalone atomic state/transport contracts are verified locally; host-session lifecycle wiring, Redis Boot selection, two-JVM HTTP/browser qualification and independent consumer documentation are still pending. Do not treat this prototype as a completed distributed-session integration.

`RedisSessionBackend` owns a dedicated Spring Data Redis/Lettuce transport. Commands use dedicated connections, a bounded semaphore and explicit command/connect timeouts. Automatic reconnect, disconnected queuing and mutation replay are disabled. Spring's script-cache `NOSCRIPT` fallback is allowed because Redis explicitly reports that the script did not execute. The backend is application-scoped and must be closed on shutdown.

`RedisSessionStore` accepts a trusted host identity and an optional expected epoch. Only first attachment may pass a null epoch; persist the returned epoch in trusted host-session metadata. Later handles must supply that epoch, otherwise Redis data loss/expiry could look like first use. Never select an identity from arbitrary request input. The key uses the namespace and SHA-256 identity digest; this is isolation, not access control. Configure Redis authentication, network access and host identity policy separately.

Each JSON envelope uses opaque-byte Lua CAS, a revision, epoch, available/reserved state and bounded terminal records. Time comes from Redis. Expired reservations restore values in deterministic chronological priority and invalidate their tokens atomically. The canonical core `MemorySessionStore` supplies merge semantics. Caller-supplied delivery snapshots are never trusted for terminal operations. Invalidation writes a tombstone; it does not delete and recreate the domain.

Operations have bounded explicit conflict attempts and a one-second monotonic dispatch/recalculation budget. A command already submitted may wait up to the configured command timeout (maximum five seconds); this is not a one-second end-to-end response guarantee. CAS also rejects stale read deadlines and reservations whose leases expired before commit. Configure host response/props budgets and lease with those limits in mind. Unknown outcomes are reported as `UNKNOWN_WRITE`; neither the adapter nor application should blindly repeat the mutation or fall back to local storage.

Inputs are finite JSON literals, limited to 48 nested levels, 65,536 nodes per write and 1,000 characters per numeric literal, in addition to the configured UTF-8 envelope size. Decimal precision, large integers, empty arrays/objects and independent error bags are preserved. Reservation/terminal bounds fail closed until eligible terminal records expire. Regular operations and `recoverExpired()` perform cleanup; Redis TTL alone cannot execute the Java merge needed to recover a reservation. This is not a browser-receipt, Redis failover or long-duration capacity guarantee.

Run the actual backend suite from `inertia-java` using a Java 21 environment:

```sh
python3 scripts/verify-redis.py --redis-server /absolute/path/to/redis-server
```

If no runtime is installed, an explicit local source build is available:

```sh
python3 scripts/verify-redis.py --build-runtime
```

The latter downloads Redis 7.2.11 from the official download server, checks the pinned SHA-256 and builds inside a new temporary output directory. It requires `make` and a C compiler. It does not install a global service. Each test owns an ephemeral loopback-only Redis process, uses `noeviction`, and closes it afterwards. A missing runtime or failed suite fails the command; mocks and skipped tests cannot count as qualification. Raw TCP fault injection verifies that an applied CAS with a lost reply is classified unknown and submitted only once.
