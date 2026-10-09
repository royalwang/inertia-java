---
title: "Java API 指南"
description: "Java 核心、Spring MVC、props、会话、SSR 和验证 API 的使用与所有权。"

translation:
  locale: zh-CN
  canonicalId: legacy-api-guide
  source: api-guide.md
  sourceRevision: e4c9be60cb2944e901c684ccff67eea7f4a72916aeaeb4567e86c60288f11d34
---

# Java API 指南

本指南介绍已实现的 `0.1.0-SNAPSHOT` API，使用 Java 21、Spring Boot 3.5.7 和 Jackson 2。该版本对应源码和本地分发，不表示这些坐标已发布到 Maven Central。React/Node 示例独立锁定前端版本。已验证的客户端行为及有意保留的 Rust 差异，参见仓库兼容性矩阵。

## 选择依赖

应用自行实现 HTTP 适配器时使用 `io.inertia:inertia-core`。Servlet Spring MVC 使用 `io.inertia:inertia-spring-boot-starter`，由它提供 MVC 和自动配置依赖。需要 Page 断言时，在测试作用域添加 `io.inertia:inertia-testing`。当前检出中的模块均使用 `0.1.0-SNAPSHOT`。starter 会传递引入可选的 SSR/Vite 集成库，但不会配置或启动 JavaScript 渲染器。

