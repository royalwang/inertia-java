---
title: "Page 断言"
description: "使用 JSON Pointer 检查 Page，并单独验证 HTTP 状态、响应头和浏览器行为。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-testing/src/main/java/io/inertia/testing/AssertablePage.java
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/MvcContractTest.java
verification:
  - inertia-java/scripts/verify-maven-consumer.py
translation:
  locale: zh-CN
  canonicalId: testing/assertable-page
  source: testing/assertable-page.md
  sourceRevision: a292eaac31fce74540fcabb959cbb9bb2430e7d5ab5fb75422ccf128bb42b640
---

# Page 断言

用 `inertia-testing` 的 `AssertablePage` 检查服务端适配器测试的 Page 载荷。HTTP 状态、响应头和会话转换还需 HTTP 测试框架断言。

## 解析响应正文

`AssertablePage.fromBody(body)` 接受原始 Page JSON 或标准 HTML 文档中使用双引号的 `data-page` script。链式 `component(expected)`、`equals(jsonPointer, expected)` 和 `missing(jsonPointer)` 在契约不符时抛出 `AssertionError`。JSON Pointer 使用 `/props/user/name`，不是 prop 声明的点路径。

辅助器用 `PageCodec` 值比较，预期 collection/map 转成 JSON 结构。`data()` 返回深复制，修改它不会改变内部断言状态。

## 选择有意义的断言

断言组件和用户可见 prop。局部请求同时断言包含与省略的提供方输出。会话交付比较首次 Page 和随后刷新，单次响应不能证明一次性消费。版本冲突先断言 409 和 location header，再考虑解析 Page body。

不要用此辅助器推断 hydration、客户端合并、CSRF 或 cookie 行为。HTML 提取识别标准 script 形状，任意自定义根模板可能需要 DOM parser 或直接 Page JSON 断言。

## 编译与运行

规范 API 指南包含可编译 core/Spring 示例与独立消费验证。现有 MVC 测试将辅助器与 MockMvc 状态/响应头检查结合。在 `inertia-java/` 运行 `./mvnw --batch-mode test` 执行聚合单元/集成契约，也可用 Maven 标准选择器选择受影响测试，并允许其他模块没有该测试。

JSON 断言通过只是一层证据。实际官方客户端导航见[浏览器验收](browser-tests.md)，兼容基准见 [fixture](fixtures.md)。
