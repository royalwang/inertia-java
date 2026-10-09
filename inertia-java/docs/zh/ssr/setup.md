---
title: 设置 React 与 Node SSR
description: 配置浏览器和 Node 入口、可信 endpoint，以及一致的 build/root 身份。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: ssr/setup
  source: ssr/setup.md
  sourceRevision: 7829141a632cf94645034811ce7349e70f2fbab88e52c21b17a053b63eb1042a
sources:
- inertia-java/examples/spring-react/frontend/src/app.tsx
- inertia-java/examples/spring-react/frontend/src/ssr.tsx
- inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
verification:
- inertia-java/docs/scripts/verify-first-application.mjs
- inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
---

# 设置 React 与 Node SSR

使用一个 browser 入口、一个 Node SSR 入口和共用组件 registry。Java 解析 Page，发送给可信渲染器，再把返回 body 放入应用的 root view。

## 从完整集成开始

仓库内应用见[快速上手](../getting-started/quick-start.md)，仓库外 Maven 项目见[第一个应用](../getting-started/first-application.md)。两者都有真实资源挂载、构建校验、CSRF 和 browser 入口。`InertiaConfig.basic(...)` 只是最小协议 root，不能自行提供这些浏览器功能。

既有应用需要把以下内容作为一致整体接入：

1. Java 组件白名单和共用前端 resolver。
2. browser `createInertiaApp`：已有内容使用 `hydrateRoot`，空 shell 使用 `createRoot`。
3. Node `createInertiaApp`/`createServer`：用 `renderToString` 渲染相同组件。
4. `ViteBuild`/资源配置、挂载的不可变资源目录和 root view。
5. 可复用 `HttpSsrGateway`：可信 endpoint、预算、codec，以及 root/build 校验。

示例 Node 入口检查 decoded Page envelope 和实际注册的 own component names。生产模式还校验 build receipt 与自己的 SSR bundle。非法输入返回无效/空结果并触发降级，不执行任意组件分派。

## 构建与运行

在示例前端执行 `npm ci`、`npm run typecheck` 和 `npm run build`，再用 `npm run ssr` 启动构建后的 loopback 13714 渲染器。按快速上手从示例应用目录启动 Java。修改 Node `SSR_PORT` 时，也应在 Java `-jar` 之前设置匹配的 `-Dinertia.ssr=http://127.0.0.1:<port>/render`。

源码开发使用 Vite `npm run dev` 和示例显式开启的 JVM development flag。Vite plugin 提供 `/__inertia_ssr`，与构建后独立进程的 `/render` 是不同入口。

## 分别证明 SSR 和 hydration

禁用 JavaScript 确认文档首屏，再启用 JavaScript 验证导航/表单。带当前版本的 Inertia GET 应返回 JSON 且不调用 Node。只停止自己启动的 renderer，检查默认 fallback 或 required-SSR 策略。

部署前继续阅读[资源](vite-assets.md)、[root template](root-template.md)和 [gateway 限制](gateway.md)。缓存健康状态不能代替实际 Page 渲染验收。
