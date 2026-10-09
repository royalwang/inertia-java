# Java README 专题迁移对应

日期：2026-10-09。工程记录，不进入公共站点侧栏或搜索。

README已收敛为产品/安装/运行/模块/边界入口。44个原专题全部映射到已完成的公共指南/参考，并保留原级别标题及GitHub锚点。迁移前517行原文按字节保存在 [历史文本快照](verification/readme-before-topic-migration.txt)；它包含当时的状态和链接，不是当前规范。

机器对应及历史文本SHA-256见 [readme-topic-migration.json](readme-topic-migration.json)。对应检查证明标题/锚点/目标及旧字节保留，不把链接存在等同逐句语义或所有命令重新实测。公开指南已经按D1–D4任务结构编写并经过来源/构建/浏览器验收；旧文中细粒度API契约同时由完整Javadoc继续维护。

## 对应清单

| 原专题 / 锚点 | 当前公共文档 |
| --- | --- |
| Documentation / `documentation` | [index](../../inertia-java/docs/index.md)、[README](../../inertia-java/docs/README.md) |
| API guide / `api-guide` | [api-guide](../../inertia-java/docs/api-guide.md) |
| Observation SPI / `observation-spi` | [observability](../../inertia-java/docs/integrations/observability.md)、[metrics](../../inertia-java/docs/reference/metrics.md) |
| Prop definitions and overrides / `prop-definitions-and-overrides` | [basics](../../inertia-java/docs/props/basics.md)、[diagnostics](../../inertia-java/docs/props/diagnostics.md) |
| Build / `build` | [installation](../../inertia-java/docs/getting-started/installation.md)、[quick-start](../../inertia-java/docs/getting-started/quick-start.md) |
| Run the example / `run-the-example` | [quick-start](../../inertia-java/docs/getting-started/quick-start.md) |
| Development mode / `development-mode` | [development](../../inertia-java/docs/getting-started/development.md) |
| Browser verification / `browser-verification` | [browser-tests](../../inertia-java/docs/testing/browser-tests.md) |
| Modules and ownership / `modules-and-ownership` | [project-structure](../../inertia-java/docs/getting-started/project-structure.md)、[ownership](../../inertia-java/docs/concepts/ownership.md) |
| Execution configuration / `execution-configuration` | [configuration](../../inertia-java/docs/reference/configuration.md)、[async-concurrency](../../inertia-java/docs/props/async-concurrency.md) |
| Current boundaries / `current-boundaries` | [compatibility](../../inertia-java/docs/getting-started/compatibility.md)、[custom-session](../../inertia-java/docs/integrations/custom-session.md)、[releases](../../inertia-java/docs/community/releases.md) |
| Validation and CSRF / `validation-and-csrf` | [forms-validation](../../inertia-java/docs/guide/forms-validation.md)、[csrf](../../inertia-java/docs/guide/csrf.md) |
| MVC diagnostics and error pages / `mvc-diagnostics-and-error-pages` | [errors](../../inertia-java/docs/guide/errors.md)、[spring-api](../../inertia-java/docs/reference/spring-api.md)、[startup](../../inertia-java/docs/troubleshooting/startup.md) |
| Session namespaces and failures / `session-namespaces-and-failures` | [flash-session](../../inertia-java/docs/guide/flash-session.md)、[custom-session](../../inertia-java/docs/integrations/custom-session.md) |
| Vite and SSR endpoint contracts / `vite-and-ssr-endpoint-contracts` | [vite-assets](../../inertia-java/docs/ssr/vite-assets.md)、[gateway](../../inertia-java/docs/ssr/gateway.md) |
| Renderer failure acceptance / `renderer-failure-acceptance` | [fallback](../../inertia-java/docs/ssr/fallback.md)、[browser-tests](../../inertia-java/docs/testing/browser-tests.md) |
| Background renderer health and watch / `background-renderer-health-and-watch` | [health](../../inertia-java/docs/ssr/health.md) |
| Release build integrity / `release-build-integrity` | [build-release](../../inertia-java/docs/deployment/build-release.md)、[assets-versions](../../inertia-java/docs/troubleshooting/assets-versions.md)、[browser-tests](../../inertia-java/docs/testing/browser-tests.md) |
| Renderer release verification / `renderer-release-verification` | [build-release](../../inertia-java/docs/deployment/build-release.md)、[gateway](../../inertia-java/docs/ssr/gateway.md)、[browser-tests](../../inertia-java/docs/testing/browser-tests.md) |
| Request CSP nonces / `request-csp-nonces` | [root-template](../../inertia-java/docs/ssr/root-template.md)、[proxy-security](../../inertia-java/docs/deployment/proxy-security.md)、[browser-tests](../../inertia-java/docs/testing/browser-tests.md) |
| Versioned assets and release switching / `versioned-assets-and-release-switching` | [rolling-upgrades](../../inertia-java/docs/deployment/rolling-upgrades.md)、[vite-assets](../../inertia-java/docs/ssr/vite-assets.md) |
| Custom mount root / `custom-mount-root` | [root-template](../../inertia-java/docs/ssr/root-template.md)、[configuration](../../inertia-java/docs/reference/configuration.md) |
| Browser history and per-page SSR / `browser-history-and-per-page-ssr` | [history-bigint](../../inertia-java/docs/guide/history-bigint.md)、[rendering](../../inertia-java/docs/concepts/rendering.md) |
| Aggregate verification and CI / `aggregate-verification-and-ci` | [browser-tests](../../inertia-java/docs/testing/browser-tests.md)、[contributing](../../inertia-java/docs/community/contributing.md) |
| Cancellation and worker ownership / `cancellation-and-worker-ownership` | [async-concurrency](../../inertia-java/docs/props/async-concurrency.md)、[ownership](../../inertia-java/docs/concepts/ownership.md) |
| Local HTTP performance baseline / `local-http-performance-baseline` | [performance](../../inertia-java/docs/troubleshooting/performance.md) |
| Independent release directory / `independent-release-directory` | [build-release](../../inertia-java/docs/deployment/build-release.md)、[processes](../../inertia-java/docs/deployment/processes.md)、[operations](../../inertia-java/docs/deployment/operations.md) |
| Example CSRF recovery / `example-csrf-recovery` | [csrf](../../inertia-java/docs/guide/csrf.md) |
| Page URL and shared-key presentation / `page-url-and-shared-key-presentation` | [shared-data](../../inertia-java/docs/guide/shared-data.md)、[core-api](../../inertia-java/docs/reference/core-api.md) |
| HTTP policy compatibility / `http-policy-compatibility` | [protocol](../../inertia-java/docs/concepts/protocol.md)、[compatibility](../../inertia-java/docs/getting-started/compatibility.md) |
| Once expiry and invalidation / `once-expiry-and-invalidation` | [once](../../inertia-java/docs/props/once.md)、[fixtures](../../inertia-java/docs/testing/fixtures.md) |
| Library documentation artifacts / `library-documentation-artifacts` | [javadoc](../../inertia-java/docs/reference/javadoc.md)、[releases](../../inertia-java/docs/community/releases.md) |
| Required SSR pages / `required-ssr-pages` | [fallback](../../inertia-java/docs/ssr/fallback.md)、[errors](../../inertia-java/docs/reference/errors.md) |
| Independent Maven consumer rehearsal / `independent-maven-consumer-rehearsal` | [browser-tests](../../inertia-java/docs/testing/browser-tests.md)、[releases](../../inertia-java/docs/community/releases.md) |
| Application exception pages / `application-exception-pages` | [errors](../../inertia-java/docs/guide/errors.md)、[spring-api](../../inertia-java/docs/reference/spring-api.md) |
| Application exception redirects / `application-exception-redirects` | [errors](../../inertia-java/docs/guide/errors.md)、[flash-session](../../inertia-java/docs/guide/flash-session.md) |
| Custom codec and adapter overrides / `custom-codec-and-adapter-overrides` | [spring-boot](../../inertia-java/docs/integrations/spring-boot.md)、[custom-adapter](../../inertia-java/docs/integrations/custom-adapter.md) |
| Opt-in local identity demonstration / `opt-in-local-identity-demonstration` | [authentication](../../inertia-java/docs/guide/authentication.md)、[history-bigint](../../inertia-java/docs/guide/history-bigint.md) |
| Dependency and license declaration inventory / `dependency-and-license-declaration-inventory` | [license](../../inertia-java/docs/community/license.md)、[releases](../../inertia-java/docs/community/releases.md) |
| Advanced props and named forms / `advanced-props-and-named-forms` | [merging](../../inertia-java/docs/props/merging.md)、[forms-validation](../../inertia-java/docs/guide/forms-validation.md) |
| API guide verification in CI / `api-guide-verification-in-ci` | [browser-tests](../../inertia-java/docs/testing/browser-tests.md)、[contributing](../../inertia-java/docs/community/contributing.md) |
| SSR input contract / `ssr-input-contract` | [gateway](../../inertia-java/docs/ssr/gateway.md)、[ssr-vite-api](../../inertia-java/docs/reference/ssr-vite-api.md) |
| Development acceptance / `development-acceptance` | [development](../../inertia-java/docs/getting-started/development.md)、[browser-tests](../../inertia-java/docs/testing/browser-tests.md) |
| License / `license` | [license](../../inertia-java/docs/community/license.md) |

## 内容与验收边界

- 浏览器专项命令、aggregate/Maven consumer和benchmark参数已迁入对应testing/performance正文，不再要求读者从长README寻找入口。
- SSR输入守卫、Jakarta节点/会话语义及Java/React依赖清单操作补入当前参考；旧README两个Javadoc链接的HTTP404记录保留，当前参考使用已实际返回200的大小写及.html路径。
- api-guide.md、两份canonical Java示例和部署/兼容runbook的原路径保持。没有删除原技术内容来掩盖迁移缺口；历史文本单独保留，公共页面是当前学习真源。
- 新入口仍为snapshot，本地通过不冒称公共站或远端Actions成功。此次是文档迁移，没有改变运行实现或重跑未变更的runtime/性能门槛。
