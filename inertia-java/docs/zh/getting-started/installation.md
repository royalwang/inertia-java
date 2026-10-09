---
title: 安装
description: 从源码安装本地分类包，选择 core 或 Spring starter 依赖。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: getting-started/installation
  source: getting-started/installation.md
  sourceRevision: de6029fb6e47cf652fe1094eae20c2fd2a465f37621ff9a2377c5a4223dd0f92
sources:
- inertia-java/pom.xml
- inertia-java/inertia-spring-boot-starter/pom.xml
verification:
- inertia-java/scripts/verify.mjs
- inertia-java/scripts/verify-maven-consumer.py
---

# 安装

使用 Java 21 和当前 checkout 构建的产物。坐标为 `io.inertia:*:0.1.0-SNAPSHOT`，本文不宣称已在 Maven Central 发布。

## 安装本地库

在 `inertia-java/` 中执行：

```sh
./mvnw install
```

wrapper 固定 Maven 3.9.16。命令编译并测试 reactor，生成 binary/source/Javadoc 分类包，再安装到 Maven 本地仓库；它不会构建 React 前端。首次下载 Maven 依赖及 wrapper 需要网络。

如需隔离消费演练，先按[快速上手](quick-start.md)构建前端，再从 `inertia-java/` 执行 `python3 scripts/verify-maven-consumer.py`。该工具使用临时 Maven 仓库和仓库外项目，不会把演练产物安装到日常本地缓存，也不执行远端发布。

## 选择依赖

| Artifact | 职责 |
| --- | --- |
| `inertia-core` | 协议、props、Page codec、context、renderer 与 session SPI |
| `inertia-spring-boot-starter` | Servlet MVC 依赖与条件化 Boot 接线 |
| `inertia-spring-webmvc` | 不采用 Boot 默认配置时的显式 MVC 集成 |
| `inertia-ssr-http` | 可信 HTTP SSR gateway 与可选健康监测 |
| `inertia-vite` | manifest/hot 资源与完整构建校验 |
| `inertia-testing` | Page 断言，应使用 test scope |

Boot 应用导入 Spring Boot 3.5.7 dependency BOM，并添加：

```xml
<dependency>
  <groupId>io.inertia</groupId>
  <artifactId>inertia-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

starter 传递引入可选 SSR/Vite 库，但不启动 Node、不生成 React 页面，也不替应用选择 root view。需要自行声明 `InertiaConfig` bean，提供组件白名单和渲染配置。

断言依赖为 `io.inertia:inertia-testing:0.1.0-SNAPSHOT`，使用 `<scope>test</scope>`。[独立 POM](https://github.com/royalwang/inertia-java/blob/main/inertia-java/docs/examples/first-application/pom.xml)展示不使用 reactor parent 的消费方式。

## 区分执行配置和页面配置

starter 在 `inertia.*` 下绑定执行和会话设置，包括 props/response 截止时间、executor 大小和 `session-namespace`。组件、root template、Vite 资源和 SSR endpoint 由配置 bean 提供。示例的 `-Dinertia.frontend` 等 JVM 参数属于示例应用，不是通用 starter 属性。

正式属性和 bean 替换规则见[配置参考](../reference/configuration.md)及 [API 配置表](../api-guide.md#boot-配置与-bean-替换)。

## 排查依赖解析

无法解析 `io.inertia` 产物时，确认 `./mvnw install` 成功，消费应用使用相同的 Maven 本地仓库和精确版本。不要把库源码目录加入业务应用 classpath 来绕过依赖解析。

私有仓库发布应使用已授权的 snapshot 流程，包含完整 parent/module POM 和 jar。分发包内 Maven 子目录是发布输入，不代表已具备远端 snapshot metadata、凭据、签名或命名空间授权。

继续阅读[第一个应用](first-application.md)，或使用 [API 指南](../api-guide.md)了解最小协议集成。
