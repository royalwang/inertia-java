---
title: 启动与依赖排错
description: 诊断缺失产物、配置和 handler 声明错误，并验证恢复。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: troubleshooting/startup
  source: troubleshooting/startup.md
  sourceRevision: a62a23cbc48f5911fcf8d400c6c531c9ea5918ea2173f2ca1bd710fc76ad61ad
sources:
- inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaProperties.java
- inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaHandlerValidator.java
- inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
- inertia-java/pom.xml
verification:
- inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaAutoConfigurationTest.java
- inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaHandlerValidatorTest.java
---

# 启动与依赖排错

从第一个可以采取行动的错误和精确输入版本开始。反复扩大超时无法修复缺失 class、非法 bean 或混用构建。

## 缺失 artifact 或 class

源码开发使用 JDK21，在 `inertia-java/` 通过 wrapper 构建 reactor，并保留根 Rust fixture 输入。独立应用教程先安装本地 snapshot，再构建无 reactor parent 的消费项目；`0.1.0-SNAPSHOT` 坐标不代表已经存在于 Maven Central。

结合 dependency tree 和 canonical POM 排查未解析/重复依赖，避免把当前 checkout 的 class 与旧 snapshot 分类包混用。[安装](../getting-started/installation.md)说明可支持的消费路径。

## Bean 与 handler 错误

明确提供 `InertiaConfig`，使用已知组件集合和 version。对照[配置默认值](../reference/configuration.md)：预算必须为正，response>=props timeout，executor max>=core，namespace 必须安全。`all-errors` 显式 false 与未设置不同。

handler 检查失败时，把受适配器管理的路由改成普通 controller 同步直接返回 `InertiaResponse`/`HttpOutcome`；或者将普通 REST/response-body 路由留在原框架路径。不应绕过 validator 让有歧义的 handler 启动。

## 前端与进程错误

使用 lockfile 和 Node>=22.12，client/SSR 一起重建后再读取生产 Vite receipt。检查端口是否空闲、frontend 目录是否正确、需要时是否使用绝对路径。开发 hot-server 必须是真实可信服务，生产需要经过校验的构建输出。

## 如何证明恢复

修正输入后重新启动，确认 Java 存活、初始 HTML、带当前版本的 JSON，以及启用 SSR 时真实同构建首屏。修改集成代码后运行相应启动/override/handler 合同。

报告问题时附上脱敏的首个错误、版本、最小配置和复现命令，遵循[支持说明（英文）](../../community/support.md)。不要把生产 cookie、token 或个人 Page 数据放入公开报告。
