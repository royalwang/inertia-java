# ADR 005：Redis TLS 与有界传输

日期：2026-10-12。状态：已实施并完成 N3a 本地验收；证据见 [N3 实施记录](../19-redis-tls-capacity-implementation.md)。依据：再次迭代路线 N3a。N2 已完成，不扩大其 standalone TCP 资格为 TLS 支持。

## 决策

- 保留 RedisSessionBackend 现有三参数构造器及默认明文行为；新增接受 RedisTlsOptions 的重载。TLS 选项只表达禁用、JDK 系统信任、指定 PKCS12 信任库，不接受任意 Lettuce ClientOptions 注入。
- 使用当前 Boot 3.5.7 实际解析的 Lettuce 6.6.0.RELEASE、Spring Data Redis 与 JDK SSL provider。证书链和主机名验证由上游完成，不自建握手、信任管理器或重试；没有关闭 peer verification 的配置。
- TLS 使用 TLSv1.2/TLSv1.3；握手、连接与命令预算有界。仍禁自动重连、断连排队和未知写入重放。连接及资源由 backend/application 生命周期关闭，示例不新增连接池。
- 不改变 InertiaRedisProperties record 分量或构造器。新增独立 inertia.session.redis.tls 属性类，包含 enabled、trust-store、trust-store-password。指定信任材料但未启用 TLS 时启动失败，防止误用明文；密码和路径在 toString 中隐藏。
- 保留默认 inertiaRedisBackend 配置方法及 bean 名称；TLS 启用时由单独条件方法创建同名 backend。自定义 backend/factory 的原 backoff 行为继续验证。
- 信任库密码在核心选项中防御性复制，不公开数组或日志值。只保存公有 CA 的信任库；本轮不提供客户端私钥/mTLS、动态证书轮换、Cluster/Sentinel 或 failover 保证。

## 本地验收

固定官方源码 Redis 在自有 loopback 端口启用真实 TLS，证书由本地工具生成，密钥留在临时目录；不能以 mock 或普通 TCP 成功代替。至少证明：可信链成功；错误 CA、主机名不匹配、错误凭据拒绝；错误信任库密码/路径拒绝；默认明文回归；TLS 下已执行但丢失回复仍返回 UNKNOWN_WRITE 且写命令只提交一次；显式后续操作可重新建连。

TLS 未知写入验收需要可解密的自有测试 TLS 代理：代理按 RESP 完整帧转发到真实 Redis，在收到真实 CAS 完成回复后丢弃该回复，统计提交次数并从另一个真实连接确认值。该代理只属于测试工具，不进入运行库，不代替 Redis 后端或自行实现生产协议。

完成后更新英/中文使用指南、配置表及 Javadoc 映射，执行 API 差异与独立消费；原始资格结果留工程目录。N3b 容量及恢复说明另立记录，不以 TLS 成功代替容量证据。
