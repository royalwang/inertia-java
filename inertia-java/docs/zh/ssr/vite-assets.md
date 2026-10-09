---
title: "Vite 资源与 manifest"
description: "加载导入、样式和预加载资源，统一入口、base 与构建凭据。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteAssets.java
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteManifest.java
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteBuild.java
  - inertia-java/examples/spring-react/frontend/scripts/build.mjs
verification:
  - inertia-java/examples/spring-react/frontend/scripts/verify-build.mjs
  - inertia-java/examples/spring-react/frontend/scripts/release-assets.test.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-release-switch.mjs
translation:
  locale: zh-CN
  canonicalId: ssr/vite-assets
  source: ssr/vite-assets.md
  sourceRevision: c77d5940e9f6c2b7c7d82c882173284316a3882a9bb8634dc33970ae03d720a6
---

# Vite 资源与 manifest

从已验证构建或明确可信的开发 origin 生成资源标签。输出目录、公开 URL 前缀和资源处理器必须一致；有效 manifest 不会自动提供文件服务。

## 生产构建

示例构建 `dist/client` 和 `dist/ssr`，将二者哈希写入 `dist/build.json`，再将客户端文件发布到 `.inertia/assets/<buildId>/`。Java 的 `ViteBuild` 检查凭据、文件清单与引用的客户端资源，其 build ID 成为 Page 版本及公开前缀 `/build/<buildId>/`。

`ViteAssets` 读取生产 manifest，为 `src/app.tsx` 生成标签，包含实现支持的 imports、样式与 preloads。应用通过资源处理器挂载资源存储。路径必须安全且相对于所选资源 base；不要手改凭据哈希以接受混合输出。

生产保留启动快照并忽略 hot 文件。替换运行中发布目录的文件可能破坏这些假设。应发布新的不可变构建，并保留旧资源供活跃文档和回滚使用。

## 开发 hot 模式

开发模式需明确启用。示例 Vite 插件将实际回环 origin 写入 `.inertia/hot`。`ViteAssets` 使用此可信 HTTP(S) origin 生成 Vite client、React refresh preamble 和入口标签；SSR resolver 使用同一 origin 的 `/__inertia_ssr`。

hot 值不能包含凭据、应用路径、查询或 fragment。格式错误会导致资源生成失败或开发 SSR 不可用。无 hot 文件时，开发 manifest 刷新使用文件修改时间戳。hot 文件属于应用配置，不能来自用户控制的输入 URL。

## 排查页面失败

依次检查工作目录/`inertia.frontend`、凭据、manifest 入口、资源挂载、实际资源 URL 和浏览器响应状态，再检查 Java/Node 的 build/root 身份。HTML 返回 200 但 script chunk 返回 404，不是正常浏览器集成。

Maven/前端构建后运行 `npm run test:build-integrity`，验证混合、缺失和未记录输出被拒绝。资源与发布切换检查覆盖旧 URL 保留。版本及保留策略见[版本](../concepts/versioning.md)和部署手册。

## 上游参考

- [Vite 后端集成 manifest](https://vite.dev/guide/backend-integration)
