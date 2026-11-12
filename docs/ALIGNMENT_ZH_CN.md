# 与 1.21.1 主线的对齐：1.20.1

本文件记录 Forge 1.20.1 版相对 1.21.1 主线的差异，分为特殊（不对齐）、落后（要补）、领先（主线没有）三类。主线的总表在 `NeoECOAEExtension-1.21.1/docs/ALIGNMENT_ZH_CN.md`。

- 调查日期：2026-11-11
- 本版基线：`5add91e6`（分支 `1.20.1`，20.5.7）
- 主线基线：`2e494131`
- 调查方法：文件清单、标识符计数、行数和提交标题对照。同名文件没有逐行比对，没有运行构建或游戏。
- 路径均相对于 `src/main/java/cn/dancingsnow/neoecoae/`。

**水位**：已同步到主线 11-10，只差 11-11 当天的提交。轻量合成模式（`12f0bdac`）对应主线 `ec35bf8f` 和 `7c7acbd4`，已对齐。大型工作站方块实体基本对齐，缺的三个方法见落后第 6 条。

## 特殊（不对齐）

| 项 | 位置 |
| --- | --- |
| Forge 兼容层：NBT、配方、网络、同步、菜单 | `platform/`（约 110 个文件） |
| UI 用 LDLib1 加移植的 JSON 布局运行时，主线用 LDLib2 | `platform/ui/NEUiJsonBridge.java`、`platform/ui/runtime/`、`gui/ldlib/` |
| 存储持久化用按盘 UUID 的 SavedData，主线用 `ECOStorageChunkPersistence` | `storage/persistence/ECOCellSavedData.java`、`StorageCatalogSavedData`、`Legacy2042*` |
| 物品数据用 NBT，主线用 DataComponents | `platform/nbt/NECellNbt.java`、`NEItemData.java` |
| GTCEu / GTL 联动 | `compat/gregtech/`、`mixins/compat/gregtech/`、`storage/transfer/GTLFastInfinityTransfer.java`、`recipe/profile/`、`config/RecipeProfileConfig.java` |
| KubeJS 6 的写法，主线是 Schema 写法 | `integration/kubejs/schema/` |
| Forge 渲染修补（Embeddium、区块重建） | `client/rendering/EmbeddiumFixedGeometry.java`、`mixins/client/minecraft/ChunkRebuildTaskMixin.java` |
| 工作站模型适配 Forge 图集 | `SpriteSourcesAccessor`（`f50999a3`） |
| 统一的可选附属 mixin 插件 | `mixins/compat/ECOOptionalAddonMixinPlugin.java` |
| 诊断探针、移植验证工具 | `client/diagnostics/`、`platform/diagnostics/`、`tools/`、`validation/`、`src/acceptance/` |

主线有 Useless Mod、OmniSequence、AE2 Pattern Disk、MekEnergistics 联动，本版没有。这些模组是否有 1.20.1 版未核实；有的话应移到落后。

## 落后（主线有，本版要补）

### 规划与执行

1. **清理冗余规划实现**（主线 `9fa1d04b`）。本版仍保留 `crafting/planner/cycle/UnsupportedCycleSolver`、`growth/SinglePatternGrowthCycleSolver`、`result/{CycleResourceLedger,OwnershipEvent,OwnershipReplayHarness,PrimitiveOwnershipState,ReferenceOwnershipLedger}`、`solve/CandidateAttemptStatus`。
2. **统一计划准入、统一规划结构快照**（主线 `ac366d29`、`1572c494`）。缺 `crafting/planner/ECOPlanningImprovements.java`、`crafting/execution/ECOCraftingPlanUpdater.java`。
3. **修复批量发配并优化供能动画**（主线 `2e494131`）。`ECOBatchAdmission` 缺 `StopReason` / `TRANSPORT_LIMIT`；`ECOProcessingPatternDispatcher`、`ECOAe2LtDirectDispatch` 比主线各少约 100 行。
4. **忽略 NBT 的处理样板与 Thunderbolt 规划桥**（主线 `c7ab9bc1`）。缺 `crafting/ECOComponentInsensitiveProcessingPattern.java`、`compat/thunderbolt/ECOThunderboltPlanningBridge.java`、`mixins/ae2/crafting/PatternDetailsComponentPolicyMixin`、`mixins/compat/thunderbolt/` 下三个 mixin。
5. **合成卡与 ME Requester 自动下单**（主线 `f3b572b8`，部分移植）。已有 `MERequesterPlanningMixin`，缺 `crafting/planner/ECOAutoCraftingPlanning`、`mixins/ae2/crafting/MultiCraftingTrackerMixin`、`crafting/execution/ECOCpuInventoryReturn`。

### 附属扩展 API

