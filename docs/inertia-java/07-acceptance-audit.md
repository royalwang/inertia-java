# 首版实施验收逐项审查

审查日期：2026-10-09。要求来源为 [架构规划](02-java-architecture.md)、[实施细节设计](03-implementation-design.md) 和 [实施计划与验收](04-delivery-plan.md)。这些文件保留初始设计快照；本文件按实际交付审查，历史增量和失败修复记录见 [实施记录](05-implementation-status.md)。

状态：J0–J7 首版实施及本地验收完成。逐项证据如下；正式对外发布和具体生产环境资格保留独立边界，不以测试总数代替行为覆盖。

## J0–J7 工作包

| 要求 | 已交付实现与可定位证据 | 资格范围 |
|---|---|---|
| J0 锁定版本、官方 SSR、协议 fixture | parent POM、frontend package-lock；compatibility/README；Rust exporter 与 Java RustParityTest/RustHttpParityTest/OnceTtlContractTest | 37 Page、45 HTTP、9 live TTL；React3.8.0/Java21/Boot3.5.7 的已锁定组合。四项 HTTP 差异显式声明 |
| J1 Maven/config/request/page/codec/response/policy | inertia-core；CoreContractTest、ConfigPresentationTest、RustHttpParityTest | HTML/JSON、409/303、Vary、多值业务头、安全 JSON、状态保留 |
| J2 MVC starter | InertiaMvcConfigurer/HandlerValidator；MvcContractTest、MvcErrorPageTest、InertiaHandlerValidatorTest、InertiaOverridesTest | 专用同步返回值、直访/点击/404、自动装配、普通 REST 共存；wrapped/async Page 启动拒绝 |
| J3 会话与表单 | SessionStore/HttpSessionStore/InertiaContext、ValidationBridge；SessionContractTest、SessionFailureTest、MvcSessionFailureTest、flows/advanced/auth browser | 单节点预留、一次领取、失败恢复、并发不覆盖新数据、default/named bags、成功 flash |
| J4 props | Props/Prop/PropsResolver、bounded executor；CoreContractTest、PropsOverloadTest、CancellationContractTest、PropDefinitionDiagnosticTest | lazy/optional/always/partial/deferred、规划后求值、并发/过载/deadline/取消、rescue、权限失败不转 SSR fallback |
| J5 SSR/Vite | HttpSsrGateway、SsrEndpointResolver、ViteBuild/ViteAssets、app.tsx/ssr.tsx；gateway tests、development/browser-matrix/SSR-failures/CSP/custom-root | 开发/生产实际 SSR、JS 禁用首屏、hydration/表单、Node 不可用 CSR；同 build、root 和 bigint |
| J6 扩展能力 | AdvancedPropsTest、HistoryOverridesTest、OnceTtlContractTest、Page fixtures；flows/advanced/history/auth browser | append/prepend/deepMerge/matchOn/reset/scroll/once/TTL/fresh、历史加密/清除、大整数；真实客户端状态验证 |
| J7 发布准备 | GitHub workflow、release/runtime/systemd、compatibility matrix、英文 API guide 与两例、classifier/consumer/dependency verifier、LICENSE/NOTICE | jar/source/Javadoc、完整示例/lockfile、独立消费/部署、旧资源保留、故障演练与依赖声明检查。正式对外发布另有资格事项 |

七个库模块和独立 Spring Boot + React 示例均位于 `inertia-java/`；core 的生产依赖仅 Jackson，未混入 Spring、Servlet、Node 或业务应用。SSR/Vite 为独立可选模块。发布 bundle 包含英文 API 指南和可直接编译的 CoreApiExample/SpringApiExample。

## 原始用户验收矩阵

