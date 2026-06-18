# ECOAE 1.12.2 服务端性能设计审计

审计对象：

- 本地旧项目：`E:\Minecraft Project\NovaEngineering-ECOAEExtension-1122`
- 远端参考仓库：`https://github.com/sddsd2332/NovaEngineering-ECOAEExtension`
- 本报告基于本地旧项目源码扫描；未复制 1.12.2 代码到当前项目。
- 旧项目许可证为 GPL-3.0。本报告只记录设计模式、类职责和少量方法名，不迁移旧 API 或代码片段。

当前 NeoECOAEExtension 对照分支：`perf/server-compute-cache`。

## 一、旧版核心类定位

### 1. 合成系统 / F 系列

`src/main/java/github/kasuminova/ecoaeextension/common/tile/ecotech/efabricator/EFabricatorController.java`

- 核心字段：`parts`、`channel`、`workDelay`、`maxWorkDelay`、`parallelism`、`consumedParallelism`、`coolantCache`、`outputBuffer`、`guiDataPacket`、`guiDataDirty`。
- 核心方法：`onSyncTick()`、`onAsyncTick()`、`updateComponents()`、`updateParallelism()`、`updateWorkDelay()`、`offerWork()`、`hasWork()`、`updateGUIDataPacket()`、`getGuiDataPacket()`。
- 性能职责：控制器级缓存 worker/pattern bus/parallel proc；按 `workDelay` 降低实际工作频率；GUI 数据包有 dirty 缓存；worker 工作结果先进入 `outputBuffer`，下一次同步 tick 批量写回 AE。
- 迁移价值：高。`workDelay`、dirty GUI packet、输出缓冲和 worker 队列都值得借鉴，但不能照搬 1.12.2 AE2 mixin 执行逻辑。

`src/main/java/github/kasuminova/ecoaeextension/common/tile/ecotech/efabricator/EFabricatorWorker.java`

- 核心字段：`CraftingQueue queue`、`status`、`queueDepth`、`energyCache`、`lastUpdateTick`。
- 核心方法：`doWork()`、`offerWork()`、`updateStatus()`、`setStatus()`、`markNoUpdate()`、`CraftingQueue.writeToNBT()`。
- 性能职责：每个 worker 自己维护队列；状态更新带 20 tick 防抖；状态变更只发 worker 状态包，不全量重建控制器 UI。
- 迁移价值：中高。当前分支已做 running thread 增量，仍可借鉴 worker 局部状态包/状态防抖。

`src/main/java/github/kasuminova/ecoaeextension/common/tile/ecotech/efabricator/EFabricatorPatternBus.java`

- 核心字段：`patterns`、`details`。
- 核心方法：`refreshPattern(slot)`、`refreshPatterns()`、`onChangeInventory()`、`notifyPatternChanged()`、`sendPatternSearchGUIUpdateToClient(slot)`。
- 性能职责：pattern details 按槽缓存；单槽变化只刷新单槽并向 pattern search GUI 发单槽更新。
- 迁移价值：高。当前 F 系列 pattern bus 若仍在 UI 或 AE provider 路径全量扫描 pattern，应迁移“按槽缓存 + 单槽 diff”。

`src/main/java/github/kasuminova/ecoaeextension/common/tile/ecotech/efabricator/EFabricatorMEChannel.java`

- 核心方法：`provideCrafting()`、`pushPattern()`、`isBusy()`、`postPatternChangeEvent()`。
- 性能职责：只在 AE channel active 状态变化时 post crafting pattern change；`isBusy()` 用 controller 队列满判断，避免 AE 继续投递。
- 迁移价值：中。AE2 1.20 API 不同，但“provider active 状态变化才发 pattern change”和“忙碌判断走队列容量”可借鉴。

`src/main/java/github/kasuminova/ecoaeextension/mixin/ae2/MixinCraftingCPUClusterTwo.java`

- 核心方法：覆盖 `executeCrafting()`。
- 性能职责：批量派发 pattern，按 medium 类型调整 `craftingFrequency`，EFabricator 可一次投递多份 work。
- 迁移价值：低到中。思想可借鉴为“批量入队和容量约束”，但当前明确禁止重写 `ECOCraftingCPULogic.executeCrafting`，不能迁移旧版核心执行覆盖。

### 2. 计算系统 / C 系列