```xml
<dependency>
  <groupId>io.inertia</groupId>
  <artifactId>inertia-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

消费项目需导入 Spring Boot 3.5.7 依赖 BOM。在本地构建并安装 reactor（从 `inertia-java` 运行 `./mvnw install`），或从已配置的内部仓库消费打包后的 Maven 子树。仓库托管、快照元数据、签名及命名空间授权属于独立的发布任务。不要让生产消费者依赖未经审查的本地夹具仓库。

完整的 [CoreApiExample.java](../examples/CoreApiExample.java) 和 [SpringApiExample.java](../examples/SpringApiExample.java) 可针对库制品编译。从 `inertia-java` 运行 `python3 scripts/verify-maven-consumer.py`，会将这些文件原样复制到外部消费者，通过私有 Maven 仓库和缓存解析打包的 JAR，并验证行为。运行前需要完成正常的 Maven 和前端构建。这些小示例展示不含浏览器应用的协议集成；资源、安全、hydration 和真实 Node SSR 见 `examples/spring-react`。

## 核心集成与所有权

`InertiaRequest` 对方法、绝对 URI 和请求头建立快照。请求头名称会规范化；`url()` 保留原始路径和查询字符串。请求 ID 应由服务端生成。代理信息重建由 HTTP 适配器负责。不要将 Inertia 请求头视为认证依据。

使用 `InertiaConfig.basic(version, components)` 创建包含最小根模板的不可变应用配置；需要自定义 `RootView`、`SsrGateway`、共享 props 和展示设置时，使用完整构造器。组件集合是允许列表，渲染未注册组件会失败。版本供应器必须代表部署的客户端、SSR 和资源构建。`withAllErrors`、`withSharedPropKeys` 和 `withUrlResolver` 返回新的配置值。URL 解析器改变 Page 的展示 URL，不负责路由或重定向策略。

复用 `PageCodec`、`PropsResolver`、`ResponseRenderer` 和应用配置。`InertiaContext` 与 `InertiaResponse` 属于单次请求，只能使用一次。向 `PropsResolver` 提供有界执行器，并由应用关闭。Boot starter 管理默认执行器及其生命周期。调度回调前捕获已授权的不可变数据：安全上下文、Servlet 请求和事务 ThreadLocal 不会自动传播。

独立适配器应遵循以下顺序：

1. 创建请求快照，在业务、控制器或查询工作之前调用 `ProtocolPolicy.before(request, currentVersion)`。如果返回结果，应立即写出，不要预留会话投递。
2. 对于 Page，使用请求的会话存储创建新上下文，无会话渲染则使用 `null`。调用 `renderer.render(context, response)`，并在 HTTP 传输预算内等待。渲染器在返回 `HttpOutcome` 前解析 props、渲染 HTML/JSON、应用 Page 策略并完成投递事务。
3. 对于控制器返回的重定向等 `HttpOutcome`，先调用 `context.commitRedirect()`，再在写出字节前调用 `ProtocolPolicy.after(request, outcome)`。渲染 Page 后不要再调用 `commitRedirect()`。Spring MVC 会为类型化处理器执行这些步骤。
4. 传输超时时，取消当前请求拥有的待完成工作，并调用 `context.abort()` 恢复尚未提交的预留。底层数据库和 HTTP 工作也需要设置限制。渲染成功不证明浏览器已经收到响应；会话事务完成后，网络写入失败也无法回滚该事务。

`HttpOutcome` 包含状态码、不可变的多值请求头和文本正文，不是二进制流抽象。下载、上传、REST 和 SSE 继续由宿主框架处理。`ProtocolPolicy.redirect` 创建 302；后置策略将 Inertia PUT/PATCH/DELETE 重定向改为 303，并处理片段和预取规则。`context.location` 创建外部或完整文档导航结果。重定向 URL 由应用负责；`context.back()` 只允许跳转到当前源的 Referer，否则使用 `/`。

## Page 与上下文调用

| API | 用途与生命周期 |
| --- | --- |
| `context.render(component, props)` | 创建请求专属的 Page 响应，尚未执行渲染 |
| `context.share(key, value)` | 在解析开始前定义共享 props |
| `context.flash(key, value)` | 排队副作用，同一上下文内键重复会失败 |
| `context.withErrors(...)` | 渲染前排队默认、命名或多消息错误 |
| `context.clearHistory()` / `preserveFragment()` | 排队客户端指令，可通过重定向携带到下一页 |
| `context.encryptHistory(boolean)` | 覆盖本次请求的 Page 设置，不跨重定向持久化 |
| `response.status(int)` / `withHeader(name, value)` | 设置业务状态和响应头，协议管理的响应头不可覆盖 |
| `response.withViewData(name, value)` | 提供根模板数据，不会自动成为 Page props |
| `response.flash(key, value)` | 当前 Page 的 flash，不是跨请求队列 |
| `response.encryptHistory(boolean)` / `preserveBigIntegers(boolean)` | 覆盖应用或 Page 展示默认值 |
| `response.clearHistory(boolean)` | 设置当前响应的客户端指令 |
| `response.withoutSsr()` | HTML 使用 CSR 外壳，JSON 访问不受影响 |
| `response.requireSsr()` | HTML 必须使用 SSR；网关回退变为安全的 503，JSON 仍可访问 |

`requireSsr` 和 `withoutSsr` 是有顺序的构建选项，最后一次调用生效。响应的历史设置覆盖上下文设置，上下文设置覆盖配置。共享 prop 的优先级依次为内部 `errors`、配置共享值、请求共享值和 Page props。相同键保留首次声明的位置和最后一次定义，只有最终获胜的供应器执行。`errors` 可以覆盖，因此依赖内置校验的应用应保留该键。父子路径冲突会在执行前失败；普通对象深合并则是独立的客户端指令。

## Prop 选择与异步工作

```java
Props props = Props.builder()
    .put("title", "Users")
    .put("rows", Prop.lazy(() -> java.util.List.of("Ada", "Linus")))
    .put("details", Prop.optional(() -> java.util.Map.of("enabled", true)))
    .put("statistics", Prop.defer(() -> 42).group("statistics"))
    .put("status", Prop.always("ready"))
    .build();
