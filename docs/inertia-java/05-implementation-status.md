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
| Maven reactor `verify` | 通过；core 12、CSP core 4、session 6、session failure 6、advanced props 4、error delivery 4、Rust parity 1、SSR 19、Vite 12、自动装配 6、MVC 5、MVC timeout 1、MVC error pages 4、MVC session failure 5、MVC isolation 1、Validation bridges 2、HttpSessionStore 5、启动诊断 9、resolver 4、request lifecycle 2、CSP filter 1，共 113 项 | Java 合同、会话失败恢复、适配器 wiring；未覆盖所有设计矩阵 |
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
| J1 | 共享/页面冲突诊断、完整配置与错误策略、边界审查 |
| J2 | 已落地专用响应、启动诊断和一次安全错误页；发布前仍需覆盖更多应用 advice / 自动装配替换组合 |
| J3 | namespace 与 fail-closed 失效/写失败策略已落地；示例身份策略与 CSRF 过期恢复的进一步验收仍待处理 |
| J4 | 更全面并发/取消/过载用例和观察；当前任一层超出并发额度拒绝而非排队，需压测评估 |
| J5 | 基础 SSR/Vite 与本地两版切换已验收；真实部署存储/路由资格并入 J7 |
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
