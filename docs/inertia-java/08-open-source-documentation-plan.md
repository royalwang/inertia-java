# Inertia Java 开源文档库规划

日期：2026-10-09。代码基线：`86d5c3e`。状态：实施中；D0–D2已有本地可验收交付；D3的27篇正文与实际Javadoc站点入口已落地，D4的11篇优先中文与修订检查已落地；六个运行库公开成员说明与严格doclint已完成本地验收；真实版本快照与公开托管尚未完成。具体状态见第10节。

目标是让外部开发者完成“理解产品 → 跑通示例 → 接入自己的 Spring 应用 → 使用高级能力 → 测试和部署 → 参与贡献”，不依赖阅读实施流水账或 Rust 源码。

## 1. 规划起点的材料与缺口

| 现有材料 | 当前价值 | 开源文档中的处理 |
|---|---|---|
| `inertia-java/README.md` | 构建/运行入口及大量逐次追加的能力说明，超过500行 | 保留简洁产品入口；按主题迁移正文，迁移完成后保留旧标题锚点与跳转，不一次删除 |
| `inertia-java/docs/api-guide.md` 与两份 Java 示例 | 实际 API/生命周期说明；独立 Maven 消费验证 | 保留原路径，作为第一批可用文档；示例继续作为可编译源码真源 |
| `inertia-java/deploy/README.md` | 发布目录、校验、两进程监督与切换/回滚 | 保留运维 runbook；站点增加按任务编排的部署指南 |
| `inertia-java/compatibility/README.md` | 版本组合、Rust oracle、显式差异和验证边界 | 提炼公开兼容矩阵和迁移页；保留完整维护者说明 |
| 六个运行库的 Javadoc + starter 分类包说明 | 已生成符号索引；不等同完整概念/操作文档 | 生成版本化 API 入口，与手写用法参考互相链接 |
| `docs/inertia-java/01–07` | 初始设计、实施记录、基线和验收证据 | 保留为工程档案，不直接作为面向用户的侧栏和搜索内容 |

规划起点缺少完整新项目教程、统一导航、逐项配置参考、失败排查、迁移/升级政策和贡献入口；不是继续向一个 README 追加章节。现有源码包含47个 main Java 文件，文档覆盖应以公开符号和实际行为清单为准，不把文件数当作 API 完整率。

## 2. 读者与核心路径

| 读者 | 首次任务 | 文档路径 | 可验收结果 |
|---|---|---|---|
| 初次了解的 Java 开发者 | 判断是否适用 | Overview → Compatibility → Quick start | 能说明 Java/Node/browser 所有权，并跑通 HTML/JSON 导航 |
| Spring Boot 应用开发者 | 接入自己的项目 | Installation → First application → Pages → Forms | 在仓库外应用中渲染页面，表单验证后 redirect/errors/flash 正确 |
| 进阶应用开发者 | 控制数据查询和客户端状态 | Lifecycle → Props → Partial/Deferred/Merge/Once/Scroll | 用查询计数和真实 UI 说明选择、合并和刷新语义 |
| 运维/平台开发者 | 部署、升级和定位故障 | SSR → Build release → Processes → Rolling upgrades → Troubleshooting | 同 build 启动，Node 故障可诊断，旧资源可用且可回滚 |
| 适配器作者和贡献者 | 复用 core 或修改实现 | Custom adapter/session → API → Testing → Contributing | 理解 SPI 与一次交付边界，能提交最小复现和对应验证 |

安装页必须反映当前 `0.1.0-SNAPSHOT` 的来源：先从源码安装或消费本地产物。只有实际发布后，才增加可验证的公共 Maven repository 安装路径。教程中的默认应用先保持简单，认证、复杂 props、运维分别渐进引入。

## 3. 信息架构与页面清单

机器可读目录见 [open-source-docs-catalog.json](open-source-docs-catalog.json)。它列出70个内容条目：69个拟新增页面，以及1个已存在的 API guide。**planned 表示待撰写，written 表示本轮已有正文，existing-markdown 表示原有正文；这些状态均不代表网站已公开上线。**

