---
title: 创建第一个 Spring 应用
description: 在仓库外创建独立 Maven 应用，增加 Java 路由与 React 页面。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: getting-started/first-application
  source: getting-started/first-application.md
  sourceRevision: 9fcd82a56dc63aab8bcc3382fc4931a2aab8720ee56a68eae65c16b2f46dd0d6
sources:
- inertia-java/docs/examples/first-application/pom.xml
- inertia-java/docs/examples/first-application/HelloController.java
- inertia-java/docs/examples/first-application/Hello.tsx
- inertia-java/docs/scripts/create-first-application.mjs
verification:
- inertia-java/docs/scripts/verify-first-application.mjs
- inertia-java/scripts/verify.mjs
- inertia-java/scripts/verify-maven-consumer.py
---

# 创建第一个 Spring 应用

在仓库外创建独立 Maven 应用，加入 Java 路由和 React 页面，再验证真实 SSR 与表单往返。教程复用已验证示例的资源、root template 和安全接线，使结果成为可运行的浏览器应用。

生成项目保留原 demo 路由供参考，自己的 `/hello` 是教程代码；没有数据库或持久化业务写入。

## 安装库并创建项目

先在 `inertia-java/` 中完成[本地安装](installation.md)：

```sh
./mvnw install
node docs/scripts/create-first-application.mjs /tmp/my-inertia-app
```

目标必须是尚不存在的绝对目录，并位于仓库之外；至少不能放进 `inertia-java/`。若 `/tmp/my-inertia-app` 已占用，换一个绝对目录，脚本会拒绝合并到已有工作。

脚本复制示例 Java main 源码与锁定的前端构建输入，加入 canonical 教程文件，并创建独立 `com.example:first-inertia-app:1.0.0-SNAPSHOT` POM。POM 导入 Boot BOM，以依赖方式解析 starter；没有 reactor parent、relative parent 或指回本 checkout 的源码引用。

这是仓库开发工具，不是 Maven archetype，也不是 starter 安装的命令。复制源文件时保留 LICENSE 和 NOTICE。

以下独立 POM 直接导入创建脚本实际复制的文件：

<<< @/examples/first-application/pom.xml

## 理解路由与页面

canonical [HelloController.java](https://github.com/royalwang/inertia-java/blob/main/inertia-java/docs/examples/first-application/HelloController.java)定义：

| 请求 | 结果 |
| --- | --- |
| `GET /hello` | `Hello` 组件、`message` prop 与 private/no-store 缓存策略 |
| `POST /hello` 缺少名称、名称为空或过长 | 排队 `name` 校验消息并重定向到 `/hello` |
| `POST /hello` 名称合法 | 排队一次 `toast` flash 并重定向到 `/hello` |

<<< @/examples/first-application/HelloController.java

普通 `@Controller` 返回 `InertiaResponse` 或 `HttpOutcome`。使用字符串前先验证，不把被拒绝的输入反射到异常页面。

canonical [Hello.tsx](https://github.com/royalwang/inertia-java/blob/main/inertia-java/docs/examples/first-application/Hello.tsx)使用 `useForm`、`usePage`、`Head` 和 `Link`。官方客户端正常提交表单，保留的 Spring Security 接线发放并检查 CSRF token。反馈随重定向后的 Page 返回，而不是自定义 JSON 422 API。

<<< @/examples/first-application/Hello.tsx

创建脚本同时把 `Hello` 注册到 Java 组件集合，以及 browser/Node 共用的前端 registry。增加页面时需要一起更新这两处，并创建 controller/component；只增加文件名不够。

## 构建独立项目

在 `inertia-java/` 中，用固定 wrapper 构建外部 POM：

```sh
./mvnw -f /tmp/my-inertia-app/pom.xml package
```

在 `/tmp/my-inertia-app/frontend/` 中：

```sh
npm ci
npm run typecheck
npm run build
```

前端仍使用示例的一致 manifest、build receipt 和不可变资源存储，`npm ci` 使用复制的 lockfile。应用不编译 reactor 源码，库 jar 来自前面安装的 Maven 本地仓库。

## 运行并验证

先停止占用默认端口的快速上手进程。终端 1，在 `/tmp/my-inertia-app/frontend/` 中：

```sh
npm run ssr
```

终端 2，在 `/tmp/my-inertia-app/` 中：

```sh
java -jar target/first-inertia-app-1.0.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18080
```

打开 [hello 页面](http://127.0.0.1:18080/hello)，逐项确认：

1. 禁用 JavaScript 并重载，文档已包含 **Hello from Java**。
2. 启用 JavaScript，提交空名称，出现校验反馈。
3. 提交 `Ada`，出现 **Hello, Ada** flash；重载后消失。
4. 点击 About 并用浏览器历史返回，导航和表单仍可使用。
5. 检查带 `X-Inertia: true` 和当前构建版本的请求，返回 `Hello` 组件及 JSON Page props。
6. 停止自己启动的 Node，默认响应仍能在 JavaScript 启用时通过 CSR 使用。

完成后用 Ctrl-C 停止自己启动的进程。要自动执行这些步骤，可在文档目录运行 `npm run docs:first-application`；环境要求和命令见[文档 README](https://github.com/royalwang/inertia-java/blob/main/inertia-java/docs/README.md)。

## 改造成业务应用

移动/重命名 Java package 时，同步修改 Boot plugin 的 `mainClass`。有意识地替换 demo 路由与组件注册，保持 root view、codec、SSR root ID、build receipt 和静态资源挂载一致。受保护数据上线前，加入真实认证授权、持久化与生产 session/cookie 策略。

[所有权模型](../concepts/ownership.md)说明共享与请求专属对象，[API 指南](../api-guide.md)介绍配置替换和扩展点。
