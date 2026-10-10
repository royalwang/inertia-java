# ADR 002：公共 API 与配置基线

日期：2026-10-10。状态：已审查当前候选，二进制/源码差异交由japicmp；行为由独立合同保护。

## 审查结果

保留当前构造器、方法、package、默认值及单节点session语义，不为本轮整理修改运行时API。全量公开/受保护签名快照见 `inertia-java/compatibility/api-baseline.json`；原始候选JAR与完整依赖副本存入仓库外目录，manifest记录摘要。签名文本用于审阅，不自行充当Java兼容性算法。

| 边界 | 创建/复用与所有权 | 取消/关闭/失败后的动作 | 验证入口 |
| --- | --- | --- | --- |
| InertiaConfig / RootView | 应用配置，component集合复制，callback/gateway引用保留；应用保证共享callback安全 | 无close；root view失败沿render失败路径，不吞业务错误 | core render/config测试、canonical CoreApiExample |
| InertiaContext | 每个请求创建，Page与redirect均只终结一次；不得跨请求复用 | abort终止并恢复reservation；late写入拒绝；失败后不复用旧context | SessionContractTest、SessionFailureTest、MVC lifecycle测试 |
| Prop / Props | 定义及fluent配置不可变；supplier和捕获的可变业务对象由应用管理 | 只调用选中的provider；底层IO取消尽力而为；once不是权限/服务端缓存 | PropsResolver测试、OnceTtlContractTest、RustParityTest |
| PropsResolver | 应用级复用；调用拥有独立预算及输出；应用拥有executor | timeout/fatal/cancel停止调用所属工作；不关闭调用方executor | resolver timeout/concurrency测试、Boot executor替换测试 |
| SessionStore | 应用提供身份隔离域；Delivery JSON复制；服务端投递事务 | begin/merge失败原子；complete/abort最多一次；未知结果不盲重试；不能保证浏览器接收 | memory/HttpSession及failure合同；Redis留待R3 |
| HttpSsrGateway | 应用级复用，池化客户端；endpoint来自可信配置 | 无close契约；不重试/不转发浏览器凭据；单次future取消不影响其他render | ssr-http传输/取消/大小上限测试 |
| SsrHealthMonitor | 独立调度生命周期，由创建者关闭 | 显式close，不能误以为gateway具有相同接口 | health monitor测试、实际Node health验证 |
| ViteManifest / ViteBuild | 构建产物的只读快照；每版本创建、请求间复用 | 不监听文件；应用发布时切换一致版本；manifest hash不替代全build身份 | vite manifest/build合同、release-switch验收 |
| MVC / Boot | Servlet同步未包装返回值；普通REST保留Spring行为；条件bean可替换 | 应用advice优先；error context独立；应用executor由宿主生命周期管理 | AutoConfiguration/Overrides/NonInertiaTransfer/MVC测试 |
| InertiaObserver | 可选同步callback，默认NOOP；共享sink须及时返回 | 隔离RuntimeException，不吞fatal Error；无异步buffer；不写敏感标签 | InertiaMetricsTest与core observer测试 |

上述审查基于当前源码Javadoc和实现，不表示所有外部provider都线程安全，也不把SDK失败回滚等同HTTP响应已被浏览器接收。

## 配置冻结

Boot默认值：propsTimeout=3s、responseTimeout=5s、propsConcurrency=8、executorCoreSize=8、executorMaxSize=32、executorQueueCapacity=256、allErrors=null、sessionNamespace=default。`candidateDefaultsRemainStableWhenNoPropertiesAreSet`从实际自动装配读取属性及executor，保护绑定默认值而非只比较文本。

InertiaConfig的11个配置字段由现有API map与源码Javadoc保护，回调、gateway和root view没有适用于所有应用的可序列化“默认对象”。public配置新增/默认值变化需要迁移说明、API map及中英指南同步，不用一个签名兼容结果替代行为审查。

## 工具选择

采用japicmp 0.26.2而非自行解析签名判定兼容。`scripts/api-compatibility.py`仅负责收集本地制品、复制完整依赖、验证摘要并调用工具；同时启用binary和source失败开关，包含protected扩展点，缺失依赖不忽略。Revapi不再另行接入，避免维护两套重叠检查。

候选baseline必须显式生成，既有文本基线拒绝直接覆盖。后续候选与保存的旧JAR比较；新增方法可允许，删除公共/受保护方法和增加checked exception会失败。具体工具限制仍需人工评审泛型/反射/序列化/行为变化。

参考：[japicmp官方源码与用法](https://github.com/siom79/japicmp)。版本固定在脚本，工具只用于工程验证，不进入Java库依赖。
