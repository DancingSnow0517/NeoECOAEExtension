# 架构冗余审计与整改计划

> 基线：`4908324b`，`src/main/java` 103,965 行。当前 HEAD `1ecf242f`，`src/main/java` 101,226 行（净减 2,739 行）。
> 路径默认相对 `src/main/java/cn/dancingsnow/neoecoae/`。
> 最近一次完整验证记录见 10.4（测试 1163 项，1159 通过、4 跳过）。本次整理只改文档，未重跑测试。

---

## 1. 当前状态与下一步

| 批次 | 状态 | 提交 | `src/main/java` 行数 |
|---|---|---|---|
| 基线 | — | `4908324b` | 103,965 |
| 第 0 批 正确性修复 | ✅ | 分批本地提交 | — |
| 第 1 批 删除死代码 | ✅ | 分批本地提交 | 102,137 |
| 第 2 批 横切能力收口 | ✅（`JsonAttrs` 已撤回，见 5.3） | 分批本地提交 | 102,258 |
| 第 3 批 合并平行类 | ✅ | 第 1～2 波 `1173ccc7`～`c905c901`（7 个提交）；第 3 波 5 个提交 | 101,033 |
| 第 4 批第一组（T4-9～T4-12） | ✅ | `7a99dff1`～`c8ccd192`（6 个代码提交），记录于 `1ecf242f` | 101,226 |
| 第 4 批其余（T4-1～T4-8、T4-13～T4-19） | 未开始 | — | — |

**下一步**：
1. 第 4 批其余项逐项写设计说明，经用户确认后再动手（见 6、4.5）。
2. 等待 7 节的待决项。其中 D6 阻塞 T4-19，D7 阻塞 UI 相关工作。
3. 另开小修：`en_ud.json` 等生成文件整体刷新（见 7）。

---

## 2. 执行规则与经验

### 2.1 规则

1. **分工**：总策划（Opus）负责拆分、派发、审查与集中编译测试；执行由 Haiku 子代理完成，每个任务尽量小、文件集互不重叠。
2. **提交**：按主题分批本地提交，标题用中文，不推送，不 `stash`，不切分支。
3. **保护未提交改动**：每一波开工前执行 `git status`。非本计划产生的改动一律视为用户改动，只能在其上增量修改，禁止 `checkout`、`restore`、覆盖或恢复。
4. **子代理不跑 Gradle**（避免并发锁）。每波结束后由总策划统一执行：
   - `./gradlew compileJava compileTestJava test --offline`
   - 启用 AppBot 集成时加 `-PenableAppBotIntegration=true compileJava`
   - 涉及数据生成的波次再跑 datagen（见 2.3）。
5. **子代理不得在仓库内写临时文件**；只读调查任务不得改任何文件。
6. 每个任务完成后提交"改动清单 + 理由 + 未处理项"，由总策划审 diff；不符合要求的退回重做。
7. **AE2 fork 类的语义以 AE2 19.2.18 为准**（源码在 `~/.gradle/caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/19.2.18`）。与原版的偏离视为 bug，需整组修正，不能只修一部分。
8. **以简化为目标**：死分支、只做转发的包装方法、重复副本直接删掉；手写循环明显可以用 `anyMatch`、`sum`、`Map.merge`、`removeIf` 表达的，改成这些写法。**不做**为了风格统一而把循环批量改成 Stream；热路径保持原样。不引入 Kotlin。
9. **多版本约束**：第 2～4 批新增或移动的代码按 4 节分层落位。`core` 包不得引入 `net.minecraft`、`net.neoforged`、`appeng`、`lowdragmc`、`com.mojang`、`com.tterrag` 依赖，由 `CoreDependencyBoundaryTest` 把守（依赖白名单：`java.*`、`fastutil`、`org.jetbrains.annotations`、`core.*`）。
10. **判断标准**（用于审查第 2～4 批的抽象是否过度，见 10.5）：
    - 注册名、语言键、模型路径必须能按字面量搜到；
    - 不为少数调用点新建枚举或函数式接口；
    - 不加大量位置参数（尤其相邻同类型），不用 `null`/boolean 表示含义；
    - 不留只包一层调用的转发方法；
    - 不做新旧写法混用的半途替换；
    - 热路径（每 tick、每次推送、每次插入）不新增分配：不要每次 new lambda、方法引用或 `HashSet`；
    - 真正相同的逻辑（算法、编解码、同步、生命周期）去重保留。

### 2.2 执行与审核经验

- 同时运行的 Haiku 代理超过约 9 个会触发 429，每波控制在 8 个以内。上游 502 或连接重置会让代理中途退出，用 `SendMessage` 恢复，恢复前先看工作区已落地了哪些改动。
- 代理会违反规则直接调用 `javac`、用 `sed -i` 改文件，留下 `javac.<时间戳>.args` 或全是空字节的 `sedXXXXXX`。每波结束都要 `git status` 检查未跟踪文件。
- 代理常把 CRLF 文件改成 LF，或留下 `}    }` 这类两个括号挤在一行的拼接痕迹。审核时用 grep 检查。
- 测试里有大量 Mockito 验证钉住了具体调用（重载类型、调用次数）。GUI 和渲染器的"等价替换"必须实际跑测试确认。
- 匿名子类里，与继承来的 `protected` 字段同名的参数会遮蔽字段（`return cluster` 实际返回了继承字段 null）。
- 抽出公共类后行数可能反而增加。以"单一来源"为准，但要在记录中写明净行数。
- 工作副本是 CRLF，仓库索引是 LF（`core.autocrlf=true`）。提交时会规范化，内容不受影响。

### 2.3 datagen 流程

`runData` 的结果依赖开发环境装了哪些可选模组，**不能直接采用生成结果**。

1. 生成前删除 `src/generated/resources/.cache`。
2. 用 `git archive` 把 HEAD 和当前工作区分别导出到仓库外。
3. 把 `run/mods/*.jar` 拷到导出目录的 `run/mods`（否则会因缺少 curios 而失败）。
4. 分别执行 `./gradlew runData --offline`，比较两份输出，要求零差异。
5. 如果生成器有改动，只按 key 级差量合并到仓库里的生成文件，不整文件覆盖。

---

## 3. 审计结论摘要

| 维度 | 主要问题 |
|---|---|
| 横切能力重复 | 数字格式化约 12 个入口 / 5 套单位表；溢出安全运算 9～11 份私有副本且负数语义不一；BigInteger 网络编解码 4+1 份；`Map<AEKey,BigInteger>` NBT 3～4 套；精确数量类型 ≥6 种；配方图模型 5 套；Kahn 拓扑 ×4、Tarjan ×2 |
| 平行类复制 | 集成/大型工作站；合成/计算系统控制器；三个集群计算器；`NELogicalNetworkManager` 双路径；流体仓 ×4；散热口、并行核心；AE2/AdvancedAE mixin 成对；JEI/EMI 类别；集成存储盘族；`NEBlocks`/`NEItems` 构建链 |
| 重造轮子 | `ECOStorageCell`≈AE2 `BasicCellInventory`；存储盘 tooltip/拆解≈`BasicCellHandler`；`StorageInterfaceFilter` 未用 `ConfigInventory`/`IPartitionList`；自写 AEKey 编解码；`ECOIOPortTransfer`≈`IOPortBlockEntity.transferContents` |
| 过度分层 | 执行模式枚举 ×4；规划结果载体 ×5；模式身份 ×3；工作 DTO ×5；并行提供者 3 层包装；存储 BE 约 20 个透传方法；两个同义 C2S 包 |
| 死代码 | 已核实整类 1,632 行 + 若干死方法/字段 + 33 个模型 JSON |
| 结构 | `integration/` 与 `compat/` 边界失守；模组 ID 无中心注册；`mods.toml` 漏约 16 个可选依赖；配置注释与语言文案漂移 |

**收益核对**：审计预计第 1～3 批约 −6,000～7,500 行。截至 HEAD 实际净减 2,739 行（2.6%），低于预计。主要收益仍是消除"多份实现各自漂移"引起的 bug，行数不是目标。

---

## 4. 多版本移植架构（1.7.10 / 1.20.1 / 1.21.1 / 26.1.2）

本次重构要同时为后续移植铺路：先把"与版本无关的逻辑"和"与版本绑定的胶水代码"分开，以后每移植一个版本，只重写胶水层。

### 4.1 目标版本矩阵