`src/main/java/github/kasuminova/ecoaeextension/common/tile/ecotech/ecalculator/ECalculatorController.java`

- 核心字段：`channel`、`virtualCPU`、`parallelism`、`totalBytes`、`guiDataPacket`、`guiDataDirty`。
- 核心方法：`onSyncTick()`、`updateComponents()`、`recalculateParallelism()`、`recalculateTotalBytes()`、`getAvailableBytes()`、`onVirtualCPUSubmitJob()`、`createVirtualCPU()`、`getClusterList()`、`onClusterChanged()`。
- 性能职责：结构形成时重算总存储和共享并行；维护一个 `virtualCPU` 作为 AE 可见候选 CPU；真实 job submit 后把 virtual CPU 放入 thread core。
- 迁移价值：中。当前分支已吸收 total/active bytes 和 active CPU count 的增量思想；virtual CPU 对 AE2 1.20 语义风险较高，不建议迁移。

`src/main/java/github/kasuminova/ecoaeextension/common/tile/ecotech/ecalculator/ECalculatorThreadCore.java`

- 核心字段：`cpus`、`maxThreads`、`maxHyperThreads`。
- 核心方法：`addCPU()`、`canAddCPU()`、`onCPUDestroyed()`、`getUsedStorage()`、`readCPUNBT()`、`writeCPUNBT()`。
- 性能职责：thread core 自己缓存 CPU 列表；CPU 销毁时增量移除；update tag 写入时用 `ThreadLocal` 控制是否写完整 CPU NBT，避免普通 BE 同步带大数据。
- 迁移价值：高。当前可以借鉴“客户端外观同步不携带完整 job NBT”的原则。

`src/main/java/github/kasuminova/ecoaeextension/mixin/ae2/MixinCraftingCPUCluster.java`

- 核心字段：`novaeng_ec$core`、`novaeng_ec$virtualCPUOwner`、`novaeng_ec$usedExtraStorage`、`TimeRecorder`。
- 核心方法：`submitJob` 注入、`updateCraftingLogic` 注入、`destroy` 注入、`markDirty` 注入、ECPUCluster accessor。
- 性能职责：job 提交时通知 controller；CPU destroy 时通知 thread core；ECPU markDirty 改为 controller/core 的无外观保存；记录 CPU tick 用时和 parallelism。
- 迁移价值：中。事件钩子/指标采样值得借鉴；直接 mixin AE2 CPU lifecycle 风险高。

`src/main/java/github/kasuminova/ecoaeextension/mixin/ae2/MixinCraftingGridCache.java`

- 核心方法：`updateCPUClusters()` 注入、`onUpdateTick` wrap。
- 性能职责：把 ECalculatorMEChannel 的虚拟/真实 CPU 加入 AE CPU 集合；围绕 updateCraftingLogic 做 TimeRecorder 采样。
- 迁移价值：中低。性能采样思想可迁移为可选 Spark/调试指标；AE2 cache 注入不建议迁移。

### 3. 存储系统 / EStorage

`src/main/java/github/kasuminova/ecoaeextension/common/tile/ecotech/estorage/EStorageController.java`

- 核心字段：`parts`、`energyCellsMin`、`energyCellsMax`、`channel`、`idleDrain`。
- 核心方法：`onSyncTick()`、`injectPower()`、`extractPower()`、`recalculateEnergyUsage()`、`getEnergyStored()`、`getMaxEnergyStore()`、`getCellDrives()`。
- 性能职责：5 tick 一次更新 drive 写入状态和 energy cell 容量；energy cell 用两个 priority queue 优先选择最空/最满 cell；存储 idle drain 只在 drive/cell 变化时重算。
- 迁移价值：高。当前 Storage 仍有 UI state 和 tick 重算 drive 的路径，旧版的分层更新频率和优先队列 energy 分配值得迁移。

`src/main/java/github/kasuminova/ecoaeextension/common/tile/ecotech/estorage/EStorageCellDrive.java`

- 核心字段：`driveInv`、`inventoryHandlers`、`cellHandler`、`watcher`、`isCached`、`lastWriteTick`、`writing`。
- 核心方法：`updateHandler(refreshState)`、`getHandler(channel)`、`onChangeInventory()`、`updateWriteState()`、`postChanges()`、`saveChanges()`。
- 性能职责：cell handler/watchers 懒缓存；插拔 cell 时才 invalid cache；写入灯效有 40 tick 衰减和 200 tick 静态刷新；cell 内容变化只 `markChunkDirty()`，不强制外观同步。
- 迁移价值：很高。当前 `ECODriveBlockEntity.getCellInventory()` 每次构造/查找风险较大，可迁移 drive-level cached inventory + explicit invalidation。

