# 架构冗余审计与整改计划

> 2026-10-16 同步更新：本分支合并 main 至 `2249c671`（21.2.6），同步范围及冲突处理见 10.9。下列基线、包统计和旧批次提交号属于历史快照；部分旧提交号在当前历史中不可解析，不应作为当前 HEAD。

> 基线：`4908324b`，`src/main/java` 103,965 行。本次正确性修复代码 HEAD `46ebae48`（第二、三批收尾起点 `d2e42073`）；`src/main/java` 102,706 行（净减 1,259 行，包含收尾前已加入的大型工作站功能）。
> 路径默认相对 `src/main/java/cn/dancingsnow/neoecoae/`。
> 最新完整验证见 10.12（1432 项，1428 通过、4 跳过）；启用 AppBot 的 clean 构建、独立 core/平台测试与 JAR/源码包通过，datagen 对照 714 个文件零差异。
> 生成资源整体刷新已于 2026-10-16 完成，见 10.13：按不兼容模组分两套环境生成，合并 740 个文件，5 份语言文件各 948 个 key；本轮相关测试 106 项全通过。
> 第 4 批的设计说明已并入本文件 §14（原 `ARCHITECTURE_T4_DESIGNS_ZH_CN.md` 已删除，可从 `69a20949` 恢复）。

---

## 1. 当前状态与下一步

| 批次 | 状态 | 提交 | `src/main/java` 行数 |
|---|---|---|---|
| 基线 | — | `4908324b` | 103,965 |
| 第 0 批 正确性修复 | ✅ | 分批本地提交 | — |
| 第 1 批 删除死代码 | ✅ | 分批本地提交 | 102,137 |
| 第 2 批 横切能力收口 | ✅ 已收尾并通过完整回归（见 10.6）；存储编码迁移归 T4-8 | 分批本地提交 + `aef79f56` | — |
| 第 3 批 合并平行类 | ✅ 已收尾并通过完整回归（见 10.6） | 第 1～3 波提交 + `1fa605ba` / `85a6d77b` | — |
| 第 4 批第一组（T4-9～T4-12） | ✅ | `7a99dff1`～`c8ccd192`（6 个代码提交），记录于 `1ecf242f` | 101,226 |
| 第 4 批其余（T4-1～T4-8、T4-13～T4-19） | ✅ 按兼容边界完成本轮实现；逐项范围与验证见 10.12 | 10 批代码提交与 1 批文档提交，见 10.12；未推送 | — |

**下一步**：
1. 第 4 批实现与兼容取舍已记录在 10.12；§14 保留历史设计快照，与实现有差异时以 10.12 为准。
2. 后续进入目标版本移植和运行验证，在目标工程确认 AE2 API、旧版 UI 与 Java 工具链。D7/D8 属于目标版本选择，不阻塞当前 core 抽取。
3. `en_ud.json` 等生成文件整体刷新已完成，见 10.13；客户端与真实存档验证仍待进行。

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

`runData` 的结果依赖开发环境装了哪些可选模组。常规重构验证时，**不能直接采用单一环境的生成结果**：

1. 生成前删除 `src/generated/resources/.cache`。
2. 用 `git archive` 把 HEAD 和当前工作区分别导出到仓库外。
3. 把 `run/mods/*.jar` 拷到导出目录的 `run/mods`（否则会因缺少 curios 而失败）。
4. 分别执行 `./gradlew runData --offline`，比较两份输出，要求零差异。
5. 如果生成器有改动，只按 key 级差量合并到仓库里的生成文件，不整文件覆盖。

用户明确要求整体刷新时，按 10.13 的环境矩阵生成：不兼容的可选模组分别运行，核对公共文件一致，再合并语言 key 与各自独有的资源。此时应逐项审查与旧资源的差异，包括旧配方删除；不再把历史资源漂移保留为基线。仅排版不同的 JSON 保持原文件，避免无内容变更。

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
| T4-13 | 通用算法抽取完成，平台边界保留 | 泛型规划视图、图/库存/需求传播/路线与循环数学迁入 core；AE 语义与协调适配保留，见 10.12 |
| T4-14 | 代码完成，见 10.12 | 调度数学、限额、停滞时钟和阶段屏障放 core；CPU、提供者推送放平台层 |
| T4-15 | 代码完成，见 10.12 | 泛型无限存储引擎、精确总量与字节/迁移计算放 core；SavedData 与盘内容适配保留 |
| T4-16 | 代码完成，见 10.12 | core 的 DataReader/DataWriter/PacketBuf 与共享 codec；NBT/MC 网络适配保留旧格式 |
| T4-17 | 代码完成，见 10.12 | core 承载控制器状态、存储视图和 HostText 数值数据，不依赖 LDLib |
| T4-18 | 代码完成，见 10.12 | 配置定义和三份语言生成表放 core；1.21.1 builder/generator 遍历绑定 |
| T4-19 | 代码完成，见 10.12 | 真实 core/mc1211 多项目、独立 core 产物及根任务兼容；其他版本项目随实际移植建立 |

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
| T2-3 | BigInteger 网络编解码统一到 `ExactMapSync`（有符号版 + 负数检查）；线上字节格式不变 | ✅：精确数值编码共用 `ExactMapSync`；用户确认保留 `MaterialNode` 六个字符串字段及 UTF 协议，图模型调整归 T4-2 |
| T2-4 | `AmountMapNbt` 辅助类，`ECOBigOrderController`、`ExecutingCraftingJob` 等共用 | ✅（本批范围）：数量表共用 `AmountMapNbt`；`AEKey` 键编解码与旧存档兼容明确归 T4-8 |
| T2-5 | `GenericStackNbt` 有界读取 | ✅：工作站 `readCounter` 已接入；执行计划与输出交付的读取布局、失败策略不同，有意保留自有路径 |
| T2-6 | `TopoSort`（`kahnFifo`/`kahnOrdered`）、`StronglyConnected` 统一 SCC、客户端 `GraphAdjacency` | ✅：`AcyclicCraftingSolver` 剩余手写 Kahn 已换为带取消检查点的 `TopoSort.kahnFifo` |
| T2-7 | `BigIntegers` 校验统一；插入请求和结果的校验放到 `ECOBigIntegerStorage` | ✅ |
| T2-8 | `NEBlockEntity.persistAndSync()`、`ServerTaskUtil.isServerStopping()`；约 40 处替换；裸 `executeIfPossible` 全部加保护 | ✅ |
| T2-9 | `BlockStateUtil.yRotation`（21 处）、`ThreadScope`、压缩链判等、CustomData 辅助方法 | ✅：通用作用域已收口；保留已确认的两种专用 ThreadLocal 生命周期及原计划中四种不同的清盘语义，见 10.6 |
| T2-10 | `FittedText`、`Palette`、`HostElements.tooltips`、`ECOModularUI.create(root)` | ✅：用户接受内联 `getAsString()`，不再增加 JSON 包装；墨色与状态色保持现有差异，见 10.6 |
| T2-11 | `ExecutionOutputs.accumulate`（2 处）；`ECOCraftingStackCodec` | ✅（`addAllSaturating` 保留，AE2 版会溢出） |
| T2-12 | `integration/ModIds`（19 个常量，`isLoaded` 带 null 保护）；删除 Omni 中死掉的 `supports()` | ✅ |

### 5.4 第 3 批：合并平行类

| ID | 内容 | 结果 |
|---|---|---|
| T3-1 | `NENetworkSwitchHost` 接口 + `NetworkSwitchHostSupport` | ✅（`@Persisted` 字段留在方块实体上，见 8.1） |
| T3-2 | `NEClusterCalculator.verifyEitherMirror`；`NENetworkSwitchClusterCalculator` 中间基类 | ✅ |
| T3-3 | `NELogicalNetworkManager` 泛型 `Domain`；`NetworkSwitchMultipliers`；`CONTROLLER_POS_ORDER` | ✅ |
| T3-4 | `MultiBlockBuildController.Host` 缩减为默认方法；三个控制器的 get/set 改用 Lombok | ✅（构建状态与 `autoBuild` 不移，见 8.1） |
| T3-5 | 流体仓 4 合 1、散热口与并行核心参数化；idle 功耗由构造参数传入；旧类 16 个删除 | ✅（行为变化见 9；旧公开入口已在 `d2e42073` 恢复） |
| T3-6 | 集成工作站与大型工作站共用 `isValidScale` 等；删除遮蔽常量和重复电力设置 | ✅（`PowerDrawHelper` 不做，见 8.1） |
| T3-7 | `NEBlockEntity.onUnloadOrRemove()` 钩子 | ✅ |
| T3-8 | AE2/AdvancedAE CPU mixin 共用 `ECOExternalCpuMixinSupport`；`ECOExternalCpuOutputRouter` 被实时扫描和 AdvancedAE 扫描共用；IO 端口 mixin 共用 `ECOStorageCells.orEcoCellInventory` | ✅ |
| T3-9 | `TextureConstants` 移到 `integration/xei`；EMI 多方块列表从 `NEMultiBlocks.DEFINITIONS` 推导 | ✅：新增 `RecipeLayouts` 共用冷却与工作站的几何常量；用户确认保留两种浏览器的显示差异 |
| T3-10 | `IntegrationCells`（`Size` 枚举 L4/L6/L9）、`OptionalCellUpgrades.addStandard`；MEGA 4 个子类合并为 `ECOMegaStorageCellItem` | ✅（审计原文的 `ECOCellModels.registerTiers` 不存在） |
| T3-11 | `ToolKind` 收掉 20 个工具；`NEBlockEntities` 新增 `clusterBlockEntity`、`tieredBlockEntity` 等；`NEMultiBlocks.byTier` | ✅（差异项保留，见 10.3） |
| T3-12 | `HostElements.hiddenValue`、`TabBackButton`、`ECOIcon.toolbarBackground`、`CpuSelectionServerState` | ✅（`BindableElement`、`GridSlot` 不存在；滚动条不换，见 8.1） |
| T3-13 | `CellTooltips`、`CellDisassembly`；存储盘只构建一次；MEGA 长批量盘覆盖 `appendCellLines` | ✅（Jade 存储控制器的 UUID 行未改，因为它手里是字符串） |

---

## 6. 第 4 批任务状态

原计划逐项确认；用户于 2026-10-16 明确授权一次完成第 4 阶段。当前实现与兼容调整统一见 10.12，历史设计保留在 §14。

| ID | 状态 | 内容 |
|---|---|---|
| T4-1 | 代码完成 | PlannerAmount 统一有限值/无穷，旧公开类型与存档兼容，见 10.12 |
| T4-2 | 代码完成 | 泛型规划图与布局视图，快照保持网络 DTO，见 10.12 |
| T4-3 | 代码完成 | 单一模式分类、惰性合同缓存和注册表合同收口；旧公开入口兼容，见 10.12 |
| T4-4 | 代码完成，按语义调整 | 代际字段、工具池 DTO 和工作容量验证去重；公开 work、不同身份相等域和阶段守卫有意保留 |
| T4-5 | 代码完成 | 无状态规划器复用；并行提供者实际逻辑收敛，旧入口兼容 |
| T4-6 | 代码完成 | 共享下单校验、ECOPayload 与 DeltaSyncChannel；旧包与基线失败语义兼容 |
| T4-7 | 代码完成 | 投影/CPU 查找移出 mixin，规划会话与取消入口复用 |
| T4-8 | 代码完成，按兼容调整 | ConfigInventory/IPartitionList、批量盘初始化、IO 入口及 BE 透传收敛；保留旧 NBT，显式 key-type codec，见 10.12 |
| T4-9 | ✅ | `integration/`、`compat/` 边界重划；`mods.toml` 补全可选依赖（见 10.4） |
| T4-10 | ✅ | 语言文案：死 key 清理、拼写改名、配置文案三处统一（见 10.4） |
| T4-11 | ✅ | AE2 fork 类评估：两个都保留；`ExecutingCraftingJob` 的损坏恢复小修已完成（见 10.4） |
| T4-12 | ✅ | `util.ICellHost` 别名删除，改用 API 版本 |
| T4-13～T4-19 | 本轮实现完成，平台边界保留 | 见 4.5、10.12；其他版本实际移植不计为本轮已验证 |

---

## 7. 待决事项

### 7.1 第 0～4 批的决定项

| ID | 问题 | 状态 |
|---|---|---|
| D1 | `ECOComputationCoolingControllerRenderer` 注册还是删除？ | ✅ 删除 |
| D2 | 溢出安全加法遇到负输入的统一语义 | ✅ 采用 `NEMath` 的"先截为 0"；语义不同的调用点保留私有实现并加注释 |
| D3 | 拼写错误的语言 key 改名是否保留别名 | ✅ 直接改名，不保留别名，CHANGELOG 说明（见 10.4） |
| D4 | 第 4 批各项是否执行 | ✅ 用户于 2026-10-16 授权一次完成第 4 阶段，见 10.12 |
| D5 | 1.7.10 的 Java 工具链 | ✅ 采用 jvmDowngrader（语法和标准库一起降级）。**GTNH 构建脚本的可行性验证结论未记录，需补** |
| D6 | 多版本工程组织：① 每版本一分支；② 单仓多项目；③ Stonecutter 类预处理器 | ✅ 在整体实施授权内采用 ②，已建立 `:core` + `:mc1211`；其他版本随实际移植建立，无空壳项目 |
| D7 | 1.20.1 的 UI 方案：LDLib 1.x 还是 AE2 `AEBaseScreen` + 自写 JSON 布局渲染器 | ⏳ 默认先评估 LDLib 1.x 与当前 JSON 布局的契合度，再决定 |
| D8 | 1.7.10 的功能范围 | ⏳ 默认只移植核心机器、规划器和存储；集成按 GTNH 整合包实际存在的模组另行规划 |
| D9 | `eco.lss` 中 `BUTTON_HOVER` 等悬停引用 | ✅ 指向 `BUTTON_HIGHLIGHTED`，字段和 PNG 已核对 |
| D10 | 推送上限硬编码为 64 | ✅ 删除 `ecoCpuPushTickLimit` 配置项及语言 key，保留固定上限 64 |

### 7.2 执行过程中产生的待用户决定

