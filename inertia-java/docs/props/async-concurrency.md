---
title: "Async props, budgets and cancellation"
description: "Configure bounded work, handle overload/deadlines and avoid sharing cancellable futures."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/CancellationScope.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaProperties.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CancellationContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/PropsOverloadTest.java
---

# Async props, budgets and cancellation

Prop callbacks share a bounded execution budget. Design the underlying operation's ownership and timeout before increasing concurrency to make a slow page appear faster.

## Sources and scheduling

`Prop.lazy(Task)` schedules a blocking/computed callback after selection. `Prop.async(Supplier<CompletionStage<?>>)` schedules the factory on the configured props executor, then observes the returned stage. Neither automatically propagates servlet, security or transaction ThreadLocals.

Capture explicit authorized immutable input before scheduling. Return a request-owned stage whose cancellation can signal its underlying operation. Sharing a cancellable future across requests can let one failed Page cancel another user's work.

## Budget layers

| Layer | Purpose |
| --- | --- |
| Props total deadline | Bound the whole resolution operation |
| Per-request concurrency | Limit simultaneously owned source work |
| Executor threads/queue | Bound application-wide scheduling pressure |
| Transport response deadline | Bound adapter waiting and cancellation |
| Database/HTTP provider timeout | Bound the actual external operation |

Boot defaults are 3s props, 5s response, concurrency 8, executor core/max 8/32 and queue 256. These are validated defaults, not recommended capacity for every workload. Response timeout must cover props timeout; provider work still needs independent limits.

## Failure policy

Executor rejection, overload, deadline and unrescued fatal source failures fail resolution. Fatal failure cancels owned sibling work without waiting for a never-completing sibling. The first observed parallel fatal failure wins; it need not follow declaration order. Successful values and metadata do retain declaration order.

Cancellation is cooperative beyond the owned stage. A provider ignoring interruption can keep doing work after the Page ends. Avoid swallowing cancellation or holding request/session locks around remote calls.

## Verify under pressure

Exercise a slow source, queue rejection, cancellation before factory start, a fatal sibling and a stage that never completes. Assert bounded outcome time and owned cancellation, not only exception type. The core overload/cancellation contracts cover these cases; they do not qualify your database driver or remote service. See [Spring Boot](../integrations/spring-boot.md) before replacing executor/resolver beans and [diagnostics](diagnostics.md) for safe observations.

## Upstream references

- [CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [FutureTask](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/FutureTask.html)
