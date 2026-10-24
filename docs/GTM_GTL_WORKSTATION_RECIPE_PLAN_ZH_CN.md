# GTM / GTL 集成工作站配方与矿物混用定案

日期：2026-10-24。本文给出采用的原料、数量、产出和加工费用；数值是本次设计，不是声称上游已有这些配方。现已实装为互斥的内置服务端配方包；默认保持独立模式。在 `config/neoecoae-recipes-common.toml` 中设置 `profile = "GTM"` 或 `profile = "GTL"`，完整重启后生效。启用说明、生成命令与验证边界见 [实装说明](GTM_GTL_RECIPE_PROFILE_IMPLEMENTATION_ZH_CN.md)。

## 1. 采用这套路线

**保留 ECO 的水晶锭、充能超导锭、超导处理器和各级组件，用 GT 材料与部件建立其制作进度。普通集成工作站从 EV 开始，大型工作站放在 IV。**

| 制作阶段 | GTM 方案 | GTL 方案 |
| --- | --- | --- |
| 首台集成工作站 | EV 组装机制造；前置材料可在 GT 机器和 AE 压印器制造 | 同左 |
| 大型工作站结构与仓口 | IV 部件、16M ECO 组件 | 同左；16M 组件也要求 IV 部件 |
| 16M / 64M / 256M ECO 存储组件 | EV / LuV / UV 部件 | IV / ZPM / UHV 部件 |
| L4 / L6 / L9 合成、存储、计算主机及计算矩阵 | IV / LuV / UV 部件 | IV / ZPM / UHV 部件 |
| 普通 / 高能网络交换机 | LuV / UV 部件 | ZPM / UHV 部件 |
| 无限组件及无限物品元件 | UV 场发生器与中子素板 | UEV 场发生器与 Quantanium 锭 |

这里的阶段通过消耗对应材料和部件来建立。**ECO 工作站配方仍消耗 AE；新增 GT 制造路线由原 GT 机器执行并消耗 EU。** 不要求先给 ECO 工作站实现 GT 电压、洁净室或 EU 适配器。

采用互斥的 `standalone`、`gtm`、`gtl` 配方档案：默认保持 `standalone`。选择 GT 档案后，替换本模组相关旧配方；GTL 档案要求实际加载 `gtlcore` 和 `gtceu`。不要把所有配方同时叠加，让便宜旧路线绕过新成本。

本方案逐条替换列出的 **42 条原生工作站配方：30 条主体配方、12 条可选存储配方**；另加入 16 条 GT 前置/结构件路线、4 条矿物处理路线、4 张已有类型升级卡的配方、6 条成品归一转换和 2 条 AdvancedAE 路线调整。核对实装时确认，工程还单独保留了 `compat/megacells/` 下的 14 条可选工作站配方；这些既有路线保持原样，未计入上述 42 条。

配套的 [GTM_GTL_WORKSTATION_RECIPE_PLAN.json](GTM_GTL_WORKSTATION_RECIPE_PLAN.json) 包含两个档案的全部展开参数。它作为生成器的输入清单，**不是可以直接放入数据包加载的 Minecraft 配方 JSON**；实际配方位于 `src/main/resources/recipe_profiles/`。

## 2. 先解决首台工作站：16 条 GT 制造配方

普通工作站与大型结构共用 `neoecoae:integrated_working_station` 这一控制器物品。先做单机，再用控制器、结构方块和仓口搭大型结构，不另外发明控制器物品。

以下均在原 GT 机器执行，数量为一批。`EU/t × tick` 是建议费用，不与 AE 做固定比率换算。

记号：`N:` = `neoecoae:`，`G:` = `gtceu:`，`A:` = `ae2:`，`M:` = `minecraft:`；`#` 表示物品标签。数量均为整数，流体单位为 mB。`#N:compat/gt/...` 的完整白名单见第 6 节。

