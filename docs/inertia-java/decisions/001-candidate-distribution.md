# ADR 001：候选制品身份与本地交付

日期：2026-10-10。状态：采用本地候选政策；公共namespace尚未确认。

## 决策

保留现有 `io.inertia:*:0.1.0-SNAPSHOT` 本地消费坐标和 `io.inertia.*` Java包，不在缺乏namespace权属证据时宣称可发布Maven Central。canonical项目和SCM统一为 `https://github.com/royalwang/inertia-java`，Java源码仍位于 `inertia-java/`。

不创建虚构稳定tag，不把SNAPSHOT改成正式版本来通过验收。本地候选由源码commit、工作区状态、输入/制品摘要以及验证记录标识；SNAPSHOT版本号相同不等于制品相同。

公共发布前确认 `io.inertia` 权属；如不可用，再选择维护者可证明拥有的namespace并统一更新parent、子模块、发布目录、consumer、教程和文档。Maven坐标迁移与Java包重命名分别评估，不自动捆绑。发布凭据和namespace审批不阻塞本地实现。

## 版本与兼容性

当前候选以Java21/Boot3.5.7及已锁定React/Node组合为支持基线。不为R1同步升级依赖。公共签名、构造器、默认配置和生命周期行为均作为后续兼容性审查输入；破坏性变化需要明确迁移，不能只更新基线文件消除差异。

稳定版本按真实发布流程确定。修复版保持公共签名、配置默认值和已支持协议行为；新增可选能力使用minor里程碑规划。0.x不等于可以省略迁移说明，SemVer也不代表工具能验证所有行为兼容性。

## 发布说明与迁移模板

每个候选/版本记录：

1. 源码身份、制品版本和坐标、相对上一候选的变化。
2. Added / Changed / Deprecated / Fixed，分别说明触发条件及应用可见结果。
3. 公共签名、默认值、配置归属和协议变化；无变化也应明确记录。
4. 迁移前后用法、受影响应用、回滚和数据兼容边界。
5. JDK/Boot/客户端/Node组合、实际验证命令与本地结果。
6. 已知限制、未验证范围与独立发布状态。

R1候选当前变化：统一canonical项目/SCM；纠正README安全报告状态；新增默认配置回归与候选API审查。Java包、Maven坐标、运行时默认值和协议未变。

## 验收证据

`verify-library-artifacts.py`核对canonical URL/SCM、parent坐标和binary/source/Javadoc内容；`verify-maven-consumer.py`在仓库外从独立Maven制品目录启动应用。Boot默认值测试实际启动自动装配并检查执行器配置。R1/R2 API工具的输入是本地候选归档，不要求公共仓库已有旧正式版本。

参考：[Central namespace规则](https://central.sonatype.org/register/namespace/)；本决策未申请namespace、上传制品或核实公开部署。
