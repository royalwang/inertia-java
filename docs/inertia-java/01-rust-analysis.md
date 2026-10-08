# Rust 实现分析

## 1. 项目性质

`inertia-omega` 是 Inertia 服务端协议库，不是完整业务应用，也不是 Rust 内执行 JavaScript 的 SSR 引擎。`Cargo.toml` 将框架无关核心、Axum、tower-sessions 和 reqwest SSR 分为特性；默认开启后三项。库名为 `inertia`，Rust 最低版本为 1.88。

控制器返回待渲染 `Response`，中间层在控制器之后解析 props 并选择 HTML 或 JSON。首次访问可调用 Node/Vite SSR，后续 Inertia 访问返回 Page JSON。根视图仅组装文档。

## 2. 源码映射

以下路径均相对仓库根目录。

| 文件 | 实际职责 | Java 对应设计 |
|---|---|---|
| `src/config.rs` | 版本、共享数据、URL、root id、根模板、SSR 默认策略 | 不可变 InertiaConfig 与 builder |
| `src/request.rs`、`src/header.rs` | 一次性解析协议头、partial、reset、once、prefetch | InertiaRequest、ProtocolHeaders |
| `src/inertia.rs` | 每请求共享 Context，pending props/flash/errors，commit | InertiaContext 与待消费状态 |
| `src/response.rs` | 待渲染响应→Page→HTML/JSON，会话消费与优先级 | InertiaResponse、PageAssembler、ResponseRenderer |
| `src/protocol.rs` | before/after、版本冲突、重定向和 Vary | ProtocolPolicy |
| `src/page.rs` | 强类型 Page 和扁平化 metadata | Page、PageMetadata、PageCodec |
| `src/props/prop.rs`、`resolver.rs` | 数据来源和行为组合、筛选、并发执行、元数据 | Prop、PropOptions、PropsResolver |
| `src/session/mod.rs` | get/put/pull，会话 key 和内存实现 | SessionStore、MemorySessionStore |
| `src/ssr.rs` | Gateway 抽象及 HTTP 实现 | SsrGateway、HttpSsrGateway |
| `src/view.rs`、`src/json.rs` | 根 HTML、安全 JSON、大整数 | RootView、HtmlSafeJson、BigIntegerCodec |
| `src/axum/*` | extractor、响应转换、tower layer | Spring 参数与返回值处理器、协议拦截器 |
| `src/testing.rs`、`tests/*` | Page 断言和核心/HTTP 行为测试 | AssertablePage、合同 fixtures、MockMvc |

## 3. 协议与行为

- `before` 只对 Inertia GET 检查版本；不一致则 409 + `X-Inertia-Location`，不执行控制器，并附服务端版本头。
- `after` 合并 `Vary: X-Inertia`；Inertia 空 200 回跳；PUT/PATCH/DELETE 的 302 转 303；带 fragment 的重定向在非 prefetch 时转 409 + `X-Inertia-Redirect`。
- Inertia 请求识别依据是头是否存在，而非值严格等于 `true`。Java 是否严格校验需要明确兼容策略。
- Page 必需字段是 component、props、url、version。metadata 扁平输出，空集合与 false 标志省略；once 的 expiresAt 无期限时为 null。
- 大整数可递归转换为 `{"$bigint":"..."}`，并开启 preserveBigIntegers；flash 也应用这一转换。
- SSR 只走普通 HTML 分支；成功结果的 body 已包含 app 元素和 Page script，不能再次追加同一挂载元素。

## 4. Props 解析的关键细节

`PropsResolver` 先覆盖共享数据，再展开点号路径，随后逐层筛选、执行、归并。页面数据优先于共享数据；sharedProps 记录共享来源的顶层键。

partial 只有组件名一致时成立。only 的路径采用双向祖先匹配，except 排除路径及其后代，always 跳过筛选。普通对象继续筛选子字段；callback 返回值完整保留。这意味着 partial 是传输/查询优化，不是字段权限控制。

optional 在完整访问中不求值；deferred 不求值但通知客户端分组加载。partial 命中时才求值。once 在 Inertia 完整访问中依据客户端持有 key 跳过，fresh 或显式 partial 请求可以刷新；初次普通访问不能因为 once 头省略必要数据。TTL 使用秒精度计算再输出 epoch 毫秒。

同层 callback 用 `join_all` 并发，结果与 metadata 按原声明顺序归并；不是任意深度全树同时执行。reset 抑制对应 merge metadata；scroll 根据 merge intent 添加 append/prepend wrapper 元数据。

rescue 抑制失败字段并写 rescuedProps，默认失败终止渲染。应验证允许 rescue 的组合，不能将权限查询配置为可吞异常。

点号展开存在额外细节：写入普通 callback 的子路径可能提前执行父 callback，发生在正式 partial 筛选之前；冲突树可能覆盖已有值。Java 不能无声“优化”后声称完全等价，需合同用例或显式限制此类输入。

## 5. 会话和 SSR

render 消费 session 中的 flash/errors/clearHistory/preserveFragment，并取出当前 pending；props callback 新增的 flash 也进入当前页。flash 优先级由低到高为旧 session、当前请求、callback 新增、响应显式 flash。错误合并后按照 error bag 和 all-errors 策略输出 always errors。

未被 render 消费的状态在控制器/渲染结束后 commit；无 session 时丢弃跨请求状态并记录日志。Rust pull 发生在 props 完全成功之前，因此渲染失败可能已经消费旧 flash。Java 应作为一个明确差异改为暂存读取、成功完成响应准备后消费，而不是照搬数据丢失窗口。

HttpGateway：默认 `127.0.0.1:13714`，hot 文件存在时优先 `/__inertia_ssr`，否则 `/render`；bundle 可配置为存在性门槛。支持 enabled、尾部 `*` 排除规则、watch 文件缓存和健康检查。默认总超时 5 秒，网络错误、非成功状态、错误 JSON、null 都返回无 SSR 结果。不可达警告使用进程级全局一次性标志；Java 改为按 endpoint 限频日志，防止一个故障端点遮蔽其他端点。

## 6. 测试证据与缺口

`tests/core.rs` 覆盖 partial/nested、deferred、失败、merge、once、scroll、兄弟并发、大整数、flash/errors、根视图和响应头；`tests/axum.rs` 覆盖 HTML/JSON、状态保留、版本、重定向、会话、error bag、失败处理。`protocol.rs`、`page.rs`、`json.rs` 内也有单元用例。

这些是源码中的用例，不是本次测试通过记录。仓库未包含前端源码、Node SSR 启动工程、Vite manifest 管理库、浏览器 hydration 验收和部署配置。Java 计划需补齐这些边界，不能以 Rust 测试清单代替端到端证据。
