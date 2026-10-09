---
title: "项目与模块结构"
description: "区分可复用模块、业务服务、浏览器代码和渲染器。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/pom.xml
  - inertia-java/deploy/release.mjs
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
translation:
  locale: zh-CN
  canonicalId: getting-started/project-structure
  source: getting-started/project-structure.md
  sourceRevision: a4fd9086a55a63dcd1c332d4cb1dbd43ad627c22e962efe411505c9f15ff606b
---

# 项目与模块结构

Rust crate 保留在仓库根目录。Java 聚合构建、示例和公开文档位于 `inertia-java/`；历史架构与实施规划位于 `docs/inertia-java/`。

## 可复用模块

| 目录 | 负责 | 不负责 |
| --- | --- | --- |
| `inertia-core/` | 请求快照、协议、props、Page 序列化、context/session SPI、渲染与观测 SPI | Servlet 路由、数据库、Node 生命周期 |
| `inertia-ssr-http/` | 有界 HTTP SSR 传输、端点选择和可选健康探测 | 对外渲染路由、进程监督 |
| `inertia-vite/` | 资源标签、开发 origin、manifest 和构建凭据 | 运行 npm 或提供文件服务 |
| `inertia-spring-webmvc/` | 类型化 MVC 处理、会话适配、校验桥接和异常集成 | 应用认证与 REST 行为 |
| `inertia-spring-boot-autoconfigure/` | 条件 bean、经过校验的执行配置和可选指标适配 | 应用组件与根视图注册 |
| `inertia-spring-boot-starter/` | 依赖入口 | 自有公开 Java 类 |
| `inertia-testing/` | Page 断言辅助工具 | HTTP、请求头或浏览器验收 |

核心依赖 Jackson 2，不依赖 Spring。业务应用可以使用独立宿主适配器，但必须遵循协议和 context 生命周期。

## 示例应用

`examples/spring-react/src/main/java/` 包含 Boot 配置、页面控制器、Spring Security 集成、示例身份/历史/故障场景及健康路由。`frontend/src/` 包含浏览器入口、Node SSR 入口、共享页面注册表和 React 组件。

`frontend/scripts/build.mjs` 构建两端并生成凭据；`release-assets.mjs` 发布不可变客户端资源。`frontend/dist/`、`.inertia/` 和 `node_modules/` 是被忽略的生成目录。应修复源码，而非手工修改这些输出。

`deploy/` 打包独立的不可变发布物，并提供启动器、运维手册和监督示例。这是示例的部署模式，不是嵌入库中的通用 Java 进程管理器。

## 文档与验证

- `docs/` 包含面向读者的文档库和规范 API 示例。其私有 Node 工具链不进入 Java 部署内容。
- `compatibility/` 包含由 Rust 生成的 fixture 和明确的兼容验证矩阵。
- `scripts/` 提供制品、独立消费、依赖清单和聚合验证命令。
- `target/` 是 Maven 输出，包含可复用模块的 source/Javadoc 分类包。

[API 指南](../api-guide.md) 提供详细集成示例。生成的 Javadoc 是符号索引，与用法和生命周期说明互为补充。

## 组织自己的应用

应用控制器、已授权的数据服务和配置属于业务应用。React 页面和唯一的共享组件注册表属于前端。保持客户端与 SSR 构建一致，并明确部署资源存储。运行时不要依赖仓库中的相邻目录；[第一个应用教程](first-application.md) 展示了独立 Maven 消费方式。
