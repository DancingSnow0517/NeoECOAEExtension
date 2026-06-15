# NeoECOAE 可读性与维护性优化报告

日期：2026-06-01

本轮范围：

- 已执行：EMI 多方块原生预览 UI 的结构性拆分、用户可见文本语言键迁移、3D renderer 边界拆分、BlockEntity dirty/update 语义文档。
- 未执行：注册拆分、网络拆分、大型 BlockEntity 重构、AE2 crafting CPU 核心逻辑改动。
- 约束：不重新引入 LDLib，不修改注册 ID，不修改存档兼容字段，不改 gameplay 语义。

## 本轮改动摘要

| 文件 | 改动目的 |
| --- | --- |
| `src/main/java/cn/dancingsnow/neoecoae/compat/emi/MultiblockEmiRecipe.java` | 收敛为 EMI adapter 和 widget 装配层，保留 recipe id、输入/输出、渲染入口和点击转发。 |
| `src/main/java/cn/dancingsnow/neoecoae/compat/emi/MultiblockPreviewState.java` | 抽出 `expand/layer/formed/materialPage/scene/materialStacks` 状态与 rebuild 逻辑。 |
| `src/main/java/cn/dancingsnow/neoecoae/compat/emi/MultiblockPreviewLayout.java` | 抽出 display height、按钮、scene、材料栏和分页按钮的矩形计算。 |
| `src/main/java/cn/dancingsnow/neoecoae/compat/emi/MaterialRequirementStrip.java` | 抽出材料需求栏渲染、分页点击和材料 tooltip 命中。 |
| `src/main/java/cn/dancingsnow/neoecoae/compat/emi/MultiblockPreviewStyle.java` | 集中 EMI preview 的颜色、按钮、面板和缩放文本绘制。 |
| `src/main/java/cn/dancingsnow/neoecoae/client/multiblock/preview/NEMultiblockSceneRenderer.java` | 只保留 Minecraft 渲染调用、相机状态和 block render loop。 |
| `src/main/java/cn/dancingsnow/neoecoae/client/multiblock/preview/CameraFit.java` | 抽出 8 角点投影 fitting 计算。 |
| `src/main/java/cn/dancingsnow/neoecoae/client/multiblock/preview/SceneBounds.java` | 抽出完整 scene bounds 和 center/size 计算。 |
| `src/main/java/cn/dancingsnow/neoecoae/client/multiblock/preview/SceneViewport.java` | 显式表达 renderer viewport。 |
| `src/main/java/cn/dancingsnow/neoecoae/client/multiblock/preview/PreviewScissor.java` | 抽出 GuiGraphics pose 偏移下的 scissor 计算。 |
| `src/main/java/cn/dancingsnow/neoecoae/data/lang/GuiLangs.java` | 补充 EMI preview 英文 datagen 来源键。 |
| `src/main/resources/assets/neoecoae/lang/zh_cn.json` | 补充 EMI preview 中文翻译。 |
| `src/generated/resources/assets/neoecoae/lang/en_us.json` | 同步当前生成资源的英文键，避免运行时显示 key。 |
| `src/generated/resources/assets/neoecoae/lang/en_ud.json` | 同步 upside-down 英文资源键。 |
| `docs/maintenance/blockentity-update-semantics.md` | 记录 `setChanged`、UI revision、stats dirty、visual dirty、provider dirty 的边界。 |

## 职责拆分结果

`MultiblockEmiRecipe` 不再直接维护材料列表、scene rebuild、分页计算和大部分绘制细节。后续调整 UI 顺序、按钮宽度、scene 高度或材料栏分页时，优先改 `MultiblockPreviewLayout` 和 `MaterialRequirementStrip`，避免再次把 hit test 与 render 坐标写散。

`MultiblockPreviewState` 是唯一负责 scene/material 数据刷新的类。`expand`、`layer`、`formed` 改变后会统一 rebuild scene，并同步刷新 EMI inputs，避免 UI 显示和 EMI recipe input 不一致。

`NEMultiblockSceneRenderer` 的相机 fitting 现在依赖 `SceneBounds.full(scene)` 和 `CameraFit.calculateScale(...)`。切层时仍只渲染当前 visible positions，但相机 fitting 继续使用完整 scene bounds，保持视角稳定。

