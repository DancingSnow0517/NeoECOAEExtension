# 架构冗余审计与整改计划

> 日期：2026-10-13　基线：`src/main/java` 104,250 行，`./gradlew test --offline` 通过（BUILD SUCCESSFUL）。
> 路径默认相对 `src/main/java/cn/dancingsnow/neoecoae/`。✅ = 已人工复核；未标 = 子代理报告、执行前需再确认。

---

## 1. 执行规则

1. **分工**：总策划（Opus）负责拆分、派发、审查与集中编译测试；执行由 Haiku 子代理完成，每个任务尽量小、文件集互不重叠。
2. **不提交**：全程不 `git commit` / `git push` / `git stash`，不切分支。
3. **保护未提交改动**：分支 `refactor/architecture-cleanup` 从 `4908324b` 切出，切出时工作区干净。每一波开工前总策划都要重新执行 `git status`；如果出现不是本计划产生的改动（你可能同时在改代码），一律视为用户改动，只能在其上增量修改，禁止 `checkout`、`restore`、覆盖或恢复。
4. **子代理不跑 Gradle**（避免并发锁），由总策划每波结束后统一执行：
   - `./gradlew compileJava compileTestJava test --offline`
   - 涉及数据生成的波次再跑 `./gradlew runData --offline`，并检查 `src/generated` 的 diff 只含预期变更。
5. **子代理不得写临时文件到仓库内**；只读调查任务不得改任何文件。
6. 每个任务完成后提交"改动清单 + 理由 + 未处理项"，由总策划审 diff；不符合要求的退回重做。
7. 对 fork 自 AE2 的类，语义以 AE2 19.2.18 为准（源码在 `~/.gradle/caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/19.2.18`）。
8. **以简化为目标**：明显无意义的代码直接删掉，比如死分支、只做转发的包装方法、重复的副本；明显有更简洁写法的就改，比如手写循环其实就是 `anyMatch`、`sum`、`Map.merge`、`removeIf`。**不做**为了风格统一而把循环批量改成 Stream；热路径保持原样。不引入 Kotlin。
9. **多版本约束优先**：第 2～4 批新增或移动的代码，一律按第 3 节的分层落位。新的纯逻辑辅助类不得引入 `net.minecraft`、`net.neoforged`、`appeng`、`lowdragmc`、`com.mojang`、`com.tterrag` 依赖。审查时，层级放错按不合格退回。

---

## 2. 审计结论摘要

| 维度 | 主要问题 |
|---|---|
| 横切能力重复 | 数字格式化约 12 个入口 / 5 套单位表；溢出安全运算 9～11 份私有副本且负数语义不一；BigInteger 网络编解码 4+1 份；`Map<AEKey,BigInteger>` NBT 3～4 套；精确数量类型 ≥6 种；配方图模型 5 套；Kahn 拓扑 ×4、Tarjan ×2 |
| 平行类复制 | 集成/大型工作站；合成/计算系统控制器；三个集群计算器���`NELogicalNetworkManager` 双路径；流体仓 ×4；散热口、并行核心；AE2/AdvancedAE mixin 成对；JEI/EMI 类别；集成存储盘族；`NEBlocks`/`NEItems` 构建链 |
| 重造轮子 | `ECOStorageCell`≈AE2 `BasicCellInventory`；存储盘 tooltip/拆解≈`BasicCellHandler`；`StorageInterfaceFilter` 未用 `ConfigInventory`/`IPartitionList`；自写 AEKey 编解码；`ECOIOPortTransfer`≈`IOPortBlockEntity.transferContents` |
| 过度分层 | 执行模式枚举 ×4；规划结果载体 ×5；模式身份 ×3；工作 DTO ×5；并行提供者 3 层包装；存储 BE 约 20 个透传方法；两个同义 C2S 包 |
| 死代码 | 已核实整类 1,632 行 + 若干死方法/字段 + 33 个模型 JSON |
| 结构 | `integration/` 与 `compat/` 边界失守；模组 ID 无中心注册；`mods.toml` 漏约 13 个可选依赖；配置注释与语言文案漂移 |

预计收益：第 1～3 批约 −6,000～7,500 行（6～7%），连第 4 批约 −10%。主要收益是消除"多份实现各自漂移"引起的 bug。

---

## 3. 多版本移植架构（1.7.10 / 1.20.1 / 1.21.1 / 26.1.2）

本次重构要同时为后续移植铺路：先把"与版本无关的逻辑"和"与版本绑定的胶水代码"分开，以后每移植一个版本，只重写胶水层。

### 3.1 目标版本矩阵

| 项 | 1.7.10 | 1.20.1 | 1.21.1（当前） | 26.1.2 |
|---|---|---|---|---|
| 加载器 | Forge 10.13.4.1614（GTNH 工具链 / RetroFuturaGradle） | Forge 47 | NeoForge 21.1 | NeoForge 26.1.2.x |
| AE2 | **GTNH AE2**（`Applied-Energistics-2-Unofficial`，rv3 系） | AE2 15.x | AE2 19.2.18 | AE2 26.1.x（当前 beta） |
| AE2 物品/数量模型 | `IAEStack<T>`：键与数量**合为一个可变对象**，`IAEStackType` 注册表，`IItemList`；用 `isSameType` 判等 | `AEKey`（不可变）+ long，`KeyCounter`、`GenericStack` | 同 1.20.1 | 同 1.20.1 ✅ |
| AE2 合成 API | `ICraftingPatternDetails`（`getCondensedAEInputs/Outputs`）、`ICraftingMedium`、`ICraftingProvider` | `IPatternDetails`、`ICraftingProvider`、`CraftingService` | 同 1.20.1 | 同 1.20.1 ✅（`CraftingCpuLogic`、`ListCraftingInventory` 都还在） |
| Java | 字节码 Java 8，开发时用 Jabel 或 **jvmDowngrader**（GTNH AE2 构建脚本同时支持，后者连同标准库 API 一起降级） | 17 | 21 | **25**（AE2 26.1 的 class 版本为 69 ✅） |
| 持久化 | `NBTTagCompound` | `CompoundTag`，`ItemStack` 带 NBT | `CompoundTag` + `HolderLookup` + DataComponents | **`ValueInput`/`ValueOutput`**（AE2 26.1 `AEKey.toTag(ValueOutput)` ✅） |
| 网络 | `SimpleNetworkWrapper` + `ByteBuf` | `SimpleChannel` + `FriendlyByteBuf` | `CustomPacketPayload` + `StreamCodec` | 同 1.21.1（AE2 26.1 仍有 `AEKey.STREAM_CODEC` ✅） |
| UI | GTNH **ModularUI2**（GTNH AE2 依赖它）/ `GuiContainer` | 无 LDLib2（需评估 LDLib 1.x 或 AE2 `AEBaseScreen`） | LDLib2 | LDLib2 `26.1` 分支 ✅ |
| 注册 | `GameRegistry` | Registrate（Forge） | Registrate | 待确认 |
| 能力 | 接口直接实现在 `TileEntity` 上 | Forge `LazyOptional` | NeoForge `BlockCapability` | 待确认（NeoForge 新 transfer API） |
| Mixin | UniMixins / GTNH mixin 加载器 | Forge Mixin | NeoForge Mixin | NeoForge Mixin |

