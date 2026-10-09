---
title: "SSR 缺失与 hydration 不匹配"
description: "检查构建、根、端点、实际 HTML 和日志，区分降级与 hydration 错误。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/HttpSsrGateway.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/SsrHealthMonitor.java
  - inertia-java/examples/spring-react/frontend/src/ssr.tsx
verification:
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-failures.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-health.mjs
translation:
  locale: zh-CN
  canonicalId: troubleshooting/ssr-hydration
  source: troubleshooting/ssr-hydration.md
  sourceRevision: cadcb92eb0a4605665311a29f123ba2b63f173d68384594a2ab9fa3b453d4592
---

# SSR 缺失与 hydration 不匹配

改代码之前先区分服务端 HTML、渲染器健康和客户端 hydration。交互正常的页面仍可能已经降级到 CSR。

## 确认症状

禁用 JavaScript，请求已知 SSR 页面，检查 document body 与 Page script。内容只在 JavaScript 后出现时，查看 SSR 决策/降级原因。已有 HTML 却产生 hydration 警告时，比较两端组件输出和身份。

[降级原因](../reference/errors.md)包含禁用/排除、过载、传输/状态、无效响应及 build/root 不匹配。健康 `UNKNOWN` 表示尚无结果；`UP` 是缓存的健康形状检查，不证明当前 Page 可渲染。这些信号相互独立。

## 修正所属输入

连接错误时，确认内部 Node 地址/端口和进程实际运行。build 不匹配时，一致重建 Java 使用的凭据、客户端/SSR 输出，并重启匹配渲染器。root 不匹配时，统一 Java root、renderer root 和浏览器挂载代码。路由排除时，确认是否有意使用 CSR。

hydration 不匹配时，检查浏览器专用全局变量、非确定时间/随机值、locale 及两端不同数据。保持组件注册一致。Java 传输不能修复在不同环境输出不同树的 React 组件。

## 验证恢复与失败策略

对同一页面先禁用 JavaScript 请求，再启用脚本 hydration 和导航。确认无 console/network 失败且组件数据正确。在隔离演练中停止 Node，普通页面应 CSR，必需 SSR 页面走规定错误路径。恢复 Node 后再确认真实 SSR。

渲染器健康验证将停机/恢复与应用浏览器流程分开。遵循[健康设置](../ssr/health.md)和[必需/降级策略](../ssr/fallback.md)，不要把缓存健康变成同步请求依赖。
