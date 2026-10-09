---
title: "官方客户端验收"
description: "运行开发、SSR/CSR、无脚本、认证、合并、历史与故障场景。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/package.json
  - inertia-java/examples/spring-react/frontend/scripts/verify-browser-matrix.mjs
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
  - inertia-java/examples/spring-react/frontend/e2e/auth.spec.ts
verification:
  - inertia-java/examples/spring-react/frontend/playwright.config.ts
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-health.mjs
translation:
  locale: zh-CN
  canonicalId: testing/browser-tests
  source: testing/browser-tests.md
  sourceRevision: 21e186ed2b11f2b2250e8c328e0cc5d47b431545209844957b3c9f7ff8269d51
---

# 官方客户端验收

使用固定版本的官方 Inertia React 客户端，访问实际 Java 应用与 Node 渲染器。伪网关或仅 JSON 测试不能证明 hydration 或浏览器合并行为。

## 运行矩阵

先按[开发工作流](../getting-started/development.md)构建 Maven 应用与前端。在 `inertia-java/examples/spring-react/frontend/` 执行：

```sh
npm run test:browser-matrix
```

保留结果时，将 `INERTIA_MATRIX_OUTPUT` 设为新的绝对证据目录。runner 管理自己的进程和隔离端口，写出分场景日志/截图。使用 CI Chromium channel 时安装配对 Playwright 浏览器，本地默认 Chrome。

八个场景为 SSR、all-errors、failures、替代 namespace、认证、真实认证到期、CSR failures 和 CSR 认证。覆盖列表/表单、deferred/partial/merge/once/scroll、命名/多错误、会话/渲染/写入失败恢复、CSRF、登录退出及历史。按场景跳过是有意安排，应分别记录通过和跳过数量，不能声称每种模式都执行了所有用例。

## 阅读证据

禁用 JavaScript 检查真实首次 HTML 证明 SSR，再在 hydration 后浏览器检查后续导航。只有执行 JavaScript 才显示标题不能证明 SSR。Node 停止后普通页面应从 CSR 挂载，必需 SSR 有独立失败路径。

真实空闲到期场景按设计需要时间，验收真实 cookie/session 到期时不能以虚构时钟替代。保留 console/network 失败及相关截图，并确认拥有的进程退出。

## 运行定向验收命令

以下命令在 Maven jar、锁定前端依赖及两端 bundle 构建后，从 `inertia-java/examples/spring-react/frontend/` 运行。使用 Java 21 和固定 Node/Playwright。进程脚本自行启动回环对端，不需第二个手动应用。`test:assets` 是文件系统契约，不启动浏览器。

| 命令 | 检查边界 |
| --- | --- |
| `npm run test:development` | 真实 Vite 开发 SSR、hot 资源与官方客户端流程；拒绝已有 hot 文件 |
| `npm run test:build-integrity` | 客户端/SSR 凭据及篡改/混合构建拒绝 |
| `npm run test:ssr-failures` | HTTP 错误、正文停滞、超大响应后 CSR/导航/表单仍可用 |
| `npm run test:ssr-health` | 健康、watch/重启、无效解码 Page 拒绝和随后有效渲染 |
| `npm run test:csp` | 生产 SSR 与断开 CSR 的 nonce 集成，允许可信 script 并阻止非可信 script |
| `npm run test:custom-root` | 一致的 `portal` Java/Node/客户端根、hydration、降级及恢复 |
| `npm run test:history` | 实际加密浏览器历史、清理 key 和逐 Page SSR 退出 |
| `npm run test:assets` | 不可变资源发布、幂等及拒绝覆盖损坏内容 |
| `npm run test:release-switch` | A→B 路由、旧资源字节保留、官方客户端刷新和一致新渲染 |

检查脚本打印的证据位置与最终摘要，包括清理失败。这些是定向检查，不能替代其他受影响契约。release-switch B 是受控客户端字节变化 fixture，不是第二个源码修订。健康不证明每个组件可渲染，nonce 验收也不证明 CSP 普遍适合所有应用。

完整既有运行时检查从仓库根目录执行：

```sh
node inertia-java/scripts/verify.mjs
```

命令执行干净 Maven/前端构建和 18 阶段验收。`INERTIA_VERIFY_OUTPUT=/absolute/path` 选择证据父目录，不格式化或提交源码。阅读 `summary.json` 的阶段结果和 source/build 身份；本地结果不代表远端 GitHub workflow 结果。Windows 进程树清理与生产宿主验证独立进行。

独立 Maven 消费是额外边界。正常构建后，在 `inertia-java/` 运行 `python3 scripts/verify-maven-consumer.py`。它从隔离 fixture 仓库/缓存解析打包的 starter/testing、运行 jar 和分类包，编译分发 Java 示例，检查真实 Servlet HTTP/session，并要求缺失 core 时构建被拒绝。它不远端发布，也不执行 React/Node SSR。`INERTIA_CONSUMER_OUTPUT=/absolute/new-or-empty-directory` 保留稳定证据，非空输出目录被拒绝。可选 `INERTIA_CONSUMER_DEPENDENCY_CACHE` 种子排除自有 `io.inertia` 制品，确保它们重新解析。

## 区分应用与站点检查

仓库根目录的 `npm --prefix inertia-java/docs run docs:smoke` 验证文档路由、搜索、移动导航和下载，不能替代应用矩阵。前端脚本还分别检查健康/重启、构建清单、CSP 和发布切换，按修改行为选择。

浏览器与本地平台属于证据范围。不能由本地矩阵通过推断 Linux systemd、公开 TLS 或分布式会话正确。

## 上游参考

- [Playwright 浏览器文档](https://playwright.dev/docs/browsers)
