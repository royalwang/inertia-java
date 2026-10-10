# ADR 004：WebFlux 适配器边界与启动条件

日期：2026-10-10。状态：设计建议，未实现、未声明支持。对应原路线图 R4；确认实际应用需求和独立验收预算后才启动模块。

## 决策

未来适配器使用独立 `inertia-spring-webflux`，starter 也必须与 Servlet 入口分离。复用 core 的不可变 Page/Props 描述、ProtocolPolicy、编码和观察分类；不加载 MVC configurer、Servlet filter/listener 或 HttpSessionStore。当前 core 的 resolver、renderer、SessionStore 包含同步边界，不能仅包装成 Mono 后宣称全链路非阻塞。

建议先做受限实验，确认异步组合能够保留协议和投递语义，再冻结公共接口。若只能使用有界 executor 隔离同步存储/渲染，应明确标为阻塞桥接实验；它不能通过正式响应式出口。

## 返回值与请求所有权

首批只接受单结果 `Mono<InertiaResponse>`、`Mono<HttpOutcome>`，可评估同步描述对象的兼容入口；拒绝 `Flux<InertiaResponse>`、SSE 或嵌套容器。Inertia Page 是一份完整协议响应，不能套用元素流逐项提交。普通REST/SSE继续交给Spring。

参数适配和结果处理由 WebFlux 的扩展点实现；结果处理器只认明确声明的 Inertia 类型。controller执行前完成协议版本预检，再从ServerWebExchange建立请求专属上下文。每次订阅拥有一份上下文和delivery；禁止跨订阅重用可变 InertiaContext，禁止为重试自动重新执行业务controller。

## 取消与预算

| 事件 | 必须证明的行为 |
| --- | --- |
| 未订阅 | 不查询业务、不领取delivery、不连接渲染器 |
| props阶段取消 | 取消未完成provider与网络请求；已有reservation进入明确恢复流程 |
| SSR超时/失败 | 仅渲染故障按策略CSR；权限、props和存储失败继续失败 |
| 响应提交前编码失败 | 可生成sessionless错误Page，同时终结原上下文；不能消费错误Page上的旧reservation |
| 写入中取消/断连 | 区分已知未提交与提交结果未知；不能假定浏览器已收到或盲目重复complete/abort |
| 清理失败 | 使用独立有界清理预算及观察分类；不得无限等待或吞掉未知存储结果 |

总预算使用单个monotonic deadline，取消必须向实际I/O/provider传播。生命周期需通过资源管理操作组合业务、写入与终结，不能在 `doFinally` 中启动无人持有、无截止时间的异步恢复。取消后的清理也需可观察、可等待或由明确拥有者接管；不能靠未订阅的Mono执行副作用。

## WebSession 与身份

通过 `WebSession` 取得可信身份。session属性存储epoch并不提供跨节点delivery原子性，仍沿用独立状态域和token fencing。轮换、invalidate、save都是异步边界，必须明确“先撤销delivery → 再改变身份 → 保存宿主session”的失败语义。禁止将Servlet的同步hook直接搬来。

当前Redis transport禁止重放但仍同步阻塞；正式响应式适配需要复用官方客户端的异步能力，保持相同CAS/lease/UNKNOWN_WRITE合同。不得在event loop运行现有阻塞命令。先比较新异步后端与当前Memory/Servlet/Redis共同合同，再决定是否需要独立异步SPI；本ADR不改变已交付SessionStore。

## Security 与错误响应

使用 `SecurityWebFilterChain`、reactive CSRF/session能力及Reactor Context中的安全上下文。业务provider跨scheduler时也必须保持身份与取消关系；不能从Servlet ThreadLocal读取或在工作线程继承旧用户身份。先鉴权再解析业务props，401/403不能变成SSR fallback。

响应提交前可由应用错误策略返回sessionless Page；提交后无法再替换状态、headers和整份错误Page，只记录失败并结束传输。渲染和JSON编码使用明确大小上限，在第一次写出前完成可失败的协议决策；不为隐藏提交问题无限缓存。

## 实验出口与后续采用

1. 确认真实采用方、目标Boot/JDK/服务器组合及场景；保持MVC默认starter不变。
2. 实验单结果controller、真实HTTP SSR、WebSession轮换/失效和Security Context传播。
3. 在真实客户端断连、provider慢请求、SSR取消、响应中断、未知写入下证明有界清理和不重复消费；仅StepVerifier完成不能替代真实网络。
4. 使用独立Maven消费工程和真实React/Vue浏览器链路验证HTML/JSON、forms/CSRF/flash、deferred与错误路径；提供双语指南和明确支持矩阵。
5. 通过上述结果再决定正式模块/API；失败时保留实验结论，不发布“WebFlux支持”。

Svelte是另一独立工作包：锁定官方Svelte适配器、独立client/SSR入口与页面，复用Java库，逐项验证hydration、forms/CSRF/flash、deferred/once/scroll和SSR恢复。Vue结果不能替代Svelte证据；该工作包也需采用需求后单独排期。

## 依据

于2026-10-10核对官方资料：[WebFlux返回值与响应提交边界](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/return-types.html)、[Spring Framework 6.2 WebFlux](https://docs.spring.io/spring-framework/reference/6.2/web/webflux.html)、[WebSession API](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/web/server/WebSession.html)、[WebFlux Security](https://docs.spring.io/spring-security/reference/reactive/configuration/webflux.html)。资料用于设计推导，不表示已升级当前Boot依赖或已经实现上述行为。
