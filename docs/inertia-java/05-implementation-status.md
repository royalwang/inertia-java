# Java 实施状态与验证记录

更新：2026-10-09。工程目录：`inertia-java/`。目标仍在进行中，以下状态依据实际代码和本轮命令，不代表整个 J0–J7 完成。

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
| Maven reactor `verify` | 通过；core 12、CSP core 4、session 6、session failure 6、advanced props 4、error delivery 4、Rust parity 1、SSR 19、Vite 12、自动装配 6、MVC 5、MVC timeout 1、MVC error pages 4、MVC session failure 5、MVC isolation 1、Validation bridges 2、HttpSessionStore 5、启动诊断 9、resolver 4、request lifecycle 2、CSP filter 1，history override 3、cancellation 10、outer HTTP cancellation 1、core observations 4、metrics wiring 6、MVC observations 4、HTTP observations 5、definition diagnostics 4、props overload/fail-fast 4，共 154 项 | Java 合同、会话失败恢复、适配器 wiring；未覆盖所有设计矩阵 |
| Rust `cargo test --all-features` | 通过，69 项（包含 doctest） | 现有库回归，新增 exporter 不修改库逻辑 |
| Rust → Java Page parity | 八组 fixture 完整 JSON 比较通过 | 初始、nested partial、deferred partial、异组件、default/named/scoped/first/all/重复字段 errors |
| npm typecheck | 通过 | 当前示例 TypeScript |
| npm client + SSR build | 通过 | 生产双 bundle |
| 实际 Node `/render` | 返回 head/body、一个 Page script 和一个 app root；含 server-rendered 标记 | 默认/custom root、Page 裸请求体、bigint 精确呈现 |
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
| J0 | 更多跨语言 fixtures、客户端版本完整兼容清单 |
| J1 | 完整配置与错误策略的剩余边界审查；已补齐定义来源/父子路径冲突诊断，明确同名覆盖与errors替换策略、提供有界观察和开发者报告 |
| J2 | 已落地专用响应、启动诊断和一次安全错误页；发布前仍需覆盖更多应用 advice / 自动装配替换组合 |
| J3 | namespace 与 fail-closed 失效/写失败策略已落地；示例身份策略与 CSRF 过期恢复的进一步验收仍待处理 |
| J4 | 性能基线及更广的并发边界；已验收跨请求全局queue拒绝/取消槽位恢复、async持有请求许可、unrescued失败立即终止与合法deferred rescue隔离。基础 MVC→render→props/SSR 取消链、结构化事件、HTTP transport原因细分类、Boot/Micrometer 与 MVC write/version-conflict 观察已落实 |
| J5 | 基础 SSR/Vite 与本地两版切换已验收；真实部署存储/路由资格并入 J7 |
| J6 | 扩展边界组合、更多 history/SSR override 边界组合（基础合同及官方浏览器链路已验收）；现已通过四项高级 props 核心合同，Feed 浏览器验收结果另见本页追加记录 |
| J7 | 可重现部署脚本、英文 API 示例扩展、许可证/依赖审查、实际部署环境演练和性能基线；基础远端 CI 已确认通过，后续改动仍须运行对应提交的 CI |

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

## Vite manifest 与 SSR endpoint 边界增量

统一可信 HTTP endpoint / hot origin 校验。hot 文件只接受无凭据、无 path/query/fragment 的 HTTP(S) origin，端口必须有效；SSR 对无效开发 hot 返回 unavailable，不错误拼接路径或意外转向生产 endpoint。生产始终忽略 hot，缺失配置 bundle 时禁用 SSR。except 以不含 query 的请求 path 做 exact 或尾部星号 prefix 匹配。

