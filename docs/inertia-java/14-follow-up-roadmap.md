# Inertia Java 再次迭代路线

日期：2026-10-10。规划基线：已提交的 `aed4400` 与当前 R3 工作区。本文是后续工作规划，不表示新增功能已经交付；原 R1–R3 的进展仍由[实施台账](12-next-iteration-implementation.md)记录。

本轮目标：先完成 Redis 多实例交付收口，再让第二种前端真正可用，随后验证认证会话与运行维护。继续保留框架无关 core、薄 MVC 适配层和可选 Redis 模块。功能验收在本地完成，公开发布和 GitHub 部署单列，不作为实现阻塞项。

进展补充：N0已在本路线规划后的收口中完成，直接证据见[多实例资格记录](15-multi-instance-qualification.md)；下文“当前起点”保留规划时快照。

## 1. 当前起点

| 范围 | 当前事实 | 下一步 |
| --- | --- | --- |
| R1 候选制品与兼容性 | 本地候选、独立消费、公共 API 差异工具已经交付 | 保存历史候选，继续比较实际制品 |
| R2 诊断与容量 | 故障分类、过载恢复、重复测量已有本地证据 | 新能力沿用现有 observer 和测量方法 |
| R3 Redis 状态模块 | 已提交可选模块和真实 Redis 状态/传输合同 | 收口宿主生命周期、自动装配、消费及文档 |
| R3 工作区实现 | 自动装配、宿主轮换/销毁、双 JVM 夹具和双语说明已有代码；最近一次本地双 JVM 六阶段记录为成功 | 完成统一回归、证据归档和 API/文档检查，才更新完成状态 |
| 前端与运行基线 | React 示例锁定 Inertia 3.8.0；Java 21、Boot 3.5.7 | Vue 使用兼容的同代客户端；依赖升级另行验证 |
| 尚未确认的能力 | Vue/Svelte hydration、WebFlux、Redis TLS/Cluster/Sentinel、其他 JDK/Boot/浏览器组合 | 按独立工作包取得证据，不扩大支持声明 |

最近一次双 JVM 记录包含跨节点 flash/errors、重叠领取、lease 恢复及旧 token 拒绝、Spring Session 身份轮换/销毁、真实浏览器 SSR/hydration/CSRF/表单/deferred、JVM 被终止后的恢复。这份临时记录尚不能替代最终源码对应的归档和完整回归。

## 2. 顺序与交付规模

| 阶段 | 优先级 | 应用获得的结果 | 估算 | 启动条件 |
| --- | --- | --- | --- | --- |
| N0：R3 收口 | P0 | 可独立采用的 Redis 多实例 MVC 集成 | 2–4 人日 | 立即进行 |
| N1：Vue SSR | P0 | Spring Boot + Vue + Vite + Node SSR 完整示例 | 5–8 人日 | N0 完成 |
| N2：认证与会话集成 | P1 | 登录、退出、会话轮换与跨节点跳转有完整指导和实际证据 | 4–6 人日 | N0 完成；复用 N1 验收工具 |
| N3：Redis 运行能力 | P1 | TLS 接入、容量边界及故障恢复可配置、可解释 | 4–7 人日 | N0 完成，明确目标部署拓扑 |
| N4：支持矩阵与升级 | P2 | 一项新增环境组合及可执行升级路径 | 3–5 人日/组合 | N1–N3 稳定后按实际采用需求选择 |

采用单人顺序推进。首先承诺 N0、N1，约 7–12 人日；N2、N3作为随后工作包，分别验收。估算包含实现、双语文档和本地验证，不包含公共 Maven 发布等待时间。遇到协议或状态机问题，先修复再扩范围。

## 3. N0：完成 R3，而不是重新设计 Redis

### 实施内容

