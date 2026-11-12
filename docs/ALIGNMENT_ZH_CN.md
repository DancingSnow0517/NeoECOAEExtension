# 与 1.21.1 主线的对齐：26.1

本文件记录 26.1 版相对 1.21.1 主线的差异，分为特殊（不对齐）、落后（要补）、领先（主线没有）三类。主线的总表在 `NeoECOAEExtension-1.21.1/docs/ALIGNMENT_ZH_CN.md`。移植时有意删减的内容另见 [26.1-migration-reduction-notes.md](26.1-migration-reduction-notes.md)（其中第 1 节的版本数据是 11-02 的，已过时）。

- 调查日期：2026-11-11
- 本版基线：`4e93967d`（分支 `26.1`，26.2.15-beta）
- 主线基线：`2e494131`
- 调查方法：文件清单、行数和提交标题对照。同名文件没有逐行比对，没有运行构建或游戏。主线 11-03 之后的提交按标题匹配，改了标题移植进来的提交会被误算成落后。
- 路径均相对于 `src/main/java/cn/dancingsnow/neoecoae/`。

**水位**：只同步到主线 11-02 左右。主线 11-03 到 11-11 约 100 个提交，按标题只对上 2 个。

**不能直接合并主线**：主线历史改写过，本版合并提交 `46d9caec` 合入的 `8d10df7a` 已不在主线上，共同祖先是 `787065d9`（2026-10-31）。11-02 的 4 个规划修复在主线上换了哈希（`a29c1690`、`6afb3984`、`b28093e1`、`b6c35334`）。按提交 cherry-pick 或按文件移植。

**调查时的未提交改动**：35 个文件（+586/−169），是在移植主线的精简合成模式（`ec35bf8f`、`7c7acbd4`）。`CasingBlock`、`ECOPlanMaterialValidator` 已与主线一致；`ECOCraftingPlannerService`、`CraftConfirmMenuMixin`、`ComponentPlanner` 离主线还各差几百行。

## 特殊（不对齐）

| 项 | 位置 |
| --- | --- |
| 版本：MC 26.1.2、NeoForge 26.1.2.126、LDLib2 26.1.2.45 | `gradle.properties` |
| 渲染和模型管线按 26.1 的模型 API 重写 | 本版 `client/model/ECOComputationDriveModel`、`client/item/ECOStorageCellStateTintSource`、`util/ECOModelUtil`、`api/client/`；主线的 `*BakedModel`、`client/rendering/`、`ModelBakeryMixin`、`CellModelGenerator` 没有对应 |
| 存档与容器 API 桥接 | `util/LegacyNbt`、`crafting/ECOLegacyAENbt`、`api/me/NbtValueIO`、`blocks/entity/FluidTankResourceHandler`、`InternalInventoryItemHandler` |
| 界面适配 | `gui/NEGuiColors`、`gui/common/{GuiDraw,ElementRenderHooks}`、JEI/REI 的配方 Wrapper |
| 不编译的联动（上游没有 26.1 版）：appbot、appliedcreate、appliedpneumatics、appliedsoul、appmek、arsenergistique、dataenergistics、emi、megacells；mixin：appbot、fluxnetworks、omnisequence、thunderbolt、useless | `build.gradle` 第 295–308 行 |
| 运行时停用的联动：ae2lt、ae2_pattern_disk、thunderbolt、mekanism、appmek、fluxnetworks 等 | `NeoECOAE.java` 的 `UNPORTED_INTEGRATIONS` |
| 建立在停用联动上的主线功能暂不移植 | 主线 `integration/emi/EmiBookmarkAccess`（`c488c56c`、`f657c46d`）、`compat/thunderbolt/ECOThunderboltPlanningBridge`、`compat/ae2lt/ECOAe2LtCpuStatus`（`54f69e83`） |
| REI 插件与 `GhostItemTargets`，补 EMI 的缺位 | `integration/` |
| 26.1 专用的测试基础设施 | `ClientTestSupport`、`UiTestRendering`、`StaticMockLeakGuard`、`TestFmlLoaderSessionListener` 等 |
| 迁移文档写明的删减：`ECOInfiniteStorageData` 不再报告 `WRITE_FAILED`；图表视图改为录制后回放；`accesstransformer.cfg` 未恢复部分条目 | `docs/26.1-migration-reduction-notes.md` |
| 宣传图、界面冒烟数据包等工具 | `tools/` |

