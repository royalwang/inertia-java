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
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaDiagnosticsTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/MvcObservationTest.java
translation:
  locale: zh-CN
  canonicalId: integrations/observability
  source: integrations/observability.md
  sourceRevision: 3d827e28bda00a66fb47977f04e634b48070d1eb2587acae1e8aeae8c7e618b2
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

## 定位一次访问失败

先定位失败阶段，再看整体计时：

| 信号 | 含义与下一步 |
| --- | --- |
| `inertia.props` 的 `reason=timeout` | 被选中的 provider 超出预算；检查查询时长及取消。提高 SSR 超时不能解决这个问题。 |
| `inertia.props` 的 `reason=overloaded` | 执行器或单次并发限制拒绝任务；扩容前检查活动任务、有界队列及查询扇出。 |
| `inertia.ssr_http` 的 `reason=connection` 或 `reason=timeout` | renderer 连接或响应失败；检查 Node 健康和传输预算。可选 SSR 可以回退 CSR，必需 SSR 则使渲染失败。 |
| `inertia.ssr_http` 的 `reason=response_limit` | renderer 响应超过字节上限；检查负载大小和端点契约。 |
| `inertia.session_merge` 或 `inertia.session_complete` 的 `reason=error` | session 存储失败；检查后端和事务结果。不得静默切换 store 或重试结果未知的写入。 |

provider 失败不会变成成功的 CSR 响应。修复原因后，验证下一次访问成功，并按已说明的 abort 契约保留预留的 flash/errors。响应写入失败、渲染准备成功与浏览器收到响应应分别判断。

## Prometheus 查询示例

应用可以通过自身 Boot BOM 添加 Spring Boot Actuator 和兼容的 Prometheus registry。端点暴露与访问规则由应用配置，Inertia starter 不暴露管理端点。参见 [Boot 端点配置](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)。

使用标准 Micrometer Prometheus registry 时，timer 的计数和总时长使用以秒为单位的名称。安装查询前先核对真实 scrape。以下示例描述阶段事件，不是独立请求数或生产 SLO。

按有界原因分组的 provider 每秒失败次数：

```promql
sum by (reason) (
  rate(inertia_props_seconds_count{reason=~"timeout|overloaded|error"}[5m])
)
```

SSR 传输结果每秒次数：

```promql
sum by (reason) (rate(inertia_ssr_http_seconds_count[5m]))
```

成功渲染准备的平均秒数；没有流量时，平均值没有意义：

```promql
sum(rate(inertia_render_seconds_sum{outcome="success"}[5m]))
/
sum(rate(inertia_render_seconds_count{outcome="success"}[5m]))
```

不能把 props、SSR 与 render 时长相加作为请求时长。直方图分位数需要应用配置 bucket 并使用合适 exporter；库不会自动开启直方图，也不能从 timer 总时长虚构 p95。参见 [Micrometer Prometheus timers](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html)。

## 上游参考

- [Micrometer timer](https://docs.micrometer.io/micrometer/reference/concepts/timers.html)
- [编写自动配置](https://docs.spring.io/spring-boot/3.5/reference/features/developing-auto-configuration.html)
