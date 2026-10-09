---
title: "参与贡献"
description: "开发环境、设计先行的变更流程、验证要求以及文档和 API 的维护方式。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/README.md
  - inertia-java/pom.xml
  - inertia-java/scripts/verify.mjs
verification:
  - inertia-java/docs/scripts/check.mjs
  - inertia-java/deploy/documentation.test.mjs
translation:
  locale: zh-CN
  canonicalId: community/contributing
  source: community/contributing.md
  sourceRevision: eac8190196f4c47f6b64049ce7c07612c12e992ab7581b0fafab33c5246665bd
---

# 参与贡献

先明确用户可见的行为、所属模块以及失败和恢复策略。范围连贯、附带可执行验证证据的小改动，更便于审查。

## 准备变更

使用[开发环境](../getting-started/development.md)中的 Java 21 和 Node 工具链。在修改共享抽象之前，说明问题、预期行为及兼容性影响。核心模块应保持框架无关；MVC 模块负责 Spring 传输适配，示例应用负责演示路由和认证。

修改 API、配置或协议元数据时，同时更新相关指南、参考文档、规范示例和兼容性说明。公开文档以英文为规范正文；中文译文记录对应的英文修订，源文变化时需要重新审校。两种语言的正文应同步维护。

## 验证变更边界

文档变更需通过 Markdown、目录清单和链接检查、严格站点构建及浏览器冒烟验证。独立编译修改过的 Java 示例；运行时变更需运行受影响的契约检查。验证协议和客户端行为时，复用已有测试夹具及真实浏览器矩阵，不要用当前实现的输出替代外部验证依据。

修改文档打包方式时，运行发布文件选择检查。在拉取请求中列出验证命令、结果和适用范围。生成的站点文件、target 输出、node_modules、临时日志及密钥不应提交到源码仓库。

## 提交与维护

向[规范仓库](https://github.com/royalwang/inertia-java)提交拉取请求，说明最终解决的问题、变更后的行为、相关测试及迁移步骤。不要假定公开问题跟踪器或响应时限始终可用。普通问题的安全报告方式见[获取帮助](support.md)，敏感报告见[安全问题](security.md)。

贡献采用项目的 Apache-2.0 许可证。保留第三方许可证和署名，并按需说明复制或生成的素材。模块级贡献文件遵循相同的仓库流程。维护者会一并审查模块行为、文档和发行说明；此处不预设团队分工或 CODEOWNERS 指派。
