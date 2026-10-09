---
title: "SSR、hydration 与 CSR"
description: "区分首屏 HTML、JSON 访问、React hydration 和客户端挂载。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/src/app.tsx
  - inertia-java/examples/spring-react/frontend/src/ssr.tsx
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
translation:
  locale: zh-CN
  canonicalId: concepts/rendering
  source: concepts/rendering.md
  sourceRevision: 09c8d1bc05adf0a64123bef63265f771bb67dd3a1dbf287aad00b48b563d3b44
---

# SSR、hydration 与 CSR

服务端渲染在 Node 上生成首次页面 HTML。hydration 将 React 连接到已有 HTML；客户端渲染则把 React 挂载到空壳中。这是不同结果，需要分别验证。

## 渲染选择

| 请求与策略 | 是否调用 Node | 结果 |
| --- | --- | --- |
| 普通 HTML、默认策略、渲染器正常 | 是 | 包含渲染正文和 Page script 的根视图 |
| 普通 HTML、默认策略、渲染器不可用 | 尝试调用或判定不可用 | 携带 Page 数据的 CSR 壳 |
| 普通 HTML、`withoutSsr()` | 否 | CSR 壳 |
| 普通 HTML、`requireSsr()`、渲染失败 | 尝试调用或判定不可用 | 安全的 503 |
| 任一 Page 策略下的 Inertia JSON 访问 | 否 | Page JSON |

`requireSsr()` 和 `withoutSsr()` 按调用顺序生效，最后一次构建器调用胜出。必需 SSR 影响 HTML 渲染，不影响后续 JSON 访问。

## 应用集成

完整示例维护 Java 组件白名单和前端两端入口共用的注册表，统一 root ID、客户端资源、SSR bundle 与构建版本。Java 先解析 props，再将 Page 发给可信 SSR 端点。网关不转发浏览器的认证请求头或 cookie。

`RootView.View` 提供可信 SSR head/body 和模板数据。当前锁定的渲染契约中，body 已包含预期根元素和 Page script，只能插入一次。其他模板值需要转义；自定义 Page script 边界使用 `PageCodec.htmlJson`。

`InertiaConfig.basic(...)` 只有最小根视图，不包含应用资源标签或 Node 网关。它适合演示协议，但不能独立完成 React hydration。浏览器集成见[完整示例](../getting-started/quick-start.md)。

## 浏览器与 Node 入口

示例浏览器入口在根元素已有内容时使用 `hydrateRoot`，否则使用 `createRoot`。两端通过同一显式注册表解析组件名。Node 入口校验解码后的 Page envelope，拒绝继承属性或未注册名称，并验证生产 bundle/build 身份。

HTTP 网关限制连接/渲染时间、响应字节数和并发任务，不重试渲染也不跟随重定向。格式错误或空结果、root/build 不匹配、传输失败都形成分类后的 SSR 降级。Prop 失败发生得更早，仍然是请求失败，不会伪装成正常的 CSR 降级。

## 分别验证

禁用 JavaScript 并检查初始 HTML，以证明 SSR 内容存在。启用 JavaScript，通过表单和导航证明 hydration。停止自己启动的渲染器，重新加载并单独验证 CSR 挂载。启用 JavaScript 的截图可能在两种模式下看起来一样，不能单独证明 SSR。

SSR 健康检查是可选的缓存对端探测。UP 不代表某个组件必然可渲染，渲染器故障也不应自动导致 Java 存活检查失败。应用自行决定就绪状态是否要求 SSR。

继续阅读[构建版本](versioning.md)，了解客户端资源和 Node 输出为什么必须一起发布。
