# Inertia Java 开源文档实施台账

日期：2026-10-09。对应 [开源文档规划](08-open-source-documentation-plan.md)。本台账记录工程验收，用户学习入口是 [公共文档首页](../../inertia-java/docs/index.md)。

## 当前范围

D0–D2已有本地验收；D3新增27篇部署/参考/测试/排错/社区正文，累计69篇新英文正文与既有API guide，共70页。实际Javadoc站点入口、公开符号/配置地图、贡献/安全文件和部署workflow已写入。11篇优先中文及revision检查、六个运行库完整公开源码Javadoc契约已落地；版本快照工具已通过本地合同，真实tag版本快照、认证核实安全报告渠道与公开部署仍未完成，不能将70页数量视为完整首版验收。

正文采用任务与概念结构，提供前置条件、工作目录、操作、预期结果、失败边界和后续入口。frontmatter 登记当前 `0.1.0-SNAPSHOT`、仓库源码与验证入口，网站折叠区展示来源。版权沿用 Apache-2.0 与 `Copyright (c) 2026 royalwang`。

## 产物与边界

| 产物 | 已实现行为 |
| --- | --- |
| `inertia-java/docs/package.json` / lockfile | 私有 Node 文档工具，不改应用 frontend 依赖 |
| `.vitepress/config.mjs` / theme | VitePress 1.6.4、默认主题、本地搜索、已有正文导航、编辑入口、来源说明、非根 base |
| `docs:check` | markdownlint；Markdown parser 检查清单、依赖环、状态、标题、版本、来源、内部文件与锚点 |
| `docs:build` | 严格 VitePress 死链接检查与静态站构建；canonical Java 下载复制 |
| `docs:smoke` | 实际 preview 进程、81篇中英文正文、搜索及对应页/英文回退、双语移动目录、下载/Javadoc字节与浏览器错误检查 |
| `docs:examples` | 分发包中的两份既有 Java API 示例独立编译/运行，再验证首次应用教程 |
| `docs:first-application` | 仓库外独立 POM、完整 frontend 构建、无JS SSR、JSON、hydration、表单/flash/导航及CSR |
| `docs:quick-start` | 从当前 Git 源文件清单复制干净源码（不带 target/node_modules/dist），重新 Maven/npm 构建，验证完整示例的 HTML/JSON 与浏览器流程 |
| `deploy/documentation.mjs` | release 与 frozen-inputs 共用复制范围，保留正文/示例，排除根工具与所有依赖/生成目录 |
| `.github/workflows/inertia-java-docs.yml` | prose check/build/smoke 与按改动选择的 Maven/示例任务；只读权限、固定 Actions commit、失败证据归档 |

D0–D2阶段检查未覆盖完整 API 符号。D3新增官方JDK搜索索引驱动的公开类型/成员锚点检查和配置地图；完整公开源码成员说明后来已补齐并启用六模块严格doclint；索引检查本身仍不冒充全网外链可用性或所有 Markdown fence 编译。正文中的 canonical Java 示例与 First application 文件有真实编译/交互验证；普通命令或概念片段按其描述的边界审查。

## 发布包复制策略

根目录 `node_modules`、`.vitepress`、`public`、`scripts`、文档 package/lock/linter 配置与根versions.json属于 authoring tooling，不进入 Java release。递归排除示例中的依赖和 target/dist/.inertia 生成目录；保留嵌套示例的源文件、package manifest 与必要脚本，不能一律删除 JSON 或 scripts 目录。

策略测试验证 canonical 文件字节、发布与冻结范围一致、嵌套示例元数据保留以及不支持的符号链接拒绝。部署演练额外将 manifest 中的 docs 清单与冻结输入比较，并验证不存在私有依赖/站点目录。静态 HTML 单独作为 workflow preview artifact，不混入 Java payload。

## D0–D2历史本地验收

源码基线为 `86d5c3ed64ed993abba84905aedd827879d610ee` 加当前未提交文档/工具变更；不是新的已发布版本。运行环境 Java21、Node22.22.2、Maven Wrapper3.9.16，浏览器使用本机 Chrome。CI 配置使用 Playwright 配套 Chromium，不能由本机成功推断远端成功。

