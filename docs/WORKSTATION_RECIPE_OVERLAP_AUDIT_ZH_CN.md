# 大型集成工作站联动配方重合审计与多升级卡方案

审计日期：2026-10-24。范围：Minecraft 1.21.1 / NeoForge，当前 NeoECOAE 的大型集成工作站。第 1 至 9 节保留实施前的审计与设计基准；实施情况见第 10 节。

## 1. 结论

目前确实存在联动配方重合，值得整理。但“上游机器兼容某类配方”和“NeoECOAE 重复导入了这类配方”需要分别判断。

本次确认的清单包括：

| 类别 | 确认结果 | 当前处理情况 |
| --- | --- | --- |
| 闪电科技内部重合 | 4 组：闪电坍缩矩阵、超载合金板、超载奇点、终极超载核心 | 同材料合同会去重，均由闪电卡解锁 |
| 闪电科技与水晶科技重合 | 4 组：谐振、简易、起源、并行处理器的整块材料路线 | 按卡分别筛选后去重；全局展示只保留一个来源 |
| 应用发电与高级 AE 重合 | 8 组同批次数、同材料的配方，见第 4 节 | 来自配方生成器源码；目前适配后可按材料合同去重 |
| 扩展 AE 与水晶科技批量重合 | JSON 中确认 10 组：一批 4 个与一批 32 个，材料比例相同 | 当前不会按比例去重 |
| 同 ID 的联动资源重合 | `ae2cs:data_reassembler/data_crystal_seed` 在两个 Mod 中各有一份，格式不同 | 属于资源覆盖及版本兼容问题，不能当作两个独立执行配方 |
| 闪电科技借用高级 AE | 确认有运行时借用，但借用视图没有放回 `RecipeManager` | 当前 NeoECOAE 不会因此多导入一份 |
| EAEP 借用扩展 AE | 确认有运行时转换，转换结果属于 EAEP 自己的目录 | 当前 NeoECOAE 不会因此多导入一份 |
| 数据能源的一体机借用 AE2、扩展 AE | 确认有统一配方视图与执行支持 | 当前 NeoECOAE 只适配列明的数据配方类型，没有把整套借用视图再次搬入 |

**建议：同一材料加工关系只展示一个配方组，保留全部真实来源，支持该组的升级卡任选其一即可制作；实际执行保留各来源的材料数量、批量与费用。**

需要准确描述当前实现：如果 A 卡和 B 卡已经各自有一条同材料的独立配方，两张卡通常已经可以分别制作。原因是 `selectForModule()` 先筛选当前卡可用的来源，再去重。主要缺口是：全局展示不能说明多卡支持、运行时借用关系未表达，以及成比例批量配方没有归组。不能把现状概括为“重复配方只能由获胜来源的卡制作”。[N1][N2][N3]

## 2. 审计基准与方法

### 2.1 已核对的源码版本

按要求先更新或抓取远端，再检查对应 1.21.1 分支。对于已有其他改动的仓库，使用抓取后的远端引用读取源码，保留原工作区。

| 仓库 | 本次审计引用 | 提交 |
| --- | --- | --- |
| NeoECOAEExtension-1.21.1 | 当前工作区基准 | `c47cbb0c8e8841bd5747f55ddeb48f32be3b3537` |
| AE2-Lightning-Tech-Reborn | `origin/main` | `3da388d32c6ce6f4be44b4bfc634e23e0e61e57e` |
| AE2-Lightning-Tech-Reborn | 额外检查 `origin/alpha` 的借用目录变化 | `d4d982ba0b3e58213c23d4c0a25625db8c5d2111` |
| AE2-Crystal-Science | `origin/1.21.1` | `5a92b464f40f3e9d61262fe3f9097c318e2b10f4` |
| DataEnergistics | `origin/1.21`；与官方远端同提交 | `a13ab7b8c68621af88fe69a4838495799dfecdcb` |
| ExtendedAE | `origin/1.21.1-neoforge` | `1f86f927552f`，唯一短提交号 |
| ExtendedAE_Plus | `origin/1.21.1` | `8ba8bd387922`，唯一短提交号 |
| AdvancedAE-1.21.1 | `origin/forge/1.21.1` | `7a2ad36f3741`，唯一短提交号 |
| AppliedGenerators | `origin/1.21.1` | `dc5b75e3bc3d`，唯一短提交号 |

“已更新远端”不代表把 alpha、其他游戏版本或所有本地分支合并到了当前项目。

### 2.2 判定标准

本次对上述仓库中已提交的 `src/main/resources/data`、`src/generated/resources/data` 配方 JSON 进行静态抽取，比较材料输入、流体输入、全部输出及数量，并补读运行时目录和配方生成器。Applied Generators 没有提交可用的生成配方 JSON，相关结论直接来自 `AGRecipeProvider`，不按“JSON 为零”推断其没有配方。[G1]

重合分为四类：

