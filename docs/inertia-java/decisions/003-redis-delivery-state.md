# ADR 003：Redis delivery 的原子状态与失败边界

日期：2026-10-10。状态：设计采用，待真实实现与双实例验收；不能据本ADR声明Redis功能已经完成。

## 目标和模块

新增可选 `inertia-session-redis`，通过Spring Data Redis复用连接、命令与序列化设施。core不依赖Redis或Spring。现有SessionStore/Delivery方法及UUID token保持不变；Redis内部保存epoch/revision/lease。MVC需要可替换的request→SessionStore工厂，默认仍创建HttpSessionStore，Redis选择必须显式启用。

宿主身份管理继续使用HttpSession/Spring Session。原子delivery状态单独存储；不能把Java session mutex或普通Redis session attribute的last-write-wins当作原子事务。domain key由可信宿主session身份和应用namespace形成，单个domain位于同一Redis key/hash slot。不得接受浏览器提供任意key或将session ID写入日志/指标。

## 表示与原子性

采用有大小/条目上限的单domain JSON envelope，包含schemaVersion、epoch、递增revision、revoked、available、reserved及有界终态记录。JSON嵌套值保持Jackson语义，尤其空数组、空对象、大整数和error bags，不交给Redis Lua cjson重编码。

每次操作读取状态与Redis服务端时间，在Java中验证并计算新envelope，通过Lua CAS比较完整旧字节后一次替换。脚本只比较/存储opaque字符串并设置TTL。revision每次变化避免ABA；并发冲突只有在Redis明确返回“没有执行写入”时才可有限重算，设置冲突次数与操作预算上限。网络超时及断连属于未知结果，不自动重放写入或回退本机store。

begin把available移入UUID对应reserved，并发布空available；返回defensive-copy Delivery。complete校验domain epoch/token后只终结对应reserved；abort以reservation为旧值、available为新值恢复并终结token。merge与abort的JSON优先级复用现有MemorySessionStore语义，不另写一个近似error-bag合并器。所有解码、大小检查、非法error-bag检查在CAS前完成，失败不能部分更新状态。

## Lease、撤销与身份轮换

reservation具有明确lease。恢复过期reservation时，恢复与旧token失效必须在同一次CAS提交；旧请求不得再次complete。清理可以在后续操作执行，也提供显式维护入口；不是声称Redis会自行完成Java合并逻辑。多个过期reservation的恢复顺序必须固定并与优先级合同验证。

store实例绑定创建时的epoch；domain失效使epoch变化并撤销旧token。撤销记录保留为tombstone，不能直接DEL后允许旧请求重建同一身份域。会话销毁/轮换需要宿主事件或显式factory调用；Boot/MVC自动装配必须验证相应生命周期，不仅演示手动调用。

session idle TTL、lease、终态保留及最大请求寿命形成明确的约束。Redis服务端时间用于判断lease，检测到时间倒退时失败关闭；不依赖多个JVM时钟同步来作仲裁。CAS必须拒绝过期读结果，避免长暂停客户端重新提交已失效reservation。每条状态转换由真实Redis合同验证，而不是只有mock测试。

## 容量与故障

配置包括namespace、domain TTL、reservation lease、终态保留、最大envelope字节、最大reservation数和CAS冲突预算。达到限制要显式报错，无无限队列、无限token记录或隐式空delivery。旧epoch、重复/未知token、已撤销domain、容量和未知存储结果有可区分失败。

Redis异步复制/failover可能丢失已确认写入。本模块不宣称跨failover exactly-once，更不能宣称浏览器收到一次。真实failover资格在选定拓扑后另行验证；首批本地资格覆盖明确的单Redis事务域、双JVM竞争、进程退出和网络故障。restart/数据丢失时不得恢复伪造成功。

## 需要证明的出口

1. memory/servlet/Redis的共同事务与JSON合并合同。
2. 真实Redis上的并发CAS、竞争complete/abort、重复/伪造token、lease恢复及晚到请求拒绝。
3. 两个独立JVM通过同一可信session身份完成HTTP redirect→flash/errors领取，并验证namespace隔离。
4. 宿主session轮换/失效后旧store与旧token被拒绝；新身份正常工作。
5. 网络未知写入、进程崩溃、Redis重启、TTL/容量/非法输入的失败和恢复。
6. 可选模块/自动装配的独立Maven消费、默认单节点回归、中英文文档与边界说明。

本机Redis/容器运行方式在验收启动时明确记录，不使用共享或生产实例。容器运行时不可用不能把mock当作Redis完成；继续准备可复现本地启动方式与其真实运行证据。