上游模组出 26.1 版后，对应联动从本节移到落后。

## 落后（主线有，本版要补）

### 工作站

1. **修复批量发配并优化供能动画**（主线 `2e494131`）。`ECOBatchAdmission` 29 行（主线 63 行），`ECOProcessingPatternDispatcher` 451 行（主线 499 行）。
2. **工作站供能卡片**（主线 `6198946a`）。缺 `gui/workstation/LargeWorkstationPowerPriorityCard`；`LargeWorkstationUI` 136 行（主线 183 行）。
3. **工作站模型和动态配色**（主线 `7d7c001a`）。缺 `blocks/ECOLargeIntegratedWorkingStationCasing` 及其方块实体、`client/texture/WorkstationSpriteSource`、`textures/block/working_station/cooling_controller/`；主线已删的旧贴图还在。动态配色要用 26.1 的渲染 API 重新实现。
4. **冷却与供能调整、智能冷却面板**（主线 `e815cfdd`、`e7cb1a88`、`07941311`）。`SmartCoolingModuleUI` 131 行（主线 522 行）；缺 `gui/common/{AE2TextButton,AE2TextField}`。
5. **智能冷却控制器配方、冷却设备外观**（主线 `7952b49f`、`2a15bf47`）。本版 `EcoMachineRecipes` 已有智能冷却模块配方，但内容与主线不同；贴图没有比较。未核实。
6. **其他工作站提交**：`29bdc071`（按付费工作 tick 产热）、`87f336c9`（`recipe/LargeWorkstationRecipeIndex`）、`32f576c4`（`integration/xei/multiblock/WorkstationPreviewModules`）、`65f37cfd`（`integration/guideme/NEGuideRecipeTypes`）、`f648b9de`、`8f54eea7`、`2db474bd`。
7. **配置**。`NEConfig` 缺 `outputTransferIntervalTicks`、`outputFlushThreshold`（主线 `4091d1e3`）、`smartCoolingRefillThresholdPercent`；缺 `config/KeyReuseConfig`、`config/DevToolsConfig`。

### 规划与执行

8. **精简合成模式**（主线 `ec35bf8f`、`7c7acbd4`）。在未提交改动中，尚未完成。
9. **清理冗余规划实现、统一计划准入**（主线 `9fa1d04b`、`ac366d29`）。本版仍保留 `cycle/UnsupportedCycleSolver`、`growth/SinglePatternGrowthCycleSolver`、`result/{CycleResourceLedger,OwnershipEvent,OwnershipReplayHarness,PrimitiveOwnershipState,ReferenceOwnershipLedger}`、`solve/CandidateAttemptStatus`，这些应删除。
10. **精确数量换成 U128**（主线 `1f07ea30`、`59882cc3`、`34446fbb`）。本版还是 U126。
11. **规划结构与循环求解**（主线 `1572c494`、`ce388576`、`7989b3b6`）。缺 `ECOAutoCraftingPlanning`、`cycle/CycleStartupSolver`、`solve/AcyclicSolveMetrics`、`snapshot/CycleRecursionPreview`。
12. **合成报告与递归视图**（主线 `b35f498f`、`b2f96739`、`98efa123`、`07cca830`、`db46f54e`）。缺 `network/CraftingReportStream`、`client/gui/crafting/{ECOCraftReportTooltips,ECOCraftingListImageExport}`、`client/gui/graph/{GraphMaterialCard,RecursiveCycleRenderer}`、`crafting/graph/{CycleStripLayout,RecursiveCycle*}`。
13. **合成状态面板**（主线 `ab76211f`、`2f2997b6`、`ac3e09c1`、`d4b322aa`、`2e1c1cc1`）。缺 `gui/computation/ECOCraftingStatusUI`、`crafting/display/processing/{ECOProcessingMachineActions,ECORemoteMachineReach}`、`MenuOpenerMixin`、`PlayerRemoteMachineReachMixin`。
14. **悬停卡片**（主线 `f9e9cf7c`、`6144b086`、`c55e7e05`）。缺 `gui/computation/{ComputationCards,ComputationStatCards}`、`gui/crafting/{ComputationEndgameCard,CraftingListCard,VirtualModeStatusCard}`。本版自己的 `33cfafdc`（统计卡片）是否等价未核实。
15. **合成卡与 ME Requester 自动下单**（主线 `f3b572b8`）。缺 `MultiCraftingTrackerMixin`、`mixins/compat/merequester/RequesterPlanningMixin` 及其 mixin 配置；merequester 不在停用名单上。
16. **忽略 NBT 的处理样板**（主线 `c7ab9bc1`）。缺 `ECOComponentInsensitiveProcessingPattern`、`PatternDetailsComponentPolicyMixin`。
17. **分段库存计数**。本版 `1bffc4e8` 对应主线 `2f8dd0fd`，但主线还改了 `InfiniteStorageAmounts`（+31 行）和对应测试，本版缺这部分。

