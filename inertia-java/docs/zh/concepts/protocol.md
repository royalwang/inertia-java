---
title: "Inertia 协议"
description: "理解 HTML、JSON、版本刷新、重定向和请求头的语义。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ProtocolPolicy.java
  - inertia-java/compatibility/README.md
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
translation:
  locale: zh-CN
  canonicalId: concepts/protocol
  source: concepts/protocol.md
  sourceRevision: 9afce1dd7a97079af5d73041b3b805d4417c941242486f0f0d51ba78720bec29
---

# Inertia 协议

同一条 Java 路由既可返回首次浏览器文档，也可返回后续 Page 更新。协议请求头控制传输和加载，不负责身份认证。

## HTML 与 JSON 访问

普通首次 GET 从应用的 `RootView` 获得 HTML 文档，其中包含客户端资源、根元素和序列化 Page 数据。SSR 成功时，根元素已经包含渲染内容。

官方客户端访问会发送 `X-Inertia: true`。响应返回 Page JSON，同时包含 `X-Inertia: true`。Page 包含已注册的组件名、props、URL 和版本，以及可选的 flash、deferred 分组、合并路径和展示标志等元数据。

`Vary: X-Inertia` 防止缓存混淆这两种表示。经过认证或与用户有关的响应，还应遵循应用的 private/no-store 策略。即使 Java 序列化正确，忽略这些差异的 CDN 配置仍可能泄露内容或返回错误表示。

## 请求控制

| 请求头 | 含义 |
| --- | --- |
| `X-Inertia-Version` | 与服务端当前版本比较的客户端构建标识 |
| `X-Inertia-Partial-Component` | 请求执行 partial 选择的组件 |
| `X-Inertia-Partial-Data` / `X-Inertia-Partial-Except` | 对匹配组件包含或排除 prop 路径 |
| `X-Inertia-Error-Bag` | 将默认校验交付限定到命名表单 |
| `X-Inertia-Reset` | 对替换路径抑制合并指令 |
| `X-Inertia-Except-Once-Props` | 告知服务端客户端已经持有的 once key |

partial 请求指定其他组件时，不应用其选择规则。点路径选择包含相关祖先和后代；except 移除该路径及后代。`always` 可以绕过显式排除。[API 指南](../api-guide.md#prop-选择与异步工作)说明了仅 except 时 optional 查询的重要规则。

## 重定向与刷新

应用控制的重定向返回 `ProtocolPolicy.redirect(target)`，初始状态码为 302；后置策略将 Inertia PUT/PATCH/DELETE 的重定向转换为 303。完整文档或外部导航使用 `context.location(target)`；对于 Inertia 请求，它返回协议的 location 响应。

适用 GET 携带过期版本时，在控制器执行前返回刷新结果，客户端随后加载新文档和资源。fragment、prefetch 及空响应规则统一由 `ProtocolPolicy` 处理。独立适配器应调用它，不应在控制器中复制部分规则。

`context.back()` 检查 origin，不安全的 Referer 会回退到 `/`。这些辅助方法不能自动保证任意业务重定向目标安全，应用仍需明确选择目标。

## 安全与互操作

客户端可以伪造以上所有请求头。暴露任何数据前都必须授权，包括 optional/deferred 查询。CSRF、cookie 策略、可信代理地址重建和身份存储由宿主应用负责。

[兼容矩阵](https://github.com/royalwang/inertia-java/blob/main/inertia-java/compatibility/README.md#http-policy-contracts)描述 Rust 派生契约和 Java 的明确策略差异。请求头序列化和客户端行为应使用实际 HTTP/浏览器检查；纯 fixture 对比的覆盖范围更小。
