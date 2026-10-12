# ADR 006：Redis 容量观测与测量边界

日期：2026-10-12。状态：接受，已实施并完成本地验收。依据：路线 N3b 与 [N3 实施记录](../19-redis-tls-capacity-implementation.md)。

## 观测缺口

现有 SESSION_BEGIN/COMPLETE/ABORT/MERGE 是应用交付操作；它们无法区分内部 READ、CAS 冲突与命令并发拒绝，最终 HTTP 失败也不是 CAS 冲突次数。状态上限与预算拒绝也不会通过命令传输事件自动表达，因此在原状态拒绝出口发布一次独立状态失败事件；它不提供状态操作时长或额外重试。连接数量、Redis 内存及 TTL 则由自有 Redis 的 INFO/聚合状态采样，不能伪装成应用 observer 的指标。

## 决策

- 增加 REDIS_READ、REDIS_CAS 两个命令级 operation，以及零时长、只计数的 REDIS_STATE_FAILURE 状态拒绝事件，复用 InertiaObserver、Observations.publish 与 MicrometerInertiaObserver。新增有界 REDIS_UNKNOWN_WRITE 和七个状态拒绝 reason（REDIS_CAPACITY、REDIS_CONTENTION、REDIS_BUDGET_EXHAUSTED、REDIS_STALE_DOMAIN、REDIS_INVALID_TOKEN、REDIS_INVALID_STATE、REDIS_CLOCK_REVERSED），避免把执行结果未知误记为普通可重试失败。
- READ 成功为 SUCCESS/NONE；连接/读取失败为 FAILURE/TRANSPORT；CAS 成功写入为 SUCCESS/NONE，显式不匹配为 CONFLICT/NONE，过期读取期限为 CONFLICT/TIMEOUT，时钟反向为 FAILURE/ERROR。任何提交结果不明仍抛原 UNKNOWN_WRITE，并记 FAILURE/REDIS_UNKNOWN_WRITE。命令并发拒绝为 FAILURE/OVERLOADED，不提交命令。
- 增加 backend 的显式 observer 构造重载；现有三/四参数入口保留 NOOP。Boot 自动装配使用同一应用 observer，保持现有配置方法与无参构造器可调用。资源、TLS、超时、自动重连关闭、断连拒绝及不重放约束不变。
- 事件只有固定 operation/outcome/reason、耗时、空 request/component 与固定 redis endpoint；无 key、URL、用户、身份、凭据、值或异常文本。observer RuntimeException 隔离，fatal Error 沿用既有策略。发布在命令 permit 释放后，不额外持有并发额度。
- HTTP 负载沿用 R2 benchmark 驱动；新增 Redis profile，以固定 Cookie 的热点域和每个 worker 独立 Cookie 的不同域对照，避免把不保留 Cookie 的大量新域创建成本当作稳定会话开销。测量与预热分开，记录完整 HTTP 延迟、服务端事件耗时、失败分类及恢复探针。
- 使用自有固定版本 Redis、临时端口、候选示例 JAR。采样客户端只读 INFO、已知前缀域的聚合 reservation/terminal/TTL，不记录原始 key 或 JSON。每组相同负载至少三次；原始资料留工程目录。
- 不通过压力结果预设连接复用、重放、缓存或状态丢弃优化。先报告连接成本、冲突、GC/JIT 与波动，超过原 10% 阈值调查。热点域可出现有界 CONTENTION/CAPACITY，不把吞吐数字单独解释为容量保证。

## 验收

真实 Redis 验证事件计数、CAS 冲突/期限、读失败、容量拒绝、已执行回复丢失及 observer 异常隔离；已有状态/TLS/宿主生命周期不能回归。保留 API 构造器/record，执行源码/二进制差异、自动装配、独立消费与严格 Javadoc。三次相同 HTTP 负载测量必须有直接 Redis/JVM 资源、连接及恢复证据，另验状态上限、TTL/lease、预算和重启恢复。公开双语指标与恢复文档与实际代码一致。

实际结果与限制见 [N3 最终资格记录](../verification/iteration-n3.json) 和 [容量与恢复实施](../19-redis-tls-capacity-implementation.md)。三轮波动与 TLS 连接成本已记录；保持当前专用连接和不重放约束，连接池作为后续独立设计事项。