| 项 | 1.7.10 | 1.20.1 | 1.21.1（当前） | 26.1.2 |
|---|---|---|---|---|
| 加载器 | Forge 10.13.4.1614（GTNH 工具链 / RetroFuturaGradle） | Forge 47 | NeoForge 21.1 | NeoForge 26.1.2.x |
| AE2 | **GTNH AE2**（`Applied-Energistics-2-Unofficial`，rv3 系） | AE2 15.x | AE2 19.2.18 | AE2 26.1.x（当前 beta） |
| AE2 物品/数量模型 | `IAEStack<T>`：键与数量**合为一个可变对象**，`IAEStackType` 注册表，`IItemList`；用 `isSameType` 判等 | `AEKey`（不可变）+ long，`KeyCounter`、`GenericStack` | 同 1.20.1 | 同 1.20.1 ✅ |
| AE2 合成 API | `ICraftingPatternDetails`（`getCondensedAEInputs/Outputs`）、`ICraftingMedium`、`ICraftingProvider` | `IPatternDetails`、`ICraftingProvider`、`CraftingService` | 同 1.20.1 | 同 1.20.1 ✅（`CraftingCpuLogic`、`ListCraftingInventory` 都还在） |
| Java | 字节码 Java 8，开发时用 Jabel 或 **jvmDowngrader**（见 7 的 D5） | 17 | 21 | **25**（AE2 26.1 的 class 版本为 69 ✅） |
| 持久化 | `NBTTagCompound` | `CompoundTag`，`ItemStack` 带 NBT | `CompoundTag` + `HolderLookup` + DataComponents | **`ValueInput`/`ValueOutput`**（AE2 26.1 `AEKey.toTag(ValueOutput)` ✅） |
| 网络 | `SimpleNetworkWrapper` + `ByteBuf` | `SimpleChannel` + `FriendlyByteBuf` | `CustomPacketPayload` + `StreamCodec` | 同 1.21.1（AE2 26.1 仍有 `AEKey.STREAM_CODEC` ✅） |
| UI | GTNH **ModularUI2**（GTNH AE2 依赖它）/ `GuiContainer` | 无 LDLib2（需评估 LDLib 1.x 或 AE2 `AEBaseScreen`） | LDLib2 | LDLib2 `26.1` 分支 ✅ |
| 注册 | `GameRegistry` | Registrate（Forge） | Registrate | 待确认 |
| 能力 | 接口直接实现在 `TileEntity` 上 | Forge `LazyOptional` | NeoForge `BlockCapability` | 待确认（NeoForge 新 transfer API） |
| Mixin | UniMixins / GTNH mixin 加载器 | Forge Mixin | NeoForge Mixin | NeoForge Mixin |

**结论**：
- 1.20.1、1.21.1、26.1.2 属于同一代 AE2 API（`AEKey` 系），可以共享大部分 AE2 相关代码，差异集中在持久化、网络、能力、渲染和 UI。
- 1.7.10 是另一代 API（`IAEStack` 系），能共享的只有不依赖 AE2 的纯逻辑。

### 4.2 目标分层

依赖方向只允许从上往下：L3 → L2 → L1 → L0。

| 层 | 内容 | 允许依赖 | 各版本处理方式 |
|---|---|---|---|
| **L0 core** | 规划算法（求解、环、增长、路由）、图与拓扑/SCC、精确数量与溢出安全运算、调度与批量限额、停滞诊断、数字格式化的数值部分、多方块结构定义数据、方块等级/存储盘规格表、配置项表、语言 key 表、UI 视图模型（面板状态、tooltip 内容） | 只依赖 JDK（加 fastutil） | **所有版本共用同一份源码** |
| **L1 ae-bridge** | 键与模式的适配（`KeyOps<K>`、`PatternView<K>`）、规划器编译边界（`planner/compile`）、存储盘适配、CPU 与提供者的推送适配、网格服务查询 | L0 + AE2 | 两套：**AEKey 代**（1.20.1/1.21.1/26.1 共用源码，差异用小适配类隔离）和 **IAEStack 代**（1.7.10） |
| **L2 platform** | 持久化抽象 `DataWriter/DataReader`、网络缓冲抽象 `PacketBuf`、注册、能力、服务端 tick 调度、配置绑定、语言/模型生成器输出 | L0/L1 + MC/加载器 | 每个版本一套，尽量薄 |
| **L3 content/ui/compat** | 方块、方块实体、渲染器、界面控件、mixin、JEI/EMI/Jade、第三方模组集成 | 全部 | 每个版本一套；mixin 只负责挂钩，逻辑委托给 L1/L0 |

### 4.3 当前代码的可移植性

"纯"指不 import MC/NeoForge/AE2/LDLib/Mojang/Registrate。

| 包 | 文件数 | 纯文件 | import AE2 | 说明 |
|---|---|---|---|---|
| `crafting/amount` | 4 | 4 | 0 | 已迁入 `core`（10.2 F 波） |
| `crafting/graph` | 16 | 15 | 1 | 基本可直接作为 L0；其余依赖 `CraftingGraphSnapshot`，留给 T4-2 |
| `crafting/planner` | 115 | 35 | 80 | 主要障碍：`AEKey`（100 处 import）、`IPatternDetails`（65 处）。需要泛型化（T4-13） |
| `crafting/execution` | 87 | 17 | 66 | 调度决策抽到 L0，执行胶水放 L1（T4-14） |
| `api/me` | 54 | 18 | 31 | 对外 API，各版本分别发布 |
| `impl/storage` | 26 | 6 | 17 | 字节计算、无限存储引擎可以泛型化（T4-15） |
| `gui` / `blocks` / `client` / `integration` / `mixins` | 346 | 33 | — | 属于 L3，按版本重写；视图模型抽到 L0 |

### 4.4 关键设计决定

1. **规划器对键类型做泛型**：规划器写成 `<K>` 泛型，配一个 `KeyOps<K>` 策略，而不是给每个 `AEKey` 包一层对象，热路径上零额外分配。1.7.10 的 `IAEStack` 是可变的，进入规划器前必须转成不可变键（由 L1 负责，例如 `IAEStack.copy().setStackSize(1)` 后驻留）。
2. **`planner/compile` 是唯一的 AE→core 翻译边界**：只有编译层读取 `IPatternDetails` / `ICraftingPatternDetails`，下游求解器只认编译后的结构。
3. **持久化走 `DataWriter`/`DataReader`**：规划、任务、精确数量表等领域编解码器只写这个抽象接口，各版本分别实现在 `CompoundTag`、`ValueOutput`、`NBTTagCompound` 上。NBT 辅助类（`AmountMapNbt`、`GenericStackNbt`）属于 L2，不放进 `core`。
4. **网络走 `PacketBuf`**：领域数据的编码（BigInteger、键→数量差量、图快照）写到抽象缓冲上，每个版本只包一层载荷。
5. **mixin 只做挂钩**：`CraftingServiceMixin`（814 行）、`CraftConfirmMenuMixin`（946 行）里的逻辑迁到 L1 服务类，mixin 只剩注入点加一行委托。
6. **内容声明式化**：等级、存储盘规格、工具套装、多方块结构、配置项、语言 key 都改成 L0 数据表；各版本的注册代码、数据生成代码只遍历这些表（1.7.10 输出 `.lang`，新版本输出 JSON）。**注意**：第 4 批 T4-18 之前，不把配置文案做成数据表（见 10.4）。
7. **UI 视图模型与控件分离**：面板状态、文字、格式化、tooltip 行放在 L0；控件构建（LDLib2、ModularUI2、AE2 Screen）各版本分别实现。`screens/*.json` 布局的 schema 尽量保持稳定。
8. **L0 的 Java 语言约束**：按 D5 采用 jvmDowngrader 后，L0 可以使用 Java 21 的语法和标准库。若改用 Jabel，则 L0 不能用 record 和 Java 9+ 标准库。

### 4.5 第 4 批 L 系列任务（T4-13～T4-19）

| ID | 状态 | 内容 |
|---|---|---|
| T4-13 | 未开始 | 规划器泛型化（`<K>` + `KeyOps<K>` + `PatternView<K>`），收紧 `planner/compile` 边界，并把 `planner` 下的纯逻辑迁到 `core` |
| T4-14 | 未开始 | 执行层拆分：调度、限额、停滞诊断、批量规划放 `core`；CPU、提供者推送放 L1 |
| T4-15 | 未开始 | 存储引擎泛型化：无限存储引擎、字节计算、迁移模拟放 `core`；存储盘与 AE2 存储的适配放 L1 |
| T4-16 | 未开始 | 持久化与网络抽象落地：`DataWriter`/`DataReader`/`PacketBuf` 的接口放 `core`，1.21.1 实现放 L2 |
| T4-17 | 未开始 | UI 视图模型抽离：`StoragePanelState`、`CraftingControllerPanelState`、`HostText` 的内容部分等移入 `core`，不能引用 LDLib |
| T4-18 | 未开始 | 语言 key 表与配置项表移入 `core`；1.21.1 的生成器读这些表 |
| T4-19 | 未开始，阻塞于 D6 | 拆成 Gradle 多项目（`:core` 加各版本工程）。拆分前先保证 `core` 包的禁用 import 测试长期为绿 |

---

## 5. 第 0～3 批任务状态

### 5.1 第 0 批：正确性修复

| ID | 内容 | 结果 |
|---|---|---|
| T0-1 | 嵌套存储盘检查识别 ECO 盘：先查 `ECOStorageCells`，未命中再查 AE2 `StorageCells` | ✅ |
| T0-2a | 溢出安全运算收口到 `NEMath`（新增 `saturatingAddInt`、`clampToLong`）；负数语义不等价的调用点保留私有实现 | ✅ |
| T0-2b | 合成侧溢出运算收口；`ECOExecutionSchedule` 的 Tarjan 改为迭代实现 | ✅ |
| T0-3 | MegaCells `registerExternalCells` 幂等（所有客户端都受影响，不止单人） | ✅ |
| T0-4 | 推送上限文案如实描述 `min(64, 配置值)`；16 个默认值常量化；补齐配置翻译 key | ✅（配置项后由 D10 删除） |
| T0-5 | 可用主机判定与路由原因抽到 `ECOComputationHostRouting`；提交路径改走带所有权检查的解析器 | ✅ |
| T0-6 | 删除空的 BER 注册与冷却控制器渲染器 | ✅ |
| T0-7 | 删除 `BAR`/`BAR_CONTAINER` 的死规则 | ✅（D9 处理了 `BUTTON_HOVER`） |
| T0-8 | 解锁条件改为 `BLACK_TUNGSTEN_ALLOY_INGOT`；修正 4 处 criterion 拼写 | ✅ |
| T0-9 | 工作站流体处理器只创建一次；AppBotania 的 `Upgrades.add` 改用 `enqueueWork` | ✅ |
| T0-10a | 接口传输与驱动器挂载共用同一个资格谓词，迁移中的盘被排除 | ✅ |
| T0-10b | 批量盘已用字节改为向上取整（与 AE2 一致） | ✅ |
| T0-11 | `ElapsedTimeTracker` 精确账本读档容错 | ✅ |
| I0-1～I0-6 | 只读调查，结论见 10.1 | ✅ |