1. **同批次材料完全相同**：忽略输入顺序、合并同类材料数量后，输入及输出全部一致。可以归为同一配方组，费用单独保留。
2. **材料比例相同**：所有输入和全部输出可以用同一个比例转换，但源配方的合法批量不同。可以归组展示，不能直接抹掉批量约束。
3. **具体材料有交集**：例如固定红石块与 `c:storage_blocks/redstone`。存在同一样板能同时匹配的情况，但两个接受集合未必相等，不能直接认定完全重复。
4. **仅产物相同**：材料、流体、收率或副产物不同，保留独立路线。

静态清单按所列条件装齐相关 Mod 时比较，不是当前存档实际加载数量。标签最终内容、KubeJS 修改、数据包优先级、加载失败、动态派生配方，均须在最终 `RecipeManager` 和标签加载完成后核验。

水晶科技 JSON 中的 `*_recipe_serializer` 是序列化器 ID；NeoECOAE 使用的 `*_recipe` 是运行时 `RecipeType` ID。两者不同是上游注册设计，本次已作对应，不属于漏适配证据。[C1]

## 3. 当前联动入口与套娃风险

### 3.1 当前工作站按什么加载

`LargeWorkstationRecipes.getAll()` 遍历 `RecipeManager.getRecipes()`，适配本机配方、白名单类型和扩展 API。当前主要来源如下：[N1][N2]

| 配方来源 | 运行时类型 | 当前专用卡 |
| --- | --- | --- |
| 本机工作站 | `neoecoae:integrated_working_station`、大型工作站原生配方 | 无需卡 |
| 高级 AE | `advanced_ae:reaction` | 高级 AE |
| 扩展 AE / EAEP | `extendedae:crystal_assembler`、`extendedae_plus:crystal_assembler_plus` | 扩展 AE，二者共用一张卡 |
| 水晶科技 | `ae2cs:circuit_etcher_recipe`、`crystal_aggregator_recipe`、`crystal_pulverizer_recipe` | 水晶科技 |
| 水晶科技熵变适配 | 从 `ae2:entropy` 转换，标记为 `ae2cs:entropy_variation_reaction_chamber` | 水晶科技 |
| 闪电科技 | `ae2lt:overload_processing`、`lightning_assembly`、`lightning_simulation` | 闪电科技 |
| 数据能源 | `data_energistics:data_reassembler`、`data_charger`、`data_integrated_charger`、`data_charge_press` | 数据能源 |
| 应用发电 | `appgen:synthesizing` | 应用发电 |

卡片归属目前主要看**配方类型**，不看配方 ID 的命名空间，也不看产物来自哪个 Mod。例如 `neoecoae:crystal_assembler/superconducting_processor` 使用扩展 AE 类型，因此归扩展 AE 卡；产物属于 NeoECOAE 不会让它自动成为免卡配方。

### 3.2 闪电科技是否已经兼容了数据能源

在本次检查的 main 与 alpha 中，未发现超载工厂直接读取 `data_energistics:data_reassembler`、`data_charger` 等四类数据配方的专用入口。不能据此说“数据能源的四类配方已由闪电卡完整覆盖”。

但确实存在**间接覆盖路径**：闪电科技的 `InscriberProcessorRecipeLoader` 会在脚本处理前，从 AE2 压印及工作台配方派生缺失的批量处理器配方。它按 `processor` 路径、压印模式、材料可解析性等筛选，不限定来源 Mod。数据能源自身提供 `data_energistics:ae2/inscriber/data_processor`，因此也属于这套通用派生逻辑的候选。[L2][D2]

这类派生配方会以 `ae2lt:derived/inscriber/<原命名空间>/<原路径>/<摘要>` 写回配方 JSON 集合，之后可被 NeoECOAE 作为 `ae2lt:overload_processing` 读取。但它会展开中间电路板、压印硅等材料，并选择可逆压缩块，**不能直接等同于数据一体机使用电路板的原始压印路线**。是否实际派生、具体材料与数量，取决于当次加载环境，本文不计入静态确认重合数。

### 3.3 已确认的上游借用关系

| 上游关系 | 上游如何实现 | NeoECOAE 当前是否重复读取 | 处理建议 |
| --- | --- | --- | --- |
| 闪电科技 → 高级 AE 反应室 | `OverloadProcessingRecipeCatalog` 调用 `AdvancedAeReactionAdapter`，借用视图保留原 ID，不注册回配方管理器 | 否；当前只读取原高级 AE 配方 | 如要体现闪电机器的覆盖能力，在原配方组增加闪电卡支持关系，避免再造一条执行配方 |
| EAEP → 扩展 AE 晶体装配 | `SuperCrystalAssemblerRecipeManager` 临时转换为 `compat/extendedae/...` 目录视图 | 否；当前遍历配方管理器，不遍历该组合目录 | 两者已共用扩展 AE 卡，继续读取真实来源一次即可 |
| 数据一体机 → AE2 充能、压印及扩展 AE 切片 | `DataChargePressRecipeView` 组合上游配方，并区分机器模式 | 当前没有导入整套借用视图 | 将来接入时保留模板、粉碎、切片、模块模式等语义，不能只按展示堆栈转换 |

