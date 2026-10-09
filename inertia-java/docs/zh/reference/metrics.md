---
title: "指标参考"
description: "查阅名称、标签、单位和计数，区分 SSR HTTP、渲染与响应写入。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/MicrometerInertiaObserver.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaMetricsAutoConfiguration.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaObserver.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Observations.java
verification:
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaMetricsTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/ObservationContractTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/MvcObservationTest.java
translation:
  locale: zh-CN
  canonicalId: reference/metrics
  source: reference/metrics.md
  sourceRevision: b20fc76fb58580fa7edd7620fa9583a9e7b42eeac09c5e9477fa60ab16f27393
---

# 指标参考

Micrometer 支持是可选的。集成发布有界阶段 timer，不安装 exporter、管理端点或请求追踪系统。

## Timer

`MicrometerInertiaObserver` 将操作映射为 `inertia.` 加小写枚举名：

| Timer | 操作 |
| --- | --- |
| `inertia.props` | 已选择 prop 解析 |
| `inertia.prop_override` | Prop 覆盖诊断 |
| `inertia.ssr` | 渲染器决策与结果 |
| `inertia.ssr_http` | HTTP 渲染传输 |
| `inertia.render` | Page/渲染准备 |
| `inertia.session_begin` | 预留交付快照 |
| `inertia.session_complete` | 完成成功交付 |
| `inertia.session_abort` | 恢复中止的交付 |
| `inertia.session_merge` | 合并重定向效果 |
| `inertia.version_conflict` | 过期版本处理 |
| `inertia.response` | 适配器响应写入 |

Timer 记录非负纳秒，由 registry 后端转换。标签为 `outcome`、`reason`、`response` 和 `status`。outcome 包括 SUCCESS/FAILURE/TIMEOUT/CANCELLED/FALLBACK/CONFLICT；response kind 包括 NONE/HTML/JSON/REDIRECT/LOCATION/OTHER。实际标签 token 是小写，如 `success`、`html`、`build_mismatch`，上述大写表示枚举常量。status 限于 100–599，不可用时为 0。见[原因定义](errors.md)。

## 启用与解释

存在 Micrometer 和明确唯一 registry 时，自动配置提供 observer，除非被覆盖。自定义 observer 可以用 `InertiaObserver.combine(...)` 组合日志与指标。示例的 `inertia.ssr-endpoint-id` 默认 `renderer`，用于事件身份；端点 URL 从不成为指标标签。

事件 request ID、组件和端点 ID 支持可信诊断，但不进入 timer 标签。事件省略 URL、请求头、props、flash 和异常文本。自定义标签要有界，避免用户/会话 ID。Observer 内联运行，慢 sink 应用明确有界策略卸载。普通运行时观测失败被隔离，致命 JVM 错误不会被吞掉并视为成功埋点。

响应写入成功可能交付应用 500，不表示业务成功。渲染器降级 timer 可以伴随有效 CSR 响应。Props/SSR/render/response 是不同阶段，不应相加为独立请求时长。结合 HTTP/应用指标关联总体结果。

## 验证指标

修改标签或条件后运行指标自动配置测试；改变事件位置后运行 core/MVC/HTTP 观测契约。真实 exporter 需单独启动，检查样例序列的标签是否有界。本地测试证明事件和 registry 行为，不证明遥测后端可用。