| 编号 | GT 机器 | 完整物品输入 | 流体输入 | 产出 | 费用与时间 |
| --- | --- | --- | --- | --- | --- |
| B01 | `gtceu:mixer` | `#forge:dusts/iron` ×1；`#N:compat/gt/aluminium_dusts` ×1；`#forge:dusts/certus_quartz` ×2 | 无 | `N:aluminum_alloy_dust` ×1 | 120 EU/t × 200 tick |
| B02 | `gtceu:mixer` | `#forge:dusts/tungsten` ×1；`#forge:dusts/aluminum_alloy` ×1；`#forge:dusts/fluix` ×2 | 无 | `N:black_tungsten_alloy_dust` ×1 | 480 EU/t × 200 tick |
| B03 | `gtceu:alloy_smelter` | `N:aluminum_alloy_dust` ×1 | 无 | `N:aluminum_alloy_ingot` ×1 | 120 EU/t × 200 tick |
| B04 | `gtceu:electric_blast_furnace`，4500 K | `N:black_tungsten_alloy_dust` ×1 | `G:argon` 100 mB | `N:black_tungsten_alloy_ingot` ×1 | 1,920 EU/t × 400 tick |
| B05 | `gtceu:autoclave` | `A:charged_certus_quartz_crystal` ×4；`#forge:dusts/energized_crystal` ×4 | `G:distilled_water` 250 mB | `N:energized_crystal` ×8 | 480 EU/t × 100 tick |
| B06 | `gtceu:autoclave` | `#forge:dusts/energized_crystal` ×8；`#forge:gems/fluix` ×8 | `G:distilled_water` 250 mB | `N:energized_fluix_crystal` ×8 | 480 EU/t × 100 tick |
| B07 | `gtceu:mixer` | `#forge:dusts/certus_quartz` ×4；`#forge:dusts/fluix` ×4；`#forge:dusts/energized_crystal` ×4；`#N:compat/gt/crystal_bases` ×4；`G:quartzite_dust` ×4 | `G:argon` 2000 mB | `N:crystal_ingot` ×4 | 480 EU/t × 400 tick |
| B08 | `gtceu:mixer` | `#forge:dusts/energized_fluix_crystal` ×4；`#N:compat/gt/aluminium_dusts` ×4；`#N:compat/gt/silicon` ×4；`#N:compat/gt/superconductor_ingots` ×4 | `G:argon` 2000 mB | `N:energized_superconductive_ingot` ×4 | 1,920 EU/t × 400 tick |
| B09 | `gtceu:circuit_assembler` | `N:superconducting_processor_print` ×4；`N:crystal_matrix` ×4；`A:printed_silicon` ×4；`#G:circuits/hv` ×1；`G:fine_gold_wire` ×8 | `G:soldering_alloy` 144 mB | `N:superconducting_processor` ×4 | 1,920 EU/t × 200 tick |
| B10 | `gtceu:assembler` | `G:ev_machine_hull` ×1；`N:aluminum_alloy_casing` ×4；`#G:circuits/ev` ×4；`A:molecular_assembler` ×1；`A:256k_crafting_storage` ×1；`A:cell_workbench` ×1；`N:superconducting_processor` ×2；`A:interface` ×1；`G:ev_electric_motor` ×2 | `G:soldering_alloy` 576 mB | `N:integrated_working_station` ×1 | 1,920 EU/t × 600 tick |
| B11 | `gtceu:assembler` | `N:aluminum_alloy_ingot` ×4；`G:aluminium_plate` ×4；`A:quartz_vibrant_glass` ×4；`N:crystal_ingot` ×1 | `G:soldering_alloy` 144 mB | `N:aluminum_alloy_casing` ×2 | 480 EU/t × 100 tick |
| B12 | `gtceu:assembler` | `N:black_tungsten_alloy_ingot` ×4；`G:tungsten_steel_plate` ×4；`A:quartz_vibrant_glass` ×4；`N:crystal_ingot` ×1 | `G:soldering_alloy` 144 mB | `N:black_tungsten_alloy_casing` ×2 | 1,920 EU/t × 200 tick |
| B13 | `gtceu:assembler` | `N:black_tungsten_alloy_casing` ×4；`A:engineering_processor` ×2；`#forge:storage_blocks/energized_fluix_crystal` ×1；`#G:circuits/ev` ×2；`G:stainless_steel_plate` ×4 | `G:soldering_alloy` 288 mB | `N:crafting_casing` ×1 | 1,920 EU/t × 200 tick |
| B14 | `gtceu:assembler` | `N:aluminum_alloy_casing` ×4；`A:logic_processor` ×2；`#forge:storage_blocks/energized_fluix_crystal` ×1；`#G:circuits/ev` ×2；`G:titanium_plate` ×4 | `G:soldering_alloy` 288 mB | `N:storage_casing` ×1 | 1,920 EU/t × 200 tick |
| B15 | `gtceu:assembler` | `N:aluminum_alloy_casing` ×4；`A:calculation_processor` ×2；`#forge:storage_blocks/energized_fluix_crystal` ×1；`#G:circuits/ev` ×2；`G:titanium_plate` ×4 | `G:soldering_alloy` 288 mB | `N:computation_casing` ×1 | 1,920 EU/t × 200 tick |
| B16 | `gtceu:assembler` | `A:advanced_card` ×1；`G:polyethylene_plate` ×4；`N:superconducting_processor` ×2；`#G:circuits/hv` ×2 | `G:soldering_alloy` 144 mB | `N:integrated_working_station_module_empty` ×4 | 480 EU/t × 100 tick |

补充规则：

- **B09 超导处理器**要求 GT 洁净室；其余表列前置按表中物料、GT 电压和温度执行。
- B01 保持原合金粉的实际成本：铁粉 1、铝粉 1、赛特斯石英粉 2，得到 ECO 铝合金粉 1；B02 同样保持钨粉 1、ECO 铝合金粉 1、福鲁伊克斯粉 2，得到黑钨合金粉 1。
- 水晶锭改用钻石基底，额外加入石英岩粉；充能超导锭改用真正的 GT 超导合金基底。它们都可在 GT 搅拌机中先做出来。
- **保留 AE 压印器的超导压印模板、压印件、处理器配方，以及水晶矩阵的五锭合成。** 它们消耗的水晶锭、充能超导锭已由 GT 路线提供，因此首台工作站不依赖另一台工作站。
- **保留现有雷击转化 AE 母岩、充能晶簇生长与采收路径**，用它取得最初的充能水晶和水晶粉。B05 是有种子之后的批量增殖，不承担无种子启动。
- B16 一次输出空白升级卡 4 张。升级卡沿用工程已有类型，不增加“GT 配方卡”。

## 3. 工作站内部配方：现有 42 条全部明确

