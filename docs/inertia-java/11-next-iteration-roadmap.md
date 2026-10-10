# Inertia Java 下一轮迭代路线

日期：2026-10-10。规划基线：`643ac7b`。状态：R1–R3本地交付完成，R4按采用需求独立排期；实际进展见[实施台账](12-next-iteration-implementation.md)。版本号为建议里程碑，不表示已经发布或承诺发布日期。

本轮目标是让现有 Java 21 / Spring MVC / React / Node SSR 方案更容易被独立应用采用，并建立可持续的兼容性与多实例运行能力。优先顺序为：整理首个可消费版本 → 固化升级与故障诊断 → 实现分布式会话 → 按使用需求扩展前端与服务端适配器。

工程验收以本地结果为准。公开 Maven 发布、GitHub Pages 更新、真实 release tag 快照由后续发布操作验收，不等待 GitHub 发布后的状态来结束功能迭代。本文放在工程设计目录；对外文档只呈现已经交付的行为、使用步骤和明确限制。

## 1. 当前能力与证据

| 已有能力 | 仓库依据 | 下一轮应保留的约束 |
| --- | --- | --- |
| 框架无关核心、七个库模块与 Spring Boot starter | `inertia-java/pom.xml`、各模块源码 | core 不引入 Servlet、Spring 或 Redis 依赖 |
| Page/HTTP 协议、partial/deferred/merge/once/scroll | `inertia-java/compatibility/README.md`：37个 Page、45个 HTTP 与独立 TTL 合同 | Rust 对照、官方客户端交互与 Java 自身策略分别维护 |
| Props 并发、总预算、取消、失败恢复 | core resolver/context、Spring request lifecycle | 权限和业务查询失败不能伪装成 SSR 降级 |
| Session reservation、complete、abort、merge | `SessionStore.java`、`HttpSessionStore.java`、会话合同 | 单节点互斥不能被宣传成跨节点原子性或浏览器恰好一次投递 |
| React 示例、真实 Node SSR、独立应用教程与消费验证 | `examples/spring-react`、docs examples、`scripts/verify-maven-consumer.py` | 保留仓库外应用与真实浏览器验收 |
| SSR 有界 HTTP 传输、健康与 fallback、观察 SPI | ssr-http、`InertiaObserver`、观测集成指南 | 故障分类、预算和恢复行为可解释，不泄露业务数据 |
| 中英文文档、实际 Javadoc、版本工具与图表 | docs、`verification/documentation-local-completion.json`、`documentation-mermaid.json` | 70篇英文/70篇中文同步维护，不在公共页面展示工程流水账 |

尚无合格证据的范围：Redis 分布式 session、Vue/Svelte 实际 hydration、WebFlux、其他 JDK/Boot/浏览器组合、公开 Maven 制品。已有历史测试数量仅作为基线记录；实施时以对应源码下重新运行的结果为准。

## 2. 里程碑与优先级

| 阶段 | 建议里程碑 | 读者或应用获得什么 | 主要工作包 | 出口 |
| --- | --- | --- | --- | --- |
| R1 | 0.1.0 发布候选准备 | 仓库外应用能稳定安装、配置、升级和定位错误 | 坐标/API审查、消费工程、发布说明、文档状态纠偏 | 本地候选制品与独立消费通过；实际发布单列 |
| R2 | 0.1.x 维护能力 | 升级能发现兼容性变化，运行故障能直接定位 | Java API差异、客户端矩阵、预算/过载诊断、性能复测 | 明确支持矩阵及回归证据，无未经验证的支持声明 |
| R3 | 0.2.0 多实例能力 | 两个 MVC 实例共享一次性 flash/errors，失败可恢复 | Redis delivery store、自动装配、双实例与故障用例 | 真实本地 Redis + 双实例 + 故障合同通过 |
| R4 | 后续按需版本 | 应用能选择经过验证的新客户端或适配器 | 先 Vue，之后评估 Svelte；WebFlux 独立设计 | 每个适配器独立例子、文档和实际交互证据 |

R1、R2为确定的近期范围。R3先完成存储状态机设计，再决定是否扩展SPI；R4不与R3捆绑交付。阶段采用人日估算而非固定日历：R1约4–6人日，R2约5–8人日，R3约10–15人日；真实故障与协议问题会影响估算。单人顺序推进为默认安排。

