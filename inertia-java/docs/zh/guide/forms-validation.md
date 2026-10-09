---
title: 表单与校验
description: 通过官方 Inertia 表单完成 Java 校验、错误袋和重定向反馈。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: guide/forms-validation
  source: guide/forms-validation.md
  sourceRevision: a3f7d1df18c6d91c39b7848816c8efddf256bc3165f144e922a60ea982466d82
sources:
- inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
- inertia-java/examples/spring-react/src/main/java/io/inertia/example/AdvancedDemo.java
- inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/ValidationBridge.java
- inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/JakartaValidationBridge.java
verification:
- inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
- inertia-java/examples/spring-react/frontend/e2e/advanced.spec.ts
- inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/ValidationBridgeTest.java
---

# 表单与校验

Inertia 表单提交到 Java 路由，在服务端校验后重定向到携带 errors 或 flash 的 Page，导航、表单状态和反馈都遵循官方客户端生命周期。

## 实现完整往返

1. React 用 `useForm` 声明初始值并提交到 controller 路由。
2. Java 先绑定并校验，再做业务操作。示例 `/users` 使用 Jakarta 校验 record 和 `BindingResult`。
3. 失败时通过 `ValidationBridge.errors(errors)` 复制安全错误，再调用 `context.backWithErrors(...)`；也可以先排队 errors，再选择固定重定向地址。
4. 成功时先完成已授权业务操作，再排队安全 flash 并重定向。
5. 前端显示表单错误与重定向 Page 的 flash。示例只验证输入，不持久化用户。

应用需要另外加入 Jakarta validation provider。`JakartaValidationBridge.errors(violations)` 可以桥接显式 validator 结果。桥接器复制路径/消息，不复制 rejected values。普通 REST 校验仍使用 Spring 原生语义，不自动转成 Inertia 重定向。

## 隔离多个表单

默认错误在适用时交付到请求的 `X-Inertia-Error-Bag`；显式命名袋使用 `context.withErrors(bag, errors)`。Advanced 示例用 `profile` 和 `team` 两个表单共享字段名、区分 error bag，分别获得反馈。

`ValidationErrors` 保留消息顺序。默认每字段只显示第一条；核心 `withAllErrors(true)` 或 Boot `inertia.all-errors` 覆盖可保留全部消息，此时前端需要处理数组。

## 安全与失败反馈

接入应用的 [CSRF 机制（英文）](../../guide/csrf.md)。被拒绝的写入不能自动重放。校验不等于授权，应在查询/修改受保护记录前检查当前用户权限。errors、flash 和日志都不应回显凭据或被拒绝的敏感值。

运行示例，依次提交空、非法和合法名称，检查重定向状态、errors、processing 状态和一次性 flash。在 `/advanced` 交替提交两份表单，确认它们的局部错误互不污染。浏览器测试覆盖实际显示状态，bridge 测试覆盖路径/消息复制。[flash 交付（英文）](../../guide/flash-session.md)说明失败与并发边界。