`src/main/java/github/kasuminova/ecoaeextension/common/estorage/ECellDriveWatcher.java`

- 核心方法：`injectItems()`、`extractItems()`。
- 性能职责：只在 MODULATE 且实际数量变化时 post storage alteration，并调用 `drive.onWriting()` 更新写入灯效。
- 迁移价值：高。当前可借鉴“内容变化只触发 storage diff + 状态 dirty，不立刻全量统计/外观更新”。

`src/main/java/github/kasuminova/ecoaeextension/common/estorage/EStorageCellInventory.java`

- 核心字段：`cellItems`、NBT `ITEM_SLOT`/`ITEM_SLOT_COUNT`、`storedItemTypes`、`storedItemCount`。
- 核心方法：`getCellItems()`、`loadCellItems()`、`injectItems()`、`extractItems()`、`saveChangesES()`、`getUsedBytes()`、`getRemainingItemCount()`。
- 性能职责：cell item list 懒加载；插入/提取时增量维护 stored types/count；`saveChangesES()` 避免每次 save 都全量扫描 cellItems。
- 迁移价值：高。当前 `ECOStorageCell` 已有 lazy cellItems 和增量维护，建议继续审计 `saveChanges()` 是否存在全量 persist 热点。

### 4. 多方块结构

`src/main/java/github/kasuminova/ecoaeextension/common/tile/ecotech/EPartController.java`

- 核心字段：`EPartMap<P> parts`、`assembled`。
- 核心方法：`doControllerTick()`、`updateComponents()`、`canCheckStructure()`、`assemble()`、`disassemble()`。
- 性能职责：结构形成时一次性遍历 `foundPattern` 并缓存成员；formed 后只有 40 tick 周期检查或 `MMWorldEventListener.isAreaChanged()` 触发结构检查；part invalidate/chunk unload 时主动 disassemble。
- 迁移价值：高。当前 `NECluster` 已在形成时缓存成员，旧版的“区域变化监听 + formed 后低频检查”可用于减少当前结构检查/状态刷新成本。

`src/main/java/github/kasuminova/ecoaeextension/common/util/EPartMap.java`

- 核心字段：`Reference2ObjectOpenHashMap<Class<?>, List<P>> parts`。
- 核心方法：`addPart()`、`getParts(Class)`、`assemble()`、`disassemble()`、`clear()`。
- 性能职责：按具体类和父类建立成员索引，`getWorkers()`/`getCellDrives()` 是 O(1) map lookup，不需要每次 filter。
- 迁移价值：中。当前 `NECraftingCluster`/`NEComputationCluster` 已有 typed lists；如果后续 part 类型增加，可抽象类似 typed index，但不必现在重构。

### 5. 配方查询 / Recipe logic

旧项目在本次扫描范围内没有发现类似当前 IWS 的 `lastRecipe`、`recipeDirty`、`recipeInputSignature` 或 null recipe cache。F 系列主要不是查询 MM recipe，而是 AE crafting pattern provider + worker queue；配方详情由 `EFabricatorPatternBus.details` 按槽缓存。

- 迁移价值：对 IWS 的直接参考较弱。
- 当前分支的 `recipeCacheValid` 是必要优化，旧版没有更完整的 IWS null recipe cache 设计可吸收。
- 旧版可借鉴的是 pattern details cache，而不是 machine recipe lookup cache。

### 6. 网络同步 / GUI 数据

`src/main/java/github/kasuminova/ecoaeextension/common/handler/EFabricatorEventHandler.java`

- GUI 打开后每 10 tick 发送一次 controller cached packet；首两 tick 允许快速首包。
- `controller.getGuiDataPacket()` 只在 `guiDataDirty` 或 null 时重建。

`src/main/java/github/kasuminova/ecoaeextension/common/handler/ECalculatorEventHandler.java`

- 同样每 10 tick 发送 C 系列 GUI packet，packet 本体由 controller dirty cache 控制。

`src/main/java/github/kasuminova/ecoaeextension/common/handler/EStorageEventHandler.java`