### 5.2 第 1 批：删除死代码

T1-1～T1-10 全部完成，共删除 22 个 Java 类、33 个未引用的模型 JSON、14 个空目录。删除清单见提交历史。删除前的 grep 复核过程中发现若干条误报，见 8.2。

### 5.3 第 2 批：横切能力收口

| ID | 内容 | 结果 |
|---|---|---|
| T2-0 | `core` 包骨架、依赖白名单测试 `CoreDependencyBoundaryTest`、`core.math.BigIntegers`、`core.graph.TopoSort` | ✅ |
| T2-1 | 数字格式化统一：`BigNumberFormatter` 为唯一的十进制（1000 进位）简写实现，`ExtendedDecimalUnits` 删除 | ✅ |
| T2-2 | 千位分组与百分比统一用 `DisplayNumbers.grouped`、`HostText.percent` | ✅（D 波） |
| T2-3 | BigInteger 网络编解码统一到 `ExactMapSync`（有符号版 + 负数检查）；线上字节格式不变 | 基本完成：`ExactMapSync` 已被 `ECOBigOrderProgressS2CPacket`、`ECOExactPending`、`ECOCycleItemList`、`ExactKeyAmount`、`ExactCpuSnapshot`、`MenuDataCodecs` 等使用；`MaterialNode` 的字符串编码未评估 |
| T2-4 | `AmountMapNbt` 辅助类，`ECOBigOrderController`、`ExecutingCraftingJob` 等共用 | 部分：`AEKey` 键编解码（`ECOMegaLongBulkStorageCell`、`StorageInterfaceFilter`）移到 T4-8，因为要兼容旧存档 |
| T2-5 | `GenericStackNbt` 有界读取 | 部分：工作站 `readCounter` 已接入；`ECOExecutionPlanNbtCodec`、`ECOCraftingOutputDelivery` 保留自有读取路径 |
| T2-6 | `TopoSort`（`kahnFifo`/`kahnOrdered`）、`StronglyConnected` 统一 SCC、客户端 `GraphAdjacency` | 基本完成：审计列出的 4 处 Kahn 已换；`AcyclicCraftingSolver` 另有一份手写 Kahn（审计未列，待处理） |
| T2-7 | `BigIntegers` 校验统一；插入请求和结果的校验放到 `ECOBigIntegerStorage` | ✅ |
| T2-8 | `NEBlockEntity.persistAndSync()`、`ServerTaskUtil.isServerStopping()`；约 40 处替换；裸 `executeIfPossible` 全部加保护 | ✅ |
| T2-9 | `BlockStateUtil.yRotation`（21 处）、`ThreadScope`、压缩链判等、CustomData 辅助方法 | 部分：yRotation、压缩链、CustomData 已完成；`ThreadScope` 只用于 2 处，`ECOCellMutationBatch`、`ExactAmountCollector` 仍手写 ThreadLocal；"清空存储盘"的 4 份实现语义不同，保留 |
| T2-10 | `FittedText`、`Palette`、`HostElements.tooltips`、`ECOModularUI.create(root)` | 部分：`JsonAttrs` 已撤回（见 10.5），`string()` 辅助现只剩 `NeoUiJsonLoader` 一份，其余 GUI 文件是内联 `getAsString()` 写法（约 17 个文件），需要确认是否接受 |
| T2-11 | `ExecutionOutputs.accumulate`（2 处）；`ECOCraftingStackCodec` | ✅（`addAllSaturating` 保留，AE2 版会溢出） |
| T2-12 | `integration/ModIds`（19 个常量，`isLoaded` 带 null 保护）；删除 Omni 中死掉的 `supports()` | ✅ |

### 5.4 第 3 批：合并平行类

| ID | 内容 | 结果 |
|---|---|---|
| T3-1 | `NENetworkSwitchHost` 接口 + `NetworkSwitchHostSupport` | ✅（`@Persisted` 字段留在方块实体上，见 8.1） |
| T3-2 | `NEClusterCalculator.verifyEitherMirror`；`NENetworkSwitchClusterCalculator` 中间基类 | ✅ |
| T3-3 | `NELogicalNetworkManager` 泛型 `Domain`；`NetworkSwitchMultipliers`；`CONTROLLER_POS_ORDER` | ✅ |
| T3-4 | `MultiBlockBuildController.Host` 缩减为默认方法；三个控制器的 get/set 改用 Lombok | ✅（构建状态与 `autoBuild` 不移，见 8.1） |
| T3-5 | 流体仓 4 合 1、散热口与并行核心参数化；idle 功耗由构造参数传入；旧类 16 个删除 | ✅（行为变化见 9） |
| T3-6 | 集成工作站与大型工作站共用 `isValidScale` 等；删除遮蔽常量和重复电力设置 | ✅（`PowerDrawHelper` 不做，见 8.1） |
| T3-7 | `NEBlockEntity.onUnloadOrRemove()` 钩子 | ✅ |
| T3-8 | AE2/AdvancedAE CPU mixin 共用 `ECOExternalCpuMixinSupport`；`ECOExternalCpuOutputRouter` 被实时扫描和 AdvancedAE 扫描共用；IO 端口 mixin 共用 `ECOStorageCells.orEcoCellInventory` | ✅ |
| T3-9 | `TextureConstants` 移到 `integration/xei`；EMI 多方块列表从 `NEMultiBlocks.DEFINITIONS` 推导 | 部分：纹理常量与多方块列表已统一（行为变化见 9）；JEI/EMI 布局常量的共用未核实 |
| T3-10 | `IntegrationCells`（`Size` 枚举 L4/L6/L9）、`OptionalCellUpgrades.addStandard`；MEGA 4 个子类合并为 `ECOMegaStorageCellItem` | ✅（审计原文的 `ECOCellModels.registerTiers` 不存在） |
| T3-11 | `ToolKind` 收掉 20 个工具；`NEBlockEntities` 新增 `clusterBlockEntity`、`tieredBlockEntity` 等；`NEMultiBlocks.byTier` | ✅（差异项保留，见 10.3） |
| T3-12 | `HostElements.hiddenValue`、`TabBackButton`、`ECOIcon.toolbarBackground`、`CpuSelectionServerState` | ✅（`BindableElement`、`GridSlot` 不存在；滚动条不换，见 8.1） |
| T3-13 | `CellTooltips`、`CellDisassembly`；存储盘只构建一次；MEGA 长批量盘覆盖 `appendCellLines` | ✅（Jade 存储控制器的 UUID 行未改，因为它手里是字符串） |

---

## 6. 第 4 批待办

第 4 批每项都需要先写设计说明，经用户确认后再动手。

| ID | 状态 | 内容 |
|---|---|---|
| T4-1 | 未开始 | 精确数量类型以 `PlannerAmount` 为准（并入"无限"标记），逐步替换其余 5 种 |
| T4-2 | 未开始 | 图模型：`CraftingGraphSnapshot` 作为网络 DTO，`CraftingDependencyGraph` 作为规划图，客户端和 trace 从这两者派生 |
| T4-3 | 未开始 | 执行模式枚举、求解状态枚举各合一；规划结果统一由 `ECOExecutionContract` 承载 |
| T4-4 | 未开始 | 模式身份统一；工作 DTO 合并为 `CraftWork`；推送守卫只保留一份，删除废弃的 `pushBatch`；轮询游标复用 `ECOProviderCursor` |
| T4-5 | 未开始 | 并行提供者去掉多余包装层；批量规划器改为无状态单例 |
| T4-6 | 未开始 | 两个 C2S 包合并（模式枚举 + 共享校验）；新增 `ECOPayload` 基础设施；增量同步通道 `DeltaSyncChannel` |
| T4-7 | 未开始 | `CraftConfirmMenuMixin` 的循环物品投影、`CraftingServiceMixin` 的集群遍历移出 mixin；规划会话复用 `ECOCancellation` |
| T4-8 | 未开始 | 存储：`StorageInterfaceFilter` → `ConfigInventory` + `IPartitionList`（兼容旧 NBT）；字节计算抽进 `StorageByteAccounting`；批量盘不再承担父类的初始化开销；IO 端口传输骨架复用；存储 BE 的透传方法收敛。**另含** T2-4 留下的 `ECOMegaLongBulkStorageCell.writeKey/readKey`、`StorageInterfaceFilter.encode/decode` 改用 `AEKey.toTagGeneric/fromTagGeneric`，读取时回退旧格式 |
| T4-9 | ✅ | `integration/`、`compat/` 边界重划；`mods.toml` 补全可选依赖（见 10.4） |
| T4-10 | ✅ | 语言文案：死 key 清理、拼写改名、配置文案三处统一（见 10.4） |
| T4-11 | ✅ | AE2 fork 类评估：两个都保留；`ExecutingCraftingJob` 的损坏恢复小修已完成（见 10.4） |
| T4-12 | ✅ | `util.ICellHost` 别名删除，改用 API 版本 |
| T4-13～T4-19 | 未开始 | 见 4.5 |

