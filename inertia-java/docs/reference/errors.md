---
title: "Errors and fallback reasons"
description: "Map exception/reason to symptoms, HTTP behavior, safe recovery and matching tests."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropDefinitionException.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropResolutionException.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SsrRequiredException.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Observations.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/HttpSsrGateway.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RequiredSsrTest.java
  - inertia-java/inertia-ssr-http/src/test/java/io/inertia/ssr/HttpSsrFailureTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
---

# Errors and fallback reasons

Classify a failure at its owning stage. A renderer fallback, a failed prop and a failed transport write require different recovery actions.

## Public failures

| Type | Meaning | Action |
| --- | --- | --- |
| `PropDefinitionException` | `INVALID_PATH` or `PARENT_CHILD_CONFLICT`; path/source identify declaration | Correct the declaration before retrying |
| `PropResolutionException` | Selected provider failed; includes prop path and cause | Inspect provider/deadline and apply an intentional rescue policy |
| `SsrRequiredException` | Required SSR received an unavailable/fallback result | Restore compatible renderer or change the page policy deliberately |
| `IllegalArgumentException` | Invalid budgets/configured URL/root/namespace/header/Page input | Correct configuration or input at its source |
| `IllegalStateException` | Closed/reused request effects, duplicate flash or invalid lifecycle | Fix ownership; do not reuse a context |

Internal exception causes are for trusted diagnosis. Do not serialize their messages, request headers or Page data as public errors. The MVC error resolver provides a safe outcome and optional application error Page.

## Renderer fallback strings

| Reason | Check |
| --- | --- |
| `disabled` | Whether a gateway was configured |
| `excluded-or-unavailable` | Endpoint policy/exclusion and renderer availability |
| `overloaded` | In-flight capacity and workload duration |
| `transport-or-timeout` | Connect/render budgets, transport and cancellation |
| `http-status` | Renderer returned a non-2xx status |
| `warming-up` | Renderer returned JSON null before ready |
| `invalid-response` | Head/body shape or blank body |
| `invalid-json` | Response was not valid JSON |
| `build-mismatch` | Java and renderer build identities differ |
| `root-mismatch` | Renderer and Java root identities differ |

Ordinary pages can use CSR on these outcomes. Required pages raise a structured failure instead. The gateway accepts valid 2xx responses; health sampling separately expects 200 with `status: OK`.

## Observation categories

`InertiaObserver.Reason` contains `NONE`, `ERROR`, `PROP_DEFINITION`, `PROP_OVERRIDE`, `ERRORS_OVERRIDE`, `TIMEOUT`, `CANCELLED`, `OVERLOADED`, `DISABLED`, `EXCLUDED_OR_UNAVAILABLE`, `TRANSPORT_OR_TIMEOUT`, `TRANSPORT`, `CONNECTION`, `RESPONSE_LIMIT`, `HTTP_STATUS`, `WARMING_UP`, `INVALID_RESPONSE`, `INVALID_JSON`, `BUILD_MISMATCH`, `ROOT_MISMATCH`, `VERSION_MISMATCH` and `UNKNOWN`.

Transport events may distinguish connection/response-limit failures more precisely than the public fallback string. Unknown strings map to `UNKNOWN`, not a successful render. Session abort failures are retained as suppressed causes of the original failure where appropriate. An unknown storage result must be reconciled using the backend's semantics, not blindly replayed.

Use [SSR troubleshooting](../troubleshooting/ssr-hydration.md) or [session troubleshooting](../troubleshooting/forms-session.md), then prove recovery with an actual request and the affected stage's checks.
