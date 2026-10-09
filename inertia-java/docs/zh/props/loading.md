---
title: "Lazy、optional 与 always props"
description: "通过回调次数理解完整和局部访问，包含仅 except 时 optional 的行为。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/AdvancedDemo.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CoreContractTest.java
  - inertia-java/examples/spring-react/frontend/e2e/advanced.spec.ts
translation:
  locale: zh-CN
  canonicalId: props/loading
  source: props/loading.md
  sourceRevision: c21e052d3efc3d5c60b2ec342b79dec1a214573df7d642417f89f688977210f2
---

# Lazy、optional 与 always props

根据值何时需要选择加载行为，将昂贵工作放入回调，使跳过的定义不会执行查询。

## 选择表

| 来源 | 完整访问 | 组件匹配的局部访问 |
| --- | --- | --- |
| 普通值 / `Prop.value` | 包含 | 被选择时包含 |
| `Prop.lazy(Task)` | 执行回调 | 被选择时执行 |
| `Prop.async(factory)` | 调度 factory | 被选择时调度 |
| `Prop.optional(Task)` | 省略且不调用回调 | 被选择时执行 |
| `Prop.always(value)` | 包含 | 即使显式排除也包含 |

lazy 表示执行延迟到 resolver 选择之后，不表示完整访问省略它。optional 才表示完整加载时省略。deferred 还包含稍后客户端请求与分组契约，见[延迟 props](deferred.md)。

`always` 改变选择，不会使值对所有用户都安全。创建之前必须授权；也不要为了标记 always，就预先执行昂贵计算。

## 观察示例

`/advanced` 提供昂贵 lazy 计数器、optional 计数器和 always 状态。完整访问省略 optional，显式 `only` 可以获取它。按本仓库 Rust 行为，仅 except 的访问会选择所有未排除定义，包括 optional。如果需要跳过回调，应将 optional 加入 `except`。

这些计数器是诊断用示例状态，不是缓存或持久化业务记录。它们可以直接显示哪些查询执行，避免只根据缺失字段推测。

## 失败行为

回调共享总截止时间和并发上限。optional 来源被选择且没有 rescue 时，仍可能导致失败。因为未请求而缺失的值，与查询失败不同。不要把所有异常捕获成 null，再表示为成功省略。

验证完整访问、组件匹配的局部访问和组件不匹配的访问，同时断言回调次数与 Page 输出。优化回调成本前，先阅读[局部重载](partial-reloads.md)。
