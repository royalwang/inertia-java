---
title: "SSR 与 Vite API 地图"
description: "索引 gateway、resolver、health、build 和 assets 的构造契约及资源生命周期。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/HttpSsrGateway.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/SsrEndpointResolver.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/SsrHealthMonitor.java
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteManifest.java
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteBuild.java
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteAssets.java
verification:
  - inertia-java/inertia-ssr-http/src/test/java/io/inertia/ssr/HttpSsrGatewayTest.java
  - inertia-java/inertia-vite/src/test/java/io/inertia/vite/ViteBuildTest.java
translation:
  locale: zh-CN
  canonicalId: reference/ssr-vite-api
  source: reference/ssr-vite-api.md
  sourceRevision: 3fe10c286277e0d787c689365ca81ac30d6b904a1c4593f2f7a6918f54535dbe
---

# SSR 与 Vite API 地图

HTTP 网关与 Vite 清单应来自同一发布。渲染器可访问，不代表其 build/root 与应用一致。

## 渲染器类型

| 类型 | 契约 |
| --- | --- |
| `SsrGateway`（core） | 将 Page 解析为可信 head/body 输出或结构化降级 |
| `HttpSsrGateway` | 限制字节、在途容量、预算的 HTTP 传输，支持可选身份验证 |
| `SsrEndpointResolver` | 解析可信配置的渲染 URL 与排除模式 |
| `SsrHealthMonitor` | 独立定时健康采样与缓存快照 |

网关将已解析 Page 发给可信内部对端，不转发浏览器认证或任意请求头。配置 URL 必须满足 HTTP(S) 策略；不能借重定向逃离信任边界。

依赖默认值前检查构造器重载。示例明确传入 200ms 连接、1s 渲染、2MiB、16 传输槽，并验证 build/root，不能把这些当作所有应用的 Boot 默认值。网关失败映射到[降级原因](errors.md)，必需 SSR 可以把降级提升为类型化失败。

`SsrHealthMonitor.start()` 调度检查并返回自身；打开状态重复启动无害。`snapshot()` 不发送 HTTP。监控从 `UNKNOWN` 开始，记录 `UP`/`DOWN` 与时间，`close()` 后变为 `STOPPED`。缓存的成功 `status: OK` 不证明当前 build、组件渲染或 hydration。应用拥有并关闭监控。`HttpSsrGateway` 没有公开 close 方法，应在应用生命周期复用池化客户端。

## Vite 类型

| 类型 | 契约 |
| --- | --- |
| `ViteManifest` | 解析 manifest 入口、imports、CSS 元数据 |
| `ViteBuild` | 读取并验证一致的构建凭据与客户端/SSR 清单 |
| `ViteAssets` | 根据实际元数据生成可信开发/生产资源标签 |

Java 根视图放置资源标签、SSR head 和 Page 数据 script。开发 hot server URL 与生产版本化资源生命周期不同。Java、浏览器和渲染器的 root ID 必须相同；跨发布保留资源与新 Page 版本是独立要求。

[API 指南](../api-guide.md)提供可编译 Java 示例，实际构建命令见 [Vite 设置](../ssr/vite-assets.md)，资源保留见[发布切换](../deployment/rolling-upgrades.md)。无效凭据/root/build 应按策略失败或降级；复制无关 manifest 来消除错误不是恢复方式。