上述关系分别见 [L1]、[E1]、[D1]。main 与 alpha 的闪电目录在异常保护上有变化，但“反应室借用视图不进入 `RecipeManager`”这一边界一致。

闪电科技的 `OverloadProcessingRecipe.borrowed()` 会给反应室借用视图设置 **高压闪电 1**，能量字段由适配器传入 `source.getEnergy()`。因此“增加闪电卡支持”需要核对并保存这套费用语义，不能只把闪电卡加入权限集合后，直接沿用高级 AE 的无闪电变体；也不能把这个特殊视图按普通闪电 JSON 的 FE 字段再次换算。[L1]

**因此，防套娃应优先约束导入边界：每条真实配方读取一次，上游机器能力另作支持关系。不要递归调用多个 Mod 的组合配方目录来扩充工作站配方池。**

## 4. 同批次材料重合的具体清单

### 4.1 闪电科技内部：4 组

下面每行的两条配方材料及全部输出一致，均由闪电卡解锁。[L3]

| 产物 | 配方 A | 配方 B | 源费用 A / B |
| --- | --- | --- | --- |
| 闪电坍缩矩阵 ×1 | `ae2lt:lightning_simulation/lightning_collapse_matrix` | `ae2lt:overload_processing/lightning_collapse_matrix` | 均为 50,000,000 FE + 极高压闪电 128 |
| 超载合金板 ×1 | `ae2lt:lightning_simulation/overload_alloy_plate` | `ae2lt:overload_processing/overload_alloy_plate` | 500,000 FE + 高压闪电 4 / 1,000,000 FE + 高压闪电 1 |
| 超载奇点 ×1 | `ae2lt:lightning_simulation/overload_singularity` | `ae2lt:overload_processing/overload_singularity` | 1,000,000 FE + 高压闪电 8 / 2,000,000 FE + 高压闪电 1 |
| 终极超载核心 ×1 | `ae2lt:lightning_simulation/ultimate_overload_core` | `ae2lt:overload_processing/ultimate_overload_core` | 均为 8,000,000 FE + 极高压闪电 8 |

对应材料依次为：完美电鸣晶体 ×1 + 终极超载核心 ×16；超载合金 ×16 + 超载处理器 ×16 + 下界合金锭 ×16；AE2 奇点 ×512 + 下界之星 ×1；超载合金板 ×16 + 超载奇点 ×16 + 回响碎片 ×1。

当前规则先比较 AE 费用、再比较闪电数量、最后比较 ID，所以这些组会保留 `lightning_simulation` 路线；超载合金板与超载奇点体现的是 AE 与闪电消耗之间的取舍，不能把其中一条称作所有成本都更低。FE 转 AE 使用游戏中的 `PowerUnit` 转换，表中保留上游原始 FE。[N1]

### 4.2 闪电科技与水晶科技：4 组

| 产物 | 闪电配方 ID | 水晶配方 ID | 两者一致的材料 |
| --- | --- | --- | --- |
| 谐振处理器 ×36 | `ae2lt:overload_processing/ae2cs_resonating_processor` | `ae2cs:circuit_etcher/resonating_processor` | 纯陨石晶体块标签 ×4 + 纯谐振晶体块标签 ×4 + 硅块标签 ×4 |
| 简易处理器 ×36 | `ae2lt:overload_processing/ae2cs_simple_processor` | `ae2cs:circuit_etcher/simple_processor` | 石英块 ×9 + 红石块标签 ×4 + 硅块标签 ×4 |
| 起源处理器 ×36 | `ae2lt:overload_processing/appgen_origination_processor` | `ae2cs:circuit_etcher/origination_processor` | 余烬块 ×9 + 红石块标签 ×4 + 硅块标签 ×4 |
| 并行处理器 ×36 | `ae2lt:overload_processing/eae_concurrent_processor` | `ae2cs:circuit_etcher/concurrent_processor` | 熵变块标签 ×9 + 红石块标签 ×4 + 硅块标签 ×4 |

四组闪电路线均为 **400,000 FE + 高压闪电 1**，水晶路线均为 **57,600 AE，无额外闪电**。[L3][C2]

建议显示“支持：闪电科技 / 水晶科技”。当前各卡已经可以选择自己的路线，但全局配方去重倾向保留闪电路线，展示层也只显示这一条的卡片。这里需要补齐多来源展示，不能全局删除水晶变体后再沿用单来源权限判断。[N1][N3]

起源处理器来自应用发电、并行处理器来自扩展 AE，并不意味着这两张产物所属 Mod 的卡自动解锁表中的整块材料路线。是否加入额外卡，须有真实配方或明确的机器借用能力作为依据。

### 4.3 应用发电与高级 AE：8 组

