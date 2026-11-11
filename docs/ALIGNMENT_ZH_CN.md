# 多版本对齐：1.21.1 主线

1.21.1 是所有版本的主线。本文件记录主线基线、各版本的同步水位，以及其他版本里值得回流主线的内容。各版本自己的差异清单在各自仓库的 `docs/ALIGNMENT_ZH_CN.md`。

- 调查日期：2026-11-11
- 主线基线：`2e494131`（main，21.2.15 之后的未发布段）
- 调查方法：文件清单、标识符计数、行数和提交标题对照。同名文件没有逐行比对，没有运行构建或游戏。标“未核实”的条目在动手前要先核对代码。

## 各版本水位

| 版本 | 工作区 | 调查时的提交 | 大致同步到 | 最大的缺口 |
| --- | --- | --- | --- | --- |
| 1.20.1 | `NeoECOAEExtension-1.20.1`（分支 `1.20.1`） | `5add91e6` | 主线 11-10，只差 11-11 当天 | 附属扩展 API、单元测试被删 |
| 1.20.1-GTO | `NeoECOAEExtension-1.20.1-GTO`（分支 `1.20.1-GTO`） | `8bdd779c` | 主线 11-10 的核心修复；工作站停在 21.2.13 之前 | 智能冷却、供能卡片、工作站模型 |
| 26.1 | `NeoECOAEExtension-26.1`（分支 `26.1`） | `4e93967d` | 主线 11-02 左右 | 11 月的几乎全部工作 |
| GTNH 1.7.10 | `GTNH-ECO-1.7.10`（独立仓库，分支 `main`） | `aa7f568` | 规划器有记录的基线是 `29b8eacb`（21.2.12） | 工作站新功能、精简合成模式 |

落后程度从小到大：1.20.1 < 1.20.1-GTO < GTNH < 26.1。没有哪个版本整体领先主线。

## 回流主线的候选

以下是其他版本有、主线没有的内容。是否回流需要先发 issue 讨论，不直接提交。

| 来源 | 内容 | 说明 |
| --- | --- | --- |
| 1.20.1、GTO | 结构终端物品流程（`StructureTerminalItem`、`multiblock/preview/*`） | 主线用的是 `MultiblockBuilderUI`，两种做法并存，需要定哪个为准 |
| 1.20.1、GTO | 合成状态暂停与强制开始（`ECOCraftingStatusSuspension`、`CraftConfirmForceStartMixin`、`ECOCraftingCpuControl`） | 只按文件名确认，行为未读 |
| 1.20.1 | 存储盘拆解、扩展水晶装配两个配方类型 | 已确认主线 `NERecipeTypes` 没有 |
| 1.20.1 | 删除旧版 LE4/6/9 大宗物品矩阵（`c0ddb0b1`） | 主线 `NEItems` 仍有 `ECO_BULK_ITEM_CELL_16M/64M/256M` |
| 1.20.1 | Mekanism 牛奶灌注联动 | 已确认主线没有 |
| GTNH | 样板上传系统：按玩家保存配方类别到接收端的映射、多接收端自动选择、满了顺延 | 主线只有编码终端上的上传按钮，处理样板直接返回。多数接收目标是 GTNH 专有，可回流的只有通用 AE2 接口的路由部分 |
| GTNH | AE2 原生计划转 ECO 确认页报告（`NativeCraftConfirmReports`） | 主线只找到反方向的 `AE2CraftingPlanBridge`，未完全排除在别处实现 |
| 26.1 | `d29c481e` 统一合成溢出批量上限、`cce425bd` 存储主机右键不直接取盘 | 只比过提交标题，是否适用于主线未核实 |

不算领先、不要回流的：GTO 的通用 `U126` 类（主线已转向 U128）；1.20.1、GTO、26.1 里残留的 `OwnershipReplayHarness`、`CycleResourceLedger`、`UnsupportedCycleSolver`、`SinglePatternGrowthCycleSolver`、`CandidateAttemptStatus` 等（主线已在 `9fa1d04b` 删除）。

## 跨版本的共同问题

- **附属 API**：`api/extension/`、`api/capability/ECOFeature`、`api/multiblock/ECOMultiblock*`、`api/power/`、`api/workstation/`、盘配置与优先级接口只有主线有。按主线 API 写的附属在其他版本上无法编译。
- **测试**：1.20.1、GTO、GTNH 都删了单元测试，对齐只能靠对照主线代码。26.1 有 375 个测试文件，主线 430 个。
- **配置键名**：主线是 `infiniteMigrationKeysPerTick`，1.20.1 是 `infiniteMigrationMaxKeysPerTick`。
- **26.1 的合并方式**：主线历史改写过，26.1 合入的 `8d10df7a` 已不在主线上，共同祖先是 `787065d9`（2026-10-31）。不能直接合并主线，按提交或按文件移植。

## 维护方式

- 主线落地一个需要其他版本跟进的改动时，在对应版本文档的“落后”一节加一条，写上主线提交号。
- 某个版本移植完一项后，从它的“落后”一节删掉这条，并更新本文件的水位表。
- “特殊”一节只放已决定不对齐的差异；决定变了就移到“落后”或“领先”。