**结论**：
- 1.20.1、1.21.1、26.1.2 属于同一代 AE2 API（`AEKey` 系），可以共享大部分 AE2 相关代码，差异集中在持久化、网络、能力、渲染和 UI。
- 1.7.10 是另一代 API（`IAEStack` 系），能共享的只有不依赖 AE2 的纯逻辑。

### 3.2 目标分层

依赖方向只允许从上往下：L3 → L2 → L1 → L0。

| 层 | 内容 | 允许依赖 | 各版本处理方式 |
|---|---|---|---|
| **L0 core** | 规划算法（求解、环、增长、路由）、图与拓扑/SCC、精确数量与溢出安全运算、调度与批量限额、停滞诊断、数字格式化的数值部分、多方块结构定义数据、方块等级/存储盘规格表、配置项表、语言 key 表、UI 视图模型（面板状态、tooltip 内容） | 只依赖 JDK（加 fastutil） | **所有版本共用同一份源码** |
| **L1 ae-bridge** | 键与模式的适配（`KeyOps<K>`、`PatternView<K>`）、规划器编译边界（`planner/compile`）、存储盘适配、CPU 与提供者的推送适配、网格服务查询 | L0 + AE2 | 两套：**AEKey 代**（1.20.1/1.21.1/26.1 共用源码，差异用小适配类隔离）和 **IAEStack 代**（1.7.10） |
| **L2 platform** | 持久化抽象 `DataWriter/DataReader`、网络缓冲抽象 `PacketBuf`、注册、能力、服务端 tick 调度、配置绑定、语言/模型生成器输出 | L0/L1 + MC/加载器 | 每个版本一套，尽量薄 |
| **L3 content/ui/compat** | 方块、方块实体、渲染器、界面控件、mixin、JEI/EMI/Jade、第三方模组集成 | 全部 | 每个版本一套；mixin 只负责挂钩，逻辑委托给 L1/L0 |

### 3.3 当前代码的可移植性（"纯"= 不 import MC/NeoForge/AE2/LDLib/Mojang/Registrate）

| 包 | 文件数 | 纯文件 | import AE2 | 说明 |
|---|---|---|---|---|
| `crafting/amount` | 4 | 4 | 0 | 可直接作为 L0 |
| `crafting/graph` | 16 | 15 | 1 | 基本可直接作为 L0 |
| `crafting/planner` | 115 | 35 | 80 | 主要障碍：`AEKey`（100 处 import）、`IPatternDetails`（65 处）。需要泛型化 |
| `crafting/execution` | 87 | 17 | 66 | 调度决策抽到 L0，执行胶水放 L1 |
| `api/me` | 54 | 18 | 31 | 对外 API，各版本分别发布 |
| `impl/storage` | 26 | 6 | 17 | 字节计算、无限存储引擎可以泛型化 |
| `gui` / `blocks` / `client` / `integration` / `mixins` | 346 | 33 | — | 属于 L3，按版本重写；视图模型抽到 L0 |

### 3.4 关键设计决定

1. **规划器对键类型做泛型**：规划器写成 `<K>` 泛型，配一个 `KeyOps<K>` 策略，而不是给每个 `AEKey` 包一层对象，热路径上零额外分配。1.7.10 的 `IAEStack` 是可变的，进入规划器前必须转成不可变键（由 L1 负责，例如 `IAEStack.copy().setStackSize(1)` 后驻留）。
2. **`planner/compile` 是唯一的 AE→core 翻译边界**：只有编译层读取 `IPatternDetails` / `ICraftingPatternDetails`，下游求解器只认编译后的结构。
3. **持久化走 `DataWriter`/`DataReader`**：规划、任务、精确数量表等领域编解码器只写这个抽象接口，各版本分别实现在 `CompoundTag`、`ValueOutput`、`NBTTagCompound` 上。第 2 批的 NBT 辅助类（T2-4、T2-5）直接按这个接口实现。
4. **网络走 `PacketBuf`**：领域数据的编码（BigInteger、键→数量差量、图快照）写到抽象缓冲上，每个版本只包一层载荷。
5. **mixin 只做挂钩**：`CraftingServiceMixin`（824 行）、`CraftConfirmMenuMixin`（969 行）里的逻辑迁到 L1 服务类，mixin 只剩注入点加一行委托。每个版本的 mixin 目标都不同，mixin 越薄，移植量越小。
6. **内容声明式化**：等级、存储盘规格、工具套装、多方块结构、配置项、语言 key 都改成 L0 数据表；各版本的注册代码、数据生成代码只遍历这些表（1.7.10 输出 `.lang`，新版本输出 JSON）。
7. **UI 视图模型与控件分离**：面板状态、文字、格式化、tooltip 行这些内容放在 L0；控件构建（LDLib2、ModularUI2、AE2 Screen）各版本分别实现。`screens/*.json` 布局的 schema 尽量保持稳定。
8. **L0 的 Java 语言约束**（以最严格的 1.7.10 为准，见 D5）：
   - 如采用 jvmDowngrader，L0 可以使用 Java 21 的语法和标准库；
   - 如采用 Jabel，L0 **不能用 record**，也不能用 Java 9+ 标准库（`List.of`、`Math.clamp`、`String.formatted`、`Stream.toList` 等）。
   - 这个决定会影响第 2～4 批新写代码的写法，**必须在第 2 批开工前定下来**。

### 3.5 新增与调整的任务

