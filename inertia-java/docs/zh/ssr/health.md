---
title: "渲染器健康与恢复"
description: "后台探测并独立监督，验证停止、watch 重启和恢复。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/SsrHealthMonitor.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
verification:
  - inertia-java/examples/spring-react/frontend/scripts/verify-ssr-health.mjs
translation:
  locale: zh-CN
  canonicalId: ssr/health
  source: ssr/health.md
  sourceRevision: 14e5181b8363ab695aa9244852dafb3979d8d7a00ce53f24a749ab8d669770f4
---

# 渲染器健康与恢复

渲染器可用性、Java 存活及单 Page 渲染应分开监控。缓存的 UP 表示对端回答了健康协议，不保证所有组件或构建可渲染。

## 启用示例监控

示例默认关闭健康监控。Java 使用 `--inertia.ssr-health-enabled=true` 启动时创建可关闭的 `SsrHealthMonitor`。默认目标是渲染器 origin 的 `/health`；示例 JVM 属性 `-Dinertia.ssr-health=...` 可覆盖，需置于 `-jar` 之前。

示例连接预算 200ms、探测 1s，每次检查后延迟 5s。`/api/ssr-health` 返回缓存 state/reason/timestamp，读取不会额外发起探测。`/api/health` 独立表示 Java 存活。Vite 开发插件不自动提供独立健康协议。

## 状态与生命周期

| 状态 | 含义 |
| --- | --- |
| `UNKNOWN` | 尚无完成的检查 |
| `UP` | HTTP 200 且响应符合健康协议 |
| `DOWN` | 有界探测失败或响应无效 |
| `STOPPED` | 监控已关闭 |

应用负责 `start()` 和 `close()`。关闭会取消待执行任务并停止 scheduler，迟到结果不能让已停止监控复活。响应大小受限，不跟随重定向。

按 Page 策略决定就绪状态。页面支持 CSR 时，Node 故障期间服务可以继续存活。要求 SSR 的应用可使用更严格就绪策略，但仍应暴露 Java 存活用于诊断。

## 明确恢复进程

`npm run ssr:watch` 对构建输出使用 Node watch，只重启 Node，不编译 TypeScript。源码开发使用 Vite，生产使用进程监督器。重启期间保持构建输入一致。

`npm run test:ssr-health` 启动自己拥有的 Java/Node watch 进程，检查 bundle 触发重启和 UP→DOWN→UP，确认停机期间 Java/CSR 可用且恢复后 SSR 正常。输入拒绝场景还验证无效解码 envelope 不会破坏后续有效渲染。本地验证面向 macOS/Linux；Windows 子进程树需另行验证。监督见[部署手册](https://github.com/royalwang/inertia-java/blob/main/inertia-java/deploy/README.md)。

## 上游参考

- [Node 原生 watch 模式](https://nodejs.org/api/cli.html#--watch)
