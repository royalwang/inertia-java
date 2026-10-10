---
title: "自定义会话存储"
description: "选择可选的 standalone Redis 投递存储，或实现请求专属的原子存储适配器。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/SessionStore.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/MemorySessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaSessionStoreFactory.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaContext.java
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionBackend.java
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionStore.java
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionOptions.java
  - inertia-java/inertia-session-redis/src/main/java/io/inertia/redis/RedisSessionException.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaRedisAutoConfiguration.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaRedisProperties.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/RedisHttpSessionStoreFactory.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/RedisSessionLifecycleFilter.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionContractTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/SessionFailureTest.java
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/HttpSessionStoreTest.java
translation:
  locale: zh-CN
  canonicalId: integrations/custom-session
  source: integrations/custom-session.md
  sourceRevision: c4b336a29848b575a6cc98cb8ce0563a65197970ac18eda25317c903237f1dc3
---

# 自定义会话存储

Servlet 本地或内存存储不满足部署需求时，为单个用户存储域实现 `SessionStore`。分布式实现必须在并发与存储失败下保留事务契约，仅包装 key/value 不够。

## SPI 不变量

| 操作 | 必须满足的行为 |
| --- | --- |
| `get` / `put` / `pull` | 宿主定义的安全隔离域内 key/value 访问 |
| `beginPageDelivery` | 在非 null 交付 token 下原子预留快照 |
| `completePageDelivery` | 至多消费 token 一次 |
| `abortPageDelivery` | 恢复预留交付，至多消费 token 一次 |
| `merge` | 原子合并待提交效果，或报告失败 |

`Delivery` 在构造和访问时复制 JSON 快照。实现需保留隔离，不能暴露共享可变状态。begin/merge 失败必须原子；结果未知需要报告，不能视为成功的空交付。

context 不会盲目重试未知存储结果。完成失败后，它尝试一次契约规定的恢复路径。按真实存储原语设计 token 幂等和失败观测；部署范围包含进程退出或网络不确定性时，也应覆盖这些条件。

## 隔离身份与 namespace

不要把所有用户连接到全局 `MemorySessionStore`。Servlet 实现使用带 namespace 的状态域和协调 session mutex。MVC、advice 与安全重定向 handler 的 namespace 必须一致。

身份轮换/失效遵循宿主策略。类型化重定向 advice 保留原 store，不替失效会话创建新存储。无会话 Page 有效，但没有 store 就不能提交跨重定向 flash/errors。

## 验证实现

以现有 memory/Servlet 实现和会话契约/失败测试为参考。对真实后端增加重叠预留、complete/abort 竞争、新合并效果、token 重用、已脱离域和未知失败测试。单节点成功不能证明集群故障切换或浏览器恰好一次交付。

可以使用下述可选 standalone Redis 适配器，或针对部署范围验证其他后端。传输收尾顺序见[自定义适配器](custom-adapter.md)。

## 选择请求专属存储

Spring Boot 接受应用提供的 `InertiaSessionStoreFactory` bean。MVC 适配器对每个参与请求只调用一次 `create`，并在重定向或 outcome advice 中继续使用同一个存储句柄。版本冲突响应会在创建存储之前结束；错误 Page 使用无会话 context。现有 configurer 构造器保留 `HttpSessionStore` 默认行为；普通 MVC 可以使用接收工厂的构造器。

工厂必须从可信宿主会话状态取得身份，并返回非 null 句柄。后端连接由应用持有，在应用关闭时释放。应用必须为所选后端安排会话失效、身份轮换及分布式 fencing；仅替换工厂不会自动获得这些保证。不能为所有用户提供同一个全局存储，也不能直接使用请求头或参数作为存储 key。

## 可选的 standalone Redis 投递存储

添加与 starter 同版本的 `inertia-session-redis`，再显式选择。默认仍为 Servlet 本地存储。选择 Redis 却没有对应模块时，启动会失败，不会静默回退。

```xml
<dependency>
  <groupId>io.inertia</groupId>
  <artifactId>inertia-session-redis</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
inertia:
  session-namespace: my-application
  session:
    store: redis
    redis:
      host: 127.0.0.1
      port: 6379
      command-timeout: 200ms
      idle-ttl: 30m
      lease: 30s
      terminal-retention: 5m
```

