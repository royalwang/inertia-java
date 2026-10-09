---
title: "认证与授权"
description: "接入真实身份系统，捕获公开 DTO，轮换会话，并对每次访问授权。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/DemoAuth.java
  - inertia-java/examples/spring-react/frontend/src/auth.ts
  - inertia-java/examples/spring-react/frontend/src/pages/Login.tsx
verification:
  - inertia-java/examples/spring-react/frontend/e2e/auth.spec.ts
  - inertia-java/examples/spring-react/frontend/scripts/verify-browser-matrix.mjs
translation:
  locale: zh-CN
  canonicalId: guide/authentication
  source: guide/authentication.md
  sourceRevision: a27191ef4b950e144effbd31ff93238730a48c03d4048b83af443dfbcfa05eb8
---

# 认证与授权

Inertia Java 不负责用户认证。使用宿主应用的安全链和身份提供方，只暴露当前请求有权访问的数据。Inertia 请求头和前端路由不授予任何权限。

## 运行可选身份示例

先构建示例，在 `examples/spring-react/` 中将 `INERTIA_DEMO_PASSWORD` 设置为至少 12 个字符的本地密码，再使用 `--inertia.demo-auth=true` 启动 jar。Node SSR 按快速上手单独运行。本地用户名为 `demo`，没有默认密码。

访问 `/login` 登录，再打开 `/account`。POST `/logout` 结束会话。`/users`、`/feed` 和资源路由仍公开。这个内存示例用于展示集成，不是身份服务或生产账号存储。

## 身份边界

Spring Security 负责凭据验证、BCrypt 哈希、认证过滤器、会话固定攻击防护、CSRF 和退出清理。示例登录成功时使用 `newSession`，丢弃匿名应用状态，在新配置的 namespace 中排队 `clearHistory`，并重定向到固定目标。

未经认证的普通账号访问重定向到登录页；Inertia 访问收到带 `X-Inertia-Location` 的 409，强制加载新的登录文档。账号 Page 使用加密历史和 private/no-store 缓存；登录 Page 清理历史。登录表单使用 multipart form data，因为标准认证过滤器读取 Servlet 表单参数。

示例还发送同源退出修订提示，使其他标签页可以替换文档。local storage 可能不可用，因此每次受保护的服务端请求仍必须授权。清理客户端历史不等于撤销权限。

## 接入真实身份系统

用选定身份提供方替换内存账号。在调度 prop 来源之前确定记录和租户权限。捕获明确、不可变且已授权的数据，不要期望安全 ThreadLocal 自动进入 executor 回调。生产会话存储、cookie/HTTPS/代理策略和退出行为由应用决定。

不要重放被过期 token 拒绝的退出操作：用户明确重新提交之前，当前会话仍处于已认证状态。测试登录失败、成功会话轮换、未授权导航、跨标签退出和实际 Servlet 空闲到期。浏览器矩阵覆盖 SSR/CSR 身份流程，以及真实一分钟会话到期。相关客户端状态边界见 [CSRF](csrf.md)和[历史](history-bigint.md)。

## 上游参考

- [Spring Security CSRF 与 SPA 集成](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [会话固定攻击防护](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [退出处理](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
