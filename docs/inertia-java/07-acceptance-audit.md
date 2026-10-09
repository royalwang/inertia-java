# 首版实施验收逐项审查

审查基线：2026-10-09 当前工作区。原始要求来自 [实施计划与验收](04-delivery-plan.md) 与 [实施细节设计](03-implementation-design.md)，不以测试总数代替要求覆盖。初始设计文档是历史快照；最新执行证据见 [实施记录](05-implementation-status.md)。

“已有证据”仅支持表内写明的范围；不代表所有输入、部署环境和组合均已认证。以下审查保留原 J0–J7 范围，当前整体完成仍未证明。

## 工作包与可定位证据

| 原要求 | 当前实现/证据入口 | 审查结论 |
|---|---|---|
| J0 版本、官方客户端、script/root/SSR/bigint spike | `inertia-java/compatibility/README.md`；37 Page +45 HTTP Rust exporter；实时TTL gate；frontend lockfile；SSR/root browser harness | 已有实际 Rust oracle 和官方客户端证据，差异明确；不是所有非法输入的跨语言等价证明，也未认证 Rust MSRV1.88 |
| J1 Maven/core/HTML/JSON/409/303/Vary/安全JSON | core 模块、`CoreContractTest`、`RustHttpParityTest`、`ConfigPresentationTest`；本轮加入 `/failures/payload` DOM probe | 基础协议和配置有证据；DOM probe结果随本轮aggregate归档 |
| J2 MVC/starter/直访/点击/404/REST | MVC configurer/validator/resolver；`MvcContractTest`、`MvcErrorPageTest`、`InertiaHandlerValidatorTest`、`MvcAdviceContractTest`、`MvcOutcomeAdviceContractTest`、`InertiaOverridesTest` | 有实际Spring/Boot集成；wrapped/async Page明确不支持，普通Spring async transfer另验收 |
| J3 session/form/验证桥接 | session/core/MVC failure tests、validation bridge、flows/auth browser矩阵 | 原定首版单节点路径已验收，含namespace、失败恢复、身份与真实idle expiry；不扩展为集群session保证 |
| J4 lazy/optional/always/partial/deferred/并发/失败 | `PropsResolver`、`CoreContractTest`、`PropsOverloadTest`、`CancellationContractTest`、`SessionFailureTest`；Users deferred browser | 核心规划/回调/队列/取消/权限失败有证据；生产数据库和容量归应用部署资格，不等同库内并发合同 |
| J5 SSR/Vite/完整首屏/hydration/Node断开 | SSR failure tests、ViteBuild/ViteAssets tests；browser matrix、build-integrity、SSR-failures、release-switch/deployment scripts | 实际React/Node证据存在；开发模式历史记录可定位，生产当前aggregate另归档；真实Linux/proxy/storage未认证 |
| J6 merge/deep/prepend/once/scroll/history/bigint | `AdvancedPropsTest`、Rust Page fixtures、Feed/History/onceTTL/auth browser | scroll append/prepend/reset、once与history/bigint有客户端证据；新增Advanced页面及advanced.spec：deepMerge实际保留字段、按ID更新/去重与reset已通过八配置浏览器 |
| J7 CI/打包/部署/兼容/英文API/许可证 | workflow、release/runtime/systemd、classifier/consumer verifier、README/Javadoc、dependency-inventory | 打包/本地独立消费/部署演练已有证据；本轮新增依赖/许可证声明清单；自有许可证、正式版本/签名与目标环境等发布资格未关闭，当前dirty工作区不能引用旧CI为当前CI |

## 原始用户验收矩阵逐行检查

