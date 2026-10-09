---
title: "构建不可变发布物"
description: "打包库、分类包、文档与版权材料，保持 Java、客户端和 SSR 构建一致。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/deploy/release.mjs
  - inertia-java/deploy/documentation.mjs
  - inertia-java/deploy/runtime.mjs
verification:
  - inertia-java/deploy/verify-release.mjs
  - inertia-java/scripts/verify-maven-consumer.py
translation:
  locale: zh-CN
  canonicalId: deployment/build-release
  source: deployment/build-release.md
  sourceRevision: 3382577c8bf201fc2fa9d4af656954e237d3f84e04ca90fd638163e2821a521a
---

# 构建不可变发布物

在源码工作区之外打包一致的 Java/示例发布物。这生成本地制品；Maven 仓库发布与生产部署是独立操作。

## 构建输入

在 `inertia-java/` 运行 `./mvnw --batch-mode spotless:check verify`，再在示例前端运行 `npm ci`、`npm run typecheck` 和 `npm run build`。返回 `inertia-java/` 后执行：

```sh
node deploy/release.mjs /absolute/release-store
```

命令输出以 release ID 命名的发布目录。需要可执行应用、全部七个库的 binary/source/Javadoc 分类包、POM 和完整前端凭据。发布前会拒绝混合或缺失的前端清单。

## 内容与身份

目录包含 `app.jar`、客户端/SSR 输出、生产 package 输入、不可变客户端资源、发布内启动器、运维手册、文档/示例、systemd 模板和 Maven 暂存子树。私有文档工具、node_modules 和生成文档 HTML 被排除，站点是独立制品。

前端 build ID 覆盖客户端/SSR 字节；release ID 覆盖完整内容的规范清单。相同已构建输入产生相同内容 ID，但不保证 Maven/Vite 编译逐位可复现。发布先暂存，再在同一文件系统重命名。相同已有发布会复用；已有字节损坏则拒绝，不覆盖。

## 启动前检查

在输出发布目录运行 `node runtime.mjs check`。进入 `frontend/` 使用 `npm ci --omit=dev --ignore-scripts --no-audit --no-fund` 安装锁定生产依赖，再应用可信只读部署策略。

不要把日志或可变应用文件放进发布目录，精确清单会拒绝额外文件。已安装前端依赖不参与该清单，因此预检不是第三方字节证明。哈希证明内容完整，不证明发布者签名或授权。

Maven 子树含父/模块 POM 与分类包，但不是经过签名、元数据完整的公开快照仓库。发布前查看[发布策略](../community/releases.md)，运行独立部署/消费验证后，继续[进程设置](processes.md)。
