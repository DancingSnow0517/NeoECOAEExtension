# 面向 AI 与维护者的基础架构说明

> 快照：`21.2.0-beta4`，Minecraft `1.21.1`，NeoForge `21.1.233`，AE2 `19.2.17`。本文刻意写明所有权、数据流和约束，供 AI 编码代理在修改前定位正确边界。

## 1. 系统目标

Neo ECO AE Extension 是 AE2 附属 Mod，包含三类大型多方块：

- **存储系统**：有限 ECO 元件，以及支持断点迁移的无限存储域。
- **合成系统**：样板总线、并行 worker、冷却、调度和产物恢复。
- **计算系统**：合成规划计算、CPU 线程、字节容量、并行度和逻辑网络池化。

集成工作站提供机器加工与样板 Provider 能力。可选集成增加存储键类型、模型、配方查看器、信息探针以及高性能调度桥。

## 2. 不可破坏的架构规则

即使能够编译，违反以下任一规则的修改也是错误的：

1. AE 网络及其 grid service 拥有网络级状态；方块实体只拥有本地持久状态。
2. `PatternCatalog` 是样板位置、计数、空余容量和外部样板 claim 的唯一事实来源。
3. CPU 拥有合成输入、能量预留、任务记账及已接收但未交付的产物；Provider 不得扣除或退回 CPU 资源。
4. Provider 接收必须原子化：`true` 表示完整转移所有权，`false` 表示完全未转移。接收结果未知时必须进入对账，禁止走普通降级路径。
5. worker 持有的输入和产物必须能跨区块卸载、拓扑重建、取消和重启恢复。
6. 服务端游戏状态权威；客户端状态只能用于展示或缓存。
7. 规划可以异步运行，但世界、网络、Provider、库存和方块实体修改必须留在所属服务端线程。
8. 存储/合成数量使用 `long`；只有在已检查边界的 Minecraft API 入口才能收窄为 `int`。
9. 目标 Mod 不存在时，不能提前加载任何可选 Mod 类。
10. Mixin 桥是条件能力；始终用 `instanceof` 检查，并保留正常降级路径。

## 3. 源码目录地图

| 包/路径 | 职责 | 允许依赖 |
| --- | --- | --- |
| `NeoECOAE` | 通用入口和注册编排 | `all`、数据、配置、网络、集成启动 |
| `all` | 方块、物品、方块实体、配方、菜单、等级、元件类型、grid service 的注册句柄 | API 和具体注册内容 |
| `api` | 跨模块及第三方契约 | Minecraft/NeoForge/AE2；尽量减少实现引用 |
| `registration` | Registrate builder 和 entry 包装 | `all`、API、具体内容 |
| `blocks`、`items` | 世界/物品定义与本地行为 | API、方块实体、多方块契约 |
| `blocks.entity` | 本地持久状态、tick、capability、UI 宿主 | API、`impl` 服务、多方块 cluster |
| `grid` | AE 网络拥有的服务，核心是 `PatternCatalog` | API 和 AE2 grid API |
| `multiblock.calculator` | 检测、校验物理结构 | 方块实体和 cluster 类型 |
| `multiblock.cluster` | 已成型结构的运行时聚合 | 方块实体、网络池、合成 CPU |
| `multiblock.network` | 已成型结构之间的逻辑池化/连接 | cluster 抽象 |
| `impl.crafting` | 规划器、执行计划、FastPath、调度、恢复实现 | 公开合成契约和 AE2 内部类型 |
| `impl.storage` | 有限元件、无限引擎、传输/迁移 | 公开存储契约 |
| `recipe` | 配方数据、codec、serializer、数据 builder | `NERecipeTypes` |
| `network` | 私有 C2S/S2C 协议 | 菜单/客户端缓存；不是公开 API |
| `menu`、`gui`、`client` | 菜单同步、界面、渲染和模型映射 | 服务端只读快照/API 桥 |
| `integration` | 可选内容和生命周期集成 | 由发现/插件生命周期保护的目标 Mod API |
| `compat` | 可选 Provider/合成 API 的运行时适配 | 反射或受保护的可选 API |
| `mixins` | AE2 和可选 Mod 注入点 | 尽可能小地桥接到 `api`/`impl` |
| `data`、`src/generated/resources` | 数据生成定义及产物 | 注册句柄和配方 builder |
| `guidebook` | GuideME 中英文源文件 | 面向玩家的玩法文档 |