---

## 7. 待决事项

### 7.1 第 0～4 批的决定项

| ID | 问题 | 状态 |
|---|---|---|
| D1 | `ECOComputationCoolingControllerRenderer` 注册还是删除？ | ✅ 删除 |
| D2 | 溢出安全加法遇到负输入的统一语义 | ✅ 采用 `NEMath` 的"先截为 0"；语义不同的调用点保留私有实现并加注释 |
| D3 | 拼写错误的语言 key 改名是否保留别名 | ✅ 直接改名，不保留别名，CHANGELOG 说明（见 10.4） |
| D4 | 第 4 批各项是否执行 | ✅ 逐项出设计说明后由用户确认 |
| D5 | 1.7.10 的 Java 工具链 | ✅ 采用 jvmDowngrader（语法和标准库一起降级）。**GTNH 构建脚本的可行性验证结论未记录，需补** |
| D6 | 多版本工程组织：① 每版本一分支；② 单仓多项目；③ Stonecutter 类预处理器 | ⏳ 默认 ②（`:core` + `:mc1710` / `:mc1201` / `:mc1211` / `:mc2612`），**待确认，阻塞 T4-19** |
| D7 | 1.20.1 的 UI 方案：LDLib 1.x 还是 AE2 `AEBaseScreen` + 自写 JSON 布局渲染器 | ⏳ 默认先评估 LDLib 1.x 与当前 JSON 布局的契合度，再决定 |
| D8 | 1.7.10 的功能范围 | ⏳ 默认只移植核心机器、规划器和存储；集成按 GTNH 整合包实际存在的模组另行规划 |
| D9 | `eco.lss` 中 `BUTTON_HOVER` 等悬停引用 | ✅ 指向 `BUTTON_HIGHLIGHTED`，字段和 PNG 已核对 |
| D10 | 推送上限硬编码为 64 | ✅ 删除 `ecoCpuPushTickLimit` 配置项及语言 key，保留固定上限 64 |

### 7.2 执行过程中产生的待用户决定

1. **JEI 与 EMI 显示差异**（11.7）：冷却配方文字缩放（0.75 与 1.0）、动画时长（20/100 tick 与 2000）、闪电配方文案（`lightning_cost` 单行，还是 `lightning_amount` 加名称两行）、多方块分类图标（COMPUTATION_L4 与 STORAGE_L4）。
2. **AE2LT 的 `finishJob` 失败钩子**（11.7）：只调用 `ECOCraftingJobLifecycle.finish`，不像 AE2 和 AdvancedAE 那样走 `cancelAndRecover`，因此不会唤醒工作线程回收。是否对齐？
3. **计算系统计算器的镜像状态**（11.7）：结构校验失败时不重置冷却控制器的镜像状态（现有行为，已保留）。是否要修？
4. **界面墨色与状态色**（11.5、11.6）：三种近似墨色是否统一？`ECOCraftingGraphScreen.cycleStatusColor` 的绿色是否改成与 `GraphRenderer.materialStatusColor` 一致？
5. **`common.on/off` 的文案**（10.4）：这两个 key 已删（死 key）。大型工作站的 `enabled/disabled` 与镜像的 `on/off` 中文是"开/关"，`common.*` 是"开启/关闭"。若要统一，需要先定统一后的中文文案。
6. **生成文件刷新**（10.4）：`en_ud.json` 比当前生成结果少 259 个 key，另有 25 个值不同；unfinished 下的文件也有旧值。需要在装齐可选模组的环境里整体重新生成，建议另开一项。
7. **`MaterialNode` 字符串编码**（T2-3）：是否纳入 BigInteger 编码统一，尚未评估。
8. **`ECOExternalProviderBlocking`**（10.4）：移到 `crafting/adapter/ae2` 后仍向上依赖 `LargeWorkstationPatternProvider` 与 AdvancedAE 桥接，不是干净的 L1。彻底解耦需要加钩子，本批不做。

---

## 8. 有意保留、不做、误报

### 8.1 有意保留或不做

| 项 | 结论 | 理由 |
|---|---|---|
| "清空存储盘"的 4 份实现（封存/截断/创造） | 保留 | 语义各不相同（T2-9） |
| 百分比、表格 `formatAmount` 的合并 | 保留 | 合并后显示会变 |
| `CraftingControllerArtwork.drawInfoText` 等有测试钉住绘制调用序列的地方 | 保留原绘制调用，只复用 `fitScale` | 测试会失败（E 波） |
| `HostText.storageTypeAccentColor` 的回退色表、图界面状态色 | 不并入 `Palette` | 只在一处使用，并入只是换位置 |
| `crafting/graph` 其余类 | 未迁入 `core` | 依赖 `CraftingGraphSnapshot`（AE2/MC）或 slf4j，留给 T4-2 |
| `ExactAmountFormatter` | 留在 L1 | 依赖 `AEKey` |
| `PowerDrawHelper`（T3-6） | 不做 | 两套耗电算法在平局和容差上的语义不同 |
| 构建状态移入控制器（T3-4） | 不做 | 要持久化并同步给客户端 |
| 大型工作站的 `autoBuild`（T3-4） | 保留 | 行为与其他控制器不同 |
| 两种系统控制器合并（R3） | 不做 | 方块状态读取的是各自类上的属性字段 |
| `@Persisted` 字段留在方块实体上（T3-1） | 保留 | LDLib 的 `FieldManagedStorage` 按宿主字段管理，迁走会改变存档 |
| 滚动条换成 AE2 `Scrollbar`（T3-12） | 不做 | 会改变行为，且有测试钉住 |
| 侧栏两套工具包合并（T3-12） | 不做 | — |
| `BindableElement<T>`、`GridSlot` | 不做 | 这两个类不存在 |
| `ECOComputationCoolingControllerRenderer` 注册 | 删除（D1） | 历史上已被有意注释掉 |
| 配置文案做成数据表（原 13.3 方案 1） | 不做，留到 T4-18 | 属于过度抽象；`zh_cn.json` 是手写整份文件，生成器只输出配置部分会和它在 jar 中同路径冲突。改为手工对齐 + 一致性测试 |
| MEGA 守卫新增两个接口（原 13.2 方案 3） | 取消 | `instanceof` 只加载类、不触发校验；两个 MEGA 类的父类和接口都是本模组的类型。为单一调用点新建接口违反判断标准 |
| `ElapsedTimeTracker` / `ExecutingCraftingJob` 改为 AE2 原类 + mixin | 不做 | `ExecutingCraftingJob` 的构造器和 `CraftingDifferenceListener` 都是包私有，mixin 等于重写构造器；`ElapsedTimeTracker` 的 mixin 会改变 NaN 等原生行为，收益小 |
| `activeCooling` key 改名 | 不改 | 不是拼写错误 |
| 空任务的剩余数量 | 保留 `Integer.MAX_VALUE`（进度为 0） | 有意为之，源码注释有说明；AE2 为 0 |
| 推送上限 | 保留固定 64 | D10 |

### 8.2 误报（审计报告中，经核实不成立）

- `client/gui/graph/CompactTreeRenderer`：被 `GraphRenderer.java:46` 用于紧凑树布局，不是死代码。
- `api/me` 下零实现的 SPI（`ECOCraftingDispatchPolicy`、`ECOCraftingJobAttachment`、`ECOCraftingLifecycleListener`）以及二进制兼容垫片（`api/me/ECOCraftingCPU` 等）：有意保留的扩展点。
- `NEBlockEntity.registerCalculatorFactory`：对外扩展点。
- `PatternSearchKeywords.Directions.append`：有调用。
- `ExactAmountFormatter.full`、3 参数的 `simulateInsertForMigration`、`getStoredEntries`：测试在用。
- `AppEngInternalInventoryMixin` 的守卫分支：测试子类会走到。
- ExtendedAE 的 IO 端口两个 mixin（I0-1）：都必需，没有重复包装。
- `NEBlocks`、`NEMultiBlocks` 引用的 `FORMED`/`FACING` 属性（I0-5）：同一实例，无害。
- `updateMultiblockAfterNeighborUpdate` 方法名（I0-6）：正确。
- `ECOCellModels.registerTiers`：不存在。
- `ECOThunderboltBatchBridge.capacity()`：已在第 2 批删除。
- 精确订单小修 2（EAEP `manualMissing` 与 `waitingFor` 不一致）：不成立。精确订单提交给 CPU 的是 `ECOExactCraftingPlan`，不是 `IForcedCraftingPlan`，`manualMissing(plan)` 对它总是返回空。

---

## 9. 已接受的行为变化

以下变化已经合入，需要在发布说明或 CHANGELOG 中注明。

