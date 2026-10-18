# 主机面板外观扩展

适用于包含 Issue #122 改动的 1.21.1 源码构建。以下接口属于附属模组接入接口。

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

这种替换作用于使用该资源名的所有 ECO 面板。只想修改附属档位的名称和主机标记时，使用前面的 `IECOTier` 方法。

## 按成员提供面板图案

成员方块可实现 `IHostPanelTextureProvider`，在 `getPanelTexture(BlockState, Part)` 中返回可空的纹理文件位置。计算核心使用实际成员的方块状态；存储主机使用 UI 配置的 `hostItem` 方块默认状态，因此存储图案不应依赖朝向或 formed 状态。

`COMPUTATION_UPPER` 为独立 10×5 图，`COMPUTATION_CORE` / `COMPUTATION_LOWER` 为独立 10×6 图，`STORAGE_TIER` 为独立 13×18 图，均从 (0, 0) 裁剪。`STORAGE_MARKER` 为 13×16 图，上下两个 13×8 标记分别从 (0, 0) / (0, 8) 裁剪。面板位置、缩放与原图保持一致。

纹理位置和计算成员存在状态随服务器面板快照发送，不需要注册新的档位 codec。返回 null 时保留已有名称、主机标记接口及内置图集回落；存在且档位索引为 0 的成员回落第一列图案，缺席成员仍不绘制。原 `ComputationControllerPanelState.of(...)` 和六参数构造保留，新增 `ofMembers(...)` 可明确表达“存在、档位为 0”的成员。
