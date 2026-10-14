# 第 4 批设计说明（只读产出，未动代码）

> 配套文档：`docs/ARCHITECTURE_AUDIT_ZH_CN.md` §6。本文件收录各组设计说明与只读通读结论。每组都需要用户确认后才能动手。
> 状态：T4-3、T4-4/5、T4-6/7、T4-8 设计说明已返回并核实；两个大方块实体与三个执行核心类的通读已返回并核实。所有只读代理已完成。

---

## T4-3 执行模式与规划结果载体

### 1. 现状

**执行模式（4 类，语义并不同轴）**
- `ExecutionMode`（`planner/result/ExecutionMode.java`）：计划级，NATIVE / PHASED_DAG / ORDERED_CYCLE / DYNAMIC_CYCLE / BLOCKED。约 25 处引用。NBT 以 `name()` 持久化（`ECOExecutionPlanNbtCodec:31`，读取在 `:108`）。
- `ECOExecutionRequirement`（同包，127 行）：计划级分类 NONE / ORDERED / DYNAMIC / BLOCKED。约 20 处引用。与 ExecutionMode 基本一一对应，但 NONE 有两种去向：SUCCESS 且有组件时建成 PHASED_DAG 计划，否则为 NATIVE。`classify` 每次调用分配 HashMap、HashSet、IdentityHashMap。未持久化。
- `ECOExecutionSchedule.Type`（`ECOExecutionSchedule.java:40`）：阶段级 DAG / CYCLE / DYNAMIC_CYCLE。约 56 处引用。NBT 以名字持久化（`NbtCodec:151`）。
- `CycleExecutionDisposition`（15 行）：组件级，5 个值，其中 NOT_REQUIRED、STOCK_SATISFIED 不是执行态。约 26 处引用。未持久化。

审计说的"4 套"成立，但只有前两者是同一概念。

**状态枚举（不在本组范围）**：`PlanningStatus`（11 个常量）、`CyclePlanningStatus`、`CycleExternalDemandStatus`（含 OVERFLOW）、`CycleSolveStatus` 等均有 SUCCESS / UNSUPPORTED 类常量。

**规划结果载体**
- `ECOPlanningResult`（class，203 行）：独立持有 `planningId`、`executionRequirement`、`executionPlan`、`executionPlanError` 四个字段（`:32–35`）。`executionContract()`（`:191–202`）每次拼装合同。
- `ECOExecutionContract`（record，37 行）：`planningId`、`planSignature`、`mode`、`executionPlan`、`error`。main 中没有任何 `error` 读取，只有构造；`executable()`、`phased()` 无调用方。`mode` 与 `executionPlan.mode()` 重复，没有一致性校验。
- 注册表（`api/me/planning/ECOPlanningResultRegistry.java`）：
  - `SubmissionMetadata`（`:433`）、`activeSubmissionMetadata`（`:192`）、`RecoveredSchedule`（`:479`）、`recoverSchedule`（`:249`）：全仓零外部调用，是死代码。
  - `RecoveredExecutionMetadata`（`:472`）：只被 `resolveExecutionPlan` 使用。
  - `SubmissionAlias`（`:443`）、`Entry`（`:448`）：各自带 `planningId` 与 `executionPlan`。
- 字段名漂移：同一语义在三处叫 `error` / `executionPlanError` / `rejectionReason`。`rejectionReason` 是注册校验原因（如 SCHEDULE_EMPTY），不是计划错误，不能直接合并。
- `ExecutingCraftingJob.java:54` 只 import 了 `ExecutionMode`，未使用。

**关于一处"不一致"的复核（设计代理报告，已由总策划核实）**：代理报告"非 SUCCESS 且含环时，requirement 为 ORDERED/DYNAMIC，但 `executionContract()` 返回 NATIVE"。核实 `resolveExecutionPlan`（`ECOPlanningResult.java:71–106`）后，这条路径**未复现**：
- ORDERED / DYNAMIC 下，若构建失败或阶段为空，都会设置 `error` 并把 requirement 置为 BLOCKED，合同随之返回 BLOCKED；
- `executionContract()` 只在 `executionPlan == null` 时返回 NATIVE，而这种情况只会出现在 requirement 为 NONE 时；
- 因此 `ECOExternalCpuSupport:26`（只看 `contract.mode() == NATIVE`）在这条路径上不会误放行。
- 仍需一条定向测试来锁定这个行为，再考虑合并。

### 2. 目标形态

- **保留**：`ExecutionMode`（唯一计划级模式，常量名不变）；`ECOExecutionSchedule.Type`（阶段级）；`CycleExecutionDisposition`（组件级，不并入 ExecutionMode）；`ECOExecutionContract`（唯一的规划结果交接载体）。
- **删除**：`ECOExecutionRequirement`。`classify` 改为 `ECOPlanningResult` 内的私有静态方法并返回 ExecutionMode；三个组件判断方法迁为 `CycleExecutionDisposition` 的方法。另删注册表的 `SubmissionMetadata`、`RecoveredSchedule`、`activeSubmissionMetadata`、`recoverSchedule`，以及合同的 `executable()`、`phased()`。
- **改形**：`ECOPlanningResult` 只持有一个惰性字段 `ECOExecutionContract`，替代四个字段；`executionPlanError()` 与 `planningId()` 委托给它。注册表的 `RecoveredExecutionMetadata`、`SubmissionAlias`、`Entry` 把 `planningId` 与 `executionPlan` 收敛为合同引用，保留 `rejectionReason`。

### 3. 迁移步骤（每步可独立编译、测试）

- **S1 删死代码**：`ECOPlanningResultRegistry.java` 的 4 处；`ECOExecutionContract.java` 的两个方法；`ExecutingCraftingJob.java` 的死 import。测试：`ECOPlanningResultRegistryTest`、`ECOExternalCpuSupportTest`。
- **S2 合并 ECOExecutionRequirement**：删 `ECOExecutionRequirement.java`；改 `ECOPlanningResult.java`（classify、构造函数、resolveExecutionPlan）、`ECOExecutionSchedule.java`（3 处）、`ECOPlanningResultRegistry.java`（3 处）、`CycleExecutionDisposition.java`（加方法）。测试：`ECOPlanningResultRegistryTest`、`FeedbackRouteRegressionTest`、`UselessSmartDoublingPlansMixinTest`。
- **S3 ECOPlanningResult 收敛为合同字段**：`executionContract()` 改为读缓存，`executionPlanError()` 与 `planningId()` 委托。`ECOExactCraftingPlan.java:65`、`ECOCraftingPlannerService.java` 的调用点不变。
- **S4 注册表内部收敛**：`SubmissionAlias`、`Entry`、`RecoveredExecutionMetadata` 改存合同，删重复的 `planningId`。`CraftConfirmMenuMixin.java:832` 不变。

### 4. 行为与兼容

- **热路径**：`classify` 的一次性 HashMap 计算保持不变，它在规划时执行，不在每 tick。合同必须惰性生成。若构造 ECOPlanningResult 时即调用 `PlanIdentity.of(plan)`，每次规划会多一次签名计算。枚举本身无分配。
- **NBT**：ExecutionMode 与 Schedule.Type 的常量名不得改。ECOExecutionRequirement 未持久化，删除不影响存档。
- **网络**：已检索 packet / payload，这些类型都不在协议里，无变化。

### 5. 测试计划

