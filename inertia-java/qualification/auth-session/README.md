# Local authentication qualification fixture

`AuthProbe.java` is copied only into reactor-external example consumers by
`scripts/verify-maven-consumer.py` when `INERTIA_VERIFY_AUTH_REDIS=true`.
The Vue copy changes only the Java package. The fixture is not included in the
normal React/Vue artifacts and has no public deployment path.

The driver requires `INERTIA_REDIS_SERVER` and owns a standalone Redis process,
a delivery TCP fault proxy, two JVMs, a same-origin HTTP routing proxy and a
Chromium browser. It runs both `new-session` and `change-session-id`, routes
login writes to A and account reads/logout to B, retains a delivery handle on A,
and checks old writes after login, logout and actual idle expiry. It also tests
unreachable delivery Redis while host authentication storage stays available,
explicit recovery, and a real Redis restart that loses both repositories.

The `redis-session` example Maven profile adds Spring Session/Redis dependencies.
Its existing unit tests retain native Servlet sessions; actual distributed
behavior is verified by this independently compiled HTTP/browser fixture.
Control endpoints are test instrumentation, not application APIs or admin tools.
They run only on owned loopback processes and must never be exposed publicly.