下面列出 **GTM 档案的完整输入**。所有这些原生工作站配方保持读取时的 AE 费用与默认 **100 tick** 基础加工时间，原有超频/散热/冷却规则继续生效。

GTL 档案仅按第 4 节替换指定部件，数量不变。原模组加载条件继续保留，例如 Omni 存储要求 `ae2omnicells`，废料存储还要求 `appmek`。

### 3.1 五条基础材料配方

| 产出 | 完整物品输入 | 流体输入 | AE / 批 |
| --- | --- | --- | --- |
| `N:cryotheum_solution` 1000 mB | `N:cryotheum_crystal` ×4；`N:energized_crystal` ×2；`#forge:dusts/redstone` ×2；`G:sodium_dust` ×2 | `G:distilled_water` 1000 mB | 1,000 |
| `N:crystal_ingot` ×4 | `#forge:dusts/certus_quartz` ×4；`#forge:dusts/fluix` ×4；`#forge:dusts/energized_crystal` ×4；`#N:compat/gt/crystal_bases` ×4；`G:quartzite_dust` ×4 | `G:argon` 2000 mB | 200,000 |
| `N:energized_crystal` ×8 | `A:charged_certus_quartz_crystal` ×4；`#forge:dusts/energized_crystal` ×4 | `G:distilled_water` 250 mB | 62,500 |
| `N:energized_fluix_crystal` ×8 | `#forge:dusts/energized_crystal` ×8；`#forge:gems/fluix` ×8 | `G:distilled_water` 250 mB | 62,500 |
| `N:energized_superconductive_ingot` ×4 | `#forge:dusts/energized_fluix_crystal` ×4；`#N:compat/gt/aluminium_dusts` ×4；`#N:compat/gt/silicon` ×4；`#N:compat/gt/superconductor_ingots` ×4 | `G:argon` 2000 mB | 200,000 |

冷却液每批新增钠粉 2，作为这条新配方的真实消耗；不把钠粉、液态钠或其他模组冷却液直接视为 ECO 冷却液。制造配方使用 GT 蒸馏水，不向公共 `minecraft:water` 标签添加其他流体。

### 3.2 组件、主机、大型结构与交换机：25 条

