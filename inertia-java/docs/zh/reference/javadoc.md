---
title: "生成的 Javadoc"
description: "访问六个运行模块及依赖型 starter 的版本化 API 输出。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/pom.xml
  - inertia-java/inertia-spring-boot-starter/src/main/javadoc/index.html
  - inertia-java/scripts/verify-library-artifacts.py
verification:
  - inertia-java/scripts/verify-library-artifacts-test.py
translation:
  locale: zh-CN
  canonicalId: reference/javadoc
  source: reference/javadoc.md
  sourceRevision: f699e332f05790564aa0408b1f9b26ba32038798c8bf7397550626beff3a8f35
---

# 生成的 Javadoc

Javadoc 描述 `0.1.0-SNAPSHOT` 的精确公开类型、构造器和方法签名。手写 [core](core-api.md)、[Spring](spring-api.md)、[SSR/Vite](ssr-vite-api.md) 地图说明所有权与预期用法。

## 构建与浏览

在 `inertia-java/` 用 `./mvnw --batch-mode install` 构建库制品。每个发布模块产生一个 `-javadoc.jar` 分类包。Maven 构建后运行文档准备/构建命令，站点才能包含匹配的生成输出。

API 参考覆盖七个带符号索引的库：core、HTTP SSR、Vite、Redis 投递、MVC、Boot 自动配置和 testing。starter 分类包包含模块指南，而不是生成的 facade 类；依赖用途见 [Spring API](spring-api.md)。

## 模块入口

以下链接打开当前 Maven 分类包中的实际生成 HTML：

- [Core API](../../reference/javadoc/0.1.0-SNAPSHOT/inertia-core/index.html)
- [HTTP SSR API](../../reference/javadoc/0.1.0-SNAPSHOT/inertia-ssr-http/index.html)
- [Vite API](../../reference/javadoc/0.1.0-SNAPSHOT/inertia-vite/index.html)
- [Redis 投递 API](../../reference/javadoc/0.1.0-SNAPSHOT/inertia-session-redis/index.html)
- [Spring MVC API](../../reference/javadoc/0.1.0-SNAPSHOT/inertia-spring-webmvc/index.html)
- [Boot 自动配置 API](../../reference/javadoc/0.1.0-SNAPSHOT/inertia-spring-boot-autoconfigure/index.html)
- [Starter 模块指南](../../reference/javadoc/0.1.0-SNAPSHOT/inertia-spring-boot-starter/index.html)
- [Testing API](../../reference/javadoc/0.1.0-SNAPSHOT/inertia-testing/index.html)

## 阅读契约

用生成签名核对重载与嵌套 record，再阅读所属指南中的请求/资源生命周期、默认值和失败策略。[API 指南](../api-guide.md)提供完整 Java 集成示例，[第一个应用](../getting-started/first-application.md)组合 Spring 控制器与 React 页面。

## 版本与归属

使用与应用库版本相同的 Javadoc。本参考对应 `0.1.0-SNAPSHOT`，站点及分类包保留 Apache-2.0 许可证和归属。

## 上游参考

- [Maven Source 3.3.1 生命周期 goal](https://maven.apache.org/plugins-archives/maven-source-plugin-3.3.1/usage.html)
- [Maven Javadoc 3.7.0 jar goal](https://maven.apache.org/plugins-archives/maven-javadoc-plugin-3.7.0/jar-mojo.html)
