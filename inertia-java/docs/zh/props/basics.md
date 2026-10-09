---
title: "值、嵌套 props 与路径"
description: "定义可序列化值和嵌套路径，区分省略与 null，并拒绝路径冲突。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Props.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CoreContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustParityTest.java
translation:
  locale: zh-CN
  canonicalId: props/basics
  source: props/basics.md
  sourceRevision: ab7e449b540495cf51055bbe4d2753d1510a7a9f93db4dc13bd087e32ade8ab0
---

# 值、嵌套 props 与路径

`Props` 是带名称的有序定义集合，由明确可序列化的应用 DTO 和 `Prop` 来源组成。解析定义后才产生 Page props；构建器本身不是 JSON Page。

## 定义值

`Props.builder().put(path, value).build()` 将普通值包装为 `Prop.value`。标量、列表、map 和 DTO 使用配置的 Page codec。传入另一个 `Props` 会创建具有独立加载和选择行为的嵌套定义。

点路径生成嵌套输出，例如 `auth.user.name`。路径不能包含空白或空段，深度最多 32；构建器拒绝无效路径。同时定义父路径和后代会冲突，即使其中一个之后会被访问选择过滤。

| 定义 | 含义 |
| --- | --- |
| 普通 map/DTO 值 | 一个可序列化值 |
| 嵌套 `Props` | 递归规划子 prop 定义 |
| `Prop.lazy(...)` | 来源延迟执行，但加载策略为 eager |
| `Prop.optional(...)` | 完整访问省略来源 |

选择作用于定义和路径，不是对任意 DTO 所有字段执行带授权的通用 JSON 投影。定义前先构造安全 DTO。不要把完整私有实体放进 map，再期望客户端 `only` 请求保护其他字段。

## 顺序与组合

精确重复 key 使用最后一个定义，保留首次声明位置。跨来源组合也采用相同胜出规则，只有胜出的来源执行。嵌套对象的客户端合并是另一项协议能力，见[合并](merging.md)。

需要让选择跳过昂贵工作时，应把工作放入来源回调。如果调用 `put` 之前就执行查询，即使之后省略定义，成本也已经发生。

## 检查输出

断言解析值、字段省略与 null 的区别、嵌套路径。在加入查询工作之前测试父子路径冲突。`CoreApiExample.java` 是可完整编译的集成示例；core 契约和一致性测试覆盖嵌套定义与精确 Page 树。继续阅读[加载策略](loading.md)和[局部重载](partial-reloads.md)。
