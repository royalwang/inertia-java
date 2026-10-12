# N3 Redis TLS、容量与恢复

日期：2026-10-12。状态：N3a/N3b 本地验收完成。N2 已完成本地验收；维护者确认 N4 按需保留、不纳入本轮。依据：[再次迭代路线](14-follow-up-roadmap.md)。本文件属于工程记录，不进入公共文档正文。

## N3a 已实施的连接边界

决策见 [ADR 005](decisions/005-redis-tls-transport.md)。保留原三参数后端构造器和默认明文行为，新增 `RedisTlsOptions` 与四参数构造器；仅允许明文、JDK 系统信任、可读本地 PKCS12 信任库。使用实际依赖 Lettuce 6.6.0.RELEASE、Spring Data Redis 与 JDK TLS；不开放关闭证书链/主机名校验或任意客户端配置的入口。

新增独立 `InertiaRedisTlsProperties`，不改动已有属性 record 的构造器。TLS 与明文装配保留同一 bean 名，并继续对应用 backend/factory 退让。指定信任材料却未启用 TLS、密码缺少信任库路径、信任库不可读均明确失败。宿主 Spring Session 的连接配置仍由应用单独负责。

自动重连关闭、断连拒绝、并发命令上限及未知写入不重放的约束保持不变。信任密码数组防御性复制，配置摘要隐藏密码及路径；本轮不提供客户端证书、动态信任重载、Cluster/Sentinel 或 failover 持久性保证。

## 真实 TLS 验证方法

固定 Redis 7.2.11 官方源码包 SHA-256：`2f9886eca68d30114ad6a01da65631f8007d802fd3e6c9fac711251e6390323d`。本机使用 `BUILD_TLS=yes MALLOC=libc` 与 Homebrew OpenSSL 3.6.3 构建，不以容器或模拟后端资格描述它。

`RedisTlsIT` 创建自有 loopback TLS Redis、临时 CA、仅含 DNS localhost 的服务器证书、PKCS12 信任库和随机 Redis 密码。密钥及配置只留临时目录。实际用例覆盖可信链交付、错误 CA、系统信任拒绝私有 CA、独立 JVM 使用显式 JDK 信任配置成功、IP/主机名不匹配、错误 Redis 凭据、错误信任库密码、缺失文件、明文连接拒绝，以及已执行 CAS 的回复丢失。

回复丢失夹具是测试专用 TLS 代理：完整转发命令，先收到真实 Redis 的 CAS 成功回复，再丢弃回复。从独立连接确认变更已落入 Redis，客户端必须得到 `UNKNOWN_WRITE`，代理确认变更仅提交一次；之后允许显式新操作建连。不把普通断连或仅观察异常视为已执行写入的证据。

从仓库根目录复现（先按本机实际路径设置 Java 21 与 OpenSSL）：

```sh
python3 inertia-java/scripts/verify-redis.py --tls --tls-consumer \
  --redis-server /absolute/path/to/tls-enabled/redis-server \
  --output /tmp/inertia-redis-tls-new-output
```

也可使用 `--build-runtime --tls` 下载、校验并构建固定源码；macOS 的 OpenSSL 不在默认编译路径时增加 `--openssl-prefix /opt/homebrew/opt/openssl@3`。`--tls-consumer` 会在 TLS 合同通过后，用同一证书夹具重启自有 Redis，使用 reactor 外解析的制品，通过 Boot 属性完成 HTML、JSON、redirect、flash/session、异常与 required SSR 503 的 HTTP 链路，并确认 factory 确为 Redis。可设置 `INERTIA_CONSUMER_DEPENDENCY_CACHE` 复制第三方依赖缓存；工具始终排除 `io.inertia`，候选库必须从隔离仓库解析。输出目录必须为空。证书工具或 TLS 支持缺失会失败，不跳过或用明文替代。聚合驱动可通过 `INERTIA_REDIS_TLS_SERVER` 启用同一检查。

## N3b 实施设计