| 产出 | 完整物品输入 | 流体输入 | AE / 批 |
| --- | --- | --- | --- |
| `N:computation_system_l4` ×1 | `N:computation_casing` ×4；`N:computation_parallel_core_l4` ×2；`N:energized_superconductive_ingot` ×16；`N:superconducting_processor` ×16；`#G:circuits/iv` ×4；`G:iv_field_generator` ×2 | `G:soldering_alloy` 288 mB | 16,000 |
| `N:computation_system_l6` ×1 | `N:computation_system_l4` ×1；`N:computation_parallel_core_l6` ×2；`N:energized_superconductive_ingot` ×32；`N:superconducting_processor` ×32；`#G:circuits/luv` ×4；`G:luv_sensor` ×2 | `G:soldering_alloy` 1152 mB | 160,000 |
| `N:computation_system_l9` ×1 | `N:computation_system_l6` ×1；`N:computation_parallel_core_l9` ×2；`N:energized_superconductive_ingot` ×64；`N:superconducting_processor` ×64；`#G:circuits/uv` ×4；`G:uv_emitter` ×2 | `G:soldering_alloy` 4608 mB | 640,000 |
| `N:crafting_system_l4` ×1 | `N:crafting_casing` ×4；`N:crafting_parallel_core_l4` ×2；`N:energized_superconductive_ingot` ×16；`N:superconducting_processor` ×16；`#G:circuits/iv` ×4；`G:iv_field_generator` ×2 | `G:soldering_alloy` 288 mB | 16,000 |
| `N:crafting_system_l6` ×1 | `N:crafting_system_l4` ×1；`N:crafting_parallel_core_l6` ×2；`N:energized_superconductive_ingot` ×32；`N:superconducting_processor` ×32；`#G:circuits/luv` ×4；`G:luv_sensor` ×2 | `G:soldering_alloy` 1152 mB | 160,000 |
| `N:crafting_system_l9` ×1 | `N:crafting_system_l6` ×1；`N:crafting_parallel_core_l9` ×2；`N:energized_superconductive_ingot` ×64；`N:superconducting_processor` ×64；`#G:circuits/uv` ×4；`G:uv_emitter` ×2 | `G:soldering_alloy` 4608 mB | 640,000 |
| `N:eco_cell_component_16m` ×1 | `A:cell_component_256k` ×12；`N:energized_superconductive_ingot` ×32；`N:superconducting_processor` ×4；`N:crystal_ingot` ×1；`#G:circuits/ev` ×4；`G:ev_field_generator` ×2 | `G:soldering_alloy` 144 mB | 16,000 |
| `N:eco_cell_component_256m` ×1 | `N:eco_cell_component_64m` ×3；`N:energized_superconductive_ingot` ×64；`N:superconducting_processor` ×64；`N:crystal_ingot` ×1；`#G:circuits/uv` ×4；`G:uv_field_generator` ×2 | `G:soldering_alloy` 2304 mB | 144,000 |
| `N:eco_cell_component_64m` ×1 | `N:eco_cell_component_16m` ×3；`N:energized_superconductive_ingot` ×48；`N:superconducting_processor` ×16；`N:crystal_ingot` ×1；`#G:circuits/luv` ×4；`G:luv_field_generator` ×2 | `G:soldering_alloy` 576 mB | 48,000 |
| `N:eco_computation_cell_l4` ×1 | `N:eco_cell_component_16m` ×4；`N:energized_superconductive_ingot` ×64；`N:superconducting_processor` ×4；`N:crystal_matrix` ×1；`#G:circuits/iv` ×4；`G:iv_sensor` ×2 | `G:soldering_alloy` 288 mB | 64,000 |
| `N:eco_computation_cell_l6` ×1 | `N:eco_cell_component_64m` ×4；`N:energized_superconductive_ingot` ×64；`N:superconducting_processor` ×16；`N:crystal_matrix` ×1；`#G:circuits/luv` ×4；`G:luv_sensor` ×2 | `G:soldering_alloy` 1152 mB | 256,000 |
| `N:eco_computation_cell_l9` ×1 | `N:eco_cell_component_256m` ×4；`N:energized_superconductive_ingot` ×64；`N:superconducting_processor` ×64；`N:crystal_matrix` ×1；`#G:circuits/uv` ×4；`G:uv_sensor` ×2 | `G:soldering_alloy` 4608 mB | 1,024,000 |
| `N:eco_infinite_cell_component` ×1 | `N:eco_cell_component_256m` ×128；`N:energized_superconductive_ingot` ×64；`N:superconducting_processor` ×64；`A:singularity` ×64；`N:crystal_ingot` ×16；`N:storage_system_l9` ×2；`A:drive` ×16；`G:uv_field_generator` ×4；`G:neutronium_plate` ×16 | `N:cryotheum_solution` 64000 mB | 1,000,000 |
| `N:eco_infinite_item_cell` ×1 | `M:water_bucket` ×1；`M:lava_bucket` ×1；`N:eco_cell_component_256m` ×1；`N:crystal_matrix` ×1；`#G:circuits/uv` ×4；`G:uv_field_generator` ×4；`G:neutronium_plate` ×8 | `G:soldering_alloy` 576 mB | 144,000 |
| `N:computation_high_energy_network_switch` ×1 | `N:computation_network_switch` ×2；`A:singularity` ×8；`N:superconducting_processor` ×32；`N:energized_superconductive_ingot` ×64；`N:black_tungsten_alloy_block` ×16；`N:eco_cell_component_256m` ×2；`G:uv_sensor` ×4；`#G:circuits/uv` ×8 | `G:soldering_alloy` 4608 mB | 8,000,000 |
| `N:computation_network_switch` ×1 | `A:singularity` ×2；`A:calculation_processor` ×16；`N:superconducting_processor` ×8；`N:energized_superconductive_ingot` ×16；`N:computation_casing` ×8；`A:cell_workbench` ×4；`A:cell_component_256k` ×4；`#G:circuits/luv` ×4；`G:luv_sensor` ×2 | `G:soldering_alloy` 1152 mB | 2,000,000 |
| `N:crafting_high_energy_network_switch` ×1 | `N:crafting_network_switch` ×2；`A:singularity` ×8；`N:superconducting_processor` ×32；`N:energized_superconductive_ingot` ×64；`N:black_tungsten_alloy_block` ×16；`N:eco_cell_component_256m` ×2；`N:crystal_matrix` ×4；`G:uv_emitter` ×4；`#G:circuits/uv` ×8 | `G:soldering_alloy` 4608 mB | 8,000,000 |
| `N:crafting_network_switch` ×1 | `A:singularity` ×2；`A:engineering_processor` ×16；`N:superconducting_processor` ×8；`N:energized_superconductive_ingot` ×16；`N:crafting_casing` ×8；`A:molecular_assembler` ×4；`A:256k_crafting_storage` ×2；`#G:circuits/luv` ×4；`G:luv_sensor` ×2 | `G:soldering_alloy` 1152 mB | 2,000,000 |
| `N:large_integrated_working_station_casing` ×4 | `N:black_tungsten_alloy_casing` ×4；`A:sky_stone_block` ×2；`A:engineering_processor` ×2；`#forge:storage_blocks/energized_fluix_crystal` ×1；`N:eco_cell_component_16m` ×1；`N:energized_superconductive_ingot` ×8；`N:superconducting_processor` ×8；`#G:circuits/iv` ×2；`G:titanium_plate` ×4 | `G:soldering_alloy` 576 mB | 256,000 |
| `N:large_integrated_working_station_input_hatch` ×1 | `N:large_integrated_working_station_casing` ×4；`A:import_bus` ×2；`A:interface` ×1；`N:eco_fluid_cell_housing` ×1；`N:energized_superconductive_ingot` ×2；`N:superconducting_processor` ×2；`G:iv_electric_pump` ×2；`G:iv_conveyor_module` ×2 | `G:soldering_alloy` 288 mB | 128,000 |
| `N:large_integrated_working_station_interface` ×1 | `N:large_integrated_working_station_casing` ×4；`A:interface` ×1；`A:singularity` ×2；`N:eco_cell_component_16m` ×1；`N:energized_superconductive_ingot` ×4；`N:superconducting_processor` ×4；`#G:circuits/iv` ×2；`G:iv_sensor` ×2 | `G:soldering_alloy` 576 mB | 256,000 |
| `N:large_integrated_working_station_output_hatch` ×1 | `N:large_integrated_working_station_casing` ×4；`A:export_bus` ×2；`A:interface` ×1；`N:eco_fluid_cell_housing` ×1；`N:energized_superconductive_ingot` ×2；`N:superconducting_processor` ×2；`G:iv_electric_pump` ×2；`G:iv_conveyor_module` ×2 | `G:soldering_alloy` 288 mB | 128,000 |
| `N:storage_system_l4` ×1 | `N:storage_casing` ×4；`A:drive` ×4；`N:energized_superconductive_ingot` ×16；`N:superconducting_processor` ×16；`#G:circuits/iv` ×4；`G:iv_field_generator` ×2 | `G:soldering_alloy` 288 mB | 16,000 |
| `N:storage_system_l6` ×1 | `N:storage_system_l4` ×1；`A:drive` ×8；`N:energized_superconductive_ingot` ×32；`N:superconducting_processor` ×32；`#G:circuits/luv` ×4；`G:luv_sensor` ×2 | `G:soldering_alloy` 1152 mB | 160,000 |
| `N:storage_system_l9` ×1 | `N:storage_system_l6` ×1；`A:drive` ×16；`N:energized_superconductive_ingot` ×64；`N:superconducting_processor` ×64；`#G:circuits/uv` ×4；`G:uv_emitter` ×2 | `G:soldering_alloy` 4608 mB | 640,000 |

