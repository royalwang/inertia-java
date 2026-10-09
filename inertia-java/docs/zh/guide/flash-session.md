---
title: "Flash 与会话交付"
description: "排队 flash、预留单次交付、恢复失败，并使用 namespace 隔离应用。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
translation:
  locale: zh-CN
  canonicalId: guide/flash-session
  source: guide/flash-session.md
  sourceRevision: 8d8b0bd379fe8986c476c2aba4b86dbdeceba2596eb79f7d205c35c592519290
---

# Flash 与会话交付

Flash 用于短期页面反馈。跨请求交付依靠会话事务，而不是应用全局可变 map。

## 选择 flash 范围

`context.flash(key, value)` 将效果加入队列。类型化重定向把它提交到当前请求的会话域，供下一个 Page 使用。在同一 context 重复使用 key 会失败，以暴露意外重复写入。`response.flash(key, value)` 则直接设置当前 Page 的 flash，不是跨重定向队列。

通过官方客户端的 Page flash 字段读取 flash，它与普通 props 分开。示例表单成功后排队 `toast`，重定向页面显示它，再次刷新不重复交付。

校验错误、clear-history 和 fragment 指令共享 context 的交付生命周期。请求级历史加密覆盖不会通过重定向持久化。

## 交付事务

| 阶段 | 会话行为 |
| --- | --- |
| 开始渲染 Page | 使用 token 预留已存储的交付 |
| 成功完成 Page | 完成 token 一次 |
| 提交前失败或取消 | 恢复预留的存储交付，丢弃失败请求新增的待提交效果 |
| 提交重定向 | 合并队列效果，不消费无关 Page 的已存储交付 |

新排队的效果和预留需要原子存储语义。存储结果未知时会报告错误，context 不会盲目重试。完成事务发生在 HTTP 写入之前，后续网络失败既不能证明交付，也不能回滚已完成事务。

## 配置存储域

Boot 使用带 namespace 的 `HttpSessionStore`，并注册会话 mutex listener。显式 MVC 集成需自行注册。namespace 必须是 1–64 个字符的安全标识，在控制器、advice 和自定义安全处理器中保持一致。

`MemorySessionStore` 仅支持单节点。每个存储应绑定一个用户的存储域；全局单例会混合用户。无会话 Page 可以渲染，但无会话存储就无法提交跨重定向效果。分布式存储必须真实实现 SPI 的预留与合并不变量。

## 验证失败场景

检查成功交付、再次刷新、重叠 Page 请求，以及 Page 失败后成功恢复。失败 Page 之后，存储中的反馈应仍然可用，失败请求的新增效果不能泄露。身份切换可明确丢弃匿名会话状态，参见[认证](authentication.md)。新增存储后端见[自定义会话存储](../integrations/custom-session.md)。
