---
title: "日志与 Micrometer"
description: "接入 observer，限制指标标签，区分 HTTP 与渲染结果并排除敏感数据。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaObserver.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/LoggingInertiaObserver.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/MicrometerInertiaObserver.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaMetricsAutoConfiguration.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/ObservationContractTest.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaMetricsTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/MvcObservationTest.java
translation:
  locale: zh-CN
  canonicalId: integrations/observability
  source: integrations/observability.md
  sourceRevision: 0fe384f7e0fca7bfb65641973282bf6e7af97ddcdf57a3715aa98ed81c340cdc
---

# 日志与 Micrometer

观测生产阶段时，不把用户 Page 数据复制进诊断。渲染、SSR 传输、会话交付与 HTTP 写入的成功边界不同，解释计数和延迟时应分开。

## 接入统一 observer

core 构造器接受可选 `InertiaObserver`，旧构造器使用 no-op。向 props resolver 和 renderer 传入同一 observer；应用自行创建 HTTP SSR gateway 或 redirect context 时也需明确传入。`LoggingInertiaObserver` 通过 `System.Logger` 输出结构化 JSON；`InertiaObserver.combine` 组合多个 observer。

Observer 内联执行，必须快速且不阻塞。运行时异常与业务结果隔离，但不会吞掉致命 JVM 错误。不要在回调里直接进行网络导出。

Boot 将应用 observer bean 传入默认 resolver/renderer。classpath 有 Micrometer，且存在唯一候选或 primary `MeterRegistry` 时，若无自定义 observer，就提供 `MicrometerInertiaObserver`。无 registry、registry 不明确或无 Micrometer 时使用 no-op。starter 不安装 Actuator、不创建 registry、不公开端点，也不改变安全设置。

## 安全事件字段

事件通过服务端生成的 request ID 关联，包含有界 operation/outcome/reason/status/response 信息、耗时、组件和安全配置的端点 ID，不包含 URL、请求头、props、flash、异常文本或渲染 body。组件和端点 ID 应来自可信配置。这些排除约束针对库内发布者：Event 构造器不清洗自定义字符串，日志 observer 对传入字段也不额外脱敏。

Micrometer 只使用有界 `outcome`、`reason`、`response` 和 `status` 标签。请求 ID、组件名、端点 ID 不成为指标标签。应用 common tags/MeterFilters 由应用负责。`inertia.ssr-endpoint-id` 标记事件，直接读取 Environment，不是 `InertiaProperties` 的第九个字段。

## 理解阶段

- Props 与 SSR timer 是一个 Page 的不同阶段，不能将计数相加作为用户请求数。
- `inertia.ssr_http` 区分传输、超时、取消、过载和解码后的降级原因。
- render 成功表示产生结果，不表示已交付浏览器。
- `inertia.response` 测量适配器写入尝试；成功写出安全 500 仍是该写入阶段成功。
- Prop 覆盖诊断描述规划，即使后续 partial 访问排除该 key。

[现有 timer 清单](https://github.com/royalwang/inertia-java/blob/main/inertia-java/README.md#observation-spi)列出名称与详细语义。指标/观测测试检查接线、隐私和有界标签。替换默认 bean 后，自行维持 observer 注入，并验证成功与失败事件。

## 上游参考

- [Micrometer timer](https://docs.micrometer.io/micrometer/reference/concepts/timers.html)
- [编写自动配置](https://docs.spring.io/spring-boot/3.5/reference/features/developing-auto-configuration.html)