- **S0 锁定测试（合并前）**：非 SUCCESS 且含环的结果，当前返回的 mode 与 cycleExpected；以及 ORDERED/DYNAMIC 构建失败时合同为 BLOCKED。
- **classify 表驱动**：NONE / ORDERED / DYNAMIC / BLOCKED 各一例，覆盖 CycleExecutionDisposition 的组合。
- **合同不变式**：BLOCKED 必须有 error；有阶段时必须有 plan；`contract.mode == plan.mode()`。
- **NBT 往返**：每个 ExecutionMode 与 Type 常量写读一致；用旧存档字符串样本验证可读。
- **现有回归**：`ECOPlanningResultRegistryTest`（约 36 处引用）、`ECOExternalCpuSupportTest`（约 29 处）、`ECOExecutionScheduleSccTest`、`ECOExecutionRuntimeDagStreamingTest`、`ECOExactOrderTest`。

### 6. 预计行数

删除约 90 行，新增约 15 行，净减约 75 行。估计值，未逐行核实。

### 7. 风险与需用户再决定的点

1. **合同是否需要单独表达"有环"**
   - A 保留现状：合同只用 mode 表达，cycleExpected 布尔保留在注册表中。行为零变化。
   - B 把"有环"编码进合同（如新增字段）：合同自洽，但会改变调用方读取方式，需补测试。
2. **"4 个执行模式枚举合一"的范围**
   - A（推荐）：只合并 ExecutionMode 与 ECOExecutionRequirement，保留 Schedule.Type 与 CycleExecutionDisposition。少一个类型，改动约 50 行。
   - B：四者全部并入一个枚举。需加入 NOT_REQUIRED、STOCK_SATISFIED 等非执行态，或改动持久化字符串（需双读兼容），约 80 处改动，语义混杂，不建议。
3. **求解状态枚举是否纳入 T4-3**：审计 §6 原文写"执行模式枚举、求解状态枚举各合一"，本设计只列执行模式。
   - A 不纳入：本设计成立。
   - B 纳入：需另出设计，涉及 `PlanningStatus`（11 个常量，有 UI 文案）与 cycle 包的 `CycleSolveStatus`。
4. **error 与 rejectionReason 是否合并**
   - A 保持分离：校验原因与计划错误各自保留，多一个字段，信息不丢。
   - B 合并进合同 error：字段少，但校验原因（如 SCHEDULE_EMPTY）会被当作计划错误对外暴露，改变诊断文字。

### 8. 不做的部分及理由

- 不把 ECOPlanningResult 整体并入合同：前者承载 trace、cycles、精确物料与诊断，合同只是执行交接，合并会让交接对象膨胀。
- 不改 ExecutionMode、Schedule.Type 的常量名：存档兼容。
- 不合并 CycleExecutionDisposition 与 ExecutionMode：两个非执行态会污染计划模式。
- 不改 classify 的溢出安全计数算法：属于正确性逻辑。
- 不处理状态枚举的重复常量（见第 7 节第 3 项）。

---

## T4-6 / T4-7 网络包与 mixin 业务

### 1. 现状（已核实）

- `ECOPayload`、`DeltaSyncChannel` 均不存在（源码无任何引用）。
- C2S 包：
  - `network/ECOBigOrderStartC2SPacket.java`，ID `neoecoae:big_order_start`；
  - `network/ECOForceCraftStartFlagC2SPacket.java`，ID `neoecoae:force_craft_start_flag`。
  - 两者线格式都是 `VarInt containerId, Boolean`。前三步校验（菜单类型、containerId、stillValid）相同，之后动作不同：BigOrder 调 `startBigOrder(forced)`，ForceFlag 调 `setForceCraftStart` 后 `startJob()`。"校验相同"只成立于前三步。
- 注册：`network/ECONetwork.java:18–23`；协议版本串 `"3/" + modVersion`（`:29`）。
- 发送方：`client/gui/crafting/ECOCraftConfirmScreen.java:364–376`（371 行发 BigOrder，375 行发 ForceFlag，由私有 `usesBigOrderSubmission` 决定）；`mixins/client/ae2/CraftConfirmScreenMixin.java:44–52` 始终发 ForceFlag。
- 节流增量同步：`TerminalExactAmountSync.collect`（19–46）与 `CraftingCpuMenuMixin.onBroadcastChanges`（约 185–210）是同一模式（节流 + busy + MapDelta + 首次全量）。`PatternPreviewSync.tick`（187–205）是脏槽位模型，只共享 `UPDATE_INTERVAL`。审计说"写了 3 遍"，实为 2 遍相同逻辑加 1 遍仅节奏相同。
- `ECOCancellation` 已是规划器取消参数类型。未复用的是 `ECOBigOrderPlanner.begin` 内的内联 lambda 与 `search` 循环检查，以及 `ECOPlanningService.checkpoint()`。`CraftingCalculationMixin.neoecoae$checkpointExternal` 语义不同。
- 会话样板：`ECOPlanningService.begin`（29–31）、`ECOBigOrderPlanner.begin`（15–17）、`CraftingCalculationMixin`（106–111）都是 `ECOPlannerInventory.capture(grid)` + `createSession(...)`，参数一致。
- 行数（已核实）：`CraftConfirmMenuMixin.java` **946 行**（审计写 969）；`CraftingServiceMixin.java` **814 行**（审计写 824）。
- `CraftConfirmMenuMixin` 的投影循环位于 `neoecoae$applyPlannerDiagnostics`（528–622）内的 554–620；辅助方法 `neoecoae$exactAmountFor`（880）、`neoecoae$materialFor`（887）只它一处使用。
- `CraftingServiceMixin` 集群遍历约 11 处（154、210、236、446、473、521、655、703、763、787、797）。

### 2. 目标形态

- 新包 `CraftSubmitC2SPacket`：`VarInt containerId`、`SubmitMode mode`（`STANDARD` / `BIG_ORDER` 枚举，符合第 10 条"不用 boolean 表示含义"）、`Boolean forced`。handle 先做共享前置校验，再按 mode 检查对应能力并分派。
- 客户端 `usesBigOrderSubmission` 抽为纯静态函数；AE2 原屏固定发 STANDARD。
- 投影抽为 `crafting/display/ECOCycleItemProjection.project(ECOPlanningResult, CraftingGraphSnapshot)`。
- `ECOCancellation` 增加常量 `INTERRUPTIBLE`，替换两处内联检查。
- 会话样板抽为 `ECOPlanningSessions.open(IGrid, AEKey, ECOPlannerOptions)`。

### 3. 迁移步骤

1. 新包与 `ECONetwork` 注册；删除两个旧包。
2. 改 `ECOCraftConfirmScreen` 与 `CraftConfirmScreenMixin` 的发送。
3. 投影纯移动（保持原写法）。
4. `ECOCancellation` 常量替换。
5. 会话助手。若 T4-5 同时把 `ECOBigOrderPlanner` 改为单例，先做 T4-5。
6. `CraftingServiceMixin` 非热路径遍历迁出。
7. 改写测试，交总策划统一编译。

### 4. 协议与兼容

- 旧线格式 `VarInt, Boolean`。新 ID 建议 `neoecoae:craft_submit`，线格式 `VarInt containerId, VarInt mode, Boolean forced`。不得复用旧 ID，否则旧布局数据会被错解或解码失败。
- 单机：客户端与服务端同一 jar，无兼容问题。
- 多人：协议由 `3/版本号` 协商，版本不同会在连接时被拒；同版本号的不同构建不会被拒。新线格式建议前缀改为 `4/`。
- 日志 tag 按 mode 保留 `[big-order-submit]` 与 `[craft-submit]`。