| ID | 批次 | 内容 |
|---|---|---|
| T2-0 | 第 2 批开头 | 建立 `cn.dancingsnow.neoecoae.core` 包，并加一个单元测试，扫描 `core` 下所有源码，禁止出现上述禁用的 import（相当于简化版 ArchUnit）。第 2 批的纯辅助类（`NEMath`、`TopoSort`、SCC、`ThreadScope`、格式化的数值部分、`BigIntegers`）一律放进 `core` |
| T2-4/T2-5（调整） | 第 2 批 | NBT 辅助类按 `DataWriter`/`DataReader` 接口设计，1.21.1 实现基于 `CompoundTag` |
| T2-3（调整） | 第 2 批 | BigInteger 编解码写到 `PacketBuf` 抽象上 |
| T3-11（调整） | 第 3 批 | 构建链参数化时，先把规格抽成 `core` 里的数据表，再由 Registrate 遍历 |
| T4-7（调整） | 第 4 批 | mixin 瘦身的目标是"每个注入方法 ≤ 5 行，只做委托" |
| T4-13 | 第 4 批 | 规划器泛型化（`<K>` + `KeyOps<K>` + `PatternView<K>`），收紧 `planner/compile` 边界，并把 `planner` 下的纯逻辑迁到 `core` |
| T4-14 | 第 4 批 | 执行层拆分：调度、限额、停滞诊断、批量规划放 `core`；CPU、提供者推送放 L1 |
| T4-15 | 第 4 批 | 存储引擎泛型化：无限存储引擎、字节计算、迁移模拟放 `core`；存储盘与 AE2 存储的适配放 L1 |
| T4-16 | 第 4 批 | 持久化与网络抽象落地：`DataWriter`/`DataReader`/`PacketBuf` 的接口放 `core`，1.21.1 实现放 L2 |
| T4-17 | 第 4 批 | UI 视图模型抽离：`StoragePanelState`、`CraftingControllerPanelState`、`HostText` 的内容部分等移入 `core`，不能引用 LDLib |
| T4-18 | 第 4 批 | 语言 key 表与配置项表移入 `core`；1.21.1 的生成器读这些表 |
| T4-19 | 第 4 批末 | 拆成 Gradle 多项目（`:core` 加各版本工程），方案见 D6。拆分前先保证 `core` 包的禁用 import 测试长期为绿 |

---

## 4. 第 0 批：正确性修复

### 第 A 波（并行）

| ID | 问题 | 文件集 | 做法 | 验收 |
|---|---|---|---|---|
| T0-1 ✅ | **嵌套存储盘检查对 ECO 盘无效**：ECO 盘未注册进 AE2 `StorageCells`，`getCellInventory` 恒为 null，非空 ECO 盘可被塞进 ECO 盘或无限域 | `impl/storage/ECOStorageCell.java`（`canStoreKeyInsideStorageCell`）、`impl/storage/infinite/ECOInfiniteStorage.java`（`canStoreForInsert`）、`src/test/.../impl/storage/infinite/ECOInfiniteStorageHotPathTest.java` | 先查 `ECOStorageCells.getCellInventory`，未命中再查 AE2 `StorageCells`；保留准入缓存，使 `ECOStorageCell` + `ECOStorageCellItem` 的稳定分支真正可达；同步修改测试中对 `StorageCells` 的 mock | 新增回归测试：非空 ECO 盘被拒、空 ECO 盘可存；原测试通过 |
| T0-2a | 溢出安全运算收口（存储/集成侧） | `crafting/amount/NEMath.java`（可新增 `int` 重载、`saturatingIncrement`、`clampToLong(BigInteger)`）、`impl/storage/StorageByteAccounting.java`、`integration/ae2omnicells/ECOUniversalStorageCell.java`、`integration/megacells/backend/MegaBulkMarkingService.java`、`multiblock/cluster/NECraftingNetworkCluster.java` | 逐个调用点判断输入能否为负：语义等价才替换为 `NEMath`；不等价的保留并在报告中说明 | 每处替换附"输入非负"的依据 |
| T0-2b | 溢出安全运算收口（合成侧）+ **递归 Tarjan 改迭代** ✅ | `crafting/planner/solve/ActiveRouteSelector.java`、`crafting/planner/result/ECOExecutionPlanBuilder.java`、`crafting/planner/result/ECOExecutionSchedule.java`、`crafting/execution/ECODispatchStallDiagnostics.java`、`crafting/execution/fastpath/ECOBatchCraftingHelper.java` | 只调用 T0-2a 新增的 `NEMath` API（T0-2a 先合入其签名）；`ECOExecutionSchedule.stronglyConnectedComponents/tarjan` 改成迭代实现，输出顺序保持不变 | 新增长链/大环测试（≥100k 节点）不栈溢出；现有规划测试通过 |
| T0-3 ✅ | MegaCells：`applyClient()` 再调一次 `registerExternalCells()`，单人游戏下 `externalCells` 重复 | `integration/megacells/MegaCellsIntegration.java` | 让 `registerExternalCells` 幂等（先清空或加守卫） | `allCells()` 无重复 |
| T0-4 ✅ | `ecoCpuPushTickLimit` 规格默认 200_000、静态初值 393216、注释称"最多 64 次"，三者矛盾；另缺配置翻译 key | `config/NEConfig.java`、`data/lang/ConfigLangs.java`、`src/main/resources/assets/neoecoae/lang/zh_cn.json`（及 zh_hk/zh_tw/lzh 若有对应条目） | 读 `ECOCraftingDispatchStrategy.beginTick` 确认真实行为，让注释、文案、静态初值与规格默认一致；所有 `static` 初值改成与 `define*` 默认值相同；补 `megaBulkAutoMarkThreshold`、`ecoPlanningMax{Millis,Work,MemoryMiB}`、`storage` 分类的翻译 key | runData 后 en_us 只新增这些 key |
| T0-5 | CPU 可用主机判定不一致（一处不检查 `isActive`）+ 规划结果解析器被绕过 | `mixins/ae2/menu/CraftConfirmMenuMixin.java`、`mixins/ae2/crafting/CraftingServiceMixin.java`、新建非 mixin 辅助类（如 `crafting/planner/ECOComputationHosts.java`） | 把"路由原因阶梯 + 主机可用性谓词"抽到普通类，两个 mixin 共用；`:838-842` 改走 `neoecoae$resolveOwnedPlanningResult`。**若读代码后发现差异是有意为之，不改，写明理由** | 报告中给出每处差异的结论 |
| T0-6 ✅ | `ECOComputationDriveRenderer` 双重注册且 Registrate 那份 `render()` 为空；`ECOComputationCoolingControllerRenderer` 未注册（注册曾在 `d3e02619` 被注释、`eddeeb6e` 被清除） | `all/NEBlockEntities.java:388`、`client/renderer/blockentity/ECOComputationCoolingControllerRenderer.java`、`client/all/NEExtraModels.java`、`models/block/compute/computation_cooling_controller_glass.json` | 删掉 `.renderer(...)`；冷却控制器渲染器按**待决 D1** 处理（默认：删除类、`NEExtraModels` 条目，以及只被它引用的模型） | 客户端 setup 无重复注册 |
| T0-7 | `eco.lss` 引用不存在的 `ui-eco:BUTTON_HOVER`、`BAR_CONTAINER`、`BAR` | `src/main/resources/assets/neoecoae/lss/eco.lss`、`gui/theme/NETextures.java` | 查 git 历史：选择器对应的控件已不存在就删规则；仍在用就恢复纹理字段 | 报告中给出依据 |
| T0-8 ✅ | 解锁条件错用 `ALUMINUM_ALLOY_INGOT`；4 处 criterion 名拼写错误 | `integration/appmek/NEAppMekItems.java:42`；`all/NEItems.java:239,617,721,906` | 改成 `BLACK_TUNGSTEN_ALLOY_INGOT`；修正拼写 | runData 后只有对应进度 JSON 变化 |
| T0-9 | 大型工作站排出流体处理器每次调用都新建；基类 `fluidCombined` 死字段；AppBotania 的 `Upgrades.add` 未包 `enqueueWork` | `blocks/entity/ECOIntegratedWorkingStationBlockEntity.java`、`blocks/entity/ECOLargeIntegratedWorkingStationBlockEntity.java`、`integration/appbot/AppBotaniaIntegration.java` | 处理器缓存为字段；删除基类死字段（保留子类所需的方法签名）；改用 `event.enqueueWork` | 编译通过，能力查询行为不变 |

