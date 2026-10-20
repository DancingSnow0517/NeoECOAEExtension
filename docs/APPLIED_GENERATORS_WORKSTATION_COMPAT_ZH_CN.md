# Applied Generators 大型集成工作站兼容

在大型集成工作站的升级模块槽安装「应用发电集成工作站升级卡」，解锁 Applied Generators 的起源装配器配方。配方来源类型为 `appgen:synthesizing`。单方块集成工作站仍使用其自身配方类型。

## 版本与配方

源码基于 `E:\Minecraft Project\AppliedGenerators` 的 `1.21.1` 分支，已快进更新至 `dc5b75e3bc3d18ec4ce8912d42c29865a0986ac1`（2026-10-18）。当前验证目标为官方 `0.3.0+1.21.1` JAR。

适配器同时读取两代 Codec 字段：

| 版本 | 物品输入 | 流体输入 | AE 费用 |
| --- | --- | --- | --- |
| 0.2.x | `input_items` | `input_fluid` | `input_energy` |
| 0.3.0 | `item_inputs` | `fluid_input`，可省略 | `energy_input`，long |

两代配方均使用 `output` 表达物品或流体产物。适配保留成分谓词、全部物品数量、全部流体数量和 AE 费用；充能余烬石英水晶作为配方中的消耗物品正常计入。省略的 Glodium 成分数量采用上游默认值 1。

例如截图中的堆叠熔炼卡配方，保留 200 个充能余烬石英水晶、1 张高级卡、1 个下界合金块、5,000 mB 熔岩和 500,000 AE。翻倍样板需要全部材料同比翻倍，加工费用亦按基础配方次数结算。产出熔岩的无流体投入配方也受支持。负数、非整数、溢出数量和超出工作站容量的配方会被拒绝，避免截断费用。

安装应用发电卡后仍可使用工作站原生配方。更换卡片刷新新请求的可用配方，已接收的批次沿用现有任务保留规则。`largeWorkstationIgnoreUpgradeCards=true` 时继续遵循现有全解锁设置。

此前 `appgen:synthesizing` 归入「其他兼容」并始终可用；现在归入专属卡片，使用该配方的旧机器需要安装应用发电卡，或使用已有的忽略升级卡设置。

## 升级卡合成

合成地点：大型集成工作站。输出 1 张卡，消耗 **16,384,000 AE** 和 **16,000 mB 熔岩**；配方仅在加载 `appgen` 时启用。

| 材料 | 数量 |
| --- | ---: |
| 空白集成工作站升级卡 | 1 |
| 超导处理器 `neoecoae:superconducting_processor` | 16 |
| 充能超导锭 `neoecoae:energized_superconductive_ingot` | 64 |
| 晶体矩阵 `neoecoae:crystal_matrix` | 16 |
| 奇点 `ae2:singularity` | 16 |
| 起源装配器 `appgen:genesis_synthesizer` | 8 |
| 无暇的余烬石英母岩 `appgen:budding_ember_flawless` | 4 |
| 充能余烬石英水晶 `appgen:ember_crystal_charged` | 256 |
| 起源电路板 `appgen:origination_processor` | 128 |

## 外观与资源

主题色为余烬橙 `#F5A23C`。卡片直接使用用户提供的 16×16 原始 PNG，保留原始字节；SHA-256 为 `4b80680112dcd738453a46bc0bc2e44a96421a02877bd86a4cfe0e340741cf24`。界面图标从原图提取中心标志。控制器沿用既有多方块模型，提供停止灯光、运行灯光及运行屏幕动画，覆盖全部四个水平朝向。共享接口标记保留原有颜色。

界面图集在第 8 列追加图标与电路，分别使用 `(105,17,15,15)` 和 `(196,34,28,28)` 区域；现有模块枚举成员保持次序，原有美工图集列保持不变。卡片、图集、模型及控制器贴图都可以由资源包替换。

| 资源 | 路径（相对 `assets/neoecoae`） |
| --- | --- |
| 升级卡 | `textures/item/integrated_working_station_modules/appgen.png` |
| 停止灯光 | `textures/block/working_station/controller_formed/controller_formed_light_compat/appgen.png` |
| 运行灯光 | `textures/block/working_station/controller_formed/controller_formed_light_compat/appgen_on.png` |
| 运行屏幕 | `textures/block/working_station/controller_formed/controller_formed_screen_compat/appgen_on.png` |
| 控制器模型 | `models/block/large_integrated_working_station_compat/appgen_off.json`、`appgen_on.json` |

可运行 `python tools/workstation/create_appgen_artwork.py` 重建界面与控制器像素资源及预览图，卡片原图保持不变。预览输出至 `build/verification/appgen/appgen-artwork.png`。

## 验证范围

- 使用发布版起源装配器 Codec 检查实际配方发现、专属卡门控和重新加载。
- 读取发布 JAR 内全部 22 个起源装配器配方，用原版物品替代测试环境中未注册的 Mod 物品，保留原始数量、流体、输出类别和能耗；检查单次及翻倍匹配。
- 检查旧字段格式、无流体配方、流体产物、默认数量、超过 int 范围的 AE 费用及非法数量拒绝。
- 检查升级卡配方的物品、流体、加载条件与网络编解码，以及全部模型朝向、纹理透明遮罩和图集电路遮罩。

完整测试套件共 1,477 项：通过 1,475 项，跳过 2 项，失败 0 项。JAR 输出为 `build/verification/appgen/neoecoae-21.2.7.jar`，验证记录为同目录的 `verification.json`。

上述验证属于独立 JVM、资源与打包检查；像素预览图不是游戏内截图。此文档不将这些检查视为游戏内生产或动画实测验收。