**用户可见**
- EMI 的多方块列表改为从 `NEMultiBlocks.DEFINITIONS` 推导，与 JEI 一致，EMI 多显示集成工作站（共 10 个）。
- 合成系统的流体仓现在也跳过多方块内相邻成员（原先只有大型工作站输入仓如此）。在有效结构中本来就不会触发。
- 数字显示：报表标题回退由 1024 进制改为 1000 进制（与 AE2 一致）；CPU 存储字节对齐 AE2 的 `Tooltips.getByteAmount`；CPU 协处理器数量在 1e18 以上不再封顶为 "E"；`ComputationCpuEntry` 完整显示超过 2Q 改为科学计数法。
- 语言 key 改名（无别名）：`storage_dirve` → `storage_drive`、`max_lenth` → `max_length`、`unknow_cell_type` → `unknown_cell_type`。英文 `patten` 文案改为 `patterns`。资源包里翻译旧 key 的内容会失效。
- 删除了约 68 个死语言 key，以及孤儿 key（`soulkey.description`、`ecoCraftConfirmDebug` 及其 `.tooltip`）。
- 配置注释改为 zh_cn 与 en_us tooltip 拼接，toml 中的注释文字随之变化（只是注释）。

**存档与网络**
- 批量盘已用字节改为向上取整；迁移中的盘不能再挂载到驱动器，也不能被存储接口传输。
- `ExecutingCraftingJob` 读档改用 `ECOBigCraftingOrder.decode`，比 `new BigInteger` 更严格。
- 精确订单的 `exactTotal/exactRemaining` 解码失败时不再中断整个任务的恢复，而是设 `exactProgressLost`，走 `EXECUTION_METADATA_LOST` 挂起路径。
- `ECOExactPending` 同步拒绝负数与空报文。

**模组加载与资源**
- KubeJS 插件恢复加载：`kubejs.plugins.txt` 在 `9d2f6c61` 中被删除，`NEKubeJSPlugin` 从此没有被 KubeJS 加载过。`ba1ca93f` 重新启用了源码，却漏掉了插件文件和 `build.gradle` 中的 exclude。
- `mods.toml` 新增 16 个可选依赖（`[0,)`、`ordering="AFTER"`；emi 为 `side="CLIENT"`）。`extendedae`、`ae2omnicells` 两个 mixin 配置加上了 `requiredMods`。
- 删除 `util.ICellHost`：外部代码若按这个名字编译，会失败。仓库文档从未公开过这个名字。
- 删除 `compat/` 包：外部代码若引用旧包名，会失败。`ECOFastPathFacade` 的签名不变。
- 删除 MEGA 16m/64m 的死物品模型 18 个，以及对应父模型和语言条目。
- 删除 `ecoCpuPushTickLimit` 配置项。

---

## 10. 执行记录

### 10.1 第 0、1 批（2026-10-13）

- 第 0 批合入：T0-1～T0-11，见 5.1。
- 调查结论：
  - I0-1：ExtendedAE 的两个 IO 端口 mixin 都必需。
  - I0-2：确认是 bug，已由 T0-10a 修复。
  - I0-3：确认是 bug，已由 T0-10b 修复。
  - I0-4：潜在不一致，并入 T3-5。
  - I0-5、I0-6：无害或正确，见 8.2。
  - 两个 fork 类的差异清单交给 T4-11。
- 第 1 批：删除 22 个 Java 类、33 个模型 JSON、14 个空目录。
- datagen：`runData` 结果依赖开发环境中装了哪些可选模组，不能整批采用。本次只合并了配置 key 和 6 个进度文件，其余输出全部丢弃。
- 提交前复核：全部 1106 项测试实际重跑，1102 通过、4 跳过；`check`、`jar`、独立输出目录的 `runData` 均通过，另启用 AppBotania 集成完成编译。删除的 22 个 Java 类无生产引用。未进行客户端或真实存档验证。

### 10.2 第 2 批（B0～F）

- **B0**：新增 `core` 包，包括 `core.graph.TopoSort`、`core.math.BigIntegers`，以及 `CoreDependencyBoundaryTest`。
- **C 波**：
  - C1（D10）：删除 `ecoCpuPushTickLimit`，`beginTick()` 改为无参，返回常量 64。
  - C2a 格式化：`BigNumberFormatter` 成为唯一的十进制简写实现，新增 `formatSI(..., lowercaseKilo)`、`compactFixed`、`fit`；删除 `ExtendedDecimalUnits` 和 `HostText` 中 1024 进位的简写路径。
  - C3 BigInteger：`ExactMapSync` 新增有符号读写，编解码只剩一份实现。
  - C4a/C4b：新增 `platform.nbt.AmountMapNbt`、`GenericStackNbt`，存档格式字节级保持一致。`ECOExactInventory`、`ECOExecutionPlanNbtCodec`、`OutputDelivery` 的读取路径没有改，因为布局或失败策略不同。
  - C5a/C5b：规划器里 3 处 Kahn 排序换成 `TopoSort`（带取消检查点的重载）；SCC 统一为 `core.graph.StronglyConnected`；客户端新增 `GraphAdjacency`。
  - C6a/C6b：`persistAndSync()`、`persistAndSyncOnServerThread()`、`ServerTaskUtil.isServerStopping()` 落地，约 40 处调用替换。
  - C7a/b/c：`BlockStateUtil.yRotation`（21 处）、`ThreadScope`（2 处）；压缩链判断收口为一个谓词；"清空存储盘"保留 4 份实现。
  - C8：`gui/json/JsonAttrs` 替换 7 份副本，之后已撤回（10.5）；`ECOModularUI.create(root)` 无玩家参数。
  - C9：删除 MEGA 16m/64m 的死模型，以及 `ECOThunderboltBatchBridge.capacity()`。
- **D 波**：
  - D1：Jade、图界面的 `%,d` 改用 `DisplayNumbers.grouped`；CPU 条目改用 `BigNumberFormatter.format`；硬编码中文改为翻译 key。CPU 存储字节对齐 AE2：删除 `CpuSelectionListMixin` 中对存储的两处覆盖，删除 `NEByteFormatter.format`/`formatCpuStorage` 和过时的 `CpuSelectionListMixinContractTest`。
  - D3：新增 `ExecutionOutputs`（2 处使用）；`ECOCraftingThread` 的栈转换迁入 `ECOCraftingStackCodec`。
  - D4：`ModIds` 替换 31 个文件；删除 Omni 中死掉的 `supports()`。其余重复检查各有原因，保留。
  - D5：大型工作站 `readCounter` 改用 `GenericStackNbt.readBounded`，写入改用 `writePositive`，行为表与改前一致。
- **E 波**：
  - E0：新增 `gui/common/FittedText`、`gui/theme/Palette`（三种近似墨色暂时分开保存，带 TODO）；`HostElements` 新增 `hoverTooltips(List)` 和 `tooltips(element, List|Supplier)`。
  - E1～E4：storage、computation、crafting/workstation/multiblock/common 按钮、client 渲染器与 JEI/EMI 改用公共件。读实时状态的 tooltip 一律用 Supplier 重载。图渲染中重复的状态颜色映射合并为 `GraphRenderer.materialStatusColor`。
  - 审核修正：C8 删除 `string()` 后，7 个 GUI 文件出现 `}    }` 拼接残留，已拆回两行。
- **F 波**：纯逻辑类迁入 `core`（只改包名、import 和全限定名，类体逐字未动）：
  - `crafting/amount/NEMath` → `core/math/NEMath`
  - `crafting/amount/ExactAmount`、`PlannerAmount` → `core/amount/`
  - `crafting/display/format/BigNumberFormatter`、`DisplayNumbers`、`NEByteFormatter` → `core/format/`
  - `crafting/graph/GraphAdjacency` → `core/graph/GraphAdjacency`
  - `crafting/amount` 包删除。约 140 个文件的 import 随之更新。`NEMathTest` 移到 `core/math`；`GraphAdjacencyTest` 拆分，依赖包私有 `ClientCraftingGraph.synthetic` 的那一条留在 `crafting/graph`。
- 验证：1153 项测试，1149 通过、4 跳过；启用 AppBot 集成后编译通过。

### 10.3 第 3 批（三波）

**第 1 波**：
- T3-1、T3-7、T3-5 前置（`NEBlockEntity` 增加带 `idlePowerUsage` 参数的构造器，原构造器默认 16）。
- T3-6：大型工作站删除与父类重复的常量、构造器调用、`getGridConnectableSides`、重复的 `setPowerSides`。
- T3-8：`ECOExternalCpuMixinSupport` 共用 dispatch、计划校验、退还额度、finishJob 释放和 NBT 读写。`pushPattern` 处于热路径，保持内联；`dispatch` 返回布尔值，由调用方自己 `markDirty`，避免每 tick 分配。新增 `ECOExternalCpuOutputRouterTest`。
- T3-9：`TextureConstants` 从 `integration/jei` 移到 `integration/xei`。审计原说的"纹理常量重复"不成立，只有一份。
- T3-13（第 1 部分）：新增 `items/CellTooltips` 与 `items/CellDisassembly`，两者必须独立成类，因为 `StorageCellDisassemblyTest` 对 `ECOStorageCellItem` 做了 `mockStatic`。
- T3-10（基础）：新增 `integration/IntegrationCells`，AppBot 作为试点。

