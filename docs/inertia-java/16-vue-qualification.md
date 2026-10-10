# R4 Vue 本地资格记录

日期：2026-10-10。范围：原路线图R4的首个Vue示例；已完成完整本地验收。

## 实现

新增 `examples/spring-vue`，使用公共starter、ViteBuild/ViteAssets和HttpSsrGateway。业务应用定义Users/About/Feed及表单，协议、props解析、session投递、错误与SSR传输仍属于现有库。示例不复制Java协议，也不依赖React业务代码。

客户端锁定Inertia Vue/Vite3.8.0、Vue/server-renderer3.5.43、Vite8.3.3、TypeScript5.9.3、vue-tsc3.3.12、Node22.22.2。TypeScript与React隔离：实际发现vue-tsc无法加载TypeScript7的旧编译器入口，因此保留兼容版本，不修改React工具链。

生产client/SSR入口共用显式组件注册表。官方适配器负责每请求创建SSR应用与Page渲染；浏览器按data-server-rendered选择hydration或CSR挂载。build receipt和资源发布逻辑抽到内部共享函数，React仅保留调用入口，已有asset publisher合同继续使用。

## 实际覆盖

- 初始HTML包含服务端渲染Ada，hydration无控制台警告；deferred真实请求返回统计值。
- partial与optional真实重载；普通导航保持同一document，表单校验、CSRF和flash跨redirect后消费。
- Feed实际prepend/append形成9条有序数据；reset替换列表；官方client发送once exclusion、响应不重复返回catalog，显式刷新执行新查询。
- 停止Node后Java返回CSR且Vue完成挂载/deferred；重新启动Node恢复SSR，不重启Java。
- 实际Node未知页面/版本拒绝及篡改bundle拒绝通过；这两项分别执行，不由React实现推导。

独立Maven消费复制Vue应用到临时目录，禁用relative parent，使用只从候选repository解析的八库及classifiers；实际可执行JAR交给同一浏览器驱动。消费者不使用reactor target/classes或Vue示例本地JAR。前端通过锁定依赖构建；验证记录保存构建身份、JAR及driver摘要。

## 复现

用户运行步骤见[英文指南](../../inertia-java/docs/getting-started/vue.md)和[中文指南](../../inertia-java/docs/zh/getting-started/vue.md)。工程命令见[示例说明](../../inertia-java/examples/spring-vue/README.md)。使用Java21与配套Playwright Chromium。

完整aggregate27阶段通过，包含305项标准Java测试、历史候选API比较、六项制品拒绝合同、React回归、Vue依赖安装/类型检查/build，以及隔离consumer后的Vue七阶段真实浏览器链路。八库24个制品检查通过。71英文/71中文站点check/build和14阶段本地browser通过；React共享构建输出仍为R3同一build ID。

React与Vue各自的依赖清单另行通过。实际发现npm production SBOM遗漏直接依赖，因此保留其原始输出，生产范围改用官方npm ls --omit=dev完整安装图；Vue记录30个生产图组件、82个全平台lock组件和58个当前安装包，React记录10/81/38。该清单是声明与文件证据，不是发布许可。

机器结果见[iteration-r4.json](verification/iteration-r4.json)，[原始摘要归档](verification/iteration-r4-receipts.json.gz)包含最终工具结果；[实际页面截图](verification/vue-users.png)保留本地浏览器产物。

## 限制

仅声明已验证的生产构建路径和锁定Chromium组合。Vite开发模式、Vue自动发布启动器、Redis宿主部署、其他浏览器/依赖组合另行验证。WebFlux设计见[ADR004](decisions/004-webflux-adapter-boundary.md)，Svelte单列条件性工作包；当前没有这两个适配器实现。
