# 主机面板外观扩展

适用于包含 Issue #122、#123、#125 改动的 1.21.1 源码构建。以下接口属于附属模组接入接口。

## 档位名称与标记

在附属的 `IECOTier` 实现中覆盖两个方法：

```java
@Override
public Component getPanelTierName() {
    return Component.translatable("prototype.tier.l1");
}

@Override
public ResourceLocation getHostMarkerTexture() {
    return ResourceLocation.fromNamespaceAndPath("prototype", "textures/gui/host_l1.png");
}
```

`getPanelTierName()` 默认返回 `Component.literal("L" + getTier())`。内置 `ECOTier` 覆盖为 L4、L6、L9，并保留原来的面板字形。自定义档位在相同位置绘制 `Component`，支持翻译、颜色以及数值为 0 的档位；档位数值仍用于原有机器逻辑。

`getHostMarkerTexture()` 默认返回 `null`，继续使用原来的主机标记。自定义贴图为 **13×16 像素**：上方 13×8 是上标记，下方 13×8 是下标记。面板将两行分别缩放到自己的标记区域；计算面板为 14×8、合成面板为 10×7、存储面板为 13×8。

内置主机创建 UI 时已经传入实际的 `IECOTier`。附属如果自行调用 UI 工厂，可使用新增的 `Supplier<IECOTier>` 重载，例如 `StorageHostUI.create(config, () -> SimplifyTier.L1)`。原有重载仍保留，并使用原来的整数档位绘图。存储元件名称从其实际存储实现取得，并随当前页快照发送到客户端；自定义 `CellEntry` 可调用 `withPanelTier(tier)`。

## 控制器 atlas 资源名

六张完整 atlas 已加入 LDLib2 的 `ui-eco` provider：

| 资源名 | 默认贴图（`assets/neoecoae/textures/guis/` 下） |
| --- | --- |
| `ui-eco:Computation-BACKGROUND_ATLAS` | `eco_cpu_controller.png` |
| `ui-eco:Computation-WIDGETS_ATLAS` | `cpu_widget.png` |
| `ui-eco:Storage-CONTROLLER_ATLAS` | `storage/storage_controller.png` |
| `ui-eco:Storage-ATLAS` | `storage/storage_widget.png` |
| `ui-eco:Crafting-CONTROLLER_ATLAS` | `crafting_controller.png` |
| `ui-eco:Crafting-WIDGETS_ATLAS` | `crafting_widgets.png` |

在客户端、ECO 完成纹理资源初始化后，取得已有 provider 并替换条目：

```java
var resources = TexturesResource.INSTANCE.getResourceInstance();
var provider = resources.getBuiltinProviders().values().stream()
    .flatMap(java.util.Collection::stream)
    .filter(candidate -> candidate.getName().equals("ui-eco"))
    .findFirst().orElseThrow();
provider.addResource(new BuiltinPath("ui-eco:Storage-ATLAS"),
    SpriteTexture.of(ResourceLocation.fromNamespaceAndPath(
        "prototype", "textures/gui/storage_widget.png")));
```

使用 LDLib2 的 `TexturesResource`、`BuiltinPath`、`SpriteTexture` 和 Minecraft 的 `ResourceLocation`。替换值应为指向完整图片的 `SpriteTexture`；保持原 atlas 的尺寸与图块布局。绘制时读取替换图片，沿用各图块的裁剪坐标、边框和颜色，已有缓存图块也会使用替换结果。其他贴图仍按原路径加载。

这种 provider 替换作用于使用该资源名的所有 ECO 面板。只修改附属档位的整套外观时，覆盖 `IECOTier.getPanelAtlas(String atlas)`：

```java
@Override
public ResourceLocation getPanelAtlas(String atlas) {
    return switch (atlas) {
        case "Computation-BACKGROUND_ATLAS" -> id("textures/gui/eco_cpu_controller.png");
        case "Computation-WIDGETS_ATLAS" -> id("textures/gui/cpu_widget.png");
        case "Storage-CONTROLLER_ATLAS" -> id("textures/gui/storage_controller.png");
        case "Storage-ATLAS" -> id("textures/gui/storage_widget.png");
        case "Crafting-CONTROLLER_ATLAS" -> id("textures/gui/crafting_controller.png");
        case "Crafting-WIDGETS_ATLAS" -> id("textures/gui/crafting_widgets.png");
        default -> null;
    };
}

private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("prototype", path);
}
```

`atlas` 参数是上表资源名去掉 `ui-eco:` 前缀后的名字，大小写相同。该方法默认返回 null，内置 `ECOTier.L4/L6/L9` 继续使用现有图集。优先级为：档位图集 → 全局 `ui-eco` provider → 原图。档位图集只属于当前面板，沿用原裁剪矩形、边框、颜色和重复方式。背景、合成仪表刻度、进度条、资源条、类型卡、滚动滑块、槽位与缓存按钮均使用同一解析路径；`SpriteTexture.copy()` 也保留档位 supplier。

