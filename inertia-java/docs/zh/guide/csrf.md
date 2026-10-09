---
title: "CSRF 与 cookie 处理"
description: "配置 cookie/header token，并在拒绝后恢复而不重放修改操作。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/SecurityConfiguration.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/BrowserCsrfFailureHandler.java
verification:
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
  - inertia-java/examples/spring-react/frontend/e2e/auth.spec.ts
translation:
  locale: zh-CN
  canonicalId: guide/csrf
  source: guide/csrf.md
  sourceRevision: 52d87f2be6f8cf613d14a98992e890fe4ba98a57d0b8691ea3991db01027f593
---

# CSRF 与 cookie 处理

CSRF 保护属于应用安全链。starter 不提供通用安全配置，Page 请求头也不能授权写操作。

## 示例的 cookie/header 流程

示例使用 Spring Security 的 `CookieCsrfTokenRepository.withHttpOnlyFalse()`。浏览器请求处理器主动获取 deferred token，接受浏览器客户端使用的明文 token header，并保留表单处理所需的掩码请求属性行为。

可读 CSRF cookie 专用于此 token 传输，不代表认证或会话 cookie 也应可读。生产 HTTPS、Secure/SameSite/path/domain 以及可信代理配置由应用负责。调整时应确认真实 origin、cookie 作用域和浏览器行为。

首次页面请求取得 token，官方客户端同源表单提交发送匹配的 header。认证、退出或清理 cookie 后，旧表单持有的 token 可能过期。

## 恢复时不重放写入

对于已配置示例恢复路由中可识别的 Inertia POST，`BrowserCsrfFailureHandler` 排队安全的 `_csrf` 消息，并重定向到固定的检查页面。它不执行被拒绝的操作，也不将输入 token、Referer 或表单值复制到恢复目标。其他被拒绝请求保持 403，存储失败则返回安全的服务端错误。

页面提示用户检查表单后重新提交。保留可见输入不表示服务端接受了首次请求。退出 token 过期时，已认证会话保持有效，直到新的明确退出操作成功。

## 接入应用

1. 按实际客户端传输配置宿主安全链和 token repository。
2. 确保首次及重定向 Page 能取得当前 token。
3. 使用固定且由应用控制的恢复目标，namespace 与正常 handler 的 Inertia 会话一致。
4. 显示安全反馈，允许明确重新提交；不要静默重试修改操作。
5. 测试 cookie 缺失或过期、登录/退出时 token 轮换，以及非 Inertia 请求。

示例恢复映射不覆盖所有应用路由，需要明确扩展或提供自己的 handler。不要为了让表单成功而全局关闭 CSRF。精确传输实现见安全配置源码和浏览器测试，错误展示见[表单](forms-validation.md)。

## 上游参考

- [Spring Security SPA CSRF 集成](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)
- [Inertia CSRF 处理](https://inertiajs.com/docs/v3/security/csrf-protection)
