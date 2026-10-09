---
title: "独立 Spring MVC"
description: "显式注册 configurer、validator、会话 mutex listener 和同步类型化 handler。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaMvcConfigurer.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaHandlerValidator.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
verification:
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaRequestLifecycleTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaHandlerValidatorTest.java
translation:
  locale: zh-CN
  canonicalId: integrations/spring-mvc
  source: integrations/spring-mvc.md
  sourceRevision: 0f1f92de5780fd5133c26d13eaa7bc1055887e3c7e513e53e59592e196e4db6d
---

# 独立 Spring MVC

不使用 Boot 时，显式注册 Page 基础设施和 Servlet 生命周期。沿用 starter 的类型化 handler 规则，普通 REST 和传输 handler 保持原生 MVC 行为。

## 组装统一依赖图

1. 添加相同库版本的 `inertia-spring-webmvc`、所需 core 及可选 SSR/Vite 依赖，Spring/Jackson 版本由宿主管理。
2. 构造应用 `InertiaConfig`、`PageCodec`、有界 executor、`PropsResolver` 和 `ResponseRenderer`。
3. 将 `InertiaMvcConfigurer` 注册为 `WebMvcConfigurer` bean，使用接受 config、renderer、响应截止时间、可选错误页面 resolver、namespace 和共享 codec 的构造器。
4. 使用宿主 mapping provider 注册 `InertiaHandlerValidator`，启动时检查 controller/advice 契约。
5. 向 Servlet 容器注册 Spring 的 `HttpSessionMutexListener`，应用停止时关闭应用拥有的资源。

接受 codec 的构造器使 Page 序列化与请求/advice flash/error 效果一致。旧的短构造器仍创建原来的默认 codec，不适用于明确自定义序列化的应用。

## 控制器契约

普通控制器同步、无包装返回 `InertiaResponse` 或 `HttpOutcome`。通过 handler 参数注入 `InertiaRequest` 和 `InertiaContext`。不兼容的 `@ResponseBody`、REST 控制器及异步/泛型 Page 包装会被拒绝。下载、上传、SSE 和普通 API 保持原框架路径。

configurer 负责业务前预检、请求 context 创建、返回值处理、截止时间与类型化异常集成。不要在控制器里重复提交或渲染同一 context。排队效果的自定义安全 handler 必须与 MVC 使用相同会话 namespace。

## 验证宿主集成

启动实际 Servlet 应用，检查 HTML、版本化 JSON、控制器工作前的过期版本刷新、修改操作重定向、命名校验交付以及失败 Page 恢复。除 Page 内容外，还要断言状态和响应头。覆盖普通 REST 与流式路由，确认类型化适配器不拦截它们。

MVC 生命周期、validator 和 session 测试规定这些行为；消费方仍需验证自身 Servlet 注册、安全及代理配置。其他 HTTP 栈见[自定义适配器](custom-adapter.md)，advice 优先级见[错误处理](../guide/errors.md)。