### 5. mixin 影响

- `CraftConfirmMenuMixin`：投影只抽方法体，注入点（startJob 的 742/792/854/867 行、broadcastChanges 的 WrapOperation）不变。`startBigOrder` 不移出，因依赖 result、`neoecoae$originalOptions` 等 mixin 状态。
- 时序约束：`CraftConfirmMenuForceStartMixin` 的 startJob HEAD 注入消费 pending 标记。STANDARD 路径必须保持 `setForceCraftStart` 先于 `startJob`。
- `CraftingServiceMixin`：注入点不变。`@Local` 捕获的 `latestChange`、`inserted`、`cpus` 必须留在 mixin；`@Unique` 字段 `neoecoae$computationClusters` 留在 mixin，助手以参数接收。注入依赖 AE2 的 LVT 局部名与方法描述符，移动不改变这些依赖。
- `CraftingCalculationMixin`：`<init>` RETURN 注入不变，只替换 106–111 样板。

### 6. 测试计划

- `network/CraftStartPacketTest` 改写为 `CraftSubmitPacketTest`：STANDARD 用 inOrder 钉住 `setForceCraftStart → startJob`；BIG_ORDER 验证 `startBigOrder(forced)` 且 never `startJob`；菜单不符、stillValid=false（closeContainer）、非 ServerPlayer 无副作用；能力缺失写 INCOMPLETE_PLAN。
- `network/ECONetworkVersionTest`：补新前缀断言。
- 新增 `CraftSubmitModeTest`：不可表示 → BIG_ORDER；forced + MISSING_ITEMS + 循环非空 → BIG_ORDER；其余 STANDARD。
- 新增 `ECOCycleItemProjectionTest`：纳入规则（totalNetOutputKnown 或 exactMissing>0）、seed 行、seedParallelism 缺省 -1、顺序。
- 新增 `CraftingServiceClusterTraversalTest`：mock NEComputationCluster，验证 onGetCpus 仅代表节点补 fake CPU。
- 应保持通过：`ECOPlanningServiceTest`、`ECOBigOrderPlannerTest`、`ExactMapSyncTest`、`PatternPreviewSyncTest`。

### 7. 预计行数（估算）

- 包：约 -28；`ECONetwork` 约 -2。
- 投影：`CraftConfirmMenuMixin` 约 -75，新类约 +85。
- `CraftingServiceMixin`：非热路径约 -25，助手约 +30。
- 会话助手 +12、三处 -8；`ECOCancellation` 约 -7。
- 合计约 -20 至 +10；两个 mixin 合计减少约 100 行。

### 8. 风险与需用户决定的点

1. **协议前缀**：改 `4/`，则同版本号的开发构建被拒（预期）；不改，则同版本不同构建可能静默错解。建议改。
2. **模式由谁决定**：
   - A 客户端决定（现状）：保持行为。
   - B 服务端决定（包只带 containerId + forced）：删除枚举，但要在服务端复刻 `usesBigOrderSubmission`；AE2 原屏点击在不可表示或缺循环时会改走大订单，属于行为变化。建议 A。
3. **DeltaSyncChannel**：
   - 建立：合并两站点，净行数约 0 至 +15，收益是一致性。
   - 不建立（建议）：只保留现有 MapDelta / ExactMapSync 共享。两站点 reset 语义不同（CPU 站点多 map，switched 时清空并取消通道），共享需要布尔或枚举参数。
4. **ECOPayload**：建立约 +20 行，只省 5 个 `type()` 覆写，净收益约 0。建议不建，只把菜单守卫抽为包内静态方法。
5. **日志 tag**：保留（可 grep）或统一为 `[craft-submit] mode=`（需确认无人依赖旧 tag）。
6. **取消异常文本**：统一为 "ECO planning request cancelled"，或两处各保留（只影响日志）。
7. **CraftingServiceMixin 遍历**：全迁，或只迁非热路径。建议只迁 describe、updateCPUClusters、findSuitable 等；tickClusters、onInsertIntoCpus、onGetRequestedAmount 留原位（第 8 条热路径规则）。

### 9. 不做及理由

- `ECOPayload` 通用基类：共同代码只有 `type()` 覆写，且注册 ID 须字面量（第 10 条）。
- `PatternPreviewSync` 并入增量通道：模型不同。
- `startBigOrder` 移出 mixin：依赖 mixin 状态，移动需大量参数。
- 循环改 Stream、为投影建索引：第 8 条禁止；索引属于另行评估的性能改动。

---

## T4-8 存储对齐 AE2

路径相对 `src/main/java/cn/dancingsnow/neoecoae/`。AE2 对照基于 19.2.18 源码。

### 1. 现状（已核实）

- **StorageInterfaceFilter**（`impl/storage/StorageInterfaceFilter.java`）：自建 `HashSet<AEKey>` 加 `configured` 标志（`:17–18`，判定在 `:34`）。语义与 `IPartitionList` 等价（空表放行，非空即白名单）。持久化为 `CompoundTag`，键 "0"–"26"，每项是 `toTag` 加 `#t`。**未使用 ConfigInventory**，这是审计结论成立的部分。
- **ECOStorageCell**（`impl/storage/ECOStorageCell.java`）：**已经使用** `ConfigInventory` 和 `IPartitionList`（`:17–18`、`:50`、`:86`）。方法与 AE2 `BasicCellInventory` 逐一对应，但 AE2 构造函数为 private，无法继承。有意偏离：类型上限 63 不存在（`NECellTypes` 中为 315）；无 EQUAL_DISTRIBUTION 分支；有封存、迁移 API、批量 defer 持久化。审计把它列为"重造轮子"，需要修正为"已对齐，剩余偏离是有意的"。
- **字节计算**：`StorageByteAccounting` 已存在，被 Universal 盘、无限引擎、HostStatistics 使用。**它没有单测**。ECOStorageCell 仍自算 `getUsedBytes`、`getRemainingItemCount`、`simulateInsertForMigration`（与 `getUnusedItemCount` 重复）、`ceilDivide`。T0-10b 已完成向上取整。
- **批量盘**（`integration/megacells/backend/ECOMegaLongBulkStorageCell.java`，684 行）：覆盖约 21 个方法。父类构造（约 `:70–106`）仍会解析 STORAGE_CELL_INV，调用 bulk 覆盖的 `getConfigInventory`（每次新建 ConfigInventory，见 `ECOMegaLongBulkStorageCellItem:42–69`），并建 partitionList。partitionList 对 bulk 只在继承的 `simulateInsertForMigration` 中使用，属于不可达路径。继承的 `getMigrationStacks`、`migrationEntries`、`getMigrationAmount` 读的是父类空 map，目前只因 `isInfiniteStorageEligible()` 返回 false 而不可达。
- **IO 端口**（`impl/storage/transfer/ECOIOPortTransfer.java`）：insert、extract、poweredInsert、退回与 AE2 `IOPortBlockEntity.transferContents`（约 `:325–377`）一致。ECO 另有 5 键/tick 轮询队列、整键传输（无 AE2 的操作预算）、创造源过滤。AE2 方法为 private，无法复用。4 参、5 参重载仅测试使用（生产只走 6 参，`ECOStorageInterfaceTransfer:52`）。
- **批量优先**：`CombinedStorage.insert` 两遍扫描（`blocks/entity/storage/ECOStorageInterfaceTransfer.java:107–116`）；网络挂载走 `StoragePriority.mountPriority`（`ECODriveBlockEntity:229`）。两条路径不同，接口路径没有 host priority。
- **BE 透传**（`blocks/entity/storage/ECOStorageSystemBlockEntity.java`，910 行）：直接转发控制器 15 个（mega 9，infinite 6）。其中 8 个无外部调用（mega 7 个，加 `canInsertIntoInfiniteDomain`）。`getSelectedEcoMegaBulkCell/Page` 仅 BE 内部使用。另有约 20 个包级字段访问器。
- **AEKey 编解码**：批量盘 writeKey/readKey（约 `:523–543`）写 `{item, components}`，不是 AE2 格式。`ECOInfiniteStorageData`、`ExecutingCraftingJob` 已普遍使用 `fromTagGeneric`。

