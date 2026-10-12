---
title: "Redis 容量与恢复"
description: "配置投递上限、区分故障类别，并恢复可信宿主会话身份。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionBackend.java
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionStore.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/RedisHttpSessionStoreFactory.java
verification:
  - inertia-java/inertia-session-redis/src/test/java/io/inertia/redis/RedisObservationIT.java
  - inertia-java/inertia-session-redis/src/test/java/io/inertia/redis/RedisSessionStoreIT.java
  - inertia-java/inertia-session-redis/src/test/java/io/inertia/redis/RedisTlsIT.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/RedisHostLifecycleIT.java
translation:
  locale: zh-CN
  canonicalId: deployment/redis-operations
  source: deployment/redis-operations.md
  sourceRevision: 919a56e19a547e7d385ca8495df37f69bf9586eb414e59baea10b45eab272430
---

# Redis 容量与恢复

可选投递后端支持单个 standalone Redis 事务域。为专用连接配置 [TLS 与凭据](../reference/configuration.md#redis-tls)，宿主 Spring Session 存储单独配置。Boot 在应用关闭时释放后端；手工装配时应由应用共享一个后端，并在应用关闭时释放一次。

## 联合配置各项上限

| 上限 | 配置依据 |
| --- | --- |
| `max-commands` | 限制同时进行的传输操作，不等于 HTTP 并发数。一次 Page 会进行多次读取/CAS。提高请求并发前先检查 `OVERLOADED`。 |
| `command-timeout` | 约束连接、TLS 握手及每条命令。增大超时可能让命令槽位占用更久。 |
| `lease` | 必须长于响应超时加命令超时，同时短于 idle TTL；需覆盖实际请求工作量。 |
| `max-reservations` | 限制**每个域**的重叠投递。即使命令槽位可用，热点会话也可能达到此上限。 |
| `terminal-retention` / `max-terminals` | 为完成、中止及 lease 恢复产生的终态 token 预留容量，并覆盖突发。 |
| `max-bytes` | 包含 available 值、预留快照和终态记录。增加条目容量也需要字节容量。 |
| `idle-ttl` | 覆盖宿主会话的实际使用方式；成功 CAS 会刷新它。过期会隔离旧 epoch，不会创建空状态。 |
| `max-attempts` | 约束明确未写入冲突后的重算次数；独立的一秒 dispatch 预算仍然生效。 |

例如，每秒产生 100 条终态记录、保留五分钟，在未考虑突发前就需要约 30,000 条记录；默认 4,096 对该域不足。还需检查序列化 envelope 的字节预算。终态保留用于隔离旧 token，不能为了让压力测试通过而直接删除这些记录。

配置 16 个命令槽位和 16 个 reservation 的示例，在 32 个并发请求下可能拒绝流量：不同会话争用命令槽位，单个热点会话则可能达到 reservation 上限。仅增加 Servlet 线程不会增加存储上限。单 JVM 内，原生宿主会话 mutex 会串行化同会话操作；多个 JVM 之间还可能出现 CAS 冲突。

应测量实际 TLS、payload、宿主会话和节点拓扑。把成功请求的延迟/吞吐与快速拒绝率分开，检查 HTTP/服务端 p95/p99、命令耗时、有界失败原因、连接数、Redis 内存、驱逐、域占用和 TTL。采样内存或占用不是精确峰值，短期合成负载不能提供生产容量保证。

## 先定位，再调整流量

使用 [Redis 指标](../reference/metrics.md#redis-投递诊断)。`REDIS_STATE_FAILURE` 以零时长样本统计状态拒绝次数，命令 timer 属于不同范围。诊断标签不应包含 key、身份、token 或业务值。

使用可信运维账户，通过密钥机制将密码注入 `REDISCLI_AUTH`，然后检查实际 TLS 端点：

```sh
redis-cli --tls --cacert /run/secrets/redis-ca.pem \
  --user "$REDIS_USER" -h "$REDIS_HOST" -p "$REDIS_PORT" PING
redis-cli --tls --cacert /run/secrets/redis-ca.pem \
  --user "$REDIS_USER" -h "$REDIS_HOST" -p "$REDIS_PORT" INFO memory
```

保留相同连接选项，执行 `INFO stats` 和 `INFO clients`。检查 `used_memory`、`used_memory_peak`、`evicted_keys`、`expired_keys`、`connected_clients` 与 `total_connections_received`。投递客户端为命令创建专用连接；即使 Redis 内存较低，连接更替也可能成为主要 TLS 成本。不能通过关闭身份校验或启用重放来降低此成本。信任配置变化后，重建后端或重启应用，再验证真实 Page 和一次性投递。

## 按故障类别恢复

| 故障 | 恢复方式 |
| --- | --- |
| `READ_FAILED` | 修复端点/认证/信任配置，验证新的 Page 读取。此分类描述 Redis 工作，不证明应用业务工作从未执行。 |
| `UNKNOWN_WRITE` | 停止自动重放。通过业务事务或幂等标识核对应用业务状态，将投递结果视为不确定。 |
| `CAPACITY` | 区分命令 `OVERLOADED` 与状态 `REDIS_CAPACITY`。降低准入，等待活动工作撤载，再验证受影响会话的新请求。 |
| `CONTENTION` | 降低热点域的并发写入，检查 CAS 冲突次数。后续显式操作有新预算，但不能重放结果未知的业务请求。 |
| `BUDGET_EXHAUSTED` | 检查连接/握手时长、冲突次数及慢 observer。仅增大 `max-attempts` 不能修复已耗尽的 dispatch 预算。 |
| `INVALID_TOKEN` | 拒绝晚到或重用的 token，使用新 Page。不能把旧 complete/abort 强加给其他 reservation。 |
| `STALE_DOMAIN` | 通过可信宿主退出/新会话路径恢复。旧句柄保持隔离，不能删除 attachment 元数据或复用旧 epoch 来制造成功。 |
| `INVALID_STATE` / `CLOCK_REVERSED` | 停止受影响投递，修复不兼容状态或服务器时间，再协调可信身份。不能静默重置状态。 |

投递中断后，后续操作或已知存活域上的 `recoverExpired()` 可原子恢复过期 lease。Redis TTL 本身不能执行恢复预留效果所需的 Java 合并。恢复 lease 会使旧 token 失效，晚到的 complete/abort 仍应拒绝。达到 terminal 上限后，可能需要等待保留期结束，状态转换才能再次成功。

Redis 重启且投递状态丢失时，仍存活的宿主元数据已过期。先恢复 Redis。应用生命周期路径可在失效/轮换该可信宿主身份前，调用 `RedisHttpSessionStoreFactory.beforeHostChange(trustedSession)`；明确读取到缺失/已撤销 epoch 后，可以协调撤销。如果撤销仍失败，保持宿主操作失败及待协调元数据，修复存储，再显式执行生命周期协调。随后创建新宿主身份，按应用策略重新认证。不能重建旧域或复制其 reservation。外部宿主 repository 删除也需要等效应用 hook。

恢复结束前，通过配置的适配器执行真实请求：验证认证、HTML/JSON、一次性 flash/errors，以及后续访问不重放。仅 Redis `PING` 成功不证明会话投递可用。持久化、异步复制、Cluster/Sentinel 和 failover 持久性需要各自的部署验收。