- Storage GUI 每 20 tick 发送一次 `new PktEStorageGUIData(controller)`，没有 controller dirty packet cache。

`src/main/java/github/kasuminova/ecoaeextension/common/network/PktEFabricatorGUIData.java`

- packet 构造时读取 controller cached stats、worker queue peek 和 queue size。

`src/main/java/github/kasuminova/ecoaeextension/common/network/PktECalculatorGUIData.java`

- packet 构造委托 `ECalculatorData.from(controller)`，会扫描 AE crafting grid CPU 列表，成本比 F 高。

`src/main/java/github/kasuminova/ecoaeextension/common/network/PktEStorageGUIData.java`

- 每次构造扫描 drives 并计算 cell data，Storage 旧版 UI 同步比 F/C 弱。

## 二、tick 调度策略结论

旧版 tick 策略有明显分层：

1. `EPartController.doControllerTick()` 开头先做结构检查/formed 早退。结构不 formed 直接 disassemble/return。
2. formed 后 `canCheckStructure()` 不是每 tick 全量检查：已形成结构每 40 tick 检查一次，或通过区域变化监听判断。
3. F 系列 `EFabricatorController.onSyncTick()` 先判断 `channel == null || !proxy.isActive()`，再用 `workDelay` 控制是否进入 async 工作阶段。
4. C 系列 `ECalculatorController.onSyncTick()` 只每 5 tick 标记 GUI dirty，不执行 heavy CPU 更新。CPU 实际执行挂在 AE2 crafting grid update。
5. Storage `EStorageController.onSyncTick()` 每 5 tick 更新 drive 写入状态和 energy cell 容量，不每 tick 扫描 storage usage。
6. client-only 外观隐藏逻辑放在 client scheduler/lifecycle 中，server tick 路径不依赖客户端类执行。

强于当前项目的地方：

- 旧版 formed 后结构检查低频且有区域 dirty 判断；当前项目更多依赖 AE2 cluster 生命周期，但部分 controller `updateState(true)` 仍可能触发统计重算。
- 旧版 F/C GUI 有 packet object dirty cache；当前分支实现了 revision skip，比旧版更适合 Native UI，但 Storage menu 仍按实时刷新路径创建复杂 state。
- 旧版 drive handler 和 watcher 懒缓存，插拔时失效；当前 `ECODriveBlockEntity.getCellInventory()` 多处直接调用，仍有重复构造/查询风险。
- 旧版 worker 状态更新有防抖和独立状态包；当前分支已经做 running thread 增量，但 worker 外观/状态包仍可继续降频。

弱于当前项目的地方：

- 旧版 C 系列 `ECalculatorData.from()` 发送 UI 时扫描 AE crafting grid CPU 列表，缺少 revision/diff。
- 旧版 Storage GUI 每 20 tick 直接构造新 packet，仍扫描 drives，没有 dirty/revision。
- 旧版大量依赖 mixin 覆写 AE2 internals，维护风险高。
- 旧版 F 系列 `hasWork()`、`isQueueFull()`、`getEnergyStored()` 仍遍历 workers，只是 worker 数量较小且频率被限制。

明确结论：旧版 tick 性能核心优势不是某个算法，而是“formed 缓存 + 低频结构检查 + active/idle 早退 + packet dirty cache + drive/cell lazy cache”。当前分支已经吸收 F/C 和 UI revision 的一部分，但 Storage 和 drive cache 仍是主要空缺。

## 三、多方块缓存策略对照

旧版：

- `EPartController.updateComponents()` 在结构匹配后遍历 `foundPattern`，把 `AbstractEPart` 放入 `EPartMap`。
- `EPartMap` 按 class 和父类索引，controller 的 `getWorkers()`、`getPatternBuses()`、`getThreadCores()`、`getCellDrives()` 都是读取缓存。
- `AbstractEPart.invalidate()` 和 `onChunkUnload()` 会通知 controller `disassemble()`。
- `canCheckStructure()` formed 后低频检查，并使用 `MMWorldEventListener.isAreaChanged()` 判断结构区域是否变化。

当前：

