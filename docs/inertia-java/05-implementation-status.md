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
| Maven reactor `verify` | 通过；core 12、session 6、advanced props 4、Rust parity 1、SSR 2、自动装配 4、MVC 4、MVC timeout 1、Validation bridge 1，共 35 项 | Java 合同、会话失败恢复、适配器 wiring；未覆盖所有设计矩阵 |
| Rust `cargo test --all-features` | 通过，69 项（包含 doctest） | 现有库回归，新增 exporter 不修改库逻辑 |
| Rust → Java Page parity | 四组 fixture 完整 JSON 比较通过 | 初始、nested partial、deferred partial、异组件 |
| npm typecheck | 通过 | 当前示例 TypeScript |
| npm client + SSR build | 通过 | 生产双 bundle |
| 实际 Node `/render` | 返回 head/body、一个 Page script 和一个 app root；含 server-rendered 标记 | 默认 root、Page 裸请求体、bigint 精确呈现 |
| Chrome SSR flow，1280×900 | 通过 | HTML 首屏内容、hydrate、JSON 导航、deferred、空表单错误、有效表单 flash、无应用控制台错误 |
| Chrome CSR fallback，18081 | 通过 | 不可达 renderer，空 app mount、deferred、导航、无 pageerror |
| Chrome Vite SSR flow，18082 | hydration/导航/表单流程通过 | 开发 endpoint、hot assets、精确 bigint |
| 手机 390×844 截图 | 已采集 | 当前示例视觉快照，未做全浏览器/全设备认证 |

浏览器运行使用 frontend-testing-debugging 技能；Browser 插件不可用，使用本机 Chrome + Playwright。Java 源码经 Spotless/Google Java Format 格式化；Maven Wrapper verify 已实际运行通过。每种模式跳过另一模式用例，最新生产 SSR 为 2 passed / 1 skipped，CSR 为 1 passed / 2 skipped；合起来验证两条链路。第一次发现 favicon 404 后修复模板并重跑通过。

## 未关闭的实施项

| 工作包 | 尚需完成 |
|---|---|
| J0 | 更多跨语言 fixtures、非默认 root、客户端版本完整兼容清单 |
| J1 | 共享/页面冲突诊断、完整配置与错误策略、边界审查 |
| J2 | @ResponseBody 的完整组合注解启动诊断、错误页策略、更多自动装配覆盖测试 |
| J3 | 完整 Jakarta ConstraintViolation bridge、Page all-errors 模式、响应显式 flash 已实现但还需专门优先级用例、session 失效/写失败策略、认证策略与 CSRF 过期恢复 |
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

本次增量新增 Jakarta Bean Validation 的 DTO 约束和 BindingResult message bridge；first/all messages 返回不可变集合，不包含 rejected value 或 target。示例使用 Spring Security 的 cookie/header CSRF 策略，公共演示路由不要求登录，但缺失或错误 token 的 POST 返回 403；starter 不替应用配置安全策略。MockMvc 验证真实 GET cookie → POST header 路径，Chrome 表单验收检查自动发送 X-XSRF-TOKEN。Page all-errors 模式、认证策略、CSRF token 过期恢复仍未完成。

提交前重新执行 Java reactor verify（35 项通过）、Rust all-features（69 项通过）、前端 typecheck/build 和生产 SSR 浏览器用例（2 passed / 1 skipped，含 CSRF header、validation/flash 与 Feed），最新 CSR fallback 亦通过（1 passed / 2 skipped）。此增量可运行并继续开发，整个 J0–J7 目标仍在进行中。
