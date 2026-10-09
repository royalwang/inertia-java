---
title: "报告安全问题"
description: "通过 GitHub 私密报告漏洞，并准备经过脱敏的安全报告。"
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/README.md
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/SecurityConfiguration.java
  - inertia-java/scripts/dependency-inventory.py
verification:
  - inertia-java/examples/spring-react/src/test/java/io/inertia/example/BrowserCsrfRecoveryTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CspNonceTest.java
translation:
  locale: zh-CN
  canonicalId: community/security
  source: community/security.md
  sourceRevision: f70555ed4140ea5c60597ff988feb9eeeb9e1f65948f76b1dcbd5629580feeac
---

# 报告安全问题

不要在公开 Issue 或拉取请求中发布凭据、Cookie、包含个人信息的 Page 数据或敏感漏洞复现。

## 报告漏洞

通过 [GitHub 私密漏洞报告](https://github.com/royalwang/inertia-java/security/advisories/new)向仓库维护者提交报告。登录 GitHub，填写报告表单并私密提交。报告须知见[安全策略](https://github.com/royalwang/inertia-java/blob/main/inertia-java/SECURITY.md)。

敏感细节应保留在私密报告中，不要转发到公开 Issue 或拉取请求。

## 准备私密报告

说明受影响的源码或制品版本、信任边界、触发前提、影响以及经过脱敏的最小复现。描述可用的缓解措施，并说明相关信息是否已经公开。不要附带生产凭据或用户数据集；使用合成数据，并与维护者私下协调披露事宜。

## 应用的责任

本库不能替代认证、授权、CSRF 策略、受信代理配置、Cookie/TLS 策略或安全的会话后端。SSR 会收到解析完成的 Page 数据，必须作为受信的内部服务运行。请检查[认证](../guide/authentication.md)、[代理边界](../deployment/proxy-security.md)、[根模板与 CSP](../ssr/root-template.md)以及第三方依赖清单。

项目尚未声明稳定版本的安全支持和补丁回移期限。