1. **JEI 与 EMI 显示差异**：✅ 用户确认只共享相同布局参数，保留缩放、动画时长、闪电文案和分类图标差异（10.6）。
2. **AE2LT 的 `finishJob` 失败钩子**：✅ 用户确认对齐 AE2/AdvancedAE，改用 `cancelAndRecover`，发布取消决定并唤醒已加载工作线程回收（10.6）。
3. **计算系统计算器的镜像状态**：✅ 用户确认修复；两种方向均校验失败后，清除网络开关状态并重置检查范围内冷却控制器的镜像状态（10.6）。
4. **界面墨色与状态色**：✅ 用户确认保留三种面板墨色及图界面不同状态域的绿色（10.6）。
5. **`common.on/off` 的文案**（10.4）：这两个 key 已删（死 key）。大型工作站的 `enabled/disabled` 与镜像的 `on/off` 中文是"开/关"，`common.*` 是"开启/关闭"。若要统一，需要先定统一后的中文文案。
6. **生成文件刷新**：✅ 用户于第 4 阶段结束后明确要求执行，已完成整体刷新与可选集成覆盖，见 10.13。10.4 的缺 key/旧值统计为历史记录。
7. **`MaterialNode` 字符串编码**：✅ 用户确认保留字符串与 UTF 协议；图模型统一留在 T4-2。
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

### 10.6 第 2、3 批收尾（2026-10-14）

代码提交：`aef79f56`（第二批）、`1fa605ba`（配方布局）、`85a6d77b`（行为修复）。

- 用户确认六项推荐方案：保留图 DTO 的 UTF 字符串协议、公开 `bounded` 的负数/null 行为、两个专用 ThreadLocal 生命周期、内联 JSON 读取及现有颜色；JEI/EMI 只共享相同几何参数。
- 第二批：`AcyclicCraftingSolver` 改用 `TopoSort.kahnFifo`，保持 FIFO 与取消检查；`bounded` 的非负数计算共用 `NEMath.clampToLong`；图界面的两处限幅改用 Java 21 `Math.clamp`；大型工作站继承 AE2 基类而非 `NEBlockEntity`，两对持久化/同步调用保持原样；删除 `Palette` 中已决定保留差异的 TODO。
- 第三批：新增 `integration/xei/RecipeLayouts`，冷却与普通/大型工作站的相同尺寸、槽位、进度条和耗电文字坐标由 JEI/EMI 共用。JEI 槽位内容坐标仍在框内偏移一像素；文字缩放与动画时长仍各自保持。
- 用户另确认两项行为修复：AE2LT 失败终结改走 `ECOCraftingJobLifecycle.cancelAndRecover`；计算系统两种方向均校验失败后，将范围内冷却控制器设为未镜像。
- 回归：增加 `bounded` 数值边界测试、AE2LT 取消回收测试与计算系统三种镜像校验结果测试。核对实际依赖 JAR 后确认时间轮任务仍持有 `link`，原桥接只找 `id`，会静默退场；桥接现兼容直接 UUID 与 `link.getCraftingID()` 两种形状，并测试真实 JAR 的私有任务结构及空任务。
- 兼容入口：`d2e42073` 恢复合成/计算并行核心的旧二进制入口类，以及大型工作站流体输入/输出仓的旧类入口。保留的旧类是兼容垫片，参数化实现仍是单一行为来源。
- 范围已明确：旧存档键编码归 T4-8；图模型归 T4-2；面板状态与领域编解码归 T4-17/T4-16。AE2LT 传输反射和四种槽位子类有意保留，不再计为第二、三批未核实项。
- 验证：`compileJava compileTestJava test` 通过；1174 项测试，1171 通过、3 跳过、0 失败。`ECOAe2LtCancellationBridgeTest` 4 项、`NEComputationClusterCalculatorTest` 3 项、`ECOExactCraftingPlanTest` 2 项，以及既有拓扑/库存路由回归均通过。`-PenableAppBotIntegration=true compileJava` 通过。未进行真实游戏世界或客户端视觉验证。
- 构建环境：离线构建初始缺少 NeoForge 21.1.251；在线代理已显式配置。Minecraft 客户端/服务端文件经 SHA-1 核对后从本机 Windows 缓存复用，Maven 依赖也复用了现有缓存。Modrinth 的固定 Useless Mod 版本返回 404，KubeJS Maven TLS 连接失败；使用仓库外临时 init script 指向相同坐标的已缓存 JAR/POM/module，未修改仓库依赖配置。

---

### 10.7 通读审计四项优先正确性问题修复（2026-10-14，已完成）

代码提交：`1294c3df`（派发、虚拟完成与启动种子）、`46ebae48`（存档物品列表）。

- 四项均已通过回归测试复现，测试先在修复前失败，再验证修复后的结果。
- 批量派发：第二条车道的 `prepareAllocated` 抛异常时，第一条车道已成功提交的数量会丢出调用链。现仅捕获无资源转移的准备阶段异常，停止派发并返回准确余量；提交阶段的所有权不确定异常仍向调用方传播，不触发普通推送回退。
- 虚拟完成：`tryCompleteVirtualCrafting` 增加 `hasPendingFinalOutputs` 守卫。测试覆盖任务逻辑完成后仍有动态最终产物、请求方第一次拒收、第二次交付成功的完整路径。
- 启动种子：NBT 读取返回 null 或非正数量时拒绝恢复 runtime；现有 `ExecutingCraftingJob` 的失败处理会将任务设为 `suspended` / `EXECUTION_METADATA_LOST`。有效种子的阶段归属与余量恢复保持原样。
- 物品列表：25,345 件物品写成 257 条 NBT，原读档只恢复 25,344 件；输入、输出、余料均可复现。用户选择 A：取消普通物品列表的 256 条截断及超长列表损坏标记，完整读取现有格式，不升级 NBT 版本。不可解析条目、混合格式与其他损坏状态仍走原有恢复路径。三项回归确认数量完整，并保持 `ACTIVE` 正常续作状态。
- 最终验证：`compileJava compileTestJava test --offline --no-configuration-cache` 通过，包含此前暂时排除的三个存档回归；1185 项测试，1182 通过、3 跳过、0 失败。`-PenableAppBotIntegration=true compileJava` 通过。沿用 10.6 的仓库外缓存 Maven init script，本次未使用测试排除脚本。未进行真实游戏存档或客户端验证。

---

### 10.8 精简低价值界面测试（2026-10-15）

代码提交：`45716d27`。用户授权按测试价值精简，不设用例数量目标。

- `GuiModuleBoundaryTest`：删除禁止方块实体出现 UI 构造或 `.layout()` 的源码正则检查；保留服务端状态与公共菜单不引用客户端实现的两项检查。
- `CraftingUiJsonResourceTest`：保留三份资源的存在/解析、必要控件和动作名称检查；按控件 ID 递归查找，撤销固定尺寸、坐标、子节点数量与索引顺序断言。
- `StorageHostUiTest`：普通和无限模式分别验证必要控件及库存类型，撤销 JSON 布局的逐项坐标与旧树结构快照；信息图标保留 hover 和同步状态检查，撤销静态尺寸断言；删除只核对未参与渲染的 PNG 固定边框像素的测试。
- 合并两份磁盘原生绘制用例：合并后仍检查真实 ItemStack、LocalSlot，以及 TYPES_FULL / FULL / NOT_EMPTY 三种磁盘状态和无额外绘制调用。
- 三个文件净减 74 行；测试方法 26 → 23，显式断言调用 113 → 65。像素绘制位置、服务端锁、分页、资源同步、存档/守恒、算法和外部模组兼容回归继续保留。本次没有修改生产代码、资源或依赖。
- 验证：`compileJava compileTestJava test --offline --no-configuration-cache` 通过；1182 项，1179 通过、3 跳过、0 失败。沿用仓库外缓存 Maven init script，未排除任何测试。此次优化主要减少维护成本，没有据此宣称性能提升。

---

### 10.9 同步 main 至 21.2.6（2026-10-16）

- 合并前本分支为 `d20b4cc1`，main 为 `2249c671`，引入 main 的 32 个提交。第 4 批剩余任务没有因此标记完成，后续设计需基于同步后的代码重新核对。
- 同步内容：样板语义与执行优化公共注册接口、忽略组件的规划与派发一致性、循环种子保留和可达路线搜索、AE2LT IO 端口兼容、Omnicells 乏核废料矩阵、大型工作站连接与供能修复、样板界面 Shift 拖动、JEI 收藏导入，以及主机面板档位和图集资源扩展。
- 合并涉及 26 个冲突路径。保留本分支 `core`、`crafting/adapter`、`integration/*/dispatch` 的结构，新代码和测试引用适配至现有包；可选样板能力的注册入口放在 `integration/ECOPatternIntegrations`。接受 main 删除旧 EAEP 语义适配器，改走标准样板契约。
- 保留已完成的配置默认值常量化、固定 64 推送上限、数量表 NBT 公共实现，以及此前的虚拟完成、启动种子和普通物品列表恢复修复。新增配置翻译绑定，注释、语言生成器和现有语言文件按 key 对齐，不恢复已删除的推送上限配置。规划器边界测试同时覆盖旧 compat 和现有 integration 包。
- 完整验证：`compileJava compileTestJava test --offline --no-configuration-cache` 通过；1278 项，1274 通过、4 跳过、0 失败。跳过项为性能基准及三个受当前 AE2LT API 条件限制的用例；未排除测试。
- 补充验证：启用 `-PenableAppBotIntegration=true` 后，`check jar compileJava` 通过，完整测试仍为 1278 项、1274 通过、4 跳过、0 失败。
- 构建环境：Curse Maven 下载 Useless Mod 2.4.6 超时后，从同一原始地址下载 JAR（SHA256 `b86a12e8bd6417fd3c89f7c723ca195d68d1f9f9f099480850ffd3c93e5897bc`），用仓库外临时 init script 直接提供该文件，项目依赖声明未为验证而修改。未进行游戏客户端、真实存档或 datagen 验证。

### 10.10 第 4 批首轮推进：T4-3～T4-5 部分实施（2026-10-16）

- 基于 `cfb40d0e`，用户授权多 agent 继续推进。两条实施路线分开修改文件，随后交叉审查，由主代理串行运行 Gradle；本轮改动尚未提交。
- **T4-3 S1**：移除注册表中仓内零调用的 `activeSubmissionMetadata` / `SubmissionMetadata`、`recoverSchedule` / `RecoveredSchedule`，删除合同的 `phased()` 与 `ExecutingCraftingJob` 的死 import，清理失效 Javadoc。交叉审查发现 `docs/API.md` 与 `docs/API_ZH_CN.md` 的示例使用 `executable()`，因此有意保留该方法，修正原设计直接删除的建议。其余删除项仍是 public 符号，未知第三方若调用会产生源码/二进制兼容影响；仓内零调用不等同于对外兼容保证。
- **T4-3 S0**：新增 `ECOPlanningResultExecutionContractTest`，共 5 个实际路径用例。非 SUCCESS 的 ORDERED / DYNAMIC 结果跳过阶段构建、返回 NATIVE 合同但仍预期循环；SUCCESS 的相同循环缺执行顺序时通过实际调度构建器失败，转为 BLOCKED；未解循环直接保持 BLOCKED。未修改这些回退与拒绝行为。
- **设计更正**：§14 原先声称非 SUCCESS 含环结果不可能返回 NATIVE，复核后不成立，已更正。继续 S2～S4 前需沿实际提交路径检查状态过滤和外部 CPU 准入；不能将 NATIVE 合同等同于规划成功。本轮不据此修改回退策略。
- **T4-4 局部整理**：`ECOCraftingThread` 的虚拟工作与批量工作共用 `canRetainWork`，保留 output / input / remainder 的容量校验顺序和参数。三个 record getter 提前求值无副作用；冷却扣费、准入与启动时序不变。未移除 worker / thread 的准入守卫。
- **T4-5 局部整理**：`ECOExactBatchPlanner` 与 `ECOStatefulBatchPlanner` 各复用一个 `static final ECOBatchPlanner`，替代四处每次调用时分配；该规划器没有实例状态，方法只使用参数与局部变量。
- **验证**：`check jar --offline --no-configuration-cache --console=plain`（使用 10.9 的仓库外临时依赖 init script）通过。XML 统计共 1283 项、1279 通过、4 跳过、0 失败/错误；跳过项同 10.9。`git diff --check` 通过。首轮按类筛选运行时，`ECOBatchExecutorTest` 出现 Mockito / 类初始化失败；完整套件通过，单独运行的初始化依赖仍未修复，不将定向运行表述为通过。未进行客户端、真实存档或 datagen 验证。
- **尚未实施**：T4-3 S2～S4 模式合并与合同字段收口；T4-4 身份/DTO/派发守卫/游标改造；T4-5 并行适配层收敛；T4-6～T4-8。§14 中相应“现状”与行数是设计时快照，以本节增量为准，不能将整项任务标为完成。

### 10.11 第 4 批第二轮：模式与合同缓存、提交边界及内部去重（2026-10-16）

