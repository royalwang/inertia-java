---
title: "生产运维检查清单"
description: "检查目标 Linux 配置、资源预算、日志、故障恢复和基线测量。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/deploy/README.md
  - inertia-java/deploy/runtime.mjs
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaObserver.java
verification:
  - inertia-java/deploy/verify-release.mjs
  - inertia-java/scripts/dependency-inventory.py
translation:
  locale: zh-CN
  canonicalId: deployment/operations
  source: deployment/operations.md
  sourceRevision: c2399dcd0da5887989364ba2603151e6c4fff76c5dd2273d2e94c78a691db805
---

# 生产运维检查清单

按实际 Page 策略和存储需求运行服务。本地演练证明特定软件边界，生产就绪还需要目标宿主与入口证据。

## 接入流量前

| 范围 | 所需证据 |
| --- | --- |
| 发布 | 可信不可变内容、预检成功、客户端/SSR/build/root 一致 |
| 依赖 | 锁定生产安装、必要归属与审查 |
| 资源 | 有界 Java props/executor/传输、Node 进程限制和日志目的地 |
| 身份 | 真实授权、CSRF/cookie/TLS 策略及会话连续方案 |
| 资源 URL | 经过入口访问当前和保留的旧 URL |
| 恢复 | 已知回滚进程对、监督器重启与关闭行为 |

缓存健康响应不能证明 Page 可渲染。检查有代表性的实际 SSR 和客户端导航。支持 CSR 时，Java 存活应独立于渲染器健康；要求 SSR 的应用可以选择更严格就绪策略。

## 监控与响应

观察 props/deadline/过载、SSR 降级原因、会话失败和响应写入结果。不要将请求 ID 或组件名变成高基数标签。安全 500 成功写入不表示业务请求成功，阶段指标需要结合应用请求上下文。

Node 故障时确认 Java 存活，并判断哪些页面允许 CSR。恢复匹配构建的渲染器后检查真实内容。过载增加时，扩队列之前先检查被选择查询及提供方超时；积压增大会增加延迟和内存压力。

存储失败且结果未知时，需按后端方式对账。不要因为浏览器显示错误，就盲目重放写入或会话合并。

## 演练修改

修改启动器/打包后运行独立部署验证；修改行为后运行应用浏览器场景；修改 core 边界后运行对应契约。依赖图或构建输入改变时运行依赖清单。目标宿主 systemd/代理/TLS 与物理基础设施不由本地命令模拟。

诊断见[指标](../reference/metrics.md)、[错误原因](../reference/errors.md)和[排错](../troubleshooting/startup.md)。事件记录 release/build 身份，不记录敏感 Page 载荷。
