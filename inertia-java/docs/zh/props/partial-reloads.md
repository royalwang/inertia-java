---
title: "局部重载"
description: "使用 only/except、同组件匹配、祖先路径选择，并验证未选择查询不执行。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaRequest.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/examples/spring-react/frontend/src/pages/Advanced.tsx
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustParityTest.java
  - inertia-java/examples/spring-react/frontend/e2e/advanced.spec.ts
translation:
  locale: zh-CN
  canonicalId: props/partial-reloads
  source: props/partial-reloads.md
  sourceRevision: d0c968602a08c915255b3cf9ca5a440b58f6715a03f1df31d9ae7a855676cd2d
---

# 局部重载

局部重载向同一个组件请求部分定义。当来源以延迟方式定义时，它能减少查询和载荷，但不会减少请求所需的授权。

## 请求与选择

使用官方客户端 reload/visit 选项，不要手工构造第二套 Page 模型。请求通过 `X-Inertia-Partial-Component` 指定组件，通过 `X-Inertia-Partial-Data` 包含路径，通过 `X-Inertia-Partial-Except` 排除路径。

组件名必须与响应组件相同，否则按正常完整访问解析。include 匹配相关祖先和后代定义路径；except 移除该路径及其后代。`always` 可以绕过排除。嵌套 `Props` 遵循父级选择 context。

include 为空但存在 exclusions，意味着选择所有未排除定义，包括 optional 回调。此规则继承本仓库 Rust 实现。若误以为 optional 在所有 except 访问中都会省略，容易意外执行查询。

## 操作 Advanced 示例

1. 启动示例并打开 `/advanced`。
2. 请求嵌套 profile 增量，检查请求头和返回合并元数据。
3. 使用 except 操作跳过昂贵/profile 定义，同时观察 always 状态。
4. 显式获取 optional，将计数器与之前 except 访问比较。
5. 使用 reset 检查替换行为，不要认为字段缺失就会清除客户端旧状态。

局部响应缺失 prop 通常让客户端保留已有状态。省略不是删除已显示秘密的指令。身份或权限切换必须结合明确导航、历史策略及服务端授权。

## 验证边界

检查回调次数、Page 省略、同组件与不同组件、嵌套路径和 include/exclude 组合。HTTP fixture 不能证明浏览器保留状态；Advanced 浏览器测试断言实际对象和渲染值。替换与追加见[合并](merging.md)，optional 来源见[加载策略](loading.md)。