| 检查 | 结果与范围 |
| --- | --- |
| `./mvnw install` | reactor 构建与测试通过；本地库用于独立教程 |
| `npm run docs:check` | 44个 Markdown 文件（含 GitHub README）、70条清单、43篇可读正文通过 |
| `npm run docs:build` | 严格构建通过，无全局 dead-link 忽略 |
| `npm run docs:smoke` | `/inertia-omega/inertia-java/` 下43页直达/导航，四类基础搜索与CSRF/deep merge/once/health搜索结果点击、移动目录、两个下载原文和零console错误通过 |
| `npm run docs:first-application` | 独立 Maven/npm 构建；无JS SSR、带版本JSON、表单错误/成功、一次flash、导航与renderer停止后CSR通过 |
| `npm run docs:quick-start` | 当前完整源文件快照重新构建通过；不是复用温热 target/dist 的演示 |
| `node --test deploy/documentation.test.mjs` | 发布范围合同通过 |
| `node deploy/verify-release.mjs` | 安装 docs 依赖后独立release/runtime/SSR/CSR/篡改拒绝/清理回归通过 |
| 独立 Maven consumer | starter/testing/运行库、classifiers、既有API示例HTTP语义和缺core拒绝通过 |

本机工程证据（用户正文不依赖这些临时路径）：

