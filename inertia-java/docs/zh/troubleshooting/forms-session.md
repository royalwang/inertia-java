---
title: "Flash 与校验错误丢失"
description: "排查 namespace、bag、session、CSRF、交付恢复和重复效果。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ErrorBags.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/BrowserCsrfRecoveryTest.java
translation:
  locale: zh-CN
  canonicalId: troubleshooting/forms-session
  source: troubleshooting/forms-session.md
  sourceRevision: b26c73395b258386bc1290b4a671cf17625c6b9f3b97871c66f21c956f6a7be7
---

# Flash 与校验错误丢失

用相同 identity/store/namespace 追踪重定向及随后 Page 交付。Flash/errors 是请求拥有的会话效果，不是持久 prop 缓存。

## 检查请求路径

确认修改通过认证/CSRF 并进入校验。检查真实重定向状态、location 和下一请求 cookie。CSRF 拒绝不是校验 bag，仅 JSON 断言不能证明 cookie/token 轮换。

渲染前排队 errors，使用预期默认或命名 bag。检查 `X-Inertia-Error-Bag` 和 `all-errors` 展示。库保留有序消息，bridge 排除被拒绝值；UI 改为读取其他 bag，可能看起来像服务端错误丢失。

## 检查所有权与消费

带待提交效果的重定向需要会话，两次请求 namespace 必须一致。成功 Page 消费预留交付，刷新后不再显示一次性 flash。Prefetch 和完成前渲染失败不能消费其他请求预留快照。core 在适配器写入前完成交付，后续写入失败不能恢复已完成交付，不能称为浏览器恰好一次接收。

不要跨请求共享 `InertiaContext`，也不要完成后写入。同请求重复 flash key 有意失败。advice 使身份失效时，不要把原预留效果转移到新会话。自定义后端需原子预留/完成/中止，而非盲目读取删除。

## 恢复与验证

修正 bag/namespace/cookie/所有权来源，再运行修改 → 重定向 → Page → 刷新。修改存储时覆盖重叠请求和交付失败。未知存储结果需要对账，重放修改可能重复业务写入。

见 [flash/会话交付](../guide/flash-session.md)、[表单](../guide/forms-validation.md)、[自定义会话存储](../integrations/custom-session.md)及 [Spring 测试](../testing/spring-tests.md)。
