# 存储矩阵联动与开发客户端

## 新素材对应关系

桌面 `textures` 素材作者：TedXenon。

| 素材系列 | 对应模组 | ECO 注册内容 |
|---|---|---|
| 物质 | Applied Replicatics（`apprep`）与 Replication | `eco_matter_cell_housing`；`eco_matter_cell_16m/64m/256m` |
| MEGA 物质 | Applied Replicatics、Replication 与 MEGA Cells | `mega_matter_cell_housing`；`eco_mega_matter_cell_4g` |
| 放射性化学品 | MEGA Cells、Applied Mekanistics 与 Mekanism | `eco_radioactive_chemical_cell_housing`；`eco_radioactive_chemical_cell_16m/64m/256m` |

物质盘使用上游的 `apprep:m` AEKeyType，最多存储 3 种物质。普通盘采用现有 ECO L4/L6/L9 容量，MEGA 盘采用现有 MEGA 4 GiB 系列。放射性化学品盘最多存储 1 种化学品，容量为 16/64/256 MiB，待机耗电为 250/1000/4000 AE/t；接收规则与 MEGA Cells 放射性盘一致，并遵循其乏燃料存储配置。

合成和空盘拆解配方位于 `src/main/resources/data/neoecoae/recipe/compat/apprep` 与 `compat/megacells`，均附带所需模组的加载条件。新模型沿用项目的 `textures/block/storage/drive`、`textures/item/eco_cell_compat` 目录；物品状态灯保持在纹理层 2。

`storage_widget.png` 已更新；物质与放射性化学品的颜色、图例及资源类型映射同步接入，应力色条和图标的横坐标调整至 104。

## runClient 依赖

`build.gradle` 为同一个开发客户端配置以下存储联动：

- Applied Mekanistics / Mekanism：化学品。
- AppFlux：FE。
- Ars Energistique / Ars Nouveau：魔源。
- Applied Botanics / Botania：魔力，`enableAppBotIntegration=true`。
- Applied Pneumatics / PneumaticCraft：空气。
- Applied Experienced / ExperienceLib：经验。
- Applied Soul / Soulplied Energistics：灵魂。
- AE2 Omni Cells：全能、复合全能、量子全能及乏核废料。
- MEGA Cells：MEGA 各类矩阵、压缩盘及放射性化学品。
- Applied Replicatics 21.1-1.2.1 / Replication 1.21.1-1.2.7 / Kotlin for Forge 5.12.0：物质。
- Data Energistics 3.3.5：数据，所需 Configuration 3.1.1 由其自身嵌入。
- AE2 Lightning Tech 2.1.2-beta3 / Thunderbolt Core 2.0.3-beta2：闪电。

Data Energistics 与闪电科技配置在同一个环境中，不设置互斥开关。Botania 固定为 AppBot 上游指定的 `454-20260621.181850-47`，与 AppBot 1.6.0-alpha.3 配套。JEI 使用 `19.56.0.438`，满足 Data Energistics 的最低版本要求。

`installCuriosDevMod` 下载并复制开发依赖到 `run/mods`，`runClient` 会自动依赖该任务。只准备模组而不启动游戏时执行：

```powershell
.\gradlew.bat installCuriosDevMod --console=plain
```

部分已有联动模组通过 `runtimeClasspath` 加载，因此无需再放一份到 `run/mods`。

## 启动兼容处理

AppBot 会在 AE2 的能力注册事件中查询 Botania 能力，而 Botania 的能力注册事件可能尚未执行。`neoecoae.compat.appbot.mixins.json` 对尚未登记的方块能力按其 ID、接口类型和上下文类型取得 NeoForge 的同一个能力实例，不提前写入 Botania 映射，避免后续重复登记。该兼容仅在 AppBot 和 Botania 同时安装时加载。

开发环境同时安装 AE2 JEI Integration 1.2.1，供 Applied Soul 的 JEI 插件使用。

2026-10-19 实际运行 `runClient` 后已确认到达主菜单，并通过游戏观察接口确认单人世界和玩家已加载。主菜单截图位于 `run/screenshots/neoecoae-startup-main-menu.png`；启动诊断脚本位于被 Git 忽略的 `.gradle/startup-probe.init.gradle`，不属于发布源码。