## 3. R1：让首版可独立消费

### R1-1 坐标、源码身份与版本政策（P0）

现有 parent POM 使用 `io.inertia`，项目与 SCM URL仍指向旧仓库 `royalwang/inertia-omega`。下一轮统一为 canonical `royalwang/inertia-java`，同时审查所有发布元数据和示例坐标。

公开发布前确认 Maven namespace 归属。不能从 GitHub 仓库名或包名推断拥有 `io.inertia`。若该 namespace 无发布权，评估维护者可验证的 namespace，例如 `io.github.royalwang`；最终坐标由归属决定。Maven groupId 与 Java package 是不同兼容性边界，修改坐标不自动重命名 `io.inertia.*` Java包。

产物：发布决策记录、统一的POM元数据、候选版本政策、升级说明模板。候选制品在未确认公共坐标前仍可完成本地打包与消费，标明坐标决策未定，不上传公共仓库。

验收：所有子模块坐标、父POM、starter依赖及示例一致；独立消费工程不依赖reactor相对路径；binary/source/Javadoc与LICENSE/NOTICE完整。公共 namespace 审核、签名凭据和上传为发布操作，不是当前编写或本地构建门槛。

### R1-2 公共 API 与默认配置审查（P0）

对 `Config`、`InertiaContext`、`Prop`、`SessionStore`、SSR gateway、Vite 与 Spring 扩展点逐项登记：谁创建、谁关闭、可否复用、线程安全、取消语义、失败后的合法动作。现有Javadoc是输入，重点审查行为一致性与应用接入成本。

保持已公开构造器/方法可用；需要调整时先提供兼容入口或明确迁移。在尚未形成稳定版前也不要无说明修改配置默认值。冻结一份候选公共签名及配置基线，后续R2用于比较。

验收：已有 canonical Java 示例和独立Spring教程继续通过；业务自定义executor、root view、session store、observer等替换场景有覆盖；新默认值或签名变化均能定位迁移说明。

### R1-3 独立应用与文档准确性（P0）

整理一个可复制的最小Spring MVC消费工程：Maven依赖、前端依赖、配置、Controller、根HTML、Vite和Node启动步骤均来自可运行文件。复用当前 First application，不另造第二套容易漂移的脚手架。暂不实现CLI生成器。

纠正README与当前事实冲突的表述：例如仍称私密安全报告未确认；引用最新文档状态时区分“本地通过”和“公开发布”。不要把工程验收表、输入hash、source code库存重新放入公共正文。源码仓库目录调整如有需要，另写迁移ADR，R1不为开源仓库名变化直接搬动整个Maven子树。

验收：从空目录和候选本地制品完成安装；HTML首屏、JSON导航、validation/flash、SSR停机降级可复现；中英文步骤一致，Mermaid图和源码下载正常。

## 4. R2：升级与运行维护

### R2-1 兼容性基线与变更检查（P0）

在现有完整Page/HTTP fixtures上继续维护三个独立维度：Rust参考行为、Java刻意差异、官方React客户端实际行为。新增协议字段/行为先补矩阵再修改实现，不能只调整expected JSON来隐藏差异。

公共Java签名差异检查优先评估Revapi或japicmp，锁定选用版本后比较本地保存的上一候选制品与当前制品；运行时Maven无需联网取一个不存在的旧正式版。源码兼容、二进制兼容和行为兼容分别报告；工具只能覆盖前两者的一部分。

将依赖升级拆分为独立变更。默认保留当前Java21/Boot3.5.7/锁定React与Node组合；新增Boot或JDK组合必须经过编译、配置绑定、MVC异常/会话、真实SSR/hydration验收后才加入支持矩阵。不要在同一提交同时升级Boot、Jackson、Inertia和Vite。

验收：故意删除公共方法的负例被拒绝；新增方法允许；Rust freshness独立运行；当前锁定React的partial/deferred/once/scroll/forms/history恢复链路通过。

### R2-2 故障定位与预算治理（P1）

