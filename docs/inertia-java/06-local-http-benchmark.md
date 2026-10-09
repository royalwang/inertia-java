# 本地 HTTP 性能基线

日期：2026-10-09。状态：初始基线已实测；不等同真实业务或部署容量验收。

## 复现

在 `inertia-java/` 执行 `./mvnw verify`，然后进入 `examples/spring-react/frontend/` 执行 `npm ci && npm run build && npm run benchmark`。独立脚本使用已构建 jar/receipt，创建自己的 Java、Node 与临时端口，无需已有服务。输出目录每次唯一，`INERTIA_BENCH_OUTPUT` 指定存放目录。参数、进程管理、指标定义见 [工程 README](../../inertia-java/README.md#local-http-performance-baseline)。

四种模式都访问现有 `/users`：真实 Node SSR、按路径排除 SSR、renderer 未监听、renderer 返回 headers/部分 JSON 后停滞。普通模式每个并发配置128请求、8个串行 warmup；停滞模式24请求、2个 warmup，配置并发32时实际24。并发可配置为1–64，测量数量有界。客户端完整读完响应后开始下一个请求，是 closed-loop 并发，不模拟固定到达率。

## 测量环境与口径

Apple M1 Pro、8逻辑CPU、16GiB、macOS Darwin25.6.0、Java21.0.1、Node22.22.2。初始loadavg见JSON，宿主机仍有其他应用；本次记录使用没有并行启动其他验证任务的最后一轮。构建来源是 e8e7383 加本轮未提交改动，jar SHA256和完整 frontend build receipt 随摘要保存。每种模式重启 Java，在同模式内依次并发1→8→32复用 JVM；8次串行 warmup 不代表稳定态，JIT/GC/连接池和模式顺序仍影响结果。

无数据库查询，两个内存用户。初始Page包含5个顶层props（errors/appName/catalog/users/largeId）、JSON 156bytes；catalog是内存计数器，其位数变化使响应字节略变。stats是deferred，浏览器后续获取未计入本次测量。HTML约1.0–1.5KB，范围见每阶段记录。每请求独立初始访问，不保留cookie，不验证同Session并发。

预算：props3s/response5s；SSR整响应1s、16许可、connect200ms；props请求并发8；executor8–32线程/256queue；client10s。启用安全JSON observer，其日志开销计入测量。耗时从fetch调用到完整body，P50/P95/P99使用nearest-rank。测量不包含浏览器加载资源、hydration、FCP/LCP或真实数据库。

资源每约200ms通过ps采样，记录最大采样RSS；并非精确峰值，短阶段可能仅一个样本。CPU是操作系统报告的进程生命周期百分比，可因多核超过100，原值留在摘要；不能当作该阶段CPU平均值。停滞peer运行在driver进程内，资源计入driver；不存在的Node以null表示，表中用“-”。

## 实测

所有1224次测量请求返回200，client timeout/error为0，每请求均有一条SSR_HTTP事件。以下延迟单位ms、RSS单位MiB；“并发”列为配置/实际，SSR超时比例与client超时分开。

| 模式 | 并发 | 请求数 | P50/P95/P99 | SSR比例 | SSR超时比例 | Java/Node/driver RSS | HTTP原因计数 |
|---|---|---|---|---|---|---|---|
| ssr | 1/1 | 128 | 4.6/6.5/8.6 | 100.0% | 0.0% | 229.7/100.8/110.3 | NONE=128 |
| ssr | 8/8 | 128 | 7.9/12.1/13.3 | 100.0% | 0.0% | 231.4/102.0/113.8 | NONE=128 |
| ssr | 32/32 | 128 | 14.5/41.5/54.3 | 56.2% | 0.0% | 235.2/104.0/118.7 | NONE=72, OVERLOADED=56 |
| excluded-csr | 1/1 | 128 | 2.3/3.9/4.5 | 0.0% | 0.0% | 236.7/59.6/120.8 | EXCLUDED_OR_UNAVAILABLE=128 |
| excluded-csr | 8/8 | 128 | 3.4/7.0/9.6 | 0.0% | 0.0% | 238.1/59.6/122.7 | EXCLUDED_OR_UNAVAILABLE=128 |
| excluded-csr | 32/32 | 128 | 10.7/35.1/48.9 | 0.0% | 0.0% | 236.7/61.5/125.7 | EXCLUDED_OR_UNAVAILABLE=128 |
| connection-refused | 1/1 | 128 | 3.3/4.7/5.3 | 0.0% | 0.0% | 227.3/-/129.2 | CONNECTION=128 |
| connection-refused | 8/8 | 128 | 5.4/8.6/11.3 | 0.0% | 0.0% | 227.7/-/129.5 | CONNECTION=128 |
| connection-refused | 32/32 | 128 | 13.8/39.2/46.3 | 0.0% | 0.0% | 231.1/-/131.0 | CONNECTION=90, OVERLOADED=38 |
| stalled-body | 1/1 | 24 | 1011.8/1017.8/1067.9 | 0.0% | 100.0% | 219.9/-/112.2 | TIMEOUT=24 |
| stalled-body | 8/8 | 24 | 1014.3/1019.9/1020.8 | 0.0% | 100.0% | 116.8/-/61.5 | TIMEOUT=24 |
| stalled-body | 32/24 | 24 | 1017.4/1027.5/1027.8 | 0.0% | 66.7% | 135.8/-/62.8 | OVERLOADED=8, TIMEOUT=16 |

并发32已超出16个SSR许可；聚合延迟包含更快的overload回退，不应据此推断SSR扩容或首屏改善。摘要同时保留SSR与CSR各自的延迟组，比较时必须检查比例。停滞body在正常许可范围约1秒回退，故障尾延迟由该deadline控制；排除和连接拒绝能较快返回，但都没有SSR内容。短样本对P99、资源峰值和长期吞吐的证据有限，暂不根据这组数据调大生产预算。

## 证据与后续

[完整摘要](benchmarks/2026-10-09-local-http-summary.json) 保存所有12阶段、每阶段响应大小/资源/超时比例/按SSR和CSR分组延迟、版本与运行环境。原始逐请求、逐资源采样、redacted events及Java/Node日志保存在摘要output目录；未将临时大日志纳入仓库。首轮缺少X-Inertia-Version的采样请求被正确409拒绝，脚本保留success=false并清理；修正客户端header后再实际运行，不将失败计为通过。SIGTERM独立演练产生exit1/success=false、保留完成阶段并清理拥有的Java/Node；该演练完成后重新运行此处基线，未与最后测量并行。

Java Maven reactor/Spotless verify154项通过、0 failures/errors/skipped；脚本语法检查通过。性能入口是手动命令，不加入每次CI的耗时门槛。既有前端bundle未改动，未宣称本轮重新运行全套浏览器矩阵。仍需业务数据/数据库负载、稳定态更长测量、真实部署与更广并发边界，整体J0–J7继续进行。
