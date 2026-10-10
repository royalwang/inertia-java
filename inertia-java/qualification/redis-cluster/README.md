# Local Redis multi-instance qualification consumer

This standalone Boot application consumes the starter and optional Redis module from Maven artifacts. It shares host sessions through Spring Session Redis and keeps Inertia delivery state in its separate namespace. Fixture control routes are local qualification helpers, not application features or an authentication example.

Use Java 21 and the locked frontend dependencies. The driver starts an owned loopback Redis, a Node renderer, two independent JVMs and a browser, then closes its processes. It never connects to a pre-existing Redis service.

From `inertia-java`, after building the candidate libraries and React frontend:

```sh
./mvnw --batch-mode --no-transfer-progress install
./mvnw --batch-mode --no-transfer-progress -f qualification/redis-cluster/pom.xml package
INERTIA_REDIS_SERVER=/absolute/path/to/redis-server \
INERTIA_BROWSER_CHANNEL=chromium \
node examples/spring-react/frontend/scripts/verify-redis-cluster.mjs
```

The first command installs local candidate artifacts; the second has no reactor-relative parent or source dependency. For stronger isolation, run the existing consumer verifier instead:

```sh
INERTIA_REDIS_SERVER=/absolute/path/to/redis-server \
INERTIA_BROWSER_CHANNEL=chromium \
python3 scripts/verify-maven-consumer.py
```

That command stages all eight libraries, resolves binary/source/Javadoc artifacts into a new private Maven cache, copies this fixture outside the repository, builds it against those artifacts, and supplies its executable JAR to the cluster driver through `INERTIA_REDIS_CONSUMER_JAR`. Optional `INERTIA_CONSUMER_DEPENDENCY_CACHE` seeds third-party dependencies only; owned `io.inertia` coordinates are excluded. `INERTIA_CONSUMER_OUTPUT` selects a new or empty evidence directory.

The six driver phases cover cross-JVM redirect flash and independent error bags, overlapping reservations, expired leases and late original tokens, host identity rotation/destruction, real SSR/hydration/CSRF/form/validation/deferred browser interaction across both nodes, and recovery after intentionally killing one JVM. State/transport edge cases and raw lost-reply injection belong to `scripts/verify-redis.py`; native host/network-restart contracts belong to `RedisHostLifecycleIT`.

The aggregate runner includes those additional suites and the isolated consumer when `INERTIA_REDIS_SERVER` is set:

```sh
INERTIA_REDIS_SERVER=/absolute/path/to/redis-server \
INERTIA_BROWSER_CHANNEL=chromium \
node scripts/verify.mjs
```

Without the environment variable, the aggregate runs the ordinary default-store suite. With it, unavailable Redis or an incomplete Redis phase fails verification. The backend currently qualifies standalone plain TCP only, with no Cluster/Sentinel, failover durability or browser exactly-once guarantee. The driver requires the Playwright Chromium paired with the lockfile (or an explicitly selected compatible channel), and a compiled frontend with matching build identity.