**第 2 波**：
- T3-2：`verifyEitherMirror` 保持原有副作用顺序（计算系统失败时仍不重置冷却控制器）。这些计算器没有任何测试，已逐段核对与原逻辑等价。
- T3-3：`NECluster.networkSwitchHost()` 钩子；`CraftingCapabilitySnapshot` 改用 `NetworkSwitchMultipliers`；`NELogicalNetworkManager` 泛型化，保留 `attach` 与 `refresh` 的语义差异；新增超过 `HOST_LIMIT` 时的分组测试。
- T3-13（第 2 部分）：Jade 的 `ECODriveProvider` 改用 `CellTooltips`；MEGA 长批量盘未改（后由 R5 完成）。
- T3-10（推广）：AppFlux、AppMek、Ars、Data、Appex、Pneumatics、Soul、AE2LT 都已改用 `IntegrationCells` 和 `OptionalCellUpgrades`；新增 `integration/appmek/MekanismCellFilter`。

**第 3 波（R1～R5）**：
- **R1（T3-5）**：
  - 新增 `blocks/ECOFluidHatchBlock<C>` 与 `blocks/entity/ECOFluidHatchBlockEntity<C>`，差异由 `Spec(input, capacity, coolantOnly, titleKey)` 表达，共四个常量 `CRAFTING_INPUT/OUTPUT`、`LWS_INPUT/OUTPUT`。`tank` 保持 public 且非 final（`ECOCraftingCoolingControllerTest` 会给它赋值）。
  - 大型工作站仓的 `notifyController()` 改为 `NECluster.onFluidHatchChanged()` 钩子（默认空实现）。
  - 新增 `ECOVentBlock(Entity)<C>`（待机功耗 64，由构造参数传入）和 `ECOParallelCoreBlock(Entity)<C>`（`Kind.CRAFTING/COMPUTATION`；待机功耗合成 64、计算 16，两者保持不同）。
  - 集群字段改用通配类型，按 `isInput()` 区分输入、输出仓。
  - 两个计算器的 `matchingParallelCore` 和 `NETooltips` 都加了 `Kind` 判断，防止合成结构接受计算核心。
- **R2（T3-10 收尾）**：`ECOMegaStorageCellItem` 构造参数为 key 类型和可空黑名单谓词。Flux、Mekanism 的类型和 `MekanismCellFilter` 只在 `NEMegaEnergyItems`、`NEMegaChemicalItems` 中引用，缺模组时不会被加载。`ECOMegaLongBulkStorageCellItem` 保留为独立类。
- **R3（T3-11）**：
  - `NEItems` 1,124 → 835 行；`NEBlocks` 1,591 → 1,438 行；`NEBlockEntities` 552 → 488 行；`NEMultiBlocks` 的 3 个 `switch` 合为 `byTier`，`storageSystem` 改名为 `createStorageSystem`。
  - 所有 `public static final` 字段的名称、类型和顺序不变，创造模式物品栏顺序不变。
  - 语言代码（F/C/FT/CT/CM/L/LT/LE）显式写出，不依赖 `replace("L", …)`；`has_*` 进度条件名逐字保持。
  - **不要合并的差异**：批量盘的 `bytesPerType` 为容量值，普通盘为 `1 << (12 + tier)`，批量盘有 `removeTab`；母岩和晶芽的战利品表、配方、音效、光照各不相同；粗钨块要求钻石工具；萤石块的物品标签缺少 `STORAGE_BLOCKS`；计算控制器带 `noOcclusion`，合成控制器没有。
- **R4（T3-12）**：
  - `HostElements.hiddenValue` 替换 22 处同步隐藏值；删除零引用的 `syncedDisplay`、`sectionLabel`、`panelTitle`、`horizontalRow`，以及两个零调用的 `createOpenButton`。
  - `TabBackButton` 替换两处返回键，绘制参数与 `ComputationCpuPanelTest` 钉住的完全一致。
  - `CpuSelectionServerState` 只上提两个 CPU 面板共同的构造器、`refresh()` 与 `beginInteraction()` 守卫。
- **R5**：
  - `isPlayerCloseEnough` 的公共实现移到 `NEBlock`（protected static）。
  - `ECOMegaLongBulkStorageCellItem` 改为覆盖 `appendCellLines`，基类在无限成员分支也以 null 调用这个钩子。
  - `NEClusterCalculator.updateClusterUnchecked` 改为包级可见，集成工作站控制器计算器中的副本已删除。
- **验证**：编译通过，测试 1161 项（1157 通过、4 跳过，新增 1 项）。启用 AppBot 后主源码与测试源码编译通过。datagen 在仓库外分别从 HEAD 和当前工作区生成，各 711 个文件，`src/generated` 零差异。datagen 覆盖不到的运行时属性已逐项对照 HEAD（工具攻击属性、稀有度、堆叠数、方块硬度、音效、光照、`noOcclusion`、`requiresCorrectToolForDrops`、tick 器、渲染器、54 个方块实体注册名）。

### 10.4 第 4 批第一组（T4-9～T4-12，2026-10-14）

**用户对 §13 待决点的答复**（开工前按判断标准复核后确认）：

| 待决点 | 决定 | 理由 |
|---|---|---|
| `ICellHost` 删除还是标废弃 | 直接删除 | 仓库文档从未公开该名字 |
| `compat/` 并入 `integration/<modid>` | 并入 | — |
| KubeJS 插件 | 恢复 | — |
| toml 版本下限 | `[0,)` | — |
| MEGA 守卫新增两个接口 | 取消 | 见 8.1 |
| 配置文案数据表 | 改为手工对齐 + 一致性测试 | 见 8.1 |
| 拼写错误的 key | 直接改名，CHANGELOG 说明，不保留别名 | 资源包翻译的是旧 key，代码改查新 key 后旧翻译不会再显示，别名达不到目的 |
| zh_cn 配置文案以谁为准 | 以 zh_cn 为准 | 玩家实际看到的文案 |
| `jade.neoecoae.activeCooling` | 不改 | 不是拼写错误 |
| T4-11 小修 1、2 | 纳入（小修 2 后来不成立，见下） | — |

**T4-12**：删除 `util/ICellHost`，两个驱动器方块实体与 `CellHostItemHandler` 改为导入 `api.storage.ICellHost`。`StorageIntegrationApiTest` 的断言改为 `drivesImplementThePublicCellHostApi`。

**T4-9（包迁移）**：8 个代理按模组拆分，58 个文件只改包名与导入，另改 33 个外部文件的导入或全限定名。`compat/` 包已删除。

| 原包 | 新包 |
|---|---|
| `compat.ae2` | `crafting.adapter.ae2`（L1，与已有的 `ECOExactCraftingPlan` 同包） |
| `compat.advanced_ae` | `integration.advancedae.dispatch` |
| `compat.ae2lt` | `integration.ae2lt.dispatch`；`ECOAe2LtUnboundedCells` 属于存储适配，放 `integration.ae2lt` |
| `compat.extendedae` | `integration.extendedae` |
| `compat.extendedaeplus` | `integration.extendedaeplus.dispatch` |
| `compat.mekenergistics` | `integration.mekenergistics.dispatch` |
| `compat.omnisequence` | `integration.omnisequence.dispatch` |
| `compat.storage` | `integration`（`ECOOptionalUnboundedCells` 与 `ModIds` 同包） |
| `compat.thunderbolt` | `integration.thunderbolt.dispatch` |
| `compat.useless` | `integration.useless.dispatch` |
| 测试 `compat.advancedae`、`compat.fluxnetworks` | `mixins.compat.advancedae`、`mixins.compat.fluxnetworks` |

mixin JSON 的 `package`/`plugin` 都指向 `mixins.compat.*`，不受影响。

**KubeJS**：恢复 `src/main/resources/kubejs.plugins.txt`，删除 `build.gradle` 中的 `exclude 'kubejs.plugins.txt'`。`processResources` 后插件文件已在产物中。

**`mods.toml`**：新增 16 个可选依赖。已检查本地缓存中这些模组的 15 个 jar，它们的 toml 都没有引用 neoecoae，`AFTER` 不会形成环。

**T4-10（语言文案）**：
- 死 key 共 68 个，由两个只读调查代理按族逐一核对，总策划抽查确认：`gui.neoecoae.big_order.*` 18 个；`gui.neoecoae.storage.status` 及 `status.*` 8 个；`gui.neoecoae.storage.legacy.*` 14 个；`gui.neoecoae.storage.*` 8 个；`gui.neoecoae.host.*` 9 个；`tooltip.neoecoae.pattern.verified_*` 5 个；`tooltip.neoecoae.megacells.{4g,empty_only,storage_type}` 3 个；`gui.neoecoae.common.{on,off,types}` 3 个。这些 key 已从 `GuiLangs`、`NELangGenerator` 和 4 个手写语言文件中删除。
- 拼写改名见 9。
- 配置文案：`NEConfig` 的 20 处 `.comment()` 改为"zh_cn tooltip 各行 + en_us tooltip 各行"。zh_cn 的 `ecoPlanningStageDebug` 原来缺第二句，已补上。新增 `NEConfigCommentLangTest`：遍历 `NEConfig.SPEC`，断言每个注释都等于 zh_cn 与 en_us tooltip 的拼接，比较前截掉 NeoForge 追加的 ` Default:` / ` Range:` 行。
- 方案 3（复用 `common.on/off`）前提不成立：`common.on/off` 本身就是死 key，见 7.2 第 5 条。
- datagen：生成器有改动，按 key 级差量合并到仓库的 5 个生成文件（en_us、en_ud、unfinished 下的 3 个）。每个文件删 68 个、原位改名 4 个，en_us/en_ud 另有 2 处值修改。零差异判定：比较"HEAD 时仓库与 HEAD 生成结果的差异"和"当前仓库与当前生成结果的差异"，两者唯一的不同是被删除 key 上的旧值漂移消失了。

