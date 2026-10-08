# Java 实施状态与验证记录

更新：2026-10-08。工程目录：`inertia-java/`。目标仍在进行中，以下状态依据实际代码和本轮命令，不代表整个 J0–J7 完成。

## 已落地

- Maven Java 21 多模块及 Maven Wrapper；Boot 3.5.7 单一基线，Jackson 2 来自其 BOM。前端 Inertia 3.8.0、React 19.3.0、Vite 8.3.3，全部 npm 依赖固定并保存 lockfile。
- core：Request、Page、安全 JSON、大整数、HttpOutcome、多值头、版本/重定向策略、注册组件、RootView、ResponseRenderer。
- props：literal/nested/lazy/async/optional/deferred/always/rescue、merge/deepMerge/prepend/matchOn、once/TTL/fresh、scroll/reset、partial only/except、点号冲突拒绝、同层并发、总 deadline。全局 executor 有界，单请求超并发拒绝。
- 请求 Context：share/flash/error bag、history flags、一次渲染状态；会话 reservation/complete/abort，单节点 HttpSession 和内存实现。错误在渲染前设置；callback 可设置 flash，重复 key 拒绝。
- Spring MVC：typed 页面端点拦截、控制器前版本校验、参数/返回值处理器、prepared response 后写出、REST 路由共存；starter 自动装配。
- SSR：生产 `/render` 与开发 `/__inertia_ssr`，development 明确开关、hot/bundle gate、except 路由，复用 JDK HTTP client、不转发凭证、不跟随重定向、固定 HTTP/1.1（避免 Vite 的 h2c 升级超时）、响应大小限制、硬超时、并发上限、显式 fallback。
- Vite：开发 hot 文件、HMR 客户端/React refresh，以及生产 manifest snapshot/hash、CSS 与 imports 去重、资产路径校验；示例静态资源映射。
- React 示例：用户页、About、404、表单验证 redirect、flash、deferred、精确大整数；Node SSR + 客户端 hydrate/CSR mount。
- testing：JSON/HTML Page 断言工具；core/session/SSR/MockMvc 合同用例及两种模式的 Playwright 流程。

## 验证证据

| 验证 | 本轮结果 | 覆盖边界 |
|---|---|---|
| Maven reactor `verify` | 通过；core 12、session 6、session failure 6、advanced props 4、error delivery 4、Rust parity 1、SSR 2、自动装配 6、MVC 5、MVC timeout 1、MVC error pages 4、MVC session failure 5、MVC isolation 1、Validation bridges 2、HttpSessionStore 5、启动诊断 9、resolver 4、request lifecycle 1，共 78 项 | Java 合同、会话失败恢复、适配器 wiring；未覆盖所有设计矩阵 |
| Rust `cargo test --all-features` | 通过，69 项（包含 doctest） | 现有库回归，新增 exporter 不修改库逻辑 |
| Rust → Java Page parity | 八组 fixture 完整 JSON 比较通过 | 初始、nested partial、deferred partial、异组件、default/named/scoped/first/all/重复字段 errors |
| npm typecheck | 通过 | 当前示例 TypeScript |
| npm client + SSR build | 通过 | 生产双 bundle |
| 实际 Node `/render` | 返回 head/body、一个 Page script 和一个 app root；含 server-rendered 标记 | 默认 root、Page 裸请求体、bigint 精确呈现 |
| Chrome SSR flow，1280×900 | 通过 | HTML 首屏内容、hydrate、JSON 导航、deferred、空表单错误、有效表单 flash、无应用控制台错误 |
| Chrome CSR fallback，18081 | 通过 | 不可达 renderer，空 app mount、deferred、导航、无 pageerror |
| Chrome Vite SSR flow，18082 | hydration/导航/表单流程通过 | 开发 endpoint、hot assets、精确 bigint |
| Chrome 错误页 SSR，18084 | 3 passed / 2 skipped | 500 首屏/hydrate/恢复导航、403 JSON；正常 SSR/Feed 回归 |
| Chrome 错误页 CSR，18085 | 2 passed / 3 skipped | Node 不可达，500 页面 mount/恢复导航及正常 CSR 回归 |
| Chrome namespaced session，18086 | 3 passed / 2 skipped | portal namespace 下表单/flash、真实会话失效→500→恢复导航、SSR/Feed 回归 |
| Chrome all-errors 模式，18083 | 3 passed / 1 skipped | 双消息错误列表、成功提交清除错误、SSR 与 Feed 回归 |
| 手机 390×844 截图 | 已采集 | 当前示例视觉快照，未做全浏览器/全设备认证 |

