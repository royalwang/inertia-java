---
title: Inertia Java 中文文档
description: 使用 Java 路由、React 页面和可选 Node SSR 构建 Inertia 应用。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: home
  source: index.md
  sourceRevision: e867a2e5eb79f901674327ebb9158ce749eb1a15a441293247df1baf0c4165a0
sources:
- inertia-java/pom.xml
- inertia-java/README.md
verification:
- inertia-java/scripts/verify.mjs
- inertia-java/scripts/verify-maven-consumer.py
---

# Inertia Java 中文文档

使用 Java 路由、React 页面和可选的 Node 服务端渲染构建 Inertia 应用。Inertia Java 提供不依赖 Web 框架的协议核心、Servlet Spring MVC 集成，以及经过验证的 Spring Boot + React 示例。

本文档对应开发版本 **`0.1.0-SNAPSHOT`**。环境要求和构建步骤见[安装指南](getting-started/installation.md)。

## 从任务开始

| 目标 | 下一步 |
| --- | --- |
| 理解 Java、Node 与浏览器如何协作 | [项目概览](getting-started/overview.md) |
| 观察 SSR 首屏与浏览器导航 | [运行 React 示例](getting-started/quick-start.md) |
| 在仓库外创建独立应用 | [第一个 Spring 应用](getting-started/first-application.md) |
| 选择依赖并接入既有应用 | [安装](getting-started/installation.md)，再读 [API 指南（英文）](../api-guide.md) |
| 实现表单与校验 | [表单与校验](guide/forms-validation.md)，以及[认证（英文）](../guide/authentication.md)和 [CSRF（英文）](../guide/csrf.md) |
| 配置 SSR 与排查降级 | [SSR 设置](ssr/setup.md)、[错误原因](reference/errors.md)与 [SSR 排查（英文）](../troubleshooting/ssr-hydration.md) |
| 查默认值与 API 签名 | [配置参考](reference/configuration.md)、[核心 API 地图（英文）](../reference/core-api.md)和 [Javadoc（英文）](../reference/javadoc.md) |
| 理解交付与故障边界 | [请求生命周期](concepts/request-lifecycle.md)与[启动排错](troubleshooting/startup.md) |
| 发布、测试或贡献 | [部署（英文）](../deployment/build-release.md)、[测试（英文）](../testing/browser-tests.md)与[贡献（英文）](../community/contributing.md) |

## 库提供什么

- Java 21 协议核心：Page、props 选择、序列化、协议响应与事务性会话效果。
- Spring MVC 参数/返回值处理，以及具有截止时间和并发限制的 Boot 自动配置。
- HTTP SSR 与 Vite 集成，明确渲染预算和构建身份校验。
- 完整 React 示例：表单、校验、flash、partial/deferred、合并、once、scroll 及故障恢复。
- Page 断言、source/Javadoc 分类包，以及独立消费和部署验证工具。

应用负责路由、认证授权、业务数据、组件注册、root view 和部署策略。Inertia 请求头不能证明用户身份，optional/deferred 也不能代替权限校验。

## 文档语言

英文是规范正文。中文文档覆盖入门流程和部分应用指南，其他内容可阅读英文版本。尚未翻译的页面在中文侧栏中标有“英文”。

Inertia Java 使用 [Apache-2.0 许可证（英文）](../community/license.md)。