此模块保存一次性投递状态，不负责共享或认证宿主 `HttpSession`。多实例应用还需要经过验证的宿主会话存储，例如应用配置的 Spring Session Redis repository。两个实例应使用相同的宿主会话配置和投递 namespace。宿主身份来自 `HttpSession`，不能由请求任意选择 Redis key。投递传输使用单独持有的客户端，因为通用 Redis 客户端的重连/重放策略不适合结果不确定的变更。

### 身份与生命周期

`RedisHttpSessionStoreFactory` 在宿主会话元数据中保存 domain epoch 及对应的可信身份，后续请求必须匹配。若投递状态已过期或丢失，而宿主元数据仍在，操作会失败，不会把旧身份当作新的空域。应用应按自身策略安排退出登录及新会话恢复。idle 生命周期应覆盖宿主会话的实际使用方式。

Boot 将 `RedisSessionLifecycleFilter` 注册在 Spring Session 标准过滤器之后、Security/MVC 之前。它在同步 `changeSessionId` 或 `invalidate` 之前撤销投递状态；原生 Servlet listener 也使用同一工厂。撤销失败会让宿主操作失败，并保留待核对的 epoch 元数据。会话迁移时复制宿主属性，不意味着可以把旧 reservation 转移到新身份。使用自定义宿主会话过滤器时，需保持此过滤器位于其后、认证之前。外部 repository 删除、管理端退出登录和异步宿主操作必须安排等效的撤销钩子，不能仅凭缓存会话推断它们已经发生。

### 失败、lease 与容量

`beginPageDelivery` 以 token 和 lease 预留 available 效果。`completePageDelivery` 只消费该 reservation；`abortPageDelivery` 恢复它，同时保留后写入的值和独立 error bags。lease 恢复会在同一原子转换中恢复过期数据并使旧 token 失效。普通操作和 `recoverExpired()` 可清理已知域；Redis TTL 本身不能执行恢复所需的 Java 合并逻辑。

lease 必须长于响应预算和存储命令预算，Boot 会校验该关系。调整容量时应明确配置，不能通过丢弃错误来增加吞吐：envelope 字节数、未终结 reservation 数、保留的终态 token 数均有上限，达到上限会使操作失败。JSON 保留 decimal 精度、大整数和空数组/对象；拒绝 POJO/binary 节点、非有限浮点值、超过 48 层嵌套、每次写入超过 65,536 个节点，或超过 1,000 个字符的数字字面量。

| 类型 | 职责 |
| --- | --- |
| `RedisSessionBackend` | 应用持有的 Spring Data Redis/Lettuce 传输，在应用关闭时释放 |
| `RedisSessionStore` | 绑定 epoch 的请求句柄及原子投递操作 |
| `RedisSessionOptions` | namespace、idle/lease/retention 与容量上限 |
| `RedisSessionException` | 不包含业务数据的有界 `Reason` 分类 |

`READ_FAILED` 表示提交变更前读取失败。`UNKNOWN_WRITE` 表示变更结果不确定：不能重放、宣称成功、返回空状态或回退到本地存储。适配器关闭自动重连重放和断连排队。仅明确未写入的 CAS 冲突或过期读取期限允许有界重算。一秒的 dispatch/重算预算不会取消已经提交的命令；已提交命令仍受其配置的 timeout 约束。`STALE_DOMAIN`、`INVALID_TOKEN`、`CAPACITY`、`CONTENTION`、`BUDGET_EXHAUSTED`、`CLOCK_REVERSED` 和 `INVALID_STATE` 表示其他失败关闭条件。不要向最终用户暴露底层存储细节。

支持的存储拓扑为单个 standalone Redis 事务域。部署时需配置 Redis 网络访问、认证及持久化；当前后端构造器使用普通连接。Redis 异步复制、failover 或数据丢失可能丢弃已确认写入。本适配器不保证跨 failover 持久性、浏览器恰好一次收取或跨语言会话共享。上限参数见[配置参考](../reference/configuration.md)，渲染与 HTTP 写入的边界见 [flash/会话](../guide/flash-session.md)。
