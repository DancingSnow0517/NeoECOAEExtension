# Forge 1.20.1 配方与 NBT 适配检查点

更新日期：2026-10-20。主体仍为锁定 main `dc8344f48ee866e57e02c285fdda5e0fb5da9421`，没有使用旧 1.20.1 工程替换业务或存储后端。此前平台/范围检查点为 `6bcdd5a`；本文件描述其后的实现。全部验证串行运行，不推送。

## 实现与验证边界

`portingPlatformTest` 46 项、`portingContractsTest` 4 项通过；`python tools/porting/test_convert_data_resources.py` 8 项通过。平台 source set 编译指定的真实生产类，包括配方、目录、附属适配器和 NBT helper；main 没有排除业务文件。最新完整编译仍失败：**1,505 errors、12 warnings**。

测试使用真实锁定发布 JAR 中的 AAE、AE2LT 配方对象，实际 Forge registry 和 AE2 key 编解码。JUnit 不具有 ModLauncher 的类变换和正常启动生命周期：网络启动边界仍用 fixture 隔离，NBT 测试通过测试专用入口创建原生 registry。它们不能证明 Mod 启动、机内执行、库存交互、UI 视觉、多人或生产安装通过。最新日志为 `validation/porting-data-test-final.log` 和 `validation/compile-data-checkpoint-final.log`，统计与哈希见 `porting/validation-state.json`。

## 配方边界

- `CoolingRecipe`、`IntegratedWorkingStationRecipe`、`LargeIntegratedWorkingStationRecipe` 使用真实 1.20 `fromJson/fromNetwork/toNetwork`，保留配方 ID；只读输入实现原生 `Container`，禁止把快照作为可修改库存。
- 小工作站最多 9 项输入，大工作站最多 12 项；过大的网络长度在分配前拒绝。产物数量使用独立 varint，实际物品模板只同步 count=1，避免 1.20 原生字节数量截断。JSON、NBT、流体及超过 127 的数量有往返验证。
- 项目 `SizedIngredient` 保留原生物品谓词与数量。`FluidIngredient` 保留标签、多个备选、严格/部分 NBT；未知谓词明确拒绝。标签在解析/同步时保留标识，不冻结成当时的显示流体列表。
- 大集成仍按完整物料契约去重，先按模块筛选，避免锁定机型的廉价配方遮蔽已解锁配方。原生 `RecipeManager` 的 `byName` map 通过 accessor 识别重载代际；无变换器的测试使用逐 ID 对象身份检查，不能依赖 1.20 每次重新创建的 `getRecipes()` 集合身份。
- AAE 从实际反应室对象读取输入/流体/输出/能耗；AE2LT 三类配方保留来源、雷电层级和数量，并沿用 AE2 的实际 FE/AE 转换比率。能耗换算避免先把超大整数转成 double。当前输出契约与 main 一样支持单物品加单流体，无法表达的多输出或借用流体谓词明确拒绝。

## EAE 大集成缺口的落地方案

ExtendedAE 1.20.1 的 Mod ID 为 `expatternprovider`；实际 JAR 没有 1.21 的水晶装配器 serializer，也没有 entro ingot 和 concurrent processor。没有调用不存在的 builder 或把 EAE 机型禁用。

新增 `neoecoae:extended_crystal_assembler`，通过 `expatternprovider` 加载条件提供。大集成目录将其明确归入 EAE 来源；仅 EAE 模块（以及原配置的忽略卡模式）能解锁。现有 main 配方为：

| 输入/费用 | 数量 |
|---|---:|
| 超导处理器印模 | 4 |
| 水晶矩阵 | 4 |
| AE2 硅印模 | 4 |
| 产出：超导处理器 | 4 |
| AE 能耗 | 2,000 |

这是 ECO 自有配方，不在第三方 Mod 中增加方块或机器。注册、JSON/网络和实际物料匹配已有隔离验证；完整机器投料、扣能和产出仍是待验收项。

