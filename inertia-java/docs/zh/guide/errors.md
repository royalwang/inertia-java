---
title: "异常处理与错误页面"
description: "理解安全错误页面、类型化 advice、处理顺序与递归限制。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaExceptionResolver.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaMvcConfigurer.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/DemoFailures.java
verification:
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaExceptionResolverTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
translation:
  locale: zh-CN
  canonicalId: guide/errors
  source: guide/errors.md
  sourceRevision: 5dffad354cd264b4f66b520e3f8863dbbe96b30636fad33a4d3c3a44fca8834c
---

# 异常处理与错误页面

Page 请求失败时，显示安全的已注册组件或明确重定向。异常详情应留在授权诊断中，不要将原始异常文本放入 props。

## 选择应用处理方式

普通应用 `@ExceptionHandler` 方法保留优先级。类型化 Page 处理要求普通 controller/advice 同步、无包装地返回 `InertiaResponse`，且不使用 `@ResponseBody`。REST/`ResponseEntity` advice 保持 Spring 原生语义。

| advice 结果 | context 行为 |
| --- | --- |
| 类型化 Page | 中止原 context，创建新的无会话错误 context |
| 类型化 `HttpOutcome` | 中止原 context，创建保留原 store/namespace 的新 context，用于 advice 自己的重定向效果 |
| 普通 REST 结果 | Spring 原生行为 |

错误 context 不复制失败请求的共享值或待提交效果。原 Page 预留的存储交付会恢复，留给后续成功的业务 Page。类型化重定向 advice 可以排队自己的安全反馈，但不能替换已失效会话，也不能静默重新绑定已移除的 namespace 状态。

支持局部、全局和继承的泛型类型化 handler。全局 advice 应限定到目标 Page 控制器，让无关 API 错误保持自己的契约。

## 提供最终安全 Page

`InertiaErrorPage` 根据请求和状态解析已注册错误组件。示例提供仅包含状态的 `Error` Page。应明确选择缓存与 SSR 策略：若错误 Page 要求使用已经失败的渲染器，它无法独立实现成功恢复。

应用 advice 失败后，库提供有界的最终错误路径。该安全 Page 再次失败时，不会无限递归渲染。Props 失败仍是失败，不会静默变成正常 SSR 降级。

## 验证恢复

覆盖控制器直接异常、异步 prop 失败、advice Page 失败和带排队反馈的重定向 advice。HTTP 状态和响应头应与 Page 内容分开检查。再导航到正常路由，确认原存储交付保留，失败请求的效果没有泄露。

可选示例故障路由和 MVC 测试覆盖不同边界。浏览器测试验证安全错误 Page 可以 hydration 或挂载，并通过导航恢复。渲染器故障策略见 [SSR 降级](../ssr/fallback.md)，有界诊断见[可观测性](../integrations/observability.md)。

## 上游参考

- [Spring MVC 错误状态语义](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/support/DefaultHandlerExceptionResolver.html)