| 原要求 | 对应实现与验证 | 已证明的可观察结果 |
|---|---|---|
| 普通访问/点击导航 | MvcContractTest；flows SSR/CSR | 首次 HTML，Link 后 JSON；URL/标题/页面正确 |
| SSR 正常、JS 前有内容/启动后交互 | flows 的 javaScriptEnabled:false 与 hydration case；development；部署 verifier | Ada/Linus、大整数和 deferred 占位在无 JS 时可见；HTML document 导航；开启 JS 后表单/Link 可用且无 hydration console 错误 |
| SSR 断开/慢响应/非法 JSON/null | HttpSsrGatewayTest/HttpSsrFailureTest、SSR-failures、CSR browser | 有界 fallback 与原因区分；实际 CSR 挂载和导航/表单 |
| stale version | core/HTTP fixtures、MVC lifecycle/observation tests | controller 零调用，409 原 URL，未消费 flash |
| mutation redirect/fragment/prefetch | 45 HTTP oracle cases；MVC typed HttpOutcome advice | PUT/PATCH/DELETE302→303、POST 保持，fragment/prefetch 和多值头按合同处理 |
| partial only/except/异组件/未命中零查询 | CoreContractTest、Page fixtures；Feed/Advanced browser | 同组件过滤、异组件完整；expensive 排除零调用；always 保留；optional 的 except-only 选择沿 Rust 规则 |
| deferred group | CoreContractTest、Page fixtures；Users browser | 首屏省略值并公告 group，后续官方客户端请求得到值 |
| callback failure/rescue | core overload/cancel/rescue、MVC error page tests | 默认安全失败；显式允许的 deferred rescue 省略并公告 metadata；不吞业务权限错误 |
| flash/error bag | SessionContractTest、ErrorDeliveryTest；flows/advanced browser | 一次展示；profile/team 同名字段隔离、成功清错、无重放 |
| 同 session 并发与失败恢复 | SessionContractTest/SessionFailureTest/HttpSessionStoreTest/MvcSessionFailureTest | 独占领取，不覆盖后来写入；准备失败恢复，不宣称网络 exactly-once |
| merge/reset/scroll | AdvancedPropsTest、Rust Page fixtures；Feed/Advanced browser | 实际追加/前插/ID 去重/deepMerge 字段保留与更新、reset 替换状态 |
| once/TTL/fresh | live Rust gate、Java Clock tests；flows TTL/refresh | loaded key 免查询，expiry-1ms 复用，exact expiry 和显式刷新重新查询 |
| 大整数与恶意字符串 | PageCodec/CoreContractTest、Page fixtures；Users/payload DOM/CSP browser | props/flash bigint markers、客户端精确 ID；script 结束标记/Unicode 不破坏文档或执行脚本，payload case 不依赖 CSP |
| status/headers/errors/不递归 | CoreContractTest、MvcErrorPageTest/MvcAdviceContractTest/MvcOutcomeAdviceContractTest/InertiaExceptionResolverTest | 404 状态/业务头保留；安全错误页最多一次，失败转纯文本 |
| 多应用/请求 auth/props/flash 隔离 | MvcIsolationTest、namespace session tests；auth SSR/CSR/多 tab/真实 idle expiry | 请求 DTO/props/flash 不串；namespace 隔离；登录轮换、登出清理、容器过期后重新鉴权 |
| Vite A→B 发布切换 | ViteBuildTest/ViteAssetsTest；release-switch、deployment | client/SSR/manifest build 一致；旧 hash 字节保留，409 刷新，新资源和回滚可运行 |
| 非 Inertia REST/上传/下载 | NonInertiaTransferTest、普通 MVC REST contracts | multipart/StreamingResponseBody 字节和头不变，普通 async 交给 Spring，无协议重写/session 副作用 |

浏览器验收使用锁定的官方 Inertia React 客户端。没有用手工 fetch 代替 hydration；独立 HTTP 请求只用于网络/网关合同。开发模式与生产模式各自启动自有 Java/Vite/Node，生产矩阵包含 SSR、all-errors、failures、namespace、auth、auth-expiry、csr-failures、auth-csr 八种配置。

## 架构与实施细节中的约束

| 设计要求 | 当前证据与实际 API |
|---|---|
| 应用不可变配置、请求快照、一次 render/commit | InertiaConfig/InertiaRequest records、InertiaContext/OperationFuture；InertiaRequestLifecycleTest、CancellationContractTest。普通 @Controller 的专用同步返回类型决定启用范围 |
| MVC before 在 controller 前，异常 advice 先于安全 fallback | InertiaMvcConfigurer/InertiaExceptionResolver；MVC version/error/advice/outcome contracts。实际重定向/location 使用 HttpOutcome，不另造草案中的两个类 |
| 安全 codec、omitted/null、顶层 metadata、headers 所有权 | PageCodec/HttpOutcome/ProtocolPolicy；完整 Page/HTTP oracle 和 CoreContractTest；受保护协议头不能由业务覆盖 |
| 共享优先级、dot 冲突、literal/computed 过滤、顺序 | Props/PropsResolver；CoreContractTest、PropDefinitionDiagnosticTest、AdvancedPropsTest、RustParityTest。点号父子冲突在查询前拒绝；数组不提供下标 partial |
| worker 预算、异步工厂、取消和晚到 effect | bounded executor、CancellationScope；PropsOverloadTest/CancellationContractTest。关闭 context 后拒绝 pending 写；重复 callback flash key 拒绝，不依赖抢锁顺序 |
| 事务/身份/locale 不假定 ThreadLocal 自动传播 | MvcIsolationTest 在请求线程捕获公开身份 DTO 后调度；API 指南说明 callback 显式捕获 immutable data/service，事务由业务服务负责 |
| session namespace/SPI/恢复/validation/history 优先级 | HttpSessionStore/SessionStore/ErrorBags/ValidationBridge；session/failure/history/error tests。单节点原子预留；消息不带 rejected password；浏览器负责历史加密 |
| SSR 最终 Page、内部可信 endpoint、无凭证/重试/redirect | HttpSsrGateway/SsrEndpointResolver；gateway/failure/endpoint tests。JSON visits/withoutSsr/except 不发 SSR；单 HttpClient、响应大小与 deadline/concurrency 有界 |
| Node Page schema 与组件注册表 | ssr.tsx 的 decoded envelope guard；pages.ts Object.hasOwn；health verifier 14 个无效输入拒绝后正常渲染。HTTP JSON 解析/bigint revival 保留官方 server 所有权 |
| health、指标、脱敏日志和独立 Java liveness | SsrHealthMonitor、InertiaObserver/LoggingInertiaObserver、MicrometerInertiaObserver；observation/metrics/health tests 和真实 Node stop/recover。固定低基数 tags，不记录 props/Cookie/响应正文 |
| dev hot/refresh，prod manifest/imports/路径/nonce | ViteAssets/ViteBuild；ViteAssetsTest/ViteBuildTest；development/CSP/custom-root/build-integrity。hot file 写实际监听端口，prod 忽略 hot，CSP nonce 来自服务端 attribute |
| 模板、CSRF、同源 back、代理与个性化缓存 | RootView SPI/PageCodec；CspNonceTest、safeBack HTTP differences、Spring Security/CSRF recovery/auth tests。可信 SSR 片段可 raw，业务模板负责用户 view data 转义；代理信任由主机配置；页面使用 private/no-store |
| 同 build 两进程、禁止请求内 build、静态资源保留 | release/runtime/systemd 模板、immutable receipt/inventory；build-integrity/release-switch/deployment。Java HTTP 请求不执行 npm 或临时启动 renderer |

