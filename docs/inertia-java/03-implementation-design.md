# 实施细节设计

## 1. Spring MVC 生命周期

协议应用范围由可配置路由 predicate 决定，默认只覆盖明确启用的 Inertia 页面路由；REST、下载、静态资源、SSE 不参与空响应/重定向改写。

1. Spring Security 完成鉴权/CSRF，随后 MVC `HandlerInterceptor.preHandle` 获取原始 URL、协议头、会话适配和 immutable 请求快照，放入 request attribute。
2. ProtocolPolicy.before 对 Inertia GET 检查版本；若冲突直接写无 body 的 409 控制响应，设置 Vary，不执行控制器、不消费已有 flash。
3. `HandlerMethodArgumentResolver` 从 attribute 提供 InertiaContext；缺少上下文时报可诊断配置异常。
4. 控制器返回 InertiaResponse、专用 InertiaRedirect 或 InertiaLocation。注册 `HandlerMethodReturnValueHandler` 处理这些类型。
5. 返回值处理器解析 Page、SSR、模板，获得尚未写出的 HttpOutcome；对结果应用 after，完成会话消费/commit，最后设置状态与头并一次性写 body，标记请求已处理。
6. 普通返回值由原 MVC 机制处理。首版不对已经提交的普通响应补做 fragment/empty 改写；需要这些语义的页面端点使用上述专用返回类型。文档及启动诊断说明这个边界。

不能仅靠 `postHandle` 或 `afterCompletion` 改写状态：默认返回值处理器可能已写出响应。避免全局 buffering wrapper 缓存大文件/SSE。首版协议适配明确收敛到专用响应模型，后续若支持其他响应类型，需在写出前增加对应处理器并独立验收。

过滤器不承担核心渲染；request attribute 在同一请求重入时复用，并有 CREATED → RESOLVING → PREPARED → COMMITTED/FAILED 状态机，禁止二次求值或二次 commit。首次版本只支持同步 MVC dispatch；异步控制器及 ASYNC redispatch 支持需后续专项实现，不能宣称已经支持。

异常经 HandlerExceptionResolver/ControllerAdvice 转为安全错误页。解析错误页最多一次，失败回退纯文本 500，避免递归。SSR 失败走 Fallback，业务 props 失败走异常路径；未完成的 Page 不能作为成功响应发送。

## 2. 线协议与序列化

| 输入/场景 | 输出/规则 |
|---|---|
| 普通 GET | HTML UTF-8；默认不设置响应 X-Inertia |
| Inertia page | JSON + X-Inertia:true + Vary:X-Inertia |
| Inertia GET 版本不同 | 409 + X-Inertia-Location + 服务端版本，无 X-Inertia |
| PUT/PATCH/DELETE + 302 | 303；POST 默认保留 302 |
| 带 fragment 重定向，非 prefetch | 409 + X-Inertia-Redirect |
| 外部 location | Inertia 409 + Location 控制头；普通访问 302 |
| 专用响应空 200 | Inertia 回 Referer（安全校验）或 `/` |

Vary 使用多值头模型并去重，保留 Accept-Encoding 等已有值。协议控制头、Content-Type 和 Content-Length 由 adapter 最终拥有，用户 `withHeader` 不能覆盖这些受保护字段。状态码及允许的业务头在 HTML/JSON 两种路径均保留。

请求解析支持全部 Rust 协议头和 Purpose/Sec-Purpose/X-Moz。首版沿用“X-Inertia 头存在”的识别以减少迁移差异，限制头长度、逗号列表条数和路径深度；partial_component 不一致忽略 partial only/except。

Page codec：必需字段始终输出；metadata 在顶层展开；空集合/false 标志按 Rust 规则省略。保留 `onceProps[key]={prop,expiresAt}`，无期限 expiresAt 为 null；scroll 输出 pageName/previousPage/nextPage/currentPage/reset。组件名来自应用注册表，不接受用户任意路径拼接。

HTML 中使用 v3 的 `<script data-page="app" type="application/json">...</script><div id="app"></div>`。编码器对 `/ < > & U+2028 U+2029` 做 JSON 转义，禁止 HTML entity 编码 JSON script。root id 限制为安全标识符并做属性编码。JSON HTTP 分支使用普通 JSON 编码。