6. **扩展注册、特性、多方块、供能、工作站模块**。缺 `api/extension/{ECOAddonApi,ECOExtensionRegistry}`、`api/capability/ECOFeature`、`api/multiblock/{ECOMachineKind,ECOMultiblockDefinitions,ECOMultiblockParts,ECOMultiblockRequirement(s)}`、`api/power/{ECOPowerProfiles,ECOPowerRole}`、`api/workstation/{ECOWorkstationModules,ECOWorkstationRecipeAdapters}`。本版的 `recipe/LargeWorkstationRecipeAdapters` 是旧形态。工作站方块实体缺 `getInstalledModuleDescription`、`getInstalledModuleId`、`createAutoBuildButton`。`docs/API.md` 缺“Addon extension registration”“KubeJS large integrated workstation recipes”“Confirmation reports”三节。
7. **存储盘配置与优先级接口**（主线 `0b756492`、`2754cc6c`）。缺 `api/storage/{ECOStorageCellPriorities,IECOConfigurableCellDrive,IECOConfigurableCellHost,IECOCellWorkbenchDrive,ECOStorageChannelDisplays,INativeMEStorageRestrictedItem}`、`blocks/entity/storage/StorageCellWorkbench`、`gui/storage/StorageCellPriorityUI`、两个 `CellWorkbench*Mixin`。
8. **合成状态与 CPU 列表**。缺 `api/ECOCpuTierGlyph`（主线 `2c9f30ab`）、`api/me/menu/{ECOCpuListPage,ECOCpuListPageHost,ECOBulkMarkerMenu}`、`CpuSelectionListPagingMixin`、`client/rendering/ProviderLocationHighlight`、`compat/ae2lt/ECOAe2LtCpuStatus`（主线 `54f69e83`）。

### 存储

9. **存储缓存类**。缺 `storage/{ECOStorageListingCache,StorageChangeLog,StorageThresholdCache,StorageWatcherScope,StorageReturnBudget,DeferredCellPersistence}` 及对应 mixin。本版走的是另一套优化（`NetworkStoragePerformanceMixin`、`TerminalSnapshotCounterMixin`，`1fd8f0e1` 到 `94ff0ebe`），是否等价未核实，其中一部分可能因持久化格式不同而属于特殊。
10. **修复存储同步和持久化**（主线 `9ea8a6b4`）。可能只部分适用，需要逐段比对 `ECOStorageInfiniteRestore`、`ECOInfiniteStorageData`。

### 联动与配方

11. **Jade**。主线有 `integration/jade/`（9 个文件），本版没有；Jade 有 1.20.1 版。
12. **Data Energistics 工作站模块**。缺 `recipe/DataWorkstationRecipes` 和物品 `INTEGRATED_WORKING_STATION_MODULE_DATA_ENERGISTICS`，而本版 `mods.toml` 已声明该模组。Applied Generators 模块同样缺失，该模组是否有 1.20.1 版未核实。
13. **其他联动缺口**：`mixins/compat/fluxnetworks/ServerFluxNetworkMixin`、`mixins/compat/appbot/BotaniaForgeCapabilitiesMixin`、`integration/ae2lt/WorkstationLightningInputs`、`data/recipe/MekanismRecipes`，以及大型集成工作站的 KubeJS schema。

### 工具、测试、文档

14. **开发工具**。缺 `config/DevToolsConfig`、`dev/` 下的压测与网络分析（`/neodev keybench`、`/neodev start`），以及 16 个 `tooltip.neoecoae.dev.*` 语言键。
15. **单元测试**。`6202dc1e` 删除了 `src/test` 和 junit 依赖，只剩 `src/acceptance` 的 3 个游戏内验收入口。主线有 430 个测试文件。
16. **语言键**。`en_us` 比主线少 44 个键：除开发工具外还有 `gui.neoecoae.workstation.module*`、`gui.neoecoae.large_integrated_working_station.*`、`storage.migration.blocked.*` 等。其他语言未比较。
17. **配置键名**。主线是 `infiniteMigrationKeysPerTick`，本版是 `infiniteMigrationMaxKeysPerTick`。改名时要兼容旧键。
18. **指南**。缺 `guidebook/neoecoae_intro/multiblock_builder.md` 及中文页；本版用结构终端流程（见领先），是否补这一页取决于两种做法的取舍。

## 领先（本版有，主线没有）

| 项 | 位置 | 备注 |
| --- | --- | --- |
| 结构终端物品与建造预览服务 | `items/StructureTerminalItem.java`、`multiblock/StructureTerminal*`、`multiblock/preview/` | 主线用 `MultiblockBuilderUI`，需要定哪个为准 |
| 合成状态暂停、强制开始、CPU 控制 | `api/ECOCraftingStatusSuspension.java`、`api/me/menu/ECOCraftingCpuControl.java`、`CraftingStatusSuspensionMixin`、`CraftConfirmForceStartMixin` | 只按文件名确认 |
| 存储盘拆解、扩展水晶装配配方类型 | `recipe/StorageCellDisassemblyRecipe.java`、`recipe/ExtendedCrystalAssemblerRecipe.java` | |
| 删除旧版 LE4/6/9 大宗物品矩阵 | `c0ddb0b1` | 主线仍有 `ECO_BULK_ITEM_CELL_16M/64M/256M` |
| Mekanism 牛奶灌注联动、隐藏彩蛋配方 | `integration/mekanism/MilkInfusion*`、`integration/xei/HiddenEasterEggRecipes.java` | |
| 网络增量传输与提交反馈 | `network/{StateDelta,BoundedData,CraftSubmissionPacketFeedback}.java` | 只按文件名确认 |
| 工作站样板接口的界面美术与按钮 | `client/gui/widget/WorkstationInterface*`、`client/gui/workstation/WorkstationInterfaceArtwork` | 可能只是 LDLib1 的替代实现 |
| 精确数量与 GTL 规划的说明文档 | `docs/EXACT_QUANTITY_ARITHMETIC_ZH_CN.md` 等 | |

## 未核实

- 规划、执行、存储、界面下的同名文件没有逐行比对，可能还有未发现的行为差异。
- 语言只比较了 `en_us`；指南只比较了文件路径；`src/generated` 的 datagen 产物没有比较。
- CHANGELOG 未发布段没有逐条对照。
- `build.gradle` 的依赖没有完整比对。
