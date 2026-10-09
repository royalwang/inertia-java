---
title: "CSR 降级与必需 SSR"
description: "区分渲染器、props 和授权失败，并选择逐页面策略。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaResponse.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RequiredSsrTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
  - inertia-java/deploy/verify-release.mjs
translation:
  locale: zh-CN
  canonicalId: ssr/fallback
  source: ssr/fallback.md
  sourceRevision: 3532db1b56af7185f7ecc544b17118f18eebd3b714d349d14a43cf66d61ac150
---

# CSR 降级与必需 SSR

决定渲染器不可用时页面是否仍有用。默认返回客户端渲染壳；部分页面可以要求服务端 HTML，失败时返回安全错误。

## Page 策略

| 选择 | Node 失败时的 HTML 行为 | Inertia JSON 行为 |
| --- | --- | --- |
| 默认 | 携带 Page 数据的 CSR 壳 | 正常 Page JSON |
| `withoutSsr()` | 不尝试 Node，直接 CSR 壳 | 正常 Page JSON |
| `requireSsr()` | SSR 无法成功时返回安全 503 | 正常 Page JSON |

`withoutSsr()` 与 `requireSsr()` 按构建器调用顺序生效，最后一次胜出。必需 SSR 是文档请求的展示策略，不会阻塞所有 JSON 导航。

默认降级仍需要有效客户端资源和 JavaScript。禁用 JavaScript 时，空壳没有已渲染页面内容。不能因为启用脚本后最终显示页面，就将它称为 SSR 成功。

## 区分数据与渲染器失败

只有 SSR 阶段可选择此降级。未 rescue 的 prop 查询失败、schema 冲突或会话失败仍是 Page 失败，进入错误路径。掩盖成 CSR 无法修复缺失或未授权业务数据。

必需 SSR 失败不能反射异常文本、Page 载荷或对端响应 body。使用安全状态/内容和结构化服务端诊断。明确客户端能否通过普通导航恢复，或需要显式重试/检查操作。

## 验证策略

启动正常示例，检查禁用 JavaScript 的 HTML 和交互 hydration，再停止自己启动的渲染器。确认默认 HTML 仍返回壳，启用 JavaScript 后可挂载；必需 HTML 返回 503，但有效版本化 JSON 访问仍可用。恢复渲染器后检查真实内容返回，不能只靠健康探测判断。

浏览器故障场景和 core required-SSR 测试覆盖这些边界。部署验证只终止自己拥有的渲染器，确认 Java 存活及 CSR 导航/表单。前序失败见[错误处理](../guide/errors.md)，渲染降级原因见[网关诊断](gateway.md)。

## 上游参考

- [HttpClient 取消](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html#sendAsync(java.net.http.HttpRequest,java.net.http.HttpResponse.BodyHandler))