前五组是应用发电起源装配器与高级 AE 原有反应配方重合；后三组是应用发电同时为两类机器提供同一加工关系。[G1][A1]

| 产物 | 高级 AE 类型的配方 ID | 应用发电类型的配方 ID | 两者一致的投入与 AE 费用 |
| --- | --- | --- | --- |
| AE2 奇点 ×1 | `advanced_ae:singularity` | `appgen:synthesizer/singularity` | 物质球 ×64 + 熔岩 100 mB；1,000,000 AE |
| 充能赛特斯石英水晶 ×64 | `advanced_ae:certuscharger` | `appgen:synthesizer/charged_certus_quartz_crystal` | 赛特斯石英水晶 ×64 + 水 1,000 mB；1,300,000 AE |
| 福鲁伊克斯水晶 ×64 | `advanced_ae:fluixcrystals` | `appgen:synthesizer/fluix_crystal` | 充能赛特斯石英水晶 ×16 + 红石 ×16 + 下界石英 ×16 + 水 500 mB；200,000 AE |
| 福鲁伊克斯水晶 ×64，粉尘路线 | `advanced_ae:fluixcrystalfromdust` | `appgen:synthesizer/fluix_crystal_duplicate` | 充能赛特斯石英水晶 ×32 + 福鲁伊克斯粉 ×32 + 水 500 mB；100,000 AE |
| 熵变水晶 ×64 | `advanced_ae:entrocrystal` | `appgen:synthesizer/entro_crystal` | 熵变粉 ×32 + 福鲁伊克斯水晶 ×32 + 水 500 mB；100,000 AE |
| 余烬水晶 ×64 | `appgen:reaction/ember_crystal` | `appgen:synthesizer/ember_crystal` | 福鲁伊克斯水晶 ×16 + 火药 ×16 + 烈焰粉 ×16 + 熔岩 500 mB；200,000 AE |
| 余烬水晶 ×64，粉尘路线 | `appgen:reaction/ember_crystal_duplicate` | `appgen:synthesizer/ember_crystal_duplicate` | 福鲁伊克斯水晶 ×32 + 余烬粉 ×32 + 熔岩 500 mB；1,000,000 AE |
| 充能余烬水晶 ×64 | `appgen:reaction/charged_ember_crystal` | `appgen:synthesizer/charged_ember_crystal` | 余烬水晶 ×64 + 熔岩 1,000 mB；1,300,000 AE |

这 8 组应显示“支持：高级 AE / 应用发电”。如果落实闪电科技对反应室的借用能力，还可增加“闪电科技”，但必须复用原始反应配方的执行语义，不要把借用视图当成普通闪电配方重新序列化、套用默认闪电费用。

特别注意：上表起源装配器路线没有额外添加充能余烬水晶。其他起源配方可能有该材料，必须按每条原配方读取，不能给整个 `appgen:synthesizing` 类型统一补上或扣除它。[G1]

## 5. 材料比例相同、批次数不同的清单

### 5.1 静态 JSON 确认的 10 组

每行左侧一批 **4 个**，右侧一批 **32 个**；输入与输出均恰好相差 8 倍，材料谓词在静态 JSON 中一致。[N4][L3][C2][E2][A1]

| 产物 | 扩展 AE 类型配方 ID，4 个 | 水晶科技配方 ID，32 个 |
| --- | --- | --- |
| 超导处理器 | `neoecoae:crystal_assembler/superconducting_processor` | `ae2cs:aggregator/superconducting_processor` |
| 超载处理器 | `ae2lt:assembler/overload_processor` | `ae2cs:aggregator/overload_processor` |
| 累积处理器 | `extendedae:assembler/accumulation_processor` | `ae2cs:aggregator/accumulation_processor` |
| 运算处理器 | `extendedae:assembler/calculation_processor` | `ae2cs:aggregator/calculation_processor` |
| 并行处理器 | `extendedae:assembler/concurrent_processor` | `ae2cs:aggregator/concurrent_processor` |
| 能源处理器 | `extendedae:assembler/energy_processor` | `ae2cs:aggregator/energy_processor` |
| 工程处理器 | `extendedae:assembler/engineering_processor` | `ae2cs:aggregator/engineering_processor` |
| 逻辑处理器 | `extendedae:assembler/logic_processor` | `ae2cs:aggregator/logic_processor` |
| 量子处理器 | `advanced_ae:quantum_processor_eae` | `ae2cs:aggregator/quantum_processor` |
| 简易处理器 | `ae2cs:crystal_assembler/simple_processor` | `ae2cs:aggregator/simple_processor` |

左右都是“已打印电路 + 连接材料 + 已打印硅”的装配路线，不应与第 4.2 节整块材料直接制处理器的路线混合。

目前工作站把扩展 AE 晶体装配费用适配为 **2,000 AE / 4 个**，水晶路线为 **51,200 AE / 32 个**。折算同样 32 个产物，分别为 16,000 AE 与 51,200 AE。归组时保留这两种费用与批量。[N1]

