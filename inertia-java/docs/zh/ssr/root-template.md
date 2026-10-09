---
title: "根模板与 CSP"
description: "插入一次可信 SSR body，转义视图数据，传递服务端 nonce，并统一 root ID。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/RootView.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PageCodec.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaMvcConfigurer.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/CspFilter.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CspNonceTest.java
  - inertia-java/examples/spring-react/frontend/scripts/verify-csp.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-custom-root.mjs
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
translation:
  locale: zh-CN
  canonicalId: ssr/root-template
  source: ssr/root-template.md
  sourceRevision: dc97ab2a85e0041e0d7c0a3e991ff102495f301f133a91dbf4beff3717b795a9
---

# 根模板与 CSP

应用根视图把资源标签、可信渲染 head/body 和 Page 数据组合成文档。这是序列化与安全边界，不能把任意用户字符串直接拼接进 HTML。

## 根视图契约

`RootView.View` 提供 Page、SSR head/body、是否使用 SSR、仅模板数据及可选的已校验 nonce。当前锁定渲染契约中，body 已包含预期根元素和 Page script，应只插入一次；重复根元素或 Page 载荷会破坏 hydration，甚至泄露不一致状态。

`response.withViewData(...)` 提供模板数据，不是 Page props。普通模板值应按 HTML 上下文转义。自定义 Page script 边界使用 `PageCodec.htmlJson`，它转义危险 HTML/script 分隔符和行分隔符。普通对象 JSON 不自动适合嵌入 script 元素。

统一 `InertiaConfig.rootId`、网关预期 root ID、Node 的 `SSR_ROOT_ID` 和浏览器入口 root 查找。示例根 meta 标签告知浏览器使用哪个 ID；root ID 有受限安全语法。

## 提供 CSP nonce

Spring MVC 的可信 filter 可以在适配器创建快照前设置 `InertiaMvcConfigurer.CSP_NONCE_ATTRIBUTE`。同一请求 nonce 传入根视图、Page script 和 Vite 标签；浏览器入口将它交给官方 Inertia app setup，用于客户端创建的元素。

示例 `CspFilter` 生成新的随机 nonce，并设置可选策略。其开发许可针对特定本地 origin，不是通用生产策略。可信来源、样式策略、HTTPS 和代理行为由宿主应用确定。不要接受任意输入请求头中的 nonce。

## 验证边界

在 SSR/CSR 中测试恶意字符串，确认目标 script 使用同一 nonce，非可信内联 script 被阻止。只有响应头不能证明应用仍能 hydration。修改根布局、入口或网关检查时运行 CSP/custom-root 契约。

最小 `RootView` 适合 API 示例，但不加入客户端资源标签。完整浏览器配置见 [SSR 设置](setup.md)，序列化修改见[精确整数](../guide/history-bigint.md)。

## 上游参考

- [W3C CSP3 工作草案](https://www.w3.org/TR/CSP3/#strict-dynamic-usage)