## 语言键迁移

新增键：

- `emi.neoecoae.multiblock.requirements`
- `emi.neoecoae.multiblock.change_length`
- `emi.neoecoae.multiblock.show_all_layers`
- `emi.neoecoae.multiblock.show_layer`
- `emi.neoecoae.multiblock.show_formed`
- `emi.neoecoae.multiblock.show_unformed`
- `emi.neoecoae.multiblock.previous_page`
- `emi.neoecoae.multiblock.next_page`
- `emi.neoecoae.multiblock.drag_rotate`
- `emi.neoecoae.multiblock.empty_scene`

EMI preview 中原先损坏编码的 `Component.literal(...)` 已替换为 `Component.translatable(...)`。仍保留的 literal 仅用于省略号截断文本和按钮符号文本，例如 `E:3`、`L:*`、`F:N`、`<`、`>`。

## Dirty/Update 语义

本轮没有直接改 BlockEntity 行为，只新增维护文档。建议后续代码中的 helper 命名尽量区分：

- `markPersistenceDirty`：持久化 NBT 需要保存。
- `markUiStateDirty`：菜单 UI state 需要重发。
- `markStatsDirty`：派生统计缓存需要重算。
- `markVisualDirty`：外观、灯光、模型或 BER 数据变化，需要 `markForUpdate()`。
- `markProviderDirty`：AE provider handler/mount 身份变化，需要 refresh/remount。

这部分暂不做大规模机械替换，避免把已经通过 Spark 验证的存储写入路径重新扰动。

## 高风险区域未改动

- `NENetwork` 未拆分。 packet ID/注册顺序属于兼容敏感路径，本轮只记录后续建议。
- `NEBlocks`/`NEItems` 未拆分。注册顺序和静态初始化风险较高。
- F/C/Storage 系列 BlockEntity 未继续拆类。当前 dirty cache 和 storage flush 优化属于性能敏感路径，应结合 Spark 再做小步重构。
- `ECOCraftingCPULogic.executeCrafting` 未修改。
- LDLib 未重新引入。

## 验证

已运行：

- `./gradlew compileJava`：通过。
- `./gradlew build`：通过。
- `./gradlew runClient --no-daemon`：补齐 MixinExtras dev runtime 后通过。日志中 Forge 识别到 `mixinextras-forge-0.4.1_mapped_official_1.20.1`，之前缺少 `com.llamalad7.mixinextras.injector.wrapoperation.Operation` 的 mixin transform 崩溃已消失。

待运行：

- 交互式手动打开 EMI 多方块分类，重点检查 GUI scale 2/3/4 下：
  - E/L/F 按钮仍可点击。
  - 材料数量随长度变化刷新。
  - 材料分页按钮和 tooltip 正常。
  - 切层不导致相机大幅跳动。
  - 无 LDLib 环境不触发类加载失败。

## 后续建议

P0：

- 在 `MultiblockPreviewLayout` 上补一次 GUI scale 2/3/4 的截图回归检查，固定 scene/material 区域不越界。
- 如果项目后续允许轻量测试，给 `CameraFit.calculateScale` 加纯 Java 单元测试，覆盖长条结构、立方体结构和单层结构。

P1：

- 将 `NENetwork` 按 UI、structure、storage、crafting、IWS 分包，但保持中心注册顺序不变。
- 为 Storage/F/C 系列 BlockEntity 引入小型命名 helper，逐步替换直接 `markForUpdate()` 调用。

P2：

- 拆分 `NEBlocks`/`NEItems` 注册文件。只有在注册和 datagen 都稳定后再做，且必须逐项确认 registry name 不变。
> Current branch note: source search did not find `src/main/java/.../network/NENetwork.java`.
> Treat current sync boundaries as `NELDLibStateCodecs`, `NELDLibSyncedStateWidget`,
> AE2 `CraftingStatusPacket` related mixins, BE `writeUiSyncTag/readUiSyncTag`,
> AE2 native packets, LDLib state codecs, and recipe serializers.
> `NELDLibStateCodecs` is one current LDLib state codec boundary, not a replacement
> for reviewing feature widgets, BE update tags, AE2 status sync, or serializers.
> Older `NENetwork` mentions in this document are stale references for the current branch.