建议统一显示“支持：扩展 AE / 水晶科技”，并能展开查看原始批量。`advanced_ae:quantum_processor_eae` 的类型是扩展 AE 晶体装配，不应仅凭 ID 前缀增加高级 AE 卡。

### 5.2 另有标签交集候选，不能直接并入确认数

| 候选 | 不能直接认定完全相同的原因 |
| --- | --- |
| `ae2cs:aggregator/resonating_processor` 与 `ae2cs:crystal_assembler/resonating_processor` | 谐振处理器装配中的陨石粉使用标签与固定物品两种谓词，须核验最终接受集合 |
| `ae2cs:aggregator/origination_processor` 与 `appgen:assembler/origination_processor` | 4 / 32 批量对应，但连接材料由上游标签常量构造，须与最终注册标签核对 |
| 闪电与水晶的运算、工程、逻辑、能源、累积、量子处理器整块路线 | 存在固定方块与标签的区别；其中标签也可能允许其他 Mod 的替代材料 |
| `advanced_ae:quartzcrystal` 与 `appgen:synthesizer/certus_quartz_crystal_duplicate` | 前者接受赛特斯石英粉标签，后者为固定 AE2 粉尘 |
| `neoecoae:integrated_working_station/crystal_ingot` 与 `ae2lt:overload_processing/neoeco_crystal_ingot` | 材料与数量对齐，但熔岩分别是标签谓词与严格流体堆栈谓词；另有闪电费用差异 |
| `neoecoae:integrated_working_station/energized_superconductive_ingot` 与 `ae2lt:overload_processing/neoeco_energized_superconductive_ingot` | 同上：不能因为都显示熔岩桶就断言流体约束完全一致 |

当前去重会把部分简单标签转换成最终物品集合，但对组件流体和普通流体谓词仍作区分。因此，“普通熔岩实际都能加工”和“已经被去重为同一条”也不是同一结论。[N1]

## 6. 数据能源相关的覆盖与非重复路线

### 6.1 同 ID 的数据水晶种子

`ae2cs:data_reassembler/data_crystal_seed` 同时存在于：

- 水晶科技：`src/generated/resources/data/ae2cs/recipe/data_reassembler/data_crystal_seed.json`。
- 数据能源：`src/main/resources/data/ae2cs/recipe/data_reassembler/data_crystal_seed.json`。

两份材料相同：数据粉 ×8 + 充能赛特斯石英水晶 ×4 + 沙子 ×16 → 数据水晶种子 ×32。但水晶科技版本使用旧的 `item_inputs` / `item_outputs`，数据能源提供的版本使用当前 codec 的 `inputs.items` / `outputs.items`。[C3][D3]

这是同一个资源 ID 的覆盖关系，最终配方管理器不会同时以这个 ID 保留两个配方。需要核验实际数据包顺序与加载日志；若旧格式成为最终资源，可能无法按当前数据能源 codec 加载。应先解决资源覆盖和格式兼容，再谈配方归组。**不建议直接删除数据能源的同 ID 文件**，它可能承担修正旧联动格式的作用。

该数据类型配方当前归数据卡。若希望水晶卡也能制作这条种子路线，应显式声明这条联动的支持关系，而不是让水晶卡获得全部数据重组配方。

### 6.2 相同产物但不能合并的例子

| 产物 / 路线 | 实际差异 | 建议 |
| --- | --- | --- |
| 数据水晶种子：数据重组 vs 水晶聚合 | 重组为数据粉 8 + 充能水晶 4 + 沙子 16 → 32；聚合为数据粉 8 + 充能水晶 8 + 沙子 32 → 32 | 保留独立材料路线 |
| 数据处理器：数据一体机压印 vs 水晶聚合、蚀刻 vs 闪电通用派生 | 电路板装配、原料块直接制成、展开压印链等投入不同；模板与模块语义也不同 | 只有完整材料合同相等的变体才归组 |
| 充能水晶：AE2 充能 vs 高级 AE / 起源装配器 | 后两者需要水与指定批量；原版充能并非相同合同 | 不能只比较水晶输入输出 |
| 能量水晶：数据充压 vs NeoECOAE 的高级 AE 联动 | 数据路线消耗 250 mB 数据腐蚀液，产出 72；高级 AE 路线消耗 500 mB 水，产出 64 | 保留独立路线 |
| 熵变锭：高级 AE vs 起源装配器 | 当前高级 AE 使用熵变粉 32，起源装配器使用熵变水晶 32；其他材料相同也不能忽略此差异 | 保留独立路线 |
| 超载处理器：水晶蚀刻 vs 闪电原生超载 | 水晶使用 `ae2cs:charged_overload_crystal_block`，闪电使用 `ae2lt:overload_crystal_block` | 两种不同方块不能直接合并 |

其中种子与数据加工规则见 [C3][D1][D2][D3]；能量水晶见 [D4][N4]；熵变锭见 [A1][G1]；超载处理器见 [C2][L3]。

