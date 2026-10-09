---
title: "Deferred props 与 rescue"
description: "首次渲染后分组加载次要数据，并通过明确的 rescue 元数据处理可选失败。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/examples/spring-react/frontend/src/pages/Users.tsx
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/AdvancedPropsTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CancellationContractTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
translation:
  locale: zh-CN
  canonicalId: props/deferred
  source: props/deferred.md
  sourceRevision: 6a3e065174ae1aba23782a7ae2759d294f37ac3897a4b1d7cb3c910cb6e1fa55
---

# Deferred props 与 rescue

Deferred props 允许首次 Page 省略部分工作，并声明由官方客户端稍后获取的分组。适合不应阻塞首屏的次要信息。

## 定义与渲染

使用 `Prop.defer(task)`，可选链式调用 `.group(name)`。默认组为 `default`；自定义组必须非空，只适用于 deferred 加载。完整访问不执行来源，deferred 分组元数据列出缺失定义。组件匹配的局部请求可以执行它们。

示例 `/users` Page 将 `stats` 放入 `dashboard` 组，React 用官方 `Deferred` 组件包裹内容并提供加载占位。这让首次省略表现为明确的加载过程，而非原因不明的缺失值。

不能用 deferred 绕过用户授权或关键业务状态。后续请求仍是新的已授权请求，拥有独立 session/context/deadline。

## 显式恢复可选失败

`.rescue()` 只允许用于 deferred prop。来源按受支持解析契约失败时，字段省略，`rescuedProps` 元数据记录它，正常兄弟来源继续执行。它不是通用 Page 异常处理器，也不能将授权失败伪装为成功数据。

客户端应区分加载中、可用值以及有意不可用的次要信息。若 UI 需要恢复操作，应在原因处理后明确重载；不要无限重试永久失败。

## 验证两次请求

检查首次 HTML/JSON 中的省略与分组元数据，再检查客户端 deferred 请求和最终统计展示。测试分组、选中子项、正常兄弟项和显式 rescue。未 rescue 的致命失败仍会终止解析并取消当前请求拥有的兄弟任务。

core 契约覆盖分组顺序、元数据和 rescue；浏览器测试证明锁定官方客户端在 SSR/CSR 流程中获取并显示 `stats`。一个 UI 场景并不展示所有可能后端故障。取消见[异步预算](async-concurrency.md)，整个 Page 失败见[错误处理](../guide/errors.md)。