### 第 A 波只读调查（不改代码，产出结论供后续批次使用）

| ID | 内容 |
|---|---|
| I0-1 | ExtendedAE 的 `TileExIOPort` / `ContainerExIOPort` 是自己声明还是继承了 `tickingRequest` / `setupConfig`；`ExtendedAe*IOPort*Mixin` 是否从未匹配、是否会重复包装（查 gradle 缓存里的 jar） |
| I0-2 | 存储接口传输不排除"迁移中"的盘，而驱动器挂载排除（`ECOStorageInterfaceTransfer:84-89` vs `ECODriveBlockEntity:223-228`）：是否有意 |
| I0-3 | 批量盘已用字节向下取整，父类和 AE2 向上取整（`ECOMegaLongBulkStorageCell:176-180`）：是否有意 |
| I0-4 | 合成流体仓缺少"跳过集群成员"一步（大型工作站仓有）：是否遗漏 |
| I0-5 | `NEBlocks:1508,1572`、`NEMultiBlocks:145-147` 引用其他类的 `FORMED`/`FACING` 属性：是否为同一实例 |
| I0-6 | `updateMultiblockAfterNeighborUpdate` 在 AE2 19.2.18 中的名称确认 |

**第 A 波结束**：总策划审 diff → 编译 → 测试 → runData。

---

## 5. 第 1 批：删除死代码（第 B 波，并行）

删除前每个任务必须对类名、方法名做全仓 grep（含 `src/test`、`src/generated`、`src/main/resources` 下的 JSON/LSS/mixins.json），并检查是否存在字符串拼接、反射、`@LDLRegister` 等间接引用。**公共 API 包（`api/`）中的扩展点一律保留**：`ECOPlannerRequester`、`ECOBatchCapacityProvider`、`NEBlockEntity.registerCalculatorFactory`、零实现的 SPI 接口。

| ID | 删除内容 | 状态 |
|---|---|---|
| T1-1 | 规划器：`crafting/planner/result/{OwnershipEvent, ReferenceOwnershipLedger, PrimitiveOwnershipState, OwnershipReplayHarness, CycleResourceLedger}`、`crafting/execution/fastpath/ECOExtractedCraft`、`crafting/planner/cycle/UnsupportedCycleSolver`、`crafting/planner/solve/CandidateAttemptStatus`。（`SinglePatternGrowthCycleSolver` 有测试使用，保留并在类注释中标为仅测试使用） | ✅ |
| T1-2 | GUI 整类：`gui/task/HostTaskListElement`、`gui/common/HostNetworkStatusElement`、`gui/storage/StorageHostAnimatedRatio`、`gui/widget/ECOHostWidgets`、`gui/widget/ECOHostChannelScrollerView`（先确认没有 JSON 用 `eco-host-channel-scroller-view`） | ✅ |
| T1-3 | GUI 成员：`HostText` 的 `byteProgress`、`compactStorageBytes`、`preciseHugeAmount`、`hugeStackAmount`、`exactAmountTooltip`、`fullTypeProgress`、`fullByteProgressValues`、`gaugeColor`、`COMPACT_DECIMAL`、`PRECISE_HUGE_DECIMAL`；`HostPanelElements.formatPerformanceValue`；`NETextures` 未引用字段（逐个核对 `ui-eco:NAME` 在 JSON/LSS 中无引用）；`ExactAmountFormatter.full`；`ECODriveRenderer.RNG` | ✅（HostText 部分） |
| T1-4 | mixin：`mixins/client/accessor/MenuScreensAccessor` 及其 `neoecoae.mixins.json` 条目；`CraftConfirmMenuMixin` 的 `neoecoae$amountFor`、`neoecoae$hasAmountFor`；`ECOThunderboltProviderMixin`（确认插件对未知名称默认返回 false，测试只传字符串，删除后测试仍通过）；`AppEngInternalInventoryMixin` 中不可达的分支 | ✅ |
| T1-5 | 兼容层：`compat/dataenergistics/ECODataEnergisticsCountedBridge`、`compat/extendedaeplus/ECOExtendedAEPlusScaling`。（`ECOAe2LtBatchCapability` 有测试使用，保留） | ✅ |
| T1-6 | 数据生成/工具：`registration/provider/NECellModelProvider`、`data/model/CellModelGenerator`、`NEProviderTypes.CELL_MODEL`（该类若因此变空则删整个文件）；`ItemModelUtil.compatCellModel`、`compatHousingModel`；`PatternSearchKeywords.Directions.append` | ✅ |
| T1-7 | 方块/多方块：`blocks/ECOLargeIntegratedWorkingStation.java`；`NEStorageCluster.casings`（只写不读） | ✅ |
| T1-8 | 存储迁移 API：`IECOStorageMigrationCell.getUsedBytesForMigration` 及 3 处实现；3 参数的 `simulateInsertForMigration` 及其覆写；`ECOStorageSystemBlockEntity.getInfiniteMigrationProgressPercent` 和 `ECOInfiniteStorageModeController.migrationProgressPercent`；`ECOMegaLongBulkStorageCell.getStoredEntries`、`isCompressionEnabled` | |
| T1-9 | 资源：`models/block/cell/` 下约 33 个未引用 JSON（必须先读 `ECOCellModels`、`ItemModelUtil`、各 Integration 的模型名拼接逻辑，逐个确认）；死语言 key `tooltip.neoecoae.infinite_component.unlock`（`NELangGenerator` 以及各手写语言 JSON）。依赖 T0-4（同改 `zh_cn.json`），放在 T0-4 之后 | |
| T1-10 | 空目录：`mixins/{advanced_ae, extendedae, useless, sophisticated}`、`mixins/compat/{ae2lt, ftbquests, utilitarian, ae2omnicells/crafting}`、`client/craftinggraph`、`integration/jei/client`（只删确实为空的） | ✅（部分） |

