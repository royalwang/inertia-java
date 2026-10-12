# N2 认证与会话集成

日期：2026-10-12。状态：N2 本地验收完成；N3/N4 未完成。依据：[再次迭代路线](14-follow-up-roadmap.md)。本文记录 N2a/N2b 的行为设计、实现和直接证据；公开发布不包含在本地资格中。

## 状态与响应合同

| 触发 | HTML / Inertia 行为 | 身份与交付状态 | 缓存和历史 |
| --- | --- | --- | --- |
| 匿名访问 `/account` | HTML 303 到 `/login`；Inertia 409 + X-Inertia-Location | 不返回账号 props；不为拒绝单独创建会话 | private, no-store；不缓存身份页 |
| 登录成功 | POST 303 到固定 `/account` | Spring Security 先转换身份；factory 向新身份写入 clearHistory | 登录页清历史；账号页加密历史 |
| 登录失败 | POST 303 到固定 `/login` | 通过配置的 factory 写入通用 credentials 错误，下一页消费一次 | 不回显密码；前端提交完成清空密码 |
| CSRF 拒绝 | 指定 Inertia POST 303 到固定表单；其他请求 403 | 不执行被拒绝业务；通过相同 factory 写入安全提示 | no-store；不自动重放写入 |
| 退出 | 当前 CSRF 验证通过后 POST 303 到 `/login` | Spring Security 撤销身份，宿主 invalidate 必须触发交付域撤销 | 同源标签页提示刷新；服务端仍是身份权威 |
| 会话过期 | 后续受保护访问按匿名合同处理 | 不恢复旧交付域，必须重新认证 | 历史缓存不代表登录仍有效 |
| 权限不足 | 403 | 不执行业务、不附带身份敏感 props | 受保护请求不依赖客户端显示状态 |
| 登录成功处理的交付存储失败 | 异常，不返回成功跳转 | 清除 Spring 已保存的 SecurityContext，不静默退回本机 store | 后续请求仍应为匿名 |

返回地址目前固定，不接受请求参数、Referer 或外部 URL。默认示例身份转换为 Spring Security `newSession()`：清除旧应用属性；可选 `inertia.demo-fixation=change-session-id` 仅接受内置 Redis lifecycle factory，保留普通宿主属性并撤销旧交付域；两种策略均通过实际双节点验收。

## 职责与顺序

Spring Security 管理认证、SecurityContext 和 CSRF；Spring Session 负责共享宿主会话；InertiaSessionStoreFactory 负责可信宿主身份对应的 flash/errors/history 交付域。匿名写入、登录后的新身份写入以及 CSRF 恢复使用同一个配置入口。

Redis 模式过滤器必须在 Spring Session 之后、Security 之前。身份转换前撤销旧 delivery epoch；旧请求持有的 reservation 不得完成或恢复旧值。Redis 撤销失败时阻止宿主变化并保留待协调元数据，不能把返回登录页或清理浏览器历史当作撤销成功。

登录成功回调执行时，Spring Security 已保存新 SecurityContext。若 factory 创建或交付写入失败，示例显式保存空 SecurityContext 再传播异常；防止浏览器看到失败但服务端仍留下成功登录。共享宿主仓库本身发生未知写入时仍需真实网络验证，单实例 MockMvc 结果不证明跨节点原子性。

## 当前实现

- 修复 React `DemoAuth` 成功/失败处理与 `BrowserCsrfFailureHandler` 绕过已选择 store factory 的问题。
- 保留 CSRF helper 的本地构造入口供已有示例测试使用；应用装配改用配置 factory。
- 新增 factory 探针验证成功、失败和 CSRF 路径；注入存储失败，检查无成功跳转及登录身份清除。
- Vue 新增可选身份安全链、SSR/客户端登录与账号页、密码清空及同源标签页退出提示；默认公开业务路径保持可用。
- 新增共享 `npm run test:auth` 真实 Chromium 驱动，拥有并清理 Java/Node 进程，记录 JAR/构建/驱动身份；可用 `INERTIA_AUTH_CONSUMER_JAR` 指定仓库外消费产物。两种前端均执行实际一分钟 Servlet 空闲过期、角色拒绝、错误消费、旧会话与旧 CSRF 拒绝及跨标签页退出。
- 独立 Maven 消费工具增加 `INERTIA_VERIFY_AUTH=true`：复制两个示例到 reactor 外，从隔离候选仓库构建，再使用实际 JAR 执行认证浏览器链路。聚合工具可显式启用该检查。

## N2b 当前变更

