---
title: "共享数据与覆盖"
description: "理解内部、配置、请求和页面的优先级，以及 errors 覆盖和点路径冲突。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Props.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaConfig.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/PropDefinitionDiagnosticTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/ConfigPresentationTest.java
translation:
  locale: zh-CN
  canonicalId: guide/shared-data
  source: guide/shared-data.md
  sourceRevision: 2a23f5fcb468c521559516b8e3540a08f36fddc91be5cb795a1f75bd9de937bd
---

# 共享数据与覆盖

为需要的页面定义少量、已授权的公共 props。共享数据仍会发送给浏览器和 Node 渲染器，不属于私有服务端 context。

## 选择定义范围

| 范围 | 定义方式 | 生命周期 |
| --- | --- | --- |
| 应用配置 | `InertiaConfig.shared` 的 request-to-Props 回调 | 可复用回调，为当前请求调用 |
| 请求 | `context.share(key, value)` | 单个 context 开始解析之前 |
| 页面 | 响应的 `Props` | 单个 Page |

示例配置共享 `appName` 和 once 加载的目录；规范 Spring API 示例共享请求级应用标签。身份相关数据使用明确授权的 DTO，不要将 Servlet 或安全对象存入全局共享值。

## 理解优先级

定义依次从内部 `errors`、配置共享值、请求共享值覆盖到 Page props。相同精确 key 的最后一个定义胜出，位置保留首次声明顺序。只有胜出的 supplier 执行。这是定义替换，不是对象值的递归合并。

使用内置校验流程时应保留 `errors`。库允许覆盖它及其默认加载行为，同时产生 `errors_override` 诊断。定义标量 `auth` 与 `auth.name` 等父子路径冲突，会在执行 supplier 之前被拒绝。

显式组合时，`Props.from(Source, props)` 和 `Props.overlay(...)` 保留来源信息。`overrides()` 提供不含值的不可变 schema 报告，不会插入 Page JSON。

## 避免意外暴露

`withSharedPropKeys(false)` 省略共享 key 元数据，不省略共享值。隐藏元数据不是授权手段，也不能移除敏感 props。自定义 `withUrlResolver(...)` 只改变 Page 展示，不改写路由、重定向或实际请求 URL。

公共载荷应保持精简。明确选择 partial/deferred/once 行为；once 是浏览器复用，不是服务端缓存。共享 supplier 与页面 supplier 遵循相同的选择、并发和取消规则。

对重复 key 检查最终 Page 和 supplier 调用次数，并测试完整与 partial 访问。覆盖或 schema 冲突见 [prop 诊断](../props/diagnostics.md)；把共享回调放到工作线程前，先阅读[所有权](../concepts/ownership.md)。