预期依赖方向是“入口/注册 -> 内容 -> 服务实现”，契约通过 `api` 向内指向实现。新的第三方钩子放 `api`，算法放 `impl`，可选目标 Mod 引用放 `integration` 或 `compat`。

## 4. 启动顺序

`NeoECOAE` 的通用构造按以下顺序执行：

1. 强制初始化复制自 AE2/ExtendedAE 风格菜单所需的菜单类型。
2. 排队/注册创造栏、菜单、物品、方块、流体、方块实体、数据生成、grid service、等级、元件类型、配方和数据组件。
3. 扫描 `@Integration` 类，对已加载的目标 Mod 调用通用集成。
4. 注册会同步的服务端配置。
5. 在 Mod bus 上挂接 capability、升级、存储元件、自定义注册表、资源包和网络生命周期监听器。
6. 在 NeoForge bus 上挂接 tooltip、命令、标签重载、存储卸载/停止/tick 监听器。

`NewRegistryEvent` 创建同步的 `CELL_TYPE` 和 `ECO_TIER` 注册表。common setup 安装升级和内置 cell handler。`NeoECOAEClient` 独立加载客户端集成、解析延迟模型注册、安装固定 renderer/screen 并注册物品颜色。

不要把客户端工作移入通用构造。不要为了简化代码提前解析注册对象，应使用 holder/延迟注册。

## 5. 主要运行时数据流

### 5.1 合成规划与执行

```text
合成 UI / AE2 请求
  -> Mixin 暴露的 ECO 规划设置
  -> ECOPlanningService / ECOCraftingPlannerService（异步计算）
  -> 不可变 plan + ECOPlanningResultRegistry 交接
  -> 服务端线程提交到 CraftingService
  -> NEComputationCluster 选择/创建 ECOCraftingCPU
  -> ECOCraftingCPULogic 拥有任务和库存
  -> 调度策略 + ECOCraftingDispatchStrategy
  -> 普通 Provider 或已验证 FastPath Provider
  -> ECO 合成 worker / 外部 Provider
  -> 按 job 路由和认领产物
  -> 请求者、网络存储或 CPU 持久余量
  -> 生命周期结束 + 清理附件
```

规划只产生描述，不转移资源。`ECOPlanningResultRegistry` 负责把异步结果交给提交阶段，并包含恢复/匹配逻辑；它是内部交接状态，不是通用缓存 API。

计算 cluster 拥有活动 CPU 和容量。进入逻辑计算网络后，线程、字节容量和并行度会池化；只有代表节点对外发布占位空闲容量，避免终端出现重复项。

### 5.2 调度路径

普通调度探测 AE2 Provider，可通过 `ECOParallelCraftingProvider` 原子接收多个 craft。已验证 FastPath 会分类/物化配方，计算安全的算术或有状态批次，抽取输入、预留能量，再通过 `ECOFastPathDispatchProvider` 提交。

禁止把处理样板静默送入仅限 FastPath 的契约。提交结果未知时禁止改用另一个 Provider 重试。接收成功后、可观察完成前必须完成恰好一次的记账。

### 5.3 Worker 所有权与恢复

`ECOCraftingThread` 持有可持久化的工作资源，序列化活动工作、输入、输出、容器返还物、进度、job id 和恢复状态。取消或找不到 owner 时会把合格内容退回网络；插入不完整时保留余量等待重试。

永久拆除结构必须先取消/恢复，再销毁 cluster。普通拓扑重建和区块卸载应保留延迟 CPU/thread 状态。不能用世界掉落物替代这些路径：非物品 key 和超过 `Integer.MAX_VALUE` 的数量无法安全表示。

### 5.4 样板目录

```text
Grid 节点加入/离开或样板槽变化
  -> PatternCatalog 更新索引位置/计数/容量 generation
  -> 组合 IECOPatternStorage 选择可写 ECO 样板总线
  -> 唯一性证明允许 KnownUnique 插入
  -> 在时间预算内增量扫描外部库存
  -> 迁移方按 owner UUID claim 槽位
  -> 成功时移除候选项；所有终止路径释放 claim
```

