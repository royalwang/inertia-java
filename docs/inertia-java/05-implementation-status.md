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
| Maven reactor `verify` | 通过；core 12、CSP core 4、session 6、session failure 6、advanced props 4、error delivery 4、Rust parity 37、SSR 19、Vite 12、自动装配 6、MVC 5、MVC timeout 1、MVC error pages 4、MVC session failure 5、MVC isolation 1、Validation bridges 2、HttpSessionStore 5、启动诊断 19、resolver 4、request lifecycle 2、CSP filter 1，history override 3、cancellation 10、outer HTTP cancellation 1、core observations 4、metrics wiring 6、MVC observations 4、HTTP observations 5、definition diagnostics 4、props overload/fail-fast 4、CSRF recovery 3、config presentation 3、MVC config presentation 3、Rust HTTP policy 45、once TTL 11、required SSR core 5、MVC required SSR 2、MVC advice lifecycle 5、MVC outcome advice 5、Boot override wiring 4、demo identity 3、non-Inertia transfer 2，共 291 项 | Java 合同、会话失败恢复、适配器 wiring；未覆盖所有设计矩阵 |
| Rust `cargo test --all-features` | 通过，69 项（包含 doctest） | 现有库回归，新增 exporter 不修改库逻辑 |
| Rust → Java Page parity | 37组 fixture 完整 JSON 比较通过 | 原8组及merge/once/deferred rescue/scroll/history/bigint/shared组合，详见兼容矩阵 |
| Rust → Java HTTP policy | 45项通过：41项直接对照，4项明确Java策略差异 | status、全部多值headers与body；纯policy，非真实代理/网络行为 |
| once/TTL | Java 11项、Rust实时9场景、Chrome五配置到期边界通过 | 秒精度与callback trace、fresh/partial/loaded、负数/overflow恢复；非所有极端/嵌套TTL组合 |
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
| J0 | 已有37组Page与45项HTTP policy合同及明确客户端/运行时矩阵；已验证秒精度TTL实时/固定Clock语义及官方客户端到期边界；HTTP非法输入/失败组合与更多跨语言边界仍待验收 |
| J1 | 完整配置与错误策略的剩余边界审查；已补齐Page URL resolver/shared-key元数据开关及错误页集成，已补齐定义来源/父子路径冲突诊断，明确同名覆盖与errors替换策略、提供有界观察和开发者报告 |
| J2 | 已落地专用响应、mapping/advice启动诊断、一次安全错误页与typed应用Page advice隔离Context（含继承泛型）；HttpOutcome advice的重定向/会话写入/namespace失效，以及codec/resolver/renderer/MVC替换与codec候选歧义已验收；已补REST multipart与async streaming transfer边界；具体未关闭要求以第七篇逐项审查为准 |
| J4 | 更广的并发/业务负载边界；已完成本地HTTP初始性能基线（数据库查询0），真实业务/部署容量仍待资格验证；已验收跨请求全局queue拒绝/取消槽位恢复、async持有请求许可、unrescued失败立即终止与合法deferred rescue隔离。基础 MVC→render→props/SSR 取消链、结构化事件、HTTP transport原因细分类、Boot/Micrometer 与 MVC write/version-conflict 观察已落实 |
| J5 | 基础 SSR/Vite、本地两版切换及逐页requireSsr成功/503失败策略已验收；真实部署存储/路由资格并入 J7 |
| J6 | 扩展边界组合、更多 history/SSR override 边界组合（基础合同及官方浏览器链路已验收）；现已通过四项高级 props 核心合同，Feed 浏览器验收结果另见本页追加记录 |
| J7 | 已有独立发布打包/校验运行入口及macOS外置目录演练；已具备name/description/URL/SCM元数据、21个binary/source/Javadoc产物与内容门槛，仓库外Maven消费/HTTP/classifier解析及缺产物拒绝已验收；已新增实际依赖图、运行时archive对照与许可证声明/文本清单；剩余正式版本/tag/签名、英文 API 说明扩展、自有分发许可证/归属及第三方条款审查、目标Linux/代理/会话/存储演练和部署性能资格；基础远端 CI 已确认通过，后续改动仍须运行对应提交的 CI |

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

## 2026-10-09 本地 HTTP 性能基线

新增npm run benchmark，独立端口/拥有的进程，比较真实SSR、主动排除、连接拒绝和headers后body停滞四模式，记录并发、零数据库负载、props数量/大小、SSR比例、分组P50/P95/P99、SSR/client超时及采样RSS/CPU。示例增加可选日志observer和路径排除配置，默认不开启。最终12阶段全部通过、共1224请求均200，client失败0；SIGTERM失败记录/清理另已演练。最后一轮证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-benchmark-HlrLtc/summary.json，可持久审阅的摘要和测量限制见[性能基线](06-local-http-benchmark.md)。

Maven reactor/Spotless verify154项、0 failures/errors/skipped，node语法检查与git diff check通过。frontend bundle不变，本轮未重复全套浏览器验收；结果不能代表数据库、浏览器首屏或生产部署容量。本批文件尚未提交，目标保持进行中。

## 2026-10-09 独立发布目录与部署入口

新增 deploy/release.mjs，将已构建的example jar、7个库jar/8个POM（含parent）、client/SSR/receipt、package/lock、当前版assets、release-local runtime、RUNBOOK/systemd模板打成可独立运行的版本目录。完整inventory核对frontend receipt，staging→同filesystem rename发布；相同已构建输入产生同payload ID，支持已安装node_modules后的幂等发布，拒绝覆盖损坏版本。不是源码build的bit-reproducibility承诺，也不是signed Maven repository。runtime启动前校验完整payload（node_modules单独由locked npm ci部署），manifest哈希是完整性约束，不是来源签名或运行后防篡改。

runtime check/java/ssr/pair支持仓库外启动、同root/版本、固定或pair临时端口和外部资产归档。pair以真实同版本SSR页面判就绪；renderer在就绪后终止，Java继续CSR；SIGTERM父进程转发给owned children，5s Java shutdown-phase预算/8s等待后可能强制停止，不能承诺任意长请求均drain。独立systemd模板使用immutable release ID、同env固定port配对，Wants/order与独立restart保留CSR故障策略；Type=exec不是应用readiness，目标Linux执行/语法资格尚未完成。没有安装unit、上传artifact或操作外部主机。

verify-release.mjs实际将payload放在仓库外带空格路径，npm ci --omit=dev --ignore-scripts，确认部署树无Vite/Playwright；用repo里的浏览器fixture访问外置服务，两项官方客户端流程分别通过SSR hydration/navigation/deferred/validation/flash与Node死亡后的CSR导航/表单/flash。校验READY build/release ID、Java liveness、owned Java终止；损坏jar使preflight失败且重新publisher拒绝覆盖，恢复后通过。完整payload、日志、screenshots/traces位于临时证据目录，摘要随文档保存。

