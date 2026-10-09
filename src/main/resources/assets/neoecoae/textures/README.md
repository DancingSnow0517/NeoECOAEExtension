# 纹理目录约定

默认资源位于 `src/main/resources/assets/neoecoae/textures/`。Classic 资源包位于
`src/main/resources/classic_pack/assets/neoecoae/textures/`，通过资源包优先级覆盖默认资源。

## 默认资源

| 目录 | 用途 |
| --- | --- |
| `block/` | 方块与流体；机器按 `compute`、`crafting`、`storage`、`working_station` 分类 |
| `item/` | 物品；兼容存储元件素材位于 `eco_cell_compat/` |
| `gui/common/` | 当前界面使用的通用背景、按钮、槽位和边框 |
| `gui/common/side_bar/` | 左右侧栏的分段背景 |
| `gui/widget/` | 上传、维护信息、创造存储开关、样板覆盖和输出图标 |
| `gui/computation/` | 计算接口、CPU 控制和状态界面；等级覆盖图位于 `cpu_overlay/` |
| `gui/crafting/` | 合成确认界面与等级 API 使用的覆盖图 |
| `gui/storage/` | 存储主机与 MEGA 存储界面 |
| `gui/workstation/` | 集成工作站界面和工具箱 |
| `gui/recipe/` | JEI、EMI 共用的配方背景与进度条 |
| `gui/vendor/ae2/` | 随模组分发的 AE2 原始素材及许可证 |
| `gui/vendor/ldlib/` | 随模组分发的 LDLib2 原始素材及许可证 |

第三方素材来源见 [gui/vendor/THIRD_PARTY_ASSETS.md](gui/vendor/THIRD_PARTY_ASSETS.md)。
NeoECOAE 自己的 GUI 纹理统一使用 `gui/`，不再新增 `guis/`。
其他模组命名空间内的路径仍遵循其各自约定。

## Classic 资源

按实际用途划分素材：默认界面和等级 API 正在使用的纹理保留在默认资源中，
没有实际界面调用的经典旧素材只保留在 Classic 中。两个纹理目录不保存内容相同的图片副本。

Classic 中有实际差异的覆盖纹理必须与默认资源使用相同的相对路径。例如，经典高亮按钮位于
`gui/common/button_highlighted.png`，其图片内容与默认按钮不同。

Classic 的旧合成面板、状态背景与进度条保留在 `gui/legacy/crafting/`，
旧冷却/超频按钮保留在 `gui/legacy/crafting/widget/`，旧通用控件保留在 `gui/legacy/common/`。
这些素材目前没有代码引用，用于保存历史美术资源，不再注册到默认 GUI 纹理库。
默认界面仍在使用的背景、槽位、样板覆盖和等级图不属于 Classic 专用素材。

## 修改纹理时

- 将 `.png.mcmeta` 与对应的 `.png` 放在一起。
- 移动 GUI 纹理时，同时更新 Java、AE2 界面 JSON 和相关测试中的引用。
- 动态拼接的路径也要同步更新，如侧栏背景、创造存储开关和冷却/超频控件。
- 新增 Classic 差异纹理时使用与默认资源一致的路径；未使用的旧素材放入 Classic 的 `gui/legacy/`，不要在默认资源中保存副本。
- 第三方原始素材、许可证和来源说明一起维护。