- 站点：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-docs-smoke-IKDIzS/summary.json`。
- 首次应用：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-first-app-fzokcI/summary.json`。
- 干净源码快速上手：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-quick-start-NVtF6m/summary.json`。
- 发布：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-java-deploy-75P2Ir/summary.json`。
- Maven消费：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-maven-consumer-osn37r5x/summary.json`。

实施中发现并修正教程细节：Inertia GET 需要当前 `X-Inertia-Version`，否则协议返回刷新409；原始curl示例不能宣称无版本请求会返回Page JSON。干净源码演练必须复制根目录 Rust语义输入，因为 Java TTL合同通过资源读取它们；该问题出在演练复制范围，未修改运行库或放宽测试。

## D2应用指南验收

新增29篇正文：Application guides9、Props9、SSR6、Integrations5。源码/验证入口登记在每页frontmatter，站点按已有正文生成侧栏；没有把尚未写成的D3目录变成占位页。首页提供表单/认证/CSRF、数据加载/合并/once与SSR/诊断的直接路径。

- `npm run test:browser-matrix`：8个真实生产场景（ssr/all-errors/failures/namespace/auth/auth-expiry/csr-failures/auth-csr）合计77个通过；各模式不适用的测试按既有开关跳过，不能宣称全场景零跳过。认证过期案例等待真实Servlet idle timeout。
- `npm run test:ssr-health`：实际SSR、构建身份拒绝、14种非法decoded Page输入与合法渲染恢复、Node watch重启、UP→DOWN→UP、Java独立存活与CSR恢复通过。
- Java/example源码相对基线未变；读取当前构建的291项unit报告（0失败/错误/跳过）作为API合同证据，没有因纯正文重跑整套Java门槛。核心/适配器的单元证据不冒充自定义数据库、独立Servlet容器或分布式session资格。
- 扩展后真实packageRelease复制49个文档源文件，逐字节比较与当前canonical源一致；私有Node依赖、主题工具及静态站仍被排除。本次仅验证新增正文的发布范围，不将D0全流程部署演练冒充为新的D2逐次部署验收。

机器可读记录见 [documentation-d2.json](verification/documentation-d2.json)，包含页面hash、源码基线、291项unit报告摘要、8模式通过/跳过计数、日志hash、43页网站证据和健康恢复边界。原始浏览器日志在 `/tmp/inertia-docs-d2-matrix.eORf21`，健康日志在 `/tmp/inertia-docs-d2-health.log`，打包证据在 `/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-docs-d2-package-zGotFb/summary.json`。这些临时路径只用于工程复核，不进入公共用户步骤。

## D3英文正文与API入口验收

新增Deployment5、Reference8、Testing4、Troubleshooting5、Community5，共27篇；当前70条英文目录均有正文。维护者确认 `/inertia-java/` 仓库根路径，站点配置与编辑/源码链接已采用canonical仓库 `royalwang/inertia-java`。D2旧base证据保留原记录，没有改写为新路径的验收。

- `docs:check`：71个Markdown文件、70个目录页与链接/来源通过；官方JDK索引对应71个公开类型、496个成员锚点；手写地图覆盖类型所有者、19个core/Boot配置字段和11个timer名称。5个API漂移合同（含遗漏类型/字段/timer的拒绝）通过。这些检查不等于完整成员注释。
- `./mvnw --batch-mode -DskipTests package`：与新CI相同的classifier构建命令通过；未宣称本次重跑Java测试。随后从这次七个真实分类包重新准备API、构建并验收网站。
- `docs:build` / `docs:smoke`：严格构建通过；新base下70页直达与导航、基础搜索及7条搜索结果跳转（含配置/metrics/security）、移动目录、两个Java下载、七模块Javadoc入口及LICENSE/NOTICE字节、代表性成员锚点、零浏览器错误通过。
- Javadoc实际分类包存在未附带的可选DejaVu字体import；站点仅移除该import，使用原CSS的系统字体fallback，并为HTML加入data favicon。输入/输出hash写入生成API manifest；分类包原件未修改，未用忽略404或空资源掩盖问题。
- 本次正文审查明确了交付边界：核心render完成session交付后才由adapter写HTTP；此后的write失败不可回滚已完成的flash消费，不承诺浏览器恰好收到一次。
- 实际packageRelease包含76个文档源文件，逐字节与canonical一致，根工具/依赖/静态站仍排除；发布选择合同通过。此检查不是新的生产部署或全流程runtime演练。
- 新增Java CONTRIBUTING/SECURITY和专用PR模板。SECURITY明确私密报告渠道待维护者确认，没有虚构邮件、已启用GitHub私密报告或SLA。
- 手动Pages workflow已准备：限canonical仓库main，构建/验收后上传独立静态artifact；部署job单独持有Pages/OIDC权限。Actions固定commit，YAML解析通过。此workflow尚未远端执行，GitHub CLI当前未登录，无法认证核实Pages及private-reporting设置。

完整机器记录见 [documentation-d3.json](verification/documentation-d3.json)。当前站点原始证据：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-docs-smoke-sVrBvQ/summary.json`；打包证据：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-docs-d3-package-YqP7gv/summary.json`。原始路径仅为工程复核，不进入用户教程。

## D4优先中文与修订验收

已写11篇中文正文：home、overview、installation、quick-start、first-application、request-lifecycle、forms-validation、SSR setup、configuration、errors、startup。英文仍是canonical；11/70不冒称完整双语库。

- 保留相同page ID与`zh/`下对应路径；frontmatter/catalog双录英文Markdown SHA-256、库版本与来源/验证入口。
- `docs:check`检查82个Markdown文件和81篇正文；中英非Mermaid可执行fence完全相同，所有英文inline API/属性/默认值token均保留。英文变更、命令漂移、未审校/缺失优先页、错误版本/provenance及丢失属性的拒绝合同已覆盖；API+翻译12项合同全部通过。
- 原生locale导航进入语言首页；每页提示链接真实中英对应正文，未翻译侧栏带“英文”且进入真实英文页。未登记中文Markdown会失败，不允许用占位页隐藏缺口。
- 通过VitePress/MiniSearch支持的tokenizer接入标准`Intl.Segmenter`，同一分词用于构建和浏览器。未增加自制分词器或语言依赖。
- 严格构建与`/inertia-java/`浏览器验收通过：70英文+11中文直达、对应英文返回、中英互跳、实际英文fallback点击、中文“校验/预算/会话”搜索结果跳转、canonical Java下载、双语移动目录/无横向溢出和零console错误。移动截图等待目录关闭并禁用截图时的过渡动画，实际检查了无遮挡的中文安装页。
- 发布包实际复制87个文档源文件，逐字节一致，包含11篇译文，仍排除私有工具/依赖和生成站点。没有因翻译正文重跑未变更的Java/应用运行门槛；中文可执行步骤与既有已验收英文完全相同。
- 本轮更新英文首页的中文覆盖说明后，核对中文首页已有11/70、英文真源、修订与剩余发布边界，再更新该页revision；不是无审校批量刷新所有译文hash。

机器记录见 [documentation-d4-priority.json](verification/documentation-d4-priority.json)。站点证据：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-docs-smoke-l3CxJi/summary.json`。版本快照与公开托管未借此标记完成；当前没有已验收的真实release tag快照。