**T4-11（fork 类评估）**：
- 结论：`ExecutingCraftingJob`（419 行，AE2 原版 159 行）和 `ElapsedTimeTracker`（234 行，AE2 原版 134 行）都保留，见 8.1。
- 小修 1（已完成）：`ExecutingCraftingJob` 的 NBT 构造器中，`exactTotal/exactRemaining` 解码失败时不再中断恢复。先写测试 `ECOExactOrderTest.corruptExactTaskAmountSuspendsInsteadOfAbortingRestore`，用修复前的文件实测会抛 `IllegalArgumentException: Invalid exact order amount`，修复后通过。
- 小修 2（不成立）：见 8.2。
- 小修 3（已完成）：删除 plan 构造器中多余的 `exactOrder = true`，以及 NBT 构造器中第二次读取的 `exactOrder`。

**验证**：编译通过，测试 1163 项（1159 通过、4 跳过，新增 2 项）；启用 AppBot 后主源码与测试源码编译通过。datagen 在仓库外分别从 HEAD 和当前工作区生成，711 个输出文件零差异。另外，第 3 批基线（`9943989b`）与 HEAD 的生成结果也零差异。

有多个代理把 CRLF 工作副本改成了 LF，`core.autocrlf=true` 下提交时会规范化，对提交内容没有影响。

### 10.5 过度抽象回调（`571850f6`～`20681dde`）

用户认为原来的物品注册参数化不如原作者的写法好维护。之后按 2.1 第 10 条的判断标准审查了 `7c1ab38d`～`df20f156` 的全部重构提交，处理如下。

- **整体回滚**：`bfd21d3b`（`NEItems` 恢复为逐个注册的链式写法）。
- **局部修正**：
  - 并行核心：注册名与模型路径前缀写成 `ECOParallelCoreBlock.Kind` 的字面量字段，不再由枚举名拼出。
  - 兼容存储单元：`IntegrationCells.cell` 的工厂接收 `(properties, tier)`，等级只在 `Size` 中写一次；`registerModels` 写出三条字面量模型路径，删除 `Size.modelTier`。
  - MEGA 存储盘：`ECOMegaStorageCellItem` 增加无黑名单构造器，调用处不再传位置 `null`；化学与能量盘改用具名静态工厂。
  - `ECOMegaLongBulkStorageCell`：`hasFilterForChain` 改回循环，单一调用的 `writeCustomTag` 内联。
  - `ECOInfiniteStorage.insertBigInteger`：恢复原判断，空值或非正数返回 0，不再抛异常。
  - 删除 `ECOFastPathStacks.writeGenericStacks` 转发层；`AmountMapNbt.read` 只保留严格模式；`pendingPreview` 读取恢复为原内联循环。
  - `copyStacks`/`addStack`/`addGenericStacks` 移回 `ECOCraftingThread` 作私有方法。
  - `TopoSort.kahn(..., null)` 拆为 `kahnFifo` 与 `kahnOrdered`。
  - 界面：删除 `FittedText` 中无调用的方法；`drawStats` 恢复 `%,d`（每帧少 4 次分配）；撤掉 `JsonAttrs`，恢复内联写法；删除 `HostText` 的颜色别名，直接用 `Palette.HOST_*`。
  - 外部 CPU：`dispatch` 改名 `dispatchFailed`（返回 true 表示失败），`validatePlan`、`refundIdleCredit` 内联回 mixin。
  - `toggleGridIgnoringPatternSubstitutions` 内联回两个系统方块实体。
  - `verifyEitherMirror` 只留一个重载，调用处以两个 `BooleanSupplier` 显式写出未镜像与镜像两组参数。
- **保留**：核心层与三次包迁移、`NEBlockEntities` 的注册封装、`NEMultiBlocks.byTier`、`createStorageBlock`、`NENetworkSwitchClusterCalculator` 的命名钩子、能量晶簇的 `(height, inset)` 参数（同原版紫水晶簇）、`ModIds`。
- 验证：`compileJava compileTestJava test` 为绿。第 14 节回调的 datagen 核对已在 10.4 的验证中完成，零差异。

## 11. 与原审计报告的对齐（2026-10-14 复核）

复核方法：对审计中每条可以 grep 的结论重新搜索源码。无法通过搜索判断的（如通读大文件）标为"未做"。

| 审计条目 | 当前状态 | 说明 |
|---|---|---|
| B1 嵌套盘检查对 ECO 盘无效 | ✅ | 先查 ECO 注册表，再查 AE2 |
| B2 溢出安全加法 9 份副本 | 基本完成 | 按签名粗查，`(long,long)` 加法副本已收口到 `NEMath`；语义不等价的调用点按 D2 保留，未穷尽核对 |
| B3 MegaCells 重复注册 | ✅ | 未运行验证 |
| B4 推送上限三处矛盾 | ✅ | 配置项后由 D10 删除 |
| B5 递归 Tarjan | ✅ | 已改为迭代，统一到 `StronglyConnected` |
| B6、B7 主机判定与结果解析绕过 | ✅ | `CraftConfirmMenuMixin` 已使用 `ECOComputationHostRouting` 与 `neoecoae$resolveOwnedPlanningResult` |
| B8 渲染器重复注册、冷却渲染器未注册 | ✅ | 按 D1 删除 |
| B9 `eco.lss` 引用不存在的纹理 | ✅ | `BUTTON_HOVER`、`BAR`、`BAR_CONTAINER` 均已无引用 |
| B10 解锁条件错用 | ✅ | |
| B11 工作站处理器与 AppBotania `enqueueWork` | ✅ | |
| 一.1 数字格式化入口多 | 基本完成 | `.replace("K","k")` 与 `compactNumber` 已删除；`CraftingStatusTableMixin.formatExact` 仍是本地实现 |
| 一.2 clampToLong | 部分 | 已新增 `NEMath.clampToLong`；`ECOExactCraftingPlan.bounded` 仍是独立副本 |
| 一.3 BigInteger 网络编解码 | 部分 | 见 5.3 T2-3 |
| 一.4 AEKey 与 `Map<AEKey,BigInteger>` 的 NBT | 部分 | `ECOExactInventory` 未改；`StorageInterfaceFilter` 与批量盘键编解码归入 T4-8 |
| 一.5 精确数量类型 | 未开始 | T4-1 |
| 一.6 图模型 | 未开始 | 图模型统一是 T4-2；Kahn 见 5.3 T2-6 |
| 一.7 线程作用域栈 | 部分 | 见 5.3 T2-9 |
| 一.7 `setChanged(); markForUpdate();` | 大部分 | 剩余 `markForUpdate()` 17 处，分布在 7 个文件中（审计时约 37 对） |
| 一.7 yRot、`executeIfPossible`、压缩链判等 | ✅ | `BlockStateUtil` 中只剩 1 处 `toYRot() + 180` |
| 二 集成工作站与大型工作站 | 部分 | 常量遮蔽、电力设置、`isValidScale` 已合并；`PowerDrawHelper` 与 `autoBuild` 不做（见 8.1） |
| 二 合成/计算系统网络开关 | ✅ | T3-1 |
| 二 三个集群计算器 | ✅ | T3-2 |
| 二 `NELogicalNetworkManager` | ✅ | T3-3 |
| 二 集群类 `addBlockEntity` 的 instanceof 分桶 | 未做 | 审计列出，但未纳入计划 |
| 二 `MultiBlockBuildController.Host` 访问器 | ✅ | T3-4 |
| 二 流体仓、散热口、并行核心 | ✅ | T3-5 |
| 二 AE2 / AdvancedAE mixin 成对 | 部分 | 方法体共用 `ECOExternalCpuMixinSupport`；IO 端口两份 mixin 保留（I0-1 结论：都必需） |
| 二 JEI / EMI | 部分 | 见 5.4 T3-9 |
| 二 集成存储盘族与升级卡循环 | ✅ | T3-10 |
| 二 注册链（NEItems / NEBlocks / NEBlockEntities） | ✅ | T3-11；过度抽象的部分已回滚（10.5） |
| 二 GUI 小件 | 部分 | `string()` 已不是 7 份重复（只剩 `NeoUiJsonLoader` 一份，其余内联，见 5.3 T2-10）；4 个 `ItemSlot` 子类未处理（`PatternItemSlotClient`、两个 `RequiredItemSlot`、`FuzzyPlanningItemSlot`）；面板状态 NBT 编解码 5 份未处理；墨色三值未决（7.2 第 4 条）；`IBindable`、`BindableElement`、`GridSlot` 不存在 |
| 三 `ECOStorageCell` 对 `BasicCellInventory` | 部分 | 已用 `ConfigInventory` 与 `IPartitionList`；剩余偏离是有意的（315 类型、封存、迁移、批量语义），见设计说明 T4-8 |
| 三 批量盘的父类初始化开销 | 未做 | T4-8 |
| 三 存储盘 tooltip 与拆解 | 部分 | `CellTooltips`、`CellDisassembly` 已抽出，tooltip 只构建一次；未改用 AE2 `BasicCellHandler` 本身 |
| 三 `StorageInterfaceFilter` 对 `ConfigInventory` | 未做 | T4-8 |
| 三 `ECOIOPortTransfer` 对 `transferContents` | 未做 | T4-8 |
| 三 `ECOCraftingCpuView.addAllSaturating` | 保留 | 与审计结论不同：AE2 `KeyCounter.addAll` 会溢出，保留（见 5.3 T2-11） |
| 三 `NEBlockEntityTicker` | 不删除 | 被 `NEBlockEntityBuilder.serverTicker` 使用，见 §12 |
| 三 `ECOAe2LtDirectDispatch` 反射 `getSendList` | 未做 | `PatternProviderLogicAccessor` 已存在，需确认 AE2LT 的类型能否直接转换 |
| 三 `clamp(float…)` 自写版本 | 未做 | `ECOCraftingGraphScreen.clamp`，审计列出，但未纳入计划 |
| 三 `MultiBlockInfoWrapper` 手工 ModularUI | ✅ | 改用 `ECOModularUI.create(root)` |
| 三 fork 类对照 AE2 19.2.18 | ✅ | T4-11：两个都保留 |
| 四 执行模式、规划结果、模式身份、DTO、推送守卫、并行提供者、存储透传、规划会话样板、两个 C2S 包、mixin 业务逻辑 | 未开始 | T4-3 至 T4-8 |
| 四 `ICellHost` 别名 | ✅ | T4-12 |
| 五 死代码（类、方法、字段、33 个模型 JSON、语言 key） | 基本完成 | 残留见下 |
| 五 残留的空目录 | 未做 | `api/me/worker`、`blocks/entity/storage/handler`、`crafting/graph/client`、`terminal/bigamount` |
| 五 `NEBlockEntityTicker` | 不删除 | 同上，见 §12 |
| 五 `ECOPlannerRequester` | 保留 | 按第 1 节规则，作为 API 扩展点保留 |
| 五 `common.yes` / `common.no` | 不删除 | 动态拼接引用，`ECOCraftingGraphScreen.java:677`，见 §12 |
| 五 `infinite_component.unlock`、迁移相关死 API | ✅ | |
| 六 `integration/` 与 `compat/` 边界 | ✅ | `compat/` 已删除（T4-9） |
| 六 AE2LT 代码散落 | 部分 | 已收为 `integration/ae2lt` 与其 `dispatch` 子包 |
| 六 LDLib 必需依赖位置 | 未做 | 仍有 4 个文件在 `integration/` 中引用 `lowdragmc` |
| 六 模组 ID 与加载检查 | ✅ | `ModIds`；直接调用 `ModList.isLoaded` 已清除 |
| 六 mixin 插件的三种探测方式 | 未做 | `Class.forName` 等仍分散在各处 |
| 六 `mods.toml` 漏写可选依赖 | ✅ | T4-9 |
| 六 配置注释与语言文案漂移 | ✅ | T4-10，`common.on`/`common.off` 已删 |
| 局限：`ECOMachineInterfaceBlockEntity`（1,857 行）、`ECOCraftingPatternBusBlockEntity`（1,272 行）、三个执行核心类 | 未做 | 审计只抽样读过，仍未通读 |
| 局限：`/tmp/refs_<pid>.txt` 临时文件 | 不存在 | 复核时未找到 |

