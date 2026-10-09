---
title: "发布与升级"
description: "区分快照和正式版本，管理版本文档、发行说明及应用迁移。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/pom.xml
  - inertia-java/deploy/release.mjs
  - inertia-java/scripts/verify-library-artifacts.py
  - inertia-java/docs/scripts/versions.mjs
  - inertia-java/docs/scripts/release-snapshot.mjs
  - inertia-java/docs/scripts/version-artifacts.mjs
  - inertia-java/docs/scripts/site-archive.py
verification:
  - inertia-java/scripts/verify-maven-consumer.py
  - inertia-java/deploy/verify-release.mjs
  - inertia-java/docs/scripts/versions.test.mjs
  - inertia-java/docs/scripts/site-archive-test.py
  - inertia-java/docs/scripts/smoke-versions.mjs
translation:
  locale: zh-CN
  canonicalId: community/releases
  source: community/releases.md
  sourceRevision: 418daff99c8f2e1782b6d2a8c4bcf85eceb3afe544036e3e6bd7ea57ded44e0d
---

# 发布与升级

当前文档版本为 `0.1.0-SNAPSHOT / next`。本地制品和可构建的文档站点，并不能证明已有公开 Maven 版本、Git 标签或线上 Pages 部署。

## 准备发布

确定真实的版本和标签，审查 API、配置和协议变更，完成署名与依赖审查，并从同一份源码构建全部库的二进制、源码和 Javadoc 分类制品。运行制品检查、独立消费者检查以及受影响的运行时和浏览器契约检查。如果分发示例的 Java/Node 启动器，应将示例作为一致的整体打包。

发行说明应列出新增、修改和弃用的行为，不兼容的默认值或签名、迁移步骤、已验证的兼容性矩阵及已知限制。区分库版本、Inertia 协议、固定的客户端版本以及 JDK、Boot 和 Node 版本。不要从未版本化的 README 推断支持或补丁回移期限。

## 文档版本

真实发布标签产生前，维护一套规范的 `next` 内容。每个正式标签都应生成不可变的文档和 API 快照，构建成功后再添加版本映射和导航入口。单独提供 VitePress 版本菜单并不等于完成版本管理。不要将当前快照正文或 Javadoc 重新标注为旧版文档。

### 构建标签快照

创建标签前，同步更新 Maven reactor、目录版本、已审校的英文页面及中文译文修订。构建器会拒绝 `SNAPSHOT` 版本和不匹配的元数据，不会自动替换正文中的版本字符串。验证该版本的兼容性矩阵，并准备包含文档工具的源码标签。

在 `inertia-java/docs/` 目录运行以下命令，将占位值替换为 origin 上已存在的标签和一个新的输出目录：

```sh
git fetch --tags origin
RELEASE_TAG='your-existing-release-tag'
SNAPSHOT_OUTPUT='/absolute/path/new-documentation-bundle'
npm ci
npm run docs:snapshot -- --tag "$RELEASE_TAG" --output "$SNAPSHOT_OUTPUT"
```

命令会解析标签指向的精确提交，核对 origin 上的提交，并将源码导出到隔离目录。随后构建匹配的 Maven 分类制品及标签内锁定的文档工具，再检查、构建并用浏览器验证该版本路径。使用 Chromium 时，会安装标签所用 Playwright 包要求的浏览器。最终生成 `inertia-java-docs-<version>.tar.gz` 和注册回执。手动只读快照工作流执行相同构建，但不创建标签、GitHub Release 或部署。

### 注册并发布保留版本

审查归档及证据后，将其附加到对应的现有 GitHub Release，不要替换已有附件。配置好维护者身份认证后运行：

```sh
gh release upload "$RELEASE_TAG" "$SNAPSHOT_OUTPUT"/inertia-java-docs-*.tar.gz
npm run docs:register-version -- "$SNAPSHOT_OUTPUT/registry-entry.json"
git diff -- versions.json
```

注册工具在追加映射前，会核对 origin 标签、公开的规范仓库发行附件、SHA-256、源码身份和精确文件清单。SHA-256 用于发现内容变化；发布者的信任边界仍由源码和注册记录审查决定。不要替换已有的版本、标签或摘要。通过正常贡献流程审查并提交映射。

发布前构建当前站点，并组装保留的正式版本归档：

```sh
npm run docs:check
npm run docs:build
npm run docs:assemble-versions
npm run docs:smoke-versions
```

`next` 保持在仓库站点根路径；不可变正式版本位于 `/inertia-java/versions/<version>/`。每份归档保留自己的资源、API、源码链接和已有导航。当前站点的版本菜单列出已注册版本；新增版本不会重写历史 HTML。归档缺失、摘要或身份变化、路径穿越、链接错误、无效清单或已安装快照被修改，都会中止发布。Pages 工作流在上传前组装并验证所有已注册版本，后续 next 站点构建不会丢弃历史版本。

当前尚未注册真实正式版本。夹具测试用于验证工具边界，不能证明真实标签发布、跨工具链的未来重建字节一致性或公开托管。按摘要保留原始归档，并在接受重建结果前与原归档比较；不要通过覆盖归档隐藏差异。

中文译文跟踪英文内容修订。修改源文时，应标记需要审校的译文并保留英文回退，不要发布空的中文页面来增加覆盖数量。

## 升级应用

阅读变更说明，将配置归属和默认值、公开 API 以及元数据与上一版本比较。协调重建 Java、客户端和 SSR，并保留旧资源。在目标部署策略下，验证表单、会话、认证、新旧版本处理、真实 SSR/CSR 以及回滚。

本地 Maven 暂存子树可用于独立消费验证，但不是具备完整远程元数据和签名的公开快照仓库。凭据、签名、制品发布和 GitHub Pages 启用需要实际的发布基础设施及已批准的设置。参见[构建与发布](../deployment/build-release.md)和[版本切换](../deployment/rolling-upgrades.md)。
