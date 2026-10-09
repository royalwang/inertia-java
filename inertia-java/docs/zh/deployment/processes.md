---
title: "运行 Java 与 Node"
description: "在工作区之外运行发布物，安装生产依赖并监督就绪与关闭。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/deploy/runtime.mjs
  - inertia-java/deploy/systemd/inertia-java@.service
  - inertia-java/deploy/systemd/inertia-ssr@.service
verification:
  - inertia-java/deploy/verify-release.mjs
translation:
  locale: zh-CN
  canonicalId: deployment/processes
  source: deployment/processes.md
  sourceRevision: 5072a9539dfe48b23fe24613fb873f0530a9552c86a7e9642e9022a1df08b62a
---

# 运行 Java 与 Node

使用相同不可变发布物和 root/build 身份，分别监督 Java 与渲染器。渲染器故障可能允许 CSR，不应自动终止 Java。

## 本地双进程演练

预检并安装生产依赖后，在发布目录运行：

```sh
APP_PORT=18080 SSR_PORT=13714 node runtime.mjs pair
```

启动器先启动 Node，再启动 Java，验证真实同构建 `/users` SSR，并输出包含 release/build ID 和端口的 `INERTIA_READY`。这是控制台就绪记录，不是 systemd 通知或持续就绪检查。

pair 模式允许两个端口均为零，用于隔离本地演练。就绪后渲染器退出时，Java 保持可用，并报告需独立恢复；pair 不会重启 Node。

## 独立服务

在监督器下分别使用 `node runtime.mjs ssr` 与 `node runtime.mjs java`。两者选择相同固定 `SSR_PORT`、release 和 `INERTIA_ROOT_ID`。非 pair 模式拒绝 `SSR_PORT=0`。Java 可以在 Node 缺席时启动并提供默认 CSR 页面。

| 运行环境变量 | 默认值 |
| --- | --- |
| `APP_HOST` | `127.0.0.1`；支持明确的 `0.0.0.0` |
| `APP_PORT` | 8080 |
| `SSR_PORT` | 13714 |
| `INERTIA_ROOT_ID` | `app` |
| `INERTIA_ASSET_STORE` | 发布内 `assets` |

PATH 需包含 Java21 和 Node>=22.12。Node 绑定回环地址。SIGINT/SIGTERM 传给启动器拥有的子进程；Java 关闭阶段预算 5s，启动器等待 8s 再升级终止。超出预算的工作不保证排空。

## 目标宿主验证

`operations/systemd/` 模板是 Linux 起点。配置服务账号、路径与环境，在实际宿主验证 unit 语法，再测试重启、关闭和真实就绪。`Type=exec` 只确认执行，不确认就绪。macOS 本地演练不验证 Linux unit、TLS/代理或 Windows 进程清理。

启动后检查见[健康监控](../ssr/health.md)和[运维](operations.md)。应用日志与状态应放在不可变发布目录之外。