- `NECluster` 在 cluster 形成时保存 `blockEntities`，`NECraftingCluster`/`NEComputationCluster`/`NEStorageCluster` 维护 typed lists。
- 当前分支已给 F/C controller 增加 dirty stats，避免 UI state 创建触发结构重算。
- Storage cluster 已有 typed lists，但 `ECOStorageSystemBlockEntity.createStorageUiState()` 和 `updateInfos()` 仍会扫描 drives。

建议：

- 不需要迁移 `EPartMap` 抽象本身，当前 typed lists 已足够。
- 需要把 Storage 的统计从“创建 UI/20 tick 时扫描 drives”改成 “drive/cell change 标 dirty，必要时 ensure stats current”。
- 如果后续发现结构检查本身热点，再考虑类似 `isAreaChanged` 的区域 dirty/revision，而不是每次 controller 状态更新都推统计重算。

## 四、合成/计算性能实现对照

旧版已具备：

- CPU/job 缓存：C 系列 thread core 持有 `cpus` 列表；controller 持有 `virtualCPU`。
- running thread 缓存：thread core `cpus.size()` 直接代表 active CPU 数；但 UI 构造仍扫描 CPU 列表计算 hyper threads。
- worker 分发：F 系列 `offerWork()` 顺序填充 worker queue，按 remaining space split work。不是 round-robin，但能避免单个 worker 溢出。
- active bytes：C 系列通过 CPU `availableStorage` 存储 job bytes，`getAvailableBytes()` 用 total - threadCores usedStorage 计算。当前分支的 `activeJobBytes` 更直接。
- accelerator 缓存：`parallelism` 在结构变化/overclock 状态变化时重算，并写入已有 CPU。
- job submit/finish/cancel 增量：submit 通过 mixin 通知 `onVirtualCPUSubmitJob()`；destroy/cancel 通过 mixin 通知 thread core 移除 CPU。
- UI 同步：F/C 每 10 tick 发包，F/C packet 可 dirty cache；无 revision。
- idle/active：C/F 都通过 channel active 和 queue/cpu 状态切换控制可见 CPU/provider。

当前分支已吸收：

- `NEUiStateMachineMenu` revision skip。
- IWS null recipe cache。
- F 系列 `structureStatsDirty + uiRevision`。
- C 系列 `totalStorageBytes / activeJobBytes / activeCpuCount`。
- worker `runningThreadCount` 增量通知。

旧版仍可吸收的设计：

- F 系列 worker queue/status 防抖：worker 外观状态不必每次 running threads 变化都 `markForUpdate()`。
- Pattern bus 单槽 details cache + 单槽 UI diff。
- C 系列 thread core update tag 不携带完整 job NBT 的原则。
- 可选 performance recorder 只在调试/配置开启时采集，不进入默认 hot path。

不建议迁移：

- 旧版 `MixinCraftingCPUClusterTwo.executeCrafting()` 覆写。
- 旧版 virtual CPU 伪装进 AE2 CraftingGridCache 的方式。
- 直接拦截 AE2 `markDirty()` 的 mixin。

## 五、存储系统性能实现对照

旧版 EStorage：

- drive 级缓存：`EStorageCellDrive` 缓存 `cellHandler`、`watcher`、`inventoryHandlers`，`isCached` 控制懒重建。
- content diff：`ECellDriveWatcher` 在 inject/extract 实际变化时 post alteration 并 `onWriting()`。
- 写入外观：`updateWriteState()` 用 40 tick 状态衰减，changed 时发小范围包，每 200 tick 静态刷新。
- 容量统计：cell inventory 增量维护 stored count/types；GUI packet 每 20 tick 扫 drives 汇总，旧版这里并不理想。
- energy：controller 用 priority queue 分配能量，避免每次从头遍历所有 energy cell 做低效填充/提取。

当前项目：

- `ECOStorageCell` 已有 lazy `cellItems` 和增量 insert/extract 维护。
- `ECODriveBlockEntity` 多处调用 `getCellInventory()`，并在 `notifyPersistence()` 中 `updateStorageProviderState()` + `notifyControllerRefresh()`。
- `ECOStorageSystemBlockEntity` `tickingRequest()` 每 20 tick `updateInfos()`，`createStorageUiState()` 也会重算并扫描 drives。
- Storage menu 未启用 revision skip，符合实时刷新要求，但会让复杂 state 构造继续发生。

可迁移优化：

