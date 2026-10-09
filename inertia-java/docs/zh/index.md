---
title: Inertia Java 中文文档
description: 使用 Java 路由、React 页面和可选 Node SSR 构建 Inertia 应用。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: home
  source: index.md
  sourceRevision: 6d7da52d4372b23a53abed8fbfa4b5a8b879b1153083c880bfbb4d646e3b174b
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
| 理解项目架构 | [什么是 Inertia Java？](getting-started/overview.md) |
| 观察 SSR 与浏览器导航 | [运行 React 示例](getting-started/quick-start.md) |
| 在仓库外创建应用 | [第一个 Spring 应用](getting-started/first-application.md) |
| 接入既有 Java 应用 | [安装](getting-started/installation.md)，再读 [API 指南](api-guide.md) |
| 构建表单并保护写操作 | [表单与校验](guide/forms-validation.md)、[认证](guide/authentication.md)与 [CSRF](guide/csrf.md) |
| 控制加载与客户端状态 | [加载策略](props/loading.md)、[局部访问](props/partial-reloads.md)、[合并](props/merging.md)与 [once](props/once.md) |
| 配置渲染与诊断 | [SSR 设置](ssr/setup.md)、[降级](ssr/fallback.md)与[可观测性](integrations/observability.md) |
| 查找默认值和签名 | [配置参考](reference/configuration.md)、[API 地图](reference/core-api.md)与 [Javadoc](reference/javadoc.md) |
| 发布应用或诊断问题 | [部署](deployment/build-release.md)、[测试](testing/browser-tests.md)与[故障排查](troubleshooting/startup.md) |
| 参与贡献或获取帮助 | [贡献](community/contributing.md)、[支持](community/support.md)与[安全报告](community/security.md) |
| 理解生命周期与所有权规则 | [请求生命周期](concepts/request-lifecycle.md)，再读[所有权](concepts/ownership.md) |

## 库提供什么

- Java 21 协议核心：Page、props 选择、序列化、协议响应与事务性会话效果。
- Spring MVC 参数/返回值处理，以及具有截止时间和并发限制的 Boot 自动配置。
- HTTP SSR 与 Vite 集成，明确渲染预算和构建身份校验。
- 完整 React 示例：表单、校验、flash、partial/deferred、合并、once、scroll 及故障恢复。
- Page 断言、source/Javadoc 分类包，以及独立消费和部署验证工具。

应用负责路由、认证授权、业务数据、组件注册、root view 和部署策略。Inertia 请求头不能证明用户身份，optional/deferred 也不能代替权限校验。

## 文档语言

英文是规范正文。中文文档覆盖完整指南库，包括入门、应用功能、API 指南、部署及社区说明。可以通过页面上方的对应链接切换语言；API 名称、命令和可执行示例在两种语言中保持一致。

Inertia Java 使用 [Apache-2.0 许可证](community/license.md)。
