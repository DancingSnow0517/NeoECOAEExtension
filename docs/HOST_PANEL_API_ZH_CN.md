# Forge 1.20.1 主机面板外观扩展

本接口覆盖计算、合成、存储三类主机。行为与本轮锁定的 1.21.1 增量一致，示例使用本工程 Java 17、Forge 和 UI 适配层的实际 API。

## 档位名称、标记与独立图集

附属的 `IECOTier` 可以覆盖以下方法。档位名称支持翻译、颜色和数值为 0 的档位；机器能力仍由原有数值方法决定。

```java
@Override
public Component getPanelTierName() {
    return Component.translatable("prototype.tier.l1");
}

@Override
public ResourceLocation getHostMarkerTexture() {
    return new ResourceLocation("prototype", "textures/gui/host_l1.png");
}

@Override
public ResourceLocation getPanelAtlas(String atlas) {
    return switch (atlas) {
        case "Storage-CONTROLLER_ATLAS" ->
            new ResourceLocation("prototype", "textures/gui/storage_controller.png");
        case "Storage-ATLAS" ->
            new ResourceLocation("prototype", "textures/gui/storage_widget.png");
        default -> null;
    };
}
```

`getPanelTierName()` 默认返回 `L` 加数值档位，内置档位继续显示 L4、L6、L9。标记图默认返回 `null`；自定义标记图为 13×16，上下两个 13×8 标记分别从 `(0,0)`、`(0,8)` 裁剪。

图集名区分大小写，方法参数不含 `ui-eco:` 前缀：

| 图集名 | 默认路径，位于 `assets/neoecoae/textures/guis/` |
| --- | --- |
| `Computation-BACKGROUND_ATLAS` | `eco_cpu_controller.png` |
| `Computation-WIDGETS_ATLAS` | `cpu_widget.png` |
| `Storage-CONTROLLER_ATLAS` | `storage/storage_controller.png` |
| `Storage-ATLAS` | `storage/storage_widget.png` |
| `Crafting-CONTROLLER_ATLAS` | `crafting_controller.png` |
| `Crafting-WIDGETS_ATLAS` | `crafting_widgets.png` |

替换图片须保留完整图集的尺寸和布局。优先级为当前档位图集、全局 provider、原图。图块保留原裁剪矩形、边框、颜色和重复方式；缓存图块及其 `copy()` 继续读取当前档位 supplier。

## 按实际放置状态提供图案

主机或计算成员方块实现 `IHostPanelTextureProvider`，返回纹理文件位置。服务器捕获实际放置后的 `BlockState`，包括朝向、成型状态和附属属性，再将显示快照同步给客户端。

```java
@Override
public ResourceLocation getPanelTexture(BlockState state, Part part) {
    if (part == Part.STORAGE_TIER) {
        return new ResourceLocation("prototype", "textures/gui/storage_tier.png");
    }
    return null;
}

@Override
public ResourceLocation getPanelTexture(BlockState state, Part part,
                                       int index, boolean active, boolean hovered) {
    if (part == Part.CRAFTING_MODE) {
        return new ResourceLocation("prototype", "textures/gui/mode_" + index
            + "_" + active + "_" + hovered + ".png");
    }
    return getPanelTexture(state, part);
}
```

五参数方法默认回落两参数方法。返回 `null` 保留原外观；徽章优先使用方块 provider，再使用档位名称，最后使用内置字形。标记优先使用方块 provider，再使用档位标记，最后使用档位图集。

| Part | 图片布局与索引 |
| --- | --- |
| `COMPUTATION_UPPER` | 10×5 |
| `COMPUTATION_CORE`、`COMPUTATION_LOWER` | 10×6 |
| `STORAGE_TIER`、`COMPUTATION_TIER`、`CRAFTING_TIER` | 13×18 |
| `STORAGE_MARKER`、`COMPUTATION_MARKER`、`CRAFTING_MARKER` | 13×16，上下两个 13×8 标记 |
| `COMPUTATION_STATUS` | 12×12；索引为原图集列 0～5，`active` 为原粉色状态行，`hovered` 为 false |
| `COMPUTATION_SWITCH` | 15×26；索引 0/1/2 为替代材料、循环规划、快速规划 |
| `CRAFTING_MODE` | 15×26；索引 0/1 为主动冷却、超频 |

状态与按钮各变体从独立图片的 `(0,0)` 裁剪；按钮的 `active` 表示开启，`hovered` 也用于按下外观。尺寸、位置和动作继续由主机决定。

自行创建 UI 时传入真实状态 supplier，例如：

```java
StorageHostUI.create(config, this::getTier, this::getBlockState);
CraftingHostPanelUI.create(config, this::getTier, this::getBlockState);
ComputationControllerUI.create(config, panelState, guideButton,
    this::getTier, this::getBlockState);
```

旧工厂重载仍保留。旧 `HostPanelTextureState.capture(ItemStack)` 保留为 deprecated 兼容入口；新代码使用 `capture(BlockState, Panel)`。计算成员的缺席状态与“存在、档位为 0”分开表达，后者使用第一列默认图案。存储元件可以调用 `CellEntry.withPanelTier(tier)` 同步实际档位名称。

## 全局替换图集

内置主机初始化后，在客户端取得已有 provider 并替换条目。以下类型来自 `cn.dancingsnow.neoecoae.platform.ui.runtime` 下的适配层：

```java
import cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource.TexturesResource;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;

var provider = TexturesResource.INSTANCE.getBuiltinProvider("ui-eco");
if (provider != null) {
    provider.addResource("Storage-ATLAS", SpriteTexture.of(
        new ResourceLocation("prototype", "textures/gui/storage_widget.png")));
}
```

该替换影响使用同一图集名的所有面板，已有缓存图块也会重新解析图片。资源包也可以覆盖原路径。仅修改附属自己的档位时使用 `IECOTier.getPanelAtlas()`，避免改变其他主机的外观。

本工程没有 LDLib2 的 `TexturesResource.INSTANCE.getResourceInstance()` 或 `ResourceLocation.fromNamespaceAndPath()` 入口；附属应按以上 Forge 1.20.1 示例编译。