浏览器运行使用 frontend-testing-debugging 技能；Browser 插件不可用，使用本机 Chrome + Playwright。Java 源码经 Spotless/Google Java Format 格式化；Maven Wrapper verify 已实际运行通过。每种模式跳过另一模式用例，最新生产 SSR 为 2 passed / 1 skipped，CSR 为 1 passed / 2 skipped；合起来验证两条链路。第一次发现 favicon 404 后修复模板并重跑通过。

## 未关闭的实施项

| 工作包 | 尚需完成 |
|---|---|
| J0 | 更多跨语言 fixtures、非默认 root、客户端版本完整兼容清单 |
| J1 | 共享/页面冲突诊断、完整配置与错误策略、边界审查 |
| J2 | 已落地专用响应、启动诊断和一次安全错误页；发布前仍需覆盖更多应用 advice / 自动装配替换组合 |
| J3 | namespace 与 fail-closed 失效/写失败策略已落地；示例身份策略与 CSRF 过期恢复的进一步验收仍待处理 |
| J4 | 更全面并发/取消/过载用例和观察；当前任一层超出并发额度拒绝而非排队，需压测评估 |
| J5 | watch/health、except 与 hot/bundle 的专项合同、非 2xx/慢节点/超长响应浏览器验收、SSR 与 manifest build-id 对齐检查 |
| J6 | 扩展边界组合、响应级 history/SSR override 的完整合同及浏览器验收；现已通过四项高级 props 核心合同，Feed 浏览器验收结果另见本页追加记录 |
| J7 | CI、可重现部署脚本、英文 API 示例扩展、许可证/依赖审查、资源版本滚动演练和性能基线 |

另：WebFlux/集群 Session/Vue/Svelte/Precognition 继续按原设计放在首版之外。Rust 库实现未改动，新增 `examples/java_contract_fixtures.rs` 导出器，Java 工程本次交付作为首个实施增量，尚未关闭的工作包见上表。当前 demo 用户保存仅展示 flash，不访问数据库。

## Feed 实际客户端补充验收

开发模式 `http://127.0.0.1:18082` 下，第二项 SSR 浏览器用例通过：`/feed?page=2` → Load previous（3→6 项，Item 1 在首位）→ Load next（6→9 项，Item 9 在末位）→ Reset（回到 3 项）→ About → Feed（共享 catalog 的 once 值不变）→ Refresh catalog（重新查询，值变化）。本次开发模式运行 2 passed / 1 skipped。

最初把 catalog 只定义在 Feed 页时，离开到没有该 once 声明的 About 会丢失客户端的当前 Page 缓存。核对官方客户端代码后将 catalog 定义为共享 once prop，并保留跨页面验收；这是 once 的作用边界，不是服务端全局缓存。生产 SSR/CSR 基础链路已经分别通过；扩展 Feed 目前在开发模式通过，随后已完成生产构建的完整扩展链路重跑，见下条记录。

生产构建更新后重跑：`18080` 模式 2 passed / 1 skipped（基础 SSR + Feed），`18081` 模式 1 passed / 2 skipped（CSR 故障回退）。因此 Feed 的生产与开发路径均已实际验证。构建目前有官方 Inertia Vite 插件的 sourcemap 转换警告，未导致渲染失败，但错误源位置映射仍需在发布门槛中核对。


## 执行配置与超时生命周期补充

已新增 `InertiaProperties`：props/response timeout、单请求并发、线程池 core/max/queue 可绑定配置，启动时验证范围和预算关系。默认 executor 支持按 bean name 替换，并显式限定 qualifier；另有业务 executor 不再导致注入歧义。四项自动装配测试验证配置、生效线程池、替换、无效启动与非 Servlet 回退。

`InertiaContext.abort()` 在 MVC TimeoutException/InterruptedException 和异常 dispatch 清理中恢复 reservation；late SSR completion/flash 不得消费或修改已终止上下文。新增 core 与实际 MockMvc 超时测试验证恢复后下一次成功页仍收到原 flash。HttpSession 初始化使用 Spring WebUtils session mutex，starter 注册 HttpSessionMutexListener；该保证仍仅面向单节点。