明确两处数量设计：

- **大型工作站结构方块一次产出 4 个**，用 16M 组件替换旧配方的 64M 组件；接口同样使用 16M。这样大型结构可在 IV 建造，不需要先跨到 LuV。
- 无限组件中的两项 `256M ×64` 合并表示为 **256M ×128**，总消耗不减少；完整配方为 9 种物品输入。高能合成交换机也控制在 9 种输入内。

### 3.3 Omni / 废料存储的 12 条可选配方

这里保留原插件的组件、处理器、奇点、壳体以及核废料路线的全部材料。每条额外加入对应档位 GT 电路 4 个与焊锡合金；不使用普通 GT 材料替代反物质、核废料奇点等插件核心资源。

| 产出 | 完整物品输入 | 流体输入 | AE / 批 |
| --- | --- | --- | --- |
| `N:eco_complex_omni_cell_16m` ×1 | `ae2omnicells:complex_omni_cell_component_16m` ×10；`ae2omnicells:complex_link_processor` ×2；`A:singularity` ×2；`N:eco_complex_omni_cell_housing` ×1；`#G:circuits/ev` ×4 | `G:soldering_alloy` 144 mB | 1,000 |
| `N:eco_complex_omni_cell_256m` ×1 | `ae2omnicells:complex_omni_cell_component_256m` ×10；`ae2omnicells:complex_link_processor` ×8；`A:singularity` ×32；`N:eco_complex_omni_cell_housing` ×1；`#G:circuits/uv` ×4 | `G:soldering_alloy` 2304 mB | 144,000 |
| `N:eco_complex_omni_cell_64m` ×1 | `ae2omnicells:complex_omni_cell_component_64m` ×10；`ae2omnicells:complex_link_processor` ×4；`A:singularity` ×8；`N:eco_complex_omni_cell_housing` ×1；`#G:circuits/luv` ×4 | `G:soldering_alloy` 576 mB | 12,000 |
| `N:eco_omni_cell_16m` ×1 | `ae2omnicells:omni_cell_component_16m` ×10；`ae2omnicells:omni_link_processor` ×2；`A:singularity` ×1；`N:eco_omni_cell_housing` ×1；`#G:circuits/ev` ×4 | `G:soldering_alloy` 144 mB | 1,000 |
| `N:eco_omni_cell_256m` ×1 | `ae2omnicells:omni_cell_component_256m` ×10；`ae2omnicells:omni_link_processor` ×8；`A:singularity` ×16；`N:eco_omni_cell_housing` ×1；`#G:circuits/uv` ×4 | `G:soldering_alloy` 2304 mB | 144,000 |
| `N:eco_omni_cell_64m` ×1 | `ae2omnicells:omni_cell_component_64m` ×10；`ae2omnicells:omni_link_processor` ×4；`A:singularity` ×4；`N:eco_omni_cell_housing` ×1；`#G:circuits/luv` ×4 | `G:soldering_alloy` 576 mB | 12,000 |
| `N:eco_quantum_omni_cell_16m` ×1 | `ae2omnicells:quantum_omni_cell_component_16m` ×10；`ae2omnicells:multidimensional_expansion_processor` ×2；`A:singularity` ×4；`N:eco_quantum_omni_cell_housing` ×1；`#G:circuits/ev` ×4 | `G:soldering_alloy` 144 mB | 1,000 |
| `N:eco_quantum_omni_cell_256m` ×1 | `ae2omnicells:quantum_omni_cell_component_256m` ×10；`ae2omnicells:multidimensional_expansion_processor` ×8；`A:singularity` ×64；`N:eco_quantum_omni_cell_housing` ×1；`#G:circuits/uv` ×4 | `G:soldering_alloy` 2304 mB | 144,000 |
| `N:eco_quantum_omni_cell_64m` ×1 | `ae2omnicells:quantum_omni_cell_component_64m` ×10；`ae2omnicells:multidimensional_expansion_processor` ×4；`A:singularity` ×16；`N:eco_quantum_omni_cell_housing` ×1；`#G:circuits/luv` ×4 | `G:soldering_alloy` 576 mB | 12,000 |
| `N:eco_spent_nuclear_waste_cell_16m` ×1 | `ae2omnicells:quantum_omni_cell_component_16m` ×10；`ae2omnicells:spent_nuclear_waste_component` ×1；`mekanism:hdpe_sheet` ×40；`mekanism:pellet_antimatter` ×10；`mekanism:radioactive_waste_barrel` ×20；`ae2omnicells:multidimensional_expansion_processor` ×2；`ae2omnicells:spent_nuclear_waste_singularity` ×14；`N:eco_spent_nuclear_waste_cell_housing` ×1；`#G:circuits/ev` ×4 | `G:soldering_alloy` 144 mB | 1,000 |
| `N:eco_spent_nuclear_waste_cell_256m` ×1 | `ae2omnicells:quantum_omni_cell_component_256m` ×10；`ae2omnicells:spent_nuclear_waste_component` ×1；`mekanism:hdpe_sheet` ×40；`mekanism:pellet_antimatter` ×10；`mekanism:radioactive_waste_barrel` ×20；`ae2omnicells:multidimensional_expansion_processor` ×8；`ae2omnicells:spent_nuclear_waste_singularity` ×74；`N:eco_spent_nuclear_waste_cell_housing` ×1；`#G:circuits/uv` ×4 | `G:soldering_alloy` 2304 mB | 144,000 |
| `N:eco_spent_nuclear_waste_cell_64m` ×1 | `ae2omnicells:quantum_omni_cell_component_64m` ×10；`ae2omnicells:spent_nuclear_waste_component` ×1；`mekanism:hdpe_sheet` ×40；`mekanism:pellet_antimatter` ×10；`mekanism:radioactive_waste_barrel` ×20；`ae2omnicells:multidimensional_expansion_processor` ×4；`ae2omnicells:spent_nuclear_waste_singularity` ×26；`N:eco_spent_nuclear_waste_cell_housing` ×1；`#G:circuits/luv` ×4 | `G:soldering_alloy` 576 mB | 12,000 |

