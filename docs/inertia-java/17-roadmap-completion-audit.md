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