大整数：遍历 Jackson integral node，超出 ±9007199254740991 变成 `$bigint` 十进制字符串，对 props 和 flash 一并执行。Decimal/金额使用明确业务 DTO 策略，不能把浮点数一律转换为 BigInt。SSR 与客户端必须启用相同标记解析；需真实客户端用例验证。

## 3. Props 解析算法

### 3.1 阶段与优先级

配置共享数据 < 请求 share < 页面 props，按相同 key 覆盖；内置 errors 起初为 always，应用覆盖 errors 的行为需明确并提供诊断。先建立有序树，再规划 Included/Excluded，之后执行 supplier，最后按声明顺序归并结果与 metadata。

首版约束：点号树不得同时定义 scalar/callback 父节点与子路径，冲突在构建时抛出 PropDefinitionException。普通嵌套对象与不冲突点号路径受支持。该限制是相对 Rust 的显式差异，未来可增加带合同 fixtures 的兼容模式；避免为点号展开提前查询数据库。

| 行为 | 完整访问 | 同组件 partial 命中 |
|---|---|---|
| literal/lazy | 输出/执行 | 输出/执行 |
| optional | 省略，不执行 | 执行 |
| deferred | 省略，记录 group | 执行，不重新公告整组 |
| always | 输出 | 不受 only/except 限制 |
| once | 首访执行，后续持有 key 可跳过 | 显式请求可执行 |

匹配函数：within(path,ancestor) 当相等或 path 以 ancestor + '.' 开始才为真。only 双向匹配让祖先节点保留下钻；except 单向排除整棵子树。metadata 的路径命中使用单向规则，不能因为祖先仅用于下钻就误发送它的 merge/once metadata。

literal object 的子节点继续过滤；supplier 结果作为已计算值整体输出。数组保持整体值，首版不承诺数组下标 partial。请求字段过滤不能代替授权：敏感字段在 DTO 建立阶段就必须排除。

### 3.2 并发、异常与取消

同层命中 supplier 在受控 executor 上启动，按输入顺序保存 future。异步来源保持 CompletionStage，不提前 join；同层完成后依原序处理嵌套树。限制单请求与全局并发，过载拒绝作为 props 失败；不能使用无界 commonPool 或请求线程内创建 executor。

截止时间到达后停止接收结果，取消可取消的任务；JDBC/HTTP 调用需要自身超时，Future.cancel 不保证底层工作停止。回调不能在响应完成后继续修改 flash：Context 转 PREPARED 后拒绝写 pending 并记录诊断。兄弟回调写相同 flash key 的优先级不得依赖线程抢锁顺序；首版禁止冲突写，或由按 prop 顺序归并的 callback effects 实现确定性。

未 rescue 的错误终止 Page；rescue 省略字段并记录 rescuedProps。首版限定 rescue 用于 deferred 可选数据，构造器拒绝不支持组合。保持错误因果和 prop path，响应不暴露堆栈。once 使用注入 Clock，按 Rust 秒精度 TTL 生成 epoch 毫秒；once 是客户端复用指令，不是服务端缓存，不绕过鉴权。

merge/deepMerge/prepend/matchOn 都生成客户端元数据，服务端不保存上一页集合。reset 精确命中 prop path 后取消 merge 指令；scroll adapter 从 Spring Data Page/自定义 paginator 提取元数据，core 不依赖 Spring Data。匹配 id 等路径必须与客户端真实对象结构一致。

## 4. 会话、错误与历史

保留 key 命名：`inertia.flash_data`、`inertia.errors`、`inertia.clear_history`、`inertia.preserve_fragment`。配置可加应用 namespace，防止同 session 多应用碰撞。无状态请求可渲染，但跨重定向 flash/errors 必须配置 SessionStore。

状态流：redirect 把 pending 写入 session；下一次 render 读取 session 快照，与当前 pending 合并，准备好最终响应后消费该快照。flash 覆盖顺序为旧 session→请求→回调效果→response.flash；响应 history 加密开关→请求开关→全局默认，clearHistory 用 OR。