### 2. 目标形态

- Filter：内存模型改为 `ConfigInventory.configTypes(27)` + `IPartitionList`，changeListener 失效缓存。`encode`、`decode`、`with`、`get` 保留为 RPC 线格式。
- ECOStorageCell 与批量盘的字节公式全部委托 `StorageByteAccounting`，新增 long 版。
- 批量盘不再经过 partition 与 upgrade 初始化（见 8.3）。
- IO 端口：boolean 参数改为 `OperationMode` 枚举，删除 4、5 参重载，保留轮询队列。
- BE：删除 8 个死转发，其余改为直接访问控制器。

### 3. 迁移步骤（每步独立可编译、可测）

1. `StorageByteAccounting` 增加 long 饱和版 `usedBytes`；ECOStorageCell 与批量盘改用，删除两份 ceilDivide 和 simulate 中的重复计算。
2. `ECOIOPortTransfer` 换 `OperationMode`，删 4、5 参重载，改 2 处测试。
3. 删 BE 死转发，活跃转发改为包级控制器访问器。
4. Filter 内存改为 ConfigInventory + IPartitionList，持久化格式暂不变（8.1a）。
5. 批量盘按 8.3、8.4 处理。
6. `acceptsStorageResource` 改为 IPartitionList 判定，缓存 Predicate 字段。

### 4. 存档兼容

**Filter**：字段 `storageResourceFilters` 为 `@Persisted CompoundTag`（`blocks/entity/ECOMachineInterfaceBlockEntity.java:98–99`）。
- 读取时逐个处理 "0"–"26"，跳过非 compound 和越界键。每项用 `AEKey.fromTagGeneric`，未知类型返回 MISSING_CONTENT 占位而非 null。旧数据没有 `amount` 字段，不能走 `GenericStack.CODEC` 或 `readFromTag`，否则解析失败。
- 读档样例：
  ```
  {"0": {"#t":"ae2:item","id":"minecraft:stone"},
   "13": {"#t":"ae2:fluid","id":"minecraft:water"},
   "26": {"#t":"<未安装附属的类型>","id":"<key>"},
   "5": {}}
  ```
  期望：stone、water 放行；dirt 拒绝；"26" 与 "5" 无法解析时仍保持限制，不得整体放行。

**批量盘**：沿用 `ECOMegaLongBulkStorageCellTest:385` 的输入（v0 entries 含 `{item:"minecraft:iron_ingot", units:91}` 与 `{item:"missing:unknown", units:500}`）。新增 v1 样例：`{version:1, entries:[{item, components:{}, units, definition:[{item, components:{}, factor}]}], cutoffs:[...], unresolved:[...]}`，factor 以 `MegaBulkUnits.compile` 的结果为准。

**写入**：v1 格式不变。

### 5. 行为与热路径

- `StorageByteAccounting.usedBytes` 现为 BigInteger 版。直接用于 insert 路径会每次分配对象，违反第 10 条热路径规则，必须新增 long 版。
- `remainingInsertAmount(long)` 在 used > total 时返回 0，而 ECOStorageCell 返回未用余量。仅超容状态下结果不同，需要测试锁定。
- ECOIOPortTransfer 每 tick 对全部可用键调用 filter（`:55`），`matchesFilter` 为 O(1)，不增加分配。
- `storageInterface::acceptsStorageResource` 每 tick 每接口分配一次，建议缓存。
- CombinedStorage 不宜直接改为预排序，否则提取顺序也会变。

### 6. 测试计划

- 新增 `StorageByteAccountingTest`（目前**没有**单测）：long 与 BigInteger 版一致；超容边界；amountPerByte 为 0 时夹紧。
- `StorageInterfaceFilterTest`：增加第 4 节的读档样例；若采用 8.1b，增加 ListTag 往返测试。
- `ECOIOPortTransferTest`：替换 OperationMode 后全部用例通过。
- `ECOMegaLongBulkStorageCellTest`：v0、v1 读档，unresolved 与 cutoff 保持不变。
- 新增 ECOStorageCell 字节回归：固定 insert、extract 序列前后 getUsedBytes、getRemainingItemCount 不变。
- `ECOInfiniteStorageAccessTest:47–59` 通过反射读取字段名 `infiniteModeController`，BE 字段改名或删除时需同步修改。

### 7. 预计行数（估算，未核实）

StorageByteAccounting +25；ECOStorageCell −20；批量盘 −25（8.3a 时约 −5）；ECOIOPortTransfer −12；BE −45；Filter 约 63→55；ECOMachineInterfaceBlockEntity +10。主代码净减约 80–100 行，测试约 +150 行。

### 8. 风险与需用户决定

- **8.1 Filter 持久化格式**
  - (a) 保持 CompoundTag（推荐）：无迁移、无降级风险。对齐只体现在内存模型。
  - (b) 改为 ConfigInventory 的 ListTag，字段名不变，按 TAG_COMPOUND 与 TAG_LIST 区分。需手写 saveAdditional、loadTag，并核实 `@Persisted` 能否处理。降级到旧版本时会读不出。
- **8.2 无法解析的附属键**
  - (a) MISSING_CONTENT 占位：列表非空，仍然限制。GUI 显示缺失物品，原数据经 coApply 写回，重装附属后恢复。每次读档会打一条 AE2 LOG.error。
  - (b) 沿用 null 跳过并保留 configured 标志（与现状一致）：GUI 显示空槽。
- **8.3 批量盘父类初始化**
  - (a) 父类字段改为惰性初始化：改动小，但仍继承那些不可达的迁移方法。
  - (b) 抽出 abstract `ECOStorageCellBase`，ECOStorageCell 与批量盘共用。需同步改 `ECOStorageHostStatistics:254` 的 instanceof，diff 较大。推荐 (b)，待确认。
- **8.4 批量盘键格式**
  - (a) 保持 v1 `{item, components}`（推荐）：readKey 可改用 `AEItemKey.CODEC`（不用 `AEKey.CODEC`，否则 MISSING_CONTENT 会破坏 unresolved 保留），省约 10 行。
  - (b) 写 AE2 格式并升 version=2：旧版在 `version > 1` 处抛 IllegalStateException，降级即崩溃。