沿用 R2 HTTP 负载驱动及 opt-in `DemoBenchmark`，测量入口继续由真实 MVC 调用 factory/session store，不增加生产 benchmark 路由。普通不同 session 与热点同 session 分开测量；对照固定 JVM、Redis、前端 build identity、驱动、负载和 TLS 选择，同参数至少三次，保存每次原始结果与波动范围。

测量需要包括：完整 HTTP 延迟 p95/p99、吞吐、状态/有界失败原因、命令并发上限、实际新建连接数量、CAS 冲突、reservation/terminal 数及拒绝、TTL，以及 JVM heap/RSS 和 Redis used/peak memory。服务端 observer 现有覆盖需先审查；若 CAS 冲突等不在 observer 中，不可用最终 HTTP 错误数冒充内部次数。诊断只能输出聚合数字，不记录 key、身份或业务值。

恢复验收要分别证明：命令容量达到上限、同域 reservation/terminal 上限、显式 TTL/lease 过期、读连接失败、已执行回复丢失、Redis 重启数据丢失、预算耗尽，以及撤载/恢复后新请求可正常完成。状态上限不得通过静默丢弃数据绕过，未知写入不得自动重放。仅做同域锁压力或单次微基准不能满足这些要求。

公开英/中文文档提供可执行的配置、识别与处置步骤：先区分 `READ_FAILED`、`UNKNOWN_WRITE`、`CAPACITY`、`CONTENTION`、`BUDGET_EXHAUSTED` 和 `STALE_DOMAIN`，再选择修复连接/信任配置、等待租约、应用撤销旧域、人工业务核对或调整容量。吞吐建议应附实际环境及数据，不给生产 SLO。超过原 10% 调查阈值先分析连接、冲突、JIT/GC 和主机波动，不预设连接池或缓存重写。

## N3a 历史验收边界

当前归档见 [N3 进展摘要](verification/iteration-n3-progress.json)：标准 reactor 312 项、明文 Redis 14 项、TLS 10 项、自动装配 10 项、八模块 API 源码/二进制检查、中英文文档检查/构建及本地浏览器 14 阶段均通过。最终统一命令包含全部 24 项 Redis 合同与 10 个独立 Maven 消费阶段；仓库外 Boot 应用通过 TLS 完成真实 HTTP 交付，并断言 factory 确为 Redis。八个消费 JAR 的 SHA-256 与 API 候选一致，变更运行库的源码与 sources.jar 逐文件匹配。原始摘要和日志以 gzip 收据保存；此前九项/单独补充阶段保留为历史快照，不替代最终聚合结果。

该快照记录 N3a 完成时的范围；N3b 当前结果在下节及最终资格记录中独立归档。公共 Maven 发布、GitHub Actions 和线上站点验证均不作为本地实施阻塞项。

## N3b 实际容量结果

Apple M1 Pro / 8 logical CPU / 16 GiB、macOS Darwin 25.6.0、OpenJDK 21.0.1、Node 22.22.2、固定 Redis 7.2.11；JVM、Redis 与驱动同机。使用候选 React JAR、原生 HttpSession；仅为隔离投递成本关闭 Spring Session 自动装配。N2 的双 JVM 认证与宿主复制资格单独验证。本组不包含浏览器、SSR、数据库或跨 JVM 热点吞吐。

每种 TCP/TLS、不同 worker session/同 session、并发 1/8/32 组合各三次，每次 128 个测量请求，预热排除，共 4,608 次。完整 HTTP body、服务端过滤器时长、内部 READ/CAS、连接计数、状态上限、TTL、JVM heap/RSS 和 Redis memory 分开记录。采样周期 200ms，不能当作精确峰值。下表延迟与吞吐只计 HTTP 200；拒绝数为三个独立轮次，完整 p99、波动、状态及资源见 [容量原始收据](verification/iteration-n3b-capacity/manifest.json)。

