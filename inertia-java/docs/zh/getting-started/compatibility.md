---
title: "支持版本与分发状态"
description: "了解经过验证的 Java、Boot、React、Node 组合及源码快照的分发方式。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/pom.xml
  - inertia-java/examples/spring-react/frontend/package-lock.json
  - inertia-java/compatibility/README.md
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
translation:
  locale: zh-CN
  canonicalId: getting-started/compatibility
  source: getting-started/compatibility.md
  sourceRevision: b25ea90281722db0cca3dc09a61f469728a50009613ba2f446d9f50e6371a5bc
---

# 支持版本与分发状态

本页描述仓库的验证基线，不保证所有更新或更旧的依赖组合都可用。

## 当前基线

| 层级 | 版本或边界 |
| --- | --- |
| Java 库 | `0.1.0-SNAPSHOT`，源码/本地分发 |
| Java | 21 |
| Maven Wrapper | 3.9.16 |
| Spring Boot | 3.5.7；Servlet Spring MVC |
| JSON | Jackson 2，由 Boot BOM 管理 |
| Node 验证基线 | 22.22.2；前端要求 >=22.12 |
| 官方 React/Vite Inertia 包 | 3.8.0，由示例锁定 |
| React / React DOM | 19.3.0 |
| Vite | 8.3.3 |
| TypeScript | 7.0.2 |
| Playwright | 1.64.0；CI 使用与之配对的 Chromium |

复现构建时查看 POM 和前端锁文件。文档使用独立的私有锁文件和 VitePress 工具链，这些依赖不属于应用运行时。

## 验证覆盖什么

[兼容矩阵](https://github.com/royalwang/inertia-java/blob/main/inertia-java/compatibility/README.md)记录了从本仓库 Rust 实现派生的 37 个确定性 Page 场景、45 项 HTTP 策略契约，以及独立的实时 once/TTL 验证。Java 测试比较值、缺失字段和元数据，而非只检查少量字段。

浏览器和部署检查覆盖锁定的官方 React 客户端、实际 Node SSR、hydration、表单、会话及降级。独立 Maven 消费检查在外部项目中使用打包的 Maven 子树验证 jar 和分类包。这些验证的边界不同：JSON fixture 匹配不能单独证明浏览器兼容或生产环境适用。

## 明确的差异和限制

Java 将返回导航限制在当前 origin，将接受的绝对返回 URL 规范化为路径和查询，移除 Referer 的 fragment，并将 `Vary: *` 视为足够。这四项 HTTP 差异均作为预期策略记录于兼容输入。

仅 except 的 partial 选择遵循此 Rust 实现：未排除的 optional prop 可能执行。once/TTL 到期表示和 Java scroll DTO 的使用方式也有明确边界。不能直接假定其他 Inertia 适配器行为一致，应先查看矩阵。

当前证据不覆盖 WebFlux、分布式会话实现、所有官方客户端适配器、Windows 进程监督或任意依赖升级。Servlet/session 或 Node 进程边界改变后，需要重新验证。

## 分发状态

仓库可生成 binary、source、Javadoc 制品及独立发布内容。这些输出本身不表示已发布到 Maven Central、已完成签名公开发布或已部署文档网站。快照文档随源码树更新；实际 release tag 存在后，再从对应 tag 生成版本化文档。

目前安装请使用[本地制品](installation.md)或你有权限的私有仓库。部署细节见[运维手册](https://github.com/royalwang/inertia-java/blob/main/inertia-java/deploy/README.md)。