- **8.5 CombinedStorage 批量优先**：(a) 保留两遍扫描（推荐）；(b) 仅插入列表预排序，提取列表不变。二者都不并入 mountPriority。
- **8.6 StorageByteAccounting 位置**：留在 impl/storage，T4-15 再移入 core；或现在移入 core（满足白名单，但提前了 T4-15 的一部分）。
- **8.7 IO 端口轮询**：保留 5 键/tick 队列（推荐，测试固定其行为）；删除会改变大量键时的 tick 行为。
- **8.8 BE 活跃转发**：(a) 删除并暴露包级控制器访问器（推荐），同步修改 `ECOInfiniteStorageAccessTest`；(b) 保留。

### 9. 不做的部分及理由

- ECOStorageCell 委托 AE2 `BasicCellInventory`：构造函数 private，且 ECO 的 315 类型、封存、迁移、批量语义 AE2 无法承载。
- CombinedStorage 并入 mountPriority：路径不同，接口路径无 priority 概念。
- 批量盘改用 AE2 键格式写入：同 8.4，降级会崩溃。
- RPC 线格式（per-slot CompoundTag）：客户端与服务端需同时改，收益低。
- IO 端口整体换为 AE2 `transferContents`：轮询与整键语义有测试约束。
- ECOStorageCell 改为 AE2 的"仅无容器时立即写"：`ECOCellMutationBatch` 依赖现有行为。
- "清空存储盘"的 4 份实现：T2-9 已确认保留。

---

## 通读审计：两个大方块实体

审计范围：`blocks/entity/ECOMachineInterfaceBlockEntity.java`（1,857 行）与 `blocks/entity/crafting/ECOCraftingPatternBusBlockEntity.java`（1,272 行），两个文件已完整通读。死代码判断基于覆盖 `src/` 全部文件的 grep。未运行 gradle，未修改仓库。

### (a) 可删（已核实无调用方）

| 位置 | 内容 | 核实 |
|---|---|---|
| `MI:958` | `insertPatternSlot` 私有方法 | 全仓仅定义处 1 次命中 |
| `MI:758` | `onPatternBusInventoryChanged(bus)` 单参重载 | publisher 只调用双参版本 |
| `MI:680` | `getPatternInterfaceStack` | 仅定义处 |
| `MI:132` | `patternOrganizeAuxiliaryMoved` | 只写不读（`:1288` 自增，`:1469` 清零） |
| `MI:507` | `setPatternPreview` RPC | 仅定义；实际接收路径是 `network/MenuDataCodecs.java:48`。删除前需确认 |
| `MI:1005–1055` | `insertPatternFromPlayer` RPC 与 `tryInsertPatternFromPlayer` | 客户端无 rpcToServer 调用点。置信度中，删除前需人工确认 |
| `MI:1352` | `if (!task.prepare())` 分支 | `prepare()`（`:1721`）恒返回 true，分支不可达 |
| `MI:1243–1253` | 孤立 Javadoc（描述退款，压在 organizeIntoAuxiliary 的 Javadoc 上方） | 应属 refundConsumedPattern，属注释错位 |
| `PB:697` | `isDurabilityPattern` 私有静态 | 仅定义处 |
| `PB:837` | `updatePatternDetails()` 私有 | 仅定义处（catalog 内有同名方法） |
| `PB:865` | `requestPatternDetailsRefresh()` | 仅 BE 的转发方法调用 `catalog.requestPatternDetailsRefresh()`，无外部调用 |
| `PB:402` | `pushBatch(ECOBatchCraftingRequest, …)` 空实现 | 全仓无调用。外部字节码调用无法排除 |
| `PB:398–424` 中的部分转发 | `acceptVerifiedBatch` 等 | 两个 record 仍在用，保留 |
| `PB:443` | 三参 `calculateBatchOfferSize` | 只被四参版本调用，可内联 |
| 仅类内使用却标 protected 的钩子 | `MI:425`、`MI:470`、`PB:516`、`PB:997`、`PB:1017` | 全仓无子类覆写，可收窄或内联 |
| 未用 import | MI 的 `StringTag`、`java.util.Map`；PB 的 `Slot`、`UI` | 自动检查误匹配注释，已人工核实 |

### (b) 可合并

| 位置 | 内容 | 判断 |
|---|---|---|
| `MI:1313` `refundConsumedPattern` ≡ `mixins/ae2/menu/PatternEncodingTermMenuMixin.java:106` `neoecoae$returnReplacement` | 两者都是 simulate、判断全额、MODULATE，仅 IActionSource 不同（`ofMachine(this)` 与 `ofPlayer`） | **强**。已核实两处结构相同，等价性需测试确认。建议抽成带 IActionSource 参数的 helper |
| `MI:1292` `canRefundConsumedPattern` 与 `MI:1313` 前半段 | 逐行相同 | refund 可写成 `canRefund && 提交` |
| `MI:1500` `AEItems.BLANK_PATTERN.stack(count)` ≡ `PatternRefund.blankFor` | count≥1 时等价 | 可复用 |
| `ECOLargeIntegratedWorkingStationInterfaceBlockEntity.java:398–425` `diskTerminalView()` 与 `PB:561–587` `diskTerminalView(long)` | 结构相同，仅库存与 revision 来源不同 | **中**。注意避免过度抽象 |
| `MI:883` `nextPatternContentRevision()` ≡ `PB:882` `incrementPatternContentRevision()` | 同一逻辑 | 可共享 |
| `MI` 内部 | 前缀和循环重复 4 处（`:399`、`:777`、`:799`、`:811`）；`onPatternBusInventoryChanged` 两个重载的前置校验（`:766–776`、`:788–798`）逐行相同 | 可抽 `offsetOf(busIndex)` 与公共校验 |
| `MI` 内部 | `grid.getService(IECOPatternStorageService.class)` + `instanceof PatternCatalog` 出现 6 次（`:484`、`:495`、`:940`、`:984`、`:1229`、`:1265`） | 抽 `patternCatalog()` |
| `MI:565–571` 与 `MI:616–622` | "抽取、add、drop 余量"完全相同 | 可抽 |
| `MI:258–265` 与 `MI:1446` | 距离与维度判断相同 | 直接复用 `isPatternInterfacePlayer` |
| `MI:1636` `isSourceUnchanged` 与 `MI:1767` | 语义相同 | 可合并 |
| `PB:810` `onChunkUnloaded` 与 `PB:817` `setRemoved` | 除 super 外相同 | 可合并 |
| `PB:850` → `updatePatternDetailsNow` → catalog | 双层转发 | 可压成一层 |
| `MI:339`、`:357`、`:370` | "noTarget" 条件重复三次 | 可抽布尔谓词 |

### (c) 疑似 bug

1. **[中] `PB:323` `submit` 与 `integration/thunderbolt/dispatch/ECOThunderboltBatchBridge.drainFastPath`（`:28–50`）**
   - 循环中第 k 批 `batch.submit` 成功后，若第 k+1 次 `prepareAllocated` 抛 RuntimeException，异常会离开 drainFastPath，前 k 批已派发的 `accepted` 不会被计入返回值。调用方据此返回 REJECTED，但 Reservation 的 commit/refund 为空实现，submit 不可回滚。
   - 若调用方据 REJECTED 回滚输入，就会产生重复产出。
   - **未经测试确认**。建议先写单测：第二条车道准备时抛异常，确认返回值与实际派发量是否一致。