| 传输 | session | 并发 | 成功 p95 中位数 [范围] ms | 成功 req/s 中位数 [范围] | 每轮拒绝数 |
| --- | --- | --- | --- | --- | --- |
| TCP | 不同 | 1 | 15.6 [14.8, 17.6] | 89.5 [81.4, 90.6] | 0, 0, 0 |
| TCP | 不同 | 8 | 28.7 [22.4, 30.9] | 407.5 [386.7, 521.7] | 0, 0, 0 |
| TCP | 不同 | 32 | 103.3 [85.0, 112.8] | 290.5 [233.3, 348.2] | 66, 84, 99 |
| TCP | 热点 | 1 | 9.3 [9.1, 11.6] | 141.9 [131.2, 150.8] | 0, 0, 0 |
| TCP | 热点 | 8 | 65.3 [56.9, 253.4] | 209.9 [162.4, 241.4] | 0, 0, 0 |
| TCP | 热点 | 32 | 138.5 [127.1, 149.1] | 247.5 [235.4, 298.9] | 21, 22, 28 |
| TLS | 不同 | 1 | 63.4 [51.2, 89.1] | 22.9 [20.8, 24.4] | 0, 0, 0 |
| TLS | 不同 | 8 | 113.4 [104.4, 150.1] | 90.1 [75.2, 98.2] | 0, 0, 0 |
| TLS | 不同 | 32 | 245.9 [242.3, 295.6] | 52.7 [50.8, 54.0] | 112, 112, 110 |
| TLS | 热点 | 1 | 46.4 [40.2, 53.4] | 31.6 [25.9, 32.4] | 0, 0, 0 |
| TLS | 热点 | 8 | 370.4 [360.6, 1088.8] | 37.3 [18.5, 38.7] | 0, 0, 0 |
| TLS | 热点 | 32 | 1279.3 [1099.9, 2360.7] | 29.5 [22.6, 30.1] | 16, 29, 23 |

配置固定 maxCommands=16、maxReservations=16、maxTerminals=4096、commandTimeout=1s、lease=10s、terminalRetention=30s、idleTTL=2m、maxAttempts=16、Redis 64 MiB/noeviction。并发 1/8 全部成功；不同域并发 32 拒绝来自命令 permit，热点并发 32 来自 reservation 上限，实际采样达到 16。所有 load CAS 冲突为零，因为单 JVM 原生宿主锁串行化域操作；另用实际 Redis 并发变更合同证明 CAS=0 与 CONTENTION。不能把零冲突推广至双 JVM。

每轮顺序基线 128 成功请求对应 640 个投递连接，即每请求 3 READ + 2 CAS。连接数从专用 Redis total_connections_received 差分扣除已知采样 CLI 连接；不存在连接池复用。TLS 的每命令建连/握手与热点锁等待是超过 10% 调查阈值的直接候选因素；同时三轮 p95 波动明显、固定 TCP 后 TLS 顺序、主机初始负载较高，未收集 GC/JIT 专项轨迹，不能把差异全归因 TLS 或称为正式回归结论。此轮保持 UNKNOWN_WRITE 不重放及命令有界，不实施未经独立设计的连接池改写。

采样 Redis memory 最大 1,967,616 bytes、JVM heap 最大 219,828,048 bytes、JVM RSS 最大 471,580,672 bytes。单域 terminal 最大 137，未接近 4096；terminal 满额与保留期释放由真实状态合同另证，不能用该短测证明长期容量或无泄漏。所有 36 个阶段撤载后使用原始 seed session 的探针返回 200；未逐个探测全部 worker session。保留期、lease、TTL、数据丢失及网络恢复均通过独立故障合同覆盖。

## 可复制容量命令

先运行前文 TLS 合同、构建 `-Predis-session` 示例与当前前端；TLS fixture 是其 maven.log 中 `TLS qualification fixture` 指向的临时目录，含本地测试证书。设置 Java 21 后执行：

```sh
python3 inertia-java/scripts/verify-redis-capacity.py \
  --redis-server /absolute/path/to/tls-enabled/redis-server \
  --redis-cli /absolute/path/to/tls-enabled/redis-cli \
  --tls-fixture /absolute/path/to/generated-test-fixture \
  --output /tmp/inertia-redis-capacity-new-output
```