首次浏览器grep未命中实际标题，验证入口按失败退出且清理；修正为既有SSR/CSR测试并要求输出1 passed，避免空测试/skip冒充通过。加入systemd模板后safe路径规则补充@字符，仍拒绝dot traversal/symlink。统一验证入口新增deployment阶段，最终aggregate15阶段全部exit0、clean Maven/Spotless154项（0 failures/errors/skipped）、publisher3项、browser matrix/build integrity/SSR故障/CSP/custom-root/history/A→B以及独立发布全部通过。aggregate证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-o9LpaW/summary.json。该轮浏览器使用本机Chrome154.0.8037.99；CI配置仍是paired Chromium。

随后仅澄清README/RUNBOOK措辞，当前完整payload重新通过10个发布验收阶段；证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-deploy-xbXnUS/summary.json。持久摘要见[本地发布记录](deployments/2026-10-09-local-release-summary.json)，操作与模板见[部署手册](../../inertia-java/deploy/README.md)。Node语法与git diff check通过。J7基础打包/运行脚本已落地，签名/发布metadata/许可证、目标Linux与真实ingress/storage/session资格仍未关闭。性能与本发布增量尚未提交，完整目标保持进行中。

## 2026-10-09 示例 CSRF 拒绝反馈与恢复

SecurityConfiguration接入example-only BrowserCsrfFailureHandler：只处理POST /users、X-Inertia:true的CsrfException；CsrfFilter仍拒绝原写，controller不执行。固定同应用/users的303/no-store/Vary，namespaced InertiaContext提交一次性default bag _csrf error，UI展示通用反馈并保留输入，GET后由用户明确再次提交；不自动replay，Referer/header token/表单值不复制到反馈。其他路径/方法/普通请求与权限deny仍403，存储失败500且无Location。该filter阶段响应不伪装成MVC write观察。