manifest 在加载时校验所有记录的 file、可选 css/imports 数组类型与静态 import 引用，而不是直到某个 entry 输出 HTML 才发现坏配置。输出路径拒绝 absolute、traversal、空/dot 段与不安全字符；循环 imports 去重且不 preload entry 自身。生产保留启动快照；开发 mtime 刷新使用完整 FileTime，避免同一毫秒内更新被截断。与 [Vite 官方后端集成](https://vite.dev/guide/backend-integration) 的静态 imports/CSS 字段规则核对；dynamic imports 仍由 bundle 按需加载。

新增 5 项 Vite、4 项 endpoint resolver、2 项 HTTP gateway 合同。覆盖循环 imports/CSS 去重、缺 entry、坏 schema/path、不可达坏记录、生产快照/忽略 hot、亚毫秒 mtime 刷新、hot 移除、IPv6 origin、except query/相邻路径、bundle 存在/移除、302 不跟随与 Cookie/Authorization 不转发。最初两个 gateway 用例的 Page fixture 缺必填字段，已修正后完整 reactor 重跑；Maven Wrapper spotless/verify 共 89 项通过，0 failure/error/skipped。

实际生产 manifest 下 Tomcat 18087 + Node SSR + Chrome 回归为 2 passed / 3 skipped（其他运行模式），涵盖首屏 SSR/hydration、导航/deferred、CSRF/validation/flash、Feed/once；Spotless check 和 git diff check 通过。

本轮未修改前端 runtime 或 Rust 库。J5 的 watch/health、CSP nonce、慢节点/超长响应浏览器、build-id 对齐和发布切换仍未完成；目标保持进行中。

## SSR deadline、取消与真实故障浏览器增量

修复 gateway deadline 直接完成 transport future 后无法再明确请求取消底层 HTTP exchange 的问题。独立 bounded future 管理全响应期限，超时或调用方取消时对原 transport 调用 cancel(true)，并发额度只释放一次；request 构建/提交同步失败也恢复额度。保留 transport-or-timeout fallback、无重试策略。预算按纳秒计，避免正的亚毫秒值被截断为零。取消不能撤销已发送的 Node 业务工作；JDK 资源释放仍是异步 best effort。

5 项新增 Java 合同使用真实 HTTP/1.1 ServerSocket peer：先写 headers/部分 body 后停滞，观察超时或取消后的客户端 EOF/connection reset，而非只看 future 完成；再使用同一 gateway 成功请求，证明额度可恢复。超长 body 在 stream 完成前关闭连接；concurrency=1 的过载请求没有发到 peer；503、无效预算/limit，以及同步准备失败后不漏额度合同通过。Maven Wrapper spotless/verify 共 94 项，0 failures/errors/skips。

新增可重复 `npm run test:ssr-failures` harness，创建并清理自己的 loopback renderer + Java 进程，使用随机端口，保留诊断日志。实际 Chrome 三模式全部通过（各 1 passed）：503、headers 后 body 停滞、超过 2 MiB 响应。各模式确认 peer 被调用且无凭据转发；验证空 root 的 HTTP 200 CSR mount、deferred、导航、验证错误、CSRF 表单成功与 flash。前端 typecheck 与 git diff check 通过。这个 peer 是故障注入服务，不宣称真实 Node 性能/负载认证。

J5 的故障浏览器基础验收已落地；watch/health、CSP nonce、build-id 对齐/发布切换，及 J4 更全面的请求取消传播、观察和压测仍待完成。目标保持进行中。

## SSR 后台 health 与 Node watch 增量

新增可关闭 `SsrHealthMonitor`，构造显式 trusted endpoint、connect/全响应期限和探测间隔，独立 pooled client/daemon scheduler；探测完成后再延迟下一次，不重叠、不跟随 redirect、不携带凭据，health body 限 4KiB。UNKNOWN→UP/DOWN，关闭后 STOPPED，late completion 不覆盖停止状态。只认可 locked 官方 renderer 的 HTTP 200 + status=OK 协议，不把异常正文或 endpoint 写到快照。请求读取 snapshot 不发额外 HTTP，不将 health 前置于 render，不改变 SSR fallback。

示例显式 `--inertia.ssr-health-enabled=true` 才开启；默认指向配置 renderer origin 的 /health，可通过 inertia.ssr-health JVM property 覆盖。cached `/api/ssr-health` 与 Java `/api/health` liveness 分开，Node 故障不使 Java liveness 失败。生产是否把 SSR 作为 readiness 必需条件由应用决定；快照可能在下一次探测前陈旧，health 成功也不能代表所有组件可渲染。官方 Vite plugin 开发 endpoint 不自动提供该 standalone health 协议。

4 项新合同覆盖启动前 unknown、重复 start、snapshot 零 HTTP、无凭据、302 不跟随、坏 schema/oversize、恢复、deadline、单个未完成探测和关闭/late completion。Maven Wrapper spotless/verify 共 98 项，0 failures/errors/skips；前端 typecheck 通过。

新增 `npm run ssr:watch` 复用 Node native --watch，监听构建产物，不在 Java 请求中启动 npm/Node。可重复 `npm run test:ssr-health` 在真实 Node + Tomcat 上通过：SSR UP、复制 bundle 改写→Node watch 重启→SSR 恢复、停止 Node→DOWN + Java liveness/空 root CSR、启动 Node→UP + SSR（不重启 Java）。probe/script 只创建和清理自有进程和 dist 临时副本，日志保留；macOS 实际执行通过，Windows tree termination 未认证。

J5 剩余 CSP nonce、build-id/滚动发布一致性，及 J4 观察/整体取消传播/压测仍未完成。目标保持进行中。

## 构建 receipt 与 Java release 完整性增量

`npm run build` 统一执行 client + SSR 构建，只在两者成功后写 dist/build.json；开始前移除旧 receipt，失败不保留旧的成功标记。记录 client/SSR 全输出文件（含 manifest/source map）的 SHA-256，按 canonical sorted inventory 计算 build id。实际运行通过；上游 Inertia sourcemap transform 警告仍在，不把 warning 当作 source map 正确性证明。

新增 `ViteBuild` 对 receipt schema/id、完整清单、逐文件内容、required manifest/SSR entry、manifest 引用资源与目录边界做验证；生产示例启动必须通过，Page version 从仅 manifest hash 改为 build id，使 SSR-only 变化也能更新版本。普通 ViteAssets 不强制这个示例 release 约定；开发 hot 模式独立。产物启动后必须保持 immutable；本轮没有把构建当作原地原子部署。

4 项 ViteBuild 合同覆盖稳定身份、SSR 改动、混搭、missing/extra 文件、坏 receipt/id、缺 manifest 资源、traversal 和 external symlink。Maven Wrapper spotless/verify 共 102 项，0 failures/errors/skips。前端 typecheck 和双 build 通过。实际 `npm run test:build-integrity` 四场景通过：valid release 启动且 Page version=receipt id；mixed SSR、missing client、extra client 均在真实 Java 启动时失败。验证脚本只改临时拷贝，清理自有 Java process/目录，日志保留；git diff check 通过。

这里只证明 Java 本地 release 的内容一致性，不证明 provenance/authenticity 或远端 Node 正在运行同一 release。Node build-id 握手、CSP nonce、滚动切换与旧 hash 资源保留仍待完成；旧资源不能直接追加进已验证目录，应通过独立 release/static/CDN 策略保留。目标继续进行中。

## 远端 Node release 校验增量

HttpSsrGateway 新增显式 verifyBuild 构造选项（既有调用默认关闭）。生产示例打开，开发 Vite 保持关闭；返回 buildId 必须为字符串且等于实际 outgoing Page version，缺失/错误类型/不一致均为 build-mismatch fallback，不注入 Node HTML。新增匹配、不匹配、缺失、错误类型与 legacy opt-out 合同；Maven Wrapper spotless/verify 共 103 项，0 failure/error/skipped。

生产 SSR entry 在 listen 前读取同 release receipt，核对 canonical build id 和实际执行 entry 字节 hash。Page version 不一致时在组件解析前返回空 body + 本节点 verified buildId，Java 可分类 mismatch；成功 body/head 附带同一 id。检查没有覆盖外部 node_modules 内容或签名，不宣称 artifact authenticity；仍需可信锁定依赖和 immutable release。

前端 typecheck、双 bundle/build receipt 通过。三个真实 harness 通过：test:ssr-health 的 real Node mismatch Page（故意未注册组件）被 gate 拦截，正常 SSR / watch exact-byte rewrite / DOWN→UP 恢复通过；test:build-integrity 的 Java valid/mixed/missing/extra 四场景与新增 Node 改写 entry→启动失败均通过；test:ssr-failures 四模式 Chrome 全通过（503、slow body、oversize、wrong build 各 1 passed），wrong-build body 未注入，CSR mount、deferred、导航、验证/CSRF/flash 正常。watch fixture 现在改写相同 verified bytes，通过重复启动 marker 证明真实 restart；不再对 production copy 任意追加未记录内容。

J5 仍需 CSP nonce 与发布切换/旧资源保留演练。总体目标继续进行中；git diff check 通过，本轮尚未提交。

## CSP nonce 请求链路增量

新增 trusted request nonce（保留旧构造器）、RootView.View.nonce 与 asset tags nonce 重载。Spring 只读 server request attribute，不从客户端 header 取；严格 token 格式/长度校验，snapshot 重入不改变，Page JSON/props/history 不携带。生产 asset 与开发 Vite client/React refresh/app modules 都有同 nonce；CSR Page 数据 script 也加属性，可信 SSR fragments 不自动改写或 blanket grant 执行权限。

示例 opt-in filter 生成每请求 32 随机 bytes，策略使用 nonce + strict-dynamic，root meta 提供给官方 React 客户端 nonce 选项。style-src 仍允许 unsafe-inline，开发 connect 允许文档中的本机 Vite；这是脚本 nonce 集成示例，不宣称通用严格 CSP。应用 owns policy，starter 不自设安全链。

新增 3 core、1 Vite、1 MVC snapshot、1 filter 合同；Maven Wrapper spotless/verify 109 项，0 failure/error/skipped。前端 typecheck 与双 build/receipt 通过。实际 production SSR 和 CSR 的 Chrome nonce/交互验收通过；实际 Vite 15173 + Java 18082 的开发验收也通过：header/meta/modules 同值、fresh full request、deferred/nav/CSRF/flash、正常流程无 policy violation。

首次 DevTools 动态 script injection 没触发期望的 parser CSP 行为，改为独立 parser fixture。开发版本第一次复用整个应用的 synthetic route response 又遇到 Chrome loopback address-space 权限，网络诊断确认并非 CSP nonce 配置错误。现在先验证真实应用交互，再用无外部依赖的 parser fixture 保留真实响应 CSP：正确 nonce 的脚本执行，无 nonce 的脚本被拒绝且产生 violation，避免把工具特权/合成网络属性当作产品证据。新增 npm run test:csp 可重复执行生产两模式；开发 command 写入 README。全浏览器平台资格仍未证明。

J5 的基础 CSP 链路已落地；发布切换/旧资源保留、J4 cancellation/observations/压测及剩余整体门槛仍继续。该增量尚未提交，目标保持进行中。

最终 CSP fixture 的三模式均实际通过：production SSR 1 passed、disconnected CSR 1 passed、Vite development 1 passed。自有开发 Java/Vite 进程已停止并清理 hot 文件；默认示例 Java 18080 / Node 13714 已更新到当前 verified release。普通模式 Chrome 回归 2 passed / 4 skipped（其他模式），含 hydration/deferred/CSRF/validation/flash 与 Feed/once，未留下旧进程混搭新 hash 资源。

## Versioned asset store 与两版切换增量

ViteAssets/Manifest 支持安全 origin-relative asset base，旧构造默认 /build/ 不变；示例 production base=/build/<receipt-id>/，Vite output 使用 relative base，让 bundle-relative 资源保持在该 release。构建成功后 publisher 将 client 清单按 id 分目录发布到独立 .inertia/assets store：同 FS staging + 内容/清单核对 + rename，已有 release 只校验不覆盖，旧目录不自动删除。Java production 启动验证当前 published client archive，公开静态 handler 从共享 store 服务 old/current 版本。

新增 2 Java 合同（base URL/traversal/unsafe-origin、published archive 内容/extra/missing）和 3 Node publisher 合同（两版保留/idempotency、坏 archive 不覆盖、坏 source/receipt 不发布）。Java spotless/verify 111 项，0 failure/error/skipped；Node test:assets 3 项通过，typecheck/build/receipt/publication 通过。

真实 npm run test:release-switch 通过：两组 verified Node/Java A/B、独立 shared asset store、loopback router 和 Chrome。B 是改变 client bytes 的受控产物 fixture 并重新计算完整 receipt，不冒充独立源代码版本。A 首屏 SSR/hydration/deferred → route切换B → A旧asset URL经B返回200且字节一致 → 官方 Link 触发409/new version/location → full refresh读取B prefix → About/Users/deferred/CSRF/flash 正常，无 pageerror。没有手工 fetch 替代客户端版本刷新；fetch只检查旧资产字节。脚本清理自有进程和 fixture目录、保留日志。

软件层的基础 SSR/Vite、构建内容/build-id 对齐、nonce、old asset retention/版本切换已具体验收。不能据此宣称生产 CDN/ingress、跨实例身份会话连续性或集群 exactly-once；这些依部署拓扑/应用 session 策略，仍属后续发布资格。历史未版本化 /build/ URLs 的迁移保留需显式处理。无需删除 asset store 旧release，保留期限由 active clients/cached documents/rollback 约束。总体 J0–J7 仍在进行中，该批改动尚未提交。

本轮最终回归：build-integrity 的 Java valid/mixed/missing/extra 与 Node mixed-entry 全通过；production CSP SSR/CSR 各 1 passed。开发 asset handler 保留无 hot 的 /build/ manifest fallback，Vite未运行 + renderer不可达 + CSP 的 Chrome 为 2 passed，含 deferred/nav/validation/CSRF/flash 和 parser nonce enforcement；默认 production 18080 Chrome 为 2 passed / 4 skipped（其他模式），含 SSR/hydration 和 Feed/once。自有开发 Java 已停止，默认 Java已更新到 versioned asset store 的当前 jar，Node保持同一 verified receipt。最新 Java verify仍为111项通过；git diff check通过。

## 自定义 root 与本批提交前验证

示例 Java 通过 inertia.root-id、Node 通过 SSR_ROOT_ID 配置一致的安全 root token，模板 meta 将 id 提供给官方 React client。SSR 使用官方 automatic factory，以避免 locked API 的显式 SSR overload 不接受自定义 id 的类型限制；未通过类型强转跳过检查。Gateway 增加可选 root metadata 对齐校验；示例启用，旧构造器保持兼容。缺失/错误类型/不匹配按 root-mismatch CSR fallback，不注入异构 root。

新增 root token 校验和 gateway matching/mismatch 合同，并扩展 core nonce 的 custom root CSR 断言。Maven spotless/verify 113 项，0 failure/error/skipped；前端 typecheck、client/SSR build 和 Node publisher 3 项通过。构建输出已检查包含 Node receipt/self-hash、Page version gate、root metadata。

真实 npm run test:custom-root 在 portal + CSP 下通过：SSR hydration/deferred/nav/CSRF/flash、正确/错误 nonce parser enforcement、Node watch restart、DOWN 后 portal CSR mount 与交互、恢复 UP，Java 未重启。默认 root test:csp SSR/CSR 各 1 passed；test:build-integrity valid/mixed/missing/extra Java 与 mixed Node 均通过；test:release-switch A→B 浏览器通过；test:ssr-failures 503/slow-body/oversize/build-mismatch 各 1 passed。基础自定义 root 门槛已关闭，更全面跨客户端/部署矩阵继续保留。

本批提交包含前述 SSR/Vite endpoint 边界、HTTP deadline/cancel、后台 health/watch、receipt/build-id、CSP、versioned assets 与 custom root 增量；整体 J0–J7 仍未完成。默认 Java 18080 / Node 13714 已更新到本批同一 verified release。

提交前默认 production Chrome 回归为 2 passed / 4 skipped（其他配置模式），含基础 SSR/hydration/deferred/validation/CSRF/flash 与 Feed/once；git diff check 通过。

## 请求 history 优先级与响应 SSR override 增量

补齐实施细节设计中此前缺失的请求级 encryptHistory(boolean)：响应→请求→全局，false 省略字段。请求只持有本次 override，不通过 redirect/session 传播；props callback 在准备完成前可设置，late mutation 拒绝。clearHistory 保持 response/request/stored OR 和原 reservation 消费保证。

新增 3 项 core 合同：18 组全局/请求/响应组合及 false omission/late write；redirect clear 一次传递且 encrypt 不泄漏、callback override 生效；HTML/JSON 下三来源 clear OR、withoutSsr 跳过 gateway、正常 HTML 调用 gateway 而 JSON 零调用。Maven spotless/verify 总计116项，0 failures/errors/skipped。

新增 opt-in DemoHistory 与组件，官方 Link 操作四模式，仅 demo marker/visit counter。实际 npm run test:history Chrome 1 passed：首屏 SSR→history.state.page 为 ArrayBuffer→response false 的 plaintext Page→Back/Forward 保留旧 visit→重新进入 encrypted→clear 密钥→Back 实际发请求且 visit 增加→withoutSsr 空 root CSR mount/导航，无 pageerror。真实 Node mismatch gate/watch restart/DOWN→UP 故障恢复也通过。核对 [Inertia 官方 history encryption](https://inertiajs.com/docs/v3/security/history-encryption)；这不替代应用 logout、权限、CSRF/session policy，也不宣称 plaintext 历史因 clear 而受保护。

前端 typecheck/双 build/receipt/publication 通过，已有 source-map warning 继续作为发布限制。默认 Java/Node 已同步新 release。该增量尚未提交；完整目标继续进行中，剩余门槛见上表。

本轮默认 production Chrome 回归 2 passed / 5 skipped（各 opt-in 配置用例），基础 SSR/Feed 正常；未开启 demo-history 时实际 GET 返回404。git diff check 通过。

## 可重复验证入口与 CI 增量

新增 node inertia-java/scripts/verify.mjs，按顺序执行 clean Maven/Spotless verify、npm ci/typecheck/client+SSR build、publisher contracts、owned browser matrix、build integrity、SSR faults、CSP、custom root、history、两版切换。随机 loopback peer 与自有进程，不依赖默认18080/13714；不修改 source formatting。每阶段日志、耗时/exit、source HEAD/dirty、receipt 和最终 summary 保留到 unique temp output。截图转到各场景 output，失败 trace 留存。

新增 test:browser-matrix 五配置：正常 SSR/Feed、all-errors、safe error/session invalidation、portal namespace、Node停止后的CSR/error recovery。Google Chrome154本地五配置全部通过（各2/3/3/3/2 passed），其余配置用例按 mode skip。聚合入口初次实际14阶段全部exit0，Java116项，Nodepublisher3项；证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-7v1MOI/summary.json。其后将 Maven 阶段强化为 clean，并重跑失败路径：无效 browser channel 在matrix阶段exit1，summary.success=false，build-integrity及后续阶段未执行；不是将注入失败当作产品失败。

新增 GitHub workflow：Ubuntu24.04、Java21/Temurin、Node22.22.2、locked Playwright 配套 Chromium；action commit refs已通过上游git refs核对，read-only权限、paths触发/manual、cache、always artifact retention。actionlint1.7.7 首次发现 job env不允许runner context，修正为step env后无诊断通过。未提交/推送该workflow，远端job尚未运行，不宣称GitHub CI绿灯。CI和本地使用相同aggregate command；完整目标保留其他J7发布门槛。

新版 aggregate（含 clean 与环境模式重置）在 macOS/Playwright Chromium156.0.8078.4 下14阶段全部exit0，116 Java contracts零failure/error/skipped，publisher3项；五配置浏览器矩阵全部通过，构建校验/SSR faults/CSP/custom-root/history/release-switch全部通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-BSXPU2/summary.json，8张desktop/mobile截图位于各matrix output。它是本地配套Chromium证据，仍不代替Ubuntu GitHub job执行。

修正复用output目录时可能把旧build.json误标为本次receipt的边界：只有本轮成功copy才设置summary.receipt。真实失败注入在自有output预放旧receipt、使用无效JAVA_HOME，Maven阶段exit1，summary.success=false/receipt=null，未运行build且旧文件保留；证据 /tmp/inertia-java-stale-evidence-hJvQjq/summary.json。失败日志/结果仍写出，不将旧产物视为当前成功证明。

默认18080/13714的HTTP首屏仍200、SSR标记存在且version与当前receipt一致；各owned harness清理完成，未终止已有默认服务。git diff check与workflow actionlint通过。本轮新CI/验证入口和上一轮history增量尚未提交，完整目标继续进行中。

## MVC→render→props/SSR 取消链增量

修复原外层CompletableFuture取消不传播、supplyAsync取消不interrupt运行callback、async只取消派生stage的问题。内部OperationFuture以CAS仲裁终结，session消费/失败恢复与完成属于同一结果；CancellationScope跟踪任务/源future，并在锁外取消，避免completion callback锁反转。取消赢时拒绝晚到的RootView/Page/commit；commit已赢时cancel返回false，不能倒退已经开始的完成。

computed/async factory统一由FutureTask在props executor执行，跟踪真正的原始async Future；deadline与MVC等待使用纳秒。取消的ThreadPoolExecutor排队任务移除，处理cancel-before-enqueue竞态；源在取消后才返回也立即cancel。晚到的computed DTO不再序列化。async factory的线程语义改变已经明确记录：需在调度前capture request data/identity，不保证ThreadLocal/request scope自动传播；request-owned stage是取消所有权边界。

新增10项core合同：deadline中断/worker恢复、caller cancel与未启动查询零调用、queue slot释放、原始async取消/worker factory、blocking factory deadline、晚到stage、完成/取消session唯一仲裁、SSR source取消且恢复flash、provider cancel失败抑制并继续取消其他句柄、cancel(false)不强制interrupt且晚DTO零序列化。加强已有MVC timeout断言：原始async source确实cancel，late complete失败。MVC normal/error timeout即使abort cleanup异常，也finally取消future并保留线程interrupt状态。

新增真实HTTP/1.1外层render取消合同：headers/半个body后暂停，外层cancel使peer观察EOF/reset；flash回原session；相同gateway的下次HTML请求成功、许可恢复、flash只交付一次、总调用2次且无重试。Maven spotless/verify127项，0 failures/errors/skipped。总体目标继续进行中；全局观察/性能基线仍待完成，不把interrupt request等同于物理DB/远程操作停止，也不声称自动检测Servlet浏览器断连。

取消增量的最终聚合回归：配套Chromium下14阶段全部exit0，clean verify仍127项零failure/error/skipped；正常/全errors/error/namespace/CSR五配置、build integrity、SSR故障、CSP、custom root、history、release-switch均通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-u4Oddx/summary.json。默认Java18080已重启为此实现，Node13714保留同一verified frontend release；本轮取消增量与前两轮history/CI尚未提交，目标仍在进行中。

## 本批提交：history、取消链、观察 SPI 与 CI

补齐 framework-independent InertiaObserver：props、render、SSR success/fallback 和 session begin/complete/abort/merge 的耗时/结果；每个 span 最多终结一次。旧构造器默认 NOOP，可显式注入及组合，LoggingInertiaObserver 通过 System.Logger 输出 JSON。MVC 创建 context 时沿用 renderer observer，独立 redirect context 也可注入。request ID 默认由服务器生成，不读取客户端 X-Request-Id；原三/四参数 request 构造保持可用。

事件不包含 URL/header/props/flash/exception text/renderer body，未知 fallback reason 归为 UNKNOWN。component/endpoint ID 属于受信配置，request ID/component 不应作 metrics tag。observer inline 执行，必须快速、不阻塞；RuntimeException 不替换业务结果，fatal JVM Error 不属于隔离保证。render success 是产出 HttpOutcome，不能等同浏览器收到响应。

新增4项观察合同：成功/会话关联和敏感字段不泄漏、未知 fallback 分类；观察器异常隔离和组合继续；取消/超时只上报一次；redirect merge 失败保留原异常。异步成功事件测试显式等待 observation callback，不假定 future.get 与全部 dependent callback 同时完成。J4 观察基础已落地，Boot 自动装配/Micrometer、MVC response/version-conflict 事件、HTTP transport 细分类及性能基线仍待实施；保留的 operation enum 不代表这些集成已实现。

本批提交前重新运行 aggregate：macOS/Playwright Chromium156.0.8078.4，14阶段全部exit0；clean Maven/Spotless verify131项、0 failures/errors/skipped，publisher3项，五配置browser matrix、build integrity、SSR faults、CSP、custom root、history、release-switch通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-3OGa9c/summary.json。随后仅为新观察测试加入callback同步，再运行该4项合同及格式化通过，产品代码未变化。actionlint1.7.7与git diff check通过。

本次提交收录以上history/CI/取消链及观察SPI增量，覆盖前文“尚未提交”的历史记录。远端GitHub workflow结果需以推送后实际运行记录为准，不能用本地成功代替；完整J0–J7目标仍在进行中。

## Boot/Micrometer 与 MVC 观察集成

starter 默认 props resolver/renderer 使用应用的单一/primary InertiaObserver；没有 observer 时 NOOP。新增独立 InertiaMetricsAutoConfiguration，在 Servlet 应用、Micrometer 存在、单一/primary MeterRegistry 且没有自定义 observer 时创建 MicrometerInertiaObserver；按 Boot Metrics/Composite/Simple registry 自动装配顺序执行。Micrometer-core 为 optional 依赖，使用 Boot3.5.7 BOM 的1.15.5；不将 Actuator/registry/HTTP metrics/security 设置强加给应用。示例打包 jar 实际只含 micrometer-observation/commons，没有 micrometer-core 或 Actuator，完整运行验证了可选依赖缺失路径。

Timer 记录事件次数和耗时（纳秒输入），名称 inertia.<operation>。标签仅 outcome/reason/response/status：枚举和100–599/0；变化的 requestId/component/endpointId 不创建新时序。原始 request ID 仍用于事件关联，endpoint log 标识通过 inertia.ssr-endpoint-id 配置 safe token，默认 renderer。应用自定义 observer 优先，需同时日志/指标时显式 combine；应用替换 resolver/renderer 后自行注入。

MVC 在控制器前版本冲突时发布 VERSION_CONFLICT，并对 early response、正常 HTML/JSON/redirect、安全错误页/plaintext 发布 RESPONSE。错误页沿用请求快照的 server request ID，REST 不参与。RESPONSE 是写出尝试：success/status500 表示错误响应写出成功，并非业务成功；writer failure 单独计失败，plaintext retry 是另一尝试，不等同浏览器已收到或完整HTTP耗时。无效 request snapshot 不妨碍安全纯文本输出，也不伪造关联事件。观察器 RuntimeException 的隔离仍由共享 publisher 保证。

新增6项自动装配/metrics合同，涵盖无registry/缺库、custom observer、primary/ambiguous registry、实际Actuator自动装配顺序、非Servlet、unsafe endpoint启动拒绝，200组变化标识仍仅5个meter。新增4项实际MockMvc/writer合同，涵盖409时controller零调用/session不创建、HTML/JSON/redirect/REST、失败页与错误页同request ID、安全plaintext、writer异常无原因泄漏、invalid snapshot仍写出。异步事件测试等待callback完成，不依赖future返回时callback全部结束。

完整 aggregate 本轮实际通过：14阶段全部exit0，clean Maven/Spotless verify141项、0 failures/errors/skipped，Nodepublisher3项、配套Chromium五配置matrix、build integrity、SSR faults、CSP、custom root、history、release-switch全通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-qmMuX3/summary.json。API示例及语义已更新到 inertia-java/README.md；参考 [Micrometer timers](https://docs.micrometer.io/micrometer/reference/concepts/timers.html) 和 [Boot3.5自动装配](https://docs.spring.io/spring-boot/3.5/reference/features/developing-auto-configuration.html)。HTTP transport细分类/性能与其他J0–J7门槛仍继续；本增量尚未提交。

## 已确认的首次远端 CI

GitHub CLI 未登录且connector未连接，但公开运行页可读取状态；此前“远端尚未确认”的记录在此更新。[run37764719794](https://github.com/royalwang/inertia-omega/actions/runs/37764719794) 对应已推送的6967fd52cfc57e3c1e46eabf3d490e8079ace5f9，push/main、Success，总3m38s，verify job3m35s，产出一个339KB evidence artifact。网页不允许匿名查看日志/下载artifact，未宣称读过其中结果。远端成功是该提交的Ubuntu/Java21/Node22/Chromium workflow证据，不覆盖本轮未提交代码或真实生产部署。

该run有两项action升级annotation：所固定的Node20 action被runner强制以Node24执行；setup-java v4已弃用建议v5。job已成功，但后续应核对官方新action版本并更新pin后再以远端job验收；不将warning视为通过升级的证据。

默认Java18080已重启到本轮141合同的jar，实际GET/users返回200、SSR标记存在、HTML版本与当前frontend receipt相符；Node13714维持同一frontend release。git diff check通过。新增/修改共11个文件均属本增量，尚未提交；整体目标保持进行中。

## SSR HTTP 传输观察细分类

HttpSsrGateway 新增 URI/resolver 两种 observer/endpointId 构造，旧构造继续 NOOP。每次调用最多一个 SSR_HTTP 终结事件：成功、excluded/unavailable、overloaded、超时、连接失败、响应超限、其他transport失败、主动cancel，以及 HTTP status/warming/invalid JSON/invalid response/build/root mismatch。细分类通过异常类型及有界枚举确定，不解析异常message或正文；公开fallback字符串保持兼容，包括transport-or-timeout。

处理超时仍返回fallback，因此是 outcome=FALLBACK/reason=TIMEOUT；主动取消为 CANCELLED/CANCELLED。只有完整response解码后记 upstream HTTP status，否则0；不把 Java 响应状态或错误message推断为renderer状态。HTTP与SSR是不同阶段，不叠加为请求数/总时延。主动cancel先取消bounded/transport并释放许可，再调用应用observer；late transport不发布第二事件，observer RuntimeException仍隔离。异常解码以exceptional completion结束，避免意外runtime failure使结果悬挂。

示例Config显式将应用observer和inertia.ssr-endpoint-id传给gateway，与Boot renderer一致；库不自动改写应用自建gateway。使用已有Page.component()读取组件，不为诊断deep-copy整份props。API文档已补充 inertia.ssr_http、构造注入、分类/状态及阶段语义。

新增5项真实HTTP合同：stalled body TIMEOUT与超限 RESPONSE_LIMIT区别且取消连接/许可恢复；拒绝连接 CONNECTION、503 HTTP_STATUS、敏感URL/props/cookie/exception/body不入事件；overload零额外dispatch、cancel只上报一次且恢复；截断body TRANSPORT无retry，以及5组解码fallback；excluded不发HTTP和unsafe endpoint拒绝。既有外层render→HTTP cancellation/flash恢复合同仍通过。Java全量146项、0 failures/errors/skipped。

完整aggregate实际14阶段全部exit0，配套Chromium五配置matrix、build integrity、SSR faults、CSP、custom root、history、release-switch均通过，publisher3项通过；证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-JaTflX/summary.json。该次aggregate期间只将诊断组件读取改为既有getter，随后相关core/SSR模块重验通过；首次spotless:check发现参数行格式变化，经spotless:apply修复。最终全量验证记录另附。本增量与上一轮观察集成仍未提交，整体目标继续进行中，下一步继续并发/性能及剩余J0–J7资格。

getter优化后的最终全量spotless:check verify146项、0 failures/errors/skipped，全部module SUCCESS。默认Java18080已更新为该jar，实际/users返回200、SSR标记存在且version与receipt匹配；Node维持同一frontend release。git diff check通过，目前合计15个相关修改/新文件未提交（包含上一轮观察集成）。

## 2026-10-09 定义来源、覆盖诊断与过载失败反馈

新增PropDefinitionException（仍是IllegalArgumentException），提供INVALID_PATH/PARENT_CHILD_CONFLICT及路径/来源字段；Props.from标注DECLARED/INTERNAL_ERRORS/CONFIG_SHARED/REQUEST_SHARED/PAGE，overlay保留不可变origins/overrides报告及原声明顺序。父子冲突在supplier执行前拒绝，异常包含schema路径但不含值。报告不进入Page/metadata；应用若使用敏感动态key需自行保护开发者诊断。

保留同名key的既有优先级：内置errors→config shared→request share→page，未命中supplier零执行。errors允许覆盖，可替换验证数据和always行为；应用需要内置验证时应保留该key。根定义规划为覆盖发布有界PROP_OVERRIDE事件（PROP_OVERRIDE/ERRORS_OVERRIDE原因），不含key/value；定义错误分类PROP_DEFINITION。Props.overrides供程序检查来源；builder内重复put继续原覆盖行为，报告针对Props组合，nested报告可由其自身Props读取。覆盖事件duration/status为0，是schema诊断次数，不是请求/查询次数；即便partial后来排除key仍发布。

新增4项合同验证typed invalid/冲突来源与值不泄漏、immutable报告/顺序/早先组合保留、实际shared/request/page优先级和原supplier零调用、errors覆盖安全观察、跨层冲突零callback且session flash恢复。现有Micrometer合同追加zero-duration覆盖事件count=1/totalTime=0与四个固定标签，实际6项metrics合同通过。

新增过载场景首次暴露真实问题：一个async源未结束时，另一个已被请求许可拒绝的prop仍等待allOf，直到总deadline。修复为每个未rescued未来的异常立即终结owned操作并取消兄弟；规划循环检查停止标记，避免继续查询/序列化；非法scroll返回也提前终结。成功结果/metadata依声明顺序，多个fatal failure由最先观察到的终结获胜，不承诺按声明顺序选异常。

新增4项并发合同：不同请求占满全局worker/queue后第三render拒绝且SSR/root零调用，取消排队请求只释放自身槽位并使后续请求恢复；未结束async保留请求许可、兄弟拒绝快速返回并取消原源；非法scroll不等待未结束兄弟；合法deferred rescue失败不取消健康async且metadata正确。首个rescue fixture错误使用eager来源，按已有rescue仅支持deferred的约束修正为真实partial deferred请求，未扩大rescue范围。底层不可取消操作的物理停止仍不在Future保证内。

完整aggregate实际14阶段全部exit0，clean Maven/Spotless verify154项、0 failures/errors/skipped，publisher3项、Chromium五配置matrix、build integrity、SSR faults、CSP、custom root、history、release-switch全通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-cCrYl5/summary.json。随后仅补充既有metrics测试的zero-duration断言，再执行6项metrics测试及格式化通过，产品代码未变化。J1定义诊断/覆盖语义已补齐，完整配置/错误策略边界、J4性能基线和其他J0–J7资格继续；本增量与前两轮观察集成尚未提交。

默认Java18080已重启到154合同对应jar，实际/users为200、SSR标记存在且version匹配当前receipt，Node维持同一frontend release。git diff check通过，目前累计23个相关修改/新文件未提交。目标保持进行中。

## 2026-10-09 提交批次

本次提交收录前述 Boot/Micrometer 与 MVC 观察、SSR HTTP 传输细分类、props 定义来源/覆盖诊断及过载快速失败修复，覆盖以上三轮“尚未提交”的历史记录。提交前复核 aggregate summary 为 success、14 阶段 exit0，当前 Surefire 报告合计154项、0 failures/errors/skipped；最后追加的 metrics 断言已有专项验证。性能基线尚未实施，其他剩余项仍按上表推进。该批次的远端 CI 必须依据推送后对应提交的实际运行确认。