驱动沿用 R2 benchmark，每组至少三次，输出原始 JSON、服务日志与严格聚合；失败或身份不一致立即失败。缺少运行时/证书不会跳过，不自动安装。输出目录需为空；临时凭据、私钥、原始进程日志不入仓库。也可在现有 `node inertia-java/scripts/verify.mjs` 聚合命令中设置 `INERTIA_VERIFY_REDIS_CAPACITY=true`、`INERTIA_REDIS_TLS_SERVER` 与 `INERTIA_REDIS_TLS_CLI`，驱动会在其它检查结束后构建可选 Redis 示例并按序执行容量测量；缺少参数立即失败。新增入口通过参数/语法检查；本轮六次真实负载由同一 benchmark/helper 与同参数顺序执行，未声称重新经新增封装入口执行全部负载。篡改 driver 身份的收据已被汇总拒绝。

## 观测与恢复交付

新增 REDIS_READ/REDIS_CAS 计时与 REDIS_STATE_FAILURE 零时长计数；所有 reason 固定枚举，不记录 key、业务值、用户、Cookie 或凭据。命令 permit 释放后才发布 observer，RuntimeException 隔离。真实测试覆盖异常 observer、不额外占 permit、命令拒绝、reservation 满额、失效 token、实际 CAS 冲突、Redis CLIENT PAUSE 导致预算耗尽以及已执行 CAS 回复丢失。不新增自动重放。Boot 使用应用自定义 observer 或可选 Micrometer observer。

双语公开 [Redis 运维手册](../../inertia-java/docs/deployment/redis-operations.md) 提供关联容量配置、只读排查、UNKNOWN_WRITE 核对、lease/terminal 释放、旧 epoch 撤销与重新认证。原始测量仅保留在工程目录。

## 验收过程中的失败记录

第一次并行本地检查中，独立 TLS 消费首次 HTML 返回 500，类型为 RedisSessionException，旧夹具未记录有界 reason，因此无法追溯确认具体原因；不把它归因于已证明的 TLS 缺陷，也不抹去该失败。补充选定 observer 的 READ/CAS/reason 诊断与实际 CAS 收到断言后，独立消费完整十阶段通过，且没有加入请求重试、放宽生产预算或关闭 TLS 校验。冷 TLS/JIT 和主机竞争可能消耗固定一秒状态预算，仅列为解释候选。

追加回归时本机出现 `No space left on device`，中断独立缓存复制与认证示例 Maven 构建；保留失败摘要。仅清理本任务先前生成的临时第三方 Maven 缓存，保留收据、代码与归档，补跑受影响检查。该中断不记为通过，也不解释为产品故障。

## 最终本地验收

最终 [N3 资格记录](verification/iteration-n3.json) 单独保存当前源码、候选、测量 JAR、归档和实际结果，不改写 N3a 历史快照。标准 reactor 314 项、Redis 相关合同 31 项（含配置边界和共享实现，不能与标准测试简单相加）、宿主生命周期 3 项、Redis 自动装配 12 项通过；严格 Javadoc、Spotless、24 个源码/二进制/Javadoc 档案与八模块 API 兼容通过。实际消费八库与候选字节一致，测量示例 JAR 及七个嵌套运行库与最终候选一致。

统一 `verify-redis.py --tls --tls-consumer` 在显式启用真实 Redis/认证消费后完整成功：TLS 独立 Boot、跨 JVM 六阶段以及 React/Vue 各 16 个认证阶段。补跑未增加业务重试或放宽预算，磁盘中断记录保留。72 英文/72 中文文档检查、构建与本地浏览器 14 阶段通过；原 R2 executor 饱和与撤载恢复再次通过（4 成功/28 拒绝，随后 200）。容量六轮 4,608 次、36 次恢复探针及波动范围见原始收据。

N2/N3 本轮范围完成。N4 按维护者选择暂缓；公共 Maven 发布、正式 tag/签名及线上部署均为独立事项。本轮不新增支持环境、Cluster/Sentinel 或 failover 持久性保证。