保持CookieCsrfTokenRepository：cookie缺失会签发新token、header与cookie不一致被拒绝；不能把HttpSession失效本身等同这种cookie token的过期。身份/登录/注销和session-backed CSRF expiry策略仍由后续示例/应用定义；恢复限定当前默认bag表单，不由starter改变应用SecurityFilterChain。参考[Spring Security6.5 CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)及[Inertia CSRF反馈](https://inertiajs.com/docs/v3/security/csrf-protection)。

新增3项Java合同：真实Security filter拒绝、恶意Referer不改变target、namespace一次性error/no success toast、缺cookie返回fresh token且显式重提成功、非CSRF/其他路径403不建session、incompatible存储500拒绝redirect。fixture的成功POST恢复沿用既有302，拒绝反馈为明确303；未改变协议的POST redirect策略。浏览器新增missing-cookie/stale-header两项，在SSR/all-errors/failures/namespace/CSR五配置共10次真实验收：一个被拒绝POST、输入Grace和按钮状态保留、通用error、无saved toast，用户第二次click才出现第二POST并正常flash，无pageerror。

首次aggregate在deployment失败：运行过程中README变化使live source publisher生成新的合法release ID，fixture却把它当作“corrupt被覆盖”。修复verify-release先复制jar/POM/dist/lock/runtime/文档等构建输入到独立snapshot，所有幂等/篡改发布检查使用同一冻结sourceRoot；中途源变更不会偷换测试目标，payload仍实际在仓库外运行。该变化是验收隔离，不放宽publisher完整性或篡改拒绝。

修正后完整aggregate15阶段exit0，clean Maven/Spotless157项、0 failures/errors/skipped，publisher3项，扩展browser matrix/build integrity/SSR故障/CSP/custom-root/history/A→B/deployment均通过；证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-AOW6bj/summary.json。部署summary包含冻结inputSnapshot，README接口已更新。

默认本地Java18080/Node13714已由先前owned PID26153/63779切换到61044/61043；session15562/56842，SSR build fdb9eeb3cb327bbafd90c79a97d9b34e5957e579f5512bcc2096b6704b10b771。实际/users为200、SSR/version匹配；使用独立cookie jar在默认服务验收失配303固定目标/无成功toast、明确第二POST后flash成功，不记录token内容。旧版client assets仍保留。git diff check通过，本CSRF/验收快照与前两轮性能/发布增量均未提交；完整目标继续。

## 2026-10-09 扩展 Rust→Java Page 对照

从原8组扩展到34组，新增26个输入场景。描述独立放在examples/java_contract_cases.json；Rust exporter调用真实Config/Inertia/Prop/into_page，只记录Rust产生的expectedPage，不用Java结果构造oracle。Rust src/Cargo.toml/Cargo.lock仍与6667d8d1be314067af989eb049ba419a07fcd412一致；仅example exporter/输入改变。原8组expectedPage完全相同，新export重复生成的JSON树也相同。

新增merge full/partial/reset/child-only及match路径、once default/custom/excluded/fresh/child-only、deferred分组/merge/once/rescue、scroll append/prepend/reset/cursor/自定义wrapper/字面量及computed deferred、config→request→response history优先级/clear/fragment、props及flash bigint边界与覆盖、config→request→page共享顺序、nested deferred与always/partial-except。滚动DTO仅覆盖wrapper/data及metadata，不伪称Rust完整Paginator API；时间相关TTL不硬编码成稳定JSON样本。

Java parity改为34个具名DynamicTest，逐场景独立executor/config/request/response，用完整Page树比较数组顺序、字段省略、null和数据。对象key顺序不被tree equality证明，同组deferred数组与sharedProps数组顺序已比较。34场景全部通过；完整Maven reactor/Spotless verify190项、0 failures/errors/skipped（原循环1项改成34项，所以总数增加33，而新输入增加26）。Rust cargo test --all-features仍为69项含doctest，全部通过；Rust/Cargo1.96.0，未声明已验证1.88 MSRV。

新增node inertia-java/compatibility/verify-fixtures.mjs maintainer gate，用cargo --locked再次真实导出并比较已跟踪JSON，不自动覆写预期，检查场景数与唯一name。该gate实际通过；普通Maven/现Java CI读取存储fixtures，不强制Java消费者安装Rust，也未宣称CI在线重新生成Rust预期。

[兼容矩阵](../../inertia-java/compatibility/README.md)列出34场景及明确React3.8/Java21/Boot3.5.7/已运行浏览器边界、Vue/Svelte/WebFlux未验与session/路径冲突/fail-fast/rescue政策差异。Rust的config URL resolver/shared-key exposure与Java剩余配置、HTTP policy/headers、TTL及更多失败组合仍需后续处理，不把Page对照等同完整规范或部署资格。

本轮产品代码与frontend bundle未变化，未重复浏览器全量；前轮15阶段含CSRF/发布验收的记录保持其当时scope。验证日志 /tmp/inertia-java-rust-parity-expanded.log、/tmp/inertia-java-parity-full-verify.log、/tmp/inertia-rust-parity-expanded-tests.log；freshness gate、node语法、git diff check通过。当前Rust exporter/fixtures/Java测试及前几轮增量尚未提交，整体目标继续进行。


## Page 展示配置与本批提交验证

新增 InertiaConfig.withUrlResolver / withSharedPropKeys，保留原8、9参数构造的默认行为；withAllErrors复制时保留新选项。URL resolver每次渲染调用一次，只改变Page.url，不改变路由、授权、版本冲突Location或redirect目标。null、空白、控制字符和超过8192字符的结果在props/SSR/root调用前失败，并恢复已预留会话数据。callback为受信同步配置，必须快速且不阻塞。shared-key开关仅省略sharedProps元数据，共享值及errors仍按既有规则输出。

新增3项core及3项真实MockMvc合同，覆盖默认/复制语义、共享值保留、一次调用、非法URL零查询与会话恢复、Boot all-errors覆盖、错误页再次解析及安全500回退、版本冲突/REST共存。新增3组真实Rust导出使对照达到37组，先前34组保持一致。

最终聚合验证15阶段全部exit0，clean Maven/Spotless verify为199项、0 failures/errors/skipped；typecheck/build、浏览器五配置matrix、资产合同、SSR故障、CSP/custom root/history、两版切换及独立发布演练全部通过。证据：/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-UoMTgC/summary.json。运行使用本机Chrome；此证据对应提交前工作树，不代表远端CI或Linux生产部署验收。

本批提交范围包括性能基线、独立发布工具、CSRF显式恢复、扩展Rust对照及展示配置。整体J0–J7仍在进行中，未关闭项见本页表格；默认本地服务的运行状态沿用前次记录，不据本次构建宣称已经重启。


## HTTP policy 的真实 Rust 对照

新增 examples/java_http_contract_cases.json 与独立 Rust exporter，直接调用实际 protocol::before/after/redirect/location；after先应用Rust返回的replacement，否则记录mutated parts与原body。生成 compatibility/fixtures/http.json，header名称小写、所有值保留有序数组，包含多条Set-Cookie/Vary。before允许继续控制器时记录null。没有用Java输出生成expectedRust。

Java新增45项具名动态合同，41项完整匹配Rust status/headers/body；4项单独声明Java期望与差异理由，并拒绝无理由或已经收敛的override。覆盖版本、method、各redirect状态、三个prefetch头、fragment、empty200、业务头/多cookie、Vary与普通请求及helper。差异为同源back限制、绝对back规范化为path/query、Referer fragment移除、Vary星号已覆盖全部字段。Rust empty200的back分支早返回，Java策略不被伪装成相同实现。

发现并补齐Java独立ProtocolPolicy.redirect缺少Vary的遗漏：helper直接返回302/Location/Vary；adapter.after仍处理303/fragment，不重复附加既有Vary。未改变Rust库；src/Cargo.toml/Cargo.lock与分析基线diff为空。

node inertia-java/compatibility/verify-fixtures.mjs真实重新导出两类oracle：37 Page与45 HTTP全部一致；Maven Spotless/verify全量244项、0 failures/errors/skipped，Rust cargo test --all-features全量69项含doctest通过。日志 /tmp/inertia-http-java-verify.log、/tmp/inertia-http-rust-tests.log；targeted HTTP/core另有57项通过。新产品改动仅helper的Vary，完整MVC回归已通过；本轮未重跑浏览器/部署聚合，前次15阶段证据不冒充本轮证据。

该批代码与文档未提交。J0还需时间相关TTL与额外非法输入/失败合同；代理信任、真实网络头序列化、Linux/生产发布与其他J0–J7项不被纯policy fixture证明，完整目标继续。默认本地服务未重启，不以本次构建证明其加载了新helper。


## once/TTL 的时间语义与官方客户端到期边界

新增9个共享输入 examples/java_once_ttl_cases.json，Rust独立exporter实际执行Prop/into_page，采集每次解析前后毫秒时间、完整Page与callback次数；不把真实时钟输出保存为固定oracle。verify-once-ttl.mjs对秒精度expiresAt执行实测窗口检查，验证缓存key/path、值/metadata是否存在、callback trace；freshness入口追加此实时门槛。成功证据带source HEAD/dirty和输入sha256，输出唯一临时summary。Rust loaded once场景实测仍带metadata但不执行查询；最初描述中的metadata=false与真实行为不符，已依据运行结果修正，未修改Rust库。

Java固定Clock读同9个输入，明确分别截断Clock/TTL小数秒：1700000000999ms下，0/999ms TTL为1700000000000，1500ms为1700000001000，60s为1700000060000。无TTL为null；loaded跳过查询/值但保留metadata；fresh与显式partial重新查询，excluded partial零查询且无once metadata。另2项Java合同验证负数拒绝、极大TTL算术溢出安全失败与reserved flash恢复/root零调用，不宣称Rust unsigned Duration/溢出边界相同。共11项全部通过。

新增真实官方客户端浏览器流程：初始Feed→固定Date为原expiresAt-1ms→About请求携带once排除key且JSON省略catalog→Feed仍复用原值→固定Date为原expiresAt→About请求不携带key并获得更大load计数→Feed显示新值。暖请求新metadata不会延长客户端原缓存deadline。初次使用setSystemTime仍会走时，点击前已跨过1ms边界而失败；改为setFixedTime仅固定Date，常规timer/network继续运行。没有放宽边界或用手工fetch替代导航。五配置ssr/all-errors/failures/namespace/csr-failures均通过新场景，无pageerror。

实时Rust9场景门槛与既有37 Page/45 HTTP freshness全部通过；Rust全量69项含doctest通过。TTL实时证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-ttl-Zlmgaf/summary.json。Java全量255项零failure/error/skipped。clock边界为受控客户端验收，不是60s等待或分布式时钟认证；当前正epoch时间与这9种组合不覆盖所有嵌套/deferred/极端TTL。once只是client复用指令，服务端不能从loaded-key header验证缓存时间，不能替代路由授权或业务失效策略。

最终聚合验证15阶段全部exit0，clean Maven/Spotless255项通过；typecheck/build、资产合同/完整性、五配置浏览器、SSR故障/CSP/custom root/history、A→B切换与独立发布演练均通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-wzRKP6/summary.json，Chrome154.0.8037.99。失败的旧run5dHw2G保留为失败证据，不被覆盖；最终通过为修正Date控制后的独立run。

本轮TTL与前轮HTTP增量尚未提交，完整J0–J7继续；默认服务未重启，远端CI/目标Linux资格仍待对应版本验收。


## 可复用库的 source/Javadoc 产物

parent POM新增name/description、实际仓库URL和SCM连接，保持0.1.0-SNAPSHOT/HEAD；禁止SCM子模块追加路径。package阶段用固定source3.3.1与javadoc3.7.0为可复用模块附加sources/javadoc，示例可执行包明确跳过。六个API模块生成公开类文档；dependency-only starter最初Javadoc因没有public/protected类而失败，现用真实英文模块HTML说明构建classifier，不添加无用途的公开类，也不全局忽略Javadoc错误。source jar保留其package说明。doclint仅排除missing-comment类，不把页面存在等同完整注释质量。

verify-library-artifacts.py检查实际7×3=21个jar的CRC、重复/安全路径、source逐文件字节与resources、公开API class/HTML清单、非空index及Node/示例隔离。标准库Python3为该门槛所需运行时，aggregate增加library-artifacts阶段；report记录大小/hash并明确publicationQualified=false及未关闭的法律/版本/签名/namespace门槛。独立发布packager要求并携带全部classifier；release验收先冻结三类jar，随后篡改/幂等检查仍针对同一输入。

新增隔离artifact-contract命令，在临时副本完成6项实际验证：完整产物通过、源码字节变化拒绝、API文档缺失拒绝、binary公开class缺失拒绝、classifier文件缺失拒绝、恢复后通过。源码/当前target未被负例修改。当前21产物门槛与6项负例检查均通过；Java全量仍255项，0 failures/errors/skipped。

仓库Rust manifest虽声明MIT，但本轮没有虚构Java版权主体、发布授权或许可证文本；法律归属/attribution、release version/tag、签名及仓库namespace/credentials仍需发布资格确认。没有向外部制品仓库部署；hash完整性不是来源认证。

最终aggregate16阶段全部exit0（新增library-artifacts）；clean Maven/Spotless255项，npm typecheck/build、五配置真实浏览器、构建/SSR故障/CSP/root/history/两版切换、独立发布演练全通过。发布payload实际47文件，其中source/Javadoc各7个；runtime前置hash检查和发布幂等/篡改拒绝仍通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-HYr7VN/summary.json，artifact细节同目录library-artifacts.json，发布同目录deployment/inertia-java-deploy-qfcAGQ/summary.json。

额外检查实际core effective POM，SCM connection/url保持实际仓库地址，未追加子模块路径；日志 /tmp/inertia-java-effective-pom.log，输出 /tmp/inertia-java-effective-core-pom.xml。本轮及前两轮HTTP/TTL改动尚未提交，完整目标继续；默认服务没有因此重启，未宣称新版本已通过远端CI或Linux/生产资格。


## 逐页面必须 SSR 的失败策略

实现 InertiaResponse.requireSsr：document请求必须获得SsrGateway.Rendered；missing/disabled/excluded/fallback/null结果或stage、同步/异步gateway异常均使当前Page失败，不生成其CSR body，也不调用其RootView。默认仍允许CSR，JSON访问仍不发SSR。requireSsr/withoutSsr按最后调用选择，避免同时“required/disabled”的矛盾状态。prop/授权错误仍保留原有失败策略。

新增SsrRequiredException，固定安全message、固定枚举reason；未知fallback值归UNKNOWN，原始fallback字符串不进入异常message。SSR span保留原fallback/failure，RENDER失败保留分类；null结果上报invalid-response。gateway预算、并发控制与dispatch次数不变。wrapped cancellation不改为service-unavailable，取消仍向原gateway future传播并恢复session reservation。

MVC将typed失败识别为503/private,no-store。一次safe error-page渲染显式withoutSsr，不再对同一故障renderer重试；原有错误页sessionless context保留，因此业务Page abort恢复的flash/errors不被错误页消耗。应用advice仍先于库resolver；错误页自身失败仍按原有最终安全500。此策略为Java增强，不冒充Rust完全相同实现。readiness/routing由应用决定，Java liveness不绑定renderer。

新增5个core合同覆盖成功/JSON/CSR/调用覆盖、bounded fallback与未知原因、missing/null/同步异步故障、scope取消及wrapped cancellation；2个真实MockMvc合同覆盖503、安全错误页零第二SSR、默认CSR与JSON行为。后续强化同2个MVC/5个core用例：真实HttpSession的flash在503后仍可交付一次，第二次JSON不重放；null completion stage与null Result均失败。targeted7项通过，日志 /tmp/inertia-required-ssr-boundaries.log。产品代码不因强化测试再次变化。

opt-in demo-failures新增/failures/required-ssr，返回About.requireSsr。真实Chrome浏览器在renderer正常时验证200/SSR/标题，在断开时验证503/no SSR marker/Error503实际mount，再经官方Link回Users/deferred恢复。没有手工fetch替代访问。完整Java/Spotless已为262项零failure/error/skipped；生成的source/Javadoc包含新增API。

最终aggregate16阶段全部exit0，包含library-artifacts、全量Maven、五配置browser、构建完整性/故障/CSP/root/history、A→B与独立发布。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-xts7bU/summary.json，Chrome154.0.8037.99。强化测试发生在aggregate的Maven之后，仅修改断言/边界输入；同7项重新执行通过，当前产品与构建产物未改变，未重复整套browser。默认服务未重启。本轮required-SSR与前轮HTTP/TTL/库产物改动仍未提交；完整J0–J7继续，不宣称远端CI或目标生产部署完成。


## 仓库外独立 Maven 消费验证

新增 scripts/verify-maven-consumer.py，先运行21产物内容门槛，再从真实packageRelease产物的maven子树复制临时fixture repository。只在复制件增加untimestamped SNAPSHOT metadata和sha1/sha256，严格checksumPolicy；没有修改immutable release、global Maven缓存或远端仓库。外置consumer拥有独立POM、空settings和private cache，未使用reactor parent/源码/classpath。starter+testing实际解析全部七个库jar，逐个与发布字节比较，运行classpath拒绝当前checkout路径。

外置Java程序实际启动随机loopback Tomcat，通过HTML/JSON、302/Vary、缺SSR的required页面503/no-store，以及503之后flash保留/一次交付/无重放。自动装配从jar内imports/resource生效；程序结束实际graceful shutdown，没有访问现有默认端口。source/Javadoc使用Maven dependency classifier resolve下载，并逐jar比较发布字节，不用预填缓存假装依赖可解析。

第一轮cache全空，从公共仓库重新下载third-party/plugin依赖；5个阶段全部exit0，独立消费编译/HTTP及14个classifier成功。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-maven-consumer-8fsfokmp/summary.json。该轮运行中补充的负例不被倒算成该轮通过。

最终完整脚本另跑独立fixture，允许复制前轮third-party/plugin缓存以缩短重复下载，明确排除所有io/inertia坐标、记录seed；仍从新fixture repository解析七个库。7阶段如期完成，missing-core-refused为预期exit1，其余exit0：删除fixture/core jar与private/core缓存导致Maven拒绝，而恢复并清理negative缓存后compile成功。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-maven-consumer-0fn9vw3o/summary.json，EXTERNAL_CONSUMER_VERIFIED在consumer-http.log。完整脚本源码/POM/日志/cache保留在唯一临时目录，失败summary明确success=false。

这是独立本地artifact consumption验收，非公共repo规范、namespace/签名/许可证认证；这里Java smoke无Node gateway、使用CSR，真实React/Node证据仍由此前browser/deployment覆盖。该新gate为独立发布命令，不加入每轮16阶段aggregate；本轮仅脚本与文档变化，未重跑产品Java/browser全量，先前262项/16阶段保持其当时scope。本轮与前轮增量尚未提交，完整目标继续。


## 应用 typed exception Page 的 Context 生命周期

新增4项真实MockMvc合同审查应用advice/库内error resolver的优先级。初次执行实测发现typed controller error advice直接使用普通Page Context，会消费本应留给成功业务页的flash；原代码对props失败也沿用已经FAILED的Context。修复 InertiaMvcConfigurer：ExceptionHandler方法直接返回InertiaResponse且已有Inertia request snapshot时，在argument或return处理前abort原context、只创建一次新的sessionless advice context。带InertiaContext参数和无此参数的local handler均覆盖。

新error context沿用immutable request snapshot、nonce/URL，允许advice设置自己的safe shares/props；不复制已失败请求的pending/shared副作用，也不pull会话delivery。原储存flash/errors保留在会话；ordinary成功页面下一次领取。正常映射、ResponseEntity与REST advice不触发该typed Page路径。库error resolver依旧在Spring应用异常resolver之后；advice的Page再次失败时落入一次安全库errorPage，无递归。

合同通过：直接controller/异步props错误优先由应用Page处理、应用status418和shares保留且库factory零调用；两者flash保护；local typed Page（无context参数）的HTML/JSON及普通ResponseEntity advice；advice Page props再次失败时一次safe Error500、secret不入响应；普通REST advice422不添加Inertia头/Vary、不调用Page factory。typed advice需普通ControllerAdvice且synchronous/unwrapped；未把HttpOutcome advice的会话写入/重定向与更多注解组合宣称完成。

Java全量Spotless/verify266项零failures/errors/skipped，初始失败 /tmp/inertia-mvc-advice-initial.log、修复targeted /tmp/inertia-mvc-advice-fixed.log、HTML扩展 /tmp/inertia-mvc-advice-html.log、全量 /tmp/inertia-mvc-advice-java.log。新public-API源码内容未扩展，生成source/Javadoc含最新MVC实现。

最终aggregate16阶段全部exit0、clean Java266项通过；source/Javadoc内容、五配置browser、build/SSR故障、CSP/root/history、A→B切换与独立发布通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-FXnoVK/summary.json。

随后独立Maven consumer模板加入真实controller-local typed advice（InertiaContext.share、418 Page），先seed flash，再advice不领flash，再required503、正常JSON一次交付/无重放。新fixture七个阶段按预期通过，缺core为预期exit1；runtime从新jar/private repository解析，third-party seed明确排除io/inertia。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-maven-consumer-8hx4e2al/summary.json，HTTP标记包含ADVICE。这是同新jar的外置应用验证，不冒充已在前轮consumer执行该场景。

本轮MVC advice与前轮增量尚未提交；整体目标继续，更多HttpOutcome advice/注解/自动装配组合、身份策略和发布/生产资格仍待完成。默认服务未重启，远端CI未因本地通过而宣称当前代码已验证。


## typed exception handler 启动诊断与泛型 Context

InertiaHandlerValidator 保留现有构造器，作为 ApplicationContextAware bean 扩展扫描 controller-local/global ExceptionHandler；全局 advice 包含父容器。使用 MethodIntrospector、merged annotation 和带 containing class 的 MethodParameter，解析继承泛型返回类型。与普通映射共用同步/unwrapped、ResponseBody 和 context 参数限制，错误包含实际 bean class#method。ordinary REST advice 继续允许。

新增10项启动合同：RestControllerAdvice、组合 ResponseBody 方法、CompletionStage<Page>、ResponseEntity<Page>、普通返回值携带 InertiaContext、local HttpOutcome+ResponseBody、继承泛型 wrapper、request-scope bad advice、父容器 bad advice；有效 typed request-scope advice 与 REST advice 共存，构造器故意抛错但没有被诊断实例化。原9项映射合同保留，总19项通过。扫描不调用 advice resolveBean/getOrder；初次 lazy bean 试验发现 Spring 自身排序会实例化 singleton lazy advice，因此不把 lazy singleton 永不实例化作为本库承诺。

新增真实 MockMvc 泛型异常页合同，初次发现继承 T=InertiaResponse 的 handler 虽被适配器接受，prepareAdviceContext 使用 erased method return type 导致 flash 被错误页消费。修复为按 containing class 解析真实返回类型；泛型 advice 应与直接 Page handler 一样使用 fresh sessionless context。初次失败证据 /tmp/inertia-advice-generic-initial.log。该合同不以启动通过代替请求行为验证。


修复后 targeted 24项（19 startup +5 MockMvc advice）全部通过，日志 /tmp/inertia-advice-generic-fixed.log。第一套aggregate KlDTwS仅证明启动诊断版本：运行中追加了泛型请求负例和修复，不能作为最终源码/277项证据；随后已对最终源码启动新的clean aggregate，结果另记。


最终clean aggregate16阶段全部exit0，Java277项零failures/errors/skipped；21库制品内容/source/Javadoc、五配置浏览器、build/SSR故障、CSP/root/history、A→B与独立发布通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-WDE1vw/summary.json，日志 /tmp/inertia-advice-final-aggregate.log。源码在这套执行期间未变；只在结束后补本文结果。默认服务未重启；本轮6文件尚未提交，未宣称新代码的远端CI或目标生产资格。J2的HttpOutcome advice/session writes与自动装配替换组合、J3身份策略及其他工作包继续。


## HttpOutcome exception advice 的隔离效果与原会话绑定

新增真实 MockMvc 合同先复现两个问题：direct controller throw 后复用 Context，失败请求排队的 flash 会被 advice redirect 提交；props 失败后的 Context 已关闭，local HttpOutcome advice 返回500。初次失败 /tmp/inertia-outcome-advice-initial.log。

InertiaMvcConfigurer 在创建正常 typed request Context 时保存原 HttpSessionStore 引用。prepareAdviceContext 同时识别 InertiaResponse/HttpOutcome（按具体 containing class 解析泛型），先 abort 原 context；Page advice 继续 sessionless，outcome advice 创建 fresh Context 并复用原 store/namespace/observer。不会复制旧 pending/shared，也不会从 request.getSession() 重新绑定失效/被移除的状态。advice 只提交自己的 pending；原 reservation 恢复后等待成功页面领取。普通 REST advice 不参与。

5项新 MockMvc 合同覆盖：direct/async props/inherited generic global advice flash+errors merge、旧 pending 丢弃、单次领取/无重放与 namespace 隔离；无 Context 参数的 local HttpOutcome 在 props 失败后仍302并保护原 flash；真实 cookie/header CSRF 下 PUT advice 302→303；invalidated session 不新建；detached namespace 不重绑且其他 namespace 保留。targeted29项（19 startup+5 Page advice+5 outcome advice）通过，日志 /tmp/inertia-outcome-advice-expanded.log。

此行为延续既有“提交会话后写响应”边界；网络/servlet write 失败不能伪称撤销已完成 merge，也不保证客户端已经收到重定向。会话库 fail-closed 检查不等于任意 container invalidate 的原子性。


最终clean aggregate16阶段全部exit0，Java282项零failures/errors/skipped，library-artifacts21、五配置browser、build/SSR故障、CSP/root/history、两版切换、独立发布全部通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-8xrFwl/summary.json；日志 /tmp/inertia-outcome-advice-aggregate.log。默认服务没有重启，未宣称远端CI/目标生产资格。此前6文件增量加本轮新测试共7文件尚未提交；完整目标继续，下一步为J2自动装配替换/应用策略组合与J3身份链路。


## Boot 自定义 codec 与适配器替换

新增实际 WebApplicationContextRunner + WebMvcAutoConfiguration + MockMvc 合同，初次复现自定义 PageCodec 虽用于 props，MVC InertiaContext 却创建默认 codec，导致同页 flash 使用不同 serializer。初始失败 /tmp/inertia-overrides-initial.log。

InertiaMvcConfigurer 新增六参数构造器显式接受 PageCodec，原三/四/五参数入口继续委托默认codec以保留兼容。Boot 默认 MVC configurer 注入容器 codec；正常request、Page/outcome advice 及库 error resolver 均沿该 codec 创建 Context。InertiaExceptionResolver 保留原内部入口并增加 codec 参数。没有把 private PageCodec 注册到 Spring REST converter，也没有更改应用 ObjectMapper。

4项新合同：custom codec 的 props/当前 flash/跨redirect delivery/typed local advice 输出一致，普通 REST DTO 不变；custom resolver/renderer/MVC configurer 让默认bean back off且实际请求仅一个适配链；多个codec无Primary明确启动失败；带Primary时props/context序列化一致。custom MVC使用新六参数入口，并验证serializer贯穿其请求 effects。targeted10项（原Boot6+override4）通过，日志 /tmp/inertia-overrides-expanded.log。自定义组件的config/codec/budget一致性由应用负责，默认properties不强行覆盖已替换实例。


最终clean aggregate16阶段全部exit0，Java286项零failures/errors/skipped；源码/Javadoc与21库制品检查、五配置browser、build/SSR故障、CSP/root/history、A→B与独立发布通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-CJwB2o/summary.json，日志 /tmp/inertia-overrides-aggregate.log。测试运行期间未修改产品源码。默认服务未重启，未宣称远端CI/目标生产资格。本轮和前轮合计10文件未提交；整体J0–J7目标继续，J3示例身份与会话/CSRF联动仍需落地。


## 可选身份示例：Spring Security 登录、会话与 CSRF 边界

新增 DemoAuth，inertia.demo-auth=true 时替换默认公开 security chain；默认示例行为保留。要求操作员明确配置至少12字符的 inertia.demo-password，内存用户名demo、BCrypt hash，仅作为local integration sample；未建自定义认证协议。Spring Security负责formLogin过滤器、credential验证、newSession fixation策略、SecurityContext持久化及logout/session/CSRF清理。request cache关闭且所有redirect target固定，避免未审查saved target。

新增Auth/Login与Auth/Account React/SSR注册；login使用官方useForm +forceFormData，让标准Spring认证filter读取servlet参数，失败generic credentials错误，不回显密码且提交后清空输入。account只暴露authentication.name，private/no-store、encryptHistory；login clearHistory。成功登录在干净会话/configured namespace排队clearHistory，不复制匿名flash/errors。anonymous document访问account固定303/login；Inertia访问409 +X-Inertia-Location触发新文档，鉴权发生在MVC/props之前。

BrowserCsrfFailureHandler支持trusted recovery map，默认仍仅/users；auth模式增加/login→/login与/logout→/account。CSRF拒绝只保存safe _csrf并303固定target；不重放登录/注销。过期header注销仍保留authenticated session，用户显式重提后才清理。页面展示安全提示。CookieCsrfTokenRepository与既有BrowserCsrfHandler在登录/注销后的新GET发新token。

3项真实MockMvc合同通过：anonymous account控制响应且不创建session；login失败generic error单次交付/无密码、仍无权限；成功login原session invalid+新ID、匿名flash隔离、新CSRF、clear/encryptHistory，旧header注销拒绝且会话保留，有效注销使会话失效并拒绝account。日志 /tmp/inertia-auth-contract.log。

browser matrix增加auth与auth-csr两配置，显式执行auth.spec与既有flows并reset auth flag。真实Chrome两配置均通过：页面SSR首屏/CSR mount、multipart失败/成功登录、密码清空、sessionID/CSRF cookie更新、授权HTML内容、stale logout review/explicit retry、注销后官方Link访问account的409/full reload。其余公开users/once/CSRF流程同时通过。初始运行 /tmp/inertia-auth-java.log发生在最终newSession/CSRFmap等源码改动前，仅属当时scope；最终clean aggregate结果另记。

这不是生产身份系统资格；真实用户目录、HTTPS/cookie部署、集群会话和访问策略由应用提供。自然session expiry、跨标签页和history后退的更多身份边界尚未宣称通过。依据Spring官方CSRF/session-management/logout文档（链接见inertia-java/README），实际版本以锁定依赖为准。


最终clean aggregate16阶段全部exit0，Java289项零failures/errors/skipped，21库制品与source/Javadoc、七配置browser（含auth SSR/CSR）、build/SSR故障、CSP/root/history、A→B及独立发布通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-obuV62/summary.json；日志 /tmp/inertia-auth-aggregate.log。另外实际新jar以loopback临时port启用auth但不给密码，启动如期exit1并提示配置密码，日志 /tmp/inertia-auth-missing-password.log；没有启动或操作默认服务。本轮与前轮尚未提交；完整目标继续，未将本地验收等同远端CI/生产资格。


## 跨标签页注销、后退历史与真实容器会话过期

新增browser合同先实测复现另一个标签页注销后旧账户仍留在原标签页；初始失败 /tmp/inertia-auth-boundaries-initial.log，细节 /tmp/inertia-java-browser-matrix/browser-auth.log。首次执行的history步骤后来补强为“再次进入public页，把private account留在后退链”，不把初次失败误称已覆盖补强后的完整轨迹。

新增frontend/auth.ts；成功logout visit才写same-origin localStorage revision。root仅demo-auth模式输出meta并安装storage/pageshow监听：其他tab收到注销提示以replace重新加载/login，官方Login.clearHistory清理其private history；BFCache恢复时比较当前revision，避免恢复注销前document。通知只让客户端丢弃旧显示，不授予权限、也不替代Spring Security。storage不可用时当前tab仍完成注销，其他tab下次protected request仍由服务器拒绝；不承诺不可用storage时跨tab主动通知。

新增auth.spec合同分别验证多tab注销通知+private后退链，以及真实Tomcat idle expiry：新增auth-expiry矩阵使用server.servlet.session.timeout=1m，无该session请求地等待65秒，不使用page clock或手工删cookie冒充过期；之后login为anonymous且sessionID变化，official Link访问account仍409，再fresh login成功。矩阵仍reset expiry flag，其他场景明确skip这项较长用例。


八配置browser均通过，auth与auth-csr包括跨tab注销/private后退；auth-expiry实际测试耗时1.1m，浏览器日志 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-EkLYbr/browser-matrix/browser-auth-expiry.log 记录65秒真实等待后的匿名/新session/denied Link/fresh login。该项不把人工invalidate或cookie删除作为idle expiry替代。后续aggregate发布阶段继续，最终结果另记。


最终clean aggregate16阶段全部exit0，Java289项零failures/errors/skipped，21库source/Javadoc制品检查、八配置browser、build/SSR故障、CSP/root/history、两版切换及独立发布通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-EkLYbr/summary.json；日志 /tmp/inertia-auth-boundaries-aggregate.log。源码/产物一致；默认服务未重启，本轮与前轮增量未提交。

J3首版单节点HttpSession、表单验证/flash一次领取和失败恢复、namespace、CSRF显式重提，以及示例登录/注销/真实idle expiry/多tab注销/private后退的既定路径已有对应证据，因此从“未关闭实施项”移除J3。集群会话仍按原计划不属首版，生产身份系统资格仍不由本地demo代替。pageshow guard已有实现，但本轮back路径未额外断言pageshow.persisted=true，不把它宣称为所有BFCache/浏览器专用分支认证。完整目标继续，其他工作包与发布资格未据此缩小。


## 原始验收审查、依赖/许可证清单与transfer/DOM缺口

新增第七篇验收逐项审查，明确把deepMerge、partial except和命名error bag的实际UI轨迹列为缺口，不再仅以core metadata通过称完整浏览器覆盖。继续保留原J0–J7要求，不将较广部署/发布资格用本地成功替代。

新增scripts/dependency-inventory.py：固定CycloneDX Maven plugin2.9.1生成reactor含test/provided的实际图，npm官方sbom生成all-platform与production锁图；验证组件重复/图引用/lock逐项完整性。并对照可执行jar内每个BOOT-INF/lib，用SBOM hash匹配第三方、target字节匹配owned依赖；额外Boot jarmode-tools未在Maven project图中，显式保留为archive-only待审查项，而非遗漏或虚构Maven license metadata。保存原SBOM、input/source/jar hash、inventory/review表、原样LICENSE/NOTICE/COPYING/copyright文本按hash存储。包含installed npm实际文本；未安装platform optional只声明锁信息，不伪造观察证据。

npm初次官方sbom命令因示例缺version报EINVALIDPURLTYPE；仅为private示例补0.1.0-snapshot.0，lock根metadata同步，未升级任何dependency/version/resolved/integrity。两次独立收集通过；最终aggregate内再次执行，新增dependency-inventory阶段使总数17。当前89 Maven图组件、44 Java runtime jar、81 npm锁组件、4 production npm锁组件、38 installed npm包；review47项（可同component多原因），包括owned许可证缺失、reciprocal/multiple terms和运行时未识别文本等。success是收集/完整性门槛，publicationQualified始终false；不自动选择license分支、提供法律授权或漏洞清零结论。Maven插件依赖/JDK/OS/browser及bundle级attribution不在该依赖图范围。

新增NonInertiaTransferTest两项真实WebApplicationContextRunner/MockMvc：携stale Inertia头的multipart upload仍201/binary且controller一次、无session/协议改写；StreamingResponseBody真实Spring async dispatch保留二进制（含null/非UTF8/脚本文本bytes）、Content-Disposition/Cache-Control、无Vary/X-Inertia且不创建session。targeted2项通过 /tmp/inertia-non-page-transfers.log。

新增opt-in固定/failures/payload探针，About仅在收到payload时显示pre。官方客户端SSR/CSR实际Page/DOM测试验证script delimiter、&及U2028/U2029字节精确恢复；明确无CSP响应头，恶意script不执行、唯一Page JSON script、不出现pageerror。因此不会靠CSP掩盖JSON script boundary问题。最终aggregate结果另记。


最终clean aggregate17阶段全部exit0（新增dependency-inventory），Java291项零failures/errors/skipped；21 source/Javadoc库制品、89 Maven/44 actual runtime jar/81 npm锁图完整性、八配置browser、build/SSR故障、CSP/root/history、A→B及独立发布通过。证据 /var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-OVdppl/summary.json；依赖明细位于同目录dependencies/，日志 /tmp/inertia-acceptance-aggregate.log。DOM probe在failures/namespace SSR与csr-failures均通过，且无CSP掩护。另比较HEAD与当前lock JSON，除root与packages[""] version外完全相同，所有dependency metadata/resolved/integrity未变。

技术清单通过不等于47项review获法律批准或publicationQualified=true；自有分发许可证与第三方条款仍需实际关闭。默认服务未重启，本轮与前轮改动未提交；整体目标仍未证明完成。明确下一项为J6 deepMerge/partial except和命名bags实际UI验收，见第七篇。


## 高级 props 与命名错误袋的真实客户端验收

新增 `/advanced` 示例和官方 React 客户端流程：nested deepMerge 保留未更新字段，members.id 匹配更新/去重，重复delta不重复插入；reset替换profile并移除merge/match metadata。except明确排除昂贵回调/profile，同时always status越过排除；下次only expensive计数仅增加一次，证明被排除回调没有执行。两个form同用name字段但分别传profile/team errorBag，验证错误隔离、成功后本表单清错、另一表单本地错误保留和flash无重放。

初次aggregate在optional计数断言失败，证据 `/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-Y9gakn/summary.json`。核对 `src/props/resolver.rs` 的partial筛选与full-visit排除规则，以及Java `PropsResolver` 后，确认except-only会执行未被排除的optional回调，两端一致。修正浏览器断言并明确检查except响应optional=1和后续only optional=2；未为通过测试修改core语义。README记明这项Rust兼容行为，调用方如需跳过optional须显式except。


提交前最终完整验证：17阶段全部exit0，Java291项零failures/errors/skipped；八配置浏览器包含Advanced两项新流程均通过，SSR故障、CSP/root/history、A→B发布切换与独立部署通过。证据：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-V9MTC1/summary.json`；日志 `/tmp/inertia-precommit-aggregate.log`。本次仅在执行期间补充说明文档，产品与测试源码保持稳定。验收审查已关闭上述deepMerge/except/命名bag浏览器缺口；远端CI和正式分发/目标环境资格仍未据本地成功关闭。


## 英文 API 指南与随制品分发的可编译示例

新增 `inertia-java/docs/api-guide.md`：依赖选择、独立adapter生命周期、Page/Context调用、props加载/受控异步、merge/once/scroll/bigint、session/error bags、MVC advice、Boot替换bean及SSR/Vite/root使用边界。提供完整 `CoreApiExample.java`、`SpringApiExample.java`，不是仅有无法编译的片段；README与设计索引提供入口。

发布打包复制整个 `inertia-java/docs/` 并纳入不可变release inventory，部署验证冻结输入时同样包含docs。独立Maven消费脚本从实际发布目录原样复制这两个源码，记录SHA256，在仓库外使用private Maven repository/cache解析七个库后编译。core示例实际检查PUT303、当前props与redirect flash一次交付；真实Spring/Tomcat检查HTML、JSON、optional partial选择、stale409、PUT303、命名profile bag、成功flash和无重放，以及带stale Inertia头的普通REST。没有用reactor class目录作为消费classpath。

本轮 `verify-maven-consumer.py` 全部预期结果通过，sources/Javadoc解析与缺core拒绝/恢复也通过；缺core阶段exit1是预期负例。证据 `/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-maven-consumer-qsy71nfy/summary.json`，日志 `/tmp/inertia-api-guide-consumer.log`。仅复用旧private cache的第三方/plugin依赖，明确排除io.inertia，当前库通过新的fixture repository重新解析并逐jar比字节。

`node inertia-java/deploy/verify-release.mjs` 同时通过，证据 `/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-deploy-PyCTjI/summary.json`；实际release `d499533eb2ea5ce9abd770220e1f78a07a6b852be9c4516459db0b729dcb7b43` 包含指南与两例hash，SSR/CSR、Node断开Java存活、优雅停止、篡改拒绝和恢复全部通过。此次未修改运行库/前端源码，未重跑完整17阶段或Java291合同；使用前次已构建库加本轮实际消费/部署证据，不能称远端CI通过。本轮新增改动未提交，默认服务未重启。J7英文API使用指南交付已有证据；正式许可证/分发、签名/版本及目标环境资格仍未关闭，整体目标继续。


## CI 接入独立消费与英文指南例

`.github/workflows/inertia-java.yml` 在完整aggregate之后增加独立Maven消费步骤，包含指南两例的编译/执行和真实Spring HTTP合同。证据归档保留consumer summary、命令日志、独立POM与实际复制源码；不上传private cache或重复release目录。缓存只种入第三方/plugin依赖，io.inertia仍在隔离fixture repository重新解析。

`verify-maven-consumer.py` 新增可选 `INERTIA_CONSUMER_OUTPUT`，只接受新目录/空目录；未设置继续创建独立临时目录。summary增加当前HEAD/dirty与verifier SHA256。以CI同一入口运行成功：`/tmp/inertia-java-api-ci-consumer-20261009/summary.json`；九阶段全部得到预期结果（缺core拒绝exit1为负例），当前七库及两例hash均记录。独立负例实际给出非空目录sentinel，命令拒绝且目录字节/清单未变。PyYAML解析workflow七步骤成功；这是语法/本地入口证据，不是Actions远端执行证据。

尝试只读GitHub Actions API获取当前远端结果，返回HTTP403 rate limit exceeded；不将查询失败解读为CI失败，也未宣称dirty工作区已在远端运行。当前待用户明确Java许可证、版权主体与年份：Rust Cargo.toml有MIT声明但仓库没有LICENSE正文，Java没有自有license声明。已提出该问题，继续保留分发资格未关闭；未自行编造版权或授予许可。本轮不修改库/前端运行源码，不重复前轮17阶段/291合同；新增CI、消费者入口和文档改动未提交。


## 原验收要求补强：真实禁用 JavaScript 的首屏与导航

原计划04 §3明确写了JS禁用检查；此前只读HTML response验证SSR内容，再在启用JS的浏览器做hydration。虽然能证明HTML包含内容，这不是同一个浏览器模式。本轮新增flows.spec实际 `browser.newContext({ javaScriptEnabled: false })`：可见heading/list Ada、Linus/精确大整数文本；deferred停在首屏占位且没有stats；点击About走document navigation，返回text/html与SSR内容，页面title/link可见，全程没有X-Inertia请求。每个SSR配置保留no-javascript.png；未把禁用JS模式描述为可完成依赖JS的表单/deferred操作。

按新增CI顺序在稳定工作区连续执行：17阶段aggregate → 独立Maven consumer。aggregate证据 `/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-2Dzjat/summary.json` success=true，17阶段全exit0；Java291项零failures/errors/skipped；八配置browser全部通过，六个SSR配置实际运行No-JS用例，两个CSR配置明确skip。SSR faults、CSP/custom-root/history、A→B和带英文guide的独立deployment同时通过。消费者证据 `/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-maven-consumer-ds66qusp/summary.json` success=true，九阶段预期结果全部满足（缺core拒绝exit1）。日志 `/tmp/inertia-java-final-ci-order.log`。执行期间未改产品、测试或发布包输入，仅在完成后追加本记录。

这些是当前工作区的本地完整证据；没有以此冒充远端Actions/Linux/systemd/正式分发资格。Java版权/许可证问题仍待用户回答，未自行添加许可证。前轮和本轮改动尚未提交；默认服务未重启，整体目标保持进行中。


## Java 工程采用 Apache-2.0

用户于2026-10-09明确选择Apache-2.0。新增 `inertia-java/LICENSE`，原样取自Apache官方 `https://www.apache.org/licenses/LICENSE-2.0.txt`，SHA256 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`。Java parent POM声明许可证，各子模块继承；私有React示例package与lock根metadata声明Apache-2.0。只作用于inertia-java原创代码/文档/示例，第三方条款保留，Rust Cargo.toml的MIT声明未变。未根据GitHub用户名推断或编造版权主体，也未修改官方正文附录的标准示例。

Maven resources把LICENSE带入binary/source `META-INF/LICENSE`；Javadoc资源机制把正文带入 `resources/LICENSE`，依赖型starter的Javadoc同样带正文且保留说明index。示例Boot jar实际在root `META-INF/LICENSE` 保留正文；发布payload根目录复制LICENSE并纳入release hash。产物验证新增21个分类jar逐字节许可证检查。

实际验证：Maven clean verify成功，Java291项零failures/errors/skipped（`/tmp/inertia-apache-build.log`）；npm ci/双build成功，lock JSON对照证明仅package根license变化、所有dependency versions/resolved/integrities未变；21jar验证 `/tmp/inertia-apache-library-artifacts.json` 通过。独立Maven消费 `/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-maven-consumer-zxeefhxq/summary.json` 与部署SSR/CSR/Node断开/完整性 `/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-deploy-5bu5N2/summary.json` 均success=true，release manifest含LICENSE正确hash。依赖清单 `/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-dependencies-z7uvtb66/summary.json` 成功，十个owned parent/module/frontend声明均为Apache-2.0；第三方review剩31项，未伪称获得法律批准或public publicationQualified=true。

自有许可证选择和正文缺失已解决，不再以版权署名信息阻塞Java实施。实际发行的第三方归属/notice和目标环境资格仍按其真实范围记录，开发和本地验证可以继续。此次未重跑17阶段aggregate；做的是与许可证打包相关的完整Maven、前端build、制品/依赖清单、独立消费和发布部署验证。默认服务未重启。


## J0–J7 首版收尾验收（2026-10-09）

补齐原04要求的可重复开发模式验收：新增test:development与独立Java/Vite端口，hot文件写实际监听端口，CORS限定该Java origin；JS禁用首屏、hydration、表单、once/scroll/deepMerge/named bags实际8项通过。该门槛纳入aggregate第18阶段。Node端增加decoded Page envelope校验，组件registry使用Object.hasOwn；14项无效输入拒绝后合法Error页面仍能渲染。首轮恢复断言未考虑React文本分隔注释，修正为读取去HTML标签的文本后通过，未修改组件以迁就断言。

最终完整18阶段及独立Maven消费通过；Java291项零failures/errors/skipped。最新[逐项审查](07-acceptance-audit.md)覆盖原J0–J7、17行用户矩阵和02/03实施约束；[归档摘要](acceptance/2026-10-09-final-local-summary.json)保存命令结果与源状态。Java Apache-2.0与royalwang/2026版权声明已按用户决定落地。原任务的发布准备已交付；公开上传/正式签名、第三方最终归属批准及具体生产主机认证为独立后续事项，不新增为本实施任务阻塞。
