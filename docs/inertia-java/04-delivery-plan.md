# 实施计划与验收

## 1. 工作包与依赖

以下为顺序化实施规划，无未经估算的日期承诺。每阶段先审查用户行为、模块所有权和失败反馈，再以测试验证风险边界。

| 工作包 | 产物 | 前置 | 完成门槛 |
|---|---|---|---|
| J0 兼容 spike | 锁定依赖、官方前端最小 SSR、协议 fixtures | 无 | 确认 v3 script/root/SSR body/bigint 和 Spring 返回值扩展点 |
| J1 core 基础 | Maven、Config/Request/Page/Codec/Response/Policy | J0 | HTML/JSON、409/303/Vary、安全 JSON 与状态头合同通过 |
| J2 MVC starter | interceptor、argument/return handler、autoconfigure | J1 | 示例直访/点击/404/普通 REST 共存通过 |
| J3 会话与表单 | HttpSession、pending 状态、验证桥接 | J2 | 无效提交→redirect→errors，成功→flash，一次领取、失败恢复 |
| J4 props 基础 | lazy/optional/always/partial/deferred、受控并发 | J1,J3 | 未命中 callback 零调用，deferred 后续加载，超时与权限失败正确 |
| J5 SSR 和 Vite | HTTP gateway、manifest、app/ssr entry、根模板 | J2,J4 | 首屏有内容、hydration 无告警、Node 断开仍可操作 |
| J6 完整能力 | merge/deep/prepend/once/scroll/history/bigint | J4,J5 | 元数据合同和真实浏览器状态变化逐项通过 |
| J7 发布准备 | CI、打包、部署模板、兼容矩阵、英文 API 文档 | 全部 | 资源一致性、故障演练、依赖/许可证检查、示例可重现启动 |

J1–J5 构成首个可用版本，支持 React 的实际 SSR 用户路径；J6 之后才可声明已覆盖本文列举的扩展能力。WebFlux、Redis 原子 session、Vue/Svelte 示例、Precognition、SSR JavaScript 引擎嵌入不在首版范围。

## 2. 合同 fixtures

从 Rust 现有测试构造输入 method/URL/headers/session/props definition，保存预期 Page、status、headers、callback trace；记录 Rust commit 与客户端 package lock。适合纯数据的 fixture JSON 跨语言共享；闭包并发等用语义场景描述并分别编写实现。

必需字段和头精确比较，普通 JSON 对象键顺序不作为网络规范要求；数组、metadata 声明顺序和 omitted/null 区别精确比较。Rust 特有行为（点号 callback 提前求值、会话提前 pull）单独列入差异表，不能把计划改进误报为移植通过。首次导出参考输出需要实际运行 Rust 测试/fixture runner；本轮没有生成该证据。

## 3. 用户验收矩阵

| 场景 | 可观察结果 | 验证层 |
|---|---|---|
| 普通访问与点击导航 | 首次 HTML，后续 JSON；URL 和页面正确 | MockMvc + 浏览器网络 |
| SSR 正常 | JS 加载前出现列表；启动后点击、表单可用，无 hydration mismatch | JS 禁用检查 + 浏览器 |
| SSR 断开/慢响应/非法 JSON/null | 有界延迟，CSR 可挂载，指标区分原因 | mock renderer + 浏览器 |
| stale version | 控制器调用数为 0，409 指向原 URL，flash 保留 | adapter 合同 |
| mutation redirect | PUT/PATCH/DELETE 302→303；POST 保持；fragment/prefetch 正确 | HTTP 合同 |
| partial only/except | 同组件过滤、异组件完整；未命中查询零调用 | core + 浏览器 |
| deferred group | 首屏无值但有分组，后续请求加载对应数据 | core + 浏览器 |
| callback failure/rescue | 默认安全 500；允许 rescue 的字段省略并有 metadata | core + adapter |
| flash/error bag | redirect 后一次展示，第二次不重放，错误只显示目标 bag | session + 浏览器 |
| 并发请求/渲染失败 | flash 不重复领取、不覆盖新 flash，失败恢复可诊断 | session 并发用例 |
| merge/reset/scroll | 追加/前插/去重和 reset 与 UI 数据相符 | metadata + 浏览器 |
| once/TTL/fresh | 持有 key 不重复查询，过期/显式刷新执行 | 注入 Clock + 浏览器 |
| 大整数与恶意字符串 | id 精确恢复；`</script>`、Unicode 不破坏文档或执行脚本 | codec + DOM |
| headers/status/errors | 404 页面保留状态；业务头保留；错误页失败无递归 | adapter |
| 多应用/多请求隔离 | auth、props、flash 无跨请求泄漏 | concurrent adapter |
| Vite 发布切换 | client/SSR/manifest 同 build，旧 hash 资源仍可用 | build/deploy 演练 |
| 非 Inertia 路由 | REST/上传/下载不受页面协议误改写 | adapter |

关键浏览器测试使用官方客户端真实访问，不用手工 fetch 代替 hydration。生产构建和开发模式分别跑；SSR 成功不能只检查 HTTP 200，必须检查内容及交互。

## 4. CI 与命令契约

计划为 Maven reactor `./mvnw verify`，前端 `npm ci`、typecheck、client/SSR build，随后 Java + Node 启动集成浏览器用例；具体 script 在工程创建时固定。测试 SSR 节点使用独立端口和可控响应，不依赖开发者已有后台进程。

发布物包括 core/adapter/starter jar、完整示例、前端 lockfile、版本/兼容声明、部署说明。不要把 Node bundle 打入通用 core；示例业务包与 frontend dist/SSR bundle 可通过部署流水线一起发布。

性能基线至少记录并发、数据库负载、props 数量/大小、SSR 成功比例、P50/P95/P99、超时比例和资源消耗。比较 SSR/CSR 以及 Node 故障时尾延迟，再调整设计预算。不在没有压测数据时承诺吞吐或首屏改善百分比。

## 5. 实施启动时需要关闭的决策

| 决策 | 当前默认 | 验证/变更条件 |
|---|---|---|
| Java/Spring 基线 | Java 21 + 单一 Boot 基线 | J0 验证 JSON 主版本及 handler 注册行为 |
| session 并发保证 | HttpSession 单节点原子预留 | 多节点要求 Redis/Spring Session 专项实现 |
| controller 返回值 | 专用 InertiaResponse，普通 @Controller | ResponseBody/ResponseEntity 需额外扩展并验收 |
| JS renderer | 独立 Node 服务，官方客户端 | 锁定版本后核对 `/render`/body/root/bigint |
| dot-path 冲突 | 构建时拒绝 | 必须兼容 Rust 时增加明确兼容模式和 fixtures |
| 强制 SSR 页面 | 默认降级 CSR | SEO/业务硬要求时按路由明确 fail policy |

## 6. 本次交付状态

已完成源码分析、模块与运行边界、接口草案、协议/props/session/SSR/Vite/Spring 的实施设计，以及可逐步执行的工作包和验收矩阵。未创建 Java 工程、未运行 Rust/Java/前端测试、未证明 SSR/hydration、未发布库。下一步可从 J0 开始，按 J1–J5 完成第一个可运行 Java 示例。