## 12. 第 2 批收尾项复核（只读，2026-10-14）

本节只记录复核结论，用于决定开工范围。没有改动任何源码。复核发现原先第 2 批收尾清单中有 3 项判断错误（第 5、6、8 项），已撤回。

| # | 项目 | 复核结论 | 依据 |
|---|---|---|---|
| 1 | 删除 4 个空目录：`api/me/worker`、`blocks/entity/storage/handler`、`crafting/graph/client`、`terminal/bigamount` | 可做 | `find -type d -empty` 确认为空，无文件引用 |
| 2 | `ECOCellMutationBatch`、`ExactAmountCollector` 改用 `ThreadScope` | 不能机械替换，需设计 | `ECOCellMutationBatch.close()` 在最外层关闭时会 flush 变更并维护静态 `RETRY` 队列；`ThreadScope` 只负责保存和恢复值。`ExactAmountCollector` 有嵌套状态和 `abort()`，`begin()` 不恢复父值。可替换的只有 set/restore 部分 |
| 3 | `ECOExactCraftingPlan.bounded` 改用 `NEMath.clampToLong` | 有条件可做 | 对非负输入等价；负数输入不等价（`bounded` 原样返回负值，`clampToLong` 返回 0）。`bounded` 是 public，共 9 处调用（`ECOCraftingCpuView`、`ElapsedTimeTracker`、`ExecutingCraftingJob` 等），需逐处确认输入非负，或保留 `bounded` 只改实现 |
| 4 | `AcyclicCraftingSolver` 手写 Kahn 改用 `TopoSort.kahnFifo` | 可做，需跑测试 | 结构等价：入度只对去重后的边计数，初始就绪顺序为 `reachable` 的插入顺序，都是 FIFO；`TopoSort` 的 checkpoint 重载可以保留取消点。`AcyclicStockRouteTest` 覆盖该路径 |
| 5 | 删除 `common.yes` / `common.no` | **撤回，不删除** | `ECOCraftingGraphScreen.java:677` 动态拼接 `"gui.neoecoae.common." + (value ? "yes" : "no")` 引用 |
| 6 | 删除 `NEBlockEntityTicker` | **撤回，不删除** | 是 `NEBlockEntityBuilder.serverTicker` 的参数类型，`NEBlockEntities.java` 等处共 10 处注册调用 |
| 7 | `ECOCraftingGraphScreen.clamp(float)` 改用 `Math.clamp` | 可做，需确认常量 | 3 处调用，min/max 为 `MIN_ZOOM`、`MAX_ZOOM` 常量；`Math.clamp` 在 min > max 时会抛异常，需确认常量顺序；NaN 语义两者一致 |
| 8 | `ECOCycleItemList`、`ExactKeyAmount` 切换到 `ExactMapSync` | **撤回，已完成** | 两者已使用 `ExactMapSync.writeSignedInteger` / `readSignedInteger` |

**结论**：
- 可直接做：1、4、7。
- 需要先决定：3（逐处核对还是保留负数语义）。
- 需要单独设计：2。
- 撤回：5、6、8。

**其余待复核**：`ECOAe2LtDirectDispatch` 的 `getSendList` 反射（未核对）；4 个 `ItemSlot` 子类（未核对）。

## 13. 只读审计结果汇总（2026-10-14）

完整设计说明与通读结论见 `docs/ARCHITECTURE_T4_DESIGNS_ZH_CN.md`。本节只记录对审计结论的修正和新发现。所有结论都是只读产出，未修改源码。

**对原审计结论的修正**
- `common.yes` / `common.no` 不是死 key，有动态拼接引用（`ECOCraftingGraphScreen:677`）。
- `NEBlockEntityTicker` 不是死代码，被 `serverTicker` 使用 10 处。
- `ECOCycleItemList`、`ExactKeyAmount` 已使用 `ExactMapSync`。
- `ECOStorageCell` 已使用 `ConfigInventory` 与 `IPartitionList`，剩余偏离是有意的。
- `StorageInterfaceFilter` 仍是自建 HashSet 加 `configured` 标志，没有用 `ConfigInventory`，审计结论成立。
- `CraftConfirmMenuMixin` 为 946 行，`CraftingServiceMixin` 为 814 行（审计原文为 969 和 824）。
- 审计说"游标 2 份"，实为 `ECOProviderCursor`（仅调度器使用）与两个同构环（bus 的 `roundRobinIndex`、worker 的 `nextFreeThreadIndex`），后两者才是合并候选。
- `ECOExecutionRequirement` 与 `ExecutionMode` 基本一一对应，但 `Schedule.Type` 与 `CycleExecutionDisposition` 语义不同，不宜全部合一。

**新发现（已列入设计文档）**
- 疑似 bug，按优先级：
  1. `CPU:373–381`：虚拟完成分支缺少 `pendingFinalOutputs.isEmpty()`，请求方可能收到 SUCCESS 而产物回流网络（中）。
  2. `TH:1338–1342` 与写出侧：物品列表读取上限 256 条，超出部分静默丢失（低触发，数据丢失）。
  3. `RT:640–641`：启动种子读取失败时静默跳过，不 quarantine（中）。
  4. `PB:323` 与 `ECOThunderboltBatchBridge.drainFastPath`：中途异常会丢弃已派发批次的计数（中，未测试）。
- 死代码：`snapshotCraftingInputs`、`startupSeedShortfall`、`DROPPED_TO_WORLD`、`insertPatternSlot`、`getPatternInterfaceStack`、`setPatternPreview`、`isDurabilityPattern`、`requestPatternDetailsRefresh` 等（均已核实仅有定义处命中或仅有 BE 自身转发）。
- 别名机制（`RT` 的 progress alias）恒为单任务，可简化为单任务映射。
- 退款逻辑 `MI:1313` 与 `PatternEncodingTermMenuMixin:106` 结构相同，仅 action source 不同，等价性需测试确认。

**仍未完成的核实**
- 上述疑似 bug 均为静态判断，未写测试复现。
- `ECOAe2LtDirectDispatch` 的反射与 4 个 `ItemSlot` 子类尚未核对。
