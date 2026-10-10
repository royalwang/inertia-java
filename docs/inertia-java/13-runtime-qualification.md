# R2 本地运行资格与容量复测

日期：2026-10-10。范围：Java 21.0.1、Node 22.22.2、锁定Boot/React依赖、macOS 25.6.0、Apple M1 Pro（8逻辑CPU/16GiB）。这是工程验收记录，不属于公共使用指南。

## 行为和诊断

完整aggregate的22阶段通过，包含实际SSR/browser matrix、Node失败/恢复、超大响应、CSP、自定义root、history、release switch及本地部署装配。297项Java测试无失败、错误或跳过。新增四项诊断合同使真实props超时、执行器拒绝、业务provider错误、session merge失败连接到既有observer/Micrometer分类，并验证恢复后可继续服务。业务失败不能借SSR fallback变成成功。

API工具用R1保存候选对比R2候选：七模块源码/二进制差异检查通过，负例工具合同通过。Rust freshness独立通过37 Page/45 HTTP/9 live TTL。文档check/build和本地Chromium验证140页/14阶段，包括Mermaid。未查询公开站点或GitHub部署。

## 负载和对照方法

transport沿用/users（2个静态用户、无数据库），对R1 app.jar与R2 app.jar进行同机交替三次对照。每阶段8次预热，正常128请求，stalled-body仅8请求，并发1/8/32；SSR permits16、预算1秒。stalled-body的实际并发受请求数8限制，不能把配置32误作32个同时请求。

application使用显式 `inertia.benchmark-enabled=true` 的/benchmark/page（1个用户），同一当前构建重复三次；每阶段64请求、8预热、并发1/8/32；payload分别64与262144字节。JSON包含单独partial(payload)/deferred(stats)选择，校验字段选择和内容长度；同session场景先获取真实JSESSIONID。夹具默认关闭，并有自动装配合同保护。没有修改库的运行热路径。

每次驱动自建并清理独立Java/Node进程，记录构建身份、JAR摘要、环境、冷启动、p50/p95/p99、吞吐、错误、SSR比例、fallback reason和进程资源；application额外采样JVM堆与线程队列。HTTP负载来自同机Node fetch闭环，不包括浏览器渲染/数据库。完整指标及每次原始摘要见文末归档。

## 三次对照结果

表格为三次p95中位数；百分比提示人工调查，不能作为显著性检验或生产SLO。全部测量请求HTTP200且无客户端错误，SSR容量不足会按既有策略返回CSR，需同时阅读SSR比例。

| 模式 | 并发配置 | R1 p95 ms | R2 p95 ms | p95变化 | 吞吐变化 |
| --- | ---: | ---: | ---: | ---: | ---: |
| connection-refused | 1 | 7.4 | 5.5 | -25.3% | +21.7% |
| connection-refused | 8 | 12.9 | 11.0 | -14.5% | +20.3% |
| connection-refused | 32 | 49.5 | 42.7 | -13.6% | +19.2% |
| excluded-csr | 1 | 4.4 | 5.0 | +13.0% | -9.0% |
| excluded-csr | 8 | 9.1 | 8.4 | -7.9% | +0.7% |
| excluded-csr | 32 | 33.0 | 46.3 | +40.5% | +14.7% |
| ssr | 1 | 9.2 | 7.9 | -14.3% | +5.2% |
| ssr | 8 | 18.0 | 17.3 | -3.8% | -4.6% |
| ssr | 32 | 51.2 | 53.7 | +5.0% | +4.4% |
| stalled-body | 1 | 1018.1 | 1021.8 | +0.4% | +0.0% |
| stalled-body | 8 | 1024.6 | 1022.9 | -0.2% | +0.2% |
| stalled-body | 32 | 1023.6 | 1017.4 | -0.6% | +0.6% |

初测6行的p95或吞吐变化超过10%。CSR并发32的p95从33.0升至46.3ms，需额外检查；其他行也保留各次范围和吞吐值，不根据有利方向挑选结果。核对两个app.jar及嵌套自有库的99个已有编译类，均无字节变化、无删除，只新增默认关闭的DemoBenchmark。此证据缩小代码变化范围，不能独自证明性能无回归。

对CSR并发32再进行三次交替复测：每阶段128预热、1024请求。R1 p95中位数27.7ms（范围21.3–31.4），R2为26.0ms（23.2–26.7），变化−5.9%；初测延迟上升未重现。吞吐中位数差异+13.9%仍需谨慎解释。复测初始load average约11.5–24.1，超过8逻辑CPU；机器负载不可控，加上JIT/GC及短阶段影响，不能确定归因或据此优化线程模型。本轮接受的是重复测量与人工调查证据，没有确认显著提升或生产回退。

## 应用场景

以下为并发配置32的三次中位数。JSON不计入SSR比例；HTML返回CSR仍为200。单独的真实浏览器和会话合同承担flash消费/abort正确性的验收，负载统计不能替代它们。