1. 收口 `RedisHttpSessionStoreFactory`、`RedisSessionLifecycleFilter` 与自动装配。确认默认 Servlet store、应用自定义 factory、显式 Redis 选择及依赖缺失时的行为；禁止 Redis 失败后静默退回本机 store。
2. 把真实 Redis 状态合同、宿主生命周期与双 JVM 脚本整理为可复制命令。普通构建不强制依赖 Redis；显式 Redis 验收缺少运行时必须失败，不以跳过或 mock 算通过。
3. 增加独立 Maven 消费验证：使用新临时本地仓库安装候选制品，消费 starter 与 Redis 可选依赖；消费工程不能依赖 reactor 相对路径。检查八个库模块的 binary/source/Javadoc、许可证及原有 API 兼容性。
4. 同步英文/中文 Redis 指南、配置表和 Javadoc 映射；工程台账归档原始摘要并关联源码状态。公共页面只呈现用法、生命周期、错误处理和支持限制。

### 出口

- 默认 MVC/React 链路保持通过；七个原模块与上一候选比较通过，新模块明确登记为新增。
- 真实 Redis、宿主轮换/销毁、未知写入、断连/重启、双 JVM 与浏览器用例通过；证据与最终候选对应。
- 文档检查、构建及本地浏览器验收通过，中英文 API/配置没有遗漏。
- 修正文档中的“原型待接线”状态；仅声明已经验证的 standalone 范围。容器不可用时允许使用固定版本、校验源码的自有 loopback Redis 进程，并记录差异。

## 4. N1：Vue SSR 完整采用路径

### 结构与职责

新增 `inertia-java/examples/spring-vue/`，复用现有 core、MVC、Vite、HTTP SSR 与 starter。Java 继续负责路由、鉴权、props、协议和根 HTML；Vue 只负责页面与 hydration，不新增 Java 协议分支。