### 存储与 API

18. **存储同步与持久化**（主线 `9ea8a6b4`）。缺 `blocks/entity/storage/ECOStorageChunkPersistence`；`ECOInfiniteStorageData`、`ECOStorageInfiniteRestore` 各少几十行；`docs/API*.md` 缺对应内容。
19. **存储性能优化**（主线 `292dec2b`、`5c4244c4`、`ea36d923`、`737d0913`、`7da775d9`、`f9e648f5`）。缺 `storage/{ECOStorageListingCache,StorageChangeLog,StoragePerformance,StorageReturnBudget,StorageThresholdCache,StorageWatcherScope}`、`crafting/execution/ECOCpuInventoryReturn`、`crafting/display/terminal/`（5 个）、`mixins/ae2/storage/`（7 个）。
20. **附属存储盘优先级接口**（主线 `0b756492`、`2754cc6c`、`6724e698`）。缺 `api/storage/{ECOStorageCellPriorities,IECOCellWorkbenchDrive,IECOConfigurableCellDrive,IECOConfigurableCellHost}`、`gui/storage/StorageCellPriorityUI`。
21. **CPU 档位角标**（主线 `2c9f30ab`）。缺 `api/ECOCpuTierGlyph`。

### 其他

22. **AE key 复用**（主线 `bc596964`、`a1aa40fd`、`57bf8504`、`b37a7b8a`、`04e8d0a4`、`9f50fe2c`）。缺 `compat/ae2/keyreuse/`（13 个）、`mixins/keyreuse/`、`mixins/ae2/key/`、`neoecoae.key_reuse.mixins.json`。mixin 目标要对照 AE2 26.1 逐个核对。
23. **开发工具**（主线 `ccbfdb64`、`8cc03e22`、`04fdd1a1`）。缺整个 `dev/` 包。优先级低。
24. **测试**。375 个文件，主线 430 个；主线独有的 67 个基本对应上面各项。
25. **语言**。`en_us` 1354 条（主线 1755），`zh_cn` 1429 条（主线 1758）。
26. **CHANGELOG**。没有主线的 `21.2.15` 和未发布两节。
27. **指南**。文件列表与主线相同，内容没有比较；主线 `65f37cfd`、`e815cfdd` 改过 `large_integrated_working_station.md`，本版很可能是旧版。

## 领先（本版有，主线没有）

基本没有。以下提交主线都已有对应：

| 本版提交 | 主线对应 |
| --- | --- |
| `4e93967d` 优化合成产物回流 | `225f1a34` |
| `1bffc4e8` 接入分段库存计数 | `2f8dd0fd`（主线更完整，见落后第 17 条） |
| `7fe70902` 修复合成供能舍入误判与精确订单投料 | `fa1dc3f9` |
| `07b4ef8c`、`b1338ddf` | `6bec19b4`、`472d8f0d` |

可能适用于主线、但只比过提交标题的：`d29c481e`（统一合成溢出批量上限并修正交换网络执行耗时）、`cce425bd`（存储主机右键不再直接取出存储盘）。

## 未核实

- `7952b49f` 的配方材料和 `2a15bf47` 的贴图是否已在本版。
- `all/` 的方块和物品注册没有逐项比对；配方 JSON、`build.gradle` 依赖坐标没有逐条比对。
- `ECOCraftConfirmScreen`、`CraftConfirmMenuMixin` 的差异中哪些是 API 写法、哪些是功能差异。
- merequester、key 复用、开发工具在 AE2 26.1 上能否直接移植。
