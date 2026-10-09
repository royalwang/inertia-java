---
title: 配置参考
description: 区分 Boot 正式属性、应用核心配置、组件参数与示例专用开关。
version: 0.1.0-SNAPSHOT
translation:
  locale: zh-CN
  canonicalId: reference/configuration
  source: reference/configuration.md
  sourceRevision: 872de03bf52dab3aee162b2d05ddfb46a634a0f6590dcccb01a63239c6f4063a
sources:
- inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaProperties.java
- inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaAutoConfiguration.java
- inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaConfig.java
- inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
verification:
- inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaOverridesTest.java
- inertia-java/inertia-core/src/test/java/io/inertia/core/ConfigPresentationTest.java
---

# 配置参考

设置值之前先确定配置归属。starter 绑定执行预算，不会把所有核心构造参数自动变成 Spring 属性。

## Boot 正式属性

`InertiaProperties` 在 `inertia` 下绑定以下八项。自动配置用它们创建默认 bean；替换 bean 后由替换实现自己负责相应设置。

| 属性 | 类型 / 默认值 | 约束与效果 |
| --- | --- | --- |
| `inertia.props-timeout` | Duration / `3s` | 正值；被选中 props 的解析预算 |
| `inertia.response-timeout` | Duration / `5s` | 正值且 >= props timeout；MVC 响应预算 |
| `inertia.props-concurrency` | int / `8` | >=1；每请求异步容量 |
| `inertia.executor-core-size` | int / `8` | >=1；默认共用 executor |
| `inertia.executor-max-size` | int / `32` | >= core size |
| `inertia.executor-queue-capacity` | int / `256` | >=1；有界共用队列 |
| `inertia.all-errors` | Boolean / 未设置 | 未设置保留核心配置；true/false 均为显式覆盖 |
| `inertia.session-namespace` | String / `default` | 1–64 字符；`[A-Za-z0-9][A-Za-z0-9._-]{0,63}` |

`inertia.ssr-endpoint-id` 由自动配置另外读取，默认 `renderer`。必须以字母开头，最多 64 个字母、数字、下划线、点或连字符。它是诊断标签，不是 endpoint URL，也不是 `InertiaProperties` 的第九个字段。

以下 `application.yml` 片段显式设置三个默认执行值。其余设置保持默认；不设置 `inertia.all-errors` 可保留应用拥有的核心配置。

```yaml
inertia:
  props-timeout: 3s
  response-timeout: 5s
  props-concurrency: 8
```

## 应用拥有的核心配置

提供 `InertiaConfig` bean，包含 version supplier 和组件注册。`basic(version, components)` 选择 root `app`、最小 root view、无 gateway、空 shared data；关闭大整数保留、history encryption 和 all-errors；显示 shared-prop keys，使用请求 URL resolver。

| Record 字段 | 含义 / 约束 |
| --- | --- |
| `version` | 当前 client 资源版本；同一构建应稳定 |
| `rootId` | root 标识，`[A-Za-z][A-Za-z0-9_-]*` |
| `components` | 允许的组件集合 |
| `rootView` | 完整 HTML 组装及可信资源标签 |
| `gateway` | 可选 renderer；null 允许 CSR |
| `shared` | 当前请求的共享 props 函数 |
| `preserveBigIntegers` | 大整数序列化呈现策略 |
| `encryptHistory` | 官方客户端默认历史策略 |
| `allErrors` | 默认校验错误呈现策略 |
| `exposeSharedPropKeys` | 是否列出 shared keys 元数据；不删除实际值 |
| `urlResolver` | Page URL；非空白、<=8192 字符、不含控制字符 |

`withAllErrors`、`withSharedPropKeys`、`withUrlResolver` 返回不可变副本。请求的 `encryptHistory(...)` 覆盖本次响应默认值，不通过重定向持久化。见 [Boot 配置所有权](../integrations/spring-boot.md)。

## 组件参数与生命周期

`HttpSsrGateway` 构造器接收 endpoint、connect/render duration、codec、build verification，以及可选 root verification/byte/concurrency limits。这些是构造参数，不是通用 Boot 属性。示例显式使用 200ms connect、1s render、2MiB response 和 16 个并行 transport；gateway 应在应用生命周期内复用，当前没有公开 `close()` 方法；独立拥有的健康监测器才提供显式关闭契约。

`SsrHealthMonitor` 独立接收 endpoint/connect/timeout/interval/codec；示例为 200ms/1s/5s，并在关闭时释放 monitor。`ViteBuild`/`ViteAssets` 读取实际 frontend output 和开发 hot-file，不根据 URL 猜测兼容构建。见 [SSR/Vite API](ssr-vite-api.md)。

## 示例专用开关

下列 JVM system properties 属于示例 `Application`，必须放在 **`-jar` 之前**：

| 属性 | 默认值 |
| --- | --- |
| `inertia.frontend` | 绝对化的 `frontend` 目录 |
| `inertia.development` | false |
| `inertia.root-id` | `app` |
| `inertia.ssr` | `http://127.0.0.1:13714/render` |
| `inertia.ssr-health` | renderer origin 的 `/health` |
| `inertia.asset-store` | frontend `.inertia/assets` |

其他示例环境开关包括 `inertia.ssr-except`（默认空，支持精确/尾部 `*` 规则）、`inertia.ssr-health-enabled`、`inertia.benchmark-observations`、`inertia.demo-auth`、`inertia.demo-failures`、`inertia.demo-history-enabled` 和 `inertia.csp.enabled`（布尔开关默认 false）。`inertia.demo-password` 没有安全的内置密码，demo 认证要求至少 12 字符；`INERTIA_DEMO_PASSWORD` 通过 Spring Environment 映射。

Node 使用 `SSR_PORT`（13714）和 `SSR_ROOT_ID`（`app`）；`INERTIA_DEV_APP_ORIGIN` 控制示例 Vite 开发 CORS origin。发布 launcher 环境见[进程配置](../deployment/processes.md)。文档工具的 `INERTIA_DOCS_BASE`、`INERTIA_DOCS_OUTPUT`、`INERTIA_BROWSER_CHANNEL` 不属于业务应用配置。

非法预算/名称会使启动失败。缺少 custom bean 或 renderer build 不匹配，应修正相应输入，不能靠放宽超时修复。override tests 区分未设置与显式 false。
