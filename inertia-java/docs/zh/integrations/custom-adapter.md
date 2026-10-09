---
title: "自定义 HTTP 适配器"
description: "使用 core 实现预检、渲染、重定向、中止和写入，保留宿主文件与流式能力。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaRequest.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ProtocolPolicy.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
  - inertia-java/docs/examples/CoreApiExample.java
verification:
  - inertia-java/scripts/verify-maven-consumer.py
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustHttpParityTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
translation:
  locale: zh-CN
  canonicalId: integrations/custom-adapter
  source: integrations/custom-adapter.md
  sourceRevision: e313d256dc9862a44cf06e2dbec09fd0a290f4d35c994d9f152582e6a24ff652
---

# 自定义 HTTP 适配器

core 不依赖 Servlet。应用适配器可以集成其他 Java HTTP 栈，但必须保留协议顺序、请求所有权、取消和会话事务语义。

## 使用完整 core 示例

[CoreApiExample.java](../../examples/CoreApiExample.java) 是可编译的最小集成，展示 core 接线和结果，不代表完整浏览器应用或生产 HTTP server。实际传输还需添加宿主请求头/cookie 写入、可信代理地址重建、安全、资源服务和生命周期处理。

## 适配算法

1. 快照方法、绝对 URI、规范化请求头和服务端生成的安全请求 ID。nonce 必须来自可信应用状态；宿主决定 forwarded proxy 信任规则。
2. 控制器或查询前调用 `ProtocolPolicy.before(request, currentVersion)`。早期结果直接写入，不预留会话交付。
3. 使用对应用户存储创建新 context；无会话 Page 使用 null。
4. Page 在传输预算内等待 `ResponseRenderer.render(context, response)`，它负责解析、渲染、Page 策略收尾和完成交付。
5. 对控制器返回的 `HttpOutcome`，先用 `context.commitRedirect()` 提交重定向效果，再应用 `ProtocolPolicy.after` 并写入。Page 渲染后不要重复提交。
6. 超时或取消时，取消拥有的待执行任务，中止尚未提交 context。提供方操作也要有界。

保留多值响应头，例如分开的 Set-Cookie。`HttpOutcome` 是文本，不是流式抽象；保留宿主的文件/流响应。不要跨请求共享可变 context 或响应构建器。

## 测试失败顺序

过期版本应跳过业务；prop 失败应取消拥有的兄弟任务；渲染失败应恢复预留存储效果。会话完成后的写入失败不能回滚交付，也不能宣称网络恰好一次成功。

先比较 Page/HTTP 策略 fixture，再用实际宿主 server 验证序列化、取消和请求头。core 一致性不能证明代理或网络写入正确。Spring MVC 集成是额外边界的具体参考，不代表其他栈已受支持。见[所有权](../concepts/ownership.md)和[自定义存储](custom-session.md)。