该阶段完整 Maven Wrapper `spotless:apply verify` 通过，Java 累计 33 项测试；加入下述安全与 validation 用例后为 35 项。配置尚仅覆盖执行预算；应用 Page/SSR/资产仍由 Config bean 定义，错误页策略和发布门槛继续实施。

## 提交前验证与安全集成

本次增量新增 Jakarta Bean Validation 的 DTO 约束和 BindingResult message bridge；first/all messages 返回不可变集合，不包含 rejected value 或 target。示例使用 Spring Security 的 cookie/header CSRF 策略，公共演示路由不要求登录，但缺失或错误 token 的 POST 返回 403；starter 不替应用配置安全策略。MockMvc 验证真实 GET cookie → POST header 路径，Chrome 表单验收检查自动发送 X-XSRF-TOKEN。该阶段 Page all-errors 尚未接通；下述增量已完成。认证策略、CSRF token 过期恢复仍未完成。

提交前重新执行 Java reactor verify（35 项通过）、Rust all-features（69 项通过）、前端 typecheck/build 和生产 SSR 浏览器用例（2 passed / 1 skipped，含 CSRF header、validation/flash 与 Feed），最新 CSR fallback 亦通过（1 passed / 2 skipped）。此增量可运行并继续开发，整个 J0–J7 目标仍在进行中。

## ErrorBags / all-errors 与验证桥接增量

新增不可变 `ValidationErrors`、`ErrorBags`；从入站验证到 session、失败恢复再到 Page 的链路保留所有消息，重复字段按追加合并。仅在最终渲染时选择首消息或全消息。core `withAllErrors` 与 Spring `inertia.all-errors` 都可配置，后者仅在显式设置时覆盖 Config。保留原 Config 构造器及字符串 map 入参，并能读取先前单字符串 session。

核对 Rust `src/errors.rs` 后修复混合包投递：存在 default 时只投递 default，客户端 header 可将它包在指定 bag 中；无 default 时才返回全部命名 bag。不是把 named bag 附带到 default 上。错误始终作为 always prop，partial reload 也能领取；成功后消费一次，失败后恢复并合并期间新追加的消息。补充响应 flash > 请求 flash > stored flash 的优先级合同。

`ValidationBridge.errors(BindingResult)` 返回全部消息，indexed field 转为 dotted field；直接 Jakarta bridge 不依赖 Path.toString，基于节点索引/键处理 nested、list、map、container element。真实 Hibernate Validator 验证了 `items.0.name`、`tags.0`、`byKey.primary.name`、password 只传消息。ConstraintViolation 的 Set 通过 path/message 排序稳定首消息，BindingResult 保持其原顺序。原始错误 target/rejected value 不序列化，但业务自定义消息仍应避免插入敏感值。

本轮 Maven Wrapper `spotless:apply verify` 通过：42 项。Rust exporter 扩展到 8 组 Page fixtures，完整 JSON parity 通过。前端 typecheck 与双 bundle build 通过。生产 SSR + all-errors 运行于 `18083`：3 passed / 1 skipped，包含首屏/hydration、deferred、CSRF、Feed、双消息表单、成功 flash 和错误清除。首次新增浏览器断言错误假设 Playwright 的 redirect request headers 完整，实际页面已展示两条消息；改为识别 POST 的 JSON redirect chain 后整套重跑通过。

整个 J0–J7 目标仍在进行中；下一步优先处理 MVC 启动诊断、安全错误页与 SSR/资源故障及发布门槛。

## MVC 启动诊断与安全错误页增量

启动后映射初始化阶段，`InertiaHandlerValidator` 验证 direct typed return 与上下文参数组合。9 项启动合同覆盖直接 method ResponseBody、组合 method annotation、RestController、组合 type annotation、interface annotation、Callable wrapper、ResponseEntity wrapper、错误 context 参数，以及正常 Inertia + REST 共存。请求时保留诊断守卫，snapshot/context 重入复用且禁止二次 render/commit；这不表示支持异步 MVC/ASYNC redispatch。