| 模式 | 场景 | p95 ms | SSR比例 | 最大采样堆 MiB | 最大采样排队 |
| --- | --- | ---: | ---: | ---: | ---: |
| excluded-csr | deferred | 9.6 | 0.000 | 131.3 | 0 |
| excluded-csr | large-html | 63.1 | 0.000 | 80.8 | 26 |
| excluded-csr | large-json | 48.9 | 0.000 | 98.6 | 26 |
| excluded-csr | partial | 38.1 | 0.000 | 104.1 | 17 |
| excluded-csr | same-session | 24.6 | 0.000 | 103.6 | 48 |
| excluded-csr | small-html | 39.4 | 0.000 | 72.2 | 30 |
| excluded-csr | small-json | 21.6 | 0.000 | 104.8 | 42 |
| ssr | deferred | 8.4 | 0.000 | 326.2 | 0 |
| ssr | large-html | 100.2 | 0.391 | 233.5 | 8 |
| ssr | large-json | 41.4 | 0.000 | 213.7 | 6 |
| ssr | partial | 36.4 | 0.000 | 290.2 | 0 |
| ssr | same-session | 40.4 | 0.656 | 188.8 | 42 |
| ssr | small-html | 74.7 | 0.859 | 44.8 | 30 |
| ssr | small-json | 18.0 | 0.000 | 125.0 | 28 |

application正常负载没有固定-Xmx；采样堆会随分配、GC和JVM扩容变化，不得据短测试判断泄漏。另用专门的128MiB堆压力进程验证有界拒绝和恢复。

## 过载与恢复

`verify-capacity-recovery.mjs`自建-Xmx128m JVM，线程池core/max2、queue2。32个重叠JSON请求执行500ms props：4成功、28返回明确失败，观察到PROPS/OVERLOADED；最大采样queue2、堆约40.9MiB。负载结束后队列和活动线程归零，后续请求200。脚本要求真实拒绝、容量上限、恢复及正常进程退出；强制SIGKILL清理会失败。aggregate另执行一次通过。

这证明该有界配置下过载可恢复，不是长时间内存泄漏、生产容量或所有配置的资格认证。失败不会通过丢弃业务错误或静默空session换取吞吐。

## 复现

从仓库根目录执行。`R1_APP_JAR`指向保存的R1独立可执行应用；不可用当前JAR代替旧候选并称历史对照。候选归档和japicmp用法见[API工具说明](../../inertia-java/compatibility/API-COMPATIBILITY.md)。以下输出目录应是新目录，避免混入不同驱动/参数的历史运行。

```sh
cd inertia-java
INERTIA_BROWSER_CHANNEL=chromium \
INERTIA_API_BASELINE="$R1_CANDIDATE" \
INERTIA_API_TOOL="$JAPICMP_JAR" \
INERTIA_VERIFY_OUTPUT=/tmp/inertia-r2-local node scripts/verify.mjs
node compatibility/verify-fixtures.mjs
cd examples/spring-react/frontend
npm run test:capacity
for repetition in 1 2 3; do
  INERTIA_BENCH_REQUESTS=128 INERTIA_BENCH_SLOW_REQUESTS=8 \
  INERTIA_BENCH_CONCURRENCY=1,8,32 INERTIA_BENCH_JAR="$R1_APP_JAR" \
  INERTIA_BENCH_OUTPUT=/tmp/inertia-repeat/baseline node scripts/benchmark.mjs
  INERTIA_BENCH_REQUESTS=128 INERTIA_BENCH_SLOW_REQUESTS=8 \
  INERTIA_BENCH_CONCURRENCY=1,8,32 \
  INERTIA_BENCH_OUTPUT=/tmp/inertia-repeat/current node scripts/benchmark.mjs
  INERTIA_BENCH_PROFILE=application INERTIA_BENCH_REQUESTS=64 \
  INERTIA_BENCH_CONCURRENCY=1,8,32 \
  INERTIA_BENCH_OUTPUT=/tmp/inertia-repeat/application node scripts/benchmark.mjs
done
```

复测另设 `INERTIA_BENCH_MODES=excluded-csr INERTIA_BENCH_WARMUPS=128 INERTIA_BENCH_REQUESTS=1024 INERTIA_BENCH_CONCURRENCY=32`，同样交替旧/新各三次。总结工具：

```sh
python3 inertia-java/scripts/summarize-capacity.py \
  --baseline /tmp/inertia-repeat/baseline --current /tmp/inertia-repeat/current \
  --application /tmp/inertia-repeat/application --output /tmp/inertia-repeat/summary.json
```

原始运行第一轮错误选择Set-Cookie（XSRF而非JSESSIONID）被断言拒绝，修复后重新运行。三个探索application运行的全局route元数据不准确，也已完整重跑；失败与探索结果保留在本地临时目录，不混入以下21,264个审计请求（15,120主矩阵+6,144复测），预热和压力请求另计。

## 证据

- [主矩阵摘要](benchmarks/2026-10-10-iteration-r2.json)：p50/p95/p99、吞吐、堆、队列、reason、每次身份和范围。
- [预热复测摘要](benchmarks/2026-10-10-iteration-r2-followup.json)。
- [完整原始摘要归档](benchmarks/2026-10-10-iteration-r2-receipts.json.gz)：15次成功测量、压力/aggregate/API/文档浏览器及class continuity；gzip JSON，可解压审查。临时路径仅保留为原始定位信息，正文数据已归档。
- [R2机器验收](verification/iteration-r2.json)：归档SHA-256及阶段结果。

本轮没有增加JDK/Boot/浏览器支持声明，没有执行公开发布；Redis和双实例仍属于R3未完成范围。
