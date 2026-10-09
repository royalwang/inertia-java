---
title: 错误与 SSR 降级原因
description: 按失败阶段选择诊断与恢复步骤。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: reference/errors
  source: reference/errors.md
  sourceRevision: 88d92bb6d8f98c5b38244a12003662298d63cae0ff14f3c85ebab969716b9354
sources:
- inertia-java/inertia-core/src/main/java/io/inertia/core/PropDefinitionException.java
- inertia-java/inertia-core/src/main/java/io/inertia/core/PropResolutionException.java
- inertia-java/inertia-core/src/main/java/io/inertia/core/SsrRequiredException.java
- inertia-java/inertia-core/src/main/java/io/inertia/core/Observations.java
- inertia-java/inertia-ssr-http/src/main/java/io/inertia/ssr/HttpSsrGateway.java
verification:
- inertia-java/inertia-core/src/test/java/io/inertia/core/RequiredSsrTest.java
- inertia-java/inertia-ssr-http/src/test/java/io/inertia/ssr/HttpSsrFailureTest.java
- inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
---

# 错误与 SSR 降级原因

先判断失败发生在哪个阶段。renderer 降级、prop 解析失败和 transport 写出失败，需要不同恢复方式。

## 公开异常

| 类型 | 含义 | 处理 |
| --- | --- | --- |
| `PropDefinitionException` | `INVALID_PATH` 或 `PARENT_CHILD_CONFLICT`，path/source 指出声明位置 | 修正声明后再运行 |
| `PropResolutionException` | 被选中的 provider 失败，包含 prop path 与 cause | 检查 provider/预算，使用有意识的 rescue 策略 |
| `SsrRequiredException` | 必须 SSR 的响应得到不可用/fallback 结果 | 恢复兼容 renderer，或明确改变页面策略 |
| `IllegalArgumentException` | 非法预算、URL、root、namespace、header 或 Page 输入 | 修正输入源 |
| `IllegalStateException` | 已关闭/复用 context、重复 flash 或非法生命周期 | 修正所有权，不复用 context |

内部 cause 供可信诊断，不应把异常消息、请求头或 Page 数据直接序列化为公开错误。MVC resolver 负责安全 outcome，也可接入应用 error Page。

## Renderer fallback 字符串

| 原因 | 检查点 |
| --- | --- |
| `disabled` | 是否配置 gateway |
| `excluded-or-unavailable` | endpoint 排除策略及可用性 |
| `overloaded` | 在途容量和工作耗时 |
| `transport-or-timeout` | connect/render 预算、transport 与取消 |
| `http-status` | renderer 返回非 2xx |
| `warming-up` | renderer 尚未就绪，返回 JSON null |
| `invalid-response` | head/body 形状不合法或 body 空白 |
| `invalid-json` | 响应不是合法 JSON |
| `build-mismatch` | Java 与 renderer 构建身份不同 |
| `root-mismatch` | Java 与 renderer root 身份不同 |

默认页面可以通过 CSR 降级；required 页面则产生结构化失败。gateway 接受有效 2xx，独立健康采样要求 200 和 `status: OK`，两者不是同一契约。

## 观测分类

`InertiaObserver.Reason` 包含 `NONE`、`ERROR`、`PROP_DEFINITION`、`PROP_OVERRIDE`、`ERRORS_OVERRIDE`、`TIMEOUT`、`CANCELLED`、`OVERLOADED`、`DISABLED`、`EXCLUDED_OR_UNAVAILABLE`、`TRANSPORT_OR_TIMEOUT`、`TRANSPORT`、`CONNECTION`、`RESPONSE_LIMIT`、`HTTP_STATUS`、`WARMING_UP`、`INVALID_RESPONSE`、`INVALID_JSON`、`BUILD_MISMATCH`、`ROOT_MISMATCH`、`VERSION_MISMATCH` 和 `UNKNOWN`。

transport 事件对 connection/response-limit 的区分，可能比公开 fallback 字符串更细。未知字符串归为 `UNKNOWN`，不能当作渲染成功。session abort 清理失败在适用时保留为原始异常的 suppressed cause。存储结果未知时，需要按后端语义对账，不能盲目重放。

使用 [SSR 排查](../troubleshooting/ssr-hydration.md)或[session 排查](../troubleshooting/forms-session.md)，修复后通过实际请求和受影响阶段的验证证明恢复。