- 延续 10.10 的未提交改动，三条 agent 路线分别负责模式/合同、批量内部去重、提交链审查与修复，再交叉审查。已 `fetch origin`，`origin/main` 仍全部包含于当前分支，未另行合并或推送；本轮尚未提交。
- **T4-3 S2，按兼容目标调整**：实际分类统一为 `ExecutionMode`，共享原精确加法、相关身份溢出与循环求解校验；组件判断迁入 `CycleExecutionDisposition`。仓内调用改为 `executionMode()` / `requiresCycleExecution()`。旧 `ECOExecutionRequirement` 及 `executionRequirement()` 仅保留 deprecated 兼容入口，分类委托至唯一实现，没有第二份算法。`Schedule.Type`、组件 disposition 与持久化枚举名称不合并、不改名。
- **T4-3 S3，按延迟计算目标调整**：正常执行解析只发布一个 `volatile ECOExecutionContract`，在同一把锁内首次构建，后续访问复用合同和签名。保留 final `planningId` 与初始诊断分类，避免只读 ID / 模式时计算签名；分类模式与非成功诊断合同的 NATIVE 模式仍有意区分。无法取得有效签名时另存 `resolutionError`，保持旧 `EXECUTION_PLAN_BUILD_FAILED` / BLOCKED 诊断及一次解析语义，不制造缺签名合同。BLOCKED、非 SUCCESS、空组件或无 plan 的纯诊断读取仍不计算签名。
- **模糊匹配快照**：setter 复制传入集合，同锁替换已缓存合同的不可变快照，复用签名与执行计划；外部集合后续修改不影响合同，首次读取合同后再更新配置也不会丢失。
- **提交边界修复**：正常 planner service 将非 SUCCESS 结果桥为 simulation，service 层已有拒绝，因此此前模型边界不能直接等同于 UI 可复现故障。但严格匹配的失败循环仍可登记防御元数据，且 CPU 直接入口绕过 service；原 lifecycle 不检查 BLOCKED。现在 `resolveContract` 仅在严格匹配后，把非 SUCCESS 且需要循环执行的诊断投影为 `BLOCKED / PLANNING_NOT_SUCCESS:<status>` 提交合同；原 BLOCKED 及错误保持不变。CPU 入口在抽取材料、启用精确库存与建立任务前拒绝 simulation 或 BLOCKED。纯 DAG 缺料强制开工仍保留，精确订单独立执行计划入口保留。
- **T4-4 内部去重**：`ECOFastPathKey` 不再重复持有 `reloadGeneration`，经 `patternKey` 读取，访问器与缓存相等域保持；工具池直接返回现有 `BatchContract`，移除 package-private `ToolPoolBatch` 及一对一转换，不更改公开提供者接口。
- **新增回归**：合同首次签名计算/缓存、并发发布、fuzzy 快照更新、签名缺失/null/异常及恢复、相关模式身份数量溢出；全部 10 个失败状态 × ORDERED / DYNAMIC 的真实登记与外部两个 CPU hook 拒绝；直接 CPU 拒绝无物料副作用、simulation 拒绝、纯 DAG 强制开工准入；缓存代次隔离、混合耐久工具消耗/返还/容量与预览库存不变。
- **验证**：`check jar -PenableAppBotIntegration=true --offline --no-configuration-cache --console=plain` 使用 10.9 的仓库外 init script，通过完整测试与打包。共 1318 项、1314 通过、4 跳过、0 失败/错误，较 10.10 新增 35 个测试实例；跳过项仍为性能基准及三个 AE2LT API 条件用例。`git diff --check` 通过，产物 `build/libs/neoecoae-21.2.6.jar`（5,713,753 字节）。未进行游戏客户端、真实存档或 datagen 验证。
- **剩余边界**：普通 `ECOForcedCraftingPlan` 会把 missing 移入 emitted，改变 `PlanIdentity` 的完整身份；本轮未为这种循环缺料包装建立新的元数据重绑机制，不能声称所有强制循环下单已验证。公开 `ECOParallelProviderAdapter` 与注册表保留，避免为内联再添加兼容包装。T4-3 S4、`CraftWork`、跨层派发守卫、游标及 T4-6～T4-8 尚未实施。


### 10.12 第 4 批整体实施与收尾（2026-10-16）

**授权与保护**：用户在 10.11 后明确要求一次完成第 4 阶段，并允许多个 agent，覆盖旧计划中的逐项等待确认。开工前已将当前 `src`、`docs`、构建文件及 diff 快照保存到仓库外 `Temp/neoecoae-phase4-snapshots/20261016-193810-before-phase4.zip`。本节记录本轮实现与后续分批本地提交；其他 Minecraft 版本的实际移植另行进行。

**逐项实现与有意保留的边界**：

| 任务 | 实现与兼容取舍 |
|---|---|
| T4-1 精确数量 | `PlannerAmount` 统一有限值与显式正无穷，拒绝把无穷缩窄为有限数；`ExactAmount`、`HugeAmount` 保留公开接口和存档结构，算术委托同一实现。`ExactCycleAmount` 继续使用无损 `BigInteger`，不为统一名称引入额外转换。 |
| T4-2 图模型 | 泛型 `DependencyGraph<K,N,E>` 承载闭包与 SCC；AE `CraftingDependencyGraph` 保留适配。快照保持原 DTO/协议，通过 `LayoutGraph` / `CycleTopology` 提供投影视图；布局、空间索引、compact-tree 与循环簇投影迁入 `core.graph.presentation`，旧公开入口委托。 |
| T4-3 执行合同 | 延续 10.11 的单合同缓存；注册表 `Entry` / `SubmissionAlias` 持有合同，恢复接口由合同派生。恢复 `activeSubmissionMetadata` / `SubmissionMetadata` / `recoverSchedule` / `RecoveredSchedule` 为 deprecated 兼容入口，避免仓内零调用清理破坏外部调用。 |
| T4-4 身份与工作 | 代际信息从 `patternKey` 读取，工具池复用公开 `BatchContract`；批量/虚拟工作共用 `CraftWorkValidation`。保留两个公开 work record 以及 worker/thread 的不同阶段守卫；真实 tracker 作用域、提供者能力与 bus 空间检查继续在平台层执行。 |
| T4-5 批量与提供者 | 无状态 planner 实例复用；并行提供者的实际逻辑收敛到 `ECOFastPathFacade`，旧公开 `ECOParallelProviderAdapter` 为 deprecated 兼容入口。 |
| T4-6 网络 | `CraftSubmitC2SPacket` 统一 STANDARD/BIG_ORDER 的 menu/container/stillValid 校验；旧包 ID 与 VarInt+boolean 编码保留。`ECOPayload` 统一调度入口。`DeltaSyncChannel` 提供准备/提交基线，成功排队后才提交同步状态；`MapDelta` 保持兼容。 |
| T4-7 mixin 与会话 | 循环材料投影迁入 `ECOCycleItemProjection`，CPU 查找迁入 `ECOCraftingServiceClusters`；mixin 保留挂钩。`ECOPlanningSessions.open` 统一库存捕获与 session 创建，服务、巨量订单和 calculation mixin 共用；取消语义统一到 `ECOCancellation.INTERRUPTIBLE`。 |
| T4-8 存储适配 | Filter 使用 `ConfigInventory`/`IPartitionList`，保留旧 `CompoundTag` 编码；键通过 `AEKeyType.CODEC` + `loadKeyFromTag` 解析，避免缺失注册占位与测试 bootstrap。批量盘保留 v1 `item/components`、损坏/cutoff/unresolved 信息，并通过 protected 开关跳过不需要的普通盘初始化。IO 用 OperationMode 入口，保留旧三个 boolean 重载、5 次/tick 及供能/恢复语义。删除 BE 死透传。 |
| T4-9～T4-12 | 延续已完成的包边界、语言、fork 评估与别名清理（10.4）。 |
| T4-13 规划器 | `PatternView<K>` / `KeyOps<K>` 无逐 key 包装；core 承载图闭包/SCC、可达性、精确计数/库存、无环需求传播与排序、候选回退、特殊需求递归/工具数学、joint-route 数学、联合产物需求前瞻队列、启动供给层级与低层级路线回溯、simplex/整数分支、单模式增长及 indexed-cycle 搜索。AE 配方匹配、ItemStack、来源跟踪和协调策略留在适配层。未把全部 `planner` 类宣称为平台无关；`:core` 是可独立编译与测试的可复用算法集。 |
| T4-14 执行层 | 限额、dispatch budget、批量调度数学、ordered witness step、阶段屏障与 stall 时钟迁入 `core.execution`，生产路径委托；AE CPU/provider/NBT 留在平台层。 |
| T4-15 存储引擎 | `ExactStorageEngine<K>`、精确总量/算术/迁移计算迁入 `core.storage`，保留 long 快路与大整数语义。旧 `StorageByteAccounting` 保留三个 deprecated 入口。SavedData、盘内容和 MC 生命周期继续适配，未替换其他分支的 File/WAL 持久化。 |
| T4-16 持久化与传输 | `DataReader<K>` / `DataWriter<K>` / `PacketBuf<K>` 位于 core，金额映射与完整执行计划 codec 共用；NBT adapter 负责 AE pattern/key/GenericStack。图内层字段经 PacketBuf，MC 外层压缩/registry 保留。旧 NBT schema、重复次数默认值、UTF 字段和 enum ordinal 编码保持并有回归测试。 |
| T4-17 UI 状态 | crafting/computation packed-slot 状态、storage 视图/分页/大整数容量、HostText 数值内容迁入 `core.ui`；GUI adapter 保留 Component/Fluid/ItemStack/NBT/LDLib 行为，界面框架选择不影响这部分抽取。 |
| T4-18 声明表 | 15 个配置项的键、默认值、范围、注释及 5 个 section 位于 `ConfigDefinitions`；NeoForge builder 遍历绑定。三份语言生成主体迁入 core 表，旧 generator 委托；生成的语言 JSON 未改写。 |
| T4-19 多项目 | 建立真实 `:core` Java library 与 `:mc1211` NeoForge 子项目。根任务保留原 build/check/jar/runClient/runData 入口及 src/build/run 路径。模组 JAR 内嵌 core，发布元数据不再额外依赖重复 core JAR；平台 artifactId 保持 `neoecoae`。core 独立产出 JAR/sources/POM，运行依赖仅 fastutil。CI 路径覆盖两个子项目；未创建空壳其他版本项目，jvmDowngrader/GTNH 验证留给实际移植。 |

**正确性与兼容回归**：

- 提交入口在提取材料之前拒绝 simulation/BLOCKED。普通 forced 包装通过 final wrapper 的 `metadataSource` 继承来源；不放宽任意 foreign plan 的身份校验。
- 合同签名冻结四张材料/任务表；缓存后任务向量改变时返回 `PLAN_IDENTITY_CHANGED`，拒绝把旧调度套用到新 DAG。
- 修复 dispatch 在 `accepted=Long.MAX_VALUE` 时的计数溢出，以及无限存储总量减法失败后破坏内部状态的问题。
- 保留 NBT schema、批量盘 v0/v1、损坏/cutoff/unresolved、`ae2:i`/`ae2:f` 旧键类型、UTF/枚举顺序与网络版本前缀 `3/`。

**最终验证**：启用 AppBot，以 `clean build sourcesJar` 加两个子项目的 POM/Gradle metadata 生成任务执行；使用 `--offline --no-build-cache --configuration-cache --console=plain` 及仓库外 `phase4-cached-useless.init.gradle`，避免本地 Curse Maven 缓存缺包。该脚本仅对声明 Useless 依赖的项目替换本机已缓存的同版本 JAR，未修改仓库依赖版本。最终 `BUILD SUCCESSFUL in 1m 11s`，27 个任务中 26 个执行，配置缓存写入成功。

- 测试 XML：`core/build/test-results/test` 104 项全通过；`build/test-results/test` 1328 项、1324 通过、4 跳过。合计 **1432 项、1428 通过、4 跳过、0 失败/错误**。跳过项仍为性能基准和三个 AE2LT API 条件用例。
- 模组 `build/libs/neoecoae-21.2.6.jar` 为 5,879,559 字节，SHA-256 `ac91e201254f6682ba65c5fb4d576c20200436e00d805ba4af47b502c797a877`；元数据、mixin、KubeJS 插件声明、语言、指南与内嵌依赖均存在。
- 独立 `core/build/libs/neoecoae-core-21.2.6.jar` 为 277,898 字节，SHA-256 `a3c3b48c829442b6f9d7d333476a40f923444ba66b98f287f18d13f2e99c55fd`。146 个 core class 与模组内对应条目逐字节相同；所有 JAR 无重复 ZIP 条目。core 字节码不含 MC/NeoForge/AE2/LDLib/Mojang 引用，运行时发布依赖只有 fastutil。
- 模组与独立 core 源码包各含全部 78 个 core 源文件；POM 与 Gradle metadata 均无重复 core JAR 依赖。`git diff --check` 和冲突标记扫描通过。此后按用户要求分批本地提交，未推送。

**分批本地提交（2026-10-16）**：从 `cfb40d0e` 起按功能拆分，标题与说明均为中文，相关回归测试跟随对应实现。每批检查明确暂存路径与 `git diff --cached --check`；图端口分配器按移动前后路径一起核对。提交时仅清理 `ECOCraftingServiceClusters` 一处空行的行尾空格，其他源码及构建文件与提交前快照字节一致。未重新运行整套测试，沿用上面的完整验证结果。

| 顺序 | 提交 | 内容 |
|---|---|---|
| 1 | `8fd6b5e8` | 统一精确数量算术并保留旧类型兼容 |
| 2 | `810b05b7` | 抽取持久化与精确数量传输编解码 |
| 3 | `d85178c1` | 抽取规划器通用算法并复用规划会话 |
| 4 | `8e9c680b` | 收敛执行合同并抽取调度决策 |
| 5 | `aeb426ca` | 迁移图布局与客户端投影到通用核心 |
| 6 | `ac57dca8` | 统一合成下单校验并修复增量同步基线 |
| 7 | `0d04927f` | 抽取精确存储引擎并整理存储适配 |
| 8 | `6af46ce9` | 抽取控制器与存储面板状态模型 |
| 9 | `1bb6649e` | 集中配置定义与语言生成数据表 |
| 10 | `99069e73` | 拆分核心库与1.21.1平台构建工程 |
| 11 | 本节所在文档提交 | 更新第4阶段实施与验证记录 |

**datagen 对照**：当前实现与合并 main 后的 HEAD `cfb40d0e` 分别在仓库外生成，使用相同的 `run/mods` 环境与 AppBot 编译开关。两次 Gradle 均报告 `BUILD SUCCESSFUL`；去除 `.cache` 后各 714 个输出文件，文件名与内容逐字节完全相同。输出保留在 `%TEMP%/neoecoae-phase4-datagen/{20261016,head}`，没有覆盖仓库生成资源。与旧的已提交资源之间存在历史差异，因此未整体刷新生成文件。环境中的 AppliedSoul JEI 插件缺少 `IngredientConverter` 警告两次均存在，不是本次重构引入。

**构建入口**：根目录 `test --tests '*NEConfigCommentLangTest'` 保持平台筛选，并独立验证完整 core；筛选不存在的测试仍失败。单独筛选 core 使用 `:core:test --tests ...`。根目录 `runClient --args=... --dry-run` 配置通过；开发启动把平台与 core 输出归入同一个 mod。平台与 core 的 POM/Gradle metadata 分别使用 `neoecoae` / `neoecoae-core`，平台不附加重复的 core 依赖。

**边界**：当前完成的是本分支第 4 批的代码整理、可复用 core 与 1.21.1 适配。其他 MC 版本、AE2 分支 API、旧版本 UI 框架与 Java 降级仍需在目标工程验证。尚未进行游戏客户端与真实存档验证；datagen、编译和 JVM 测试不能替代这些验证。

---

