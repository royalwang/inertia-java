# 下一轮迭代实施台账

日期：2026-10-10。对应[路线图](11-next-iteration-roadmap.md)。整体状态：实施中；按R1→R2→R3推进，R4按路线图的采用需求决定。

## R1：候选与独立消费

- 候选政策见[ADR 001](decisions/001-candidate-distribution.md)：保留本地 `io.inertia` 坐标，公共namespace待发布前确认；保持Java包和SNAPSHOT，不凭空创建正式版本。
- parent POM项目/SCM四项指向canonical `royalwang/inertia-java`，制品检查同步保护该元数据及子模块parent身份。
- README纠正私密报告渠道及文档状态，区分本地可验收和公开发布。
- Boot默认配置增加真实自动装配回归：props 3秒、response 5秒、并发8、pool 8/32/256、allErrors未覆盖、namespace default。
- 公共API生命周期审查见[ADR 002](decisions/002-public-api-baseline.md)，冻结71个公开/受保护类型签名并保留所有原接口。通过真实japicmp 0.26.2检查本地七模块候选及完整依赖，差异工具不忽略缺失类。
- 292项Java测试通过，0失败/错误/跳过；六项制品拒绝合同通过。独立Maven consumer完成二进制/source/Javadoc消费、canonical Java示例、缺失core拒绝与恢复；独立教程实际SSR/JSON/hydration/validation/flash/导航/Node停机CSR通过。
- 文档check通过，英文/中文正文修订未变；没有宣称本轮重新验证公开站点。
- R1本地交付完成，机器证据见[iteration-r1.json](verification/iteration-r1.json)。公开namespace未验证，实际发布留待独立流程。

## R2：公共API变更工具

新增 `scripts/api-compatibility.py` 的snapshot/compare入口与工程用法；候选复制完整compile依赖并验证各JAR摘要，归档可移位，不依赖公共旧版本。工具只在显式提供本地jar时运行，Maven元数据校验固定版本。真实编译fixture验证：实现变化与新增方法允许，删除公共方法/受保护方法拒绝，新增checked exception源码不兼容被拒绝且binary-only检查仍允许，篡改baseline拒绝。

aggregate支持通过 `INERTIA_API_BASELINE` / `INERTIA_API_TOOL` 显式启用合同、当前候选收集与差异门槛；未提供baseline时沿用原运行验证，不伪称完成API差异检查。本轮已验证各组成命令和脚本语法，完整aggregate新增分支将在后续运行验收中执行。当前七模块工具smoke使用同一候选自比较，不冒充真实旧正式版本比较。

## 后续范围

R2的API兼容工具、故障诊断与容量复测，R3的Redis原子投递及双实例故障验收尚未完成。公共发布与GitHub部署不作为阻塞项；不会将本台账阶段进展冒充整个路线图完成。
