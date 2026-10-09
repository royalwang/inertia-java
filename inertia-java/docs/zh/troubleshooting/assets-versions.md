---
title: "资源缺失与重复刷新"
description: "检查 hot URL、CORS、manifest、资源归档、build ID 和旧客户端 409 循环。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteBuild.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ProtocolPolicy.java
  - inertia-java/examples/spring-react/frontend/scripts/build.mjs
verification:
  - inertia-java/examples/spring-react/frontend/scripts/verify-release-switch.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-build.mjs
translation:
  locale: zh-CN
  canonicalId: troubleshooting/assets-versions
  source: troubleshooting/assets-versions.md
  sourceRevision: 8a546ee3bd04aaa5f55401921fd4ae2b2b24a709aec79c923a859319375ac260
---

# 资源缺失与重复刷新

浏览器资源版本过期时，409 刷新是正常行为。重复刷新或 chunk 缺失通常指向版本不一致、缓存表示或资源保留问题。

## 检查身份与 URL

比较文档 Page 版本、Java 构建凭据、renderer build 和客户端输出。诊断时明确发送当前版本 Inertia JSON。Inertia GET 缺少版本，也可能在控制器运行前产生 409。

检查浏览器网络 URL、配置 base 和代理/CDN。保留 `Vary: X-Inertia`，防止缓存 HTML 被当作 Page JSON。版本来源不要每次请求都变化，应标识当前预期客户端构建。

## 修复一致发布

在同一次构建中生成客户端和 SSR，使用其已验证凭据。不要复制另一构建的 manifest，或混合旧 SSR 与新 Java/客户端设置。发布当前不可变资源，同时保留已打开文档和回滚需要的旧版本。

旧文档引用旧 chunk 时，恢复可信旧资源归档，或通过明确版本刷新策略引导浏览器。Java 切换后立即删除旧文件，即使新首页正常，也可能破坏延迟加载。

## 证明恢复

请求新文档，验证当前版本 JSON，并通过服务层获取真实资源字节。切换时保留一个旧浏览器文档，操作导航和延迟路径。在发布切换演练中验证 A/B 资源与回滚。

文档站 404 应用相同 `INERTIA_DOCS_BASE` 构建与预览；本站仓库 base 为 `/inertia-java/`。应用资源和文档 base 是独立配置。见[资源设置](../ssr/vite-assets.md)及[滚动升级](../deployment/rolling-upgrades.md)。
