---
title: "异步 props、预算与取消"
description: "限制异步工作，处理过载与截止时间，避免跨请求共享可取消 future。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/CancellationScope.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaProperties.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CancellationContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/PropsOverloadTest.java
translation:
  locale: zh-CN
  canonicalId: props/async-concurrency
  source: props/async-concurrency.md
  sourceRevision: 184cb379c0b9b945895fb1658c4cc6b3a5aa8cbc9f24f3f170fe09e1e120efae
---

# 异步 props、预算与取消

Prop 回调共享有界执行预算。提高并发以加快慢页面之前，应先明确底层操作的所有权与超时。

## 来源与调度

`Prop.lazy(Task)` 在选择后调度阻塞或计算回调。`Prop.async(Supplier<CompletionStage<?>>)` 在配置的 props executor 中调度 factory，再观察返回 stage。两者都不会自动传播 Servlet、安全或事务 ThreadLocal。

调度前捕获明确授权的不可变输入。返回由请求独占的 stage，使取消能够通知底层操作。跨请求共享可取消 future，可能让一个失败 Page 取消其他用户的任务。

## 预算层次

| 层次 | 目的 |
| --- | --- |
| Props 总截止时间 | 限制整个解析操作 |
| 每请求并发 | 限制同时拥有的来源任务 |
| Executor 线程与队列 | 限制应用范围调度压力 |
| 传输响应截止时间 | 限制适配器等待并触发取消 |
| 数据库/HTTP 提供方超时 | 限制实际外部操作 |

Boot 默认 props 3s、响应 5s、并发 8、executor core/max 8/32、队列 256。这些是经过校验的默认值，不是所有负载的推荐容量。响应超时必须覆盖 props 超时，提供方工作仍需独立限制。

## 失败策略

线程池拒绝、过载、截止时间和未 rescue 的致命来源失败，都会导致解析失败。致命失败取消请求拥有的兄弟任务，不等待永不完成的任务。并行任务中最先观察到的致命失败胜出，不一定遵循声明顺序；成功值和元数据仍保留声明顺序。

对请求拥有的 stage 之外的操作，取消是协作性的。忽略中断的提供方可能在 Page 结束后继续工作。不要吞掉取消，也不要在远程调用期间持有请求或会话锁。

## 压力验证

覆盖慢来源、队列拒绝、factory 启动前取消、致命兄弟失败和永不完成的 stage。断言结果时间有界及任务取消，而非只看异常类型。core 过载与取消契约覆盖这些场景，但不能替你验证数据库驱动或远端服务。替换 executor/resolver bean 前阅读 [Spring Boot](../integrations/spring-boot.md)，安全观测见[诊断](diagnostics.md)。

## 上游参考

- [CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [FutureTask](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/FutureTask.html)
