---
title: "许可证与署名"
description: "Java 模块的 Apache-2.0 许可、royalwang/2026 版权声明及第三方条款。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/LICENSE
  - inertia-java/NOTICE
  - inertia-java/scripts/dependency-inventory.py
  - inertia-java/docs/scripts/dependency-inventory.py
verification:
  - inertia-java/scripts/verify-library-artifacts.py
  - inertia-java/deploy/verify-release.mjs
  - inertia-java/docs/scripts/dependency-inventory-test.py
translation:
  locale: zh-CN
  canonicalId: community/license
  source: community/license.md
  sourceRevision: 9c2b165a53c3a44658043263bc86e0721d8e4106d1360d6030cbf365fb33188a
---

# 许可证与署名

Java 模块采用 Apache License 2.0，版权声明为：

```text
Copyright (c) 2026 royalwang
```

## 分发文件

Java 子树中的 `LICENSE` 和 `NOTICE` 是分发所用的许可文件。重新分发 Java 库、源码或 Javadoc 分类制品、示例发行包以及改编文档时，应保留这些文件。上层 Rust 项目和第三方依赖各自拥有独立的许可和署名要求；Java 模块的声明不会改变它们的版权归属。

二进制、源码和 Javadoc 分类制品及发行验证器，会在各自边界检查 Java 许可文件。生成的网站 API 资源与源码页面分别保留署名。打包检查成功只证明文件存在且内容符合检查要求，并非对所有下游用途作出法律判断。

## 第三方软件

发布前审查依赖清单及依赖许可证。Maven 和 npm 依赖图覆盖不同的运行时或构建依赖。文档工具是私有构建包，不会进入 Java 发行载荷；静态站点中实际分发的资源仍需满足相应许可义务。

生成 Java/React 示例依赖清单前，先构建 Maven reactor，并安装和构建示例前端。从 `inertia-java/` 运行 `python3 scripts/dependency-inventory.py`，可指定一个新的或空的输出目录。它使用固定版本的 CycloneDX Maven 插件和 npm SBOM 命令，覆盖 Maven test/provided 条目及完整和生产 npm 锁文件依赖图。工具将实际可执行包中的 `BOOT-INF/lib` JAR 与 reactor 和依赖哈希比较，记录仅在打包时出现的条目，并原样复制实际观察到的运行时和 npm 许可文件。阅读审查表和 `summary.json`；缺失的可选平台文件仍属于未观察到的内容，构建插件、JDK、操作系统、浏览器及打包资源署名需要另行审查。收集成功后仍保留 `publicationQualified=false`。

文档工具依赖清单需在 `inertia-java/docs/` 运行 `npm ci` 后执行：

```sh
npm run docs:dependencies
```

命令会输出一个唯一的证据目录，保留 npm 原始 CycloneDX 图，检查全部平台的锁文件路径及已安装包身份，并原样复制观察到的许可和通知文件，记录 SHA-256。嵌套的相同包实例仍保留各自锁文件路径；声明冲突会导致失败。本机未安装的可选平台包只记录缺失状态，不会虚构其许可文件证据。

阅读该目录内的 `inventory.json`、`summary.json` 和 `license-texts/`。收集成功不代表批准发布：`publicationQualified` 仍为 false，缺失声明、许可正文、互惠条款或多种许可条款都需要审查。此依赖图独立于 Java/React 应用清单，无法确定哪些构建依赖实际进入静态 HTML/JavaScript，也不覆盖 Node、npm、Python、浏览器和操作系统的许可。发布前审查实际分发的资源并保留其必要通知。

引入上游代码、插图或示例时，应保留相应通知。不要用 Java 模块的版权声明替换上游版权。许可要求时，应链接原始来源并记录修改。

## 贡献与发布

按项目许可证提交贡献，并标明第三方材料。发布时应随制品附带适用的许可和通知文件。发行流程还需针对具体版本审查传递依赖和生成输出，不能将旧依赖清单视为永久批准。

可构建源码和分发结构见[参与贡献](contributing.md)及[发布策略](releases.md)。准确法律条款以 Java 模块随附的 Apache-2.0 许可证为准。

## 上游参考

- [npm SBOM 文档](https://docs.npmjs.com/cli/commands/npm-sbom/)
- [CycloneDX Maven 插件](https://github.com/CycloneDX/cyclonedx-maven-plugin)
