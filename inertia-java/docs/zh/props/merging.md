---
title: "追加、前插与深度合并"
description: "结合真实客户端状态使用 matchOn，更新标识匹配项并重置合并状态。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/AdvancedDemo.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/AdvancedPropsTest.java
  - inertia-java/examples/spring-react/frontend/e2e/advanced.spec.ts
translation:
  locale: zh-CN
  canonicalId: props/merging
  source: props/merging.md
  sourceRevision: 373571973df1c40d217075fef1dbbc1e94fed364307a2e8fe8c799d143cb3c7b
---

# 追加、前插与深度合并

合并 API 描述官方客户端如何将匹配的局部响应与当前状态组合。Java 发送元数据，不在服务端保留浏览器合并后的集合。

## 选择指令

| API | 意图 |
| --- | --- |
| `.merge()` | 追加根集合 |
| `.prepend()` | 前插根集合 |
| `.deepMerge()` | 递归合并对象状态 |
| `.appendAt(path)` / `.prependAt(path)` | 合并内部路径的集合 |
| `.matchOn(path)` | 通过相对标识路径匹配集合项 |

路径相对于当前 prop。必须先定义合并选项再调用 `matchOn`，否则失败。修饰符不总是叠加，选择另一个模式可能替换之前选项。不要叠加不兼容的根、deep 和路径模式，并假定它们都保留。

## 观察增量

Advanced 示例将 `profile` 声明为 deep merge，并使用 `matchOn("members.id")`。后续局部响应改变主题、更新成员 2、新增成员 3，不重复未修改字段。客户端保留姓名和语言，并避免重复匹配成员。

使用 reset 操作请求替换。`X-Inertia-Reset` 抑制重置路径的合并元数据。普通完整访问应包含完整一致快照，不能依赖另一个浏览器此前的增量历史。

## 明确处理身份与删除

合并是状态组合，不是授权、持久化或删除协议。标识必须稳定并具有正确作用域。记录需要消失或权限改变时，应选择适当的替换、reset 或导航流程，不能认为省略就会从客户端保留集合中删除。

验证重复增量、标识匹配、reset 和新文档访问，同时断言真实浏览器状态与服务端元数据。core 测试覆盖选项及元数据规则，Advanced 浏览器测试证明嵌套 ID 更新不产生重复项。方向分页元数据见[滚动](scroll.md)，选择规则见[局部重载](partial-reloads.md)。
