---
title: "运行 Vue 示例"
description: "构建 Spring MVC、Vue、Vite 与 Node SSR，体验表单、deferred props 和滚动。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-vue/src/main/java/io/inertia/example/vue/Application.java
  - inertia-java/examples/spring-vue/frontend/package.json
  - inertia-java/examples/spring-vue/frontend/src/app.ts
  - inertia-java/examples/spring-vue/frontend/src/ssr.ts
verification:
  - inertia-java/examples/spring-vue/frontend/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
translation:
  locale: zh-CN
  canonicalId: getting-started/vue
  source: getting-started/vue.md
  sourceRevision: 8c1b3b48a2cfda47446aba375f9067d7e1f0448d41aa2b1acb6ac070bbd091f9
---

# 运行 Vue 示例

Vue 示例与 React 共用 Java core、Spring MVC starter、Vite manifest reader 和 HTTP SSR gateway。Java 负责路由与 props，官方 Vue 适配器渲染页面。表单使用演示数据，不会写入数据库。

## 构建

使用 Java 21 和 Node 22.22.2（最低 22.12）。在 `inertia-java/` 执行：

```sh
./mvnw --batch-mode --no-transfer-progress -pl examples/spring-vue -am package
cd examples/spring-vue/frontend
npm ci
npm run typecheck
npm run build
```

lockfile 锁定 `@inertiajs/vue3` 与 `@inertiajs/vite` 3.8.0、Vue 与 `@vue/server-renderer` 3.5.43，以及 Vite 8.3.3。本示例使用 TypeScript 5.9.3 和 `vue-tsc` 3.3.12。React 有独立的 TypeScript 工具链，修改一个 lockfile 不会升级另一个。

客户端与 SSR 入口共用显式页面注册表。构建会生成配套的客户端、SSR 输出及 `dist/build.json`；Java 应用和 Node 渲染器拒绝不一致的构建身份。应同时重新构建两份输出。

## 启动两个进程

第一个终端进入 `inertia-java/examples/spring-vue/frontend/`：

```sh
npm run ssr
```

第二个终端进入 `inertia-java/examples/spring-vue/`：

```sh
java -jar target/spring-vue-0.1.0-SNAPSHOT.jar --server.port=18083
```

打开 `http://127.0.0.1:18083/users`。Node 渲染器监听 loopback 端口 13715。使用 `SSR_PORT` 修改端口，并为 Java 的 `--inertia.ssr` 配置匹配的 endpoint。从其他工作目录启动 Java 时，可通过 `--inertia.frontend` 提供前端目录的绝对路径。Java 库不会自动启动 Node。

## 体验应用

- 查看初始 HTML：JavaScript 执行前已渲染 Ada。hydration 激活现有标记，deferred 请求补充统计数据。
- 执行 partial reload 并加载 optional 值。Vue 适配器应用响应，同时保留未请求的 props。
- 提交空姓名查看校验，再提交有效姓名查看 flash。导航到 About 再返回后，flash 已消费；没有 CSRF token 的 POST 会被 Spring Security 拒绝。
- 打开 Feed，追加/前插页面、重置列表并刷新 once catalog。服务端提供 scroll/once 元数据，官方客户端管理累积列表与复用。
- 保持 Java 运行，停止 Node 后刷新。Java 返回 CSR shell，Vue 客户端正常挂载；重新启动 Node 并刷新即可恢复 SSR。

根元素具有 `data-server-rendered` 时客户端执行 hydration，CSR shell 则正常挂载。官方适配器在每次 SSR 请求中创建新的 Vue 应用。不要在进程级单例中保存用户专属响应式状态。参见 [Vue SSR](https://vuejs.org/guide/scaling-up/ssr) 和 [Inertia SSR](https://inertiajs.com/docs/v3/advanced/server-side-rendering)。

## 范围与部署

本示例覆盖 Chromium 上的本地生产构建，不代表所有 Vue、客户端或浏览器版本都可用。[兼容性页面](compatibility.md)记录锁定组合。现有打包发布启动器面向 React 示例；Vue 使用上述显式 Java/Node 命令。Vue 发布启动器、Vite 开发模式集成、Svelte 和 WebFlux 需要分别验证。

将示例接入实际应用时，参照 [CSRF 指南](../guide/csrf.md)、[会话投递](../guide/flash-session.md)及[进程监督](../deployment/processes.md)。保留构建配对、loopback 渲染器隔离与应用自行定义的认证策略。