Java 改进方案：SessionStore 增加 beginPageDelivery/completePageDelivery/abortPageDelivery SPI。HttpSession 实现在短临界区内取得旧快照并预留 token；不持有锁执行 props/SSR；失败恢复预留数据且不覆盖后来写入的新数据，成功删预留。每个同会话 render 独占领取一次快照。Redis/Spring Session 集群实现必须以原子脚本/CAS 提供相同保证；不能认为本地 synchronized 能解决跨节点竞争。首版验收 HttpSession 单节点，并明确集群适配未完成。网络写出失败无法保证浏览器收到一次，承诺仅为响应准备成功后的一次领取，不宣称 exactly-once delivery。

error bags 保留 default 和命名 bag，首个消息/全消息可配置；BindingResult 或 Jakarta Validation 转为库内 ErrorBags，排除 rejected password 等原始值。表单验证失败返回 redirect，不直接 JSON 422；独立 REST API 保留原语义。clearHistory/encryptHistory 是浏览器指令，服务端不自己实现浏览器历史加密。

## 5. SSR 网关和前端

接口拟为 `CompletionStage<SsrResult> render(Page page, RenderRequest request)`，SsrResult 为 Rendered(head,body) 或 Fallback(reason)。首次 HTML 才调用；JSON、withoutSsr、disabled 和 except 路径直接 CSR。

复用单个 JDK HttpClient。开发模式读取可信 hot 文件 URL，POST `/__inertia_ssr`；生产模式 POST 配置 endpoint `/render`。请求体是最终 Page JSON，不另包 `{page:...}`。响应为 null 或 `{head:[...],body:"..."}`；head join 换行，body 作为完整 app body 原样嵌入。响应缺字段、错误类型、超长 body、非 2xx、超时均降级并记录原因。Node 端输入 schema 与路径注册表做验证。

endpoint 只能由可信配置给出，禁止从请求 header/props 生成。关闭跨 host HTTP redirect 跟随，限定内部目标；hot-file 在 production 禁用。不得转发 Cookie/Authorization 给 Node。SSR 收到的 Page 仍可能包含用户数据，因此禁止公共 renderer 及跨用户 Page 缓存。

不重试单次 SSR 请求，避免延迟放大；必要时使用成熟 resilience 库做熔断，首版可只做硬超时和并发上限。健康 `/health` 在后台定期检查，不能每次请求先 health 再 render。fallback 输出安全 Page script + 空 app 节点，前端选择 mount；SSR 成功选择 hydrate。

前端工程包含 app.tsx、ssr.tsx、pages/Users/Index.tsx、pages/Error.tsx、vite.config.ts。依官方锁定版本配置 createInertiaApp、组件 resolver 和 SSR renderer；不要复制其他 Inertia 主版本启动代码。实现 spike 必须确认 SSR body 是否已包含 script/root、root-id 自定义能力和 bigint revive，未确认前保留为 release gate。

## 6. Vite、模板和安全集成

开发资源来自可信 hot URL，React refresh preamble 按已锁定 Vite 插件生成；生产解析 manifest 的 entry/file/css/imports，递归 imports 去重，输出 script/modulepreload/stylesheet。每个 entry 不存在时报告配置错误，不能静默返回缺资产 HTML。

开发允许 mtime 刷新；生产启动时固定 manifest/hot 策略，version 为同一 build 的 manifest hash 或 build id。文件路径相对明确 asset root，拒绝 traversal。建议 CI 固定 build id，并校验客户端与 SSR bundle 来源相同。

RootView 可通过模板库实现，只有可信 SSR head/body 片段允许 raw HTML；用户 view data 正常转义。CSP nonce 同时传给资产标签/refresh/根模板，开发与生产策略分开验证。Spring Security 负责 CSRF cookie/header 对接和 session cookie 安全配置；Inertia 头不是安全凭证。URL 重建只信任已配置代理，back 默认限制同源 Referer，否则回 `/`，防止开放重定向。对个性化页面设置 private/no-store，Vary 不能代替身份隔离。