## 命令与制品

- `./inertia-java/mvnw -f inertia-java/pom.xml clean spotless:check verify`：完整 reactor、291 tests、编译、格式、binary/source/Javadoc 打包。
- `node inertia-java/compatibility/verify-fixtures.mjs`：真实 Rust 重新导出 37 Page、45 HTTP；九项 live TTL 的时间窗口/回调 trace；不覆盖写回 oracle。
- `node inertia-java/scripts/verify.mjs`：18 阶段，包含 npm ci/typecheck/client+SSR build、依赖声明清单、开发模式、生产八配置、资源/故障/CSP/root/history/A→B/独立部署。
- `python3 inertia-java/scripts/verify-library-artifacts-test.py`：隔离副本的有效包、源码篡改、缺 API 文档/class、缺 classifier、恢复六种检查，不污染构建结果。
- `python3 inertia-java/scripts/verify-maven-consumer.py`：仓库外 private repository/cache，七库与14 classifier 重新解析，指南两例编译和真实 HTTP，缺 core 拒绝后恢复。
- `npm run benchmark`：手动性能入口。[基线](06-local-http-benchmark.md) 已记录并发1/8/32、DB负载0、props 数量/字节、SSR比例、P50/P95/P99、超时和资源，包含 SSR/CSR/refused/stalled；不把它当生产容量保证。

Java 自有材料使用 Apache-2.0；`NOTICE` 精确为 `Copyright (c) 2026 royalwang`。binary/source jar 的 META-INF、Javadoc resources、Boot jar及release payload携带 LICENSE/NOTICE，verifier逐字节检查。第三方依赖保留原有条款；清单完整性和声明收集检查不等同法律批准。

## 完成范围与独立发布资格

本次目标是按原计划实现 Java 库及完整示例，J7 是发布准备。公开上传 Maven 仓库、正式版本/tag、签名与仓库 namespace/凭证、第三方最终归属审批，以及具体生产 Linux/proxy/CDN/session/storage 部署认证，仍为后续正式发布/上线事项；本次没有执行或宣称完成这些操作，也不把它们追加为原实施任务的阻塞门槛。

首版原定不包含 WebFlux、Redis/Spring Session 集群原子存储、Vue/Svelte SSR 示例、Precognition 或内嵌 JS 引擎。保留这些原始边界；不宣称对所有版本/环境/输入组合或 Rust MSRV 的完整资格。

## 最终执行证据

最终18阶段完整通过：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-1xEHYv/summary.json`；包含新增开发模式与 Node schema/registry 检查。Java291项零failures/errors/skipped，开发模式8项passed、5项模式限定skip；生产八配置均通过，Node输入14项拒绝后恢复、故障/CSP/root/history/A→B与仓库外部署通过。源码在验证时以f85ec26为基线且dirty，随后整体提交；不伪装为验证了尚未生成的commit。

独立消费 `/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-maven-consumer-e3ad0vhg/summary.json` success=true；缺core的exit1为明确期望负例，恢复成功。Rust新鲜导出37 Page/45 HTTP和9 live TTL通过；日志`/tmp/inertia-java-final-fixtures.log`。依赖清单完整性通过，31项第三方review仍单独列出。Apache/NOTICE随21库jar、Boot jar和release携带。

[归档验收摘要](acceptance/2026-10-09-final-local-summary.json) 保存阶段退出码、运行环境、source HEAD/dirty、浏览器各模式计数、独立消费、依赖/构建与原始证据路径；[性能基线摘要](benchmarks/2026-10-09-local-http-summary.json) 是明确记录早期构建的性能起点，不冒称当前产品或生产容量测量。

版权提交 `f85ec2658efc193a27c5ae3ccdd7aa3b9f72087f` 的 [远端 CI](https://github.com/royalwang/inertia-omega/actions/runs/37892386224) 已 success，覆盖当时17阶段和独立消费。新增代码提交后的18阶段及消费结果以 [工作流对应commit的run](https://github.com/royalwang/inertia-omega/actions/workflows/inertia-java.yml) 为准；远端结果在推送后核查，不能用此前CI替代。
