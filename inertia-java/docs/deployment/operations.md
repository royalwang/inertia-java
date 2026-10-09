---
title: "Production operations checklist"
description: "Check target Linux templates, resource budgets, logs, failure recovery and baseline measurement."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/deploy/README.md
  - inertia-java/deploy/runtime.mjs
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaObserver.java
verification:
  - inertia-java/deploy/verify-release.mjs
  - inertia-java/scripts/dependency-inventory.py
---

# Production operations checklist

Operate the service against its actual Page policy and storage requirements. Local rehearsal proves specific software boundaries; production readiness also needs evidence from the intended host and ingress.

## Before traffic

| Area | Required evidence |
| --- | --- |
| Release | Trusted immutable payload, successful preflight, coherent client/SSR/build/root |
| Dependencies | Locked production installation and required attribution/review |
| Resources | Bounded Java props/executor/transport, Node process limits and log destinations |
| Identity | Actual authorization, CSRF/cookie/TLS policy and session continuity plan |
| Assets | Current and retained old URLs through ingress |
| Recovery | Known rollback pair, supervisor restart and shutdown behavior |

Do not use a cached healthy renderer response as proof that a Page can render. Check actual representative SSR and client navigation. Keep Java liveness independent from renderer health where CSR is supported; required-SSR applications may choose stricter readiness.

## Monitor and respond

Observe props/deadlines/overload, SSR fallback reasons, session failures and response-write outcomes. Avoid turning request IDs or component names into high-cardinality metric tags. A successful safe 500 write is not a successful business request; stage metrics need application request context.

When Node is down, confirm Java remains live and determine which pages permit CSR. Restore the matching renderer build and verify content. When overload rises, inspect selected queries and provider timeouts before expanding queues; a larger backlog can increase latency and memory pressure.

A storage failure with an unknown result requires reconciliation appropriate to that backend. Do not blindly replay a write or session merge because the browser saw an error.

## Rehearse changes

Run the independent deployment verifier after launcher/packaging changes, application browser cases after behavior changes, and focused contracts for changed core boundaries. Run dependency inventory when graph/build inputs change. Target-host systemd/proxy/TLS and physical infrastructure are not simulated by those local commands.

Use [metrics](../reference/metrics.md), [error reasons](../reference/errors.md) and [troubleshooting](../troubleshooting/startup.md) for diagnosis. Record release/build identity with incidents without recording sensitive Page payloads.