**第 B 波结束**：编译 → 测试 → runData → 统计行数 → **向用户汇报第 0、1 批结果**。

---

## 6. 第 2 批：横切能力收口

| ID | 内容 | 目标 |
|---|---|---|
| T2-1 | 数字格式化统一门面 | `crafting/display/format` 下一个 `DisplayFormat`：long 走 AE2 `AmountFormat`/`ReadableNumberConverter`，二进制字节只用 `NEByteFormatter`，统一 1000 进位；删除 `HostText` 内重复的格式化方法、`CraftingControllerArtwork.compactNumber`、`.replace("K","k")` hack、`ECOCraftReportHeaderRenderer` 的混合进位 |
| T2-2 | 千位分组与百分比 | 统一用 `DisplayNumbers.grouped`、`HostText.percent` |
| T2-3 | BigInteger 网络编解码 | 统一用 `ExactMapSync.writeInteger/readInteger`（`ECOCycleItemList`、`ECOExactPending`、`ExactKeyAmount`；`MaterialNode` 的字符串编码单独评估） |
| T2-4 | AEKey 与 `Map<AEKey,BigInteger>` 的 NBT | 新增 `AmountMapNbt` 辅助类；`ECOBigOrderController`、`ExecutingCraftingJob`、`ECOExactInventory` 共用；`ECOMegaLongBulkStorageCell.writeKey/readKey`、`StorageInterfaceFilter.encode/decode` 改用 `AEKey.toTagGeneric/fromTagGeneric`（需兼容旧存档格式，读取时回退旧格式） |
| T2-5 | GenericStack 列表的有界读取 | `GenericStackNbt` 辅助类（`ECOFastPathStacks`、`ECOLargeIntegratedWorkingStationBlockEntity.readCounter`、`ECOCraftingOutputDelivery`、`ECOExecutionPlanNbtCodec`） |
| T2-6 | 图算法 | `crafting/planner/graph/TopoSort.kahn`，替换 4 处；SCC 统一用迭代实现；`ClientCraftingGraph` 只构建一次 `GraphAdjacency`，传给各布局 |
| T2-7 | BigInteger 校验 | `BigIntegers.nonNegative`、`checked`；`NetworkStorageMixin` 改调 `ECOBigIntegerStorage.insert` |
| T2-8 | 方块实体持久化与同步 | `NEBlockEntity.persistAndSync()`、`isServerStopping()`；替换 37 处 `setChanged(); markForUpdate();`；3 处裸 `executeIfPossible` 改走 `ServerTaskUtil` |
| T2-9 | 小工具 | `BlockStateUtil.yRotation(facing)`（22 处）；作用域栈 `ThreadScope<T>`（4 处）；压缩链判等 `sameChain`（4 处）；"清空存储盘"作为默认方法（4 处）；`CustomData` 读改写辅助方法 |
| T2-10 | GUI 公共件 | `gui/json` 暴露 `string()`（7 份）；`FittedText.draw`（约 7 处）；`Palette` 统一调色板；`HostElements.tooltips` 统一 tooltip 构建；`ECOModularUI` 增加不需要 player 的重载 |
| T2-11 | 输出累加 | `ExecutionOutputs.accumulate`（"模式产物 × 次数"共 4 处）；`ECOCraftingThread` 的栈转换移入 `ECOCraftingStackCodec` |
| T2-12 | 模组 ID 与加载检查 | `ModIds` 常量 + `ModGate.isLoaded`；去掉 `ECOOmniSequenceDirectDispatch` 等处的重复检查 |

---

## 7. 第 3 批：合并平行类

| ID | 内容 |
|---|---|
| T3-1 | 合成/计算系统控制器：抽出 `NetworkSwitchHostSupport`（网络开关、频率、节点回调、忽略替代） |
| T3-2 | 集群计算器：把"镜像试两次"和约 8 个辅助方法上提到 `NEClusterCalculator`；控制器查找每次只算一次 |
| T3-3 | `NELogicalNetworkManager` 按集群类型泛型化；集群类抽出 `NENetworkedCluster`（网络倍率） |
| T3-4 | `MultiBlockBuildController.Host` 给简单访问器提供默认方法，构建状态移进控制器；大型工作站的 `autoBuild` 改走控制器 |
| T3-5 | 流体仓 4 合 1（方块实体 + 方块参数化）；散热口、并行核心各合并为一个通用类；`setIdlePowerUsage` 改由构造参数传入 |
| T3-6 | 集成工作站与大型工作站：组合式 `MultiblockMemberSupport` + `PowerDrawHelper`，删除遮蔽常量和重复的电力设置 |
| T3-7 | `NEBlockEntity` 增加 `onUnloadOrRemove()` 钩子，统一 `setRemoved`/`onChunkUnloaded` 两个入口 |
| T3-8 | AE2/AdvancedAE 成对 mixin：方法体委托给同一个静态辅助类；按任务定向插入产物收口到 `ECOExternalCpuOutputRouter`；IO 端口 mixin 按 I0-1 的结论删除或合并 |
| T3-9 | JEI/EMI：共用布局常量和配方收集；EMI 的多方块列表改为从 `NEMultiBlocks.DEFINITIONS` 推导；纹理路径常量统一 |
| T3-10 | 集成存储盘族改为数据驱动的 `IntegrationCellFamily`；升级卡注册循环（10 份）、`ECOCellModels.registerTiers`、Mega 盘 4 个子类合并 |
| T3-11 | `NEBlocks`/`NEItems`/`NEBlockEntities`/`NEMultiBlocks` 构建链：工具套装、存储盘、控制器族、分级核心、存储方块与外壳、能量盘、仓室、晶芽等参数化辅助方法 |
| T3-12 | GUI 平行件：CPU 选择 `ServerState`、按钮/返回键、工具栏图标按钮、`BindableElement<T>`、`GridSlot`、滚动条适配到 AE2 `Scrollbar`、侧栏分段 |
| T3-13 | 存储盘 tooltip、拆解、`bytesUsedLine`、无限成员提示抽成静态辅助方法（物品类和 Jade 共用，每次 tooltip 只构建一次存储盘） |

---

## 8. 第 4 批：架构统一（每项需先出设计说明，经用户确认后再动手）