### 10.13 生成资源整体刷新（2026-10-16）

**范围与保护**：按用户要求单独处理生成资源漂移。起点为 `71d42766`；工作区已有 `gradle.properties` 将版本号改为 `21.2.6-core-branch`，本轮保留该修改并排除在提交外。刷新前的生成资源、文档与版本配置备份在仓库外 `%TEMP%/neoecoae-resource-refresh-20261016-210039/before.zip`。

**生成环境**：所有运行目录与输出均在上述临时目录内，未增删原 `run/mods`。沿用已安装的可选集成，补充本机缓存的 Botania `454-SNAPSHOT`、AppBot `1.6.0-alpha.3` 与各环境所需依赖，启用 `-PenableAppBotIntegration=true`。

| 环境 | 单独安装的模组 | 输出文件数（不含 `.cache`） |
|---|---|---|
| Lightning | AE2LT `2.1.0-beta.5`、Thunderbolt `2.0.0-beta.4`；不装 Data Energistics | 728 |
| Data | Data Energistics `3.2.2`、Configuration `3.1.1`；不装 AE2LT/Thunderbolt | 740 |

AE2LT 与 Thunderbolt 的模组元数据明确禁止与 Data Energistics 共存；首次尝试由加载器拒绝，随后按表拆分环境。两套输出的 723 个公共非语言文件逐字节相同；5 份语言文件的公共 key 值一致，各自补入 Lightning/Data 的 4 个物品 key，合并后共 740 个文件。每套环境又从新的空输出目录复跑一次，728/740 个文件分别逐字节一致。

**生成器修复**：完整启用 AppBot 时复现 `Duplicate translation key cell_type.neoecoae.mega_mana`。`LanguageContent` 内另有 Mana/Data 物品和 MEGA Mana 的 11 条声明，与各集成自身的 `.lang()` / `addLang()` 重复。本轮删除共享表中的重复声明，以集成注册为唯一来源；未改动手写中文翻译。

**语言结果**：

| 文件 | 刷新前 key 数 | 刷新后 key 数 | 内容变化 |
|---|---|---|---|
| `en_us.json` | 947 | 948 | 新增 `computation.ui.parallel_full`；4 个 Mana 名称采用实际集成声明；整体按生成顺序排序 |
| `en_ud.json` | 646 | 948 | 新增 304 个、删除 2 个旧配置 key、更新 31 个值 |
| `unfinished/{lzh,zh_hk,zh_tw}.json` | 各 624 | 各 948 | 各新增 326 个、删除 2 个旧配置 key；同步现有翻译与英文回退 |

5 份文件 key 集合一致，Lightning/Mana/Data 的物品名称均保留。unfinished 的三个文件各有 195 条 `UNLOCALIZED:`，明确暴露手写翻译尚缺的条目；本轮未把英文回退冒充完成翻译。完整核对这些文件与手写翻译的对应关系，以及 en_us/en_ud 格式占位符的类型和数量。

**配方结果及实际影响**：本轮同步已有源码，未更改配方定义代码。

- 补齐批量物品盘的 4 个合成、3 个拆解配方和 4 个解锁进度；补齐 Mana 外壳注魔、3 个盘合成、3 个拆解配方及 3 个解锁进度。
- Data 外壳改用黑曜石锭与数据水晶；三个 Data 盘迁入 `integrated_working_station/`，每盘消耗 10 个对应组件，拆解同样返还 10 个。删除旧的 3 个无序合成配方与 3 个进度，避免旧的一组件配方继续生效。
- 无限存储组件按当前源码生成：能耗由旧资源的 1,000,000 改为 144,000，材料改为水桶、熔岩桶、64 圆石、1 个 256M 组件、各 8 个超导锭/处理器与 4 个水晶矩阵。这是修正旧资源与源码不一致带来的游戏内配方变化。
- 共新增 24 个、删除 6 个资源文件；已有资源中仅 5 份语言和 6 份配方/进度有内容修改。38 处仅换行或文件末尾换行不同的输出恢复原排版，全部 740 个 JSON 内容仍与合并结果一致。

**验证**：两套环境四次最终 `runData` 均成功；使用仓库外 init script 指定隔离运行目录、输出目录及同版本 Useless 缓存 JAR。`test --tests '*NEConfigCommentLangTest' jar -PenableAppBotIntegration=true --offline --no-configuration-cache` 通过（104 项 core 测试与 2 项配置语言测试，0 跳过/失败/错误）；另执行 `:core:jar` 成功。本轮没有重复运行 10.12 的平台全量测试。

740 个 JSON 均通过解析与重复键检查；14 个 Mana/Data 配方保留相应的 `mod_loaded` 条件，三个 Data 盘的十组件投入/返还数量一致。产物 `build/libs/neoecoae-21.2.6-core-branch.jar` 为 5,907,975 字节，SHA-256 `1e3d9b2e1dcbfa4ab577812a5431a144c256a9100f284386490bc7e6a82f39be`；全部 740 个生成资源与工作区逐字节相同，无重复 ZIP 条目，旧 Data 无序配方不在 JAR 内。运行日志仍有既有 AppliedSoul JEI 插件加载警告，两套环境均能完成生成。本轮验证范围为 datagen、JVM 测试和打包。

**分批本地提交**：`60988165` 修复可选集成语言重复生成并刷新语言资源；`1122a291` 刷新存储盘配方与解锁进度生成资源；本节随单独文档提交记录。每批核对暂存路径与 `git diff --cached --check`；配方按关闭重命名检测后的 36 个新增、修改、删除路径复核。未推送，已有版本号修改未纳入提交。

---

## 11. 与原审计报告的对齐（2026-10-16 更新）

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
| 一.1 数字格式化入口多 | ✅ | `formatExact` 仅按空值与显示模式选择 `AEKey.formatAmount` / `BigNumberFormatter` / `HostText`，没有独立数值格式化算法 |
| 一.2 clampToLong | ✅ | `bounded` 的非负数计算共用 `NEMath.clampToLong`，保留负数、负溢出与 null 的公开行为 |
| 一.3 BigInteger 网络编解码 | ✅ | 见 5.3 T2-3；图 DTO 字符串协议有意保留 |
| 一.4 AEKey 与 `Map<AEKey,BigInteger>` 的 NBT | ✅（本批范围） | 共享 AmountMapCodec/NBT adapter，Filter 与批量盘兼容旧键格式，见 10.12；特殊槽位编码按语义保留 |
| 一.5 精确数量类型 | ✅（兼容收敛） | PlannerAmount 统一算术；公开类型与存档兼容入口保留，见 10.12 |
| 一.6 图模型 | ✅（按投影语义） | 泛型规划图、网络 DTO 与客户端投影视图，布局算法迁入 core，见 10.12 |
| 一.7 线程作用域栈 | ✅ | 专用生命周期有意保留，见 5.3 T2-9 |
| 一.7 `setChanged(); markForUpdate();` | ✅（等价调用对） | NEBlockEntity 的等价调用对已收口；大型工作站走 AE2 基类，不能复用该方法；独立同步或带分支的路径保留 |
| 一.7 yRot、`executeIfPossible`、压缩链判等 | ✅ | `BlockStateUtil` 中只剩 1 处 `toYRot() + 180` |
| 二 集成工作站与大型工作站 | 部分 | 常量遮蔽、电力设置、`isValidScale` 已合并；`PowerDrawHelper` 与 `autoBuild` 不做（见 8.1） |
| 二 合成/计算系统网络开关 | ✅ | T3-1 |
| 二 三个集群计算器 | ✅ | T3-2 |
| 二 `NELogicalNetworkManager` | ✅ | T3-3 |
| 二 集群类 `addBlockEntity` 的 instanceof 分桶 | 未做 | 审计列出，但未纳入计划 |
| 二 `MultiBlockBuildController.Host` 访问器 | ✅ | T3-4 |
| 二 流体仓、散热口、并行核心 | ✅ | T3-5 |
| 二 AE2 / AdvancedAE mixin 成对 | 部分 | 方法体共用 `ECOExternalCpuMixinSupport`；IO 端口两份 mixin 保留（I0-1 结论：都必需） |
| 二 JEI / EMI | ✅ | 见 5.4 T3-9 |
| 二 集成存储盘族与升级卡循环 | ✅ | T3-10 |
| 二 注册链（NEItems / NEBlocks / NEBlockEntities） | ✅ | T3-11；过度抽象的部分已回滚（10.5） |
| 二 GUI 小件 | ✅（本批范围） | JSON 内联、颜色差异已确认；四个槽位子类的绘制、同步与交互职责不同，有意保留；面板状态编码归 T4-17；不存在的类已排除 |
| 三 `ECOStorageCell` 对 `BasicCellInventory` | 部分 | 已用 `ConfigInventory` 与 `IPartitionList`；剩余偏离是有意的（315 类型、封存、迁移、批量语义），见设计说明 T4-8 |
| 三 批量盘的父类初始化开销 | ✅ | protected 初始化开关跳过配置/升级/NBT 普通盘路径，保留公开继承与工厂，见 10.12 |
| 三 存储盘 tooltip 与拆解 | 部分 | `CellTooltips`、`CellDisassembly` 已抽出，tooltip 只构建一次；未改用 AE2 `BasicCellHandler` 本身 |
| 三 `StorageInterfaceFilter` 对 `ConfigInventory` | ✅ | ConfigInventory/IPartitionList 适配与旧 NBT 兼容，见 10.12 |
| 三 `ECOIOPortTransfer` 对 `transferContents` | ✅（按精确数量语义） | 统一 OperationMode 入口；供能、精确数量、回退及旧重载保留，见 10.12 |
| 三 `ECOCraftingCpuView.addAllSaturating` | 保留 | 与审计结论不同：AE2 `KeyCounter.addAll` 会溢出，保留（见 5.3 T2-11） |
| 三 `NEBlockEntityTicker` | 不删除 | 被 `NEBlockEntityBuilder.serverTicker` 使用，见 §12 |
| 三 `ECOAe2LtDirectDispatch` 反射 `getSendList` | 有意保留 | 可选适配器用反射预解析完整传输契约；直接依赖任一 mixin accessor 会改变未注入环境与测试的兼容条件 |
| 三 `clamp(float…)` 自写版本 | ✅ | 两处调用改用 `Math.clamp`；上下界为 0.15 / 2.5 与 0.15 / 1.5 |
| 三 `MultiBlockInfoWrapper` 手工 ModularUI | ✅ | 改用 `ECOModularUI.create(root)` |
| 三 fork 类对照 AE2 19.2.18 | ✅ | T4-11：两个都保留 |
| 四 执行模式、规划结果、模式身份、DTO、推送守卫、并行提供者、存储透传、规划会话样板、两个 C2S 包、mixin 业务逻辑 | ✅（按兼容边界） | 实际逻辑已收敛；公开 DTO/旧入口、不同身份相等域及各阶段守卫有意保留，见 10.12 |
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
| 局限：`ECOMachineInterfaceBlockEntity`、`ECOCraftingPatternBusBlockEntity`、三个执行核心类 | 已通读 | 通读结论与疑似 bug 在 §14；正确性修复需另行验证 |
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

**后续收尾结果**（10.6）：直接可做项已完成；负数公共契约保留，专用 ThreadLocal 生命周期保留；AE2LT 反射边界与四个槽位的不同职责有意保留。上表是开工前的历史复核，不再代表当前待办。

## 13. 只读审计结果汇总（2026-10-14）

设计说明与通读结论见 §14。本节只记录对审计结论的修正。所有结论都是只读产出，未修改源码。

**对原审计结论的修正**
- `common.yes` / `common.no` 不是死 key，有动态拼接引用（`ECOCraftingGraphScreen:677`）。
- `NEBlockEntityTicker` 不是死代码，被 `serverTicker` 使用 10 处。
- `ECOCycleItemList`、`ExactKeyAmount` 已使用 `ExactMapSync`。
- `ECOStorageCell` 已使用 `ConfigInventory` 与 `IPartitionList`，剩余偏离是有意的。
- `StorageInterfaceFilter` 仍是自建 HashSet 加 `configured` 标志，没有用 `ConfigInventory`，审计结论成立。
- `CraftConfirmMenuMixin` 为 946 行，`CraftingServiceMixin` 为 814 行（审计原文为 969 和 824）。
- 审计说"游标 2 份"，实为 `ECOProviderCursor`（仅调度器使用）与两个同构环（bus 的 `roundRobinIndex`、worker 的 `nextFreeThreadIndex`），后两者才是合并候选。
- `ECOExecutionRequirement` 与 `ExecutionMode` 基本一一对应，但 `Schedule.Type` 与 `CycleExecutionDisposition` 语义不同，不宜全部合一。

**新发现与死代码**：详见下方 §14 各节的 (a) 可删与 (c) 疑似 bug，此处不再重复列出。

## 14. 第 4 批设计说明

> 原文件 `ARCHITECTURE_T4_DESIGNS_ZH_CN.md` 已并入本节。内容只做了两类改动：标题层级下调一级；指向原文件"第 7 节"等内部位置的引用改为指向本文件。各组设计都需要用户确认后才能动手。

### T4-3 执行模式与规划结果载体

> 实施增量见 10.10、10.11：S0 / S1 已推进，S2 / S3 按兼容与延迟计算要求调整实施；旧枚举保留 deprecated 入口，`executable()` 保留，S4 尚未实施。下列形状与估算属于设计快照，以增量记录为准。

#### 1. 现状

**执行模式（4 类，语义并不同轴）**
- `ExecutionMode`（`planner/result/ExecutionMode.java`）：计划级，NATIVE / PHASED_DAG / ORDERED_CYCLE / DYNAMIC_CYCLE / BLOCKED。约 25 处引用。NBT 以 `name()` 持久化（`ECOExecutionPlanNbtCodec:31`，读取在 `:108`）。
- `ECOExecutionRequirement`（同包，127 行）：计划级分类 NONE / ORDERED / DYNAMIC / BLOCKED。约 20 处引用。与 ExecutionMode 基本一一对应，但 NONE 有两种去向：SUCCESS 且有组件时建成 PHASED_DAG 计划，否则为 NATIVE。`classify` 每次调用分配 HashMap、HashSet、IdentityHashMap。未持久化。
- `ECOExecutionSchedule.Type`（`ECOExecutionSchedule.java:40`）：阶段级 DAG / CYCLE / DYNAMIC_CYCLE。约 56 处引用。NBT 以名字持久化（`NbtCodec:151`）。
- `CycleExecutionDisposition`（15 行）：组件级，5 个值，其中 NOT_REQUIRED、STOCK_SATISFIED 不是执行态。约 26 处引用。未持久化。

