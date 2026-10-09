---
title: 什么是 Inertia Java？
description: 理解访问流程并选择 Java 集成方式。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: getting-started/overview
  source: getting-started/overview.md
  sourceRevision: 93e51c16896ead2d8750eb9eabee5b06ebf8e16450ce5ffc2f049eb881619625
sources:
- inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
- inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaMvcConfigurer.java
verification:
- inertia-java/scripts/verify.mjs
- inertia-java/scripts/verify-maven-consumer.py
---

# 什么是 Inertia Java？

Inertia 把服务端路由连接到浏览器页面组件，无需为每个页面另建一套 REST API。Java 选择已注册的组件并提供 props；首次文档访问获得 HTML，后续 Inertia 访问获得 JSON Page，由官方客户端更新浏览器。

Inertia Java 实现这套服务端协议，参考本仓库的 Rust 实现，同时记录有意保留的差异，不假定所有适配器行为完全相同。

## 跟随一次访问

1. Spring 将 `GET /users` 路由到普通 `@Controller` 方法。
2. 方法返回 `InertiaResponse`，指定 `Users/Index` 并声明 props。
3. 渲染器选择需要的 props，在有界截止时间内解析。
4. 对首次文档请求，调用配置好的 Node 渲染器获得 HTML，再把 Page 和资源标签装入应用的 root view。
5. React 对已有 HTML 执行 hydration。之后官方 `Link` 导航携带 Inertia 请求头，获得 Page JSON，而不是另一份完整文档。

HTML 访问可以不启用 SSR。SSR 失败时，默认策略返回可由客户端渲染的外壳；使用 `requireSsr()` 的响应在无法提供 SSR 时返回安全的 503。JSON 访问不调用 Node。完整区别见[渲染模式（英文）](../../concepts/rendering.md)。

## 选择集成方式

| 宿主应用 | 起点 |
| --- | --- |
| Servlet Spring MVC + Boot | `inertia-spring-boot-starter` 和应用提供的 `InertiaConfig` bean |
| 不使用 Boot 的 Spring MVC | `inertia-spring-webmvc`，显式配置 configurer、executor 和会话 listener |
| 其他 Java HTTP 框架 | `inertia-core`，按 [API 指南中的生命周期（英文）](../../api-guide.md#core-integration-and-ownership)实现适配器 |

示例使用 React 与 Node；可复用核心不依赖 React、Spring、Node 或数据库。其他客户端/框架组合需要单独集成和验收，当前 React 证据不能代表所有 Inertia 客户端。

## 先理解责任边界

普通 `@Controller` 方法应同步、直接返回 `InertiaResponse` 或 `HttpOutcome`。`@RestController`、`@ResponseBody`、`ResponseEntity<InertiaResponse>` 以及异步包装的 Inertia 返回值不属于受支持的类型化入口，启动检查会拒绝不兼容声明。普通 REST、文件及流式响应继续交给 Spring。

在声明受保护数据和调度查询前完成授权。optional/deferred 控制何时加载数据，不决定谁可以读取。Node 是接收已解析 Page 数据的可信内部服务，不应把渲染入口直接暴露给不可信流量。

## 下一步

通过[快速上手](quick-start.md)运行仓库示例，或通过[安装](installation.md)为自己的应用选择依赖。修改锁定客户端或运行时前，先阅读[兼容版本（英文）](../../getting-started/compatibility.md)。