## 按成员提供面板图案

成员或主机方块可实现 `IHostPanelTextureProvider`，在 `getPanelTexture(BlockState, Part)` 中返回可空的纹理文件位置。计算成员使用实际成员状态，三类主机均使用主机放置后的实际状态，包含朝向、formed 和附属自行维护的属性；例如可直接根据 `energized_parallel_core` 选择状态灯图片。读取 `hostItem` 仅用于显示物品，不用于新接口的状态查询。

| Part | 图片布局 |
| --- | --- |
| `COMPUTATION_UPPER` | 独立 10×5，(0, 0) 裁剪 |
| `COMPUTATION_CORE` / `COMPUTATION_LOWER` | 独立 10×6，(0, 0) 裁剪 |
| `STORAGE_TIER` / `COMPUTATION_TIER` / `CRAFTING_TIER` | 独立 13×18，(0, 0) 裁剪 |
| `STORAGE_MARKER` / `COMPUTATION_MARKER` / `CRAFTING_MARKER` | 13×16，上下两个 13×8 标记分别从 (0, 0) / (0, 8) 裁剪 |
| `COMPUTATION_STATUS` | 独立 12×12，(0, 0) 裁剪 |
| `COMPUTATION_SWITCH` / `CRAFTING_MODE` | 独立 15×26，(0, 0) 裁剪 |

面板位置、缩放、文字颜色和按钮尺寸均沿用原值。主机徽章优先使用方块 provider，其次使用 `getPanelTierName()`，再回落内置字形；文字分支先用当前控制器图集的空白边列清除烤在底图中的占位字形。标记优先使用方块 provider，其次使用 `getHostMarkerTexture()`，最后回落档位图集。

## 状态灯与五颗按钮

状态图覆写五参数 default 方法；默认实现调用原来的两参数方法，所以只交固定图也可以：

```java
@Override
public ResourceLocation getPanelTexture(BlockState state, Part part, int index,
                                        boolean active, boolean hovered) {
    if (part == Part.COMPUTATION_STATUS) {
        // 这里读取的是放置态。属性由附属自己的方块更新机制维护。
        return id(state.getValue(ENERGIZED_PARALLEL_CORE)
            ? "textures/gui/status_charged.png" : "textures/gui/status_normal.png");
    }
    if (part == Part.COMPUTATION_SWITCH || part == Part.CRAFTING_MODE) {
        return id("textures/gui/switch_" + (active ? "on" : "off")
            + (hovered ? "_hover" : "") + ".png");
    }
    return getPanelTexture(state, part);
}
```

| Part | index | active / hovered |
| --- | --- | --- |
| `COMPUTATION_STATUS` | 0 连接，1 替代，2 循环规划，3 快速规划，4 普通网络开关，5 高能网络开关 | active 是原有粉色行的判定，hovered 恒为 false；高能列 5 使用原有蓝色行，粉色时使用列 4 |
| `COMPUTATION_SWITCH` | 0 替代，1 循环规划，2 快速规划 | active 为开关启用状态，hovered 为悬停状态，按下沿用悬停图 |
| `CRAFTING_MODE` | 0 主动冷却，1 超频 | active 为模式启用状态，hovered 为悬停状态，按下沿用悬停图 |

状态灯保留现有联网、开关及倍频显隐条件，接口只提供图片，不改变业务语义。每张图片都可单独返回 null：该状态继续使用原来的图集裁剪。服务器捕获当前放置态对应的全部状态图，并将资源位置随面板同步给客户端；放置态变化会刷新已打开的面板及按钮。无需监听 `ScreenEvent`、查找 JSON id 或修改上游资产。

内置主机已经传入 `this::getBlockState`。自行组装 UI 的附属使用带 `Supplier<BlockState>` 的重载，例如：

```java
StorageHostUI.create(config, this::getTier, this::getBlockState);
CraftingHostPanelUI.create(config, this::getTier, this::getBlockState);
ComputationControllerUI.create(config, panelState, guide, buildButton, buildWindow,
    this::getTier, this::getBlockState);
```

合成面板带工具栏的 `create(...)` 重载同样在 `panelTier` 后接收 `hostState`。旧 UI 工厂签名保留；要使用实际放置态图案，应传入新的状态 supplier。旧的 `HostPanelTextureState.capture(ItemStack)` 保留为 deprecated 兼容入口，旧存储工厂的物品预览行为不变；当前三类主机均传入实际状态，使用 `capture(BlockState, Panel)`。

纹理位置和计算成员存在状态随服务器面板快照发送，不需要注册新的档位 codec。返回 null 时保留已有名称、主机标记接口及内置图集回落；存在且档位索引为 0 的成员回落第一列图案，缺席成员仍不绘制。原 `ComputationControllerPanelState.of(...)` 和六参数构造保留，新增 `ofMembers(...)` 可明确表达“存在、档位为 0”的成员。