| 导航分组 | 页数 | 主要内容 | 交付阶段 |
|---|---:|---|---|
| Home | 1 | 产品定位、版本状态、四个主要入口 | D1 |
| Getting started | 7 | overview、installation、quick-start、first-application、development、project-structure、compatibility | D1 |
| Concepts | 5 | request-lifecycle、protocol、ownership、rendering、versioning | D1 |
| Application guides | 9 | pages、responses、shared-data、forms-validation、flash-session、authentication、csrf、errors、history-bigint | D2 |
| Props | 9 | basics、loading、partial-reloads、deferred、async-concurrency、merging、once、scroll、diagnostics | D2 |
| SSR and Vite | 6 | setup、vite-assets、root-template、gateway、fallback、health | D2 |
| Integrations | 5 | spring-boot、spring-mvc、custom-adapter、custom-session、observability | D2 |
| Deployment | 5 | build-release、processes、proxy-security、rolling-upgrades、operations | D3 |
| Reference | 8 | configuration、core-api、spring-api、ssr-vite-api、protocol-metadata、errors、metrics、javadoc | D3 |
| Testing | 4 | assertable-page、spring-tests、browser-tests、fixtures | D3 |
| Troubleshooting | 5 | startup、ssr-hydration、assets-versions、forms-session、performance | D3 |
| Community | 5 | contributing、support、security、releases、license | D3 |
| Existing API guide | 1 | 保持已有入口和可编译示例兼容 | 已有 Markdown |

目录中每页包含稳定 ID、目标路径、标题、读者结果、阶段、状态与前置页面；各分组附已有源码和验证入口。实施时进一步将示例与测试精确关联到对应文章，避免只以整套测试通过宣称每个片段都正确。

顶栏建议为 Guide / Reference / Examples / Community / Versions / Language / GitHub。Guide 侧栏按任务渐进展开；Reference 单独按模块与配置组织；Examples 指向源码和教程，不额外复制同一份业务应用。首页重点展示已支持的 Spring MVC + React SSR 路径。

## 4. 文档站技术与目录设计

建议采用 **VitePress 默认主题 + Markdown + 本地全文搜索**。其稳定版文档提供内容站结构、静态部署、本地搜索和多语言配置，适合直接维护仓库 Markdown；文档站使用 Vue 不改变示例应用使用 React 的选择。初始候选版本为官方稳定版文档对应的1.6.4，实施时锁定实际依赖与 lockfile；不使用浮动 latest。

