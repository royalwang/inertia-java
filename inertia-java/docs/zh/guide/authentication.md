---
title: "认证与授权"
description: "接入真实身份系统，捕获公开 DTO，轮换会话，并对每次访问授权。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/DemoAuth.java
  - inertia-java/examples/spring-react/frontend/src/auth.ts
  - inertia-java/examples/spring-react/frontend/src/pages/Login.tsx
  - inertia-java/examples/spring-vue/src/main/java/io/inertia/example/vue/DemoAuth.java
  - inertia-java/examples/spring-vue/frontend/src/pages/Login.vue
verification:
  - inertia-java/examples/spring-react/frontend/e2e/auth.spec.ts
  - inertia-java/examples/spring-react/frontend/scripts/verify-browser-matrix.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-auth.mjs
translation:
  locale: zh-CN
  canonicalId: guide/authentication
  source: guide/authentication.md
  sourceRevision: a590a36511ef843f53f2b7e27a6fb00e58d67f2cb053c8c1e5bcffe26058fcfe
---

# 认证与授权

Inertia Java 不负责用户认证。使用宿主应用的安全链和身份提供方，只暴露当前请求有权访问的数据。Inertia 请求头和前端路由不授予任何权限。

## 运行可选身份示例

先构建示例，在 `examples/spring-react/` 中将 `INERTIA_DEMO_PASSWORD` 设置为至少 12 个字符的本地密码，再使用 `--inertia.demo-auth=true` 启动 jar。Node SSR 按快速上手单独运行。本地用户名为 `demo`，没有默认密码。

Vue 示例也接受相同环境变量和启动标志，在 `examples/spring-vue/` 中启动。先构建它的前端，再按 Vue 指南运行自己的 Node 渲染进程。

访问 `/login` 登录，再打开 `/account`。POST `/logout` 结束会话。`/users`、`/feed` 和资源路由仍公开。这个内存示例用于展示集成，不是身份服务或生产账号存储。

## 身份边界

Spring Security 负责凭据验证、BCrypt 哈希、认证过滤器、会话固定攻击防护、CSRF 和退出清理。示例登录成功时使用 `newSession`，丢弃匿名应用状态，在新配置的 namespace 中排队 `clearHistory`，并重定向到固定目标。

未经认证的普通账号访问重定向到登录页；Inertia 访问收到带 `X-Inertia-Location` 的 409，强制加载新的登录文档。账号 Page 使用加密历史和 private/no-store 缓存；登录 Page 清理历史。登录表单使用 multipart form data，因为标准认证过滤器读取 Servlet 表单参数。

示例还发送同源退出修订提示，使其他标签页可以替换文档。local storage 可能不可用，因此每次受保护的服务端请求仍必须授权。清理客户端历史不等于撤销权限。

## 交付存储与认证失败

认证成功、失败和 CSRF 恢复统一使用应用配置的 `InertiaSessionStoreFactory`。Redis 交付配置不会替代应用的宿主会话仓库：跨节点共享认证还需要共享 Spring Session 仓库及生命周期集成。

登录成功处理中的交付存储失败时，示例清除已保存的 SecurityContext 并传播异常，不跳转到账号页。不要静默退回本地存储。分布式宿主仓库故障需要应用自己的协调机制；浏览器跳转不能证明两个仓库之间的原子撤销。

## 跨应用节点共享会话

在 Java 项目根目录使用可选 Redis profile 构建所选示例：

```sh
./mvnw -pl examples/spring-react -am -Predis-session package
./mvnw -pl examples/spring-vue -am -Predis-session package
```

各应用使用 `--inertia.demo-auth=true`、由运行者提供且一致的 `INERTIA_DEMO_PASSWORD`，以及相同的宿主/会话设置。通过 `spring.data.redis.host`、`spring.data.redis.port` 和 `spring.session.redis.namespace` 配置认证共享；另行选择 `inertia.session.store=redis`，通过 `inertia.session.redis.host`、`inertia.session.redis.port` 和 `inertia.session-namespace` 配置 Inertia 交付。两个 namespace 分离职责；服务同一应用的节点必须分别保持一致。参见 [Redis 会话](../integrations/custom-session.md)。

默认 `inertia.demo-fixation=new-session` 丢弃匿名应用属性。可选 `change-session-id` 示例保留普通宿主属性，要求内置 Redis 生命周期 factory，并在轮换 ID 前撤销旧交付域。应明确选择策略；迁移认证会话不应把旧 flash/errors 带入新身份。

请求 handle 会拒绝超过可信宿主会话所报告空闲期限的操作。缓存请求无法发现外部直接删除仓库，或其他节点缩短超时的变化。应用管理操作或身份提供方 hook 应安排等价的交付撤销；已经获准执行的业务操作还需要应用自己的授权/撤销策略。

管理操作持有可信 `HttpSession` 时，应在失效或删除宿主会话前调用已注册的 `RedisHttpSessionStoreFactory.beforeHostChange(session)`，并传播失败。只有仓库 ID 时，factory 没有暴露直接撤销 API；离线撤销需要宿主适配器传递同样的生命周期边界，不应解析 factory 的私有元数据，也不应仅因仓库删除成功就报告交付撤销完成。

交付撤销失败是错误，不代表成功退出。恢复连接后显式协调并重新执行宿主操作，不重放结果未知的 Redis 写入。未持久化的 Redis 重启会丢失会话，用户必须重新认证。此集成不声明 Cluster/Sentinel 或 failover 持久性保证。

## 接入真实身份系统

用选定身份提供方替换内存账号。在调度 prop 来源之前确定记录和租户权限。捕获明确、不可变且已授权的数据，不要期望安全 ThreadLocal 自动进入 executor 回调。生产会话存储、cookie/HTTPS/代理策略和退出行为由应用决定。

不要重放被过期 token 拒绝的退出操作：用户明确重新提交之前，当前会话仍处于已认证状态。测试登录失败、成功会话轮换、未授权导航、跨标签退出和实际 Servlet 空闲到期。浏览器矩阵覆盖 SSR/CSR 身份流程，以及真实一分钟会话到期。相关客户端状态边界见 [CSRF](csrf.md)和[历史](history-bigint.md)。

## 上游参考

- [Spring Security CSRF 与 SPA 集成](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [会话固定攻击防护](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [退出处理](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
