---
title: "Once props、到期与刷新"
description: "配置复用 key、TTL 和显式刷新，保持授权独立。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/OnceTtlContractTest.java
  - inertia-java/compatibility/verify-once-ttl.mjs
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
translation:
  locale: zh-CN
  canonicalId: props/once
  source: props/once.md
  sourceRevision: 69bd2df7db6c004c7466ff9dc196659e0f4b0422e0f712fd0f8f4b49ff0fe3e0
---

# Once props、到期与刷新

Once props 让官方客户端复用此前加载的值，不创建服务端缓存，也不会免除下一次请求的授权。

## 声明复用

`.once()` 默认使用 prop 路径作为 key，`.onceAs(key)` 指定非空 key。`.until(Duration)` 增加非负 TTL；没有 TTL 时协议不声明到期。即使客户端表示已有 key，`.fresh()` 仍强制提供值。

服务端从 `X-Inertia-Except-Once-Props` 读取已加载 key。完整 Inertia 访问可以省略已加载 once 值，显式匹配的局部选择可以再次获取。首次文档访问仍需数据，以渲染完整页面。

## 选择 key 作用域

在每个需要它的页面声明共享 once prop。Page 局部定义不会自动变成全局共享。key 应代表同一个已授权值，不要将某个身份的 key 用作应用范围权限缓存。

示例共享目录使用 `feed-catalog` key 和 60 秒 TTL。加载计数器让浏览器测试区分复用和重新查询；刷新操作明确请求新值。

## 时间与失效

到期元数据使用 resolver 的服务端 Clock。Java/Rust 语义验证记录表示差异和实际到期边界，不能从变化的时间戳 fixture 推断跨适配器相等。官方客户端决定已持有值何时到期并重新请求。

授权、身份或数据契约改变时，可能需要显式 fresh 请求、历史清理或新的构建/导航策略。TTL 不是安全边界。负时长或空自定义 key 会在定义校验时失败。

## 验证复用与刷新

检查首次交付、重复访问、显式局部刷新、fresh 覆盖和精确客户端到期边界，同时观察回调次数与浏览器保留值。固定时钟 Java 契约和实时 Rust 语义检查覆盖服务端元数据；浏览器场景覆盖锁定客户端的到期判断。定义位置见[共享数据](../guide/shared-data.md)，构建改变见[版本](../concepts/versioning.md)。