不要在菜单或方块实体中再建一套网络扫描/缓存。应扩展 catalog，并由槽位和拓扑增量更新。

### 5.5 存储域

有限元件通过 `ECOStorageCells` handler 发现，并以 `IECOStorageCell` 暴露。存储多方块聚合元件和统计信息。无限模式使用基于 SavedData 的引擎及可恢复 transfer 对象，只有 `IECOStorageMigrationCell` 参与迁移。

迁移是所有权转移，不是复制。修改前必须模拟；进度必须跨重启持久；只有目标已接收相应内容后才能清除源。level 卸载/server stop 会清除临时 handler/运行时缓存，但不能删除持久存储。

### 5.6 多方块生命周期

calculator 校验方块并创建 `NECluster` 子类；方块实体挂入 cluster，controller 暴露聚合状态；network switch 可通过 `NELogicalNetworkManager` 把 cluster 连接成网络池。

成型和销毁是服务端拓扑操作。增加组件时需要同时更新：物理校验、cluster 成员列表、容量/统计聚合、存在本地状态时的序列化、UI 快照、可见时的 renderer/model，以及能持有资源时的永久拆除恢复。

## 6. 状态所有权与持久化

| 状态 | 所有者 | 持久化/传输 |
| --- | --- | --- |
| 注册定义 | NeoForge 注册表 | 同步注册数据 |
| 单方块库存/配置 | 方块实体 | 方块实体 NBT/数据组件 |
| 样板网络索引 | `PatternCatalog` grid service | 从 grid node 重建/增量维护；claim 是运行时协调 |
| 活动合成任务 | `ECOCraftingCPULogic`/CPU | 计算 controller/core NBT 和执行 codec |
| worker 在途资源 | `ECOCraftingThread` | 完整 NBT，包括恢复状态 |
| 每任务扩展状态 | attachment 实例 | job 数据内带命名空间的 compound |
| 无限存储内容 | infinite storage engine | level `SavedData` 加可恢复 transfer 状态 |
| 规划结果交接 | planning result registry | 有界运行时元数据及显式持久恢复元数据 |
| 客户端精确数量/UI | 客户端缓存/菜单 payload | 私有网络包；不具权威性 |
| 配置 | `NEConfig` server spec | NeoForge 服务端配置，必要值同步至客户端 |

增加数据前先确定唯一 owner，并随 owner 持久化。若没有明确的失效/对账协议，不要在 UI、cluster 和方块实体中重复保存权威值。

## 7. 服务端/客户端与线程边界

- 通用/API 类必须能在专用服务端加载；客户端 import 只能放在 `client` 或受保护的客户端入口。
- 方块实体、grid service、cluster、Provider commit、产物 claim 和迁移运行在服务端线程。
- ECO 规划使用 executor 和中断检查点，只能给规划器不可变快照或规划器自有结构。
- 网络 handler 修改状态前必须校验当前 menu/container，并通过 NeoForge context 排队执行。
- renderer 和 screen 消费快照，必须容忍服务端数据过期或缺失。
- 全局注册表在回调并存处使用 synchronized 或 copy-on-write，但这并不允许离开服务端线程修改游戏状态。

## 8. 可选集成架构

项目有四种不同机制，需要有意识地选择：

1. `integration/<mod>`：可选内容注册或目标 Mod 官方 API；`@Integration` 根据已加载 Mod 保护通用/客户端 `apply`。
2. `compat/<mod>`：行为适配器，常用于 Provider FastPath 和跨版本调用。
3. `mixins/compat/<mod>` 加独立 mixin JSON：注入可选 Mod 内部。会提前解析类时使用 `requiredMods`。
4. JEI/EMI/Jade/KubeJS/LDLib 等插件入口：由目标框架发现插件。

通用方法签名不能包含缺失目标 Mod 的类。优先在 `api` 建立小型本地桥接口，并在可选层实现/适配。确需跨版本时可使用反射，但要集中封装，并输出明确诊断。

## 9. 网络与菜单

`ECONetwork` 拥有私有协议版本 `"1"`：

- C2S 强制开始合成标记。
- C2S 当前容器的 ECO 规划请求。
- S2C 精确数量数据。

