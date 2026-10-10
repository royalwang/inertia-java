# 下一轮迭代实施台账

日期：2026-10-10。对应[路线图](11-next-iteration-roadmap.md)。整体状态：R1–R3及R4首个Vue示例完成本地交付；Svelte/WebFlux实现按原路线条件另行排期。

## R1：候选与独立消费

- 候选政策见[ADR 001](decisions/001-candidate-distribution.md)：保留本地 `io.inertia` 坐标，公共namespace待发布前确认；保持Java包和SNAPSHOT，不凭空创建正式版本。
- parent POM项目/SCM四项指向canonical `royalwang/inertia-java`，制品检查同步保护该元数据及子模块parent身份。
- README纠正私密报告渠道及文档状态，区分本地可验收和公开发布。
- Boot默认配置增加真实自动装配回归：props 3秒、response 5秒、并发8、pool 8/32/256、allErrors未覆盖、namespace default。
- 公共API生命周期审查见[ADR 002](decisions/002-public-api-baseline.md)，冻结71个公开/受保护类型签名并保留所有原接口。通过真实japicmp 0.26.2检查本地七模块候选及完整依赖，差异工具不忽略缺失类。
- 292项Java测试通过，0失败/错误/跳过；六项制品拒绝合同通过。独立Maven consumer完成二进制/source/Javadoc消费、canonical Java示例、缺失core拒绝与恢复；独立教程实际SSR/JSON/hydration/validation/flash/导航/Node停机CSR通过。
- 文档check通过，英文/中文正文修订未变；没有宣称本轮重新验证公开站点。
- R1本地交付完成，机器证据见[iteration-r1.json](verification/iteration-r1.json)。公开namespace未验证，实际发布留待独立流程。

## R2：兼容性、诊断与容量

- japicmp 0.26.2 的真实源码/二进制兼容合同及七模块比较已进入完整aggregate。本轮比较R1保存候选与R2新候选，全部通过；不再以同一候选自比较代替历史对照。
- 新增四项真实resolver/renderer/Micrometer诊断合同，覆盖props超时、executor拒绝、业务provider错误及session merge失败。验证错误分类、flash恢复、恢复后继续服务及标签隐私；未新增另一套SPI。
- 中英文观测指南同步加入reason排错表与PromQL示例，明确stage计数和timer均值边界，不把未配置的histogram写成p95。
- 新增默认关闭的示例负载夹具；性能驱动扩展到HTML/JSON、小/大props、partial/deferred及同session并发，采集实际JVM堆和线程队列。
- 同机交替对照三次，另对CSR并发32进行128次预热、每组1024请求的三次复测。审计总计21,264个测量请求；原始成功摘要压缩归档随仓库保存。初测差异和复测解释见[运行资格记录](13-runtime-qualification.md)，不宣称性能提升或生产SLO。
- 实际过载验收：2线程/2排队/128MiB堆、32个并发请求，4成功/28明确拒绝；采样队列上限2，负载结束后200且队列/活动线程归零。aggregate再次执行通过。
- 完整本地aggregate 22阶段通过；297项Java测试0失败/错误/跳过。Rust freshness为37 Page/45 HTTP/9 live TTL；文档check/build及140页/14阶段浏览器验收通过，包括Mermaid。
- R2本地交付完成，机器证据见[iteration-r2.json](verification/iteration-r2.json)。工程性能记录不进入公共使用正文。

## R3：Redis投递状态

[ADR 003](decisions/003-redis-delivery-state.md)已完成standalone多实例本地出口。以下先保留状态原型阶段记录，再列最终收口：

- 新增可选 `inertia-session-redis`，core无Spring/Redis依赖。Spring Data Redis 3.5.5 / Lettuce 6.6.0.RELEASE沿用Boot BOM；原子opaque JSON CAS、revision/epoch、Redis server TIME、lease恢复及晚到token拒绝、撤销tombstone、有界终态/字节/冲突/dispatch预算均已实现。
- 原有MemorySessionStore合并语义直接复用，真实共同合同覆盖Memory/Servlet/Redis；空数组、大整数、精确decimal、error bags、新写入优先级及失败不半写入通过。
- 新增MVC `InertiaSessionStoreFactory`，Boot允许应用bean替换，原构造器/API入口保留。实际合同验证每请求一次、版本冲突不创建store、默认行为和替换bean；中英文工厂使用说明同步。
- 传输禁止自动重连重放和断连排队，使用Spring脚本缓存及每命令独立连接。原始TCP代理使真实CAS执行后丢回复，验证UNKNOWN_WRITE且只提交一次；不是mock未知结果测试。
- 固定Redis 7.2.11官方源码SHA，本地编译loopback进程。`scripts/verify-redis.py`的现有可执行文件路径与从源码构建路径都实际通过14项测试，0失败/跳过。标准reactor install通过300项测试；七旧模块源码/二进制兼容通过，新模块登记为新增，禁止移除旧模块的工具负例通过；24个binary/source/Javadoc档案通过检查。
- 140页docs check及72个已支持公开类型地图通过；Redis原型API尚未加入公共站点支持声明。未重新执行全套browser aggregate，不借用R2证据声称新分布式HTTP行为合格。
- 原型阶段机器证据见[iteration-r3-state.json](verification/iteration-r3-state.json)，保持其历史未完成范围；模块边界和复现见[模块说明](../../inertia-java/inertia-session-redis/README.md)。

- 最终收口新增expected epoch持久化、revoking协调、原生listener和Spring Session过滤器，显式Redis自动装配及应用factory backoff，默认Servlet行为保持。
- 305项标准Java测试、14项真实Redis测试、3项宿主IT通过；options用例重复计入标准套件。完整aggregate25阶段通过，六项制品拒绝合同另行通过。
- 隔离Maven消费解析八库及source/Javadoc，在仓库外编译独立应用，再完成双JVM六阶段HTTP/浏览器/故障验证；不借用reactor编译JAR替代独立消费。
- Rust freshness、140页文档check/build/browser通过；Javadoc地图为八模块、81公开类型、570成员锚点。详细要求审查、支持限制及原始证据见[多实例资格记录](15-multi-instance-qualification.md)和[iteration-r3.json](verification/iteration-r3.json)。

## R4：Vue示例与条件性适配设计

- 新增独立spring-vue业务应用与Vue/Vite/Node入口，复用公共Java库；React/Vue共享构建摘要与资源发布工具，未复制Java协议。
- 锁定Inertia3.8.0、Vue/server-renderer3.5.43、Vite8.3.3、TypeScript5.9.3；Java21/Boot3.5.7不变。独立Maven消费后执行实际SSR/hydration、navigation、forms/CSRF/flash、partial/optional/deferred、scroll/once及SSR停机恢复；Node registry/version/tamper拒绝通过。
- 完整aggregate27阶段、305标准Java测试、八库兼容/24制品与142页双语文档14阶段浏览器通过。Vue七阶段和独立依赖清单见[资格记录](16-vue-qualification.md)。
- [ADR004](decisions/004-webflux-adapter-boundary.md)完成WebFlux返回值、取消、WebSession、Security、错误及提交设计；Svelte单列工作包。两个适配器实现仍以实际采用需求和独立预算为启动条件，没有宣称支持。
- 原路线要求逐项判断见[完成审查](17-roadmap-completion-audit.md)。

## 后续范围

Vue生产构建路径已合格；Svelte/WebFlux实现仍依路线图采用需求单独排期。后续工作包见[再次迭代路线](14-follow-up-roadmap.md)。公共发布与GitHub部署不作为本地功能交付阻塞项。