| ID | 内容 |
|---|---|
| T4-1 | 精确数量类型以 `PlannerAmount` 为准（并入"无限"标记），逐步替换其余 5 种 |
| T4-2 | 图模型：`CraftingGraphSnapshot` 作为网络 DTO，`CraftingDependencyGraph` 作为规划图，客户端和 trace 从这两者派生 |
| T4-3 | 执行模式枚举、求解状态枚举各合一；规划结果统一由 `ECOExecutionContract` 承载 |
| T4-4 | 模式身份统一；工作 DTO 合并为 `CraftWork`；推送守卫只保留一份，删除废弃的 `pushBatch`；轮询游标复用 `ECOProviderCursor` |
| T4-5 | 并行提供者去掉多余包装层；批量规划器改为无状态单例 |
| T4-6 | 两个 C2S 包合并（模式枚举 + 共享校验）；新增 `ECOPayload` 基础设施；增量同步通道 `DeltaSyncChannel` |
| T4-7 | `CraftConfirmMenuMixin` 的循环物品投影、`CraftingServiceMixin` 的集群遍历移出 mixin；规划会话复用 `ECOCancellation` |
| T4-8 | 存储：`StorageInterfaceFilter` → `ConfigInventory` + `IPartitionList`（兼容旧 NBT）；字节计算抽进 `StorageByteAccounting`；批量盘不再承担父类的初始化开销；IO 端口传输骨架复用；存储 BE 的透传方法收敛 |
| T4-9 | `integration/`、`compat/` 边界重划（`compat/ae2` 移到 `crafting/adapter/ae2` 一类位置）；`mods.toml` 补全可选依赖 |
| T4-10 | 语言文案：英文只在生成器中定义；复用 `common.*`；配置注释与 `ConfigLangs` 只保留一个来源；拼写错误的 key 迁移（保留旧 key 作为别名，或在 CHANGELOG 中说明） |
| T4-11 | AE2 fork 类（`ElapsedTimeTracker`、`ExecutingCraftingJob`）对照 19.2.18 做 diff，评估能否改为"AE2 原类 + mixin" |
| T4-12 | `util.ICellHost` 别名并入 API 版本（连同修改 `StorageIntegrationApiTest`） |

---

## 9. 待决事项

| ID | 问题 | 默认方案 |
|---|---|---|
| D1 | `ECOComputationCoolingControllerRenderer`（冷却控制器玻璃渲染）：注册还是删除？ | 删除（历史上已被有意注释掉） |
| D2 | 溢出安全加法遇到负输入的统一语义 | 采用 `NEMath` 的"先截为 0"；语义不同的调用点保留私有实现并加注释 |
| D3 | 拼写错误的语言 key（`storage_dirve`、`max_lenth`、`patten`、`unknow_cell_type`）改名会让第三方资源包失效 | 放到第 4 批，改名同时保留兼容 |
| D4 | 第 4 批各项是否执行 | 逐项出设计说明后由用户确认 |
| D5 | 1.7.10 的 Java 工具链：Jabel（只降级语法，`core` 不能用 record 和 Java 9+ 标准库）还是 jvmDowngrader（语法和标准库一起降级） | **jvmDowngrader**：`core` 可以按 Java 21 写，不用为 1.7.10 降低写法。需先在 GTNH 构建脚本中验证可行（**第 2 批开工前必须定**） |
| D6 | 多版本工程组织：① 每个版本一个分支，共享 `:core` 制品；② 单仓多项目（`:core` + `:mc1710` / `:mc1201` / `:mc1211` / `:mc2612`）；③ Stonecutter 一类预处理器 | **②**：`core` 按二进制共享；AEKey 代的三个版本之间，L1 用共享源码目录加少量按版本区分的适配类。1.7.10 工具链差异太大，可以单独作为一个 Gradle 根工程，只引用 `core` |
| D7 | 1.20.1 的 UI 方案：LDLib 1.x 还是 AE2 `AEBaseScreen` 加自写的 JSON 布局渲染器 | 先评估 LDLib 1.x 与当前 JSON 布局的契合度，再决定 |
| D8 | 1.7.10 的功能范围：GTNH AE2 没有部分模组集成（如 MEGA、AppFlux），1.7.10 是否只移植核心机器、规划器和存储 | 只移植核心，集成按 GTNH 整合包里实际存在的模组另行规划 |

---

## 10. 已排除的误报

- `client/gui/graph/CompactTreeRenderer`：被 `GraphRenderer.java:46` 用于紧凑树布局，**不是死代码**。
- `api/me` 下零实现的 SPI（`ECOCraftingDispatchPolicy`、`ECOCraftingJobAttachment`、`ECOCraftingLifecycleListener`）以及二进制兼容垫片（`api/me/ECOCraftingCPU` 等）：是有意保留的扩展点。
- `NEBlockEntity.registerCalculatorFactory`：对外扩展点。

---

## 11. 进度记录

| 批次 | 状态 | 行数变化 | 备注 |
|---|---|---|---|
| 基线 | ✅ | 103,965（`4908324b`） | 测试通过 |
| 第 0 批 | ✅ 完成（已分批提交） | 见下 | T0-1～T0-11 全部合入；编译通过，测试 1102 个通过、4 个跳过 |
| 第 1 批 | ✅ 完成（已分批提交） | `src/main/java` 103,965 → 102,137（−1,828）；全仓 +493 / −3,994 | 另删 33 个模型 JSON、14 个空目录 |
| 第 2 批 | 未开始 | | |
| 第 3 批 | 未开始 | | |
| 第 4 批 | 未开始 | | |

### 11.1 第 0、1 批执行记录（2026-10-13）

**第 0 批合入**：
- T0-1：嵌套存储盘检查先查 ECO 注册表
- T0-2a/b：溢出安全运算收口到 `NEMath`（新增 `saturatingAddInt`、`clampToLong`）；递归 Tarjan 改为迭代
- T0-3：MegaCells 外部盘列表幂等（所有客户端都受影响，不止单人）
- T0-4：推送上限文案改为如实描述 `min(64, 配置值)`；16 个默认值常量化；补齐配置翻译 key
- T0-5：可用主机判定与路由原因抽到 `ECOComputationHostRouting`；提交路径改走带所有权检查的解析器
- T0-6：删除空的 BER 注册与冷却控制器渲染器
- T0-7：删除 `BAR`/`BAR_CONTAINER` 的死规则
- T0-8：修正解锁条件与拼写
- T0-9：工作站流体处理器只创建一次；AppBotania 改用 `enqueueWork`
- T0-10a：接口传输与挂载共用同一个资格谓词，迁移中的盘被排除
- T0-10b：批量盘已用字节改为向上取整
- T0-11：`ElapsedTimeTracker` 精确账本读档容错

