---
title: "页面、控制器与组件"
description: "返回类型化 InertiaResponse，注册允许的组件，并区分 REST 路径。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
  - inertia-java/examples/spring-react/frontend/src/pages.ts
  - inertia-java/docs/examples/first-application/HelloController.java
verification:
  - inertia-java/docs/scripts/verify-first-application.mjs
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaHandlerValidatorTest.java
translation:
  locale: zh-CN
  canonicalId: guide/pages
  source: guide/pages.md
  sourceRevision: 4268d8b3c1b0836529fbb8b0834536b09805b2d1223e92f4956b8acbde3d61f4
---

# 页面、控制器与组件

Page 将应用路由连接到已注册前端组件。Java 决定当前用户可以接收哪些数据，React 决定展示方式。如果资源、根视图和 SSR 尚未配置，请先完成[第一个应用](../getting-started/first-application.md)。

## 新增页面

1. 在前端 pages 目录创建 React 组件。
2. 将名称加入浏览器与 Node 入口共用的注册表。示例使用 `frontend/src/pages.ts`，通过 `Object.hasOwn` 检查自有 key。
3. 在 `InertiaConfig.components` 加入完全相同的名称。名称是区分大小写的应用标识，不是 Java 类名，也不依赖文件系统自动发现。
4. 在普通 Spring `@Controller` 中添加 GET 方法，直接、同步返回 `InertiaResponse`，不添加包装。用组件名和 `Props` 构造响应，或调用 `context.render(...)`。
5. 重新构建并重启 Java，按所选开发或生产流程更新前端。

教程中的规范源码 `HelloController.java` 和 `Hello.tsx` 实现了这些步骤。创建脚本将 `Hello` 加入两端注册表。仓库示例还将 `/users` 映射到 `Users/Index`，说明 URL 不必与组件名称相同。

## 区分框架契约

需要时以方法参数注入 `InertiaContext` 或 `InertiaRequest`。它们属于单次请求，不能保存在单例控制器字段中。定义数据来源之前完成授权和输入校验。

类型化 Page handler 不能使用 `@RestController`、`@ResponseBody`、`ResponseEntity<InertiaResponse>` 或异步 Page 包装。启动验证会拒绝这些契约，而非把构建器作为普通 JSON 序列化。REST、上传下载和流式方法继续使用框架原生路径。

## 验证与恢复

禁用 JavaScript，以普通文档方式加载路由，再通过官方 Link 访问。检查版本化 Inertia 响应中的组件与 props。Java 白名单不匹配会导致渲染失败；前端缺少注册会阻止渲染或 hydration。应修复注册表并重建一致的前端，不能将空白页面当作成功。

状态码和请求头见[响应策略](responses.md)，公共 props 见[共享数据](shared-data.md)。Node 渲染器不能补上 Java 中缺失的授权判断。