2. **[中低] `PB:246` `getAuxiliarySearchKeywords`** 全仓无调用，但其 Javadoc（`PB:239`）称终端只靠关键字找到配方。需确认磁盘内配方在搜索中是否可见。未验证。
3. **[低] `MI:302` `startNetworkPatternTransfer`**：tryAcquire 失败时已清空结果并置 `patternTransferPerformed=true`；`organizePerformed` 为真时，状态方法先返回整理结果，用户看不到"忙"提示。
4. **[低] `PB:362` `findExternalPattern`** 只查 `catalog.availablePatterns()`，不含磁盘配方；而 `PB:265` `getAvailablePatterns` 含磁盘配方。外部 fastpath 对磁盘配方返回 UNSUPPORTED_PROCESSING，不崩溃，但行为不一致。
5. **[低] `MI:1280`** `outcome == null` 只判空，未判 INSERTED。当前 `tryAuxiliaryPass`（`PatternCatalog.java:263–317`）只返回 INSERTED 或 null，属潜在隐患。
6. **[低] `PB:953` `getPageCount()`**：客户端 catalog 为空时，会把本地算出的页数写回 `@DescSynced` 的 activePages。未在客户端验证。
7. **[低，注释] `MI:1274`** 注释称磁盘拒绝重试会"永久失配"，但 DUPLICATE 分支（`MI:1157–1162`）会回收，注释结论需复核。

### (d) 不动

- `src/test/.../gui/GuiModuleBoundaryTest.java:39–48` 钉住 PB 不得含 `new UIElement(` 或 `.layout(`，UI 代码不要搬进两个 BE。
- `CraftingInterfaceRenderingTest.java:245` 通过反射读取字段 `patternOrganizeInProgress`，不可改名。
- RPC 名 `openPatternPreview`、`actOnPatternPreview`、`quickMovePatternPreview` 被测试与客户端字符串引用（`CraftingPatternPreviewStateTest:130`、`CraftingInterfaceRenderingTest:111/190`、`gui/crafting/CraftingPatternPreviewState.java:168/252/278`）。
- `PatternRefundTest` 钉住 PatternRefund 的数量与清源规则。
- `ECOThunderboltBatchBridgeTest:74/91` 依赖 `bus.pushPattern(pattern, inputs, job)` 三参。
- `PB:1017–1172` TerminalPatternInventory 的槽位冻结、磁盘行经 mod 路径、隐藏行不可写，注释明确，属于有意设计。
- `PB:127–134` auxiliaryRevision 与 patternContentRevision 分离，注释论证了索引不收敛的问题。
- `PB:962` `if (activePages != pages)` 守卫，注释说明是为避免同步抖动。
- `PatternMigrationCoordinator.beginSlice` 返回 0 时 finish(unavailable)，是 lease 丢失的设计分支。

### 优先处理（建议顺序，均未开始）

1. **(c1) submit 异常路径丢失已派发批次。** 唯一可能导致物品重复、且外部可触发的路径。先写单测复现。
2. **(b1/b2) 退款逻辑统一。** 三处副本（MI 两个私有方法与 `PatternEncodingTermMenuMixin`）实现同一守恒逻辑，仅 action source 不同。统一后可避免日后只改一处。
3. **(a) 批量删除死代码与死分支。** 高置信、低风险，同时清理 import。`MI:507`、`MI:1005` 两个 RPC 删除前需人工确认。

---

## T4-4 / T4-5 模式身份、工作 DTO、推送守卫，并行提供者

路径相对 `src/main/java/cn/dancingsnow/neoecoae/`。

### 1. 现状（已核实）

- **模式身份**：三类相等域不同，审计"3 种"成立。
  - `planner/identity/PlanIdentity.PatternIdentity`：嵌套 record，结构化相等，59 处引用。
  - `batch/ECOPatternIdentity`：原始 pattern 实例 + definition + provider 实例，18 处。
  - `fastpath/ECOFastPathPatternKey`：definition + generation，不含 provider，7 处。
  - 冗余确认：`fastpath/ECOFastPathKey` 第 21、33、76、87 行单独存 `reloadGeneration`，而 `patternKey` 已包含该值。
- **DTO**："5 个"成立，但集合需修正：`ECOBatchCraftingWork`、`ECOVirtualCraftingWork`、`ECOVerifiedVirtualExecution`（凭证）、`api/me/provider/ECOFastPathDispatchProvider.Batch`（公开 API）、`ECOStatefulBatchPlanner.PreparedBatch`。另有 `ECODurabilityBatchModel.ToolPoolBatch` 与 `ECOStatefulBatchCalculator.BatchContract` 字段完全相同且 1:1 转换。`ECOExactBatchPlanner.PreparedBatch`（BigInteger）与 `PreparedWork`（ItemStack）数值域不同。
- **推送守卫**：`ECOCraftingPatternBusBlockEntity` 第 404 行 `pushBatch` 为空实现，只被 `fastpath/ECOBatchCraftingRequest` 引用（全仓 2 个文件）。真实链路为 Bus `acceptVerifiedBatch`（398）→ Dispatcher（134）→ Worker `pushBatch`（210）→ Thread `pushBatch`（341）。`isIssuedBy` 在 worker 与 thread 层各出现一次；`isFullVirtualCraftingMode` 在 worker（249）与 thread（397）重复。
- **游标**："2 份"需修正：`crafting/execution/ECOProviderCursor` 是按 pattern 的 provider 环，仅调度器使用（`ECOCraftingTaskScheduler`）。同构的"环 + 记住下一个"实现是 `ECOCraftingPatternBusDispatcher.roundRobinIndex`（24、57–63）与 `ECOCraftingWorkerBlockEntity.nextFreeThreadIndex`（69、268–287，另在 532、586 行重置）。
- **并行提供者**：三层为 `api/me/provider/ECOParallelCraftingProvider`（接口，站内唯一实现 `LargeWorkstationPatternProvider`）→ `crafting/execution/batch/ECOParallelProviderAdapter`（record，只被 `api/me/ECOFastPathFacade` 使用）→ `ECOParallelCraftingProviders.Adapter`（嵌套函数式接口）。注册表 `register` 站内零调用，`find` 被 3 个类、5 处调用。
- **规划器**：`ECOExactBatchPlanner`（34、41）与 `ECOStatefulBatchPlanner`（73、92）每次 `new ECOBatchPlanner()`。`ECOBatchPlanner` 本身无状态，`ECOBatchDispatchPlanning` 已用静态字段。

### 2. 目标形态

- **身份**：保留三类并各自写明相等域；删除 `ECOFastPathKey` 的冗余字段；`ECOFastPathPatternKey` 去掉未用访问器。
- **DTO**：新增 `CraftWork`（craftCount、三个 GenericStack 列表、craftingJobId、上限），替换 `ECOBatchCraftingWork` 与 `ECOVirtualCraftingWork`；`ToolPoolBatch` 直接用 `BatchContract`；公开 `Batch`、`PreparedBatch` 不动。
- **守卫**：`isIssuedBy`、`isFullVirtualCraftingMode` 只在 thread 落点检查；删除 bus 空 `pushBatch`。
- **游标**：不复用 `ECOProviderCursor`；bus 与 worker 两个同构环若语义可对齐，合并为一个私有小工具。
- **并行**：`ECOParallelProviderAdapter` 内联为 `ECOFastPathFacade` 的私有方法；注册表保留。
- **规划器**：两处改为 `static final` 实例。