## 7. 多升级卡共享配方的建议规则

### 7.1 玩家能看到的结果

1. 同一材料合同在大型工作站的 JEI / EMI 中显示一组。
2. 信息栏注明“支持升级卡：A / B / C”，含义是**任选其一**。仍只占一个升级模块槽，无需叠插多张卡。
3. 切换卡片后，同组可用配方跟随当前卡刷新；提示和实际费用一致。
4. 不同材料、收率或副产物的路线继续独立显示。
5. 批量成比例的路线归组时，展开显示各自最小批量、费用及支持卡，不凭比例生成原本不存在的小批量配方。

例如“谐振处理器 ×36”可显示闪电 / 水晶两张卡：安装闪电卡执行 400,000 FE 换算 AE + 高压闪电 1 的路线；安装水晶卡执行 57,600 AE 的路线。多卡解锁不应让水晶卡被迫支付闪电，也不应让闪电路线丢失闪电费用。

### 7.2 组、来源与执行变体分开保存

| 对象 | 保存内容 | 用途 |
| --- | --- | --- |
| 材料配方组 | 规范化输入、全部输出；可选的比例分组键 | 合并展示与查找 |
| 来源集合 | 原配方 ID、原类型、直接导入 / 派生 / 借用关系 | 多卡提示、诊断与追溯 |
| 执行变体 | 原始数量、全部资源费用、合法批量、可执行的卡集合 | 确定机器真正执行哪条路线 |
| 原 ID 索引 | 每个原配方 ID 对应的执行变体 | 恢复已经开始的存档任务 |

同组支持卡取所有有效变体的并集。**并集只决定哪些卡可以打开该加工关系；不能因此让每张卡使用组内任意费用变体。**

运行时借用可以增加支持卡，但必须先核对借用是否改变输入约束、能耗、闪电或机器模式。不能通过产物命名空间推断支持卡，也不能仅凭 A 机器能做 B 配方，就让 A 卡获得 B Mod 所有类型的配方。

### 7.3 去重与选路顺序

建议继续保留现有“先筛选当前卡，再选择变体”的边界：

1. 从最终配方管理器收集真实配方；识别会写回管理器的派生配方。
2. 建立材料合同和来源关系；对上游借用只附加已验证的支持关系。
3. 收集组内变体，合并支持卡信息，生成 JEI / EMI 视图。
4. 按当前卡、样板完整投入产出及合法批量筛选可执行变体。
5. 在这些变体中应用成本排序，执行时绑定明确变体。

当前排序为：**有闪电投入的变体优先 → AE 费用更低 → 额外资源总数量更少 → 配方 ID**。本文不建议顺带改变已存在的闪电优先规则，但应将其限制在当前卡合法的变体中。[N1]

比例分组的费用比较必须换算到同一批次，不能直接比较 2,000 AE / 4 个与 51,200 AE / 32 个的绝对值。归组也不能把严格组件谓词、催化剂返还、概率产物、数据流等非闪电资源忽略掉。当前实现只将闪电作为成本排序项，其他自定义资源仍参加材料合同比较，这个边界应保留。

如果以后希望“任意支持卡都享受同一个最便宜费用”，那是额外的平衡规则变更，应另行明确；实现多卡解锁本身无需附带这项变化。

### 7.4 防止套娃的导入约束

- 不把 `OverloadProcessingRecipeCatalog`、EAEP 的组合目录或数据一体机展示列表整体递归导入。
- 一条真实来源只建立一个执行来源记录；上游借用作为能力关系附着于该来源。
- 派生配方保留其 ID 与原料合同，不按产物直接吸收为原始压印配方。
- 对同 ID 的资源覆盖，先处理最终资源及格式，不把被覆盖文件算作独立候选。
- 来源关系只在已加载、适配成功且满足条件的配方之间建立；数据包删除原配方后，借用支持也随之失效。

## 8. 建议实施顺序与验收

本次仅交付文档，以下是后续代码工作的建议顺序。

| 顺序 | 工作 | 主要代码位置 |
| --- | --- | --- |
| 1 | 引入配方组、来源集合和变体索引；保留原 ID 查找 | `LargeWorkstationRecipe`、`LargeWorkstationRecipes` |
| 2 | 对第 4 节同合同配方聚合多卡提示，保持按卡选路 | `LargeWorkstationModule`、`ECOWorkstationModules`、`selectForModule` |
| 3 | JEI / EMI 共用多卡提示与费用来源信息 | `LargeWorkstationRecipeDisplay` 及两个查看器接入层 |
| 4 | 显式加入已核实的上游借用支持，避免新增导入链 | 工作站适配与来源关系注册层 |
| 5 | 处理第 5 节比例分组，保留各卡合法批量 | 分组键、样板匹配及费用显示 |
| 6 | 最终运行环境核对标签交集、同 ID 覆盖和动态派生 | 配方加载日志、最终配方管理器 |

建议验收案例：

