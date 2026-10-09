---
title: "构建与资源版本"
description: "统一 Java、manifest、客户端和渲染器的构建身份，并刷新旧客户端。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-vite/src/main/java/io/inertia/vite/ViteBuild.java
  - inertia-java/examples/spring-react/frontend/scripts/build.mjs
  - inertia-java/deploy/release.mjs
verification:
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
translation:
  locale: zh-CN
  canonicalId: concepts/versioning
  source: concepts/versioning.md
  sourceRevision: 2d89efc0f220c2c7ffd1b878fc20300f0f6b0e10519501dcbc6b57ef43428825
---

# 构建与资源版本

协议版本标识客户端可见的构建。示例用一个 SHA-256 build ID 覆盖客户端及 SSR 文件清单，任一端变化都会改变 Page 版本。

## 三种身份

| 身份 | 用途 |
| --- | --- |
| Maven 版本，目前为 `0.1.0-SNAPSHOT` | Java 库与应用制品坐标 |
| 前端 build ID | 一致的客户端/SSR 构建凭据和 Page 版本 |
| 部署 release ID | 完整不可变部署内容，包含 Java、文档和 Maven 制品 |

这些身份相互关联，但不可互换。仅修改文档可能产生新的部署 release ID，而不改变前端 build ID。SHA-256 完整性验证字节，不证明发布者真实性或远端授权。

## 一起构建

示例构建脚本生成客户端与 SSR 输出，将哈希记录到 `dist/build.json`，并把客户端资源发布到 `.inertia/assets/<buildId>/`。生产 Java 加载 `ViteBuild`、验证客户端资源存储，并使用 build ID 作为版本来源。Node 根据同一凭据验证自己的 SSR bundle。

不能将使用某份凭据的 Java 实例与无关 Node/客户端 bundle 混合。生产网关检查响应的 build/root 身份。混合输出会导致验证或 SSR 验收失败，不会静默视为一致发布。

## 旧浏览器会发生什么

Inertia GET 携带客户端当前版本。如果与服务端当前版本不同，预检会在业务执行之前返回刷新结果，官方客户端重新加载文档以取得新构建。

部署期间应保留旧的不可变资源，供正在执行或此前已加载的文档使用。发布切换前打开的页面仍可能引用旧 chunk URL。即使新版本内部一致，立即删除这些文件仍会破坏旧浏览器页面。

[部署手册](https://github.com/royalwang/inertia-java/blob/main/inertia-java/deploy/README.md)介绍资源保留与发布切换。保留策略由运维负责，库不会发现活跃浏览器标签页或回收其依赖。

## 开发与文档版本

开发 hot 模式根据资源状态产生自己的版本。生产忽略 hot 文件，依赖不可变凭据。不要把开发 origin 当作生产构建身份。

本文档描述当前源码快照。版本选择器应指向实际 release tag 生成的文档；不能把无 tag 的快照改称稳定发布。参见[分发状态](../getting-started/compatibility.md)。
