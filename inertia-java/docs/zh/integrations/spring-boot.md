---
title: "Spring Boot starter"
description: "配置应用 InertiaConfig，理解自动配置、可选指标和 bean 替换。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaAutoConfiguration.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaProperties.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaMetricsAutoConfiguration.java
verification:
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaAutoConfigurationTest.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaOverridesTest.java
translation:
  locale: zh-CN
  canonicalId: integrations/spring-boot
  source: integrations/spring-boot.md
  sourceRevision: 11659e32c2bfb544d6b731a61bf01cbf5ef33853ffe3942c0bbd050f9045086a
---

# Spring Boot starter

Servlet Spring MVC 应用可使用 starter，并提供自己的 Page 配置。它简化基础设施接线，不创建前端、不选择页面，也不启动 Node。

## 安装与配置

按[安装](../getting-started/installation.md)流程添加 `io.inertia:inertia-spring-boot-starter:0.1.0-SNAPSHOT`，使用 Boot 3.5.7 BOM。定义 `InertiaConfig` bean，设置组件注册表、根视图、资源和 SSR 策略。类型化 Page handler 保持普通 `@Controller` 方法。

默认依赖图提供 Page codec、有界 executor、props resolver、renderer、MVC configurer、handler validator 和 Servlet 会话 mutex listener。自动配置以 Servlet 应用为条件，不是 WebFlux 适配器。

## 限制执行

| 正式属性（`inertia.`） | 默认值 |
| --- | --- |
| `props-timeout` / `response-timeout` | 3s / 5s |
| `props-concurrency` | 8 |
| `executor-core-size` / `executor-max-size` | 8 / 32 |
| `executor-queue-capacity` | 256 |
| `all-errors` | 未设置，保留 config 值 |
| `session-namespace` | `default` |

超时与限制必须为正，max 线程数不小于 core，响应超时不小于 props 超时。无效配置导致启动失败。组件、root ID、Node 端点和开发资源路径通过应用 bean 配置；示例使用相似前缀的 JVM 标志，不是 `InertiaProperties` 的其他通用属性。

## 一致地替换默认值

名为 `inertiaPropsExecutor` 的 `ExecutorService` bean 替换默认 executor。用户的 `PageCodec`、`PropsResolver`、`ResponseRenderer` 和 `InertiaMvcConfigurer` bean 分别替换对应默认值。多个 codec 候选需要一个 primary，库不会任意选择。

私有 Page codec 复制传入 ObjectMapper，不替换 Spring REST message converter。默认 resolver/renderer/MVC/advice 共用 codec。替换部分依赖图时，需自行维持 codec/config/deadline/observer 一致性；属性不会自动重新配置应用拥有的实例。

默认 executor 具有受管理的关闭过程；应用替换实例需管理自身生命周期。可选 Micrometer 接线需要应用提供 registry，不安装 Actuator，也不公开 metrics 端点。

验证启动拒绝、替换 bean 优先级、codec 一致性以及正常 Page/REST 请求。Boot 测试覆盖这些边界。继续阅读[可观测性](observability.md)，或在非 Boot 宿主中使用[独立 MVC](spring-mvc.md)。