- 闪电科技内部 4 组，每组仅显示一次，能耗与闪电均正确。
- 闪电 / 水晶的 4 组，各卡都能制作，水晶路线不被全局闪电优先隐藏。
- 高级 AE / 应用发电的 8 组，分别插卡后执行结果一致；展示列出两张卡。
- 闪电借用反应室后，同一真实反应配方没有增加第二条执行记录，费用按借用语义核对。
- 4 / 32 批量的 10 组，归组后保留合法批次，放大样板按实际变体结算材料和费用。
- 标签存在额外替代材料时，不会因归组丢失较宽或较窄的输入范围。
- 数据种子同 ID 的最终资源能够按当前 codec 加载，且不会被统计为两个任务来源。
- 关闭某个联动、移除配方或重载数据包后，支持卡集合与缓存同步刷新。
- 归组前已保存的批次，仍能按原 ID 恢复原费用、材料和进度。
- `largeWorkstationIgnoreUpgradeCards=true` 时，仍保留必要资源与真实材料约束。

本次完成的是静态源码与配方资源核对，未启动游戏，未验证实际整合包加载顺序、动态派生数量或 JEI / EMI 画面。整合包的最终重合清单应以加载后的配方与标签为准。

## 9. 源码索引

以下本仓库链接指向当前文件；外部仓库链接按 `E:\Minecraft Project` 的同级目录组织。复核时以第 2 节引用为准；工作区分支不同的文件可用 `git show <引用>:<路径>` 读取。

| 编号 | 证据入口 |
| --- | --- |
| N1 | [LargeWorkstationRecipes.java](../src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationRecipes.java)：收集入口、材料合同、排序、先筛选后去重、原 ID 缓存 |
| N2 | [LargeWorkstationModule.java](../src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationModule.java)、[ECOWorkstationModules.java](../src/main/java/cn/dancingsnow/neoecoae/api/workstation/ECOWorkstationModules.java)：卡片与类型归属 |
| N3 | [LargeWorkstationRecipeDisplay.java](../src/main/java/cn/dancingsnow/neoecoae/integration/recipe/LargeWorkstationRecipeDisplay.java)、[去重测试](../src/test/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationRecipeDeduplicationTest.java)：单卡展示及按卡选路证据 |
| N4 | [NeoECOAE 配方资源](../src/generated/resources/data/neoecoae/recipe)、[EAERecipes.java](../src/main/java/cn/dancingsnow/neoecoae/data/recipe/EAERecipes.java)、[AAERecipes.java](../src/main/java/cn/dancingsnow/neoecoae/data/recipe/AAERecipes.java) |
| L1 | [OverloadProcessingRecipeCatalog.java](../../AE2-Lightning-Tech-Reborn/src/main/java/com/moakiee/ae2lt/machine/overloadfactory/recipe/OverloadProcessingRecipeCatalog.java)、[AdvancedAeReactionAdapter.java](../../AE2-Lightning-Tech-Reborn/src/main/java/com/moakiee/ae2lt/machine/overloadfactory/recipe/AdvancedAeReactionAdapter.java) |
| L2 | [InscriberProcessorRecipeLoader.java](../../AE2-Lightning-Tech-Reborn/src/main/java/com/moakiee/ae2lt/machine/overloadfactory/recipe/InscriberProcessorRecipeLoader.java)、[InscriberProcessorAdapter.java](../../AE2-Lightning-Tech-Reborn/src/main/java/com/moakiee/ae2lt/machine/overloadfactory/recipe/InscriberProcessorAdapter.java) |
| L3 | [闪电科技配方资源](../../AE2-Lightning-Tech-Reborn/src/main/resources/data/ae2lt/recipe) |
| C1 | [AECSRecipeTypes.java](../../AE2-Crystal-Science/src/main/java/io/github/lounode/ae2cs/common/init/AECSRecipeTypes.java) |
| C2 | [水晶科技配方资源](../../AE2-Crystal-Science/src/generated/resources/data/ae2cs/recipe) |
| C3 | [数据能源联动生成器](../../AE2-Crystal-Science/src/main/java/io/github/lounode/ae2cs/datagen/recipes/compat/AECSCompatDataEnergisticsRecipeProvider.java)、[旧格式种子资源](../../AE2-Crystal-Science/src/generated/resources/data/ae2cs/recipe/data_reassembler/data_crystal_seed.json) |
| D1 | [DataChargePressRecipeView.java](../../DataEnergistics/src/main/java/com/fish_dan_/data_energistics/integration/viewer/xei/recipe/DataChargePressRecipeView.java) |
| D2 | [数据能源压印配方](../../DataEnergistics/src/main/resources/data/data_energistics/recipe/ae2/inscriber)、[DataChargePressRecipeSupport.java](../../DataEnergistics/src/main/java/com/fish_dan_/data_energistics/common/recipe/chargepress/DataChargePressRecipeSupport.java) |
| D3 | [DataRipperReassemblerRecipeSerializer.java](../../DataEnergistics/src/main/java/com/fish_dan_/data_energistics/api/recipe/reassembler/DataRipperReassemblerRecipeSerializer.java)、[新格式种子资源](../../DataEnergistics/src/main/resources/data/ae2cs/recipe/data_reassembler/data_crystal_seed.json) |
| D4 | [DERecipeProvider.java](../../DataEnergistics/src/main/java/com/fish_dan_/data_energistics/datagen/DERecipeProvider.java) |
| E1 | [SuperCrystalAssemblerRecipeManager.java](../../ExtendedAE_Plus/src/main/java/com/extendedae_plus/recipe/SuperCrystalAssemblerRecipeManager.java)，使用 `origin/1.21.1` 读取；本地其他分支未必有此文件 |
| E2 | [扩展 AE 配方资源](../../ExtendedAE/src/main/resources/data/extendedae/recipe) |
| A1 | [高级 AE 配方资源](../../AdvancedAE-1.21.1/src/main/resources/data/advanced_ae/recipe) |
| G1 | [AGRecipeProvider.java](../../AppliedGenerators/src/main/java/io/github/sapporo1101/appgen/datagen/AGRecipeProvider.java)、[GenesisSynthesizerRecipeBuilder.java](../../AppliedGenerators/src/main/java/io/github/sapporo1101/appgen/recipe/GenesisSynthesizerRecipeBuilder.java) |