## 4. GTL 档案的确切差异

GTL 的基础材料、首台工作站、IV 大型结构和升级卡沿用上述方案。仅对以下成品配方里的新增 GT 材料做替换，**不全局替换所有 EV / LuV / UV 物品**。

| 适用成品 | GTM 使用 | GTL 改为 | 数量 |
| --- | --- | --- | --- |
| 16M ECO 组件、16M Omni/废料存储 | EV 电路；ECO 组件另用 EV 场发生器 | IV 对应部件 | 电路 4；场发生器若存在仍为 2 |
| 64M ECO 组件、64M Omni/废料存储 | LuV 电路；ECO 组件另用 LuV 场发生器 | ZPM 对应部件 | 同原表 |
| 256M ECO 组件、256M Omni/废料存储 | UV 电路；ECO 组件另用 UV 场发生器 | UHV 对应部件 | 同原表 |
| L4 主机、L4 计算矩阵 | IV 部件 | IV 部件 | 不变 |
| L6 主机、L6 计算矩阵、普通交换机 | LuV 电路及传感器 | ZPM 对应部件 | 同原表 |
| L9 主机、L9 计算矩阵、高能交换机 | UV 电路、发射器或传感器 | UHV 对应部件 | 同原表 |
| 无限组件 | UV 场发生器 4；中子素板 16 | `G:uev_field_generator` 4；`G:quantanium_ingot` 16 | 明确替换这两项 |
| 无限物品元件 | UV 电路 4、UV 场发生器 4、中子素板 8 | UHV 电路 4、UEV 场发生器 4、Quantanium 锭 8 | 其余材料不变 |

Quantanium 是本次 GTL 源码实际注册的材料名。它作为 GTL 无限配方中的指定消耗，不加入中子素、钻石或其他材料的通用等价标签。

## 5. 四张已有类型升级卡的具体配方

每条在普通/大型工作站按原生配方执行，消耗 **16,000 AE，100 tick，产出 1 张**。空白卡由 B16 制造。

| 编号 / 输出卡 | 完整物品输入 | 加载条件 |
| --- | --- | --- |
| U01 / `N:integrated_working_station_module_advanced_ae` ×1 | `N:integrated_working_station_module_empty` ×1；`advanced_ae:reaction_chamber` ×1；`G:ev_field_generator` ×2；`N:superconducting_processor` ×8 | `advanced_ae` 可用 |
| U02 / `N:integrated_working_station_module_extendedae` ×1 | `N:integrated_working_station_module_empty` ×1；`expatternprovider:ex_molecular_assembler` ×1；`G:ev_robot_arm` ×2；`N:superconducting_processor` ×8 | `expatternprovider` 可用 |
| U03 / `N:integrated_working_station_module_aelt` ×1 | `N:integrated_working_station_module_empty` ×1；`ae2lt:lightning_assembly_chamber` ×1；`G:ev_emitter` ×2；`N:superconducting_processor` ×8 | `ae2lt` 可用 |
| U04 / `N:integrated_working_station_module_ae2cs` ×1 | `N:integrated_working_station_module_empty` ×1；`N:crystal_ingot` ×16；`G:ev_sensor` ×2；`N:superconducting_processor` ×8 | `ae2cs` 可用 |