1. `ECODriveBlockEntity` 增加 cached `IECOStorageCell` 和 cached handler/inventory invalidation。插拔 cell 或 stack NBT replacement 时失效；内容变化只保存，不重建 handler。
2. `ECOStorageSystemBlockEntity` 增加 `storageStatsDirty + uiRevision`，drive 插拔、cell content change、energy cell significant change 时标 dirty；UI createState 只读缓存或低频 ensure。
3. Storage UI 可保留实时刷新但采用 revision/interval 双策略：内容变化递增 revision；无变化时跳过复杂 createState；能量如果必须实时，可独立低频 revision。
4. drive 外观只在 `HAS_CELL`、mounted/online、CellState 变化时 `markForUpdate()`；内容数量变化不触发外观更新。

## 六、配方查询缓存分析

旧版没有当前 IWS 这种 machine recipe lookup 缓存。相关设计差异：

- F 系列 pattern bus 缓存的是 AE pattern details，不是 recipe manager 查询结果。
- `EFabricatorPatternBus.onChangeInventory()` 只刷新变化 slot，触发 AE pattern changed。
- 旧版没有发现 null recipe cache、input signature 或 output blocked cache。

对当前 IWS：

- 当前 `recipeCacheValid` 方向正确。
- 后续可补 `recipeInputRevision` 或 input signature，以减少“多个 setter 漏掉 invalidate”的风险。
- 输出空间不足不应重新 find recipe；应把 recipe cache 和 output-can-insert cache 分开。
- 只有 recipe id 变化才 reset `processingTime` 的规则应保留。

## 七、网络/UI 同步分析

旧版同步特点：

- F/C controller GUI：每 10 tick 发一次，首包快速；controller 内有 `guiDataDirty` 和 cached packet。
- Storage GUI：每 20 tick 发一次，每次构造新 packet，弱于 F/C。
- Worker/block 外观：worker status 和 drive writing 用小包，避免完整 BE update tag。
- Pattern search：pattern bus 单槽变化发 `SINGLE` update，不全量发 pattern 列表。

当前 Native UI 建议：

- F/C 已有 revision skip，方向优于旧版。
- Storage 不建议简单照搬 revision skip，因为用户要求实时刷新；但可以引入 storage stats revision，只在 drive/cell/energy 变化时构造新 state，保留固定心跳作为兜底。
- Pattern bus UI 应优先单槽 diff，不要每 tick/每次 GUI 打开重建所有 pattern details。
- `markForUpdate()` 应只用于 block model、灯光、mounted/online、HAS_CELL、CellState、writing visual；纯数值通过 UI state packet。

## 八、结论矩阵

| 旧版机制 | 旧版文件 | 当前对应文件 | 是否已实现 | 是否建议迁移 | 风险 | 备注 |
|---|---|---|---|---|---|---|
| 多方块成员缓存 | `EPartController.java`, `EPartMap.java` | `NECluster.java`, `NECraftingCluster.java`, `NEComputationCluster.java`, `NEStorageCluster.java` | 已实现大部分 | 部分 | 低 | 当前 typed lists 足够；可补区域 dirty/低频结构检查。 |
| 结构 dirty 标记 | `EPartController.canCheckStructure()` | `NECluster`, F/C controller dirty stats | 部分 | 是 | 中 | 旧版有区域变化监听；当前可先只优化 stats dirty。 |
| recipe null cache | 未发现 | `ECOIntegratedWorkingStationBlockEntity.java` | 已实现 | 否 | 低 | 当前分支的 `recipeCacheValid` 已比旧版强。 |
| recipe input signature | 未发现 | `ECOIntegratedWorkingStationBlockEntity.java` | 未实现 | 是 | 低 | 用 revision/signature 防漏 invalidate。 |
| active/idle tick | `EFabricatorController.onSyncTick()`, `ECalculatorController.onSyncTick()` | F/C controller tick/grid tick | 部分 | 是 | 中 | F/C 可继续减少空闲 tick 和外观更新。 |
| worker round-robin | `EFabricatorController.offerWork()` | `ECOCraftingWorkerBlockEntity`, crafting logic | 未完全实现 | 谨慎 | 中 | 旧版是顺序填充+split，不是真 round-robin；不要改 AE2 CPU 语义。 |
| CPU active bytes 增量 | `ECalculatorController.onVirtualCPUSubmitJob()`, `ECalculatorThreadCore` | `NEComputationCluster.java` | 已实现更直接版本 | 否 | 中 | 当前 `activeJobBytes` 比旧版 `availableStorage` 复用字段更清晰。 |
| running thread 增量 | `ECalculatorThreadCore.cpus`, `EFabricatorWorker.queue/status` | `ECOCraftingSystemBlockEntity.runningThreadCount` | 已实现 | 部分 | 低 | 可补 worker 状态防抖。 |
| storage capacity 增量 | `EStorageCellInventory.saveChangesES()` | `ECOStorageCell.java`, `ECOStorageSystemBlockEntity.java` | cell 内已部分实现，controller 未实现 | 是 | 中 | 重点优化 Storage controller/drive cache。 |
| UI state diff/revision | `guiDataDirty`, cached packet | `NEUiStateMachineMenu.java` | F/C 已实现 | Storage 可迁移变体 | 低 | Storage 需兼顾实时刷新。 |
| markForUpdate 降频 | `EStorageCellDrive.markDirty() -> markChunkDirty()`, worker 小包 | `ECODriveBlockEntity.java`, F/C worker | 部分 | 是 | 低 | 内容数量变化不应外观同步。 |
| packet sync 节流 | F/C 10 tick, Storage 20 tick, worker/drive 小包 | Native UI menus/network packets | 部分 | 是 | 低 | 当前 revision skip 更好；Storage 仍需节流。 |
| client-only 渲染缓存 | `BlockModelHider`, client scheduler | client render/state paths | 部分 | 谨慎 | 中 | 只迁移原则，避免 server path 引 client 类。 |