```

| 工厂 | 完整访问 | 匹配的局部访问 |
| --- | --- | --- |
| 普通值 / `Prop.value` | 包含 | 被选中时包含 |
| `Prop.lazy(Task)` | 执行 | 被选中时执行 |
| `Prop.async(Supplier<CompletionStage<?>>)` | 调度工厂 | 被选中时调度 |
| `Prop.optional(Task)` | 省略且不执行回调 | 被选中时执行 |
| `Prop.defer(Task)` | 省略并生成延迟组元数据 | 被选中时执行 |
| `Prop.always(value)` | 包含 | 即使显式排除也包含 |

局部请求必须指定相同组件。点路径选择匹配祖先和后代；except 排除指定路径及其后代。只指定 except 的局部请求会选择其余全部 props，包括 optional 回调，这与本仓库 Rust 实现一致。要避免该查询，应在 `except` 中显式排除。嵌套解析值遵循父级选择。选择不能替代授权：先完成授权再定义数据，不要依靠 optional 或 deferred 标记保护敏感信息。

解析器在一次总截止时间和单请求并发上限内并行执行兄弟工作。执行器拒绝、过载、超时和未救援的失败都会使操作失败；致命失败会取消请求拥有的兄弟工作。成功值和元数据保留声明顺序，但并行失败的选择顺序不固定。异步供应器应返回请求专属 stage，不要返回取消后会影响其他请求的共享 future。底层提供者必须实现自己的超时和取消。

`.rescue()` 仅适用于延迟 props。被救援的失败会省略该字段，并生成 `rescuedProps` 元数据；它不会把授权错误变成普通 SSR 回退。只对明确可选的业务信息使用此功能。

## 合并、once、滚动与大整数

这些 API 为官方客户端生成协议元数据；服务端不维护浏览器侧的合并集合。

| 定义 | 行为 |
| --- | --- |
| `Prop.value(rows).merge()` | 在匹配的局部访问中追加根集合 |
| `Prop.value(rows).prepend()` | 在根集合前插入 |
| `Prop.value(profile).deepMerge().matchOn("members.id")` | 递归合并对象，按 ID 匹配嵌套集合成员 |
| `.appendAt("data")` / `.prependAt("data")` | 在内部路径合并；使用它们避免组合不兼容的合并模式 |
| `.matchOn("id")` | 按相对路径匹配条目，需要先设置合并选项 |
| `Prop.lazy(task).onceAs("catalog").until(Duration.ofMinutes(5))` | 声明客户端复用键和 TTL |
| `.fresh()` | 客户端已有键时仍强制生成新的 once 值 |
| `Prop.scrollWith(task)` / `Prop.scroll(page)` | 输出分页元数据及与方向匹配的合并指令 |

路径相对于 prop。追加其他合并模式可能替换之前的选项，不要假设每个修饰器都会累积。`X-Inertia-Reset` 会抑制重置路径的合并元数据，使客户端替换状态。`ScrollPage` 携带数据、页名、上一页、下一页、当前页和包装键，默认包装键为 `data`。页码和游标属于应用数据，库不会查询数据库。

Once 是客户端复用机制，不是服务端缓存或授权策略。需要某个共享 once prop 的每页都应声明它；Page 局部 once 值不会自动成为全局值。TTL 必须非负，使用服务端 Clock 元数据；请求和响应差异记录在兼容性矩阵中。可运行示例中的 Feed 和 Advanced 展示真实客户端追加、前插、重置、嵌套匹配和重复访问。

ID 超出 JavaScript 安全整数范围时，在配置或响应上启用 `preserveBigIntegers`。编解码器输出锁定客户端要求的 bigint 表示，实际 Node 和客户端入口必须使用匹配的还原路径。还原前不要通过 JavaScript Number 转换标识符。`PageCodec.htmlJson` 保护 Page 脚本边界；自定义根模板仍需转义原始用户字符串。

## 会话、校验与 MVC 异常

`MemorySessionStore` 是单节点实现，应关联到单个用户的存储域，不要让所有用户共用一个全局存储。`HttpSessionStore` 提供带命名空间的 Servlet 集成，Boot 会注册会话互斥监听器。独立 MVC 集成还必须注册 `HttpSessionMutexListener`。命名空间由 1–64 个安全标识符字符组成，在同一请求和 advice 链中必须一致。

SPI 原子地开始一次投递预留，每个令牌最多完成或中止一次，并合并新排队的副作用。渲染失败时，已预留的投递会恢复，失败请求中待处理的副作用会丢弃。存储错误会报告，不会对结果未知的操作盲目重试。这不构成分布式或网络层恰好一次保证。无会话 Page 可以渲染，但没有会话时提交跨重定向 flash 或错误会失败。

`ValidationErrors` 保留有序消息。`ErrorBags` 合并默认和命名错误包。存在默认错误时，它们优先，并归入请求指定的 `X-Inertia-Error-Bag`；否则按名称投递命名错误包。默认展示第一条消息；`withAllErrors(true)` 在 Page 中保留全部消息。`ValidationBridge.errors(BindingResult)` 和可选的 `JakartaValidationBridge.errors(violations)` 复制消息与路径，不包含被拒绝的值。使用 Jakarta 校验时，需另外添加校验提供者。校验通过重定向返回 Page，普通 REST 校验保持 Spring 语义。

控制器使用普通 `@Controller`，同步返回未包装的 `InertiaResponse` 或 `HttpOutcome`，通过处理器参数注入 `InertiaContext` 和 `InertiaRequest`。对类型化 Page 处理器，`@ResponseBody`、`@RestController`、`ResponseEntity<Page>`、异步 Page 包装及不兼容的泛型包装会在启动时被拒绝。普通 REST 和异步传输处理器继续由宿主负责。

应用的 `@ExceptionHandler` 优先。类型化 Page advice 创建新的无会话错误上下文，将原投递留给后续成功页面。类型化 outcome advice 创建新上下文，保留原会话存储和命名空间以处理自身重定向副作用，但不会替换已经失效的会话。支持局部、全局及继承的泛型类型化处理器。应用处理失败时，`InertiaErrorPage` 提供最终安全 Page；不要把异常文本放入它的 props，而且该 Page 失败后不会递归再次渲染。

## Boot 配置与 Bean 替换

定义应用的 `InertiaConfig` Bean。starter 不会从通用属性推导组件注册、模板、资源或 SSR 端点。经过校验的执行属性如下：

| 属性（`inertia.` 前缀） | 默认值 |
| --- | --- |
| `props-timeout` / `response-timeout` | `3s` / `5s` |
| `props-concurrency` | `8` |
| `executor-core-size` / `executor-max-size` | `8` / `32` |
| `executor-queue-capacity` | `256` |
| `session-namespace` | `default` |
| `all-errors` | 未设置，使用配置值 |

超时和限制必须为正，最大线程数不能小于核心线程数，响应超时不能小于 props 超时。提供名为 `inertiaPropsExecutor` 的 `ExecutorService` Bean 即可替换执行器。用户提供的 `PageCodec` 会在默认 props、渲染、MVC 和 advice 中共享；它复制传入的 ObjectMapper，不会修改 REST 转换器。多个 Bean 存在歧义时需使用 `@Primary`。

用户提供的 `PropsResolver`、`ResponseRenderer` 和 `InertiaMvcConfigurer` Bean 会替换默认实现，此时应用负责保持依赖连接一致。自定义 MVC 配置器可以使用 `(config, renderer, deadline, errorPage, namespace, codec)` 保留配置的编解码器。旧构造器仍可用，并保持默认编解码器行为。观察器、Micrometer 连接方式及当前事件语义见 README。

## SSR、Vite 与根模板

复用一个 `HttpSsrGateway`，配置受信端点、连接和渲染超时、响应大小上限、并发、编解码器、构建验证及根 ID。网关直接 POST Page JSON，不带凭据，不跟随重定向，也不重试渲染。`SsrEndpointResolver` 选择生产 `/render` 端点或显式启用的受信开发热端点，并应用配置的排除规则。bundle 缺失、渲染器返回 null、格式错误、响应过大或传输失败，会生成分类回退；props 失败在此前仍作为失败处理。`SsrHealthMonitor` 是可选、由应用管理且可关闭的探针；健康状态不能证明特定 Page 一定可渲染。

`ViteBuild` 验证同一组客户端、SSR 和构建回执。`ViteAssets` 从生产 manifest 或显式启用的开发 hot 文件生成标签。应用必须挂载不可变资源目录，使版本、Node bundle、入口、根 ID 和构建 ID 一致。生产环境不要读取 hot 文件。`RootView.View` 提供 Page、受信 SSR head/body、模板数据和 CSP nonce。提供的 body 只插入一次，因为锁定的渲染契约已经包含预期 Page 脚本和根节点。其他模板数据需转义。最小根模板不包含应用客户端资源标签，因此独立 API 示例并不是完整浏览器应用。

完整 React/Node 启动、不可变发行包、进程管理、资源保留和故障演练见分发包的 README 和 RUNBOOK。认证、CSRF、HTTPS/Cookie、代理信任和生产存储策略仍由应用负责；starter 不能替代安全过滤链。

## 断言与验证范围

`AssertablePage.fromBody(body)` 接受原始 Page JSON 或标准 HTML Page 脚本，然后支持 `.component("Home")`、`.equals("/props/message", "Hello")` 和 `.missing("/props/details")`。路径采用 JSON Pointer。`.data()` 返回副本。此辅助工具不验证 HTTP 状态和响应头、自定义模板、浏览器 hydration 或客户端投递；这些应在各自边界断言。

生成的源码和 Javadoc 分类制品为全部运行时模块提供符号索引。本指南解释使用方式和所有权，兼容性矩阵与实现记录则说明已验证场景及尚待确认的事项。本地验证和依赖声明不授予发布或许可授权。