**调查结论**：
- I0-1：ExtendedAE 的 IO 端口两个 mixin 都必需，没有重复包装（调整 T3-8：只抽公共方法体，不删除）
- I0-2：bug，已修（T0-10a）
- I0-3：已修（T0-10b）
- I0-4：潜在不一致，并入 T3-5（合并后的流体仓统一跳过集群成员）
- I0-5：同一实例，无害
- I0-6：方法名正确
- 两个 fork 类（`ElapsedTimeTracker`、`ExecutingCraftingJob`）的差异清单交给 T4-11

**datagen**：`runData` 结果依赖开发环境中装了哪些可选模组，不能整批采用。本次只合并了配置 key 和 6 个进度文件，其余输出全部丢弃。

**审核中纠正的审计误报**（以下条目未删除）：
- `PatternSearchKeywords.Directions.append`：有调用
- `ExactAmountFormatter.full`、3 参数的 `simulateInsertForMigration`、`getStoredEntries`：测试在用
- `AppEngInternalInventoryMixin` 的守卫分支：测试子类会走到

**留给后续批次**：
- `ECOThunderboltBatchBridge.capacity()` 现在只有测试调用 → 第 2 批
- `eco_mega_*_cell_{16m,64m}` 物品模型疑似死资源 → 第 2 批核实
- `ECOMegaLongBulkStorageCell` 本地复制了一份 `ceilDivide` → T4-8 字节计算收口时去掉
- `ECOOmniSequenceDirectDispatch` 等处重复的模组加载检查 → T2-12

**新增待决**：
- **D9（已解决）**：`eco.lss` 中两处按钮悬停引用已改为 `BUTTON_HIGHLIGHTED`，并核对字段和 PNG 均存在。
- **D10** 推送上限硬编码为 64，配置值超过 64 不起作用：是否保留现状

**提交前复核（2026-10-13）**：实际重跑全部 1106 项测试，1102 项通过、4 项跳过，零失败；`check`、`jar`、独立输出目录的 `runData` 均通过，另启用 AppBotania 集成完成编译。核对本次修改的语言 key 和 6 个进度文件与新生成结果一致，删除的 22 个 Java 类无生产引用，33 个单元模型及冷却控制器模型无残留引用，产物包含按钮悬停修正。未进行客户端或真实存档验证。

本次按存储、算法、合成路由、配置配方、工作站、旧代码清理、界面资源和审计文档拆为 8 次本地提交。检查期间开始写入的第 2 批内容另行保留，不纳入这 8 次提交。

### 11.2 用户决定（2026-10-13）

- **D5**：1.7.10 采用 jvmDowngrader。`core` 可以使用 Java 21 的语法和标准库。依赖采用白名单：只允许 `java.*`、`it.unimi.dsi.fastutil.*`、`org.jetbrains.annotations.*` 和 `cn.dancingsnow.neoecoae.core.*`。
- **D9**：`eco.lss` 里的 `BUTTON_HOVER` 改为指向 `BUTTON_HIGHLIGHTED`。
- **D10**：有了智能翻倍，`ecoCpuPushTickLimit` 已经没有作用。删除这个配置项和它的语言 key，保留固定上限 64 的现有行为。

### 11.3 第 2 批执行波次

| 波次 | 内容 |
|---|---|
| B0（单独） | T2-0：`core` 包骨架、依赖白名单测试、`core.math.BigIntegers`、`core.graph.TopoSort` |
| C（并行） | D9/D10；T2-1/2 格式化；T2-3/7 BigInteger 编解码与校验；T2-4/5 NBT 辅助类（`StorageInterfaceFilter` 和批量盘的键编解码挪到 T4-8，因为要兼容旧存档）；T2-6 规划器拓扑排序/SCC、客户端图邻接表；T2-8 方块实体持久化与同步；T2-9 yRotation、ThreadScope、存储辅助；T2-10 JSON 属性辅助、`ECOModularUI` 无玩家重载；死资源核实 |
| D（并行） | 数字显示与 CPU 字节格式对齐 AE2；T2-11 输出累加；T2-12 ModIds；工作站 `readCounter` 接入 NBT 辅助类 |
| E（并行） | FittedText/Palette/tooltip 公共件及各界面、客户端渲染器、JEI/EMI 调用接入 |
| F（单独，待执行） | 把纯逻辑类（`NEMath`、格式化的数值部分、图算法）迁入 `core`，同时更新 import |

NBT 辅助类属于 L2 平台层，不放进 `core`；`DataWriter`/`DataReader` 抽象仍放在 T4-16。

### 11.4 第 2 批第 B0/C 波执行记录

B0 新增 `core` 包，内容：`core.graph.TopoSort`、`core.math.BigIntegers`，以及依赖白名单测试 `CoreDependencyBoundaryTest`。

第 C 波合入情况（编译通过，测试 1145 个通过）：

- **C1（D10）**：删除 `ecoCpuPushTickLimit`。`beginTick()` 改为无参，返回常量 64。
- **C2a 格式化**：
  - `BigNumberFormatter` 成为唯一的十进制（1000 进位）简写实现，新增 `formatSI(..., lowercaseKilo)`、`compactFixed`、`fit`。
  - 删除 `ExtendedDecimalUnits`，以及 `HostText` 中 1024 进位的简写路径和若干死方法。
  - 报表头的字节显示统一为 1000 进位，与 AE2 一致。
- **C3 BigInteger**：
  - `ExactMapSync` 新增有符号读写，原来的严格版改为"有符号版 + 负数检查"；编解码只剩一份实现，线上字节格式不变。
  - 插入请求和结果的校验统一放到 `ECOBigIntegerStorage`。
- **C4a/C4b**：新增 `platform.nbt.AmountMapNbt`、`GenericStackNbt`，存档格式按字节保持一致。
  - `ECOExactInventory`、`ECOExecutionPlanNbtCodec` 以及 `OutputDelivery` 的读取路径没有改：它们的布局或失败策略和辅助类不同。
- **C5a/C5b**：
  - 规划器里 3 处 Kahn 排序换成 `TopoSort`（新增带取消检查点的重载）。
  - SCC 统一为 `core.graph.StronglyConnected`，`ECOExecutionSchedule` 和 `TarjanSccAnalyzer` 共用。
  - 客户端新增 `GraphAdjacency`。
- **C6a/C6b**：`NEBlockEntity.persistAndSync()`（public）、`persistAndSyncOnServerThread()`、`ServerTaskUtil.isServerStopping()` 已落地，约 40 处调用替换；裸 `executeIfPossible` 全部加了保护。
- **C7a/b/c**：
  - 新增 `BlockStateUtil.yRotation`（21 处），新增 `core.concurrent.ThreadScope`（2 处使用）。
  - 压缩链判断收口为一个谓词，并补了 CustomData 辅助方法。
  - "清空存储盘"的 4 份实现语义各不相同（封存/截断/创造），**保留**。