数据包是 UI 命令/快照，不是公开 RPC API。服务端 handler 必须从发送者当前打开的 menu/grid 推导权限，不能信任包中提供的世界引用。增加 payload 时需要同时处理注册、codec 边界、正确 side、menu/session 校验及协议版本兼容性。

## 10. 配置、数据和资源

- `NEConfig` 是服务端配置，对玩法数值有权威性。
- `src/main/resources` 存放手写 asset/data/mixin 配置。
- `src/generated/resources` 是 datagen 产物，并入 main resources。
- `processResources` 会把 `guidebook` 复制到 `assets/neoecoae/ae2guide`。
- 已有生成器的配方和模型变更应从 data provider/builder 发起。
- Mixin JSON 分为核心、终端显示和可选兼容组；新 Mixin 必须进入正确配置和 side。

## 11. AI 修改任务路由

| 修改目标 | 首先阅读 | 同时验证 |
| --- | --- | --- |
| 增加/修改方块或物品 | `all/NEBlocks`、`all/NEItems`、具体类 | 方块实体、模型/datagen、掉落、配方、语言、创造栏 |
| 修改多方块组件 | 对应 calculator、cluster、方块实体 | 销毁/恢复、网络池化、UI、renderer |
| 修改合成规划 | `impl/crafting/planner` | 规划结果交接、循环/替代设置、异步安全 |
| 修改调度/性能 | `ECOCraftingCPULogic`、dispatch、fastpath 包 | 原子回滚、能量、任务/产物记账、可选桥 |
| 增加外部 Provider 支持 | 公开 Provider 契约，再写 `compat` 适配 | 拒绝/异常/结果未知测试、目标缺失时加载 |
| 修改任务产物 | output API、CPU logic、worker eject | job id 所有权、部分插入、持久余量、完成判断 |
| 修改存储元件 | `api/storage`、handler、`impl/storage` | 迁移资格、缓存释放、AE2 cell 语义、UI 颜色 |
| 修改无限存储 | `impl/storage/infinite` 和 transfer 包 | SavedData、重启、模拟插入、回滚/防复制 |
| 修改样板迁移 | `PatternCatalog`、样板总线、机器接口 | claim、generation 失效、扫描预算、重复证明 |
| 增加可选 Mod | 按需选择 `integration`、`compat` 或 compat mixin | 目标缺失类加载、版本范围、客户端/服务端分离 |
| 增加 UI 字段 | 服务端 owner -> menu/payload -> 客户端快照/render | 权限、codec 边界、过期数据行为 |
| 暴露新公开 API | `api` 中最小接口/record | 所有权文档、nullability、线程、失败语义、二进制演进 |

## 12. 测试与验证

Gradle 项目使用 Java 21 和 JUnit 5。常规检查：

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
```

另有 `testThunderboltLegacy`，使用旧版 Thunderbolt/AE2 Lightning Tech artifact 运行兼容测试。数据生成使用 NeoForge ModDev 创建的 `runData` 配置/任务。

测试强度按资源所有权风险决定：

- 纯 codec/数学/视图修改：聚焦单元测试。
- 注册或类加载修改：客户端及专用服务端启动。
- 规划/调度修改：成功、缺输入、取消、Provider 拒绝、Provider 异常、接收结果未知、溢出和重启。
- 存储/迁移修改：simulate/action、容量不足、卸载/重载、重启、防重复、超过 `Integer.MAX_VALUE` 的数量。
- 可选集成：目标缺失环境，以及每个支持的目标版本系列。

结束前检查 diff 中是否混入生成资源、IDE 或 run 文件。狭窄 Java 修改不应无故重新生成全部数据。

## 13. 已知架构风险

- `api` 包同时包含稳定契约和大型实现类，public 可见性不等于支持等级。
- 没有独立 API artifact，也没有配置公开 Maven 发布仓库。
- 除 AE2 公共 API 外，代码还依赖 AE2 实现类和 Mixin，因此升级 AE2 需要广泛兼容测试。
- 若干可选集成对版本敏感，使用 compile-only artifact、运行时适配器或 Mixin。
- 进程级回调注册表要求严格单次注册，测试后也要清理。
- 私有网络协议只使用粗粒度字面版本，没有文档化的向后协商机制。

不确定时应保留资源所有权并关闭操作。带诊断的停滞任务可以恢复，复制或删除的资源无法恢复。