依据：[稳定版入门](https://vuejs.github.io/vitepress/v1/guide/getting-started)、[本地搜索](https://vuejs.github.io/vitepress/v1/reference/default-theme-search)、[部署](https://vuejs.github.io/vitepress/v1/guide/deploy)、[国际化](https://vuejs.github.io/vitepress/v1/guide/i18n)。2026-10-09 核对。推荐是本项目的工程选择，不代表框架已经在此仓库完成构建验证。

拟定结构如下；本轮只创建规划和文档索引，不创建空文章或伪造站点配置：

```text
inertia-java/
  README.md                         # 产品/安装/文档入口，逐步收敛
  CONTRIBUTING.md                   # 后续新增，仓库贡献入口
  SECURITY.md                       # 后续新增，确认私密报告渠道后撰写
  docs/
    README.md                       # GitHub 上可读的现有材料索引
    package.json                    # 独立 private 文档工具包，后续新增
    package-lock.json               # 文档站独立锁定
    .vitepress/config.mts           # theme/base/search/locale/navigation
    index.md
    getting-started/ concepts/ guide/ props/
    ssr/ integrations/ deployment/ reference/
    testing/ troubleshooting/ community/
    api-guide.md                    # 保留既有路径
    examples/*.java                 # 保留既有 canonical 示例
    public/                         # 构建阶段复制下载与 Javadoc 资源
    scripts/                        # 内容/示例/站点检查入口
    zh/                             # D4 按相同 page ID 增补
  deploy/README.md                   # 保留源码/制品 runbook
  compatibility/README.md            # 保留维护者协议证据
```

文档 package 与示例 frontend package 完全独立，不修改后者 lockfile，不把站点依赖加入 core 或应用运行包。候选文档构建 Node 基线沿用当前 CI 的22.22.2；先验证站点工具兼容，再固定版本。

实施时配置忽略 `docs/README.md` 作为网站文章，保留它供 GitHub 浏览。`.vitepress/cache`、`.vitepress/dist` 和生成的 Javadoc/示例下载目录不提交。只有已有且完成审查的文章才进入侧栏、搜索和 sitemap；manifest 中 planned 项不生成公开空页。

### 文档工具与应用制品的边界

当前 `deploy/release.mjs` 会复制整个 `inertia-java/docs/`，`deploy/verify-release.mjs` 也冻结整个docs目录。直接在这里运行站点npm ci会把node_modules、缓存或生成站点带进发布输入；这不是只增加.gitignore就能解决的问题。

D0必须同步调整两个入口：用一致的显式文件清单/排除策略构建文档payload，保留现有 `docs/api-guide.md`、`docs/examples/*.java`、已发布正文和版权材料；排除node_modules、站点package/lock/脚本、.vitepress缓存与构建输出。若需要离线HTML文档，作为独立、明确命名的site归档产物，而非隐式夹带整个工具目录。冻结输入与最终payload校验必须使用同一规则。

对应验收：先安装并构建文档站，再执行独立Maven consumer和release verifier；确认API示例原路径/字节保持、没有文档工具依赖或缓存进入Java库/Boot jar/release，site artifact可单独校验。新docs工具链的依赖/许可证清单单独记录，不冒充已有Java运行依赖或React应用清单。

### 站点能力

- 默认主题、代码高亮、目录、上一页/下一页、编辑链接和最后更新时间；移动端导航、键盘搜索、清晰焦点与可读对比度。
- 首版本地搜索，降低第三方账号和索引服务依赖；用 `flash`、`deferred`、`requireSsr`、`session namespace` 等词验收搜索结果。
- Markdown 中 Java/XML/YAML/TypeScript/sh 代码可复制；完整示例从 canonical 源文件导入，站点中的短片段关联示例 ID/区域。
- 搜索、下载链接、代码资产和 Javadoc 都以实际 base 验证，不仅检查首页200。
- 普通内容静态预渲染；网站不需要 Java/Node SSR 服务常驻。自定义组件限于帮助理解的简单协议或生命周期示意，不另造文档 CMS。

## 5. 正文规范与关键参考设计

### 页面模板

每个指南按“解决的问题 → 前置条件/适用版本 → 最小完整做法 → 可观察结果 → 失败与恢复 → 相关参考”组织。概念页先说明所有权和行为，再介绍 API。参考页按字段/签名、默认值、约束、效果、覆盖顺序和链接组织；排查页按症状、检查、修复、如何证明恢复组织。

不能把只显示方法调用的片段称为可直接运行的完整程序。Java 示例需给出 imports、配置、依赖和生命周期；shell 命令明确工作目录、参数、预期 URL 和清理方法。示例中的 demo 认证与内存数据要清楚标识，避免用户当作完整身份/持久化实现。

### 配置参考必须分四类

| 分类 | 真源与例子 | 文档要求 |
|---|---|---|
| Boot 正式属性 | `InertiaProperties` 的8项：props-timeout=3s、response-timeout=5s、props-concurrency=8、executor-core-size=8、executor-max-size=32、executor-queue-capacity=256、all-errors=unset、session-namespace=default | 名称、类型、默认、正值与大小关系校验、对应 bean 和覆盖效果；unset 与 false 不混淆 |
| 应用构造的核心配置 | `InertiaConfig` 的 version/rootId/components/rootView/gateway/shared/presentation/urlResolver | 明确需要 bean/构造器，不虚构 `inertia.root-id` 等为 starter 自动绑定属性 |
| 网关/Vite 组件参数 | endpoint/connect/render/maxBytes/concurrency/build verification、manifest/hot/base 等 | 明确构造器/配置入口、信任边界与资源生命周期 |
| 示例专用开关 | `Application` 中 `-Dinertia.frontend`、`-Dinertia.ssr`、`-Dinertia.development` 等及 demo flags | 给出准确传参方式，`-D` 在 `-jar` 前；不要宣称可普遍替换为任意业务应用 YAML |

Micrometer 的 endpoint label 等环境配置还须从实际读取点逐项收集；同一前缀并不意味着都由 `InertiaProperties` 绑定。属性表应带 source locator，API 变更时能发现漂移。

### API 参考与 Javadoc

六个运行库生成的 Javadoc提供符号、签名、参数、异常和返回值索引；starter 是依赖入口，其分类包当前是模块指南。手写八篇 Reference 负责用法、配置、协议与错误地图，不能以现有 Javadoc 页存在证明说明已完整。

D3 增加公开符号清单与维护要求：公开类型、主要方法、生命周期 SPI 和异常需有用途/约束说明；优先补完善 `InertiaContext`、`Prop`、`PropsResolver`、`SessionStore`、MVC adapter、SSR gateway 和 Vite 的 Javadoc。已有构建只排除 missing-comment doclint，需逐模块收紧，先修正文档再开启更严格门槛。

站点生成 `reference/javadoc/<version>/<module>/...`，从同次 Maven 构建取出实际 Javadoc 内容；校验模块入口、代表性方法锚点和 starter 说明。生成内容不写回源码仓库，不与手写 API guide 复制维护。Javadoc 下载和网站页面均保留 LICENSE/NOTICE。

### 兼容性与升级

明确区分库版本、Inertia 协议版本、客户端版本、Boot/JDK 与 Node 工具链。首版文档标为 `0.1.0-SNAPSHOT / next`，不伪装成已正式发布的0.1.0。最先维护一个 next 内容集；有真实 release tag 后构建不可变版本快照与手动版本导航。VitePress 导航能力不等同自动的多版本文档管理，应由发布脚本维护 tag→站点版本映射。

每次发布说明新增/改变/弃用 API、配置及行为、对应迁移步骤和已验证版本矩阵。Rust 迁移页链接四项 HTTP 差异、dot-path 冲突、rescue 限制、会话预留等真实差异，不承诺所有 Rust 应用直接兼容。

## 6. 示例、迁移和内容真源

| 文档主题 | 优先复用 | 验收方式 |
|---|---|---|
| 最小 core/独立 Spring 集成 | `docs/examples/CoreApiExample.java`、`SpringApiExample.java` | 现有独立 Maven consumer 编译、协议和真实 HTTP |
| 完整入门、页面/表单/SSR | `examples/spring-react` 的 Application、Users、app/ssr | 按正文命令从干净 checkout 启动，HTML/JSON、列表、表单和CSR |
| merge/named bags、scroll/once | AdvancedDemo、Advanced/Feed 组件 | existing advanced/flows browser，补正文特有断言 |
| auth/CSRF/history | DemoAuth、SecurityConfiguration、DemoHistory、对应 browser | 正文明确 demo/应用职责，实际拒绝、重试和跨tab历史变化 |
| 配置/bean override/session/error | InertiaProperties、core配置、MVC与Boot tests | 编译配置示例并证明覆盖生效/错误诊断 |
| SSR/发布/部署 | gateway、Vite、release/runtime 与现有 verifier | same-build、Node stop/recover、旧资源和回滚 |

迁移顺序：先建立新页和链接 → 将既有内容按用户路径重写并验证 → 新旧内容对应表无遗漏后缩减 README → 保留旧锚点/入口。`api-guide.md` 和 `docs/examples` 原路径不移动，避免破坏独立消费/发布脚本。网站的 Java 下载资源由 canonical 示例复制生成，并实际检查 base 路径下返回的源文件字节；不能只让 GitHub 相对链接可用。

公开教程用永久仓库路径/源代码 tag 链接证据，工程档案里保留临时运行日志。本机绝对路径、临时输出目录和逐次构建流水不进入用户侧的学习步骤。

## 7. 文档 CI 与发布设计

以下是文档工具接口。最初规划时未实现；当前落地范围与验收记录见第10节及实施台账。

```sh
# repository root
npm --prefix inertia-java/docs ci
npm --prefix inertia-java/docs run docs:check
npm --prefix inertia-java/docs run docs:examples
npm --prefix inertia-java/docs run docs:build
npm --prefix inertia-java/docs run docs:smoke
```

- `docs:check`：验证目录清单/状态/依赖、Markdown 结构、内部链接与锚点、示例区域、配置名称和来源。使用成熟 Markdown parser/link/lint 工具，只对项目自己的内容合同编写少量检查。
- `docs:examples`：从文章登记的完整示例构建独立 Maven 应用；复用现有 consumer，增补 First application。代码区域从已编译文件提取，不能编译另一份近似代码来代替文档示例。
- `docs:build`：严格死链接检查，生成静态站/搜索、复制下载与可选当次Javadoc；不设全局 ignoreDeadLinks 掩盖错误。
- `docs:smoke`：在 `/inertia-java/` 非根 base 预览，检查首页→快速上手→参考、深链接刷新、搜索、移动导航、示例下载和Javadoc链接；检查浏览器console错误。
- 纯文字改动跑 check/build/smoke；Java 示例/配置/API 改动增加对应 Maven/consumer；功能改变跑原18阶段中受影响的合同，按库现有要求补完整验收。新增 `.github/workflows/inertia-java-docs.yml` 专用校验、`inertia-java-docs-deploy.yml` 专用部署；不把每次文字修订都变成全套应用部署演练。
- 第三方外链定时或手动检查，网络暂时失败与真实断链分别记录，不让任意外站波动阻塞所有文字PR。API符号/内部链接/发布路径失败仍为硬门槛。

### 托管与权限

仓库已重定向至 `royalwang/inertia-java`。维护者已确认使用 GitHub Pages 仓库根路径：`https://royalwang.github.io/inertia-java/`，VitePress base 为 `/inertia-java/`。该地址是已确认的布局，尚不是部署成功证据；部署前仍需核实 Pages 源及现有内容。当前本机 GitHub CLI 未登录。之后经GitHub连接器读取仓库元数据，确认`has_pages=false`且连接器仅有pull权限；未取得Pages管理权限，private-reporting状态仍未核实。需要独立域名时再配置 DNS 与 base。

Pull request 只构建/归档站点预览，不持有部署写权限；可信主分支/正式tag才允许部署。workflow中 Actions 固定commit、docs依赖锁定、Pages deployment串行化，失败保留旧站；按站点需要给 `pages:write`/`id-token:write`，不修改Java库现有CI权限。D0验证文档预览；公开 Pages 部署属于后续实施动作，本轮不更改远端设置。

## 8. 语言、贡献与维护机制

英文作为开源正文真源，中文为优先第二语言；本规划用中文。D4先翻译 overview/installation/quick-start/first-application、核心生命周期、forms、SSR setup、配置与常见错误，保留同一page ID，标记对应库版本与英文revision。未翻译内容显示明确的英文入口，不生成看似已有的空中文页。API名字、属性名和错误码不翻译。

新增 `inertia-java/CONTRIBUTING.md` 和 `SECURITY.md`，由仓库已有维护机制收集实际联系渠道，不编造安全邮箱、维护者团队或支持SLA。贡献文档需涵盖问题报告最小信息、设计讨论、代码/文档变更、focused checks、许可证与第三方归属、release notes；贡献者行为准则采用维护者实际认可的政策后再对外发布。

文档职责随代码模块归属：core、MVC/Boot、SSR/Vite、example/deploy各自审查对应正文与示例。PR模板增加“用户行为/API/配置是否变化、对应页面/示例/兼容矩阵、是否有迁移说明”。不以尚未建立的CODEOWNERS人员名单作当前事实。

## 9. 分阶段实施与完成门槛

阶段按可验收工作包组织，不把目录数量作为完成证明。

| 阶段 | 产物 | 依赖与完成门槛 |
|---|---|---|
| D0 文档基础设施 | 独立VitePress包/lock、主题/搜索、清单驱动导航、check/build/smoke、预览CI | 用已有API guide验证真实构建、下载和非根base；调整release/frozen docs复制范围并回归consumer/deployment；planned文章不可出现为完成页；不部署公开站 |
| D1 可上手预览 | Home + Getting started7页 + Concepts5页，共13新页；现有API guide可读 | 干净checkout跑示例；仓库外新应用教程完整；JS禁用首屏和HTML/JSON/表单；英文用户无需工程流水账即可上手 |
| D2 应用功能指南 | Guide9 + Props9 + SSR6 + Integrations5，共29页 | 每个主题有完整来源/示例/结果；deferred/merge/once/scroll/auth/CSRF有对应交互；应用配置与库配置准确区分 |
| D3 完整首版文档库 | Deployment5 + Reference8 + Testing4 + Troubleshooting5 + Community5，共27页；Javadoc补全/站点入口；发布政策 | 所有69拟新增页及既有API guide审查通过；全部公开导航/搜索/链接/示例可用；公开符号/配置无遗漏；确认托管和维护渠道后发布网站 |
| D4 中文与持续维护 | 优先路径中文、翻译状态追踪、版本快照和升级文档流程 | 关键路径与对应英文版本一致；发布tag构建可重现；不将部分中文覆盖宣称为完整双语站 |

D1是可上手预览，D3才可称完整首版开源文档库。每页完成需同时满足：正文覆盖读者结果；API/参数有源码依据；示例编译或操作实测；必要的异常反馈和恢复可执行；内部链接/搜索正确；版本状态明确；已审查并进入导航。只有标题、复制流水账、TODO或自动生成签名都不满足完成条件。

## 10. 实际交付与当前进度

初始规划完成现状盘点、用户路径、70条目录清单、站点/版本/语言策略、配置/API设计、示例迁移、CI/托管和D0–D4验收设计。此后按用户要求开始撰写标准文档，当前交付为：

- D0：独立 VitePress 1.6.4 工具链、锁文件、可用页面驱动的导航、全文搜索、严格构建、Markdown/目录/内部链接/来源检查、非根 base 浏览器验收和专用预览 CI 配置。
- D0 发布边界：Java 发布与 frozen inputs 使用同一文档复制策略，排除根文档工具、依赖和生成站点，保留正文及 canonical 示例；真实 consumer/deployment 回归通过。
- D1：Home、Getting started 7页、Concepts 5页，共13篇新正文；既有 API guide 保持原路径。每页记录版本、源码和验证入口。完整仓库外 Spring 应用教程包含独立 POM、Java/React 示例与自动化实测；快速上手也从干净源文件副本重新构建验证。
- D2：新增 Application guides 9篇、Props 9篇、SSR 6篇、Integrations 5篇，共29篇；各页提供任务步骤、来源、验证和明确边界。该阶段目录为43篇可读页面、27篇待写页面。八种真实浏览器场景、renderer健康恢复、43页导航/搜索与发布内容边界验证通过；保留为D2历史证据。
- D3正文：新增部署5、参考8、测试4、排错5、社区5，共27篇；70条英文目录均有正文。实际七个Javadoc分类包进入版本化站点；71公开类型、496成员锚点、19核心/Boot配置字段和11指标名称有机器检查。贡献/安全文件与Java PR模板已建立；安全私密渠道未确认，不能宣称公开首版全部完成。
- D4优先中文：11篇（首页、入门4、请求生命周期、表单、SSR设置、配置、错误、启动排错），保留canonical ID、版本与英文SHA-256修订。内置locale导航、每页中英对应、明确英文回退、标准分词搜索已接入；check拒绝过期修订/命令漂移/缺失或未登记中文页。
- Javadoc：六个运行库的44个公开源码类型文件已补齐用途/参数/所有权/结果/主要失败契约，record字段与enum值均有说明；父构建和六个模块默认完整doclint且warnings失败。71公开类型/496成员锚点、21个分类/运行包、85class签名/指令不变、core/MVC隔离缺失注释拒绝、81篇站点回归通过。初期增量记录保留为历史，当前证据见 [完整Javadoc验收](verification/documentation-javadoc-complete.json)。
- 版本工具：已实现真实origin tag源码导出、稳定版本校验、静态归档及SHA-256登记、不可变版本合并与原生版本选择器；本地合同与next站点通过。远端当前没有release tag，registry为空；没有伪造正式版本或真实历史导航验收。
- 文档工具清单：独立npm CycloneDX/lock核对、实际许可证文件与review项已落地，覆盖300个锁路径/291个组件；五项拒绝合同和本地站点验收通过。清单收集不是静态捆绑归属批准；README的44个专题迁移对应、原锚点及历史原文保留已完成；外链检查和只读手动workflow已落地，当前24个外部HTTP目的地可达，远程fragment/内容时效性不在该检查范围。
- 站点交互：中英教程从canonical POM/Java/TSX原生导入完整源码；两种语言五种代码的实际键盘复制/可见焦点、搜索Enter/Ctrl-K/Escape和关闭焦点恢复已补验。见 [交互验收](verification/documentation-site-interactions.json)。
- 未创建空正文，未将 D3/D4全部标记完成；真实tag版本快照和正式托管继续推进；公开API契约随源码维护并由严格检查保护。
- 本地验收与远端状态分别记录：文档库及CI修复已提交推送；远端site/examples两个job和Java完整验证通过，见 [远端CI验收](verification/documentation-remote-ci.json)。公开Pages尚未配置或发布，私密安全渠道仍待确认。

命令、验收边界及证据见 [文档实施台账](09-documentation-implementation.md)。
