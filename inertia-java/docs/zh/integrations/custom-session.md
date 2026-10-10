---
title: "自定义会话存储"
description: "实现原子交付 SPI 及隔离保证，并验证真实后端并发与失败。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SessionStore.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/MemorySessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaSessionStoreFactory.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/HttpSessionStoreTest.java
translation:
  locale: zh-CN
  canonicalId: integrations/custom-session
  source: integrations/custom-session.md
  sourceRevision: 647c16d89526439139f733a1584ccf383fd6b6a264bc23921759e71645bde8ba
---

# 自定义会话存储

Servlet 本地或内存存储不满足部署需求时，为单个用户存储域实现 `SessionStore`。分布式实现必须在并发与存储失败下保留事务契约，仅包装 key/value 不够。

## SPI 不变量

| 操作 | 必须满足的行为 |
| --- | --- |
| `get` / `put` / `pull` | 宿主定义的安全隔离域内 key/value 访问 |
| `beginPageDelivery` | 在非 null 交付 token 下原子预留快照 |
| `completePageDelivery` | 至多消费 token 一次 |
| `abortPageDelivery` | 恢复预留交付，至多消费 token 一次 |
| `merge` | 原子合并待提交效果，或报告失败 |

`Delivery` 在构造和访问时复制 JSON 快照。实现需保留隔离，不能暴露共享可变状态。begin/merge 失败必须原子；结果未知需要报告，不能视为成功的空交付。

context 不会盲目重试未知存储结果。完成失败后，它尝试一次契约规定的恢复路径。按真实存储原语设计 token 幂等和失败观测；部署范围包含进程退出或网络不确定性时，也应覆盖这些条件。

## 隔离身份与 namespace

不要把所有用户连接到全局 `MemorySessionStore`。Servlet 实现使用带 namespace 的状态域和协调 session mutex。MVC、advice 与安全重定向 handler 的 namespace 必须一致。

身份轮换/失效遵循宿主策略。类型化重定向 advice 保留原 store，不替失效会话创建新存储。无会话 Page 有效，但没有 store 就不能提交跨重定向 flash/errors。

## 验证实现

以现有 memory/Servlet 实现和会话契约/失败测试为参考。对真实后端增加重叠预留、complete/abort 竞争、新合并效果、token 重用、已脱离域和未知失败测试。单节点成功不能证明集群故障切换或浏览器恰好一次交付。

本文档不提供或证明 Redis/数据库分布式存储适配。存储序列化、保留期、访问控制与一致性由应用选择。用户可见生命周期见 [flash/会话](../guide/flash-session.md)，传输收尾顺序见[自定义适配器](custom-adapter.md)。

## 选择请求专属存储

Spring Boot 接受应用提供的 `InertiaSessionStoreFactory` bean。MVC 适配器对每个参与请求只调用一次 `create`，并在重定向或 outcome advice 中继续使用同一个存储句柄。版本冲突响应会在创建存储之前结束；错误 Page 使用无会话 context。现有 configurer 构造器保留 `HttpSessionStore` 默认行为；普通 MVC 可以使用接收工厂的构造器。

工厂必须从可信宿主会话状态取得身份，并返回非 null 句柄。后端连接由应用持有，在应用关闭时释放。应用必须为所选后端安排会话失效、身份轮换及分布式 fencing；仅替换工厂不会自动获得这些保证。不能为所有用户提供同一个全局存储，也不能直接使用请求头或参数作为存储 key。
