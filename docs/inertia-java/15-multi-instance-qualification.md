# R3 多实例本地资格记录

日期：2026-10-10。对应[原路线图 R3](11-next-iteration-roadmap.md)。这是工程记录；公共接入步骤见[自定义会话存储](../../inertia-java/docs/integrations/custom-session.md)。

## 交付结果

八个库模块、24个 binary/source/Javadoc 制品通过检查。可选 Redis 模块通过显式配置接入 Boot，默认 Servlet store 和应用 factory 替换行为保留。core 的 SessionStore/Delivery SPI 不变，原七个模块与 R1 保存候选的 japicmp 源码/二进制比较通过。

305项标准 Java 测试、14项真实 Redis 状态/传输测试、3项原生宿主生命周期测试通过，无失败、错误或跳过。14项中的 options 测试与标准套件重叠；另外16项 IT 为新增验收，不将重复执行当作独立覆盖。

完整本地 aggregate 的25阶段通过。六项制品拒绝合同单独通过，并已加入后续 aggregate 入口；本次25阶段不包含该新增入口，避免把单独执行写成已运行的 aggregate 阶段。Rust freshness重新通过37 Page、45 HTTP、9 live TTL。文档check/build及140页、14阶段本地浏览器验收通过，包含八模块Javadoc、81公开类型、570成员锚点与Mermaid。

## R3 要求与直接证据

| 要求 | 当前实现与实际验收 |
| --- | --- |
| 共用事务和合并语义 | RedisSessionStoreIT在Memory/Servlet/Redis上参数化执行；复用MemorySessionStore canonical merge |
| 同session重叠领取 | 独立连接并发状态合同；两个JVM并发HTTP只有一次收到对应flash |
| error bags与begin后merge | 共用合同验证独立bag、新写入、abort优先级和失败不半写入；跨节点redirect验证两个bag |
| complete/abort竞争、重复与伪造token | 真实并发CAS只有一个终态；重复、未知或外域token拒绝 |
| lease恢复与晚到请求 | Redis时间及CAS deadline；原JVM仍存活的晚到complete被拒绝 |
| 宿主身份轮换/失效 | 原生Tomcat HTTP合同与真实Spring Session双JVM链路；旧domain/token拒绝，新会话可访问 |
| 网络断开和未知写入 | 原生宿主合同验证断连阻止轮换；TCP代理令实际CAS执行后丢回复，UNKNOWN_WRITE且不重放 |
| Redis重启/数据丢失 | 原生宿主合同停止并重启自有Redis，保留待协调metadata，不把旧epoch伪装为空新域 |
| JVM崩溃 | 独立消费JAR运行A/B两个JVM，SIGKILL A后B在lease过期恢复一次 |
| TTL、容量与namespace | 真实状态合同验证过期/撤销、跨namespace隔离、字节/reservation/terminal上限及非法JSON |
| 自动装配及默认回归 | 未选择Redis时保持默认；显式缺模块失败；应用factory backoff、预算和过滤器顺序通过 |
| 独立制品与实际浏览器 | 新私有Maven缓存解析八库及source/Javadoc；仓库外编译消费应用后执行SSR/hydration/CSRF/forms/flash/validation/deferred |

独立消费者还验证canonical Java示例、Spring教程HTTP、移除core制品时拒绝及恢复。双JVM浏览器流量由本地代理送往不同节点，验证跨节点POST→GET；不是两个对象或一个进程模拟多实例。

## 复现与证据

命令和运行前提见[本地消费夹具](../../inertia-java/qualification/redis-cluster/README.md)。完整验收使用Java21、Boot3.5.7、Node22.22.2和锁定的React依赖；Playwright使用配套Chromium。Redis为固定官方源码摘要构建的7.2.11，本机自有loopback进程；未使用mock、共享服务或生产实例。未验证容器装配。

- [iteration-r3.json](verification/iteration-r3.json)：各阶段、源码摘要、实际候选兼容比较、独立消费、双实例与文档结果。
- [原始摘要压缩归档](verification/iteration-r3-receipts.json.gz)：实际成功记录、工具输出和live TTL原始结果；摘要身份保留原始临时路径，归档使证据不依赖临时目录继续存在。
- [早期状态原型证据](verification/iteration-r3-state.json)：保留历史范围，不改写其未完成状态；最新资格以前述记录为准。

## 支持限制与后续

当前资格是standalone明文TCP事务域，不涵盖TLS、Cluster/Sentinel、Redis failover持久性或浏览器恰好一次收到。Spring Session宿主共享与delivery状态分别配置。同步Servlet轮换/失效有hook；外部仓库删除、管理端撤销及异步宿主操作必须安排等价撤销。达到容量、旧epoch或未知写入时失败关闭，不切换本机存储。

R3本地出口已满足。公开Maven坐标、正式tag、签名和上传仍是独立发布操作；本次没有验证GitHub部署或公开站点。R4的Vue/Svelte/WebFlux按原路线图分别排期，[再次迭代路线](14-follow-up-roadmap.md)进一步拆分采用路径，不以R3结果声称这些适配器已完成。
