---
title: "Spring API 地图"
description: "索引 MVC、校验、会话、Boot 条件和替换 bean 的行为。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaMvcConfigurer.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaHandlerValidator.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/HttpSessionStore.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaSessionStoreFactory.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/JakartaValidationBridge.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaAutoConfiguration.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/main/java/io/inertia/boot/InertiaMetricsAutoConfiguration.java
verification:
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaHandlerValidatorTest.java
  - inertia-java/inertia-spring-boot-autoconfigure/src/test/java/io/inertia/boot/InertiaOverridesTest.java
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/MvcOutcomeAdviceContractTest.java
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/MvcAdviceContractTest.java
translation:
  locale: zh-CN
  canonicalId: reference/spring-api
  source: reference/spring-api.md
  sourceRevision: cfd65799716fff11c702c38747cf2c162170d163bbaef194dc98d8f7578d8e75
---

# Spring API 地图

MVC 适配器将同步类型化 `InertiaResponse`/`HttpOutcome` 响应及请求拥有的效果接入 Spring MVC。starter 围绕应用 `InertiaConfig` 提供默认基础设施，不提供业务路由、身份或 SSR 进程。

## MVC 模块

| 类型 | 用途与边界 |
| --- | --- |
| `InertiaMvcConfigurer` | 注册参数/返回值 handler、拦截器与生命周期集成 |
| `InertiaHandlerValidator` | 启动时拒绝模糊或不受支持的 handler 声明 |
| `InertiaErrorPage` | 应用错误 Page 扩展点 |
| `HttpSessionStore` | 带 namespace 的会话预留、完成、中止及重定向合并 |
| `InertiaSessionStoreFactory` | 从可信宿主会话身份创建请求专属的存储句柄 |
| `ValidationBridge` | 将支持的 binding error 转为校验 bag |
| `JakartaValidationBridge` | 适配 Jakarta 约束违反，不包含被拒绝值 |

普通 `@Controller` 直接、同步声明返回 `InertiaResponse` 或 `HttpOutcome`。`@RestController`、`@ResponseBody`、异步 Inertia 包装与不兼容泛型/容器返回声明无法满足同一契约，检测到时拒绝。Spring 将继承泛型 advice 解析为具体受支持 Page/outcome 类型时可以使用。普通 REST/文件/SSE 仍由 Spring 负责。见 [MVC 集成](../integrations/spring-mvc.md)。

异常 advice 替换 Page 时，恢复原交付并使用新的无会话错误 context。重定向/outcome advice 使用新的 context，在原 store/namespace 上排队自己的效果，不复制失败请求的共享值或待提交效果。会话失效后新建会话，不代表可以把已预留快照转移给新身份。

最终 `InertiaExceptionResolver` 是包私有类型，由 configurer 注册。用应用 advice 或 `InertiaErrorPage` 配置错误行为，它不是公开扩展构造器。

## Boot 模块

| 类型或制品 | 用途 |
| --- | --- |
| `InertiaAutoConfiguration` | 根据应用输入提供默认 codec、executor/resolver、session/MVC 接线 |
| `InertiaProperties` | 八项经过校验的执行、展示和会话设置 |
| `InertiaMetricsAutoConfiguration` | 前提满足时提供可选 Micrometer 集成 |
| `MicrometerInertiaObserver` | 将有界事件转换成 timer |
| `inertia-spring-boot-starter` | 依赖入口及模块指南，没有运行时 facade 类 |

替换默认值时应明确提供所需配置、observer 或 store。标准覆盖测试包括自定义 bean、显式 false 和可选指标缺席。指标集成需要唯一或 primary registry；不会自动公开管理端点或认证策略。

## 校验与生命周期验证

`ValidationBridge` 从 binding error 复制消息，不序列化被拒绝值或表单目标。`JakartaValidationBridge` 使用 [Jakarta Path 节点](https://jakarta.ee/specifications/bean-validation/3.0/apidocs/jakarta/validation/Path.Node.html)适配约束违反，不依赖提供方特有 `Path.toString()` 输出。可选 Jakarta 集成需添加 `spring-boot-starter-validation` 或选定提供方。自定义消息本身也必须避免敏感值。

索引/key 字段形成 `items.0.name`、`byKey.primary.name` 等路径；bean 级错误使用 `_form`，无索引 iterable 元素使用 `*`。非字符串/数字/枚举 map key 被拒绝。约束违反输入是 Set，因此按路径和消息排序；Spring binding error 保留自身消息顺序。保存交付时保留全部消息，直到渲染阶段决定 first/all-errors 展示。

`HttpSessionStore` 在 session mutex 内操作前后，确认状态仍附着于原活跃会话。[Servlet 失效会移除会话绑定](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/http/HttpSession.html)。失效或 namespace 替换会失败，不会把旧 flash 重新附着新身份。core 已完成交付不能因后续 Servlet/网络写入失败而回滚，见[会话所有权](../guide/flash-session.md)。

MockMvc 用于 handler 校验、Page 元数据、重定向/会话所有权和错误路径。CSRF、cookie、官方客户端表单和 hydration 需要真实浏览器。`AssertablePage` 辅助载荷断言，不替代状态/响应头检查。见 [Spring 测试](../testing/spring-tests.md)。

公开构造器与方法精确签名见 [Javadoc](javadoc.md)。认证示例仅作演示，实际身份/会话后端应按应用策略接入。
