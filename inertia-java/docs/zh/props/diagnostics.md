---
title: "Prop 错误与覆盖诊断"
description: "追踪定义和解析失败，在不泄露值的前提下观察覆盖来源。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Props.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropDefinitionException.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/PropDefinitionDiagnosticTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/ObservationContractTest.java
translation:
  locale: zh-CN
  canonicalId: props/diagnostics
  source: props/diagnostics.md
  sourceRevision: c05365abf103eebed29b83b85d8d48e6722d984ae47f5e7e0d54e0facd520ae6
---

# Prop 错误与覆盖诊断

使用 schema 诊断和有界观测，解释 prop 为什么被替换、拒绝、跳过或失败。诊断值应与公开 Page 数据及高基数指标标签分开。

## 定义错误

`PropDefinitionException` 是带 kind、路径和定义来源访问器的 `IllegalArgumentException`。无效路径语法、深度及父子冲突在 supplier 执行前失败。应修复 schema，重试相同定义无法解决问题。

允许精确 key 替换。`Props.overrides()` 报告路径、之前来源和替换来源，不包含值。`Props.from(...)` 标记显式组合；renderer 标记内部、配置、请求和 Page 来源。重复 builder put 保持最后定义胜出，跨来源 overlay 报告组合情况。

特殊 `errors_override` 诊断提示应用定义替换了内置校验。如果表单依赖它，应保留 `errors`。

## 运行时观测

Observer 可以接收 prop 规划、解析与失败事件。覆盖事件原因是 `prop_override` / `errors_override`，定义失败归类为 `prop_definition`。覆盖观测可能发生在 partial 过滤排除 key 之前，它不是额外 HTTP 请求。

路径与来源属于开发 schema 信息，不自动适合公开展示。如果 key 包含敏感应用信息，不要放入公开错误或无差别日志。普通 observer 事件避免载荷和 schema 路径。

## 按顺序诊断

1. 查看安全的定义/来源信息，确认冲突或覆盖。
2. 判断缺失值是查询失败之前，先检查访问选择和加载标志。
3. 对已选择工作检查 executor、deadline 和 overload 观测。
4. 区分已 rescue 的 deferred 失败与未 rescue 的整个 Page 失败。
5. 用服务端生成的请求 ID 关联日志，不把 ID 用作指标标签。

测试验证胜出 supplier 执行、查询前 schema 失败和隐私边界。接线见[可观测性](../integrations/observability.md)；因过载调整容量前，先阅读[异步预算](async-concurrency.md)。