ExtendedAE 1.20.1 的配方使用实际存在的 `expatternprovider:ex_molecular_assembler`，不引用这个版本不存在的 `crystal_assembler` 物品。水晶科技卡沿用当前源码已有的 `ae2cs` 卡类型，只在该来源真正可用时提供配方。

## 6. 矿物与材料混用：采用有限白名单

### 6.1 允许混用的具体材料

| 用途 | 接受的物品/标签 | 比例与限制 |
| --- | --- | --- |
| 纯铝锭 | `N:aluminum_ingot`、`G:aluminium_ingot` | 1:1；本模组 GT 档案铝锭输入改用私有桥接标签 |
| 纯铝粉 | `N:aluminum_dust`、`G:aluminium_dust` | 1:1，只接受完整一份粉 |
| 纯铝块 | `N:aluminum_block`、`G:aluminium_block` | 1:1；每块 9 锭，不混入粗矿块 |
| 纯钨锭/粉/块 | 两模组对应完整成品；已有 `#forge:ingots/tungsten`、`#forge:dusts/tungsten`、`#forge:storage_blocks/tungsten` | 同形态 1:1；不包括热钨锭、矿石或矿粉中间态 |
| 超导配方中的硅 | `A:silicon`、`G:silicon_dust`、`G:silicon_ingot` | 这条用途每份计 1；不把二氧化硅/石英粉当成硅 |
| 水晶锭基底 | `#forge:gems/diamond` | 每批 4；GT 档案取消绿宝石替代 |
| 充能超导锭基底 | `G:niobium_titanium_ingot`、`G:vanadium_gallium_ingot`、`G:yttrium_barium_cuprate_ingot` | 每批合计 4 锭，可以混放；只作为这条配方的功能性原料白名单 |
| 赛特斯、福鲁伊克斯粉 | 原有 `#forge:dusts/certus_quartz`、`#forge:dusts/fluix` | 保留原有身份，不把普通石英或石英岩粉直接并入 |

新增私有标签的完整成员已写入配套 JSON 的 `private_material_tags`。这里不让 `aluminum` 与 `aluminium` 公共标签相互引用，避免形成标签循环或改变其他模组配方。

硅和超导基底的规则只作用于指定 ECO 配方，不注册自由互换配方，也不把这三种超导合金声明成同一种材料。

### 6.2 六条成品归一转换

GT 档案提供下列无序合成，**Neo 成品 1 → GT 同材质成品 1**，无额外能耗。输出统一到 GT，减少仓库重复品种；不提供反向转换。纯铝与纯钨的压块/拆块仍严格为 9:1，统一输出 GT 成品。

| 编号 | 输入 | 输出 |
| --- | --- | --- |
| M01 | `N:aluminum_ingot` ×1 | `G:aluminium_ingot` ×1 |
| M02 | `N:aluminum_dust` ×1 | `G:aluminium_dust` ×1 |
| M03 | `N:aluminum_block` ×1 | `G:aluminium_block` ×1 |
| M04 | `N:tungsten_ingot` ×1 | `G:tungsten_ingot` ×1 |
| M05 | `N:tungsten_dust` ×1 | `G:tungsten_dust` ×1 |
| M06 | `N:tungsten_block` ×1 | `G:tungsten_block` ×1 |

本模组 GT 档案里原来要求 `#forge:ingots/aluminum`、`#forge:dusts/aluminum`、`#forge:storage_blocks/aluminum` 的本地配方输入，分别改用对应私有桥接标签，因此 GT 铝也能直接用于 ECO 的工具/配方。

### 6.3 Neo 矿石接入 GT 的四条处理路线

| 编号 | GT 机器 | 完整物品输入 | 流体输入 | 产出 | 费用与时间 |
| --- | --- | --- | --- | --- | --- |
| O01 | `gtceu:macerator` | `N:aluminum_ore` ×1 | 无 | `N:raw_aluminum_ore` ×2 | 120 EU/t × 200 tick |
| O02 | `gtceu:macerator` | `N:raw_aluminum_ore` ×1 | 无 | `G:aluminium_dust` ×1 | 120 EU/t × 200 tick |
| O03 | `gtceu:macerator` | `N:tungsten_ore` ×1 | 无 | `N:raw_tungsten_ore` ×2 | 480 EU/t × 200 tick |
| O04 | `gtceu:macerator` | `N:raw_tungsten_ore` ×1 | 无 | `G:tungsten_dust` ×1 | 1,920 EU/t × 400 tick |

得到 GT 金属粉后继续走该实例原有的金属冶炼流程，不补“矿石直接得到成品锭”的工作站捷径。

例如钨继续保留 GT 的高炉与真空冷却链：本次两个 GTM 源码基线的钨均有 **3600 K、EV 档高炉工艺**；热锭和冷锭不能直接混用。GTL 若改写原工艺，以其实际有效配方为准，不用这里的原料兼容把工艺覆盖回上游版本。

### 6.4 明确不合并的矿物与合金