| 原验收场景 | 具体证据 | 当前边界/缺口 |
|---|---|---|
| 普通访问与点击导航 | flows首屏/hydrate/Link网络JSON，MockMvc | 已验证 |
| SSR正常，JS前有内容且可交互 | first document内容、SSR marker、hydration/表单，部署独立pair | 已验证实际内容与交互；非仅200 |
| SSR断开/慢响应/非法JSON/null | `HttpSsrFailureTest`、mock peers、SSR-failures/CSR浏览器 | core/gateway异常分类与有界fallback已验证；不把所有fault组合均称浏览器覆盖 |
| stale version不调controller且flash保留 | MVC observations/request lifecycle、core session/HTTP parity | 已有对应层合同 |
| mutation redirect/fragment/prefetch | 45 HTTP oracle合同；MVC outcome advice PUT303 | 方法、状态、body、多值headers与明确Java差异有证据 |
| partial only/except、异组件完整、未命中零调用 | core/parity、Feed only/reload | advanced.spec在八配置浏览器验证except请求头、响应排除、昂贵回调零执行和always越过排除；optional沿Rust的except-only选择规则执行 |
| deferred group首屏metadata与后续取值 | core/parity、Users Deferred | 已验证首屏与真实后续加载；更多组合不据此外推 |
| callback failure/rescue | core overload/cancel/rescue、MVC安全错误页 | default失败与允许rescue有核心/适配合同 |
| flash/error bag一次展示和目标bag | core errors/parity，browser default/all-errors/form | advanced.spec双表单同名字段、profile/team请求bag、错误隔离/成功清错/无重放已通过八配置浏览器 |
| 同会话并发/失败恢复/不覆盖新flash | SessionContract/Failure、HttpSessionStore、MVC session failure | 单节点预留语义已验证；网络exactly-once与集群不作保证 |
| merge/reset/scroll UI | Feed actual InfiniteScroll追加/前插/去重/reset | Feed验证scroll路径；Advanced另验证nested deepMerge/matchOn/reset实际UI状态 |
| once/TTL/fresh | Rust实时TTL、Java Clock、官方client expiry-1ms/exact expiry、Feed refresh | 已验证；浏览器Date控制不用于服务端session过期 |
| 大整数与恶意字符串/Unicode | CoreContract安全JSON、Users bigint、CSP probe；本轮固定payload DOM probe | bigint实际精度已有证据；本轮补独立于CSP的实际Page script边界与显示检查 |
| headers/status/errors/无递归 | MVC error/advice/requiredSSR、404 browser、header tests | 已有对应层证据 |
| 多应用/多请求auth/props/flash隔离 | MVC isolation、HttpSessionStore namespace；auth SSR/CSR/多tab/idle | 单节点应用边界已有证据；不声称完整多租户生产身份资格 |
| Vite发布切换与旧hash可用 | release-switch、immutable asset archive、independent deployment | 本地实际A→B/CSR/rollback输入完整性有证据；真实目标路由/存储待资格 |
| 非Inertia REST/上传/下载 | 原REST合同；本轮 `NonInertiaTransferTest` | 两项实际MockMvc合同新增multipart和StreamingResponseBody：stale Inertia头不触发409、字节/业务headers保留、无session、async由Spring处理 |

## 命令、产物与额外门槛

| 要求 | 当前入口 | 审查边界 |
|---|---|---|
| Maven reactor、npm ci/typecheck/双build、独立peers | `node inertia-java/scripts/verify.mjs` | 当前命令增加dependency-inventory成为17阶段；最终结果必须读取summary，不用历史16阶段结果替代 |
| Rust oracle freshness | `node inertia-java/compatibility/verify-fixtures.mjs` | 实际export，正常Java构建只读fixture；fixture生成需独立Rust工具链 |
| classifier source/Javadoc | `verify-library-artifacts.py`、隔离负例脚本 | 21 jar字节/内容检查，不等同完整英文API教程覆盖与发布授权 |
| 仓库外Maven消费 | `verify-maven-consumer.py` | 独立POM/private repo/cache，14 classifier与缺core拒绝；本轮未重跑，旧证据仅说明其当时jar |
| 发布bundle/部署模板/旧资源保留 | `deploy/release.mjs`、`verify-release.mjs`、runtime/systemd | 当轮实际macOS/loopback资格；Linux模板不能仅凭存在宣称在systemd目标可运行 |
| 性能基线P50/P95/P99/并发/props/DB/资源 | [本地HTTP基线](06-local-http-benchmark.md) | 数据库负载明确0；SSR/CSR/refused/stalled有数据，不据此承诺业务生产容量 |
| 依赖/许可证检查 | `scripts/dependency-inventory.py`，原始CycloneDX/npm SBOM、实际Boot jar匹配、LICENSE/NOTICE hashes | 技术清单完整性与声明收集门槛；不生成自有许可证、不把多许可证列表自动解释为OR、不提供法律批准或漏洞清零结论 |

## 下一步按明确缺口推进

1. J6 deepMerge/partial except与命名error bags的上述浏览器缺口已补齐；继续按原要求审查剩余制品/分发资格。
2. 完成英文API及正式分发所需的许可证/归属与制品资格；依赖清单里未决项需实际审查。
3. 对最终提交运行对应CI；目标Linux/proxy/storage与正式签名/namespace资格保留独立证据，不以本地演练替代。

本文提供审查结果，不删除或弱化原始设计要求；未证明项继续保持未完成。


## 本轮执行结果

最终17阶段aggregate全部exit0，Java291项零failures/errors/skipped。新增transfer两项与无CSP的payload DOM SSR/CSR用例实际通过，依赖清单完整性通过但publicationQualified=false。证据：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-OVdppl/summary.json`；同目录`dependencies/`含原SBOM、44 runtime jar对照、license文本与47项review。该次运行时J6/bag仍有缺口；后续Advanced验收补齐这两项，发布资格继续保持未完成。


提交前最终完整验证：17阶段全部exit0，Java291项零failures/errors/skipped；八配置浏览器包含Advanced两项新流程均通过，SSR故障、CSP/root/history、A→B发布切换与独立部署通过。证据：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-verify-V9MTC1/summary.json`；日志 `/tmp/inertia-precommit-aggregate.log`。本次仅在执行期间补充说明文档，产品与测试源码保持稳定。验收审查已关闭上述deepMerge/except/命名bag浏览器缺口；远端CI和正式分发/目标环境资格仍未据本地成功关闭。
