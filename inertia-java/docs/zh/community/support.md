---
title: "获取帮助与报告问题"
description: "提供包含版本和行为的最小复现，避免公开敏感数据。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/README.md
  - inertia-java/docs/getting-started/compatibility.md
verification:
  - inertia-java/scripts/verify.mjs
translation:
  locale: zh-CN
  canonicalId: community/support
  source: community/support.md
  sourceRevision: d295430090446b1c891e78c60e485dde4fc4a329a6150333a11fca42d83ef248
---

# 获取帮助与报告问题

提供经过脱敏的最小复现，并明确问题所在层。当前文档对应 `0.1.0-SNAPSHOT`；这不代表已有公开稳定制品，也不构成支持服务等级承诺。

## 提供有效证据

说明库版本或源码修订、Java、Boot、Node 和官方客户端版本、操作系统、SSR/CSR 模式以及完整的复现命令。列出预期行为与实际行为，并提供能展示问题的最小控制器、配置或应用。附上 HTTP 状态、脱敏后的相关协议头、浏览器控制台或网络错误，以及第一个可采取行动的错误信息。

SSR 问题应包含构建标识、根目录标识和回退类别。会话问题应说明请求顺序、错误包或命名空间，以及身份是否已失效；不要提供真实 Cookie、令牌或含个人信息的 Page 数据。部署问题应区分本地演练与实际代理或主机上的结果。

## 选择当前可用的渠道

规范仓库为 [royalwang/inertia-java](https://github.com/royalwang/inertia-java)。非敏感复现可通过仓库当前开放的公开贡献或讨论功能提交。该仓库是一个 fork，编写文档时未显示公开 Issues 入口，因此这里不提供假定可用的 `/issues/new` 地址。如果能够提出修改，可以提交附带失败复现的聚焦拉取请求。

此处未声明独立的支持邮箱、论坛、付费支持协议或响应期限。维护者可随仓库设置变化补充正式渠道。

## 发布前检查

先搜索现有文档和公开报告，尝试对应的[排错指南](../troubleshooting/startup.md)，并核对工具链和构建输入。日志应精简且清除密钥。疑似漏洞应遵循[安全报告流程](security.md)，不要在公开审查中披露利用细节或敏感复现。

只有错误截图不足以构成完整报告。可复现的输入、预期行为和明确的失败边界，才能支持进一步调查。
