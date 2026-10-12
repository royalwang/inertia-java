# 原迭代路线要求审查

日期：2026-10-10。审查对象：[11-next-iteration-roadmap.md](11-next-iteration-roadmap.md)。状态：原路线本次要求已逐项完成本地验收；本文与机器证据共同构成审查。

## 范围解释

原路线图明确R1/R2近期交付、R3 standalone多实例，以及R4先Vue、Svelte单列、WebFlux先ADR并按实际需求启动。本审查保留这些边界：Vue示例需要实际交付；WebFlux需交付设计，不能把未实现的适配器算作支持。Svelte的独立工作包和WebFlux的实现启动条件见[ADR 004](decisions/004-webflux-adapter-boundary.md)。

公共namespace、签名、正式tag和上传属于路线图明确单列的发布操作；用户要求以本地验收为准。因此不将缺少公共发布证据包装成已发布，也不等待GitHub部署。后续[再次迭代路线](14-follow-up-roadmap.md)里的认证强化、Redis TLS/容量与新支持组合不是本次原路线图的追加实现范围。

## 要求对应

| 原要求 | 可定位实现/证据 | 判断 |
| --- | --- | --- |
| R1-1 canonical项目/SCM与一致坐标 | parent POM、制品gate、ADR001；本地坐标保留io.inertia | 本地完成；公共归属未宣称 |
| R1-1 版本政策与发布说明/迁移 | ADR001/002、升级和分发文档、版本工具；保持SNAPSHOT | 完成；无伪造正式版本 |
| R1-1 binary/source/Javadoc与LICENSE/NOTICE | 八库24档案gate，缺失/篡改拒绝合同 | 本地完成 |
| R1-1 仓库外消费无reactor路径 | 独立临时Maven缓存；React/core教程消费与Vue复制工程 | 本地完成 |
| R1-2 公共API所有权/生命周期/取消/失败 | ADR002审查、类Javadoc、保存候选；旧入口仍可用 | 完成 |
| R1-2 配置默认值与替换 | Boot/MVC测试含executor、root、store factory、observer；新Redis默认关闭 | 完成 |
| R1-3 独立应用与canonical示例 | R1机器记录、最新consumer的Java/core/Spring HTTP与Vue实际浏览器 | 本地完成 |
| R1-3 README、安全渠道和公开页面准确性 | 当前README、security指南；工程台账不进入站点正文 | 完成 |
| R1-3 双语、Mermaid与源码下载 | 71英文/71中文，站点check/build/browser与实际Javadoc | 本地完成 |
| R2-1 Rust/Java/官方客户端分维度 | 37Page/45HTTP/9liveTTL；显式Java差异；React/Vue单独交互证据 | 完成 |
| R2-1 Java源码/二进制兼容工具与负例 | japicmp0.26.2、历史候选、移除方法/checked exception/缺模块/篡改合同 | 完成 |
| R2-1 锁定支持矩阵，不混合升级 | Java21/Boot3.5.7保持；Vue独立lockfile，未推导其他组合支持 | 完成 |
| R2-2 分类诊断与reason隐私 | 真实props超时、拒绝、provider/session错误；11timer、PromQL与bounded tags | 完成 |
| R2-2 Node停止/恢复、超大响应与失败边界 | aggregate中真实故障；业务错误不被CSR成功掩盖；observer失败隔离 | 完成 |
| R2-3 CSR/SSR/partial/deferred/props/session测量 | R2原始摘要、三次重复及差异复测；21,264测量请求 | 完成；历史容量基线非生产SLO |
| R2-3 有界过载与恢复 | R2实际2线程/2排队、4成功/28拒绝及撤载恢复；aggregate保留该gate | 完成 |
| R3 模块、依赖复用与可信身份 | 可选redis模块、Spring Data/Lettuce、独立状态域；core不引入依赖 | 完成 |
| R3 原子begin/complete/abort/merge | 共用Memory/Servlet/Redis合同、opaque CAS、epoch/revision | 完成 |
| R3 lease、晚到token、TTL、大小/条目上限 | 实际Redis时间、原子恢复、终态记录与deadline；真实边界合同 | 完成 |
| R3 UNKNOWN_WRITE与不重放 | 真实CAS丢回复代理、禁自动重连/断连排队、bounded预算 | 完成 |
| R3 宿主轮换、失效与恢复 | factory、原生listener、Spring Session前置撤销filter及网络/重启IT | 完成；外部仓库撤销需应用hook |
| R3 真实Redis运行装配 | 本机无容器运行时，ADR003采用固定官方源码SHA构建的自有loopback Redis；不是mock替代 | 真实后端完成；未宣称容器装配资格 |
| R3 双实例HTTP/browser与进程崩溃 | 隔离消费JAR，两个实际JVM、真实Redis、跨节点POST/GET与SIGKILL | 完成 |
| R3 自动装配、默认回归、独立消费与双语限制 | 305标准测试、17Redis/host执行项（1项重复）、R3归档与指南 | 本地完成；无failover保证 |
| R4 Vue独立示例且不复制Java协议 | spring-vue独立业务应用，公共starter/Vite/gateway；共享build工具 | 完成 |
| R4 Vue HTML/hydration/nav/forms/CSRF/deferred/once/scroll/SSR恢复 | 本地及仓库外Vue浏览器链路；新7阶段加入registry/version和tamper拒绝 | 完成 |
| R4 双语使用文档及支持矩阵 | Vue指南、React/Vue分开列版本和边界 | 文档本地完成 |
| R4 Svelte单列与WebFlux独立设计 | ADR004包含Svelte工作包及响应式返回值/取消/WebSession/Security/错误/提交设计 | 设计完成；实现按原计划条件启动 |