### 3. 迁移步骤（每步独立编译；gradle 由总策划执行）

1. `ECOFastPathKey` 删除 reloadGeneration 字段，改经 `patternKey()` 取值（cache 第 121、200 行）。
2. 删除 bus `pushBatch` 与 `ECOBatchCraftingRequest`；`ToolPoolBatch` 换成 `BatchContract`。
3. 规划器 4 处 `new` 改为静态实例。
4. 内联 `ECOParallelProviderAdapter`。
5. 删除 worker 层重复守卫；thread 中两处 `canRetain` 检查抽为一个私有方法。
6. 引入 `CraftWork`，替换两个 DTO 及 `ECOCraftingThreadWork` 的调用点。
7. （可选）环合并。

### 4. 行为与兼容

- **存档**：身份与 DTO 均不落盘。`PatternIdentity` 由 `ECOExecutionPlanNbtCodec` 解码时重算，存档格式不变。
- **网络**：DTO 不进入 packet，无变化。
- **API**：`Batch`、`PreparedBatch`、`ECOParallelCraftingProviders` 保持不变。
- **热路径**：每批仍只分配一个 record；planner 每次 prepare 少 4 次分配；内联后 lambda 数量不变。
- **语义**：批量路径的 `MAX_BATCH_STACK_AMOUNT` 上限与 `consumeCraftingCoolant(batchSize)` 只属于批量路径，合并时必须保留。

### 5. 测试计划

- **既有**：`ECOCraftingFastPathCacheTest`、`ECOBatchExecutorTest`、`ECOBatchDispatchIntegrationTest`、`ECOProcessingDispatchIntegrationTest`、`ECOExternalCpuFastPathTest`、`ECOThunderboltWorkstationBridgeTest`、`ECOVirtualCraftingDiagnosticsTest`、`ECOEAEPVirtualCraftingTest`、`ECOUselessBigIntegerDispatchTest`。
- **新增**：不同 generation 的 key 不相等；CraftWork 批量与虚拟上限各一例；注册表 `find` 缓存命中，适配器抛异常时返回 null。

### 6. 预计行数

主代码约 −70 行（区间 −55 至 −80）；测试约 +60 至 +100 行。

### 7. 风险与需决定的点

1. **CraftWork 合并方式**：A 单 record 加上限参数，省行数，但需逐个核对调用点的上限；B 保留两个 record，只抽出校验，更安全，省行有限。
2. **身份统一**：A 保留三类并文档化（推荐）；B 统一为 `PlanIdentity`，批量准入变为结构相等，且结构化计算分配较多，违反热路径规则；C 缓存键加入 provider，缓存命中率下降。
3. **删除 bus `pushBatch`**：A 删除，代码干净，但若 Crafting Tracker 经反射或 mixin 调用，会从静默 no-op 变为找不到方法；B 保留空函数，违反审计第 8 条。建议先确认外部依赖。
4. **游标**：A 只合并同构的两个环（推荐）；B 强行复用 `ECOProviderCursor`，需泛化 key 与元素类型，属过度抽象。
5. **并行注册表**：A 保留，作为 api 扩展点（推荐）；B 删除，站内无注册方，但 Javadoc 面向其他模组，属破坏性变更。另 `ECOProcessingPatternDispatcher:254` 仍用 `instanceof`，改用 `find` 会改变被适配提供者的行为，需单独决定。
6. **BigInteger 的 `ECOExactBatchPlanner.PreparedBatch`**：A 本组不并入，留给 T4-1（推荐）；B 并入会在一个 CraftWork 中混合 GenericStack 与 BigInteger 两套数值域。
7. **`ECOPatternIdentity.of` 的 null 定义**：`getDefinition()` 返回 null 时直接 NPE。A 保持现状；B 回退到结构化身份，需先确认 AE2 包装 pattern 是否会返回 null。

### 8. 不做的部分及理由

- 改名或合并公开的 `Batch`、`PreparedBatch`：外部提供者依赖其形状，属 API 破坏。
- 把 `ECOProviderCursor` 用于 worker 环：类型与语义不匹配。
- `dynamicCursor`、`resumeDispatchPattern`：语义不同，不是同构游标。
- 循环改 Stream、为少量调用点新建接口：违反审计第 8、10 条。
- 若 `nextFreeThreadIndex` 的重置语义无法保留，则不与 bus 环合并。

---

## 通读审计：三个执行核心类

范围：`crafting/execution/worker/ECOCraftingThread.java`（TH，1,535 行）、`crafting/execution/ECOExecutionRuntime.java`（RT，1,433 行）、`crafting/execution/ECOCraftingCPULogic.java`（CPU，756 行）。三个文件已完整通读。死代码判断基于覆盖 `src/` 的 grep（含测试与资源）。未运行 gradle 或测试，疑似 bug 均为静态判断。三个文件内无 ThreadLocal，也无跨线程共享状态。

### (a) 可删

| 位置 | 内容 | 置信度 |
|---|---|---|
| `TH:535–544` | `snapshotCraftingInputs`：全仓无调用 | 高（已核实，仅定义 1 处命中） |
| `TH:76` | `RecoveryState.DROPPED_TO_WORLD`：从未赋值 | 高 |
| `RT:528–537` | `startupSeedShortfall`：无任何调用方 | 高（已核实，仅定义 1 处命中） |
| `RT` 别名机制（`:134`、`:151`、`:1077–1088`、`:1141–1180`、`:1251–1279`） | `rejectSharedProgressAliases` 只要出现多任务共享 TaskProgress 即抛异常，因此 `progressAliasesByTaskId[t]` 恒为 `{t}`，`describeProgressAliases` 恒返回 `"[]"`，`logSharedProgressAliases` 永不输出。整套可简化为单任务映射 | 高 |
| `RT:70、72、1196–1204` | `remainingDependencies` / `dependentsByPhase`，只服务诊断字符串；`dependenciesComplete`（`:699–707`）只读 `completedPhases` | 中高，删除需同步诊断 |
| `RT:515–526`、`RT:232`、`RT:419` | `reservedInputAmount(key, Map)`、`candidates(Map)`、`isComplete(Map)`：外部只用无参版本，后两者仅 `FeedbackRouteRegressionTest` 使用 | 中，需决定是否改测试 |
| `CPU:209–211` | `deliverStoredFinalOutput` 为纯转发（唯一调用在 `CPU:170`） | 中，可内联 |
| `CPU:555–561` | load/writeJobAttachments 只多一层 null 判断 | 中，可并入 Persistence |
| 未使用 import | CPU 15 个；TH 4 个（`AEFluidKey`、`Object2LongMap`、`Map`、`Set`）；RT 1 个（`Collections`） | 高 |
| `TH:1340` | `outputItems.clear()` 冗余（`:1336` 已清） | 高 |
| `RT:1377` | 三目 `: false` 可简化为 `&&` | 高 |
| `TH:1242` | 单数 `outputItem` 写入：读取仅在 version<4 时发生（`TH:1354`），当前版本写出的键永不被消费。若为降级兼容则保留并注明 | 中 |

### (b) 可合并