| 不合并的对象 | 处理方式 |
| --- | --- |
| GT 铝土矿 `bauxite` 与 Neo 铝矿/粗铝 | 保留铝土矿原化工处理链；Neo 矿走 O01/O02，不互贴矿石标签 |
| GT 白钨矿 `scheelite`、钨酸矿 `tungstate` 与 Neo 钨矿/粗钨 | 保留其原有分解与提炼链；Neo 矿走 O03/O04 |
| ECO 铝合金与 GT 铝/其他铝合金 | 仍为 ECO 独立复合材料，通过 B01/B03 制造 |
| ECO 黑钨合金与 GT 钨钢 | 仍为 ECO 独立材料；钨钢作为结构件的额外材料使用，不做 1:1 互换 |
| 热钨锭、完整金属锭 | 热锭先按原工艺冷却，再参与成品混用 |
| 洁净/含杂/粉碎矿、完整金属粉 | 保留矿物处理阶段，不按“一份粉”混入 |
| 小撮粉、小堆粉、完整粉 | 先走 GT 原有数量合并；不能把 1/9 或 1/4 份算成 1 份 |
| 其他模组极寒之凛冰、ECO 极寒之凛冰与冷却液 | 保留各自配方与资源键；同名不直接等价 |

## 7. 必须一起替换或保留的旧路线

GT 档案启用时做以下处理，`standalone` 保留原行为：

1. 现有 42 条工作站配方按第 3、4 节替换，继续保留各自原插件加载条件。
2. 删除首台工作站的旧工作台合成，改用 B10。五种外壳/机壳的旧工作台路线改用 B11–B15。
3. 删除 ECO 铝合金粉、黑钨合金粉的旧无序合成，改用 B01/B02。合金锭的旧熔炉/高炉捷径改用 B03/B04。
4. 删除 Neo 纯铝、纯钨的矿石/粗矿/金属粉直接熔炉、原版高炉配方。不要让 GT 铝粉经成品转换后再用 Neo 旧配方绕过原工艺。这里的删除对象是 Minecraft `smelting` / `blasting`，不是 GT 电力高炉。
5. 删除 `neoecoae:transform/crystal_ingot`、`neoecoae:transform/energized_superconductive_ingot` 的旧爆炸转化；`neoecoae:transform/energized_fluix_crystal` 的水中转化由 B06/工作站路线接替。
6. 保留 AE 压印模板、压印件、处理器与水晶矩阵；保留 EAE 电路切割和本工程已有的虚拟水晶装配配方，它们仍使用已受 GT 制造路线约束的材料。
7. AdvancedAE 两条材料配方继续保留，每批仍为 64 个、500,000 AE；水改为 **GT 蒸馏水 500 mB**。原料分别为“充能赛特斯 32＋充能水晶粉 32”和“充能水晶粉 64＋福鲁伊克斯水晶 64”。不改变其原始整批执行和卡片权限。
8. 纯金属压块/拆块采用第 6 节规则；材料转换不改变物品总量。

精确识别到的 27 个旧配方 ID 列于 JSON 的 `disable_old_recipe_ids_when_profile_active`；42 条原生替换项与 2 条 AAE 调整项分别列出。未来数据生成新增配方时，应同时更新这份覆盖清单。

## 8. 落地检查与来源

实装按配方档案生成/加载互斥资源；清单 JSON 中的 `proposal_profile` 仅供生成器选择，不会写入实际配方，也没有伪装成 Forge 条件。GT API 旧版与新版分别生成，根据实际安装的配方能力注册表选择 String 或 ResourceLocation 格式；GTL 仍要求实际加载 `gtlcore` 和 `gtceu`，不把 GTM 8.0.0 开发版直接当成 GTL 1.4.4 的替代。

本方案沿用 2026-10-24 刷新后相同的参考提交：GTM 1.4.4 `e3844ba9`、最新 1.20.1 开发分支 `f3855da4`、GTL `6ca516d0`。主要依据如下：

- [EcoMachineRecipes.java](../src/main/java/cn/dancingsnow/neoecoae/data/recipe/EcoMachineRecipes.java)、[NEItems.java](../src/main/java/cn/dancingsnow/neoecoae/all/NEItems.java)、[NETags.java](../src/main/java/cn/dancingsnow/neoecoae/all/NETags.java)：本模组现有材料、配方与铝标签拼写。
- [src/generated/resources/data/neoecoae/recipes](../src/generated/resources/data/neoecoae/recipes)：现有 42 条原生配方及原费用、可选条件、旧路线 ID。
- GTM 的 `common/data/materials/ElementMaterials.java`、`FirstDegreeMaterials.java`、`data/recipe/CustomTags.java` 与 `api/data/tag/TagPrefix.java`：材料名、`#gtceu:circuits/...`、金属形态与生成命名。
- GTM 的 `common/data/GTItems.java`：旧版显式注册和新版按档位生成的部件 ID。
- GTL 的 `MaterialBuilder.java`：`quantanium` 的实际材料名。
- [EAERecipes.java](../src/main/java/cn/dancingsnow/neoecoae/data/recipe/EAERecipes.java) 与锁定发布 JAR 的模型资源：ExtendedAE 1.20.1 没有水晶装配器物品；升级卡引用存在的扩展分子装配室。AAE 反应室和 AE2LT 闪电装配室也已核对锁定 JAR 中的对应物品资源。

实装校验关注：42 条指定配方无遗漏、每条原生配方不超过 9 种物品输入、首台工作站无自身依赖、可选依赖条件保留、两档案展开一致、成品转换与压块比例守恒。编译、资源与自动测试记录见实装说明；真实 GT 实例中的加工交互与平衡验收仍需单独进行。
