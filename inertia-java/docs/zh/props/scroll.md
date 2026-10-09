---
title: "无限滚动与分页元数据"
description: "适配 ScrollPage，并使 pageName、方向与重置行为匹配客户端集合。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ScrollPage.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/examples/spring-react/frontend/src/pages/Feed.tsx
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustParityTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/AdvancedPropsTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
translation:
  locale: zh-CN
  canonicalId: props/scroll
  source: props/scroll.md
  sourceRevision: 138c3a8917413f0908f4450b65c668172cb6048d8f3e4a0628614f8031fafe1a
---

# 无限滚动与分页元数据

无限滚动组合应用分页与客户端合并指令。库描述一页数据，不查询数据库，也不决定用户可访问哪些记录。

## 构造一页

`ScrollPage` 包含数据、分页查询参数名称、上一页/下一页/当前页标识和包装 key。便利构造器使用 `page` 与 `data`。边界处 previous/next 可以为 null；标识可以是数字或游标。包装 key 必须非空，且不能包含点。

用 `Prop.scroll(page)` 包装，或通过 `Prop.scrollWith(task)` 计算。使用相对 `matchOn` 路径，例如 `data.id`，稳定识别集合项。scroll 来源返回非 ScrollPage 时会失败，不会伪造分页元数据。

此 Java DTO 不承诺 Rust length-aware paginator 的全部字段或集合辅助功能。应用所需的 total/count/link DTO 应明确构造。

## 观察 Feed

示例 `/feed?page=1` 返回三行和下一页元数据。官方滚动组件可以追加后续页面或前插更早页面，合并意图来自请求。reset 替换已持有集合，并抑制此前合并行为。

页码和游标校验由应用负责。示例将 1–3 范围之外的页码拒绝为安全 400 Error Page。真实游标分页每次获取都要授权并保持稳定顺序，包括其他标签页操作或数据改变后的请求。

## 验证边界

测试首尾页、追加、前插、reset 和重复匹配。检查 Page 包装、数据和分页元数据，再检查浏览器行的实际顺序。null previous/next 是边界指令，不是错误，也不是要求获取第零页。

core 一致性场景覆盖数字、游标、包装和 deferred 组合。浏览器测试覆盖示例真实官方客户端前插、追加和 reset，但不证明数据库在并发写入时的一致性。标识语义见[合并](merging.md)，首屏后加载见[延迟 props](deferred.md)。