## 九、当前分支需要修正的问题

1. `ECOStorageSystemBlockEntity.createStorageUiState()` 在 server side 会调用 `recalculateStorageStats()`，随后又扫描 cluster drives 分组。Storage GUI 打开时仍可能每 sync 周期重复扫描所有 drive/cell。
2. `ECOStorageSystemBlockEntity.tickingRequest()` 每 20 tick `updateInfos()`，不区分内容是否变化。旧版 Storage 也不完美，但 drive watcher 至少只在内容变化时触发 alteration/writing。
3. `ECODriveBlockEntity.getCellInventory()` 被多处调用，缺少旧版 `isCached` 风格的 drive-level inventory cache。若 `ECOStorageCells.getCellInventory()` 构造成本高，会成为 Storage UI 和 mount path 热点。
4. `ECODriveBlockEntity.notifyPersistence()` 对每次内容保存都 `updateStorageProviderState()` 和 `notifyControllerRefresh()`；可改成内容 stats dirty，不必每次请求 storage provider update，除非 CellState/idle drain/handler 变化。
5. F 系列 `updateInfo()` 仍存在兼容方法且内部直接 mark dirty + ensure；应确认没有 hot path 频繁调用它，否则 dirty 化收益会被抵消。

## 十、后续可执行任务

### P0：低风险、立即可做

1. Storage controller stats dirty 化
   - 目标文件：`ECOStorageSystemBlockEntity.java`, `ECODriveBlockEntity.java`, `NEStorageControllerMenu.java`
   - 修改点：新增 `storageStatsDirty`、`uiRevision`、`ensureStorageStatsCurrent()`；drive 插拔/content change 标 dirty；Storage menu 使用 revision，但保留低频兜底刷新。
   - 预期收益：减少 Storage UI 打开时和 20 tick 周期 drive 全量扫描。
   - 风险：低。需要确保能量和容量变化仍更新。
   - 验证方式：插拔 item/fluid/chemical cell、写入/取出物品、Storage UI 数据变化。
   - 是否需要 Spark：不需要，作为结构性优化可先做。

2. Drive cached cell inventory
   - 目标文件：`ECODriveBlockEntity.java`, `ECOStorageCells.java`
   - 修改点：drive 持有 cached `IECOStorageCell`；`setCellStack/loadTag` invalid cache；content save 不重建 inventory。
   - 预期收益：降低 `getCellInventory()` 在 UI/mount/provider path 的重复构造成本。
   - 风险：低到中。必须保证 NBT 保存回原 cell stack。
   - 验证方式：存取后重进世界，cell 内容不丢；不同类型 cell 插拔正常。
   - 是否需要 Spark：不需要，但可用 profiler 验证收益。