审计说的"4 套"成立，但只有前两者是同一概念。

**状态枚举（不在本组范围）**：`PlanningStatus`（11 个常量）、`CyclePlanningStatus`、`CycleExternalDemandStatus`（含 OVERFLOW）、`CycleSolveStatus` 等均有 SUCCESS / UNSUPPORTED 类常量。

**规划结果载体**
- `ECOPlanningResult`（class，203 行）：独立持有 `planningId`、`executionRequirement`、`executionPlan`、`executionPlanError` 四个字段（`:32–35`）。`executionContract()`（`:191–202`）每次拼装合同。
- `ECOExecutionContract`（设计时 record，37 行）：`planningId`、`planSignature`、`mode`、`executionPlan`、`error`。设计时 main 中没有任何 `error` 读取，只有构造；`executable()`、`phased()` 无仓内 Java 调用方，但双语 API 文档使用 `executable()`，本轮保留它。`mode` 与 `executionPlan.mode()` 重复，没有一致性校验。
- 注册表（`api/me/planning/ECOPlanningResultRegistry.java`）：
  - `SubmissionMetadata`（`:433`）、`activeSubmissionMetadata`（`:192`）、`RecoveredSchedule`（`:479`）、`recoverSchedule`（`:249`）：全仓零外部调用，是死代码。
  - `RecoveredExecutionMetadata`（`:472`）：只被 `resolveExecutionPlan` 使用。
  - `SubmissionAlias`（`:443`）、`Entry`（`:448`）：各自带 `planningId` 与 `executionPlan`。
- 字段名漂移：同一语义在三处叫 `error` / `executionPlanError` / `rejectionReason`。`rejectionReason` 是注册校验原因（如 SCHEDULE_EMPTY），不是计划错误，不能直接合并。
- `ExecutingCraftingJob.java:54` 只 import 了 `ExecutionMode`，未使用。

**2026-10-16 更正：非 SUCCESS 含环结果确实可能返回 NATIVE 诊断合同。** 旧构造器对 `status != SUCCESS` 直接设置 `executionPlanResolved = true`；10.11 的缓存实现通过 `needsExecutionPlan()` 保留这一诊断行为。因此分类为 ORDERED / DYNAMIC 的非成功结果跳过构建、没有 executionPlan，`executionContract()` 会返回 NATIVE；不能据此认定没有循环执行需求。
- SUCCESS 结果实际进入构建时，ORDERED / DYNAMIC 构建失败或阶段为空才会转为 BLOCKED；分类已经是 BLOCKED 的非成功结果也维持 BLOCKED。
- `ECOBigOrderController` 仅在 SUCCESS 后启动子任务，注册表的提交别名也要求 SUCCESS。10.11 已在 `resolveContract` 的提交投影拒绝非成功循环，在 CPU 直接入口拒绝 simulation / BLOCKED，外部 CPU hook 也有真实登记路径回归；不能把原始诊断合同模式当作提交许可。
- S0 锁定分类、延迟构建与失败行为；10.11 的边界修复不改变普通纯 DAG 的缺料强制开工。普通循环强制包装改变完整计划身份后的元数据传递仍需复核。

#### 2. 目标形态

- **保留**：`ExecutionMode`（唯一计划级模式，常量名不变）；`ECOExecutionSchedule.Type`（阶段级）；`CycleExecutionDisposition`（组件级，不并入 ExecutionMode）；`ECOExecutionContract`（唯一的规划结果交接载体）。
- **删除与兼容**：移除 `ECOExecutionRequirement` 中独立的分类算法，统一由 `ECOPlanningResult.classifyExecutionMode` 返回 ExecutionMode，旧枚举仅保留 deprecated 委托入口。组件判断迁为 `CycleExecutionDisposition` 的方法。另删注册表的 `SubmissionMetadata`、`RecoveredSchedule`、`activeSubmissionMetadata`、`recoverSchedule`，以及合同的 `phased()`；保留公开 API 文档使用的 `executable()`。
- **改形**：`ECOPlanningResult` 正常解析只缓存一个惰性 `ECOExecutionContract`；`planningId` 与诊断初始分类单独保留，避免只读诊断触发签名计算；签名不可用异常以 `resolutionError` 承载（10.11）。注册表的 `RecoveredExecutionMetadata`、`SubmissionAlias`、`Entry` 内部收敛留给 S4，保留 `rejectionReason`。

#### 3. 迁移步骤（每步可独立编译、测试）

- **S1 删死代码（10.10 已实施）**：`ECOPlanningResultRegistry.java` 的 4 处；`ECOExecutionContract.java` 的 `phased()`；`ExecutingCraftingJob.java` 的死 import。保留 `executable()`。测试：`ECOPlanningResultRegistryTest`、`ECOExternalCpuSupportTest`。
- **S2 统一分类（10.11 已按兼容目标实施）**：旧枚举保留 deprecated shim，仓内调用迁移到 ExecutionMode / requiresCycleExecution；分类算法只有一份。测试：`ECOPlanningResultRegistryTest`、`FeedbackRouteRegressionTest`、`UselessSmartDoublingPlansMixinTest`。
- **S3 ECOPlanningResult 合同缓存（10.11 已按延迟计算要求实施）**：`executionContract()` 改为读缓存，错误读取保持原先的延迟构建与诊断行为；`planningId()` 不强制生成合同。`ECOExactCraftingPlan`、`ECOCraftingPlannerService` 的调用点不变。
- **S4 注册表内部收敛**：`SubmissionAlias`、`Entry`、`RecoveredExecutionMetadata` 改存合同，删重复的 `planningId`。`CraftConfirmMenuMixin.java:832` 不变。

#### 4. 行为与兼容

- **热路径**：`classify` 的一次性 HashMap 计算保持不变，它在规划时执行，不在每 tick。合同必须惰性生成。若构造 ECOPlanningResult 时即调用 `PlanIdentity.of(plan)`，每次规划会多一次签名计算。枚举本身无分配。
- **NBT**：ExecutionMode 与 Schedule.Type 的常量名不得改。ECOExecutionRequirement 未持久化，删除不影响存档。
- **网络**：已检索 packet / payload，这些类型都不在协议里，无变化。

#### 5. 测试计划

- **S0 锁定测试（合并前）**：非 SUCCESS 且含环的结果，当前返回的 mode 与 cycleExpected；以及 ORDERED/DYNAMIC 构建失败时合同为 BLOCKED。
- **classify 表驱动**：NONE / ORDERED / DYNAMIC / BLOCKED 各一例，覆盖 CycleExecutionDisposition 的组合。
- **合同不变式**：BLOCKED 必须有 error；有阶段时必须有 plan；`contract.mode == plan.mode()`。
- **NBT 往返**：每个 ExecutionMode 与 Type 常量写读一致；用旧存档字符串样本验证可读。
- **现有回归**：`ECOPlanningResultRegistryTest`（约 36 处引用）、`ECOExternalCpuSupportTest`（约 29 处）、`ECOExecutionScheduleSccTest`、`ECOExecutionRuntimeDagStreamingTest`、`ECOExactOrderTest`。

#### 6. 预计行数

删除约 90 行，新增约 15 行，净减约 75 行。估计值，未逐行核实。

#### 7. 风险与需用户再决定的点

1. **合同是否需要单独表达"有环"**
   - A 保留现状：合同只用 mode 表达，cycleExpected 布尔保留在注册表中。行为零变化。
   - B 把"有环"编码进合同（如新增字段）：合同自洽，但会改变调用方读取方式，需补测试。
2. **"4 个执行模式枚举合一"的范围**
   - A（推荐）：只合并 ExecutionMode 与 ECOExecutionRequirement，保留 Schedule.Type 与 CycleExecutionDisposition。少一个类型，改动约 50 行。
   - B：四者全部并入一个枚举。需加入 NOT_REQUIRED、STOCK_SATISFIED 等非执行态，或改动持久化字符串（需双读兼容），约 80 处改动，语义混杂，不建议。
3. **求解状态枚举是否纳入 T4-3**：审计 §6 原文写"执行模式枚举、求解状态枚举各合一"，本设计只列执行模式。
   - A 不纳入：本设计成立。
   - B 纳入：需另出设计，涉及 `PlanningStatus`（11 个常量，有 UI 文案）与 cycle 包的 `CycleSolveStatus`。
4. **error 与 rejectionReason 是否合并**
   - A 保持分离：校验原因与计划错误各自保留，多一个字段，信息不丢。
   - B 合并进合同 error：字段少，但校验原因（如 SCHEDULE_EMPTY）会被当作计划错误对外暴露，改变诊断文字。

#### 8. 不做的部分及理由

- 不把 ECOPlanningResult 整体并入合同：前者承载 trace、cycles、精确物料与诊断，合同只是执行交接，合并会让交接对象膨胀。
- 不改 ExecutionMode、Schedule.Type 的常量名：存档兼容。
- 不合并 CycleExecutionDisposition 与 ExecutionMode：两个非执行态会污染计划模式。
- 不改 classify 的溢出安全计数算法：属于正确性逻辑。
- 不处理状态枚举的重复常量（见 §14 中 T4-3 风险第 3 项）。

---

### T4-6 / T4-7 网络包与 mixin 业务

#### 1. 现状（已核实）

- `ECOPayload`、`DeltaSyncChannel` 均不存在（源码无任何引用）。
- C2S 包：
  - `network/ECOBigOrderStartC2SPacket.java`，ID `neoecoae:big_order_start`；
  - `network/ECOForceCraftStartFlagC2SPacket.java`，ID `neoecoae:force_craft_start_flag`。
  - 两者线格式都是 `VarInt containerId, Boolean`。前三步校验（菜单类型、containerId、stillValid）相同，之后动作不同：BigOrder 调 `startBigOrder(forced)`，ForceFlag 调 `setForceCraftStart` 后 `startJob()`。"校验相同"只成立于前三步。
- 注册：`network/ECONetwork.java:18–23`；协议版本串 `"3/" + modVersion`（`:29`）。
- 发送方：`client/gui/crafting/ECOCraftConfirmScreen.java:364–376`（371 行发 BigOrder，375 行发 ForceFlag，由私有 `usesBigOrderSubmission` 决定）；`mixins/client/ae2/CraftConfirmScreenMixin.java:44–52` 始终发 ForceFlag。
- 节流增量同步：`TerminalExactAmountSync.collect`（19–46）与 `CraftingCpuMenuMixin.onBroadcastChanges`（约 185–210）是同一模式（节流 + busy + MapDelta + 首次全量）。`PatternPreviewSync.tick`（187–205）是脏槽位模型，只共享 `UPDATE_INTERVAL`。审计说"写了 3 遍"，实为 2 遍相同逻辑加 1 遍仅节奏相同。
- `ECOCancellation` 已是规划器取消参数类型。未复用的是 `ECOBigOrderPlanner.begin` 内的内联 lambda 与 `search` 循环检查，以及 `ECOPlanningService.checkpoint()`。`CraftingCalculationMixin.neoecoae$checkpointExternal` 语义不同。
- 会话样板：`ECOPlanningService.begin`（29–31）、`ECOBigOrderPlanner.begin`（15–17）、`CraftingCalculationMixin`（106–111）都是 `ECOPlannerInventory.capture(grid)` + `createSession(...)`，参数一致。
- 行数（已核实）：`CraftConfirmMenuMixin.java` **946 行**（审计写 969）；`CraftingServiceMixin.java` **814 行**（审计写 824）。
- `CraftConfirmMenuMixin` 的投影循环位于 `neoecoae$applyPlannerDiagnostics`（528–622）内的 554–620；辅助方法 `neoecoae$exactAmountFor`（880）、`neoecoae$materialFor`（887）只它一处使用。
- `CraftingServiceMixin` 集群遍历约 11 处（154、210、236、446、473、521、655、703、763、787、797）。

#### 2. 目标形态

- 新包 `CraftSubmitC2SPacket`：`VarInt containerId`、`SubmitMode mode`（`STANDARD` / `BIG_ORDER` 枚举，符合第 10 条"不用 boolean 表示含义"）、`Boolean forced`。handle 先做共享前置校验，再按 mode 检查对应能力并分派。
- 客户端 `usesBigOrderSubmission` 抽为纯静态函数；AE2 原屏固定发 STANDARD。
- 投影抽为 `crafting/display/ECOCycleItemProjection.project(ECOPlanningResult, CraftingGraphSnapshot)`。
- `ECOCancellation` 增加常量 `INTERRUPTIBLE`，替换两处内联检查。
- 会话样板抽为 `ECOPlanningSessions.open(IGrid, AEKey, ECOPlannerOptions)`。

#### 3. 迁移步骤

1. 新包与 `ECONetwork` 注册；删除两个旧包。
2. 改 `ECOCraftConfirmScreen` 与 `CraftConfirmScreenMixin` 的发送。
3. 投影纯移动（保持原写法）。
4. `ECOCancellation` 常量替换。
5. 会话助手。若 T4-5 同时把 `ECOBigOrderPlanner` 改为单例，先做 T4-5。
6. `CraftingServiceMixin` 非热路径遍历迁出。
7. 改写测试，交总策划统一编译。

#### 4. 协议与兼容

- 旧线格式 `VarInt, Boolean`。新 ID 建议 `neoecoae:craft_submit`，线格式 `VarInt containerId, VarInt mode, Boolean forced`。不得复用旧 ID，否则旧布局数据会被错解或解码失败。
- 单机：客户端与服务端同一 jar，无兼容问题。
- 多人：协议由 `3/版本号` 协商，版本不同会在连接时被拒；同版本号的不同构建不会被拒。新线格式建议前缀改为 `4/`。
- 日志 tag 按 mode 保留 `[big-order-submit]` 与 `[craft-submit]`。

#### 5. mixin 影响