EAE 升级卡中目标不存在的物品做以下显式映射，保留各输入数量及总能耗 16,384,000 AE：

| main 物品 | 1.20.1 输入 | 数量 |
|---|---|---:|
| `extendedae:crystal_assembler` | `expatternprovider:ex_molecular_assembler` | 8 |
| `extendedae:entro_ingot` | `neoecoae:energized_superconductive_ingot` | 256 |
| `extendedae:concurrent_processor` | `neoecoae:superconducting_processor` | 128 |

1.20 Circuit Cutter 继续用原生 EAE 类型及实际 `item_input` 字段。1.21 的三个自定义 crystal-fixer 配方删除：目标版本的晶体修复器使用固定逻辑，没有该配方 API。

## 拆解与资源

AE2 15 没有 AE2 19 的 `StorageCellDisassemblyRecipe`。项目实现 `neoecoae:storage_cell_disassembly`，保留 main 的“物品操作读取元数据”设计。它不参与工作台匹配；真正的拆解仍由 `ECOStorageCellItem` 在服务端检查空盘、替换手持物并归还产物/升级。数据定义和 lookup 返回独立物品副本，支持重载。

静态账本核对了当前已有的 9 条定义。发现并修正源 main 的三种闪电盘组件返还增殖：16M/64M/256M 的投入为 1/2/4，原返还为 4/16/64，现返还为 1/2/4。保持原有的盘壳、ECO 组件和闪电组件这三种返回物，不把额外加工耗材加入返回。main 源码中的 Bulk Item 三等级 datagen 仍保留，实际资源须在完整 datagen 恢复后生成并复核，不能用当前 9 条账本冒充所有存储盘已经验收。

`data-resource-conversion.json` 为 621 份输入记录原路径/哈希、目标路径/哈希和后续修订。最终保留 428 份；193 份因排除附属或目标不存在的 API 删除，包括第二轮发现的 175 份条件附属资源。单数目录改为 1.20 复数目录，NeoForge 条件改为 Forge，`c` 公共标签改为 `forge`，同步调整 Java 标签入口。

`verify_data_resources.py` 核对当前目标哈希、目录/条件残留和显式物料账本。它没有加载游戏 registry，也未替代 datagen 或游戏重载。原始 `docs/porting-2026-10-20/evidence/` 未改写。

## NBT 状态

`NEItemData` 不在读取时创建标签；更新先复制再替换，保留其他物品数据，异常更新不会发布半成品。普通 ECO 盘内容使用项目拥有的 `neoecoae_cell_contents`：`format=1`，`stacks` 为 AE2 原生 GenericStack NBT 列表，数量为 long。未知 key、坏列表或版本不匹配明确失败并保留原始 NBT。模糊模式单独存储，清空内容不会删除其他标记。

无限盘成员身份/迁移域、恢复回执、控制器域和精确 CPU 恢复载体改接 1.20 根标签；工作线程便携恢复使用原生 `BlockEntityTag`。记忆卡使用 AE2 15 的 `CompoundTag` settings 钩子，自动导出侧向保存为独立键，缺失与显式清空能够区分。

NBT helper 已验证带 NBT 的真实 AE2 key、`Long.MAX_VALUE`、普通物品保存/读取、未知 key 不覆写、坏数量更新失败和其他元数据保留。相关业务 BE、主存储/恢复和 FastPath 仍未完整编译；异常只读状态、保存/卸载/拆除、恢复守恒与客户端显示尚不能标记通过。这里没有实现跨 Minecraft 版本世界格式转换。

## 继续实施

下一阶段仍按原计划推进其余 LDLib1 UI 组件、AE2 15 合成/菜单/执行 API、BE/NBT 保存边界和结构终端共享后端。恢复完整编译后运行已有业务测试、datagen、开发客户端/专服，再做普通 Forge 安装和真实功能验收。