3. Pattern bus slot details cache 审计
   - 目标文件：当前 pattern bus/core 相关 block entity。
   - 修改点：确认 pattern details 是否按槽缓存；若没有，增加 per-slot cache 和单槽 invalidation。
   - 预期收益：减少 pattern provider 和 UI 搜索时重复解析 pattern。
   - 风险：低。
   - 验证方式：插入/移除 pattern 后 AE crafting pattern list 正确刷新。
   - 是否需要 Spark：不需要。

### P1：中风险、需要测试

1. Storage content diff/revision 分离
   - 目标文件：`ECOStorageCell.java`, `ECODriveBlockEntity.java`, `ECOStorageSystemBlockEntity.java`
   - 修改点：区分 content revision、visual state revision、provider handler revision；内容数量变化只递增 stats/ui revision。
   - 预期收益：显著减少 `markForUpdate()` 和 provider update 噪声。
   - 风险：中。AE storage alteration 和 UI 统计可能不同步。
   - 验证方式：大量自动输入/输出时观察 UI、驱动灯、AE 终端内容。
   - 是否需要 Spark：建议有。

2. Worker 状态防抖与局部同步
   - 目标文件：`ECOCraftingWorkerBlockEntity.java`, crafting controller network/UI state。
   - 修改点：worker RUN/IDLE/OFF 视觉状态加最小间隔；running thread 继续走 controller revision。
   - 预期收益：减少高频工作时外观包。
   - 风险：中。外观可能延迟。
   - 验证方式：持续合成时 worker 状态变化、UI running threads 正确。
   - 是否需要 Spark：建议有。

3. IWS input signature
   - 目标文件：`ECOIntegratedWorkingStationBlockEntity.java`
   - 修改点：为物品/流体/升级配置维护 `recipeInputRevision` 或 hash signature；`ensureRecipeCached()` 比较 signature。
   - 预期收益：防止漏 invalidate 导致缓存过期，也避免重复 findRecipe。
   - 风险：中。signature 必须覆盖所有影响配方的输入。
   - 验证方式：无配方、有配方、切换输入、输出堵塞、升级变化。
   - 是否需要 Spark：不需要。

### P2：高风险、需要 Spark 证明瓶颈后再做

1. AE2 crafting provider 批量入队策略
   - 目标文件：当前 crafting worker/core/provider 相关类。
   - 修改点：只借鉴旧版 EFabricator 的“按 worker 剩余容量批量接受 work”，不改 `ECOCraftingCPULogic.executeCrafting`。
   - 预期收益：大批量 pattern dispatch 时减少中间状态和循环次数。
   - 风险：高。容易改变 AE2 crafting plan/CPU 语义。
   - 验证方式：大规模合成、取消、缺料、输出堵塞、容器返还。
   - 是否需要 Spark：必须。

2. C 系列 CPU lifecycle 更细粒度 diff
   - 目标文件：`NEComputationCluster.java`, computation controller/menu/network。
   - 修改点：active CPU 列表按 submit/finish/cancel 发送 diff，而不是 UI state 包含完整列表。
   - 预期收益：大量 CPU 同时运行时减少 UI packet 构造和带宽。
   - 风险：高。客户端状态一致性复杂。
   - 验证方式：同时提交/取消/完成多个 AE2 job，断线重开 UI。
   - 是否需要 Spark：必须。

3. 可选 TimeRecorder / profiling hooks
   - 目标文件：F/C controller、worker、storage stats、IWS recipe lookup。
   - 修改点：加配置开关的轻量计数器或 Spark marker，不默认启用。
   - 预期收益：定位真实热点，避免盲目优化。
   - 风险：中到高。默认启用会引入新开销。
   - 验证方式：开关关闭时无额外日志/同步；开启后指标稳定。
   - 是否需要 Spark：必须。

## 最终建议

下一阶段最值得做的是 Storage：`ECODriveBlockEntity` 的 drive-level cell inventory cache，以及 `ECOStorageSystemBlockEntity` 的 storage stats dirty/revision。旧版 F/C 的大部分低风险思想当前分支已经吸收；剩余旧版最有价值的设计集中在 drive watcher、cell inventory 增量保存、pattern slot cache 和 GUI packet dirty cache。

不建议把旧版 AE2 mixin 覆写、virtual CPU 注入或 `executeCrafting()` 改写迁移到当前项目。这些机制在 1.12.2 环境可行，但对 1.20.1 AE2 的行为语义和维护风险都过高。
