---
title: 运行 React 示例
description: 构建 Java 与前端，观察 HTML/JSON、表单及 CSR 降级。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: getting-started/quick-start
  source: getting-started/quick-start.md
  sourceRevision: 361f4b1231c4c25913d4d67facdddb97897442210f6ae6c1039fa1d0d36f2c98
sources:
- inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
- inertia-java/examples/spring-react/frontend/package.json
verification:
- inertia-java/docs/scripts/verify-quick-start.mjs
- inertia-java/scripts/verify.mjs
- inertia-java/scripts/verify-maven-consumer.py
---

# 运行 React 示例

本教程运行仓库内 Spring Boot + React 应用和真实 Node SSR。按标明的工作目录执行；Java 默认前端路径相对于进程工作目录。

## 前置条件

使用 Java 21、验收基线 Node 22.22.2、npm 和完整 checkout。Node 包最低要求 22.12。先检查 `java -version` 与 `node --version`；冷缓存构建需要访问 Maven/npm 公共仓库。

## 构建 Java 与前端

在 `inertia-java/` 中：

```sh
./mvnw verify
```

在 `inertia-java/examples/spring-react/frontend/` 中：

```sh
npm ci
npm run typecheck
npm run build
```

前端命令同时构建 client/SSR bundle，生成 `dist/build.json`，并把 client 资源发布到 `.inertia/assets/<buildId>/`。Java 构建不会隐式调用 npm；这些前端输出必须来自同一次构建。

## 启动两个进程

终端 1，在前端目录中：

```sh
npm run ssr
```

终端 2，在 `inertia-java/examples/spring-react/` 中：

```sh
java -jar target/spring-react-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18080
```

打开[用户页面](http://127.0.0.1:18080/users)。Node 监听 loopback 13714，Java 监听 18080。有端口冲突时，先解决冲突，或同时设置相互匹配的 endpoint/port。

## 验证实际行为

1. 禁用 JavaScript 后重新加载 `/users`：HTML 已有用户名称，才说明首屏由服务端渲染。
2. 启用 JavaScript 并重载，点击 **About this app**：官方 Inertia Link 应在不加载完整文档的情况下导航。
3. 提交空名称：重定向后显示校验反馈。
4. 提交 `Ada`：出现 flash 消息；示例不会持久化新用户。
5. 再次重载：一次性交付的 flash 不应重复出现。

检查传输层：

```sh
curl -i http://127.0.0.1:18080/users
# From the example frontend directory:
BUILD_ID=$(node -p "require('./dist/build.json').buildId")
curl -i -H 'X-Inertia: true' -H "X-Inertia-Version: $BUILD_ID" http://127.0.0.1:18080/users
```

第一份响应是包含 Page script 和首屏内容的 HTML。第二份携带当前构建版本，获得 Page JSON 和 `X-Inertia: true`，不会要求 Node 渲染 HTML。Inertia GET 缺少版本会返回刷新响应 409，不能把它当作 JSON 访问成功。浏览器表单测试还需要应用的 CSRF cookie/header 流程。

## 验证降级并清理

只停止自己启动的 Node，保持 Java 运行，启用 JavaScript 后重载 `/users`。默认页面返回 CSR shell，由 React 挂载；禁用 JavaScript 时没有首屏内容。这是正常降级，不能称为 SSR 成功。

完成后用 Ctrl-C 停止自己启动的 Java。源码编辑见[开发模式（英文）](../../getting-started/development.md)，仓库外应用见[第一个应用](first-application.md)。自动化浏览器检查见[浏览器测试（英文）](../../testing/browser-tests.md)。
