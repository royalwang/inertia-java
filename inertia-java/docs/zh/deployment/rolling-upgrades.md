---
title: "发布切换与回滚"
description: "保留旧哈希资源，切换 A/B，并理解单节点会话连续性限制。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/scripts/release-assets.mjs
  - inertia-java/deploy/README.md
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteBuild.java
verification:
  - inertia-java/examples/spring-react/frontend/scripts/verify-release-switch.mjs
  - inertia-java/examples/spring-react/frontend/scripts/release-assets.test.mjs
translation:
  locale: zh-CN
  canonicalId: deployment/rolling-upgrades
  source: deployment/rolling-upgrades.md
  sourceRevision: 94a7c2a91ddbcdc76476f9b6d5cf6f7d3a8869bab3484374a0516a533610f40a
---

# 发布切换与回滚

修改入口前准备下一组 Java/Node，保留两个构建的客户端资源。切换前打开的浏览器仍可能请求旧 chunk。

## 准备 A 与 B

1. 保留不可变发布 A 及其已验证端口映射。
2. 一致地构建和打包 B，将客户端文件发布到同时保留 A 的可信共享资源归档。
3. 每个服务将 `INERTIA_ASSET_STORE` 指向该归档。在源码前端运行 `npm run publish:assets -- /absolute/shared-store`，暂存并验证当前构建，不裁剪旧构建。
4. 在未使用的固定端口启动 B，root/SSR 端点设置匹配。
5. 切换流量前，经过资源服务层验证 Java 存活、真实 B SSR/版本、B 客户端 URL 和旧 A URL。

归档也可从版本化发布资源逐字节配置。Java 提供资源前验证当前构建，不要将文件混入无版本目录。

## 切换与保留

就绪后才更改可信代理 upstream，并按宿主策略排空入口。过期 Inertia 版本可以强制新文档，但不保证所有旧 chunk 已不再需要。保留期需覆盖活跃文档、缓存和回滚。

库不追踪所有标签页，也不回收资源。相同 root/build 不保证独立 Java 进程间认证会话连续。会话亲和/迁移、身份连续和数据库兼容是独立部署要求。

## 明确回滚

选择保留的不可变 A 进程对及已知端口映射，重新验证真实 SSR/资源，再将入口切回。确认业务/数据库修改向后兼容；恢复 jar 无法撤销不兼容数据迁移。

本地发布切换验证覆盖保留不可变资源和客户端版本行为，不执行真实代理、TLS、会话迁移或数据库回滚。需在目标宿主验证并明确记录边界。见[构建身份](../concepts/versioning.md)和[运维](operations.md)。
