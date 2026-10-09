---
title: "响应、状态码与请求头"
description: "设置业务状态和响应头，并使用协议重定向、location 和 back。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaResponse.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/HttpOutcome.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ProtocolPolicy.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustHttpParityTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
translation:
  locale: zh-CN
  canonicalId: guide/responses
  source: guide/responses.md
  sourceRevision: 175a2b05b0af72d33719b17baa41e88ae9f260efcde9a326081d690abf1a5be1
---

# 响应、状态码与请求头

内容返回 Page；协议重定向、location 或其他小型文本结果返回 `HttpOutcome`。Spring 类型化适配器在写入字节前完成 context 与协议收尾。

## 选择结果

| 需求 | API |
| --- | --- |
| 渲染已注册页面 | `new InertiaResponse(component, props)` 或 `context.render(...)` |
| 设置业务状态，例如安全的未找到页面 | `response.status(404)` |
| 设置应用缓存或安全响应头 | `response.withHeader(name, value)` |
| 表单后重定向 | `ProtocolPolicy.redirect(applicationTarget)` |
| 强制完整文档导航 | `context.location(applicationTarget)` |
| 返回安全的来源页面 | `context.back()` / `backWithErrors(...)` |
| 仅供根视图使用的数据 | `response.withViewData(name, value)` |

`InertiaResponse` 是可变、一次性的构建器，并非已解析的 JSON Page。其 `withHeader` 拒绝 `X-Inertia*`、content type/length 和 transfer encoding，因为这些由传输层负责。每个构建器 header key 只保存一个值；需要其他传输语义时，使用宿主框架。

`HttpOutcome` 包含文本正文和不可变的多值响应头，不是文件或流式 API。此类响应应使用 Spring 的文件与流式抽象。

## 明确重定向目标

重定向辅助方法产生 302，后置策略将 Inertia PUT/PATCH/DELETE 的重定向转换为 303。fragment/prefetch 和空正文策略也统一执行。不要在每个控制器中复制这些规则；普通类型化 Spring handler 不应手工调用 `commitRedirect()`，此操作由适配器负责。

`back()` 只接受当前 origin，否则回退到 `/`。其他重定向目标由应用负责，应选择已知目的地，而非直接反射任意输入。

## 正确缓存

经过认证或用户特定的页面通常需要明确的 private/no-store 策略。协议响应按 Inertia 表示设置 Vary，代理/CDN 必须保留此区别。`withViewData` 不会把值加入 Page props，也不能保证自定义模板值可不经转义直接插入。

分别检查状态码、`Location`、`Vary`、Page JSON 和 HTML。`AssertablePage` 只检查 Page 内容。示例的 `/missing`、表单重定向及普通 `/api/health` 展示不同响应路径。异常见[错误处理](errors.md)，版本刷新见[协议](../concepts/protocol.md)。