- **C8**：新增 `gui/json/JsonAttrs`，替换 7 份重复副本；新增 `ECOModularUI.create(root)`，无需玩家参数。
- **C9**：
  - 删除 MEGA 16m/64m 的死物品模型 18 个、父模型 18 个，以及对应语言条目 18 组。
  - 删除 `ECOThunderboltBatchBridge.capacity()`。

**遗留**：确认界面中 CPU 存储那一行（`NEByteFormatter.formatCpuStorage`）仍是 1024 进位，而报表头已经改为 1000 进位。交给第 D 波按 AE2 的做法统一。

### 11.5 第 2 批第 D/E 波执行记录

**第 D 波**：
- **D1**：Jade、图界面的 `%,d` 改用 `DisplayNumbers.grouped`；CPU 条目改用 `BigNumberFormatter.format`；硬编码中文改为翻译 key `gui.neoecoae.crafting_report.missing_startup_seed_amount`。
  - CPU 存储字节的显示**对齐 AE2**：删除 `CpuSelectionListMixin` 中对存储的两处覆盖，确认界面传原始数值，ECO 自己的 CPU 页改用 AE2 的 `Tooltips.getByteAmount`。
  - 删除 `NEByteFormatter.format`/`formatCpuStorage` 和已过时的 `CpuSelectionListMixinContractTest`。
  - 百分比、表格 `formatAmount` 这几处如果合并，显示会变，**保留**。
- **D3**：新增 `crafting/execution/ExecutionOutputs`（2 处使用）。`ECOCraftingThread` 的栈转换方法迁入 `ECOCraftingStackCodec`。`addAllSaturating` 保留，因为 AE2 的版本会溢出。
- **D4**：新增 `integration/ModIds`（19 个常量，`isLoaded` 带 null 保护），替换 31 个文件；删除 Omni 中死掉的 `supports()`。其余重复检查各有原因，保留。
- **D5**：大型工作站 `readCounter` 改用 `GenericStackNbt.readBounded`，写入改用 `writePositive`，行为表与改前一致。

**第 E 波**：
- **E0**：新增 `gui/common/FittedText`、`gui/theme/Palette`（三种近似墨色暂时分开保存，带 TODO）；`HostElements` 新增 `hoverTooltips(List)` 和 `tooltips(element, List|Supplier)`。
- **E1**（storage）、**E2**（computation）、**E3**（crafting/workstation/multiblock/common 按钮）、**E4**（client 渲染器与 JEI/EMI）：颜色、tooltip、文字适配改用公共件。
  - 读实时状态的 tooltip 一律用 Supplier 重载。
  - 有测试钉住了具体绘制调用序列的地方（`ComputationCpuPanel.text/fittedText`、`CraftingControllerArtwork.drawInfoText`）保留原绘制调用，只复用 `fitScale`。
  - 图渲染中重复的状态颜色映射合并为 `GraphRenderer.materialStatusColor`。
- **审核修正**：C8 删除 `string()` 后，7 个 GUI 文件出现 `        }    }` 两个括号挤在一行的格式残留，已拆回两行（保持 CRLF）。

## 12. 暂停点（2026-10-13）

**状态**：
- 分支 `refactor/architecture-cleanup`。第 0、1 批已分 8 个提交合入；本次从 `8b96e4da` 起新增 12 个中文标题及描述的本地提交，未推送。
- 第 2 批的 B0、C、D、E 波**已按功能分批提交**，源码提交截至 `c5d880b7`；最后一批更新本审计记录。
- 本次复核：编译、`check`、`jar` 均通过；强制重跑全部 1153 项测试，1149 项通过、4 项跳过，零失败；启用 AppBotania 可选集成后的主代码与测试代码编译也通过。

**下次继续前先做**：
1. 执行 `git status` 和 `git log`，确认工作区仍是上面的状态；用户可能已经提交或另做了修改。
2. 跑 `./gradlew compileJava compileTestJava test --offline`，确认基线为绿。
3. 重读第 1 节执行规则、第 3 节多版本分层，以及 11.2 的用户决定（D5 用 jvmDowngrader；`core` 依赖白名单；"以简化为目标、不做为风格而改写"）。

**剩余工作**：
- **第 2 批第 F 波（单独执行）**：把纯逻辑类迁入 `core`，同时更新 import。
  - 候选：`crafting/amount/NEMath`；`crafting/display/format` 中不依赖 MC/AE2 的部分（`BigNumberFormatter`、`DisplayNumbers`，需先确认没有 MC/AE2 依赖）；`crafting/graph` 中的纯类。
  - 迁移后 `CoreDependencyBoundaryTest` 必须为绿。
  - 这一步会改动大量 import，不能和其他任务并行。
- **第 2 批遗留的小项**：
  - `HostText.storageTypeAccentColor` 等未收进 Palette 的颜色；状态颜色是否加 `Palette.STATUS_*`。
  - 三种近似墨色（`0x3F3D52`/`0x403E53`/`0x413F54`）是否统一：需要用户做设计决定。
  - `ECOCraftingGraphScreen.cycleStatusColor` 的绿色和 `materialStatusColor` 不一致，是否统一：需要用户决定。
  - `ECOCellMutationBatch`、`ExactAmountCollector` 不适合用 `ThreadScope`，保留现状。
- **第 3 批**（第 7 节 T3-1～T3-13）：合并平行类。注意：
  - I0-1 已确认 ExtendedAE 的 IO 端口两个 mixin 必需，T3-8 只抽公共方法体。
  - I0-4 的流体仓跳过集群成员，并入 T3-5。
- **第 4 批**（第 8 节 T4-1～T4-19，含多版本 T4-13～T4-19）：每项先出设计说明，用户确认后再动手。T4-11 的 fork 差异清单见 11.1（I0-1/I0-6 调查结论）。

**执行经验**：
- 同时运行的 Haiku 代理超过约 9 个会触发 429 限流，每波控制在 8 个以内。
- datagen 结果依赖开发环境装了哪些可选模组，不能直接采用：先删 `src/generated/resources/.cache`，把输出生成到临时目录，再只合并需要的 key 或文件。
- 测试里有大量 Mockito 验证钉住了具体调用（重载类型、调用次数）。对 GUI 和渲染器的"等价替换"，必须实际跑测试确认。
- 代理会把文件改成 LF，有时还会残留格式问题。审核时要 grep 检查 `}    }` 这类拼接痕迹。
