# NeoECOAE GUI 素材说明

本目录存放模组自绘的界面贴图（PNG）。下面按子目录列出每张素材的尺寸、用途和代码中的引用位置，供美术参考。

## common/ 通用控件

| 文件 | 尺寸 | 用途 | 引用位置 |
|---|---|---|---|
| `background.png` | 16x16 | 通用界面背景（九宫格，边框 2/2/2/4） | `NETextures.BACKGROUND`，`MultiblockBuilderUI` |
| `button.png` | 20x20 | 普通按钮（九宫格，边框 2/2/2/5），也作为滚动条滑块 | `NETextures.BUTTON`，`MultiblockBuilderUI`，`AE_SCROLLBAR_THUMB` |
| `button_highlighted.png` | 20x20 | 按钮高亮、悬停、按下状态（边框 2/3/2/5） | `NETextures.BUTTON_HIGHLIGHTED`，`MultiblockBuilderUI` |
| `card_background.png` | 16x16 | 卡片背景，也作为滚动条轨道（边框 3） | `NETextures.CARD_BACKGROUND`，`AE_SCROLLBAR_TRACK` |
| `inventory_border.png` | 16x16 | 玩家物品栏外边框（边框 1） | `NETextures.INVENTORY_BORDER`，`MultiblockBuilderUI` |
| `slot.png` | 18x18 | 物品槽底图（边框 1/2/1/1） | `NETextures.ITEM_SLOT`，`PatternItemSlotClient`，`MultiBlockInfoWrapper`，`MultiblockBuilderUI` |

### common/side_bar/ 侧边栏页签

每个页签由 上/中/下 三段拼接而成，`mirrored_` 前缀为另一侧的镜像版本。

| 文件 | 尺寸 |
|---|---|
| `button_slot_up.png` | 23x30 |
| `button_slot_middle.png` | 23x24 |
| `button_slot_down.png` | 23x27 |
| `mirrored_button_slot_up.png` | 23x30 |
| `mirrored_button_slot_middle.png` | 23x24 |
| `mirrored_button_slot_down.png` | 23x27 |

## computation/ 计算系统（合成 CPU）

| 文件 | 尺寸 | 用途 | 引用位置 |
|---|---|---|---|
| `eco_craftingcpu.png` | 384x384 | CPU 状态面板图集，取 (0,0) 整体背景、(0,260)、(69,260) 两个小区域 | `ComputationCpuStatusPanel` |
| `eco_cpu_controller.png` | 288x256 | CPU 控制器面板图集，按调用处的坐标裁切多个区域 | `ComputationCpuPanel`（约第 287 行） |
| `nbtbench.png` | 256x256 | 计算接口（NBT 工作台）背景，区域 176x253，槽位已烘焙 | `ComputationInterfaceUI` |
| `eco_batching.png` | 16x16 | 批处理图标 | `ComputationCpuStatusPanel`（`BATCH_ICON`） |
| `cpu_overlay/l4.png` | 7x7 | L4 等级 CPU 叠加层 | `api/ECOTier.java` |
| `cpu_overlay/l6.png` | 7x7 | L6 等级 CPU 叠加层 | `api/ECOTier.java` |
| `cpu_overlay/l9.png` | 7x7 | L9 等级 CPU 叠加层 | `api/ECOTier.java` |

## ECO planner/ ECO规划UI

| 文件 | 尺寸 | 用途 | 引用位置 |
|---|---|---|---|
| `eco_craftingreport_cycle.png` | 384x384 | 合成确认界面图集。背景取 (0,0) 330x260；循环项取 (0,265) 67x22，悬停取 (0,287) 67x22 | `src/main/resources/assets/ae2/screens/eco_craft_confirm.json` |
| `f0.png` | 10x9 | 合成等级图标，默认值 | `api/IECOTier.java`（`NETextures.Crafting.F0`） |
| `f4.png` | 10x9 | L4 合成等级图标 | `api/ECOTier.java` |
| `f6.png` | 10x9 | L6 合成等级图标 | `api/ECOTier.java` |
| `f9.png` | 10x9 | L9 合成等级图标 | `api/ECOTier.java` |

## storage/ 存储系统

| 文件 | 尺寸 | 用途 | 引用位置 |
|---|---|---|---|
| `eco_mega_storage.png` | 103x130 | 大型存储控制面板背景 | `storage/StorageMegaPanelUI.java` |
| `estorage_infinite_controller.png` | 288x256 | 无限存储控制器界面背景 | `storage/StorageHostUI.java` |
| `estorage_controller_elements.png` | 256x256 | 存储控制器元件图集：进度条（物品/流体/化学品/总量/能量）、类型标签、仪表（上/中/下）、存储元件（L4/L6/L9、空、物品、流体、化学品），引用自1.12.2 | `storage/StorageHostUI.java`（约第 898–916 行，各元件坐标） |

## workstation/ 集成工作站

| 文件 | 尺寸 | 用途 | 引用位置 |
|---|---|---|---|
| `large_integrated_working_station.png` | 256x256 | 大型集成工作站界面背景，区域 176x180 | `blocks/entity/ECOLargeIntegratedWorkingStationBlockEntity.java` |
| `eco_extra_panels.png` | 80x80 | 网络工具附属面板（工具箱），区域 61x66 | `NETextures.AE2_TOOLBOX`，`ECOIntegratedWorkingStationBlockEntity` |

## widget/ 小部件与图标

| 文件 | 尺寸 | 用途 | 引用位置 |
|---|---|---|---|
| `information.png` | 18x18 | 信息提示图标 | `crafting/CraftingHostPanelUI.java` |
| `upload.png` | 16x16 | 上传按钮图标 | `widget/UploadButton.java`，`client/NeoECOAEClient.java` |
| `outputs.png` | 16x16 | 输出相关图标（通过 `addPostIcon` 添加） | `NETextures.OUTPUTS`，`ECOIntegratedWorkingStationBlockEntity` |
| `pattern_overlay.png` | 18x18 | 样板槽覆盖层 | `NETextures.PATTERN_OVERLAY`，`crafting/CraftingInterfaceUI.java` |
| `ignore_creative_on.png` | 16x16 | "忽略创造模式输入"开启状态 | `common/CreativeStorageInputButton.java` |
| `ignore_creative_off.png` | 16x16 | "忽略创造模式输入"关闭状态 | `common/CreativeStorageInputButton.java` |