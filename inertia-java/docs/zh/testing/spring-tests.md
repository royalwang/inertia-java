---
title: "Spring 集成测试"
description: "验证类型化控制器、校验/advice、flash 恢复、普通路由和 bean 覆盖。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/MvcContractTest.java
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/MvcOutcomeAdviceContractTest.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaHandlerValidator.java
verification:
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaOverridesTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/HttpSessionStoreTest.java
translation:
  locale: zh-CN
  canonicalId: testing/spring-tests
  source: testing/spring-tests.md
  sourceRevision: d98cddf826b72ff5821340a64ba7e792fa6e2f1b31615b46d146c7cfb9e90a7a
---

# Spring 集成测试

Spring 测试验证适配器注册、HTTP 语义和会话/错误所有权。浏览器行为需要独立真实客户端检查。

## 选择验证边界

| 修改 | 对应契约 |
| --- | --- |
| 默认/覆盖 bean 或预算 | `InertiaAutoConfigurationTest`, `InertiaOverridesTest` |
| handler 声明 | `InertiaHandlerValidatorTest` |
| MVC Page/重定向 | `MvcContractTest`, `InertiaRequestLifecycleTest` |
| 交付预留/中止/身份 | `HttpSessionStoreTest`, `MvcSessionFailureTest` |
| 异常 advice | `MvcAdviceContractTest`, `MvcOutcomeAdviceContractTest` |
| 校验展示 | `ValidationBridgeTest`, `JakartaValidationBridgeTest`, `MvcAllErrorsTest` |
| Observer/指标 | `MvcObservationTest`, `InertiaMetricsTest` |

在 `inertia-java/` 执行定向示例：`./mvnw --batch-mode -pl examples/spring-react -am -Dtest=MvcContractTest -Dsurefire.failIfNoSpecifiedTests=false test`。修改跨越模块契约时运行更广的聚合检查，不能将一个类视为整套兼容测试。

## 断言真实生命周期

快照原会话，发送请求，同时断言状态、表示、响应头和 Page 数据。对 flash/errors 验证重定向合并、成功 Page 消费及完成前失败恢复。单独确认 core 完成后的传输写入失败不能回滚交付。advice 使会话失效时，断言效果仍属于原身份/store，没有复制进新会话。

启动时检查被拒绝 handler 形状，不等意外运行时处理。确认普通 REST/下载/SSE 仍走 Spring 原生路径。覆盖 bean 时，断言实际使用覆盖实例，缺少前提的可选设施仍不出现。

## 剩余验证范围

MockMvc 不启动生产 Node 渲染器，也不执行 React。CSRF token 轮换、官方客户端表单、merge/once/scroll 和退出/历史需在[浏览器矩阵](browser-tests.md)测试。真实代理/TLS、分布式存储和宿主服务需要各自集成环境。

测试应关联行为和失败反馈。纯文档编辑可复用未改变的运行时证据；修改 Java 片段或配置需要编译和对应契约。
