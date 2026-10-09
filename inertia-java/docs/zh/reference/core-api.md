---
title: "Core API 地图"
description: "索引请求、配置、上下文、响应、Page、codec、props、会话和根视图契约。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ResponseRenderer.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SessionStore.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CoreContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CancellationContractTest.java
translation:
  locale: zh-CN
  canonicalId: reference/core-api
  source: reference/core-api.md
  sourceRevision: 9754a831e5e90a070d50f57af813aa586d590beeabbff08b3bfea57768c10ff1
---

# Core API 地图

适配器能够创建请求快照、管理响应生命周期并提供会话交付语义时，可在无框架环境使用 `inertia-core`。精确签名和嵌套类型见 [Javadoc](javadoc.md)，本页说明各类型的职责。

## 请求、响应与渲染

| 公开类型 | 职责 |
| --- | --- |
| `InertiaRequest` | 不可变请求输入、规范化请求头、版本/局部选择、安全返回导航 |
| `InertiaConfig` | 应用版本、根、组件、共享数据、SSR 与展示策略 |
| `InertiaContext` | 单请求排队的共享/flash/errors/history 效果与会话预留 |
| `InertiaResponse` | 组件/props 响应、状态、允许的响应头与必需 SSR 策略 |
| `Page` | 解析后的不可变 Page 数据，防御性 JSON 复制 |
| `PageCodec` | 基于 Jackson 的 JSON 值、解析与 HTML 安全序列化 |
| `ResponseRenderer` | 选择 HTML/JSON、解析 props、准备响应交付 |
| `HttpOutcome` | 已校验状态、多值响应头与文本正文 |
| `ProtocolPolicy` | 版本冲突、redirect/location 与表示策略 |
| `RootView` | 可信完整 HTML 组装 |
| `ConfiguredHttpUrl` | 校验配置的 HTTP(S) 端点和 origin |
| `CspNonce` | 校验并携带根视图的可信 nonce |

每次请求创建新 context。`share` 和 errors 必须在解析前排队，部分 flash/history 效果允许在 resolving 状态设置。`render` 构造 `InertiaResponse`，不写传输字节。core 渲染成功时，在适配器写字节前完成预留会话快照；完成前中止或失败会恢复预留。后续写入失败不能回滚已完成交付，也不能证明浏览器收到什么。`commitRedirect` 合并效果供下一请求使用，有效果时需要会话，渲染消费 context 后不能再调用。

外部写入结果不确定时，不要将 `abort` 作为通用重试操作。准备/提交边界见[适配器所有权](../integrations/custom-adapter.md)。

## Props 与校验

| 公开类型 | 职责 |
| --- | --- |
| `Props`, `Prop` | 不可变点路径定义及选择、加载、元数据修饰符 |
| `PropsResolver` | 在截止时间、并发和取消限制下解析选中提供方 |
| `ScrollPage` | 分页元数据及可选包装项路径 |
| `ValidationErrors`, `ErrorBags` | 不可变有序消息与命名 bag 展示 |
| `PropDefinitionException` | 声明时无效路径或父子冲突 |
| `PropResolutionException` | 携带路径与 cause 的提供方失败 |

声明顺序决定成功输出顺序。致命提供方失败可以立即终止并取消兄弟任务，不等待声明顺序。`rescue` 是逐 prop 恢复，不能修复无效声明。Merge/once/scroll 修饰符指示客户端，不创建服务端持久化或授权。选择 eager、lazy、optional、deferred 前阅读 [props 加载](../props/loading.md)。

## 扩展与观测

`SessionStore` 定义预留/完成/中止/合并契约；`MemorySessionStore` 是本地实现，不是分布式持久存储。`SsrGateway` 返回渲染成功或降级；`SsrRequiredException` 防止必需 SSR 页面静默接受降级。resolver 内部管理请求拥有的取消，其实现不是公开扩展类型。

`InertiaObserver` 定义结构化有界诊断；`Observations` 隔离普通 observer 失败并分类事件；`LoggingInertiaObserver` 提供日志实现。回调内联运行，必须快速。安全标签见[指标](metrics.md)。

可执行 imports 和初始化见[规范 API 示例](../api-guide.md)。框架传输行为属于 [Spring API](spring-api.md)，不应重新实现第二套 core。