## 10. 实施状态与展示层接口

已实现通用的材料比例分组，适用于本次审计清单及数据包新增的同合同配方。展示每组只取一个真实数据包来源的原始代表，避免把虚拟借用别名变成 JEI / EMI 或书签的来源 ID；执行保留全部原始变体。按卡筛选后，根据样板的全部输入输出及整数批量匹配变体，在相同能源合同内比较同批次 AE/FE 与额外资源费用；保留闪电优先和稳定 ID 顺序。显式填写额外资源的样板也可以匹配未被选作展示代表的原始路线。

已加入两条明确的上游能力关系：支持公开 `borrowed(...)` 能力的闪电科技版本，可执行兼容的高级 AE 反应配方，保留工厂实际 FE 预算与加工时间，并收取高压闪电 1（网络 FE 供能必须安装 Applied Flux）；数据水晶种子的那条数据重组配方，在水晶科技已加载时也支持水晶卡。前者使用稳定的运行时执行别名保存任务，保留原始来源 ID，不向配方管理器注册新配方；后者只扩展该条种子的权限。

没有导入 EAEP 或数据一体机的组合目录，也没有覆盖第三方 Mod 的同 ID 种子资源。旧格式种子是否被最终数据包覆盖的问题，仍以实际加载日志和资源优先级为准；工作站只适配最终成功加载的真实配方。

展示层沿用已有动态布局，通过以下接口接入：

```java
var group = LargeWorkstationRecipes.getGroup(level, recipe.id());
if (group != null) {
    var moduleIds = group.supportedModules();   // 多卡任选，不是同时安装
    var cardItemIds = group.upgradeCards();
    var noCardRequired = group.availableWithoutCard();
    var routes = group.availableVariants(installedModuleId);
    // 每个 route.recipe() 保留原始批量、energy()、extraInputs() 和完整输出。
    // route.sourceId()/sourceType()/borrowed() 用于来源追溯。
}
```

`LargeWorkstationRecipeDisplay.cards()` 已将分组卡片及各卡路线费用接入动态卡片槽，JEI / EMI 共享。`getAll()` 是展示列表，`getAvailable()` 是执行列表，不能用前者替代后者或假设两个列表长度相同。全部原 ID 和借用别名都能通过 `getById()` 恢复原始变体；标签更新会使分组和按卡列表缓存失效。

实施验证：配方、工作站、动态布局及分组展示相关的 **306 项测试通过，0 失败、0 错误、0 跳过**；包含真实高级 AE codec 的读取与再编码、借用费用和流体条件保留、原 ID 恢复、按卡批量、显式闪电、标签失效及附属模块权限。JAR 构建通过，已核对六种语言及中英文指南与源码字节一致，测试用 AE2AddonLib 没有混入发布包。构建输出位于 `.gradle/workstation-recipe-build/libs/neoecoae-21.2.9.jar`。尚未进行真实游戏内加工与查看器画面验收。

2026-10-24 后续实施已同步来源时间并将基础配方封顶至 200 tick，保留总费用，FE 缺额允许等价 AE 代偿。查看器只显示耗能、加工时间和适用时的额外需要，安装 Applied Flux 时 FE 配方优先显示 FE，否则显示等价 AE；详情按 Mod 主题色标题分段放入 Tooltip。升级卡固定右侧，JEI 物品数量使用小字与 SI 缩写，悬停显示完整数量。新旧任务的时间、能源支付和验收说明见[加工时间与网络 FE](WORKSTATION_PROCESSING_TIME_AND_FE_ZH_CN.md)。
