---
title: "慢请求与过载"
description: "分离 props、数据库、SSR、响应计时，使用有界预算与现有基准工具。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/frontend/scripts/benchmark.mjs
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/HttpSsrGateway.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaProperties.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/PropsOverloadTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CancellationContractTest.java
  - inertia-java/examples/spring-react/frontend/scripts/benchmark.mjs
translation:
  locale: zh-CN
  canonicalId: troubleshooting/performance
  source: troubleshooting/performance.md
  sourceRevision: 2dba3f9797a2d7b7e24c2fd0c97bba1aae1dd3c24fce8b78ed29a10e56d1fa6b
---

# 慢请求与过载

扩大并发前先定位慢阶段。Props、渲染传输、响应交付和会话存储具有独立预算与失败方式。

## 测量边界

结合有界[阶段指标](../reference/metrics.md)、应用请求延迟及数据库/提供方指标。response timer 记录写入，不是完整业务操作。查找 prop 超时、executor 拒绝、SSR 在途过载和后端会话延迟。

确认哪些提供方被选择。Optional/deferred/partial/once 元数据改变执行时机；包装 lazy 之前就执行昂贵工作会使选择失效。验证真实提供方取消/超时，不能假设取消 future 就会终止任意阻塞数据库操作。

## 明确调优

默认每请求 props 容量为 8，共享 executor 有界。请求容量失败不是无限队列。先修复提供方成本和上游限制，再按测量需求调整预算/并发。保持响应超时 >= props 超时。更大队列可能增加延迟和内存，不增加可持续吞吐。

网关容量/响应字节限制独立于 props 配置。渲染器 CPU 瓶颈需要 Node/进程容量分析；扩大 Java 超时可能只是保留更多等待请求。判断可否降级时保留必需 SSR 行为。

## 证明改善

使用相同输入、构建、环境重复代表性负载，比较阶段分布、失败、资源和取消。覆盖过载与关闭，不只检查单次成功请求。指标与日志排除敏感 Page 数据和高基数身份。

示例 benchmark 是本地测量工具，不保证生产、其他硬件/入口/会话后端容量。记录条件，优化后保留真实浏览器正确性检查。见[异步所有权](../props/async-concurrency.md)和[网关设置](../ssr/gateway.md)。

## 运行本地 HTTP 基线

先在 `inertia-java/` 运行 `./mvnw verify`，再在 `inertia-java/examples/spring-react/frontend/` 运行 `npm ci` 与 `npm run build`。在该前端目录执行：

```sh
npm run benchmark
```

命令在隔离回环端口启动自己的 Java/Node，测量完整首次 `/users` HTML 响应，保留独特临时证据目录。`INERTIA_BENCH_OUTPUT=/absolute/parent` 指定父目录。不会使用或停止已有应用进程。

| 环境输入 | 默认 | 接受值 |
| --- | --- | --- |
| `INERTIA_BENCH_CONCURRENCY` | `1,8,32` | 去重逗号分隔整数，每项 1–64 |
| `INERTIA_BENCH_REQUESTS` | `128` | 每普通 profile 1–10000 次测量访问 |
| `INERTIA_BENCH_SLOW_REQUESTS` | `24` | 每正文停滞 profile 1–1000 次测量访问 |

普通阶段先顺序预热 8 次，停滞阶段 2 次。有效并发不超过阶段请求数。worker 等待完整响应后才发送下一个请求，因此吞吐是已达到的闭环吞吐，不是开环到达容量。

四种模式为真实渲染器、有意路由排除、渲染器连接被拒及响应头/部分正文后停滞的对端。脚本启用日志观测，本地条件是 props 3s、响应 5s、renderer 1s/16 permits 及示例有界 executor。这是测量条件，不是生产推荐。高并发下过载降级可能降低整体延迟，应结合 SSR/CSR 数和降级原因比较延迟分布。

阅读 `summary.json` 和各阶段 JSON 中 jar/build/source 身份、P50/P95/P99、响应大小/状态、SSR 比例、renderer 超时比例、客户端错误与个别样本。错误状态、缺少预期降级原因或缺失顺序 SSR 会使测量失败。失败保留 `success=false` 和已完成阶段，不复用旧成功凭据。

资源约每 200ms 用 `ps` 采样。RSS 是采样最大值；CPU 是操作系统报告的进程生命周期比例，多核可超过 100。两者都不是精确峰值或区间利用率。负载不保留会话 cookie，使用两个内存用户，数据库查询为零，不测同会话竞争、浏览器绘制/hydration、真实数据库或部署入口/存储容量。JIT/GC、日志、共享宿主负载及模式顺序会影响结果，需重复可比条件并保留[浏览器正确性检查](../testing/browser-tests.md)。