- `CraftConfirmMenuMixin`：投影只抽方法体，注入点（startJob 的 742/792/854/867 行、broadcastChanges 的 WrapOperation）不变。`startBigOrder` 不移出，因依赖 result、`neoecoae$originalOptions` 等 mixin 状态。
- 时序约束：`CraftConfirmMenuForceStartMixin` 的 startJob HEAD 注入消费 pending 标记。STANDARD 路径必须保持 `setForceCraftStart` 先于 `startJob`。
- `CraftingServiceMixin`：注入点不变。`@Local` 捕获的 `latestChange`、`inserted`、`cpus` 必须留在 mixin；`@Unique` 字段 `neoecoae$computationClusters` 留在 mixin，助手以参数接收。注入依赖 AE2 的 LVT 局部名与方法描述符，移动不改变这些依赖。
- `CraftingCalculationMixin`：`<init>` RETURN 注入不变，只替换 106–111 样板。

#### 6. 测试计划

- `network/CraftStartPacketTest` 改写为 `CraftSubmitPacketTest`：STANDARD 用 inOrder 钉住 `setForceCraftStart → startJob`；BIG_ORDER 验证 `startBigOrder(forced)` 且 never `startJob`；菜单不符、stillValid=false（closeContainer）、非 ServerPlayer 无副作用；能力缺失写 INCOMPLETE_PLAN。
- `network/ECONetworkVersionTest`：补新前缀断言。
- 新增 `CraftSubmitModeTest`：不可表示 → BIG_ORDER；forced + MISSING_ITEMS + 循环非空 → BIG_ORDER；其余 STANDARD。
- 新增 `ECOCycleItemProjectionTest`：纳入规则（totalNetOutputKnown 或 exactMissing>0）、seed 行、seedParallelism 缺省 -1、顺序。
- 新增 `CraftingServiceClusterTraversalTest`：mock NEComputationCluster，验证 onGetCpus 仅代表节点补 fake CPU。
- 应保持通过：`ECOPlanningServiceTest`、`ECOBigOrderPlannerTest`、`ExactMapSyncTest`、`PatternPreviewSyncTest`。

#### 7. 预计行数（估算）

- 包：约 -28；`ECONetwork` 约 -2。
- 投影：`CraftConfirmMenuMixin` 约 -75，新类约 +85。
- `CraftingServiceMixin`：非热路径约 -25，助手约 +30。
- 会话助手 +12、三处 -8；`ECOCancellation` 约 -7。
- 合计约 -20 至 +10；两个 mixin 合计减少约 100 行。

#### 8. 风险与需用户决定的点

1. **协议前缀**：改 `4/`，则同版本号的开发构建被拒（预期）；不改，则同版本不同构建可能静默错解。建议改。
2. **模式由谁决定**：
   - A 客户端决定（现状）：保持行为。
   - B 服务端决定（包只带 containerId + forced）：删除枚举，但要在服务端复刻 `usesBigOrderSubmission`；AE2 原屏点击在不可表示或缺循环时会改走大订单，属于行为变化。建议 A。
3. **DeltaSyncChannel**：
   - 建立：合并两站点，净行数约 0 至 +15，收益是一致性。
   - 不建立（建议）：只保留现有 MapDelta / ExactMapSync 共享。两站点 reset 语义不同（CPU 站点多 map，switched 时清空并取消通道），共享需要布尔或枚举参数。
4. **ECOPayload**：建立约 +20 行，只省 5 个 `type()` 覆写，净收益约 0。建议不建，只把菜单守卫抽为包内静态方法。
5. **日志 tag**：保留（可 grep）或统一为 `[craft-submit] mode=`（需确认无人依赖旧 tag）。
6. **取消异常文本**：统一为 "ECO planning request cancelled"，或两处各保留（只影响日志）。
7. **CraftingServiceMixin 遍历**：全迁，或只迁非热路径。建议只迁 describe、updateCPUClusters、findSuitable 等；tickClusters、onInsertIntoCpus、onGetRequestedAmount 留原位（第 8 条热路径规则）。

#### 9. 不做及理由

- `ECOPayload` 通用基类：共同代码只有 `type()` 覆写，且注册 ID 须字面量（第 10 条）。
- `PatternPreviewSync` 并入增量通道：模型不同。
- `startBigOrder` 移出 mixin：依赖 mixin 状态，移动需大量参数。
- 循环改 Stream、为投影建索引：第 8 条禁止；索引属于另行评估的性能改动。

---

### T4-8 存储对齐 AE2

路径相对 `src/main/java/cn/dancingsnow/neoecoae/`。AE2 对照基于 19.2.18 源码。

#### 1. 现状（已核实）

- **StorageInterfaceFilter**（`impl/storage/StorageInterfaceFilter.java`）：自建 `HashSet<AEKey>` 加 `configured` 标志（`:17–18`，判定在 `:34`）。语义与 `IPartitionList` 等价（空表放行，非空即白名单）。持久化为 `CompoundTag`，键 "0"–"26"，每项是 `toTag` 加 `#t`。**未使用 ConfigInventory**，这是审计结论成立的部分。
- **ECOStorageCell**（`impl/storage/ECOStorageCell.java`）：**已经使用** `ConfigInventory` 和 `IPartitionList`（`:17–18`、`:50`、`:86`）。方法与 AE2 `BasicCellInventory` 逐一对应，但 AE2 构造函数为 private，无法继承。有意偏离：类型上限 63 不存在（`NECellTypes` 中为 315）；无 EQUAL_DISTRIBUTION 分支；有封存、迁移 API、批量 defer 持久化。审计把它列为"重造轮子"，需要修正为"已对齐，剩余偏离是有意的"。
- **字节计算**：`StorageByteAccounting` 已存在，被 Universal 盘、无限引擎、HostStatistics 使用。**它没有单测**。ECOStorageCell 仍自算 `getUsedBytes`、`getRemainingItemCount`、`simulateInsertForMigration`（与 `getUnusedItemCount` 重复）、`ceilDivide`。T0-10b 已完成向上取整。
- **批量盘**（`integration/megacells/backend/ECOMegaLongBulkStorageCell.java`，684 行）：覆盖约 21 个方法。父类构造（约 `:70–106`）仍会解析 STORAGE_CELL_INV，调用 bulk 覆盖的 `getConfigInventory`（每次新建 ConfigInventory，见 `ECOMegaLongBulkStorageCellItem:42–69`），并建 partitionList。partitionList 对 bulk 只在继承的 `simulateInsertForMigration` 中使用，属于不可达路径。继承的 `getMigrationStacks`、`migrationEntries`、`getMigrationAmount` 读的是父类空 map，目前只因 `isInfiniteStorageEligible()` 返回 false 而不可达。
- **IO 端口**（`impl/storage/transfer/ECOIOPortTransfer.java`）：insert、extract、poweredInsert、退回与 AE2 `IOPortBlockEntity.transferContents`（约 `:325–377`）一致。ECO 另有 5 键/tick 轮询队列、整键传输（无 AE2 的操作预算）、创造源过滤。AE2 方法为 private，无法复用。4 参、5 参重载仅测试使用（生产只走 6 参，`ECOStorageInterfaceTransfer:52`）。
- **批量优先**：`CombinedStorage.insert` 两遍扫描（`blocks/entity/storage/ECOStorageInterfaceTransfer.java:107–116`）；网络挂载走 `StoragePriority.mountPriority`（`ECODriveBlockEntity:229`）。两条路径不同，接口路径没有 host priority。
- **BE 透传**（`blocks/entity/storage/ECOStorageSystemBlockEntity.java`，910 行）：直接转发控制器 15 个（mega 9，infinite 6）。其中 8 个无外部调用（mega 7 个，加 `canInsertIntoInfiniteDomain`）。`getSelectedEcoMegaBulkCell/Page` 仅 BE 内部使用。另有约 20 个包级字段访问器。
- **AEKey 编解码**：批量盘 writeKey/readKey（约 `:523–543`）写 `{item, components}`，不是 AE2 格式。`ECOInfiniteStorageData`、`ExecutingCraftingJob` 已普遍使用 `fromTagGeneric`。

#### 2. 目标形态

- Filter：内存模型改为 `ConfigInventory.configTypes(27)` + `IPartitionList`，changeListener 失效缓存。`encode`、`decode`、`with`、`get` 保留为 RPC 线格式。
- ECOStorageCell 与批量盘的字节公式全部委托 `StorageByteAccounting`，新增 long 版。
- 批量盘不再经过 partition 与 upgrade 初始化（见 8.3）。
- IO 端口：boolean 参数改为 `OperationMode` 枚举，删除 4、5 参重载，保留轮询队列。
- BE：删除 8 个死转发，其余改为直接访问控制器。

#### 3. 迁移步骤（每步独立可编译、可测）

1. `StorageByteAccounting` 增加 long 饱和版 `usedBytes`；ECOStorageCell 与批量盘改用，删除两份 ceilDivide 和 simulate 中的重复计算。
2. `ECOIOPortTransfer` 换 `OperationMode`，删 4、5 参重载，改 2 处测试。
3. 删 BE 死转发，活跃转发改为包级控制器访问器。
4. Filter 内存改为 ConfigInventory + IPartitionList，持久化格式暂不变（8.1a）。
5. 批量盘按 8.3、8.4 处理。
6. `acceptsStorageResource` 改为 IPartitionList 判定，缓存 Predicate 字段。

#### 4. 存档兼容

**Filter**：字段 `storageResourceFilters` 为 `@Persisted CompoundTag`（`blocks/entity/ECOMachineInterfaceBlockEntity.java:98–99`）。
- 读取时逐个处理 "0"–"26"，跳过非 compound 和越界键。每项用 `AEKey.fromTagGeneric`，未知类型返回 MISSING_CONTENT 占位而非 null。旧数据没有 `amount` 字段，不能走 `GenericStack.CODEC` 或 `readFromTag`，否则解析失败。
- 读档样例：
  ```
  {"0": {"#t":"ae2:i","id":"minecraft:stone"},
   "13": {"#t":"ae2:f","id":"minecraft:water"},
   "26": {"#t":"<未安装附属的类型>","id":"<key>"},
   "5": {}}
  ```
  期望：stone、water 放行；dirt 拒绝；"26" 与 "5" 无法解析时仍保持限制，不得整体放行。

**批量盘**：沿用 `ECOMegaLongBulkStorageCellTest:385` 的输入（v0 entries 含 `{item:"minecraft:iron_ingot", units:91}` 与 `{item:"missing:unknown", units:500}`）。新增 v1 样例：`{version:1, entries:[{item, components:{}, units, definition:[{item, components:{}, factor}]}], cutoffs:[...], unresolved:[...]}`，factor 以 `MegaBulkUnits.compile` 的结果为准。

**写入**：v1 格式不变。

#### 5. 行为与热路径

- `StorageByteAccounting.usedBytes` 现为 BigInteger 版。直接用于 insert 路径会每次分配对象，违反第 10 条热路径规则，必须新增 long 版。
- `remainingInsertAmount(long)` 在 used > total 时返回 0，而 ECOStorageCell 返回未用余量。仅超容状态下结果不同，需要测试锁定。
- ECOIOPortTransfer 每 tick 对全部可用键调用 filter（`:55`），`matchesFilter` 为 O(1)，不增加分配。
- `storageInterface::acceptsStorageResource` 每 tick 每接口分配一次，建议缓存。
- CombinedStorage 不宜直接改为预排序，否则提取顺序也会变。

#### 6. 测试计划

- 新增 `StorageByteAccountingTest`（目前**没有**单测）：long 与 BigInteger 版一致；超容边界；amountPerByte 为 0 时夹紧。
- `StorageInterfaceFilterTest`：增加第 4 节的读档样例；若采用 8.1b，增加 ListTag 往返测试。
- `ECOIOPortTransferTest`：替换 OperationMode 后全部用例通过。
- `ECOMegaLongBulkStorageCellTest`：v0、v1 读档，unresolved 与 cutoff 保持不变。
- 新增 ECOStorageCell 字节回归：固定 insert、extract 序列前后 getUsedBytes、getRemainingItemCount 不变。
- `ECOInfiniteStorageAccessTest:47–59` 通过反射读取字段名 `infiniteModeController`，BE 字段改名或删除时需同步修改。

#### 7. 预计行数（估算，未核实）

StorageByteAccounting +25；ECOStorageCell −20；批量盘 −25（8.3a 时约 −5）；ECOIOPortTransfer −12；BE −45；Filter 约 63→55；ECOMachineInterfaceBlockEntity +10。主代码净减约 80–100 行，测试约 +150 行。

#### 8. 风险与需用户决定

- **8.1 Filter 持久化格式**
  - (a) 保持 CompoundTag（推荐）：无迁移、无降级风险。对齐只体现在内存模型。
  - (b) 改为 ConfigInventory 的 ListTag，字段名不变，按 TAG_COMPOUND 与 TAG_LIST 区分。需手写 saveAdditional、loadTag，并核实 `@Persisted` 能否处理。降级到旧版本时会读不出。
- **8.2 无法解析的附属键**
  - (a) MISSING_CONTENT 占位：列表非空，仍然限制。GUI 显示缺失物品，原数据经 coApply 写回，重装附属后恢复。每次读档会打一条 AE2 LOG.error。
  - (b) 沿用 null 跳过并保留 configured 标志（与现状一致）：GUI 显示空槽。
- **8.3 批量盘父类初始化**
  - (a) 父类字段改为惰性初始化：改动小，但仍继承那些不可达的迁移方法。
  - (b) 抽出 abstract `ECOStorageCellBase`，ECOStorageCell 与批量盘共用。需同步改 `ECOStorageHostStatistics:254` 的 instanceof，diff 较大。推荐 (b)，待确认。
- **8.4 批量盘键格式**
  - (a) 保持 v1 `{item, components}`（推荐）：readKey 可改用 `AEItemKey.CODEC`（不用 `AEKey.CODEC`，否则 MISSING_CONTENT 会破坏 unresolved 保留），省约 10 行。
  - (b) 写 AE2 格式并升 version=2：旧版在 `version > 1` 处抛 IllegalStateException，降级即崩溃。
- **8.5 CombinedStorage 批量优先**：(a) 保留两遍扫描（推荐）；(b) 仅插入列表预排序，提取列表不变。二者都不并入 mountPriority。
- **8.6 StorageByteAccounting 位置**：留在 impl/storage，T4-15 再移入 core；或现在移入 core（满足白名单，但提前了 T4-15 的一部分）。
- **8.7 IO 端口轮询**：保留 5 键/tick 队列（推荐，测试固定其行为）；删除会改变大量键时的 tick 行为。
- **8.8 BE 活跃转发**：(a) 删除并暴露包级控制器访问器（推荐），同步修改 `ECOInfiniteStorageAccessTest`；(b) 保留。

