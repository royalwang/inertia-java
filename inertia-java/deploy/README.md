# Release packaging and host deployment

This entrypoint packages the current Java example and library artifacts into an independent directory. It does not publish to a Maven repository or operate a production host. Current qualification is a local macOS deployment rehearsal; Linux/systemd, reverse proxy, TLS, authenticated session continuity, signatures and production storage remain deployment gates. License/third-party attribution review is still required before public distribution.

From `inertia-java/`, first build Maven and the frontend:

```sh
./mvnw --batch-mode spotless:check verify
cd examples/spring-react/frontend
npm ci
npm run typecheck
npm run build
cd ../../..
node deploy/release.mjs /absolute/release-store
```

The final line prints `/absolute/release-store/<release-id>`. Packaging checks the complete frontend inventory against its canonical build receipt, then stages the complete release and publishes via same-filesystem rename. The SHA-256 release ID covers the version/build ID and all payload file hashes. Existing identical releases are reused; changed/corrupt existing content is rejected without overwriting it. Source builds are inputs: this produces the same payload ID from identical built inputs, not a claim of bit-reproducible Maven/Vite compilation.

The payload includes `app.jar`, `frontend/dist/{client,ssr,build.json}`, package/lock files, `assets/<build-id>/`, a release-local `runtime.mjs`, runbook, systemd templates and all seven library binary/source/Javadoc jars and module POMs plus parent POM under `maven/io/inertia/...`. The Maven subtree is an artifact staging layout, not a signed/metadata-complete Maven repository; source/Javadoc archive content is verified locally; public publication, dependency/license attribution, signing and release-version policy are separate gates. Node bundle/dependencies are kept out of generic core jars.

The manifest inventories all payload files. `runtime.mjs check` verifies exact inventory, file hashes, canonical release ID and frontend version, rejecting additional/changed/missing files and payload symlinks before launching Java or Node. `frontend/node_modules` is excluded because it is installed separately from the locked production dependencies; startup does not attest installed third-party bytes. Hashes do not establish artifact authorship/signatures, defend against a party that can rewrite manifest and payload together, or prevent mutations after checking. Deploy from trusted read-only release storage and prepare locked dependencies before runtime users gain access.

In the printed release directory:

```sh
node runtime.mjs check
cd frontend
npm ci --omit=dev --ignore-scripts --no-audit --no-fund
cd ..
APP_PORT=18080 SSR_PORT=13714 node runtime.mjs pair
```

Node >=22.12 and Java21 must be available on PATH. npm ci requires access to the configured registry/cache and may fail before runtime; no development dependencies/source checkout are required to serve the release. Installation can be done in a staging copy before making the directory read-only. Do not put application logs or mutable files inside the release directory: unexpected files fail verification. Both root IDs use `INERTIA_ROOT_ID`, default `app`. `APP_HOST` defaults to loopback; `0.0.0.0` is explicitly supported for a host whose ingress/access controls are configured.

`pair` is a local operational rehearsal: it starts renderer then Java, checks real same-version SSR `/users`, prints an `INERTIA_READY` JSON record, and waits. `APP_PORT=0 SSR_PORT=0` selects isolated local ports (renderer uses a short reserve/release window). Renderer exit after readiness leaves Java available with CSR and a diagnostic; pair mode does not restart it. SIGINT/SIGTERM propagates to owned children; Java's shutdown-phase budget is5s, runner waits8s then force-stops if required. Unfinished work beyond that budget is not guaranteed to drain. This console readiness marker is not systemd sd_notify, service-mesh readiness or a continuous readiness guarantee.

For independently supervised services, use `node runtime.mjs ssr` and `node runtime.mjs java` with the same release, root and **fixed** SSR port. They forward termination and preserve child failure as a failing exit status. Java can start and serve CSR while renderer is absent. `/api/health` is Java liveness; `/api/ssr-health` is a cached optional renderer check, automatically enabled by this deployment entry. Neither substitutes for testing actual SSR/version and application dependencies before ingress switching.

## Shared assets and release switching

The packaged `assets/` contains only the current client release. Before switching A→B on a real host, prepare a trusted shared archive containing **both** build IDs, and set `INERTIA_ASSET_STORE=/absolute/shared-store` for each Java service. From a source build use the existing `npm run publish:assets -- /absolute/shared-store` before startup; it stages/validates and retains old assets. A deployment archive can also provision the versioned payload directory byte-for-byte; Java verifies its current archived files before serving. Do not merge files into a single unversioned directory or prune old releases merely because ingress changed. Archive retention is owned by the deployer and must cover active documents, caches and rollback.

Start the new Java/renderer pair on unused fixed ports, verify actual same-build SSR and Java liveness, check old `/build/<A>/...` URLs through the new serving layer, then change the reverse proxy's upstream. Rollback selects the retained immutable A pair and its port mapping. Keep same-origin browser URLs stable; renderer listens only on localhost and is not exposed by ingress. No reverse proxy/TLS/DNS change is performed by these scripts. Cookie forwarding, CSRF, proxy headers, graceful ingress drain, session affinity/migration and any database state need qualification in the intended deployment. The example's single-node HttpSession does not prove session survival across A/B processes.

## Linux systemd templates

`deploy/systemd/inertia-java@.service` and `deploy/systemd/inertia-ssr@.service` in the source (under `operations/systemd/` in the payload) use immutable release IDs as instance names and `/opt/inertia/releases/<id>` as their payload directory. Provision a non-login `inertia` user/group, Java21/Node on the service PATH (`/usr/bin/node` in these templates), read-only payload/production dependencies, and `/etc/inertia/<id>.env` based on `release.env.example`. Adjust executable paths for the host, install the two units, validate with the host's `systemd-analyze verify`, then start the Java instance (it Wants/orders the SSR instance). These steps are a target-host runbook, not commands already performed here.

The units supervise services independently, restart failing processes, and do not bind Java lifetime to SSR availability. `Type=exec` confirms wrapper execution, **not** application readiness; perform the actual health/SSR checks above. `KillMode=mixed` sends the initial stop to the wrapper which forwards it; the manager handles remaining descendants after the stop timeout. Read-only filesystem/private temporary space reduce accidental payload mutation, but are not a full host security policy. Configuration references: [systemd service documentation source](https://raw.githubusercontent.com/systemd/systemd/main/man/systemd.service.xml), [execution documentation source](https://raw.githubusercontent.com/systemd/systemd/main/man/systemd.exec.xml), [kill documentation source](https://raw.githubusercontent.com/systemd/systemd/main/man/systemd.kill.xml). Unit syntax/runtime is not qualified on this macOS host.

## Local verification

After the builds, run `node deploy/verify-release.mjs`. It creates a release under a path containing spaces outside the checkout; installs only production npm dependencies; confirms Vite/Playwright absent from that deployed dependency tree; checks publication idempotence after install; and starts the release-local pair. Two real browser flows qualify SSR hydration/navigation/deferred/validation/flash and CSR navigation/form/flash after killing only the owned renderer. It confirms Java remains live, verifies parent termination, corrupts the jar to test preflight rejection/no overwrite, restores it and checks again. Logs, browser screenshots/traces and a false/success summary remain in a unique temporary directory. It never silently treats a failed browser command as qualified deployment. POSIX ps is required for identifying the owned renderer; Windows is not covered. `INERTIA_DEPLOY_OUTPUT` selects a parent directory (created if missing).

This command prepares local files and starts isolated loopback services; it does not install units, require administrator privileges, upload artifacts, or restart existing application processes. The regular aggregate command and performance benchmark are separate checks.
