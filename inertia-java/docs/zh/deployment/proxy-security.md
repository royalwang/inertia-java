---
title: "代理、TLS 与私有页面"
description: "配置可信代理、cookie/CSRF、私有缓存和内部渲染器路由。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaRequest.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ConfiguredHttpUrl.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/SecurityConfiguration.java
  - inertia-java/deploy/README.md
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustHttpParityTest.java
  - inertia-java/examples/spring-react/frontend/e2e/auth.spec.ts
translation:
  locale: zh-CN
  canonicalId: deployment/proxy-security
  source: deployment/proxy-security.md
  sourceRevision: a7368833417214a0a247a590a8b541cb647f29bd85b6d7efaf9cfd3ecf691222
---

# 代理、TLS 与私有页面

按真实 HTTP 和身份边界配置入口。示例启动器默认将 Java 绑定回环地址，绝不通过入口公开渲染器。

## 保留表示与 origin

浏览器流量路由到 Java，并持续提供不可变资源 URL。通过明确可信的代理请求头策略保留外部 origin。`InertiaRequest` 要求绝对 URI，返回导航 origin 检查依赖适配器快照；库无法决定基础设施可以信任哪些上游请求头。

保留 `Vary: X-Inertia` 和应用缓存头，防止混淆 HTML 与 JSON。认证或用户特定响应应明确使用 private/no-store。在真实代理/CDN 后检查，不能只直连 Java。

## 保护渲染器与 cookie

渲染器接收解析后的 Page 数据，是可信内部对端。不要给它公开路由，并应用网络/进程访问控制。网关不转发浏览器 cookie、认证或任意请求头。

按宿主策略终止 TLS，在最终浏览器 origin 下验证会话/CSRF cookie 的 Secure、SameSite、domain 和 path。可读 CSRF token cookie 与会话/认证 cookie 不同。本地示例设置不代表生产 cookie 正确。

## 通过入口验收

验证首次 HTML、版本化 Page JSON、过期版本刷新、修改重定向、CSRF token 轮换和退出。检查新旧不可变资源、私有缓存、绝对导航目标，以及不存在公开 Node 路由。发布切换时覆盖已有会话的浏览器。

本地脚本不更改反向代理、TLS 或 DNS。这里不提供声称能够代表你的信任模型的通用 nginx 片段。启用生产流量前，记录目标宿主证据和回滚方案。相关行为见[认证](../guide/authentication.md)、[CSRF](../guide/csrf.md)和[发布切换](rolling-upgrades.md)。
