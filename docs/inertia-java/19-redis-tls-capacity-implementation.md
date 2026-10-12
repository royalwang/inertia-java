# N3 Redis TLS、容量与恢复

日期：2026-10-12。状态：N3 实施中。N2 已完成本地验收；维护者确认 N4 按需保留、不纳入本轮。依据：[再次迭代路线](14-follow-up-roadmap.md)。本文件属于工程记录，不进入公共文档正文。

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

## 当前验收边界

当前归档见 [N3 进展摘要](verification/iteration-n3-progress.json)：标准 reactor 312 项、明文 Redis 14 项、TLS 10 项、自动装配 10 项、八模块 API 源码/二进制检查、中英文文档检查/构建及本地浏览器 14 阶段均通过。最终统一命令包含全部 24 项 Redis 合同与 10 个独立 Maven 消费阶段；仓库外 Boot 应用通过 TLS 完成真实 HTTP 交付，并断言 factory 确为 Redis。八个消费 JAR 的 SHA-256 与 API 候选一致，变更运行库的源码与 sources.jar 逐文件匹配。原始摘要和日志以 gzip 收据保存；此前九项/单独补充阶段保留为历史快照，不替代最终聚合结果。

N3a 已完成本地验收。N3b 尚未交付重复测量和恢复手册，N3 整体未完成。公共 Maven 发布、GitHub Actions 和线上站点验证均不作为本地实施阻塞项。