## 证据与判断原则

R1/R2/R3原始记录分别在[实施台账](12-next-iteration-implementation.md)、[运行资格记录](13-runtime-qualification.md)、[多实例资格记录](15-multi-instance-qualification.md)。历史结果只用于对应未改动的范围；本轮新增Vue、公共文档及共享build工具使用当前实际结果，不以React成功推导Vue成功。

最终检查当前源码与归档摘要、候选API比较、实际consumer JAR、浏览器构建身份及文档输出相符；本轮aggregate27阶段和Vue七阶段通过。机器结果与范围见[Vue资格记录](16-vue-qualification.md)。R3库的99个源码/测试/资源文件与其归档逐字节一致，因此保持既有真实Redis/故障资格；本轮不声称重新执行Redis网络/双JVM套件。未交付的条件性生态适配与公开发布继续如实列为后续，不把它们写成已支持。


## 2026-10-12 N2/N3 收尾审查

前文为原 R1–R4 完成时快照。新增 N2/N3 使用 [认证实施](18-authentication-implementation.md)、[Redis 实施](19-redis-tls-capacity-implementation.md) 与 [N3 最终机器记录](verification/iteration-n3.json)，不把历史候选当作本轮变更的证据。

| 要求 | 当前实现与证据 | 结论 |
| --- | --- | --- |
| N2a 失败反馈、登录、受保护页面、退出、过期 | React/Vue 实际认证及原生宿主资格；N2 历史记录保持，N3 后再次执行各 16 阶段 Redis 认证 | 本地完成 |
| N2b 双节点身份、轮换、在途 reservation 与故障 | 当前隔离消费八库、两 JVM、真实 Redis；跨节点六阶段及两套认证 16 阶段；外部仓库撤销需应用 hook | 本地完成，边界保留 |
| N3a 默认明文、TLS trust/hostname/auth 和 UNKNOWN_WRITE | 可信/拒绝/执行后丢回复真实合同；独立 Boot TLS HTTP 及选定 observer 实际 CAS | 本地完成 |
| N3b 不同域/热点、连接/CAS/容量/TTL/延迟/吞吐/资源 | 相同 JAR/驱动/环境参数，TCP/TLS 各三轮、4,608 请求；成功/拒绝分离，全部原始 JSON 归档 | 本地完成，无生产 SLO |
| N3b 故障与恢复 | 实际命令 permit、reservation/terminal/bytes、TTL/lease、CLIENT PAUSE 预算、CAS 冲突、读失败/回复丢失、宿主撤销/数据丢失；36 撤载恢复探针 200 | 本地完成，无自动重放 |
| N3 API、可选装配和默认观察器 | 旧构造器/record 保留；八模块 source/binary 对比、314 标准测试、12 Redis 自动装配、24 档案与独立消费 | 本地完成 |
| N3 双语官方文档 | 72 + 72 页，新增使用者运维手册与指标；check/build/14 浏览器阶段；工程备注留工程目录 | 本地完成 |
| N4 新支持组合 | 维护者于 2026-10-12 明确本轮只完成 N2/N3 | 按需暂缓，不冒充完成 |

初次 TLS 首请求失败及磁盘耗尽中断均保留失败记录；补充诊断后的最终统一资格通过，没有隐式重试或跳过。新增容量封装/聚合开关进行了语法、参数拒绝和收据身份篡改拒绝验证；六轮测量使用同一底层 benchmark/helper 直接按序执行，未宣称重新执行整套默认聚合或封装六轮。未改动的协议、SSR、前端功能继续引用对应历史资格；当前改变的 Redis/装配边界已重新执行。