## Javadoc契约说明与逐模块门槛

本轮补充18个Java源码类型文件的说明：SSR3、Vite3、Boot自动配置4、testing1，以及core的InertiaContext、Prop、PropsResolver、SessionStore、RootView、SsrGateway、PageCodec。用途、参数、所有权、加载/提交时机、取消及失败反馈依据实现审查；没有把内部CancellationScope列为公开扩展API，也没有增加网关close方法以迎合旧说明。

- SSR、Vite、Boot自动配置、testing模块启用`doclint=all`与`failOnWarnings=true`，缺失注释/参数同样导致构建失败。core仍有未完成类型，七个本轮源码文件另以`javadoc -Xdoclint:all -Werror`通过；不能用该子集成功代替整个core验收。MVC也保留原missing排除，继续补齐。
- 两个Boot配置类的隐式构造器改成附说明的等价公开空构造器。前后六个运行库共85个class的`javap -p -c -s -constants`结果逐字节相同，覆盖私有/公开签名及指令；不宣称包含调试行号的原始class文件相同。没有改动运行指令或重跑未变的runtime测试。
- 全reactor `-DskipTests package`通过；21个实际binary/source/Javadoc包的源码字节、API/许可证清单和6项隔离拒绝合同通过。从实际分类包抽查18个类型的新契约正文，排除只有源码注释更新而Javadoc仍旧的情况。
- 修正英文及中文配置页对gateway关闭行为的说明；HttpSsrGateway没有公开close，可关闭的是健康监测器。中文配置页逐项复核后更新对应英文revision。InertiaContext的location只验证header字符，目标信任由应用选择，未虚构URL授权保证。
- 最新七个分类包准备后，82个Markdown/81篇中英正文、71类型/496锚点、12项API与翻译合同、严格站点构建均通过。浏览器重新验收`/inertia-java/`的英文/中文直达、搜索跳转、对应页/英文fallback、Java下载/Javadoc归属与双语移动目录，零console错误。
- 实际packageRelease包含87个文档源文件，与当前canonical逐字节一致；私有工具/依赖/生成站点仍排除，发布选择合同通过。此验收只覆盖包内容，未冒称新的生产部署。

机器记录见 [documentation-javadoc-progress.json](verification/documentation-javadoc-progress.json)，包含当前输入、日志、21包校验、85class对比、18处实际分类包正文、站点与发布范围的证据。当前站点证据：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-docs-smoke-cMJBR4/summary.json`；包内容证据：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-docs-d3-package-2FclZ4/summary.json`。历史D2/D3/D4证据保持原时间和范围。

## 六个运行库公开API与严格Javadoc验收

在前一批增量基础上补齐core剩余20个公开源码文件及MVC6个公开源码文件。当前六个运行库共44个公开源码类型文件均有用途、参数、所有权、返回值和主要失败行为说明；record组件/访问器、enum值及可序列化状态也有描述。公开与protected源码说明由完整doclint检查；继承Object/record实现保留标准生成说明，不发明无实际实现的facade。

- core的模型/配置、响应builder、prop定义overlay、session事务、JSON/URL/nonce、观察事件与异常契约已经按实现审查。说明区分浅层不可变与业务值所有权、client metadata与授权、实际HTTP header校验与目标信任，不虚构自动冻结、深拷贝或导航授权。
- MVC明确同步unwrapped控制器协议，以及Servlet线程在预算内等待prop/SSR；不是Servlet异步dispatch。超时/中断取消、session失效/移除不重绑、application advice优先与sessionless最终error Page均按实现说明。
- 校正Spring手写参考：最终异常解析器为包内实现；outcome advice使用fresh context提交自己的新效果；能解析为具体返回类型的继承泛型advice受到支持。观测说明明确Event与logging sink不会自动脱敏自定义字符串。英文首页与中文首页逐项复核后同步覆盖状态及revision。
- 父POM与六个运行库统一`doclint=all`、`failOnWarnings=true`，无missing-comment排除。全reactor `-DskipTests package`通过，Javadoc无warning。两个隔离当前源码fixture分别普通package通过；删除core/MVC公开方法注释后均因Javadoc warnings失败，不靠命令行额外收紧参数才生效。
- 21个binary/source/Javadoc归档检查与6项隔离合同通过；实际分类包中44个公开源码类型均抽查新契约正文，71类型/496锚点保留。MemorySessionStore与Props.Builder新增等价显式空构造器；连同前批Boot构造器，六个运行库全部85个class的公开/私有签名及指令与注释前相同。未将行号/debug/archive差异混作运行指令变化；未重跑未变的runtime测试。
- 最新81篇中英文正文的目录/来源/链接、12项API和翻译合同、严格构建与`/inertia-java/`浏览器验收通过。英文/中文直达、搜索、真实语言对应与fallback、两个Java下载、七Javadoc入口及归属字节、双语移动目录均通过，零console错误。
- 实际packageRelease复制87个文档源文件，逐字节与canonical一致，私有工具/依赖/生成站点排除；发布选择合同通过。该范围验收不冒充公开托管或新的runtime部署。