`InertiaErrorPage` 由应用配置，starter 将 resolver 放在应用 ExceptionHandler 之后、Spring 默认 status resolver 之前。原请求 abort 恢复 reservation 后，最多一次使用 sessionless error context 渲染；强制失败状态与 private/no-store，不传原异常给页面。ErrorResponse/ResponseStatus/400 conversion 与 unreadable body 保留安全状态。无 factory 时使用固定纯文本；error factory、props、模板或等待超时失败时固定纯文本 500，不递归。已经提交的响应和普通 REST 不改写，安全 headers 在 fallback 中保留。请求级日志仅写 exception class/status，不写 message。

本轮实际发现 TypeMismatchException 不实现 ErrorResponse，最初被误判 500；按 Spring 默认 resolver 的状态映射补齐 ConversionNotSupported 与 TypeMismatch 的区分，完整合同重跑通过。Context 在 session cleanup 前先进入 FAILED，避免清理失败时继续接受 late effects；后端 session 写失败/失效的完整策略仍在 J3。每个 page attempt 有独立等待预算，主页面失败再尝试错误页最多使用两次 response budget。

本轮 Maven Wrapper `spotless:apply verify` 通过，共 60 项。新增 MockMvc 验证 500 controller/props failure、403 supplier denial、404/410 annotated status、400 参数错误、安全 JSON/HTML、flash 恢复、应用 advice 和 REST 独立行为、一次错误页失败后 plaintext。resolver 合同验证 committed response、重复进入、模板失败、错误页 SSR timeout 与 late completion；request lifecycle 合同证明重入保留 snapshot/share。

新增 opt-in `inertia.demo-failures=true` 演示路由，默认禁用。Chrome 生产 SSR 模式 18084 为 3 passed / 2 skipped：500 Error 首屏/hydration、Back to Users 后正常 deferred、403 JSON 不含内部原因，正常 SSR 和 Feed 也通过。Node 不可达的 18085 为 2 passed / 3 skipped：500 Error CSR mount/恢复导航与正常 CSR 都通过。前端 typecheck 通过；前端运行源码本轮未改动，只扩展 e2e。整个 J0–J7 仍未完成。

## Session namespace、失效和写失败增量

`inertia.session-namespace` 绑定到实际 MVC HttpSessionStore，默认 namespace 保留原有 attribute key，其他应用 scope 独立保存 canonical flash/errors/history keys；启动校验 safe identifier。单会话里的不同 namespace 不重复领取或覆盖彼此状态，应用应使用可信配置值，不从任意请求头选 namespace。

HttpSessionStore 在 session mutex 内，对每个读写/领取/完成/恢复操作做前后 attached 检查。失效、移除或替换 attribute 后的旧 adapter 不能继续写 detached memory，不迁移到新会话。Servlet container 的任意并发 invalidation 无法与网络投递原子化，这里的保证是发现失效即失败、不复活私有状态，不是跨进程 exactly-once。

Context 的 begin/redirect merge/complete 失败不返回成功；先关闭状态再尝试一次恢复，原错误因果保留，cleanup error 作为 suppressed diagnostic。不对未知写入结果自动重放。Delivery data 防御复制；Memory merge 与 abort restoration 先计算完整新状态，再替换 values/remove reservation，错误 payload 不会造成半写。明确 SessionStore SPI 的原子和 token 生命周期要求。

新增 6 项 core failure 合同、5 项 HttpSessionStore 合同、1 项 namespace 启动拒绝、5 项实际 MockMvc 会话验收和 1 项并发 principal 隔离合同。覆盖 begin/merge/complete/abort 失败、callback 零调用、失败恢复一次、late effects 拒绝、原因与 cleanup 同时保留、memory 半写防止、快照复制、namespace 隔离、失效/替换/初始化写失败、MVC properties 接线、真实渲染中失效和重定向失败，且私有 reserved flash 不出现在失败响应。并发 principal 是合成 Servlet 测试数据，证明 DTO/props/session request isolation，不代表实现了真实账号登录系统。

本轮 Maven Wrapper `spotless:apply verify` 78 项通过，前端 typecheck 通过。opt-in failure demo 新增 session scenario。实际 Tomcat + Node SSR + Chrome 在 18086、namespace=portal 下为 3 passed / 2 skipped：普通 SSR/hydration、CSRF 表单与 flash、Feed、数据源错误页、403 JSON、真实会话 invalidation → 500 Error → Back to Users 和 deferred 都通过。前端运行源码未改变，扩展了 e2e；Rust 库未修改。整体 J0–J7 目标保持进行中。
