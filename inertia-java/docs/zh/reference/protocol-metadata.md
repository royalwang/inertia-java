---
title: "请求头与 Page 元数据"
description: "查阅字段、缺失与 null、合并、once、滚动、历史及 bigint 的协议语义。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaRequest.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ProtocolPolicy.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Page.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/HttpOutcome.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustHttpParityTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustParityTest.java
translation:
  locale: zh-CN
  canonicalId: reference/protocol-metadata
  source: reference/protocol-metadata.md
  sourceRevision: 2983d27fba22ff13d20e66b67bef84c5584078ca7450ef8ff09660566739434c
---

# 请求头与 Page 元数据

适配器将同一已解析 Page 转为文档访问的 HTML，或 Inertia 访问的 JSON。代理必须保留表示请求头，客户端请求应携带当前资源版本。

## 请求头

| 请求头 | 含义 |
| --- | --- |
| `X-Inertia` | 选择 Inertia 请求处理 |
| `X-Inertia-Version` | 浏览器资源版本；Inertia GET 过期或缺失时可能触发 409 |
| `X-Inertia-Partial-Component` | 应用 partial 选择的组件 |
| `X-Inertia-Partial-Data` | 包含的点路径 |
| `X-Inertia-Partial-Except` | 排除的路径 |
| `X-Inertia-Error-Bag` | 选择的校验 bag |
| `X-Inertia-Reset` | 需要重置合并状态的 props |
| `X-Inertia-Except-Once-Props` | 客户端已持有的可复用 once key |
| `X-Inertia-Infinite-Scroll-Merge-Intent` | 客户端滚动合并意图 |

Prefetch 检测还检查 purpose 请求头（`Purpose`、`Sec-Purpose`、`X-Moz`）。组件不匹配时解析正常 Page，不应用其他组件的选择。选择和 [once 元数据](../props/once.md)不能用于授权。

## 响应头与结果

JSON Page 携带 `X-Inertia` 和 JSON content type，HTML 使用文档 content type。`Vary: X-Inertia` 区分表示。版本冲突在控制器执行前返回 409 和 `X-Inertia-Location`。Location/redirect 辅助方法选择协议响应；PUT/PATCH/DELETE 的 302 重定向规范化为 303。

实现还处理 `X-Inertia-Redirect` 及版本相关响应元数据。应用响应头经过校验，`InertiaResponse.withHeader` 保留 `x-inertia*`、content type/length 和 transfer encoding。`HttpOutcome` 校验名称和值，保留多值头和 `Vary: *`。不要覆盖协议头来掩盖过期资源。

## Page 字段

| 字段 | 值或省略语义 |
| --- | --- |
| `component`, `props`, `url`, `version` | Java Page 必需值；props 为对象，version 为文本 |
| `preserveBigIntegers`, `encryptHistory` | 适用时的展示/历史标志 |
| `flash`, `clearHistory`, `preserveFragment` | 存在时的待交付效果 |
| `sharedProps` | 公开时的共享顶层 key 列表；隐藏元数据不隐藏值 |
| `deferredProps` | 分组 → deferred prop 路径 |
| `rescuedProps` | 具有已解析 rescue 输出的路径 |
| `deepMergeProps`, `prependProps`, `mergeProps`, `matchPropsOn` | 描述客户端合并的 prop 路径数组 |
| `scrollProps` | 路径 → pageName/previousPage/nextPage/currentPage/reset |
| `onceProps` | 复用 key → `{ prop, expiresAt }`；到期为 null 或 epoch 毫秒 |

框架通常安装 `props.errors`，应用可明确覆盖并产生诊断。客户端元数据描述合并和加载，服务端不保留浏览器合并 Page。Node 输入保护允许的 version 形状，不等于 Java `Page` 构造器契约。

经过检查的 Rust HTTP/Page fixture 是兼容基准，含四项有意 Java HTTP 差异。改变线协议前阅读 [fixture 策略](../testing/fixtures.md)。