| 位置 | 内容 | 判断 |
|---|---|---|
| `CPU:485–489` 与 `CPU:491–495` | `setJobFromPersistence` / `setJobFromLifecycle` 方法体完全相同 | 高，合为一个 |
| `CPU:453–463` | 三个 `taskSchedulerFor*` 返回同一字段 | 高，合为一个访问器 |
| `CPU:643–645` 与 `CPU:656–658` | `recordPhysicalInsert + postChange + markCpuDirty` 三行相同 | 高，抽 helper |
| `TH:415–417` 与 `TH:426–428` | 三项 `canRetain` 判定逐项相同 | 高，抽 `canRetainWork(...)` |
| `TH:661` 与 `TH:920–921` | `ECOBigIntegerStorage.insert(storage, key, amount, MODULATE, actionSource)` 相同；两处 drainExact 成功后的 `onBatchStopped/clearWork/return true` 也相同 | 高 |
| `TH:697–702`、`714–719`、`1140–1145` | 同一 100 tick 节流日志逻辑出现三次 | 高，抽 static helper |
| `TH:826–835` 与 `TH:866–877` | 存储插入、扣减、retain 结构相同，仅错误标签与 retain 状态不同 | 中，可参数化 |
| `RT:871–884` `inputKeys` 与 `ECOCraftingInputPreview:106–111` | 语义接近，但后者在 `possible[0]==null` 时整体跳过该 input，前者不跳过 | **不建议合并**，需先对齐语义 |
| `RT:769–782` `fixedProcessingInputs` 与 `NetGrowthPatternValidationRegistry:84–88` | 只部分重叠，用途不同 | **不建议合并** |

### (c) 疑似 bug

- **C1（中）`CPU:373–381`：虚拟完成判定缺少 `pendingFinalOutputs.isEmpty()`**（已核实代码对比）。`ECOCraftingOutputDelivery:89–92` 有该条件，CPU 的虚拟分支没有。
  - 触发：任务中已有物理最终产物停留在 CPU 库存（pending 非空）时发生虚拟完成。CPU 强制 `remainingAmount=0` 后调用 `finishJob(true)`。lifecycle 先 `storeItems()` 把产物回流网络，再清空 pending。结果是请求方收到 SUCCESS，而最终产物已进入网络。
  - 依据为代码对比，**未测试确认**。
- **C2（中低，影响低）`CPU:167–176`**：169 行 `beginResolveTick` 之后，170、174 两处早退未调用 `finishResolveTick`（仅 `:180`、`:200` 调用）。只丢失该 tick 的诊断日志。代码路径确认。
- **C3（低）`RT:395–396`**：`onVirtualAccepted` 先 `progress.accept(count)` 再 `onAccepted` 校验；后者若抛异常，任务进度已扣减，而 `CPU:363–365` 吞掉异常并返回 false。正常候选下不应失败，属防御性缺陷。
- **C4（低）`RT:74`、`239`**：`candidateBuffer` 是共享可变列表。`CPU:351–352` 的 `candidates()` 会 clear 它；若外部在 provider push 中同步回调该 sink，`TS:166–185` 的循环会读到被改写的列表。仓库内无此调用路径。
- **C5（低）`RT:744–746`、`RT:824`**：使用 long 的 `multiplyExact/addExact`。当 `(laps-1)*count` 或总量超过 Long.MAX（约 9.2e18 次合成）时抛 ArithmeticException，`TS:166` 调用链无 try，会每 tick 抛出。BigInteger 边界转换（`TH:650`、`1180`，`RT:327`、`860`，`ECOExactInventory:50`、`67`）均已先 `min(MAX)`，未发现越界。
- **C6（中）`RT:640–641`：启动种子静默丢失**（已核实）。`GenericStack.readTag` 返回 null 时，`if (stack != null && stack.amount() > 0L)` 直接跳过，不标记损坏。对比 `TH:1411–1425`，批量条目不可读会 quarantine。结果是该种子不再受保护，库存可能被其他阶段消耗，正是 `RT:1395–1398` 注释所要防止的情形。触发需卸载模组或 NBT 损坏。
- **T1（低触发，数据丢失）`TH:1338–1342` 与写出侧 `TH:1245–1247`、`1286–1293`**：读取侧已核实只读前 256 条（`MAX_PERSISTED_ITEM_STACK_ENTRIES`）并标记损坏；写出侧按代理报告不设上限。单次非批量合成输出超过 256×99=25,344 件（或拆分后超过 256 条）时，超出部分会在 quarantine 后静默丢失。写出侧的不设上限仍需复核。
- **T2（高，影响低）`TH:1234`、`1242`**：经 `saveSerializableStack`（`:1259–1264`）把数量截断到 99，重载后 craftingEventOutput 数量 ≤99，影响 ItemCraftedEvent。
- **T3（低，未确认）`TH:155`**：控制器退出全虚拟模式后，`virtualBatch` 为真的批次会落入物理进度路径（`:181–185`），并按非虚拟计费后弹出虚拟产物。是否重复产物取决于派发前输入是否已物理抽取，未核实。
- **T4（低）`TH:646–662`**：CraftingService 非 `ECOCraftingOutputRouter` 时 lambda 返回 ZERO 且不改状态，drainExact 永不完成；`690–705` 只处理异常，会静默停滞。依赖 mixin 缺失，实际概率低。

**NBT 对称性**：RT 的 writeToNBT/fromNBT 逐键核对一致；CPU 持久化一致；TH 除 T1、T2 外一致。

### (d) 不动

- `CPU:230–239` 的 `thunderboltMixinAnchorEnabled()` 恒 false 分支，`CPU:213–217` 的 `collectAvailableProviders`，`CPU:282–285` 原样保留的 `provider.pushPattern`：注释明确为 Mixin/Thunderbolt 二进制兼容锚点。
- `CPU:297–300`、`309–311`：注释说明 AE2 Utility 依赖这些调用点。`CPU:518–520` 的 `getElapsedTimeTracker`（deprecated）仍被 `CraftingCpuMenuMixin` 调用。
- `RT:1215–1219`、`1282–1298`：绑定 TaskProgress 时以 progress 为真源并忽略持久化 cursor，属有意设计。
- `RT:600–622` 的 laps/ahead 严格校验，与 `ExecutingCraftingJob:239` 的 try 兜底配合：fromNBT 失败转为 suspended。
- `ECOCraftingCPU:232–240` 注释：load 抛异常即"拒绝并重放"，`AmountMapNbt` 对非正值抛异常亦属此设计。
- `ECOBigOrderController:294–296` 写入以 `job!=null` 门控，`317–318` 读取 `clear()`，对称。
- `ECOExactInventory:92–96` 的 restore 对 0 抛异常，但 `deferredEmitted` 在 `CPU:662–669` 每次清零，NBT 读取拒绝非正值，零值不可达，不是 bug。
- `TH:1456–1471` 的 quarantine 机制与 `GenericStackNbt.readBounded`（`54–66`）的 Optional 语义：空 remainder 合法，已核对不会误判。

### 优先处理（建议顺序，均未开始）

1. **C1（`CPU:373–381`）**：请求方收到 SUCCESS，而产物回流网络，是行为层不一致。修复可直接复用 `ECOCraftingOutputDelivery` 的谓词，并补一个 pending 非空的虚拟完成测试。
2. **T1（`TH:1338–1342`）**：静默且不可恢复的数据丢失。建议改为按 count 计数上限，或超限时拒绝保存，而非截断。
3. **C6（`RT:640–641`）**：启动种子静默丢失会破坏跨阶段保护。建议与 TH 路径一致，改为 quarantine 或显式报错。
