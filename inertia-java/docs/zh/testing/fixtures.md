---
title: "Rust 兼容 fixture"
description: "通过实际 Rust 生产器维护外部预期与实时 TTL，保留来源。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustParityTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustHttpParityTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/OnceTtlContractTest.java
  - inertia-java/pom.xml
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/AdvancedPropsTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CancellationContractTest.java
translation:
  locale: zh-CN
  canonicalId: testing/fixtures
  source: testing/fixtures.md
  sourceRevision: 47e1532eeac4e84247c96f647c6aca278154652018f935652aa7bce4c7667323
---

# Rust 兼容 fixture

使用已检查的 Rust 生成预期作为外部行为基准。同时修改 Java 实现与预期结果，可能掩盖协议回归。

## Fixture 层次

Java 测试消费 37 个 Page fixture 和 45 个 HTTP 场景。其中 41 个 HTTP 场景直接遵循 Rust 基准，四个记录有意 Java 差异；另有九个实时 once-TTL 场景验证时间行为，不只依赖静态 JSON。

`RustParityTest` 比较解析后 Page 结构，`RustHttpParityTest` 覆盖结果、响应头和导航策略，`OnceTtlContractTest` 验证到期边界。父构建还消费根 Rust fixture 输入，只复制 Java 子目录不足以从源码干净构建独立 reactor。发布的 Maven 消费属于另一种制品消费边界。

## 明确改变基准

修改兼容性前，先确定上游行为与应用预期契约。通过实际生产器重新生成或更新上游 fixture，保留来源并解释 Java 特有差异。不要用当前 Java 输出覆盖预期 JSON，只为让测试变绿。

四项有意差异及理由记录在[兼容文档](../getting-started/compatibility.md)。点路径声明冲突、取消/rescue 和会话所有权还有 Java 特有契约测试，仅线协议 fixture 一致不能覆盖这些行为。

## 验证与审查

在 `inertia-java/` 运行聚合测试，包含资源准备及相关契约。同时审查预期 Page/HTTP 输出与源码。新增客户端元数据能力时，补充或扩展真实浏览器交互；正确元数据数组不等于官方客户端合并/加载正确。

这些 fixture 证明已记录 Rust 实现对应的场景，不承诺未来客户端或所有 Rust 应用配置普遍兼容。兼容声明应与 client/JDK/Boot 矩阵和发布说明一起版本化。