#### 9. 不做的部分及理由

- ECOStorageCell 委托 AE2 `BasicCellInventory`：构造函数 private，且 ECO 的 315 类型、封存、迁移、批量语义 AE2 无法承载。
- CombinedStorage 并入 mountPriority：路径不同，接口路径无 priority 概念。
- 批量盘改用 AE2 键格式写入：同 8.4，降级会崩溃。
- RPC 线格式（per-slot CompoundTag）：客户端与服务端需同时改，收益低。
- IO 端口整体换为 AE2 `transferContents`：轮询与整键语义有测试约束。
- ECOStorageCell 改为 AE2 的"仅无容器时立即写"：`ECOCellMutationBatch` 依赖现有行为。
- "清空存储盘"的 4 份实现：T2-9 已确认保留。

---

### 通读审计：两个大方块实体

审计范围：`blocks/entity/ECOMachineInterfaceBlockEntity.java`（1,857 行）与 `blocks/entity/crafting/ECOCraftingPatternBusBlockEntity.java`（1,272 行），两个文件已完整通读。死代码判断基于覆盖 `src/` 全部文件的 grep。未运行 gradle，未修改仓库。

#### (a) 可删（已核实无调用方）

| 位置 | 内容 | 核实 |
|---|---|---|
| `MI:958` | `insertPatternSlot` 私有方法 | 全仓仅定义处 1 次命中 |
| `MI:758` | `onPatternBusInventoryChanged(bus)` 单参重载 | publisher 只调用双参版本 |
| `MI:680` | `getPatternInterfaceStack` | 仅定义处 |
| `MI:132` | `patternOrganizeAuxiliaryMoved` | 只写不读（`:1288` 自增，`:1469` 清零） |
| `MI:507` | `setPatternPreview` RPC | 仅定义；实际接收路径是 `network/MenuDataCodecs.java:48`。删除前需确认 |
| `MI:1005–1055` | `insertPatternFromPlayer` RPC 与 `tryInsertPatternFromPlayer` | 客户端无 rpcToServer 调用点。置信度中，删除前需人工确认 |
| `MI:1352` | `if (!task.prepare())` 分支 | `prepare()`（`:1721`）恒返回 true，分支不可达 |
| `MI:1243–1253` | 孤立 Javadoc（描述退款，压在 organizeIntoAuxiliary 的 Javadoc 上方） | 应属 refundConsumedPattern，属注释错位 |
| `PB:697` | `isDurabilityPattern` 私有静态 | 仅定义处 |
| `PB:837` | `updatePatternDetails()` 私有 | 仅定义处（catalog 内有同名方法） |
| `PB:865` | `requestPatternDetailsRefresh()` | 仅 BE 的转发方法调用 `catalog.requestPatternDetailsRefresh()`，无外部调用 |
| `PB:402` | `pushBatch(ECOBatchCraftingRequest, …)` 空实现 | 全仓无调用。外部字节码调用无法排除 |
| `PB:398–424` 中的部分转发 | `acceptVerifiedBatch` 等 | 两个 record 仍在用，保留 |
| `PB:443` | 三参 `calculateBatchOfferSize` | 只被四参版本调用，可内联 |
| 仅类内使用却标 protected 的钩子 | `MI:425`、`MI:470`、`PB:516`、`PB:997`、`PB:1017` | 全仓无子类覆写，可收窄或内联 |
| 未用 import | MI 的 `StringTag`、`java.util.Map`；PB 的 `Slot`、`UI` | 自动检查误匹配注释，已人工核实 |

#### (b) 可合并

| 位置 | 内容 | 判断 |
|---|---|---|
| `MI:1313` `refundConsumedPattern` ≡ `mixins/ae2/menu/PatternEncodingTermMenuMixin.java:106` `neoecoae$returnReplacement` | 两者都是 simulate、判断全额、MODULATE，仅 IActionSource 不同（`ofMachine(this)` 与 `ofPlayer`） | **强**。已核实两处结构相同，等价性需测试确认。建议抽成带 IActionSource 参数的 helper |
| `MI:1292` `canRefundConsumedPattern` 与 `MI:1313` 前半段 | 逐行相同 | refund 可写成 `canRefund && 提交` |
| `MI:1500` `AEItems.BLANK_PATTERN.stack(count)` ≡ `PatternRefund.blankFor` | count≥1 时等价 | 可复用 |
| `ECOLargeIntegratedWorkingStationInterfaceBlockEntity.java:398–425` `diskTerminalView()` 与 `PB:561–587` `diskTerminalView(long)` | 结构相同，仅库存与 revision 来源不同 | **中**。注意避免过度抽象 |
| `MI:883` `nextPatternContentRevision()` ≡ `PB:882` `incrementPatternContentRevision()` | 同一逻辑 | 可共享 |
| `MI` 内部 | 前缀和循环重复 4 处（`:399`、`:777`、`:799`、`:811`）；`onPatternBusInventoryChanged` 两个重载的前置校验（`:766–776`、`:788–798`）逐行相同 | 可抽 `offsetOf(busIndex)` 与公共校验 |
| `MI` 内部 | `grid.getService(IECOPatternStorageService.class)` + `instanceof PatternCatalog` 出现 6 次（`:484`、`:495`、`:940`、`:984`、`:1229`、`:1265`） | 抽 `patternCatalog()` |
| `MI:565–571` 与 `MI:616–622` | "抽取、add、drop 余量"完全相同 | 可抽 |
| `MI:258–265` 与 `MI:1446` | 距离与维度判断相同 | 直接复用 `isPatternInterfacePlayer` |
| `MI:1636` `isSourceUnchanged` 与 `MI:1767` | 语义相同 | 可合并 |
| `PB:810` `onChunkUnloaded` 与 `PB:817` `setRemoved` | 除 super 外相同 | 可合并 |
| `PB:850` → `updatePatternDetailsNow` → catalog | 双层转发 | 可压成一层 |
| `MI:339`、`:357`、`:370` | "noTarget" 条件重复三次 | 可抽布尔谓词 |

#### (c) 疑似 bug

1. **[中] `PB:323` `submit` 与 `integration/thunderbolt/dispatch/ECOThunderboltBatchBridge.drainFastPath`（`:28–50`）**
   - 循环中第 k 批 `batch.submit` 成功后，若第 k+1 次 `prepareAllocated` 抛 RuntimeException，异常会离开 drainFastPath，前 k 批已派发的 `accepted` 不会被计入返回值。调用方据此返回 REJECTED，但 Reservation 的 commit/refund 为空实现，submit 不可回滚。
   - 若调用方据 REJECTED 回滚输入，就会产生重复产出。
   - **未经测试确认**。建议先写单测：第二条车道准备时抛异常，确认返回值与实际派发量是否一致。
2. **[中低] `PB:246` `getAuxiliarySearchKeywords`** 全仓无调用，但其 Javadoc（`PB:239`）称终端只靠关键字找到配方。需确认磁盘内配方在搜索中是否可见。未验证。
3. **[低] `MI:302` `startNetworkPatternTransfer`**：tryAcquire 失败时已清空结果并置 `patternTransferPerformed=true`；`organizePerformed` 为真时，状态方法先返回整理结果，用户看不到"忙"提示。
4. **[低] `PB:362` `findExternalPattern`** 只查 `catalog.availablePatterns()`，不含磁盘配方；而 `PB:265` `getAvailablePatterns` 含磁盘配方。外部 fastpath 对磁盘配方返回 UNSUPPORTED_PROCESSING，不崩溃，但行为不一致。
5. **[低] `MI:1280`** `outcome == null` 只判空，未判 INSERTED。当前 `tryAuxiliaryPass`（`PatternCatalog.java:263–317`）只返回 INSERTED 或 null，属潜在隐患。
6. **[低] `PB:953` `getPageCount()`**：客户端 catalog 为空时，会把本地算出的页数写回 `@DescSynced` 的 activePages。未在客户端验证。
7. **[低，注释] `MI:1274`** 注释称磁盘拒绝重试会"永久失配"，但 DUPLICATE 分支（`MI:1157–1162`）会回收，注释结论需复核。

#### (d) 不动

- 原 `GuiModuleBoundaryTest.extractedMachinesDelegateLayoutToGuiComponents` 的布局位置限制已在 `45716d27` 撤销（10.8）。UI 归属仍按架构判断，测试只保留客户端依赖隔离检查。
- `CraftingInterfaceRenderingTest.java:245` 通过反射读取字段 `patternOrganizeInProgress`，不可改名。
- RPC 名 `openPatternPreview`、`actOnPatternPreview`、`quickMovePatternPreview` 被测试与客户端字符串引用（`CraftingPatternPreviewStateTest:130`、`CraftingInterfaceRenderingTest:111/190`、`gui/crafting/CraftingPatternPreviewState.java:168/252/278`）。
- `PatternRefundTest` 钉住 PatternRefund 的数量与清源规则。
- `ECOThunderboltBatchBridgeTest:74/91` 依赖 `bus.pushPattern(pattern, inputs, job)` 三参。
- `PB:1017–1172` TerminalPatternInventory 的槽位冻结、磁盘行经 mod 路径、隐藏行不可写，注释明确，属于有意设计。
- `PB:127–134` auxiliaryRevision 与 patternContentRevision 分离，注释论证了索引不收敛的问题。
- `PB:962` `if (activePages != pages)` 守卫，注释说明是为避免同步抖动。
- `PatternMigrationCoordinator.beginSlice` 返回 0 时 finish(unavailable)，是 lease 丢失的设计分支。

#### 优先处理（当前状态见 10.7）

1. **(c1) submit 异常路径丢失已派发批次（已修复并通过回归，10.7）。** 唯一可能导致物品重复、且外部可触发的路径。先写单测复现。
2. **(b1/b2) 退款逻辑统一。** 三处副本（MI 两个私有方法与 `PatternEncodingTermMenuMixin`）实现同一守恒逻辑，仅 action source 不同。统一后可避免日后只改一处。
3. **(a) 批量删除死代码与死分支。** 高置信、低风险，同时清理 import。`MI:507`、`MI:1005` 两个 RPC 删除前需人工确认。

---

### T4-4 / T4-5 模式身份、工作 DTO、推送守卫，并行提供者

路径相对 `src/main/java/cn/dancingsnow/neoecoae/`。

#### 1. 现状（已核实）

- **模式身份**：三类相等域不同，审计"3 种"成立。
  - `planner/identity/PlanIdentity.PatternIdentity`：嵌套 record，结构化相等，59 处引用。
  - `batch/ECOPatternIdentity`：原始 pattern 实例 + definition + provider 实例，18 处。
  - `fastpath/ECOFastPathPatternKey`：definition + generation，不含 provider，7 处。
  - 冗余确认：`fastpath/ECOFastPathKey` 第 21、33、76、87 行单独存 `reloadGeneration`，而 `patternKey` 已包含该值。
- **DTO**："5 个"成立，但集合需修正：`ECOBatchCraftingWork`、`ECOVirtualCraftingWork`、`ECOVerifiedVirtualExecution`（凭证）、`api/me/provider/ECOFastPathDispatchProvider.Batch`（公开 API）、`ECOStatefulBatchPlanner.PreparedBatch`。另有 `ECODurabilityBatchModel.ToolPoolBatch` 与 `ECOStatefulBatchCalculator.BatchContract` 字段完全相同且 1:1 转换。`ECOExactBatchPlanner.PreparedBatch`（BigInteger）与 `PreparedWork`（ItemStack）数值域不同。
- **推送守卫**：`ECOCraftingPatternBusBlockEntity` 第 404 行 `pushBatch` 为空实现，只被 `fastpath/ECOBatchCraftingRequest` 引用（全仓 2 个文件）。真实链路为 Bus `acceptVerifiedBatch`（398）→ Dispatcher（134）→ Worker `pushBatch`（210）→ Thread `pushBatch`（341）。`isIssuedBy` 在 worker 与 thread 层各出现一次；`isFullVirtualCraftingMode` 在 worker（249）与 thread（397）重复。
- **游标**："2 份"需修正：`crafting/execution/ECOProviderCursor` 是按 pattern 的 provider 环，仅调度器使用（`ECOCraftingTaskScheduler`）。同构的"环 + 记住下一个"实现是 `ECOCraftingPatternBusDispatcher.roundRobinIndex`（24、57–63）与 `ECOCraftingWorkerBlockEntity.nextFreeThreadIndex`（69、268–287，另在 532、586 行重置）。
- **并行提供者**：三层为 `api/me/provider/ECOParallelCraftingProvider`（接口，站内唯一实现 `LargeWorkstationPatternProvider`）→ `crafting/execution/batch/ECOParallelProviderAdapter`（record，只被 `api/me/ECOFastPathFacade` 使用）→ `ECOParallelCraftingProviders.Adapter`（嵌套函数式接口）。注册表 `register` 站内零调用，`find` 被 3 个类、5 处调用。
- **规划器**：`ECOExactBatchPlanner`（34、41）与 `ECOStatefulBatchPlanner`（73、92）每次 `new ECOBatchPlanner()`。`ECOBatchPlanner` 本身无状态，`ECOBatchDispatchPlanning` 已用静态字段。

#### 2. 目标形态

- **身份**：保留三类并各自写明相等域；删除 `ECOFastPathKey` 的冗余字段；`ECOFastPathPatternKey` 去掉未用访问器。
- **DTO**：新增 `CraftWork`（craftCount、三个 GenericStack 列表、craftingJobId、上限），替换 `ECOBatchCraftingWork` 与 `ECOVirtualCraftingWork`；`ToolPoolBatch` 直接用 `BatchContract`；公开 `Batch`、`PreparedBatch` 不动。
- **守卫**：`isIssuedBy`、`isFullVirtualCraftingMode` 只在 thread 落点检查；删除 bus 空 `pushBatch`。
- **游标**：不复用 `ECOProviderCursor`；bus 与 worker 两个同构环若语义可对齐，合并为一个私有小工具。
- **并行**：`ECOParallelProviderAdapter` 内联为 `ECOFastPathFacade` 的私有方法；注册表保留。
- **规划器**：两处改为 `static final` 实例。

#### 3. 迁移步骤（每步独立编译；gradle 由总策划执行）