复用 `InertiaObserver` 和现有Micrometer集成，将已有 `OVERLOADED`、`TIMEOUT`、transport、session与fallback分类连接到排错路径。检查线程池排队、props预算、renderer并发/响应上限是否能帮助应用分清：业务查询慢、执行容量耗尽、Node故障、session故障。

新增指标前先核对现有11个timer，优先补充可用的reason分类与示例仪表查询；不要建立另一套观察SPI。指标标签只用有界枚举，不放URL、用户ID、session ID、prop值或异常正文。管理端点复用Spring Boot Actuator能力，独立适配依赖保持可选。

验收：制造props超时、executor拒绝、Node停止/恢复、过大SSR响应和session失败，实际反馈符合文档；业务失败不被CSR成功掩盖；observer故障不破坏正常业务结果；恢复后可继续服务。

### R2-3 性能与容量复测（P1）

复用 `06-local-http-benchmark.md` 的方法，分别测CSR、SSR、partial/deferred、小/大props和同session并发。记录冷/热状态、负载工具、JVM/Node/CPU/内存、请求数、并发、p50/p95/p99、错误率、排队和堆增长。

使用同机同参数基线比较，初始可将p95或吞吐变化超过10%列为人工调查提示，至少重复三次确认噪声；不据一次本地benchmark宣称生产SLO。先定位再优化；本轮不预设需要缓存、熔断器或线程模型重写。

验收：提交可复现命令、原始摘要与差异解释；过载时内存与排队有界，负载撤除后可恢复；吞吐提升不能以吞掉权限错误或破坏flash投递换取。

## 5. R3：分布式一次性会话

### 5.1 模块与复用边界

建议新增可选 `inertia-session-redis`。core继续只保留 `SessionStore` 契约；Redis连接、序列化与脚本执行复用Spring Data Redis及其客户端，不自行实现Redis协议、连接池或重试框架。Spring Boot自动装配通过classpath与显式配置选择，默认HttpSession行为不变。

Spring Session可用于宿主身份/HttpSession共享，但不能据此认定现有Java互斥锁或session attribute更新具有跨实例delivery原子性。一次性数据使用独立、命名空间隔离的Redis状态域；身份取自可信宿主session，不接受客户端指定任意Redis key。

### 5.2 状态与原子操作

设计输入是现有 `SessionStore` 的begin/complete/abort/merge合同，先用ADR明确以下状态，再写实现：

| 操作 | 原子状态变化 | 必须保留的行为 |
| --- | --- | --- |
| merge | 新pending按现有error bag/flash优先级并入available | 写失败不能半合并；不得覆盖其他用户/应用数据 |
| begin | available转为token对应reserved，随后新写入仍归available | 并发请求不得领取同一份reserved数据；多份delivery按既有合同处理 |
| complete | 校验token及所属domain/epoch后终结reserved | 旧token不得消费后来产生的数据；重复终结不能悄悄影响新状态 |
| abort | 校验token后按现有合并优先级恢复，并终结reserved | 保留reservation之后写入的新值；并发complete/abort只有一个终态 |
| expire/invalidate | domain失效，旧epoch/token被拒绝 | 旧请求不能在登录轮换或失效后复活旧数据 |

状态建议包含schemaVersion、domain epoch、available、reserved tokens、token状态及有界终态记录。UUID token到内部revision/epoch的映射由后端负责；不要未经设计给现有 `Delivery` record增字段。只有确有契约表达缺口时才修改SPI并提供迁移。

同一domain需要原子处理的Redis key使用同一Cluster hash slot，脚本仅操作该domain；序列化沿用受限JSON模型，不启用任意Java类型反序列化。先验证脚本/事务能准确实现现有merge优先级，再确定存储布局。配置至少明确namespace、数据/条目大小上限、session过期与reservation清理策略。

### 5.3 故障与恢复策略

网络超时可能发生在操作已经执行之后。沿用core对未知结果不盲目重试的约束；Redis客户端自动重放脚本的行为需要审查。重复complete/abort、未知token和存储失败要有明确分类，不把失败当作空session或静默切换到本机store。

进程崩溃后的reserved恢复需要lease与epoch/fencing设计。不能仅因TTL到期就恢复数据并同时允许旧请求complete；旧epoch必须失效。终态记录保留时间、晚到请求和session清理必须形成一致的上限策略。Redis异步复制和failover可能丢失已确认写入，不能宣称跨failover恰好一次；支持范围应明确持久化/复制假设及不确定结果处理。

