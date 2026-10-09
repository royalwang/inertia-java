---
title: "状态、身份与所有权"
description: "区分应用配置、请求状态、业务事务和异步任务捕获的身份。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
translation:
  locale: zh-CN
  canonicalId: concepts/ownership
  source: concepts/ownership.md
  sourceRevision: 2c5beb536483084fed31bf0460d4ca2eddfc35d5ff04b48d913e31e970cb3cc3
---

# 状态、身份与所有权

复用不可变配置与有界基础设施，将可变 Page 构建和交付状态限制在单次请求内，以避免跨用户泄露、重复效果或误取消其他请求的任务。

## 对象生命周期

| 对象 | 生命周期与所有者 |
| --- | --- |
| `InertiaConfig` | 可复用应用配置；方法返回新的配置值 |
| `PageCodec` | 可复用的私有序列化配置；复制传入的 ObjectMapper |
| `PropsResolver`, `ResponseRenderer` | 可复用应用服务 |
| Executor | 显式 core 集成时由应用管理；Boot 管理默认 bean 生命周期 |
| `HttpSsrGateway` | 可复用的可信对端客户端，具有并发限制 |
| `InertiaRequest` | 单次请求的不可变快照 |
| `InertiaContext`, `InertiaResponse` | 可变、请求独占、仅可使用一次 |
| 会话存储 | 单个用户的存储域，具有明确 namespace 和原子交付 SPI |
| 异步来源 stage | 请求独占的任务，其取消不能影响其他请求 |

不要在单例控制器字段中保存 context/response，不要让所有用户共享一个全局 `MemorySessionStore`。如果取消一个 Page 会取消所有消费方，就不能从来源返回共享 future。

## 调度之前完成授权

Prop supplier 可以在有界工作线程池执行。安全、Servlet、事务和请求 ThreadLocal 不会自动传播。调度前捕获已授权的不可变 DTO 或明确标识；后续任务所需的事务和安全边界应由应用管理。

lazy、optional、deferred 和 once 控制加载及客户端复用，都不能替代授权。隐藏前端组件或设置 partial 选择标志，不能成为保护数据的唯一检查。

## 共享的是定义，而非全局可变值

定义按以下顺序覆盖：内部 `errors`、配置共享 props、请求共享值、Page props。相同精确 key 最后一个定义胜出，只有它的 supplier 执行；位置保留首次声明顺序。父子路径冲突在执行来源之前失败。

依赖内置校验时，应保留 `errors`。库允许覆盖并输出诊断，因此应用需自行遵守此约定。覆盖诊断属于 schema 信息，不是公开 Page 数据。

## 会话交付

会话 SPI 原子预留待交付状态，然后对该预留完成或中止一次。新排队的效果按 SPI 契约合并。渲染失败会恢复存储中的交付，并丢弃失败请求新排队的效果。

`HttpSessionStore` 隔离 namespace 状态，并使用 Servlet 会话协调。Boot 注册 mutex listener；显式 MVC 集成必须自行注册。`MemorySessionStore` 是单节点实现，两者都不提供集群协调、故障切换语义或网络恰好一次交付保证。

Page 可以不使用会话。跨重定向的 flash/errors 需要存储；无存储却尝试提交这些效果会失败。请求、advice 和自定义安全处理器的 namespace 设置必须一致。

失败顺序见[生命周期](request-lifecycle.md)，精确集成规则见 [API 指南](../api-guide.md#会话、校验与-mvc-异常)。
