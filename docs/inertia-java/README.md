# Inertia Java 服务端适配与 SSR 项目设计

日期：2026-10-10。状态：J0–J7首版及双语文档本地交付完成，下一轮R1/R2本地完成，R3实施中。

目标是基于当前 Rust 项目的架构，建设可独立复用的 Java Inertia 服务端适配库，并提供 Spring Boot + React + Vite + Node SSR 的完整示例。初期规划为本文档集；当前已交付七个可复用 Java 模块及完整 React/Node 示例，开发与生产模式均通过实际验收；完成范围和正式发布边界见第八项逐项审查。

## 阅读顺序

1. [Rust 实现分析](01-rust-analysis.md)：源码证据、能力清单、迁移注意点。
2. [Java 架构规划](02-java-architecture.md)：模块、职责、API、运行与部署边界。
3. [实施细节设计](03-implementation-design.md)：请求生命周期、props、会话、Spring、SSR 与资源。
4. [实施计划与验收](04-delivery-plan.md)：工作包、依赖、验收用例、发布门槛。
5. [实施状态与验证](05-implementation-status.md)：当前代码、测试证据与未完成项。
6. [本地 HTTP 性能基线](06-local-http-benchmark.md)：可复现命令、SSR/CSR与故障实测及测量限制。
7. [发布打包与部署手册](../../inertia-java/deploy/README.md)：独立发布目录、校验启动、主机模板与验收边界。

8. [首版验收逐项审查](07-acceptance-audit.md)：原始要求、可定位证据与明确缺口。

9. [英文 Java API 指南](../../inertia-java/docs/api-guide.md)：实际调用方式、生命周期和随发布物分发的可编译示例。

10. [开源文档库规划](08-open-source-documentation-plan.md)：用户路径、70条目录、站点与语言/版本策略、内容与示例规范、CI和D0–D4验收。

11. [开源文档实施台账](09-documentation-implementation.md)：D0/D1正文、工具链、独立教程和本地验收边界。

12. [下一轮迭代路线](11-next-iteration-roadmap.md)：首版消费、兼容性与诊断、多实例Redis会话，以及按需Vue/WebFlux扩展的优先级、实施设计与本地验收。

13. [下一轮实施台账](12-next-iteration-implementation.md)：R1–R3实际变更、验证结果与未完成范围。

14. [运行资格与容量复测](13-runtime-qualification.md)：R2本地诊断、兼容性、重复测量、过载恢复及原始证据。

## 主要决策

- Java 负责业务路由、鉴权、数据查询、Page 对象、Inertia 协议和根 HTML；JavaScript 运行时负责 React/Vue/Svelte SSR。保持现有 Inertia 客户端，无需另建客户端路由或同用途 REST API。
- 保留框架无关核心和薄适配层。首个适配器使用 Spring MVC；WebFlux 作为后续独立模块，避免在一个实现中混合阻塞与响应式模型。
- Java 21 为设计基线，Maven 多模块管理。Spring Boot、Jackson、Node、Inertia 与 Vite 的具体补丁版本在实施启动时锁定，并保存依赖清单与前端 lockfile。本文不宣称任何版本是当前最新版本。
- 首个示例选择 React + TypeScript。协议核心面向所有官方客户端；Vue/Svelte 的 SSR 示例后续分别验证。
- 先完成 HTML/JSON、重定向、版本、会话、partial 和 SSR/hydration 的完整用户链路，再扩展 merge/once/scroll 等能力。
- SSR 故障允许回退 CSR，props/权限查询故障不能伪装成 SSR 故障而被吞掉。

## 依据与可信边界

Rust 分析基线：`6667d8d1be314067af989eb049ba419a07fcd412`。依据为本仓库源码和现有测试代码；本次未执行 Rust 测试，未访问 README 所链接的兄弟 demo/Vite 仓库，未验证真实 Node SSR 响应。

外部资料于 2026-10-08 核对：

- [Inertia v3 协议](https://inertiajs.com/docs/v3/core-concepts/the-protocol)：作为线协议审查依据；兼容范围以已通过的用例矩阵为准。
- [Inertia SSR](https://inertiajs.com/docs/v3/advanced/server-side-rendering)：作为 JavaScript 渲染运行方式依据。
- [Spring MVC 返回值](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html)：作为适配扩展点依据。

Java 类名、配置名、目录和代码片段均为拟定接口。协议之外的设计取舍在各章明确说明。上述四篇为初始设计快照；实际实现与仍待完成的范围见第五篇实施记录。

- [README专题迁移对应](10-readme-topic-migration.md)：旧锚点、新文档与历史文本保留。