机器记录见 [documentation-javadoc-complete.json](verification/documentation-javadoc-complete.json)，包含当前44文件与POM输入、七Javadoc分类包digest、85class对比、44处实际正文、隔离门槛和站点/包证据。原始站点证据：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-docs-smoke-p2Krab/summary.json`；包证据：`/var/folders/8x/3x9597tn1tgf738n84985_m80000gn/T/inertia-docs-d3-package-Lh6CID/summary.json`。前批Javadoc增量证据保留历史范围，未覆盖重写为全模块成功。

## 真实版本快照工具与维护流程

维护者已确认GitHub Pages根路径`/inertia-java/`。当前next保留`0.1.0-SNAPSHOT`；历史版本路径为`/inertia-java/versions/<stable-version>/`。本轮只准备可审查的发布工具，没有创建或推送tag。

- `docs:snapshot`仅接受实际`refs/tags`，核对origin同一tag的peeled commit；从该commit导出独立源码，不带工作区修改。要求tag内Maven与catalog为相同稳定版本，再用tag自身lockfile构建、检查、浏览器验收。流程不会替维护者批量改写正文/译文的版本或revision。
- 归档记录tag、commit、base与每个静态文件SHA-256。打包顺序/mtime固定；同一静态输入归档字节可重现，不据此承诺未来工具链重新生成相同HTML。拒绝路径穿越、链接/特殊文件、重复项、超额解压、内容清单漂移和覆盖既有版本。
- `docs:register-version`要求公开release asset可下载、SHA-256及归档内身份一致，再追加映射。CI与base commit比较，拒绝删除或改写既有映射。`docs:assemble-versions`先检查当前构建，再从已登记静态包恢复历史版本，不从当前源码重建旧版。
- 原生版本选择器接入中英文导航。快照链接对应不可变commit，编辑入口改为查看源码；next继续指向main。旧快照保留当时导航，可经next查看之后登记的版本。
- 手动snapshot workflow只产出待审查bundle，读取仓库且Actions固定commit；Pages workflow在上传前合并并检查历史版本。新增workflow解析通过；没有远端运行证据。
- 16项Node合同和5项Python归档合同通过；origin轻量/注释tag、缺失tag及不同commit的隔离拒绝合同使用临时本地Git origin，不代表公开release。发布范围合同与实际87个文档文件逐字节检查通过，根versions.json未混入Java payload。
- 当前81篇站点浏览器检查通过，包括中英文、搜索、双语移动目录、真实下载/Javadoc归属与零console错误。当前registry为空，历史版本导航实际旅程为0；未将这个空集合成功冒充真实release快照验收。
- 本轮再次读取origin，release tag数量为0；GitHub CLI未登录。公开归档、真实tag构建、历史版本往返、Pages部署以及私密安全报告渠道继续保持未验收。

机器记录见 [documentation-version-tooling.json](verification/documentation-version-tooling.json)。实际站点与版本导航证据路径登记在该文件；历史各阶段记录保持原范围。

## 独立文档工具声明清单与操作文档补齐

规划第4节要求的文档工具链依赖/许可证清单此前未单独落地，本轮增加`docs:dependencies`。继续使用npm原生CycloneDX SBOM及既有Java inventory的身份/图验证工具，不新增依赖解析器或npm包。

- 原始npm JSON保持不变；完整比对278个all-platform lock路径、270个唯一组件。npm对嵌套相同包重复使用bom-ref，收集器核对各路径身份、要求重复元数据完全一致，再以合并的相同组件验证图引用；不同声明、漏路径/重复路径或未知依赖引用均拒绝。
- 实际观察230个已安装包路径、225份许可证/归属文件，文件按SHA-256原样保留并记录原路径。48个其他平台optional路径明确未观察；10项已安装组件没有识别到许可文件，列入待审查项。缺失必需包、已安装版本不符、越界许可证链接均失败。
- 清单与Java/应用frontend图区分，保留owned docs package元数据、工具及lock hashes、原始SBOM/日志/inventory/summary/许可证字节。`publicationQualified=false`；没有把所有build包说成已捆绑到静态站，也没有从清单成功推断法律批准或漏洞审计。
- 五项隔离合同通过，已加入`docs:check`。预览/Pages workflow收集并保留独立证据；没有更改Java发行payload范围或把工具/清单输出混入库包。
- 公共许可指南新增具体运行、结果读取与审查边界。浏览器测试指南补齐9个专项gate、18阶段aggregate和独立Maven consumer的工作目录/前提/作用/输出。性能排查页新增真实benchmark命令、参数范围、四种模式、统计/采样与失败反馈，避免依赖500行README才找到操作入口。
- README更正全成员Javadoc已完成的陈旧状态；尚未完成全文专题迁移对应审查，因此保留旧技术段落和锚点，没有提前删除材料。第三方外链手动/定时检查也仍待落地，不能以内部链接检查替代。

当前正文check、26项Node/Python合同、严格构建和81页浏览器检查通过；实际Java payload仍为87个canonical文档文件，逐字节一致。对应机器记录见 [documentation-maintenance.json](verification/documentation-maintenance.json)。本轮未重跑未变更的runtime矩阵或benchmark，没有将命令说明核对冒充新的性能测量。

## README专题迁移与第三方外链维护

本轮关闭前批记录的两项本地文档缺口：44个旧专题均映射到已写规范指南/参考，README由517行收敛为210行；完整旧文按原字节保存在工程verification文本，不进入公共搜索。原标题、级别和GitHub slug均保留在折叠导航。20条上游参考链接迁入13篇对应正文；精细API契约由规范页及完整Javadoc维护，而历史原文保留当时状态/命令，不作为当前学习真源。

- [专题对应表](10-readme-topic-migration.md)与JSON记录每个标题/锚点、目标页、原文SHA-256、上游引用及两项链接修复。`check-readme.mjs`解析原文和当前README，核对44个标题/级别/slug、所有目标为已有规范页、实际链接与fragment、完整原文字节及迁移后的上游引用。没有删除旧文以冒充“无遗漏”，也没有将结构检查宣称为每句语义证明。
- 补充当前Spring API的Jakarta节点/会话说明、SSR decoded Page守卫与Java/React依赖清单操作；旧README两条小写/缺`.html`的Jakarta地址实际返回404，当前参考已改用实际HTTP200的官方Javadoc地址。首次失败的外链证据仍保留，未重写成成功。
- 锁定Linkinator7.6.1，复用成熟HTTP检查与已有进程所有权/预算工具。`docs:links`收集85个规范/索引/贡献文件的Markdown链接/图片，去重并保留原页面/fragment。只检查所列HTTP目的地，不爬取第三方站点；404/410失败，timeout/5xx/429和访问/机器人限制分别保持unverified，不能因为收集完成而报告链接有效。
- 本仓库main源码URL只核对本地目标，标为`repository-source-local-only`；本机/示例URL不联系。此次实际37个目的地中24个外部HTTP地址均可达，另13个本地/源码引用独立分类。未宣称远程fragment、所有生成资源或外站内容时效性通过。
- 三项外链合同通过，其中真实本地HTTP fixture覆盖200/404/410/403/503、重定向、挂起响应超时与不爬取未列链接。纯文字PR仍以内部链接/API/构建为硬门槛；新增只读手动workflow独立处理外部网络波动。四份文档workflow均能解析，Actions全部固定commit；未远端执行。
- 新锁文件的独立工具清单重新生成，覆盖300个锁路径/291个组件、253个已安装路径和248份许可证观察；保留原始SBOM与review项，仍`publicationQualified=false`。Java/React应用锁文件与运行实现未改动。
- 29项Node/Python合同、44专题迁移门槛、严格站点构建和81篇中英文浏览器检查通过。Java payload仍为87个canonical文档文件，逐字节一致，工具/依赖/站点输出未混入。没有重跑未改变的runtime或性能测量。

机器记录见 [documentation-migration-links.json](verification/documentation-migration-links.json)。GitHub连接器进一步确认canonical仓库`has_pages=false`、issues关闭，当前连接仅pull权限；这不代表本机SSH Git push权限。Pages管理、真实tag发布和维护者认可的私密安全渠道仍待处理，未将CLI未登录直接当成所有可用渠道均无权限的证明。读取证据见 [documentation-hosting-state.json](verification/documentation-hosting-state.json)。

## 教程源码导入与键盘交互补验

确认继续使用 GitHub Pages 仓库根路径 `/inertia-java/`。本轮补齐规划第4节尚缺直接验收的代码展示、复制和键盘交互：

- 中英文 First application 使用 VitePress 原生 `<<<` 导入实际创建脚本复制的独立 POM、完整 Java controller 和 TSX 页面；不再只给源码链接。配置页增加 `application.yml` 执行预算片段，明确其余默认值及 unset all-errors 的所有权。
- 翻译合同新增 canonical 导入路径/顺序一致性检查及替换文件拒绝用例；两篇对应英文与中文内容已同步审查并更新修订。静态检查共20项Node合同与10项Python合同通过；现有Boot配置覆盖测试4项通过。
- 真实浏览器首次检查发现VitePress 1.6.4关闭搜索后焦点未恢复；主题增加小型Escape恢复组件，保留原搜索、结果与焦点陷阱。失败receipt保留。复验通过Enter和Ctrl-K打开、输入焦点、Escape关闭与原焦点恢复。
- 英中文各验证XML/Java/TSX/YAML/sh共10次：键盘进入复制按钮，有可见焦点与按钮，按Enter后读取实际系统剪贴板。导入源码与页面逐字符一致，仅VitePress标准高亮会移除末尾换行；复制与实际展示文本一致，不将它宣称为原始文件逐字节复制。既有Java下载仍按原始字节校验。
- 严格构建、81篇中英页面及原搜索/移动导航/Javadoc/console回归通过；Java发布payload的87个canonical文档文件仍逐字节一致，工具与生成站点排除。51项运行源码/POM输入仍匹配先前完整Javadoc审计，未重跑runtime矩阵或性能测试。

机器证据见 [documentation-site-interactions.json](verification/documentation-site-interactions.json)。这些是本地验收；不是全面WCAG审计或远端Pages成功证明。公开托管仍需可管理Pages的维护者身份及正式部署，私密安全联系渠道仍待维护者确认；真实稳定tag快照待实际发布时验收。当前文档工作未提交、未推送。

## 首次推送与远端 workflow 修复

完整文档库已在 `495b67e` 提交并推送至canonical仓库main，203个文件变更；推送后本地HEAD与远端SHA一致，工作区干净。此前各验收JSON的uncommitted字段保留其采集时事实，不回写历史记录。

首次远端检查发现四份文档workflow均因job级env引用runner.temp被GitHub拒绝，尚未运行job。普通YAML解析与Actions固定SHA检查没有覆盖表达式上下文可用性。原始文档验证失败见 [Actions run 37917397282](https://github.com/royalwang/inertia-java/actions/runs/37917397282)。

修复将runner派生的证据目录放到可执行step，通过RUNNER_TEMP与GITHUB_ENV设置后续步骤环境；保留原证据位置和上传路径。加入固定版本actionlint1.7.7校验四份文档workflow表达式。实际回放确认原始四份被拒绝、修复四份通过；shellcheck/pyflakes未启用，不将此项宣称为脚本行为验证。对应远端重新验收另行记录，不以本地lint代替GitHub执行。

### Linux浏览器安装与启动合同修复

runner目录修复已在 `a6721ee` 推送。GitHub随后通过actionlint、Javadoc分类包、publication selection、30项文档合同、独立依赖清单和静态构建；[该次运行](https://github.com/royalwang/inertia-java/actions/runs/37937326508)在浏览器启动时失败：安装命令使用no-shell的完整Chromium，但四个文档browser入口将chromium映射为undefined，导致Playwright选择未安装的headless shell。

四个入口统一直接传递chromium channel，与已有应用browser verifier一致；真实tag构建也复用修正后的入口。保留no-shell安装方式。固定Playwright配对Chromium已在本地实际启动，81篇站点/搜索/键盘复制等回归通过；版本导航命令在空registry下只验证next入口，不冒充真实历史版本导航。远端再次验收结果另行追加。

### 远端CI验收结果

修复Chromium选择的 `adcbb87` 已推送；[GitHub Actions run 37937779692](https://github.com/royalwang/inertia-java/actions/runs/37937779692)完成且success，site/examples两个job均success。site覆盖完整Javadoc分类、actionlint、30项文档合同、独立工具清单、严格静态构建、81篇中英页面/搜索/键盘复制/移动导航验收；examples完成291项Maven测试且无失败/错误/跳过、实际前端构建、外部Maven消费、仓库外First application与干净源码Quick start浏览器验收。Java完整aggregate在同一运行源码的先前 `495b67e` 提交也已远端success。

两份GitHub站点/教程证据artifact已生成，机器记录保留ID、GitHub报告的digest和到期时间。本机取得的临时下载引用返回403，未宣称已下载审查ZIP内容或自行验证artifact digest。各历史失败保留，不回写为成功。验收输入SHA与明确边界见 [documentation-remote-ci.json](verification/documentation-remote-ci.json)。此次没有触发手动Pages发布、真实tag快照或外链workflow；站点CI通过不代表公开部署成功。

## 对外正文精简与首次公开部署核实

根据维护者反馈，公开指南以读者操作为主：中英文首页去掉翻译修订机制、检查门槛、实施进度与发布待办；翻译提示只保留语言入口与英文回退。SHA-256修订继续保留在frontmatter和目录中供维护检查，不在页首显示。页尾保留相关源码链接，移除验证脚本清单和验收说明。

Javadoc站点构建细节、字体补丁、清单哈希和doclint门槛移入不进入站点路由的文档README。入门教程移除发布门槛旁白，保留运行步骤、自动化入口和版本限制；安全页保留尚无私密渠道的真实用户限制，去掉内部launch prerequisite说明。同步审校首页和两篇入门中文及英文修订。

检查还发现原Java下载重写规则将教程HelloController源码链接改成不存在的顶层下载文件。已将重写限定为实际生成的CoreApiExample/SpringApiExample，教程保留GitHub canonical源文件地址，并增加中英文浏览器链接检查。线上旧地址实测404，当前本地修复尚未提交或部署。

与此同时，维护者已启用Pages并手动运行[发布workflow 37939084658](https://github.com/royalwang/inertia-java/actions/runs/37939084658)，其build/deploy均success，发布commit为a483124。当前仓库has_pages=true，公开英文/中文首页均HTTP200；此前has_pages=false的采集记录保持历史原样。本轮文字与链接修订仍为未提交工作区内容，不将旧线上站点当成本轮新稿。

本轮check/build及本地81篇浏览器检查通过，Java payload的87个canonical文档文件逐字节一致，工具仍排除。已对先前发布的真实Pages地址单独执行逐页、搜索、中英切换、代码复制、移动导航和Javadoc锚点检查；该检查通过其列出的路径，但没有覆盖上述教程错误下载地址，后者已单独确认404并在本地修正。Javadoc HTML不与不同JDK的本地生成字节比较；源码下载/版权文本继续逐字节核对。公开probe等待页面hydration后操作，并与本地剪贴板检查串行执行。完整范围和早期失败保留于 [documentation-reader-cleanup.json](verification/documentation-reader-cleanup.json)。

## 后续阶段

D3的27篇正文、Javadoc/地图入口和六个运行库公开源码契约已完成本地验收；D4优先中文路径和revision对应已落地，版本快照工具已实现本地合同，真实tag快照仍待验收。当前英文目录没有planned占位页。

远端文档与Java验证Actions已通过，公开Pages已有首次成功部署。本轮正文精简和教程链接修订尚未提交/推送或重新部署。真实tag快照待实际稳定版本发布时验收，私密安全报告渠道仍需维护者确认。
