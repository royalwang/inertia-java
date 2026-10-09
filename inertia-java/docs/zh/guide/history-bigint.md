---
title: "历史状态与精确整数"
description: "使用历史控制和 bigint，理解浏览器密钥清理及序列化边界。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PageCodec.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaResponse.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/DemoHistory.java
  - inertia-java/examples/spring-react/frontend/src/app.tsx
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/HistoryOverridesTest.java
  - inertia-java/examples/spring-react/frontend/e2e/history.spec.ts
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
translation:
  locale: zh-CN
  canonicalId: guide/history-bigint
  source: guide/history-bigint.md
  sourceRevision: 3a5e32a566e74b0f8fd84e9cc49c993af8d2cdaa1cb59f9b9fe588270d275b57
---

# 历史状态与精确整数

历史展示与整数序列化影响浏览器状态。必须配合匹配的官方客户端和 SSR 入口配置，并验证实际浏览器行为，而非只检查 JSON 标志。

## 历史策略

`InertiaConfig.encryptHistory` 设置应用默认值；context 覆盖作用于当前请求，response 覆盖优先于两者。context 的加密覆盖不通过重定向持久化。`clearHistory` 可以排队交付，response 也可以直接设置当前 Page 指令。

可选 `--inertia.demo-history-enabled=true` 示例在 `/demo-history/{mode}` 提供加密、明文、清理和 CSR Page。它展示表现行为，与认证分开。身份示例将新会话登录、账号加密历史和退出清理组合使用。

加密历史与 clear-history 指令不能授权后续请求，也不保证其他标签页已经清除 DOM。服务端权限和 private/no-store 缓存必须继续生效。跨标签通知只是便利机制，还受存储可用性限制。

## 保留精确标识符

JavaScript Number 不能精确表示超出 ±9007199254740991 的整数。发送更大的 ID 时，在配置或响应上启用 `preserveBigIntegers`。codec 输出携带十进制字符串的 `$bigint` 标记，锁定的客户端/服务端入口执行匹配的还原。

还原前不要先转换为 Number。显示 bigint 使用 `String(value)`，避免 Number 与 bigint 混合运算。示例 `9007199254740993L` 刻意超出安全范围，必须在 SSR、Page 传输和浏览器显示中保持精确。

该设置也影响嵌套值和 flash；响应设置覆盖配置。如果应用选择字符串 ID，应统一定义 DTO 契约，而非只在单个入口部分启用还原。

## 验证两种表示

检查精确 Page JSON 标记、禁用 JavaScript 的服务端 HTML、hydration 后文本和浏览器前进/后退状态。修改入口、root ID 或序列化时运行历史和 CSP/浏览器场景。JSON 标志本身不能证明加密或 hydration 成功。

SSR/CSR 区别见[渲染模型](../concepts/rendering.md)；修改锁定客户端或 bundle 格式之前，先阅读[构建版本](../concepts/versioning.md)。

## 上游参考

- [官方历史加密文档](https://inertiajs.com/docs/v3/security/history-encryption)