如现有SPI无法让宿主安全完成身份轮换、撤销及后台恢复，先交付ADR与实验结果，不以宽泛的“Redis支持”掩盖缺口。

### 5.4 本地验收矩阵

使用本机容器中的真实Redis以及两个独立JVM应用进程；Testcontainers可作为测试装配候选，实施时锁版本。容器不可用时可交付设计，但不得把mock当作Redis功能验收完成。

必测：跨节点redirect后flash/errors领取；同session重叠请求；独立error bags；begin后新merge；complete/abort竞争；重复/伪造token；session失效与身份轮换；进程被终止后的reservation清理；网络断开和未知写入结果；Redis重启；TTL清理；超大payload拒绝；namespace隔离。先以memory/servlet/Redis共用合同比较，再执行真实两节点HTTP与浏览器链路。

出口：独立模块及自动装配可消费、默认单节点无行为回归、双实例实际证据、失败操作与恢复文档、中英文指南和明确failover限制。不需要连接生产Redis或验证GitHub部署。

## 6. R4：按需求扩展生态

先完成一个Vue + Vite + Node SSR独立示例，复用core/MVC/SSR模块，不复制Java协议实现。实际验证初始HTML、hydration、导航、表单/CSRF、deferred/once/scroll、SSR失败和恢复后，才在兼容表声明Vue合格。Svelte单列工作包，不能由Vue成功推导兼容。

WebFlux需要单独ADR：响应式返回值、取消传播、WebSession、Security上下文、错误页与传输提交如何映射；适配器为独立模块。不能把现有阻塞MVC调用包装成Mono就宣称响应式支持。确认有实际使用需求和验收预算后再启动，R1–R3不承担WebFlux交付。

本轮不安排：全套CLI、管理后台、自己的前端router、Redis协议客户端、跨语言共享session、自动升级所有依赖、无证据的“生产可用”标签。

## 7. 工作顺序、交付与验收

1. R1-1 → R1-2 → R1-3：形成候选制品、消费示例、签名/配置基线和迁移记录。
2. R2-1 → R2-2 → R2-3：形成兼容性比较、诊断场景和容量基线。任何真实回归先修复，再继续扩展支持矩阵。
3. R3 ADR → 原子状态原型 → 共用合同 → 双实例/故障 → 自动装配/文档；存储语义不成立时停在设计评审，不先包装starter。
4. R4按实际采用需求逐项排期，不同时增加多个尚无验收的适配器。

每个工作包交付源码或设计、对应使用文档、验证命令与结果、已知限制。涉及英文指南改动同步更新中文及revision；工程记录保留在本目录。测试范围按变更选择：纯规划仅做路径/链接与差异检查；API/运行行为执行受影响Java合同及真实交互；发布打包执行独立consumer；站点主题/渲染执行build与本地browser smoke。风险变化才扩大回归，不因小改动重复所有历史验收。

进入发布操作前的待决策项：公共namespace、最终版本、签名与发布身份；进入R3实施前的待决策项：Redis支持版本/拓扑、超时后结果分类、reservation恢复、会话身份轮换。缺少公共发布凭据不影响前述本地工作。

## 8. 参考资料与使用边界

以下资料于2026-10-10核对，具体依赖版本在各工作包实施时锁定。

- [Spring Session API](https://docs.spring.io/spring-session/reference/api.html)：Redis session存储和save/flush行为；本文据其并发更新边界推导，宿主session共享不能替代本项目delivery状态机的原子实现。
- [Sonatype namespace登记](https://central.sonatype.org/register/namespace/)：公共Maven坐标需要可验证namespace归属，不从现有groupId推断发布资格。
- [Central Portal Maven发布](https://central.sonatype.org/publish/publish-portal-maven/)：优先使用官方发布集成；本文没有上传制品、申请namespace或创建正式tag。

关联文档：[初始架构](02-java-architecture.md)、[实施设计](03-implementation-design.md)、[首版验收](07-acceptance-audit.md)、[文档实施](09-documentation-implementation.md)、[当前兼容矩阵](../../inertia-java/compatibility/README.md)。
