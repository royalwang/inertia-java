---
title: "开发工作流"
description: "运行 Vite 开发 SSR，配置端口与热更新，并关闭自己启动的服务。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/vite.config.ts
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
translation:
  locale: zh-CN
  canonicalId: getting-started/development
  source: getting-started/development.md
  sourceRevision: f01f2fabf066509df26d64ae7fd8195759f079f22ec1f18b1e0c17868e970e88
---

# 开发工作流

前端修改使用 Vite 源码开发模式；后端修改需要重新构建并重启 Java。开发 SSR 与生产构建使用不同的运行模式。

## 启动 Vite 与 Java

完成 Maven 打包和 `npm ci` 后，在 `examples/spring-react/frontend/` 中运行 `npm run dev`。Vite 监听 `127.0.0.1:15173`，将实际 origin 写入 `.inertia/hot`，并通过锁定版本的 Inertia Vite 插件提供开发 SSR。

在 `examples/spring-react/` 中执行：

```sh
java -Dinertia.development=true -jar target/spring-react-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18082
```

打开[开发环境用户页面](http://127.0.0.1:18082/users)。此模式下，Java 使用开发资源 URL 和 Vite 的 `/__inertia_ssr` 端点，无需单独启动生产 Node 渲染器。

`-Dinertia.development=true` 是示例读取的 JVM 系统属性，必须放在 **`-jar` 之前**。`--server.port=18082` 是 Spring 命令行属性，放在 jar 文件之后。starter 并不为所有业务应用提供通用的开发模式开关。

## 修改源码后的操作

| 修改内容 | 所需操作 |
| --- | --- |
| 已有 React 组件或样式 | 等待 Vite 更新浏览器，同时确认服务端 HTML |
| Java 控制器或配置 | 重新打包并重启自己启动的 Java 进程 |
| 新页面组件 | 在 Java 和 `frontend/src/pages.ts` 注册，再重启 Java |
| 依赖或锁文件 | 使用项目的包管理器重新安装，核对构建兼容性 |
| 生产客户端或 SSR 入口 | 同时重新构建两端及构建凭据，再测试生产模式 |

Vite 的 CORS 配置包含文档中的本地 Java 端口 18080 和 18082。若使用其他 Java origin，启动 Vite 时设置与之对应的 `INERTIA_DEV_APP_ORIGIN=http://127.0.0.1:<port>`。不要为掩盖端口不匹配而放开任意来源。

## 排查过期的开发状态

正常停止 Vite 会删除其创建的 hot 文件。如果进程崩溃，先检查 `.inertia/hot` 和实际监听端口，再清理过期文件。不要删除其他运行中开发会话拥有的文件。

hot 值必须是可信的 HTTP(S) origin，不能包含凭据、查询、fragment 或应用路径。格式错误会导致资源加载失败和 SSR 端点不可用，不会静默切换到生产渲染器。

生产模式忽略 hot 文件并验证不可变构建输入。开发渲染成功，不能证明生产凭据、manifest 挂载或独立 Node 进程正确。

## 交付前验证

后端修改运行 Java 检查；前端修改运行 `npm run typecheck` / `npm run build`。完成 Maven 打包和浏览器安装后，在示例前端运行 `npm run test:development`，验证完整开发场景。该命令启动独立的回环地址进程，验证禁用 JavaScript 的 SSR 与官方客户端交互，并清理自己创建的 hot 文件和进程。

编辑本文档时，使用 `inertia-java/docs/` 中的独立工具及锁文件，不修改应用前端依赖。命令见[文档 README](https://github.com/royalwang/inertia-java/blob/main/inertia-java/docs/README.md)。