- 两个示例提供可选 Maven `redis-session` profile，分别引入 Spring Session 宿主共享与 Inertia Redis 交付依赖；默认消费不增加 Redis 依赖。
- 仓库外夹具复制实际示例源代码，仅额外注入本地 `AuthProbe` 控制路由。真实 HTTP 代理将登录写入 A、账号读取及退出送往 B；旧 reservation 留在 A，分别在身份转换、退出、过期后尝试写回。
- 独立检查 `new-session` 和 `change-session-id`，记录普通属性的清除/保留，不把清理客户端历史当作服务端撤销。
- 实际 Spring Session 空闲过期暴露了缓存 handle 可延续旧交付域的问题；factory 增加宿主报告的空闲期限检查。远程删除仓库、在其他请求中缩短期限等缓存无法发现的变化仍需应用撤销 hook。
- Redis profile 单元测试明确保留原生 Servlet 宿主；真实分布式行为由双 JVM HTTP/browser gate 证明。首次直接激活 profile 导致原生单元测试连接默认 Redis，现已隔离两种验收职责。

## 本地验收与要求对应

| N2 要求 | 当前证据 | 结论 |
| --- | --- | --- |
| 六类认证状态、HTML/Inertia、缓存/CSRF/history 合同 | 上表；DemoAuthTest；SSR/CSR 实际浏览器 | 完成 |
| 成功/失败/CSRF 使用选定 factory，无本地回退 | factory 探针单元合同；真实 Redis 跨节点错误消费和身份跳转 | 完成 |
| 登录处理存储失败不能留下虚假成功 | 单元注入失败后 SecurityContext 清除；真实独立交付端口断连及恢复 | 完成 |
| newSession 清属性，changeSessionId 保留普通属性且撤销旧交付域 | React/Vue 各两策略双节点检查；旧 cookie 拒绝 | 完成；轮换示例限内置 Redis lifecycle factory |
| Spring Session 后、Security 前的生命周期桥接 | 两实际 JVM 使用 Spring Session Redis；登录/退出/旧 reservation | 完成 |
| 跨节点失败错误只消费一次、登录重定向和退出 | 登录 POST 到 A，账号 GET 和退出到 B；两种前端真实交互 | 完成 |
| 并发标签页、并发登录/退出及晚到写入 | 双标签页退出；固定同一旧 cookie 同时提交登录/退出；A 持有旧 reservation | 完成；不声明全局用户级事务撤销 |
| 宿主真实过期与旧请求 | 原生 Servlet 实际一分钟；Spring Session 实际两秒；缓存 handle 拒绝写回 | 完成 |
| Redis 断连、撤销失败协调、真实重启 | 独立交付 TCP 故障代理；宿主仓库保持可用；重启丢数据后旧认证拒绝 | 完成；无 failover 保证 |
| Vue SSR/hydration、CSR、CSRF、密码清空及退出同步 | 当前 SSR 六阶段、CSR 五阶段；分布式每前端十六阶段 | 完成 |
| 独立 Maven 消费 | 新隔离缓存、候选制品、仓库外复制实际示例与额外本地探针；两种 profile 产物 | 完成 |
| 双语指南与外部撤销边界 | guide/authentication 英/中文；可信 HttpSession 的 beforeHostChange 指引与离线限制 | 完成 |
| 原有兼容性与回归 | 307 标准 Java 测试；3 个真实宿主 IT；原 Redis 双节点六阶段；八库 API 比较 | 完成；未改动的 Redis 原子状态合同沿用 R3，不冒称重跑 |

### 证据范围

当前私有候选消费总计 15 阶段，包含已有 Redis 双节点六阶段和 React/Vue 各十四阶段认证。随后在同一独立消费 JAR 上补跑并发登录/退出，最终两种前端各十六阶段；新增用例没有冒充此前整条消费命令已经包含它。原生 SSR 六阶段和 CSR 五阶段分别记录。最终源码、候选/JAR/驱动身份、API 结果与原始摘要见 [N2 本地资格摘要](verification/iteration-n2.json)及其 gzip 收据。

第一批单实例与独立消费进展保留在 [N2a 历史摘要](verification/iteration-n2-progress.json)，它标识当时源码，不代表当前源码的最终资格。

### 修复与可信边界

实际 Spring Session 空闲过期时，仓库已拒绝旧身份，但请求本地缓存的 session/交付 handle 尚能续写旧域。新的 idle 检查使用可信宿主时间与期限，防止此类晚到操作。真实验收先复现写回未被拒绝，再在当前候选确认拒绝。

检查的是缓存宿主报告的期限；在另一节点缩短超时、离线直接删除宿主仓库等变化不能由缓存自动发现。应用必须通过可信生命周期 hook 协调撤销。已经获准执行的业务操作、全局账号/全部设备退出以及不同仓库之间的原子事务不属于本示例的保证。

并发登录/退出允许明确成功或存储失败；两者竞态不复活旧身份。成功的新登录可能创建独立的新身份，不能把旧会话退出解释成永久禁止任何后续登录。

## 后续

N2 不包含正式 Maven 发布、容器装配、Redis HA 或公开站点部署。下一步按原路线完成 N3 TLS、容量和恢复指南；N4 依实际采用环境选择。整个路线目标仍未完成。
