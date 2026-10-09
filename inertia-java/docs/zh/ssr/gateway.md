---
title: "HTTP SSR 网关"
description: "配置连接、渲染、字节和并发限制，并校验 Page envelope。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/HttpSsrGateway.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/SsrEndpointResolver.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ConfiguredHttpUrl.java
  - inertia-java/examples/spring-react/frontend/src/pages.ts
  - inertia-java/examples/spring-react/frontend/src/ssr.tsx
verification:
  - inertia-java/inertia-ssr-http/src/test/java/io/inertia/ssr/HttpSsrGatewayTest.java
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-failures.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-health.mjs
translation:
  locale: zh-CN
  canonicalId: ssr/gateway
  source: ssr/gateway.md
  sourceRevision: a9b63b32260237c1bc8ec3d966e446eb545d45e8beb6b1a0688f3fa7151ed368
---

# HTTP SSR 网关

为可信渲染器复用有界 `HttpSsrGateway`。它发送已解析的 Page JSON，不转发浏览器凭据，不重试渲染，也不跟随重定向。

## 配置对端

选择配置 URI 或 `SsrEndpointResolver`，设置连接/渲染预算、最大响应字节、并发、共享 Page codec，以及可选 build/root 验证。示例明确配置 200ms 连接、1s 渲染、2MiB 和 16 并发。这些是示例构造参数，不是通用 starter YAML 默认值。

resolver 可以选择生产端点、明确启用的开发 hot origin 或无端点。排除规则比较去掉开头斜杠的请求路径，支持精确匹配或末尾 `*` 前缀，不是任意正则。配置 bundle 缺失或 hot origin 无效会返回不可用选择。

不要从请求头派生对端 URL。Node 接收用户 Page 数据，即使不转发 cookie，也必须置于适当的内部访问控制之后。

## 响应校验

网关限制实际流式读取字节，校验 HTTP/JSON 结构，并要求可用的渲染输出。启用校验时，返回 build 身份必须匹配 Page 版本，root ID 必须匹配配置。仅 HTTP 200 不能证明有效渲染。

| 失败类型 | 预期策略 |
| --- | --- |
| 无端点或被排除 | 分类为不可用降级 |
| 并发耗尽 | 分类为过载降级 |
| 超时、连接或传输失败 | 分类为传输降级 |
| 空、格式错误或超大结果 | 无效或超限响应降级 |
| build/root 不匹配 | 拒绝 SSR 结果 |

随后 renderer 应用 Page 的默认或必需 SSR 策略。Props 失败发生在此之前。调用方取消会释放拥有的传输任务并报告取消，不会伪造成功降级。

## 安全诊断

示例在调用 React 前，还校验解码 Page：组件名必须是已注册自有属性，props 必须为对象，URL 为最多 8192 字符的字符串，version 为字符串/null，flash 如存在必须为对象，展示标志如存在必须为布尔。拒绝 JavaScript 原型继承的名称，包括 `toString`、`constructor` 和 `__proto__`。

无效 envelope 返回带 `invalidPage: true` 的空输出；Java 网关拒绝不可用 body 并应用正常降级策略。此保护不替代锁定官方 server 的 HTTP 解析或 bigint 还原，也不授权 Page 或组件。健康验证发送 14 个无效解码输入，再渲染有效 Error Page 验证恢复。修改回调时，在已构建示例前端运行 `npm run test:ssr-health`。

需要关联网关观测时，传入相同 observer 和安全配置的端点 ID。不要记录原始 Page body，或将原始对端 URL 作为指标标签。公开降级字符串保持稳定分类，结构化原因枚举区分更细结果。

网关测试及失败/浏览器验证覆盖有界失败场景。用户展示见[降级策略](fallback.md)，对端监控与单 Page 成功的区别见[健康检查](health.md)。