前端使用官方 `@inertiajs/vue3`、Vue SSR renderer 与 Vite 集成。启动时核对与 React 当前锁定的 Inertia 3.x 版本兼容性，再锁定依赖和 lockfile；不要直接照搬旧版教程。Vue 的 SSR 入口与客户端入口共享页面解析方式，每次 SSR 创建新的应用实例，避免跨请求共享用户状态。[Vue SSR 指南](https://vuejs.org/guide/scaling-up/ssr)、[Vue SSR API](https://vuejs.org/api/ssr)

先复用现有构建身份、root id、健康检查和 HTTP gateway 合同。评估 React/Vue 共用的构建清单和进程启动逻辑，只有确实重复且职责相同的部分才抽为内部工具；页面、框架入口和断言保持各自清晰。首个 Vue 工作包不顺带重写 React 示例或提供 CLI。

### 最小用户链路

- 用户列表、详情及创建表单：首次 HTML 包含真实内容，随后完成 hydration 和无整页刷新导航。
- Spring Security CSRF、校验错误、独立 error bags、成功 flash、返回/前进及滚动恢复。
- partial/deferred/once/merge/scroll 按现有协议矩阵逐项执行，避免仅验证首页能显示。
- Node 停止时明确回退 CSR，恢复后重新 SSR；权限与业务查询失败继续按原错误路径处理。
- 构建身份不一致、版本冲突、自定义 root、CSP、非法/超大 SSR 响应沿用现有服务端边界。

### 出口

从空目录按照教程消费候选制品，实际启动 Java、Vite/生产资源和 Node；本地浏览器确认 HTML、hydration、导航、表单及故障恢复。只有完成对应交互的能力才加入 Vue 支持矩阵。新增双语 Vue 指南和与 React 并列的入口，不复制整套通用文档。

## 5. N2：认证、退出与会话生命周期

当前 Redis 夹具覆盖技术轮换，但仍需完整应用认证路径。新增使用 Spring Security 的会话认证示例；不自建账户平台或另造认证协议。

设计先明确匿名访问、登录成功、登录失败、退出、过期、权限拒绝六类状态；记录宿主身份变化、delivery epoch、flash/errors 与响应提交的先后顺序。尤其验证登录成功后的 `changeSessionId`、退出的 `invalidate`，以及旧请求晚到不能消费或复活新会话数据。

Spring Session 会替换宿主 HttpSession，因此过滤器必须放在其后、依赖会话的安全和业务处理之前；实际顺序以所锁定版本及应用配置验收，不从原生 Servlet listener 行为推断 Spring Session 行为。[Spring Session HttpSession 集成](https://docs.spring.io/spring-session/reference/http-session.html)

验收至少包含：跨节点登录后重定向、认证失败表单恢复、并发标签页登录/退出、轮换期间的在途 reservation、session 过期后的重新访问、Redis 故障时不产生虚假的登录成功。外部直接删除会话仓库的撤销策略单独说明；不能假定它会自动执行 Servlet 生命周期 hook。先验证 React，再将通用路径用于 Vue。

## 6. N3：Redis TLS、故障与容量

### 安全连接与配置

目前后端支持 standalone 明文 TCP。通过 Spring Data Redis/Lettuce 原生配置增加 TLS、证书信任和认证能力，不自行实现握手、证书校验或重试。配置优先使用应用提供的 SSL/连接配置机制，具体扩展 API 在编码前通过 ADR 确定；保留已有构造入口与默认行为。

本地 TLS Redis 验收：可信证书成功、错误信任拒绝、主机名不匹配拒绝、认证失败不降级。密码、证书私钥、session identity 和业务值不进入日志或指标。Cluster/Sentinel 与异步 failover 持久性不包含在 TLS 工作包中。

### 容量与故障

沿用 R2 负载驱动，新增不同 session 与同 session 的 Redis 对照；覆盖命令并发上限、连接开销、CAS 冲突、reservation/terminal 上限及 TTL。测量服务端 p95/p99、吞吐、失败类别、JVM/Redis 内存及撤载后的恢复，同参数至少三次；超过阈值先调查，不预设连接池或缓存重写。

沿用现有 observer 和有界 reason。先核对指标覆盖，确需新增时再扩展；不把 Redis key、用户或 session ID 放入标签。故障验收继续覆盖读失败、已执行但回复丢失、重启数据丢失、预算耗尽及人工恢复步骤。任何优化必须保留 UNKNOWN_WRITE 不自动重放的约束。

## 7. N4 与暂缓事项

支持矩阵每次只增加一个实际需要的组合：例如一个 Boot/JDK 组合，或 Firefox/WebKit。独立升级依赖，执行编译/API 差异、自动装配、会话/异常与实际 SSR/hydration，再更新支持声明；不要同时升级 Boot、Jackson、Inertia 和 Vite。

Svelte 等 Vue 完成交付且有采用需求后另立示例。WebFlux 先写返回值、取消、WebSession、安全上下文、阻塞 Redis/SSR 边界及响应提交的 ADR，再决定独立适配器；不能把阻塞 MVC 包进 Mono 就称为响应式实现。

暂不安排管理后台、全套 CLI、跨语言 session、自动依赖大升级或 Redis 高可用保证。公共坐标、正式版本与发布身份在真正发布前确认；缺少发布凭据不影响本地候选交付。

## 8. 实施与状态记录

每个阶段按“行为和边界设计 → 最小实现 → 实际消费/交互 → 双语使用文档 → 证据归档”推进。工作包状态采用未开始、实施中、本地验收完成、已发布；计划、临时通过记录与源码对应的最终验收分开记录。

本轮先执行 N0，随后 N1。后续实施台账应逐项链接提交、候选身份、验证命令、结果和剩余限制；工程记录仍放在本目录，公共文档站不展示这些台账。纯规划只验证链接、路径与差异，不为本文重跑整套 Java/浏览器测试。

关联：[上一轮路线](11-next-iteration-roadmap.md)、[实施台账](12-next-iteration-implementation.md)、[容量记录](13-runtime-qualification.md)、[Redis 状态决策](decisions/003-redis-delivery-state.md)。