1. `ECOFastPathKey` 删除 reloadGeneration 字段，改经 `patternKey()` 取值（cache 第 121、200 行）。
2. 删除 bus `pushBatch` 与 `ECOBatchCraftingRequest`；`ToolPoolBatch` 换成 `BatchContract`。
3. 规划器 4 处 `new` 改为静态实例。
4. 内联 `ECOParallelProviderAdapter`。
5. 删除 worker 层重复守卫；thread 中两处 `canRetain` 检查抽为一个私有方法。
6. 引入 `CraftWork`，替换两个 DTO 及 `ECOCraftingThreadWork` 的调用点。
7. （可选）环合并。

#### 4. 行为与兼容

- **存档**：身份与 DTO 均不落盘。`PatternIdentity` 由 `ECOExecutionPlanNbtCodec` 解码时重算，存档格式不变。
- **网络**：DTO 不进入 packet，无变化。
- **API**：`Batch`、`PreparedBatch`、`ECOParallelCraftingProviders` 保持不变。
- **热路径**：每批仍只分配一个 record；planner 每次 prepare 少 4 次分配；内联后 lambda 数量不变。
- **语义**：批量路径的 `MAX_BATCH_STACK_AMOUNT` 上限与 `consumeCraftingCoolant(batchSize)` 只属于批量路径，合并时必须保留。

#### 5. 测试计划

- **既有**：`ECOCraftingFastPathCacheTest`、`ECOBatchExecutorTest`、`ECOBatchDispatchIntegrationTest`、`ECOProcessingDispatchIntegrationTest`、`ECOExternalCpuFastPathTest`、`ECOThunderboltWorkstationBridgeTest`、`ECOVirtualCraftingDiagnosticsTest`、`ECOEAEPVirtualCraftingTest`、`ECOUselessBigIntegerDispatchTest`。
- **新增**：不同 generation 的 key 不相等；CraftWork 批量与虚拟上限各一例；注册表 `find` 缓存命中，适配器抛异常时返回 null。

#### 6. 预计行数

主代码约 −70 行（区间 −55 至 −80）；测试约 +60 至 +100 行。

#### 7. 风险与需决定的点

1. **CraftWork 合并方式**：A 单 record 加上限参数，省行数，但需逐个核对调用点的上限；B 保留两个 record，只抽出校验，更安全，省行有限。
2. **身份统一**：A 保留三类并文档化（推荐）；B 统一为 `PlanIdentity`，批量准入变为结构相等，且结构化计算分配较多，违反热路径规则；C 缓存键加入 provider，缓存命中率下降。
3. **删除 bus `pushBatch`**：A 删除，代码干净，但若 Crafting Tracker 经反射或 mixin 调用，会从静默 no-op 变为找不到方法；B 保留空函数，违反审计第 8 条。建议先确认外部依赖。
4. **游标**：A 只合并同构的两个环（推荐）；B 强行复用 `ECOProviderCursor`，需泛化 key 与元素类型，属过度抽象。
5. **并行注册表**：A 保留，作为 api 扩展点（推荐）；B 删除，站内无注册方，但 Javadoc 面向其他模组，属破坏性变更。另 `ECOProcessingPatternDispatcher:254` 仍用 `instanceof`，改用 `find` 会改变被适配提供者的行为，需单独决定。
6. **BigInteger 的 `ECOExactBatchPlanner.PreparedBatch`**：A 本组不并入，留给 T4-1（推荐）；B 并入会在一个 CraftWork 中混合 GenericStack 与 BigInteger 两套数值域。
7. **`ECOPatternIdentity.of` 的 null 定义**：`getDefinition()` 返回 null 时直接 NPE。A 保持现状；B 回退到结构化身份，需先确认 AE2 包装 pattern 是否会返回 null。

#### 8. 不做的部分及理由

- 改名或合并公开的 `Batch`、`PreparedBatch`：外部提供者依赖其形状，属 API 破坏。
- 把 `ECOProviderCursor` 用于 worker 环：类型与语义不匹配。
- `dynamicCursor`、`resumeDispatchPattern`：语义不同，不是同构游标。
- 循环改 Stream、为少量调用点新建接口：违反审计第 8、10 条。
- 若 `nextFreeThreadIndex` 的重置语义无法保留，则不与 bus 环合并。

---

### 通读审计：三个执行核心类

范围：`crafting/execution/worker/ECOCraftingThread.java`（TH，1,535 行）、`crafting/execution/ECOExecutionRuntime.java`（RT，1,433 行）、`crafting/execution/ECOCraftingCPULogic.java`（CPU，756 行）。三个文件已完整通读。死代码判断基于覆盖 `src/` 的 grep（含测试与资源）。未运行 gradle 或测试，疑似 bug 均为静态判断。三个文件内无 ThreadLocal，也无跨线程共享状态。

#### (a) 可删

| 位置 | 内容 | 置信度 |
|---|---|---|
| `TH:535–544` | `snapshotCraftingInputs`：全仓无调用 | 高（已核实，仅定义 1 处命中） |
| `TH:76` | `RecoveryState.DROPPED_TO_WORLD`：从未赋值 | 高 |
| `RT:528–537` | `startupSeedShortfall`：无任何调用方 | 高（已核实，仅定义 1 处命中） |
| `RT` 别名机制（`:134`、`:151`、`:1077–1088`、`:1141–1180`、`:1251–1279`） | `rejectSharedProgressAliases` 只要出现多任务共享 TaskProgress 即抛异常，因此 `progressAliasesByTaskId[t]` 恒为 `{t}`，`describeProgressAliases` 恒返回 `"[]"`，`logSharedProgressAliases` 永不输出。整套可简化为单任务映射 | 高 |
| `RT:70、72、1196–1204` | `remainingDependencies` / `dependentsByPhase`，只服务诊断字符串；`dependenciesComplete`（`:699–707`）只读 `completedPhases` | 中高，删除需同步诊断 |
| `RT:515–526`、`RT:232`、`RT:419` | `reservedInputAmount(key, Map)`、`candidates(Map)`、`isComplete(Map)`：外部只用无参版本，后两者仅 `FeedbackRouteRegressionTest` 使用 | 中，需决定是否改测试 |
| `CPU:209–211` | `deliverStoredFinalOutput` 为纯转发（唯一调用在 `CPU:170`） | 中，可内联 |
| `CPU:555–561` | load/writeJobAttachments 只多一层 null 判断 | 中，可并入 Persistence |
| 未使用 import | CPU 15 个；TH 4 个（`AEFluidKey`、`Object2LongMap`、`Map`、`Set`）；RT 1 个（`Collections`） | 高 |
| `TH:1340` | `outputItems.clear()` 冗余（`:1336` 已清） | 高 |
| `RT:1377` | 三目 `: false` 可简化为 `&&` | 高 |
| `TH:1242` | 单数 `outputItem` 写入：读取仅在 version<4 时发生（`TH:1354`），当前版本写出的键永不被消费。若为降级兼容则保留并注明 | 中 |

#### (b) 可合并

| 位置 | 内容 | 判断 |
|---|---|---|
| `CPU:485–489` 与 `CPU:491–495` | `setJobFromPersistence` / `setJobFromLifecycle` 方法体完全相同 | 高，合为一个 |
| `CPU:453–463` | 三个 `taskSchedulerFor*` 返回同一字段 | 高，合为一个访问器 |
| `CPU:643–645` 与 `CPU:656–658` | `recordPhysicalInsert + postChange + markCpuDirty` 三行相同 | 高，抽 helper |
| `TH:415–417` 与 `TH:426–428` | 三项 `canRetain` 判定逐项相同 | 高，抽 `canRetainWork(...)` |
| `TH:661` 与 `TH:920–921` | `ECOBigIntegerStorage.insert(storage, key, amount, MODULATE, actionSource)` 相同；两处 drainExact 成功后的 `onBatchStopped/clearWork/return true` 也相同 | 高 |
| `TH:697–702`、`714–719`、`1140–1145` | 同一 100 tick 节流日志逻辑出现三次 | 高，抽 static helper |
| `TH:826–835` 与 `TH:866–877` | 存储插入、扣减、retain 结构相同，仅错误标签与 retain 状态不同 | 中，可参数化 |
| `RT:871–884` `inputKeys` 与 `ECOCraftingInputPreview:106–111` | 语义接近，但后者在 `possible[0]==null` 时整体跳过该 input，前者不跳过 | **不建议合并**，需先对齐语义 |
| `RT:769–782` `fixedProcessingInputs` 与 `NetGrowthPatternValidationRegistry:84–88` | 只部分重叠，用途不同 | **不建议合并** |

#### (c) 疑似 bug

- **C1（中）`CPU:373–381`：虚拟完成判定缺少 `pendingFinalOutputs.isEmpty()`**（已核实代码对比）。`ECOCraftingOutputDelivery:89–92` 有该条件，CPU 的虚拟分支没有。
  - 触发：任务中已有物理最终产物停留在 CPU 库存（pending 非空）时发生虚拟完成。CPU 强制 `remainingAmount=0` 后调用 `finishJob(true)`。lifecycle 先 `storeItems()` 把产物回流网络，再清空 pending。结果是请求方收到 SUCCESS，而最终产物已进入网络。
  - 依据为代码对比，**未测试确认**。
- **C2（中低，影响低）`CPU:167–176`**：169 行 `beginResolveTick` 之后，170、174 两处早退未调用 `finishResolveTick`（仅 `:180`、`:200` 调用）。只丢失该 tick 的诊断日志。代码路径确认。
- **C3（低）`RT:395–396`**：`onVirtualAccepted` 先 `progress.accept(count)` 再 `onAccepted` 校验；后者若抛异常，任务进度已扣减，而 `CPU:363–365` 吞掉异常并返回 false。正常候选下不应失败，属防御性缺陷。
- **C4（低）`RT:74`、`239`**：`candidateBuffer` 是共享可变列表。`CPU:351–352` 的 `candidates()` 会 clear 它；若外部在 provider push 中同步回调该 sink，`TS:166–185` 的循环会读到被改写的列表。仓库内无此调用路径。
- **C5（低）`RT:744–746`、`RT:824`**：使用 long 的 `multiplyExact/addExact`。当 `(laps-1)*count` 或总量超过 Long.MAX（约 9.2e18 次合成）时抛 ArithmeticException，`TS:166` 调用链无 try，会每 tick 抛出。BigInteger 边界转换（`TH:650`、`1180`，`RT:327`、`860`，`ECOExactInventory:50`、`67`）均已先 `min(MAX)`，未发现越界。
- **C6（中）`RT:640–641`：启动种子静默丢失**（已核实）。`GenericStack.readTag` 返回 null 时，`if (stack != null && stack.amount() > 0L)` 直接跳过，不标记损坏。对比 `TH:1411–1425`，批量条目不可读会 quarantine。结果是该种子不再受保护，库存可能被其他阶段消耗，正是 `RT:1395–1398` 注释所要防止的情形。触发需卸载模组或 NBT 损坏。
- **T1（低触发，数据丢失）`TH:1338–1342` 与写出侧 `TH:1245–1247`、`1286–1293`**：读取侧已核实只读前 256 条（`MAX_PERSISTED_ITEM_STACK_ENTRIES`）并标记损坏；写出侧按代理报告不设上限。单次非批量合成输出超过 256×99=25,344 件（或拆分后超过 256 条）时，超出部分会在 quarantine 后静默丢失。写出侧的不设上限仍需复核。
- **T2（高，影响低）`TH:1234`、`1242`**：经 `saveSerializableStack`（`:1259–1264`）把数量截断到 99，重载后 craftingEventOutput 数量 ≤99，影响 ItemCraftedEvent。
- **T3（低，未确认）`TH:155`**：控制器退出全虚拟模式后，`virtualBatch` 为真的批次会落入物理进度路径（`:181–185`），并按非虚拟计费后弹出虚拟产物。是否重复产物取决于派发前输入是否已物理抽取，未核实。
- **T4（低）`TH:646–662`**：CraftingService 非 `ECOCraftingOutputRouter` 时 lambda 返回 ZERO 且不改状态，drainExact 永不完成；`690–705` 只处理异常，会静默停滞。依赖 mixin 缺失，实际概率低。

**NBT 对称性**：RT 的 writeToNBT/fromNBT 逐键核对一致；CPU 持久化一致；TH 除 T1、T2 外一致。

#### (d) 不动

- `CPU:230–239` 的 `thunderboltMixinAnchorEnabled()` 恒 false 分支，`CPU:213–217` 的 `collectAvailableProviders`，`CPU:282–285` 原样保留的 `provider.pushPattern`：注释明确为 Mixin/Thunderbolt 二进制兼容锚点。
- `CPU:297–300`、`309–311`：注释说明 AE2 Utility 依赖这些调用点。`CPU:518–520` 的 `getElapsedTimeTracker`（deprecated）仍被 `CraftingCpuMenuMixin` 调用。
- `RT:1215–1219`、`1282–1298`：绑定 TaskProgress 时以 progress 为真源并忽略持久化 cursor，属有意设计。
- `RT:600–622` 的 laps/ahead 严格校验，与 `ExecutingCraftingJob:239` 的 try 兜底配合：fromNBT 失败转为 suspended。
- `ECOCraftingCPU:232–240` 注释：load 抛异常即"拒绝并重放"，`AmountMapNbt` 对非正值抛异常亦属此设计。
- `ECOBigOrderController:294–296` 写入以 `job!=null` 门控，`317–318` 读取 `clear()`，对称。
- `ECOExactInventory:92–96` 的 restore 对 0 抛异常，但 `deferredEmitted` 在 `CPU:662–669` 每次清零，NBT 读取拒绝非正值，零值不可达，不是 bug。
- `TH:1456–1471` 的 quarantine 机制与 `GenericStackNbt.readBounded`（`54–66`）的 Optional 语义：空 remainder 合法，已核对不会误判。

#### 优先处理（当前状态见 10.7）

1. **C1（`CPU:373–381`，已修复并通过回归，10.7）**：请求方收到 SUCCESS，而产物回流网络，是行为层不一致。修复可直接复用 `ECOCraftingOutputDelivery` 的谓词，并补一个 pending 非空的虚拟完成测试。
2. **T1（`TH:1338–1342`，已修复并通过回归，10.7）**：用户选择完整读取现有列表，取消条数截断；存档格式不变。
3. **C6（`RT:640–641`，已修复并通过回归，10.7）**：启动种子静默丢失会破坏跨阶段保护。建议与 TH 路径一致，改为 quarantine 或显式报错。
