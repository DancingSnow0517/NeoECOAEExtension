# GTM / GTL 配方联动范围与完整类型清单

审计日期：2026-10-24。对象：本工程 Forge 1.20.1 工作树、GTM 1.20.1、GTLCore。本文是源码盘点和后续设计建议，尚未增加 GT 配方适配器。

具体原料、用量与产出见 [GTM / GTL 集成工作站配方与矿物混用定案](GTM_GTL_WORKSTATION_RECIPE_PLAN_ZH_CN.md)。该方案已通过互斥配方档案实装，以指定的 42 条 ECO 工作站配方、GT 原机前置制造和有限矿物混用为主，启用方法见 [实装说明](GTM_GTL_RECIPE_PROFILE_IMPLEMENTATION_ZH_CN.md)。本文保留为 GT 全类型导入的研究附录；任意 GT 机器配方导入工作站仍未实现。

## 1. 先看结论

**目前大型集成工作站确实没有 GTM / GTL 的机器配方导入。可联动的范围很大：常规机械加工、化工、矿物处理、组装、冶炼、冷却、材料复制和大部分 GTL 高阶物料加工都有接入路径。** 真正的工作量在于保留 GT 的执行规则，而不仅是把输入、输出抄进来。

如果目标是尽量覆盖全部配方，建议分成两条执行路径：

1. **物料加工导入工作站**：把满足已实现能力的配方接入 ECO 工作站，保留原料、催化物、电路、产物、EU、时长、概率和工艺门槛。
2. **特殊机器保留原机执行**：有物料产物的机器可通过 AE 处理样板调度；发电和没有普通产物的控制机器通过供料、开关与状态接口协作。原机继续处理世界方块、实体、天气、研究宿主和专属状态。具体提供器、缓存仓与样板行为仍需在目标实例验证。

这两条路径合起来比“全部强行转成普通工作站配方”更接近完整覆盖。本文按类型标出推荐路径，并列出具体限制。

### 本次盘点到的范围

| 来源 | 通用机器类型定义数 | `.recipeBuilder(...)` 调用点 | 静态配方 JSON |
| --- | --- | --- | --- |
| GTM 1.4.4 | 60 | 1,913 | 0 |
| GTM 最新 1.20.1 开发快照 | 60，另有 1 个专用装备类型 | 1,982 | 0 |
| GTLCore 新增 | 81 | 496 | 29 |

跨版本合并后为 **143 个不重复的通用类型，另加 1 个新版专用装备类型**。通用类型的建议分为 A 66 种、B 51 种、C 25 种、D 1 种；这不是实际可执行配方的覆盖率。

配套索引包含 **4,391 个配方构建调用点**（其中 3,751 个 ID 参数是字符串字面量，其余保留表达式），另有 **1,624 个辅助合成/复制/装备构建调用点**和 GTL 的 **29 份静态 JSON**。有 3 个通用调用点的类型来自动态表达式，索引保留来源和表达式，不猜测类型。

配套文件：[GTM_GTL_RECIPE_SOURCE_INDEX.json](GTM_GTL_RECIPE_SOURCE_INDEX.json)。其中记录源码版本、注册类型、配方构建位置、构建 ID 表达式、输入输出参数表达式、能耗/时长表达式、附加字段和现有静态 JSON 配方。可以按类型 ID、材料名或构建 ID 搜索。

**这里的调用点数不等于运行时配方条数。** 一个材料循环会生成很多配方；一个构建钩子还可能继续生成研究、拆解或其他机器配方。整合包脚本又会删除、替换和新增配方。本文完整列出核对范围内的内置类型定义，并提供可定位的源码配方索引；没有把源码调用点冒充最终生效配方。

## 2. 版本与来源

读取参考源码前已执行 `git fetch origin --prune`。参考代码从下列提交导出审计，没有切换或覆盖参考仓库的工作树。

| 对象 | 审计版本 | 提交 | 用途 |
| --- | --- | --- | --- |
| 本工程 | `port/forge-1.20.1`，含读取时已有的未提交改动 | `9b2f2523c4db73f0b3cc93c4f0aa379702addc99` | 判断当前能力与缺口 |
| GTM，与 GTL 声明依赖对应的上游基线 | 标签 `1.20.1-1.4.4` | `e3844ba943e2388c0856617ae4f8b473a0ae1b64` | GTL 基础配方类型/API 参考 |
| GTM，最新 1.20.1 开发分支快照 | `origin/1.20.1`，`mod_version=8.0.0` | `f3855da4fd48f837d1d5c98dd6fa928c3e828f6a` | 独立 GTM 新版覆盖与 API 差异 |
| GTLCore | `origin/gtl-1431-skyblock`，`mod_version=1.2.3.2-fix7` | `6ca516d0933490ecf42d84d5f91648e5f2a0e48e` | GTL 新增类型、机体规则、派生配方 |

本地参考仓库分别是 `E:\Minecraft Project\GregTech-Modern` 和 `E:\Minecraft Project\GTLCore`。GTLCore 的本地 origin 为 `AaAdoniSsS/GTLCore`；本次同时核对 `GNSW/GTLCore` 对应分支，远端提交相同。GTM 1.4.4 标签也已与远端核对。

需要特别区分：

- GTLCore 的 `gradle.properties` 声明 `gtceu_version=1.4.4`。这不表示该 GTL 分支能和当前 GTM 8.0.0 开发版直接混装，也不证明任意同版本号的定制 JAR 都有完全相同的 API。
- GTM 新开发版与旧版都保留许多同名类型，但序列化器、EU 数据结构、配方执行和机体 API 有变化。应分别制作版本适配层，并核对实例实际加载的 JAR。
- `gtl-1431-skyblock` 是分支名；本文没有据此把 GTLCore 的实际版本写成 1.4.3.1。
- 本工程批准的主体移植输入仍是 `porting/source-lock.json` 中的 `dc8344f48ee866e57e02c285fdda5e0fb5da9421`。本次研究不改变该基线，也不增加已批准四类工作站之外的实装变体。

## 3. 当前工程已经有什么，还缺什么

### 当前入口

在 [LargeWorkstationRecipes.java](../src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationRecipes.java) 的 `COMPAT_TYPES` 和导入循环中，外部类型入口是 AdvancedAE、AE2LT、AE2 Crystal Science，以及独立处理的 ExtendedAE/原生配方分支；没有 GT 的入口。

[LargeWorkstationRecipeAdapters.java](../src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationRecipeAdapters.java) 没有 GT 适配分支；[LargeWorkstationModule.java](../src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationModule.java) 的来源识别没有 `gtceu` 或 `gtlcore`；[build.gradle](../build.gradle) 也没有 GTM/GTL 的编译依赖与专用运行配置。

因此，现有 GTL CPU/模型兼容记录不能作为“GT 配方已能在工作站执行”的证据。把类型名加入允许列表，仍不足以完成联动。

### 能力对照

以下描述以读取时的工作树为准，包含已有未提交改动，不代表这些行为都已进入当前发行 JAR。

| 能力 | 当前源码状态 | 对 GT 联动的意义 |
| --- | --- | --- |
| 多物品输入 | 已有列表与数量匹配 | 可复用；GT 原料谓词仍需准确转换 |
| 多流体输入 | `additionalFluidInputs` 已存在 | 不必把所有 GT 配方裁成单流体 |
| 多物品/流体输出 | `additionalOutputs` 已存在 | 可复用确定产物结算；还要核对大量输出的显示与容量 |
| 配方时长 | `processingTicks` 已存在 | 有接入位置，仍须保留 GT 工艺时长与超频规则 |
| AE 与 FE 预算 | 已有独立字段与结算路径 | 可以复用部分支付结构；不能据此宣称已经支持 GT EU |
| EU/t、电压、安培与启动 EU | 没有 GT 专用合同 | 需要能源桥和电压门槛，不能仅用“总 FE 足够”替代 |
| 编程电路、模具、非消耗输入 | 当前工作站合同没有独立执行字段 | 需要配置/催化物保留与并行占用规则 |
| 概率输入、概率产物、等级概率加成 | 当前合同输出为确定数量 | 需要概率执行与对 AE 的正确产物承诺 |
| CWU/t、总研究量、研究解锁 | 没有 GT 研究/CWU 适配 | ECO 计算网络的算力不自动成为 GT 研究算力 |
| GT 条件与机体 `data` | 没有通用 GT 条件解释器 | 需要洁净室、温度、维度、等级及专机规则的逐项实现 |
| 单项输入超过 int | 本地 `SizedIngredient.count`、`SizedFluidIngredient.amount` 是 int | 与 GTL `LongIngredient.actualAmount` 不等价；批量计数为 long 不能消除这一缺口 |
| 世界操作与特殊任务 | 不属于当前物料配方合同 | 优先对接原机，而后逐项评估专门扩展 |

主要依据：[LargeWorkstationRecipe.java](../src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationRecipe.java)、[IntegratedWorkingStationRecipe.java](../src/main/java/cn/dancingsnow/neoecoae/recipe/IntegratedWorkingStationRecipe.java)、[SizedIngredient.java](../src/main/java/cn/dancingsnow/neoecoae/platform/recipe/SizedIngredient.java)、[SizedFluidIngredient.java](../src/main/java/cn/dancingsnow/neoecoae/platform/recipe/SizedFluidIngredient.java)。

## 4. 如何阅读下面的全类型表

| 标记 | 含义 | 可联动方式 |
| --- | --- | --- |
| A | 可优先适配的物料加工 | 通用桥完成后，筛选已支持的具体配方导入工作站 |
| B | 需要条件或专属加工支持 | 补齐对应工艺/宿主/研究条件后导入；也可先调度原机 |
| C | 建议保留原机器执行 | 发电、世界操作、无普通产物控制、专属 ME 任务等，优先做原机调度 |
| D | 占位入口 | 不作为普通加工类型整体导入；关联机器另看其真实行为 |

**A 也不表示当前已经支持，或该类型所有实例配方均可无条件导入。** KubeJS 可以给任意普通类型追加洁净室、研究、自定义能力或行为。分类是开发顺序建议；最终允许执行必须逐条检查具体配方。

I/O 顺序为“物品进 / 物品出 / 流体进 / 流体出”，来自类型注册的 `setMaxIOSize` 声明。它是声明容量，不是已注册配方数，也不是某一实体机器的所有物理限制。GTL 和脚本可继续修改声明，运行时以最终注册对象为准。

## 5. GTM 全部通用机器配方类型：两个版本合计 62 种

“旧”表示 GTM 1.4.4，“新”表示本次 GTM 1.20.1 开发分支快照。两者各有 60 个通用类型，差异如下：旧版另有 `evaporation`、条件注册的 `create_mixer`；新版另有 `spooling`、`welder`。新版的专用 `equipment_foundry` 见第 7 节。

I/O 若发生变化，写作“旧 → 新”。类型来源及精确行号见索引中 `types`，包括容易漏掉的旧版 `GCyMRecipeTypes.java` 与新版 `GCYMRecipeTypes.java` 的合金冶炼类型。

| 配方/加工名称 | 类型 ID | 版本 | I/O 声明 | 分类 | 联动内容与必须保留的规则 |
| --- | --- | --- | --- | --- | --- |
| 合金冶炼炉 | `gtceu:alloy_blast_smelter` | 旧+新 | 9/0/3/1 | B | GCyM 合金冶炼；线圈温度与大容量流体材料均须保留。 |
| 蒸汽锅炉 | `gtceu:steam_boiler` | 旧+新 | 1/0/1/1 | C | 锅炉燃料、水与蒸汽循环；宜对接原锅炉，不按普通加工扣 AE 能量。 |
| 电炉 | `gtceu:electric_furnace` | 旧+新 | 1/1/0/0 | A | 普通熔炼；同时处理其代理的原版 smelting 配方，保留输入谓词。 |
| 合金炉 | `gtceu:alloy_smelter` | 旧+新 | 2/1/0/0 | A | 合金、熔炼与模具加工；不消耗的模具单独保留。 |
| 电弧炉 | `gtceu:arc_furnace` | 旧+新 | 1/4/1/1 → 1/9/1/1 | A | 电弧冶炼、回收；保留气体、炉渣和全部副产物。 |
| 组装机 | `gtceu:assembler` | 旧+新 | 9/1/1/0 | A | 通用组装；保留电路号、焊料及催化物。 |
| 高压釜 | `gtceu:autoclave` | 旧+新 | 2/2/1/1 | A | 结晶、高压处理；概率产物须走概率执行，不能按必出处理。 |
| 卷板机 | `gtceu:bender` | 旧+新 | 2/1/0/0 | A | 板、箔等成型；保留编程电路，不按批次消耗电路。 |
| 酿造室 | `gtceu:brewery` | 旧+新 | 1/0/1/1 | A | 物品与流体酿造；保留流体输入与输出数量。 |
| 研磨机 | `gtceu:macerator` | 旧+新 | 1/4/0/0 | A | 粉碎、矿物研磨；保留多产物、概率和等级增益。 |
| 装罐机 | `gtceu:canner` | 旧+新 | 2/2/1/1 | B | 容器灌装/拆装；核对容器返还、流体 NBT、动态输出和 ingredientActions。 |
| 离心机 | `gtceu:centrifuge` | 旧+新 | 2/6/1/6 → 2/6/2/6 | A | 多组分离心；需要多流体、多物品完整结算。 |
| 化学浸洗机 | `gtceu:chemical_bath` | 旧+新 | 1/6/1/1 | A | 化学浸洗；概率副产物与浸洗液必须保留。 |
| 化学反应釜 | `gtceu:chemical_reactor` | 旧+新 | 2/2/3/2 | A | 化学合成；逐条检查洁净室等附加条件，条件未实现时跳过。 |
| 压缩机 | `gtceu:compressor` | 旧+新 | 1/1/0/0 | A | 压缩块、材料压缩；按原数量与电压等级执行。 |
| 切割机 | `gtceu:cutter` | 旧+新 | 1/2/1/0 | A | 切片、切板；冷却/润滑流体是原料，不能省略。 |
| 蒸馏室 | `gtceu:distillery` | 旧+新 | 1/1/1/1 | A | 单级蒸馏；保留电路号，和蒸馏塔产率分别处理。 |
| 电解机 | `gtceu:electrolyzer` | 旧+新 | 2/6/1/6 | A | 电解分离；保留所有物品、气体和流体产出。 |
| 电磁选矿机 | `gtceu:electromagnetic_separator` | 旧+新 | 1/3/0/0 | A | 电磁选矿；保留概率矿粉副产物。 |
| 提取机 | `gtceu:extractor` | 旧+新 | 1/1/0/1 | A | 物品提取与流体提取；支持两种输出形式。 |
| 压模器 | `gtceu:extruder` | 旧+新 | 2/1/0/0 | A | 压模加工；模头是非消耗输入。 |
| 发酵槽 | `gtceu:fermenter` | 旧+新 | 1/1/1/1 | A | 发酵；保留物品/流体物料和原时长。 |
| 流体加热器 | `gtceu:fluid_heater` | 旧+新 | 1/0/1/1 | A | 流体加热；流体转化与电路条件按原配方执行。 |
| 流体固化器 | `gtceu:fluid_solidifier` | 旧+新 | 1/1/1/0 | A | 熔融材料固化；模具不能按原料消耗。 |
| 锻造锤 | `gtceu:forge_hammer` | 旧+新 | 1/1/0/0 | A | 锻打、碎矿；只使用该类型的机器配方，不混入手工锤配方。 |
| 冲压机床 | `gtceu:forming_press` | 旧+新 | 6/1/0/0 | B | 静态压印可导入；命名、模具 NBT 或动态生成结果须另适配。 |
| 车床 | `gtceu:lathe` | 旧+新 | 1/2/0/0 → 1/2/1/0 | A | 车削；边角料与可能存在的流体必须完整保留。 |
| 搅拌机 | `gtceu:mixer` | 旧+新 | 6/1/2/1 | A | 粉体、合金粉、溶液混合；保留全部流体和电路。 |
| 洗矿厂 | `gtceu:ore_washer` | 旧+新 | 2/3/1/0 | A | 洗矿；保留水、纯净矿和副产物。 |
| 打包机 | `gtceu:packer` | 旧+新 | 2/2/0/0 | A | 打包与拆包；电路模式不同的配方分别识别。 |
| 两极磁化机 | `gtceu:polarizer` | 旧+新 | 1/1/0/0 | A | 磁化加工；保留电压等级与原始产物。 |
| 激光蚀刻机 | `gtceu:laser_engraver` | 旧+新 | 2/1/0/0 | B | 镜片、编程电路及部分洁净室条件；GTL 还会派生其他激光配方。 |
| 筛选机 | `gtceu:sifter` | 旧+新 | 1/6/0/0 | A | 筛选与宝石分级；需概率结算；GTL 会扩展其流体 I/O。 |
| 热力离心机 | `gtceu:thermal_centrifuge` | 旧+新 | 1/3/0/0 | A | 热力选矿；保留全部固体副产物。 |
| 线材轧机 | `gtceu:wiremill` | 旧+新 | 2/1/0/0 | A | 拉丝与线材加工；保留电路号和倍率。 |
| 电路组装机 | `gtceu:circuit_assembler` | 旧+新 | 6/1/1/0 | B | 电路组装；保留洁净室、焊料、原电压与材料约束。 |
| 集气室 | `gtceu:gas_collector` | 旧+新 | 1/0/0/1 | B | 按维度等环境条件集气；不能允许在任意维度无条件执行。 |
| 空气净化器 | `gtceu:air_scrubber` | 旧+新 | 1/3/1/3 | C | 空气净化涉及环境污染/危害状态；优先由原机处理世界状态。 |
| 研究站 | `gtceu:research_station` | 旧+新 | 2/1/0/0 | B | 真实 CWU/t、总研究量、研究介质和研究数据；ECO 算力不能直接等同 CWU。 |
| 碎岩机 | `gtceu:rock_breaker` | 旧+新 | 1/4/0/0 | B | 相邻流体/方块与环境条件；不能只复刻 JEI 中的物料。 |
| 扫描仪 | `gtceu:scanner` | 旧+新 | 2/1/1/0 | B | 扫描研究与数据载体 NBT；保留解锁链和动态研究结果。 |
| 内燃发电机 | `gtceu:combustion_generator` | 旧+新 | 0/0/1/0 | C | 燃料发电，EU 为输出；应走发电接口。 |
| 燃气轮机 | `gtceu:gas_turbine` | 旧+新 | 0/0/1/0 | C | 燃气发电，EU 为输出；保留燃料效率与原机限制。 |
| 蒸汽涡轮 | `gtceu:steam_turbine` | 旧+新 | 0/0/1/1 | C | 蒸汽发电及冷凝流体循环；不能按耗电加工导入。 |
| 等离子燃料 | `gtceu:plasma_generator` | 旧+新 | 0/0/1/1 | C | 等离子发电与回收流体；GTL 还会派生热交换配方。 |
| 大型锅炉 | `gtceu:large_boiler` | 旧+新 | 1/0/1/1 | C | 大型锅炉的燃料/水/蒸汽及运行状态；优先原机。 |
| 焦炉 | `gtceu:coke_oven` | 旧+新 | 1/1/0/1 | A | 焦炭与焦油生成；按原材料和时长，不虚构原配方的 EU 消耗。 |
| 土高炉 | `gtceu:primitive_blast_furnace` | 旧+新 | 3/3/0/0 | A | 土高炉冶炼；燃料、灰等均保留为真实物料。 |
| 电力高炉 | `gtceu:electric_blast_furnace` | 旧+新 | 3/3/1/1 | B | 线圈温度 ebf_temp、环境与电压；须先实现工艺温度门槛。 |
| 蒸馏塔 | `gtceu:distillation_tower` | 旧+新 | 0/1/1/12 | A | 多馏分蒸馏；保留全部输出及衍生蒸馏室配方的差异。 |
| 蒸发 | `gtceu:evaporation` | 仅旧 | 0/1/1/6 | A | 旧版蒸发类型；只在实际注册该类型的环境导入。 |
| 热解炉 | `gtceu:pyrolyse_oven` | 旧+新 | 2/1/1/1 | B | 线圈、气氛和原机处理规则；保留木炭、焦油与气体。 |
| 裂化机 | `gtceu:cracker` | 旧+新 | 1/0/2/2 | B | 裂化介质、线圈及原机修正；不能仅比较物料与总能量。 |
| 聚爆压缩机 | `gtceu:implosion_compressor` | 旧+新 | 3/2/0/0 | A | 聚爆加工；炸药等真实消耗与副产物不能省略。 |
| 真空冷冻机 | `gtceu:vacuum_freezer` | 旧+新 | 1/1/2/1 | A | 真空冷却/冷凝；保留原配方冷却流体，与 ECO 散热系统分别处理。 |
| 装配线 | `gtceu:assembly_line` | 旧+新 | 16/1/4/0 | B | 研究解锁、数据仓、输入顺序与焊料；须完整保留研究和工艺约束。 |
| 大型化学反应釜 | `gtceu:large_chemical_reactor` | 旧+新 | 3/3/5/4 | B | 多物料化工；检查洁净室与原机修正，保留多个流体输入输出。 |
| 核聚变反应堆 | `gtceu:fusion_reactor` | 旧+新 | 0/0/2/1 | B | 启动 EU、缓存容量、反应等级及持续供电；不等同两流体普通混合。 |
| 占位符 | `gtceu:dummy` | 旧+新 | — | D | 占位/特殊机器入口；不把它作为普通加工类型整体导入。 |
| 机械动力搅拌机 | `gtceu:create_mixer` | 仅旧 | 6/1/2/1 | B | 旧版 Create 条件联动；需要其动力/运行前提，当前新版表中未注册。 |
| 绕线机 | `gtceu:spooling` | 仅新 | 3/1/1/0 | A | 新版绕线配方；只在实际注册该类型的 GTM 版本启用。 |
| 焊接器 | `gtceu:welder` | 仅新 | 4/1/1/0 | A | 新版焊接加工；保留实际物料、流体和能耗。 |

## 6. GTL 新增的全部通用机器配方类型：81 种

**下面 81 种的实际类型 ID 都是 `gtceu:`，不是 `gtlcore:`。** GTLRecipeTypes 调用的是 GTM 的 `GTRecipeTypes.register(...)`，该方法使用 `GTCEu.id(...)`。识别来源时要结合实际类型和 GTL 是否加载，不能仅按命名空间判断来源。

`primitive_void_ore` 为配置控制的可选注册。其余条目也不保证目标整合包必定配置了可执行实例；有些主要依靠整合包脚本或运行期生成。

| 配方/加工名称 | 实际类型 ID | I/O 声明 | 分类 | 联动内容与必须保留的规则 |
| --- | --- | --- | --- | --- |
| 原始虚空采矿 | `gtceu:primitive_void_ore` | 0/0/1/0 | C | 受 enablePrimitiveVoidOre 控制，物料产出在专属 PrimitiveOreMachine 逻辑；优先原机。 |
| 半流质发电机 | `gtceu:semi_fluid_generator` | 0/0/1/0 | C | 半流质燃料发电；EU 为输出。 |
| 超临界蒸汽涡轮 | `gtceu:supercritical_steam_turbine` | 0/0/1/1 | C | 超临界蒸汽发电及冷凝回路；优先保留原机循环。 |
| 火箭引擎 | `gtceu:rocket_engine` | 0/0/1/0 | C | 火箭燃料发电；不能导入为耗电加工。 |
| 硅岩反应堆 | `gtceu:naquadah_reactor` | 0/0/1/0 | C | 硅岩燃料发电；须区分该类型与其他裂变物料加工。 |
| 电力聚爆压缩机 | `gtceu:electric_implosion_compressor` | 2/1/0/0 | A | 电力聚爆；默认配方构建含 1 tick 与 UV 级 EU，不能忽略默认值。 |
| 拆解 | `gtceu:disassembly` | 1/16/0/4 | A | 拆解回收；最多 16 物品和 4 流体输出，包含动态派生配方。 |
| 中子活化 | `gtceu:neutron_activator` | 6/3/1/1 | B | ev_min/ev_max/evt 中子能量与消耗；高级原机也会按 evt 改写 EU。 |
| 热交换机 | `gtceu:heat_exchanger` | 0/0/2/2 | B | 热交换与超临界蒸汽；保留 data.eu 及原机换热规则。 |
| 元素复制机 | `gtceu:element_copying` | 1/1/2/1 | A | 元素复制；模板为非消耗物，长整型数量不能截断。 |
| 闪电处理 | `gtceu:lightning_processor` | 6/1/6/1 | A | 最多 6 流体输入；该 GT 类型名称含闪电，不代表 AE2LT 闪电键。 |
| 脱水机 | `gtceu:dehydrator` | 2/6/2/2 | A | 脱水、分离；保留多产物，若具体原机/配方要求线圈则转条件适配。 |
| 集成矿石处理 | `gtceu:integrated_ore_processor` | 2/9/1/0 | A | 集成选矿；保留完整产率、9 个固体产物和耗水。 |
| 裂变反应堆 | `gtceu:fission_reactor` | 1/1/0/0 | B | FRheat 与裂变堆热状态；ECO 设备温度不能直接替代。 |
| 太空电梯 | `gtceu:space_elevator` | 1/0/0/0 | C | 维护太空电梯状态、部件与关联模块；没有普通配方产物时不能导入。 |
| 恒星热能熔炼 | `gtceu:stellar_forge` | 3/2/9/2 | B | SCTier 恒星约束等级；保留所有气体与多物料。 |
| 部件装配 | `gtceu:component_assembly_line` | 9/1/9/0 | B | CATier 部件装配线等级；最多 9 个流体输入。 |
| 屠宰场 | `gtceu:slaughterhouse` | 1/0/0/0 | C | 实体生成、战利品表与场地状态在 afterWorking 中处理；优先原机。 |
| 温室 | `gtceu:greenhouse` | 3/3/1/0 | B | GreenhouseMachine 检查天空光照，运行中会退回进度；须保留该环境规则。 |
| 超维度熔炼 | `gtceu:dimensionally_transcendent_plasma_forge` | 2/2/2/2 | B | ebf_temp 与特殊线圈的温度规则；按原机校验。 |
| 等离子冷凝 | `gtceu:plasma_condenser` | 2/2/2/2 | A | 等离子冷凝；保留两路流体输入/输出和固体产物。 |
| 稀土离心 | `gtceu:rare_earth_centrifugal` | 1/17/1/1 | A | 稀土分离；最多 17 个固体产物，需要宽输出与副产物容量预留。 |
| 魔力生成 | `gtceu:magic_manufacturer` | 2/0/0/1 | A | 该类型表现为物料转流体；其他模组资源若在实例中额外参与须另识别。 |
| 超临界合成 | `gtceu:sps_crafting` | 3/1/3/1 | A | 超临界合成；保留高阶 EU 与多流体；不从名称推定存在 Mekanism 气体能力。 |
| 物质生成机 | `gtceu:matter_fabricator` | 2/1/0/1 | A | 物质生成；保留原物料、流体和高阶能耗。 |
| 虚空采矿 | `gtceu:void_miner` | 2/4/2/0 | B | 虚空采矿配方及其维度/电路/催化条件；概率产物单独结算。 |
| 精准矿石模式 | `gtceu:large_void_miner` | 1/4/1/0 | B | 精准矿石模式；保留定位材料、钻井液与条件。 |
| 随机矿石模式 | `gtceu:random_ore` | 0/216/1/0 | B | 随机矿石模式，声明最多 216 个输出；需概率模型和完整输出预留。 |
| 虚空流体钻机 | `gtceu:void_fluid_drilling_rig` | 2/0/0/1 | B | 虚空流体采集；保留定位与世界/维度条件，实例脚本需逐条审查。 |
| 世界信息扫描 | `gtceu:world_data_scanner` | 3/1/2/0 | B | 世界信息扫描与结果 NBT；需要对应扫描逻辑。 |
| 湮灭发电机 | `gtceu:annihilate_generator` | 1/1/0/0 | C | 人造恒星发电；EU 为输出且可能产生燃料副产物。 |
| 超能反应 | `gtceu:hyper_reactor` | 0/0/2/0 | C | 超能反应堆燃料发电；保留多燃料消耗和发电效率。 |
| 进阶超能反应 | `gtceu:advanced_hyper_reactor` | 0/0/1/0 | C | 进阶超能燃料发电；不放入加工执行器。 |
| 大型硅岩反应 | `gtceu:large_naquadah_reactor` | 0/0/2/0 | C | 大型硅岩燃料发电；保留两路流体燃料。 |
| 宇宙模拟 | `gtceu:cosmos_simulation` | 1/120/1/18 | B | HarmonyMachine 的氢/氦储备、无线 EU 启动代价，以及 120 固体/18 流体输出。 |
| 宇宙探测 | `gtceu:space_probe_surface_reception` | 2/0/0/1 | B | 探测器接收单元的 beforeWorking 与运行状态；保留维度和探测材料。 |
| 天基宇宙探测 | `gtceu:space_cosmic_probe_receivers` | 2/0/0/1 | B | 天基探测接收的专属运行与维度约束；不能作为任意地点集气。 |
| 衰变加速 | `gtceu:decay_hastener` | 0/1/1/1 | A | 衰变材料/流体加工；保留原配方能耗和全部产物。 |
| 材料回收 | `gtceu:large_recycler` | 1/1/0/0 | A | 材料回收；如为概率回收，保留实际概率而非平均收益。 |
| 质量发生器 | `gtceu:mass_fabricator` | 1/0/1/1 | A | 质量生成；固体增幅材料与流体消耗都保留。 |
| 电路装配线 | `gtceu:circuit_assembly_line` | 16/1/4/0 | B | 电路装配线工艺、原机规则和派生拆解；需多流体与宽输入。 |
| 超时空装配线 | `gtceu:suprachronal_assembly_line` | 16/1/4/0 | B | 研究槽、ResearchManager 派生研究及专属 beforeWorking/onWorking。 |
| 精密组装 | `gtceu:precision_assembler` | 4/1/4/0 | A | 精密物料组装；最多 4 路流体，逐条保留附加条件。 |
| 太空组装 | `gtceu:assembler_module` | 16/1/4/0 | B | SEPMTier、真实太空电梯宿主、模块状态与其减耗/并行规则。 |
| 太空采矿 | `gtceu:miner_module` | 2/6/1/0 | B | 绑定太空电梯宿主和模块等级；保留采矿材料与条件。 |
| 太空钻井 | `gtceu:drilling_module` | 2/0/1/1 | B | 绑定太空电梯宿主和模块等级；保留采集流体及条件。 |
| 渔场 | `gtceu:fishing_ground` | 2/24/0/0 | B | 渔场配方的水域/脚本条件与概率；最多 24 个固体输出。 |
| 方块转换 | `gtceu:block_conversion` | 1/0/0/0 | C | BlockConversionRoomMachine 直接处理世界方块和模拟卡；优先原机。 |
| 培养缸 | `gtceu:incubator` | 6/1/2/1 | A | 培养与生物材料加工；保留多输入和培养液。 |
| PCB工厂 | `gtceu:pcb_factory` | 2/1/2/0 | B | PCBFactoryMachine 的纳米蜂群材料及数量会改写能耗和时长；按原机规则适配。 |
| 熔岩炉 | `gtceu:lava_furnace` | 1/0/0/1 | A | 固体熔化为流体；保留高阶电压和产量。 |
| 大型集气室 | `gtceu:large_gas_collector` | 2/0/0/1 | B | 大型集气；保留电路、维度与实例条件。 |
| 聚合装置 | `gtceu:aggregation_device` | 9/1/0/0 | A | 9 输入聚合加工；保留催化物和实际数量。 |
| 粒子对撞 | `gtceu:super_particle_collider` | 0/0/2/1 | B | 高能流体反应；完整检查启动/工艺 data 与实例条件后适配。 |
| 维度聚焦激光蚀刻 | `gtceu:dimensional_focus_engraving_array` | 2/1/1/0 | B | 线圈、光刻胶、镜片以及由 GTM 激光配方派生的能耗规则。 |
| 精密激光蚀刻 | `gtceu:precision_laser_engraver` | 9/1/1/0 | B | 多材料精密蚀刻；保留镜片、洁净/工艺条件。 |
| 超维度搅拌 | `gtceu:dimensionally_transcendent_mixer` | 9/1/6/1 | A | 9 物品与 6 流体混合；保留全部物料与高阶能耗。 |
| 奇点压缩 | `gtceu:neutron_compressor` | 2/1/0/0 | A | 奇点压缩；重点支持 LongIngredient 大数量与精确批次。 |
| 量子操纵者 | `gtceu:qft` | 18/1/3/1 | A | 量子操纵者；最多 18 物品与 3 流体输入，精确保留长数量。 |
| 龙蛋复制 | `gtceu:dragon_egg_copier` | 1/2/1/0 | A | 龙蛋物料复制；保留模板是否消耗与复制介质。 |
| 创造之门 | `gtceu:door_of_create` | 1/0/0/0 | C | 类型不声明普通输出；可能由实例脚本实现特殊作用，须先确定完整行为。 |
| 基岩钻机 | `gtceu:bedrock_drilling_rig` | 2/1/0/0 | C | BedrockDrillingRig 专属世界采集逻辑；优先由原机执行。 |
| 创造聚合 | `gtceu:create_aggregation` | 1/0/0/0 | C | 类型不声明普通输出；先核对整合包特殊行为与进度作用。 |
| 强引力震爆 | `gtceu:gravitation_shockburst` | 2/1/0/0 | A | 强引力压缩加工；保留高阶 EU 与真实消耗。 |
| 终极物质锻造 | `gtceu:ultimate_material_forge` | 2/2/2/0 | A | 终极物质锻造；长数量、多产物及高阶 EU 需要精确处理。 |
| 戴森球 | `gtceu:dyson_sphere` | 1/0/1/0 | C | EUIO.BOTH 与 DysonSphereMachine 状态，涉及建设/发电；优先原机。 |
| 石化工厂 | `gtceu:petrochemical_plant` | 0/0/2/12 | B | 线圈与石化工艺，多达 12 路流体输出；保留原机修正。 |
| 天气控制 | `gtceu:weather_control` | 1/0/0/0 | C | 无普通输出的天气控制；属于世界操作，应由原机/脚本执行。 |
| 深度化学扭曲仪 | `gtceu:distort` | 9/9/9/9 | B | ebf_temp 与线圈；9/9/9/9 宽 I/O，保留全部化工物料。 |
| 纳米蜂群工厂 | `gtceu:nano_forge` | 6/1/3/0 | B | nano_forge_tier 与机体/纳米蜂群规则；不能只检查电压。 |
| 燃料精炼 | `gtceu:fuel_refining` | 3/0/6/1 | B | ebf_temp、线圈和 6 路流体输入；保留燃料精炼的原工艺。 |
| 原子能激发 | `gtceu:atomic_energy_excitation` | 3/0/6/2 | B | ebf_temp、线圈与 6 进 2 出流体；保留全部物料。 |
| 湿法碾磨 | `gtceu:isa_mill` | 2/1/1/0 | B | grindball 对应研磨球仓、等级和损耗；不能把球变成免费模板。 |
| 浮游选矿 | `gtceu:flotating_beneficiation` | 2/0/1/1 | A | 浮游选矿与矿浆；保留药剂和矿浆流体数量。 |
| 真空干燥 | `gtceu:vacuum_drying` | 1/6/1/2 | B | ebf_temp 与线圈；多固体和流体产物。 |
| 溶解 | `gtceu:dissolution_treatment` | 2/2/2/1 | A | 矿物溶解；完整保留酸液、溶液与残渣。 |
| 煮解 | `gtceu:digestion_treatment` | 1/1/1/1 | B | ebf_temp 与线圈的煮解工艺；保留原液体和材料。 |
| 木化工厂 | `gtceu:wood_distillation` | 1/1/1/16 | A | 木化工；最多 16 路流体输出，不能只保留木炭或焦油。 |
| 脱硫 | `gtceu:desulfurizer` | 0/1/1/1 | A | 流体脱硫；保留固体硫等副产物。 |
| 分子装配 | `gtceu:molecular_assembler` | 3/12/1/1 | C | 专属 ME 分子装配任务与矩阵逻辑；应对接原机任务接口。 |
| 碎片世界采集 | `gtceu:fragment_world_collection` | 3/12/1/1 | B | 碎片世界采集；先核对实例世界条件、采集代价和 12 个固体产物。 |

## 7. 容易遗漏的专用配方与其他配方

### 7.1 新版专用装备加工：额外 1 种

| 类型 | 所属版本 | 分类 | 内容与适配要求 |
| --- | --- | --- | --- |
| `gtceu:equipment_foundry` | 本次 GTM 新开发版 | B | `EquipmentFoundryRecipe`，给模块化装备安装模块；会读取并修改输入装备状态，结果取决于真实输入，不能复制固定展示结果 |

它通过 Registrate 注册为普通 Minecraft `RecipeType<EquipmentFoundryRecipe>`，不是 `GTRecipeType`。因此，**143 个通用类型之外，还有这 1 个专用类型，共 144 个不重复的 GT 来源加工/控制类型**。只枚举 `GTRegistries.RECIPE_TYPES` 会漏掉它。

依据：新版 `common/data/GTRecipeTypes.java` 的 `EQUIPMENT_FOUNDRY_RECIPES`、`common/recipe/type/EquipmentFoundryRecipe.java`、`data/recipe/misc/EquipmentFoundryRecipes.java`。专用配方构建调用也列入索引的辅助调用部分。

### 7.2 手工、原版加工与动态合成序列化器

这些配方也属于“可以联动的配方”，但不属于上面 143 个 `GTRecipeType`，不能给每一种合成序列化器都算一台 GT 机器。

| 配方来源 | 推荐入口 | 需要保留的内容 |
| --- | --- | --- |
| GTM/GTL 的普通有序、无序合成 | 原生 AE 合成样板/分子装配器 | 工具损耗、容器返还、原料标签和真实 `assemble` 结果 |
| 原版熔炼、烧炼、烟熏、营火、切石、锻造配方中的 GT 内容 | 各自原生类型或原机处理样板 | 只接入实例实际存在的配方，不把支持的序列化器当成已存在的配方 |
| GT 严格有序合成 | AE 合成路径，逐条核对 | 严格位置/匹配规则，不按普通无序配方简化 |
| GT 能量转移、工具头替换、流体容器合成、外观覆盖板 | 专用动态合成路径或原合成执行器 | 输入物品的电量、NBT、流体、工具属性和返还行为 |
| GTL `gtlcore:prank_fish` | 原生合成路径 | 真实结果由合成配方决定；JEI 伪装显示不作为产物依据 |
| GTL `gtlcore:plushie_conversion` | 原生合成路径 | `assemble` 继承输入的染色、名称及其他 NBT，不能只读静态 result |

旧版 GTM 可见 `gt_recipe_serializer`、`strict_shaped_recipe_serializer` 等命名，新版改为 `machine`、`crafting_shaped_strict` 等。**JSON 的 `type` 通常表示序列化器，不应一概等同运行时机器 RecipeType。** GT 适配应读取运行时配方对象与其真实类型，不依赖一个跨版本硬编码的 JSON `type` 字符串。

### 7.3 不在静态配方表里的机器能力

| 来源 | 为什么不能仅从注册类型导出“全部配方” | 覆盖方式 |
| --- | --- | --- |
| GTM 矿机、大矿机、基岩矿机、流体钻机 | 依赖世界矿物/矿脉、基岩资源和机体状态，部分行为不由静态物料表表达 | 保留原机，按其资源与世界逻辑调度 |
| GTL 原始虚空采矿、基岩钻机、屠宰场、方块转换 | 在机体运行回调中产生物品、处理实体或更改方块 | 接原机；若要虚拟化，须单独设计完整行为合同 |
| GTL 计算提供机器、研究计算组件 | 计算输出或研究任务不等于普通可库存物品 | 对接对应计算/CWU 协议，另设能力验收 |
| GTL 分子装配矩阵 | 有自己的 ME 任务与装配逻辑 | 对接任务接口和恢复协议，避免重复接单 |
| JEI/EMI 的矿脉、矿物处理说明、多方块说明页 | 是展示分类或提示，不一定是可执行 `Recipe` | 用作展示，不当作免费资源配方 |

## 8. 想尽量覆盖全部配方，需要补齐的执行规则

### 8.1 EU 与电压体系

保留配方的 EU/t、时长、电压等级和实际安培约束。新版 GTM 的 EU 内容使用 `EnergyStack(voltage, amperage)`；旧版配方和 GTL 的扩展不能按同一个字段布局强行读取。

建议新增独立的 GT 能源适配能力，或明确设计可配置的 EU 转换合同。即使以后允许 FE/AE 供能，也必须保留工艺电压门槛。本文不指定固定 EU↔FE↔AE 比例，不把发电配方的 EU 输出反向解释为成本。

启动 EU 要与持续 EU 分开。核聚变反应堆的启动能量、GTL HarmonyMachine 的无线 EU 启动支付与氢/氦储备，都不是“配方总能量除以时长”能表达的。

### 8.2 非消耗输入、电路、工具与催化物

GT `Content` 的概率语义参与消耗规则，`notConsumable` 不能转换成普通消耗输入。模具、模头、镜片、编程电路和材料模板要保留匹配与占用语义。电路号必须区分配方，不能把同料但不同电路的配方合并。

工具损耗、容器返还、KubeJS `ingredientActions` 以及原机额外消耗另行处理。例如艾萨研磨的研磨球还有等级/仓位/损耗规则，不能因“看起来像催化物”就变成永久免费的模板。

### 8.3 多路物料与大数量

至少覆盖本次发现的实际声明上限：GTL 随机矿石 216 个物品输出、宇宙模拟 120 个物品与 18 路流体输出、木化工 16 路流体输出、量子操纵者 18 个物品输入。它们是各自类型的不同上限，不应拼成一种假想配方。

GTL 的 `LongIngredient.actualAmount` 不能通过 `getAmount()` 的 int 显示值或展示 `ItemStack` 还原。输入输出、网络运输、批量乘法、持久化与显示都需统一使用精确数量；超过当前合同能力的配方应明确跳过，不能饱和到 int 上限后执行。

每次执行前预留全部必出与可能产物的去向。主产物能放下而副产物放不下时，应按设计暂停；不得删除副产物来使配方“可联动”。

### 8.4 概率输入与产物

保留 `chance / maxChance / tierChanceBoost`、每种能力的 ChanceLogic、范围数量与批次语义，不能把概率显示的物品都当作必出产物，也不能直接用数学期望替代真实产出。

AE 需要可靠的任务完成条件。可优先把必出产物作为可承诺产物、随机产物作为副产物；只有随机主产物的配方则需要明确的持续生产/补足目标策略，或先走原机。不能让 ECO 承诺固定产量后永久等待未掷出的产物。

随机决定与批次状态需要一起保存。断电、输出堵塞或重载后，不得重新抽签以改变已确定产物，也不能在返还重试中重复产出。

### 8.5 每 tick 物料、CWU 和持续条件

GTRecipe 有独立的 `inputs/outputs/tickInputs/tickOutputs`。tick 输入不只有 EU；不能统一扁平化为开工时一次扣料。持续流体、持续计算、启动量和完成时产物都应在各自时机结算。

研究站要保留 CWU/t、总研究量、研究介质和研究数据。ECO 计算矩阵容量与 GT 的 CWU 协议属于不同概念，需要显式桥接。研究尚未完成时，装配线不能因材料齐全就绕过解锁。

### 8.6 工艺温度与设备散热

明确分开三件事：GT 线圈/工艺温度，ECO 设备温度，配方里的冷却介质。

- `ebf_temp` 和线圈能力决定能不能加工，不应由 ECO 当前热量达到某个数值来替代。
- ECO 温度/超频/冷却控制设备自身能否继续工作，不能删除 GT 配方要求的冷却流体。
- GTL 的裂变热 `FRheat`、中子活化 `ev_min/ev_max/evt` 属于独立工艺状态。先保留其规则，再讨论统一 UI 或数值平衡。

### 8.7 条件与机体专属字段

需要覆盖洁净室/无菌环境、维度、生物群系、Y 高度、昼夜、雨雷、相邻流体/方块、排气、环境危害，以及可选任务/阶段条件。GTL 还注册了 `GravityCondition` 重力条件。不同版本条件集合有差别，按实际注册内容读取，不能只覆盖几个常见条件后自动放行其余。

GTL 还存在机体检查字段，例如 `SCTier`、`CATier`、`SEPMTier`、`nano_forge_tier`、`grindball`、`FRheat`、`ebf_temp`。字段有的在机体或 recipeModifier 中消费，不一定出现在通用条件列表。天空光照、太空电梯宿主、PCB 纳米蜂群减耗等也不能只从类型 ID 还原。

**未知执行能力、未知条件或未实现的机体字段，应记录原因并拒绝该条配方导入。** 已确认仅用于展示的数据可以有单独的可忽略规则。

### 8.8 来源、去重、派生和恢复

读最终运行时配方，而不是重建一份未经实例修改的上游配方。保留原始 ID、类型、来源与处理方式；JEI/EMI 应显示真实来源、条件和费用。

GTL 的 `RecipeModify` 和 `GenerateDisassembly` 会派生拆解、热交换及维度聚焦激光配方，GTM 也有研究、蒸馏室等构建钩子。不能只扫描最初的 Java 声明就宣称这些派生项不存在，也不能在配方重载时叠加生成重复项。

去重时把非消耗输入、电路、概率模型、研究与环境条件、机体等级、所有产物都纳入合同。物料相同但研究不同，或总能量相同但电压不同，都不能直接判为同一可替换配方。保存中的批次必须仍能按原始 ID 找回其准确合同。

## 9. 建议的落地顺序

| 阶段 | 目标 | 明确边界 |
| --- | --- | --- |
| 0：实例清点 | 确定实际 GTM/GTL JAR、脚本、数据包及有效配方 | 不混用旧 API 与新开发版，不复活已被整合包删除的配方 |
| 1：通用基础 | 可选加载、运行时枚举、来源、EU、精确数量、持续结算及重载 | 已知能力白名单；不支持项有原因记录 |
| 2：首批确定加工 | A 类中无未实现条件、无概率、无动态行为的具体配方 | 优先研磨/冶炼、成型、组装、混合、电解/离心、蒸馏、冷却 |
| 3：扩展加工 | 非消耗物、概率、复杂谓词、宽 I/O 和大数量 | 再覆盖矿物副产物、GTL 拆解、稀土、木化工、复制、QFT 等 |
| 4：条件加工 | B 类的研究、CWU、线圈、专用等级和宿主规则 | 逐个能力实现与验收，不能一次允许整个类型 |
| 5：原机协作 | C 类发电、世界交互、专属 ME 任务 | 以原机执行和状态反馈实现覆盖；后续按需求独立设计虚拟化 |

建议把新的 GT 配方导入做成可选配置，初始关闭，并允许按类型、配方 ID 和能力筛选。配置名、升级卡和 GT 外观/机型尚未设计或注册；它们不应被本文视为已经批准的实现范围。

这样开发时无需为 143 个类型各写一套材料转换器；可按“共同物料能力 + 独立条件能力 + 少量专机适配”逐步扩大覆盖，同时保持来源与规则准确。

## 10. 如何得到目标整合包逐条、最终生效的全部配方

本次有上游源码和本工程工作树，没有指定要审计的 GT 整合包实例。因此还没有“该实例最终生效 N 条配方”的统计，也没有列出循环展开后每一条的最终材料 ID。

拿到实例后，应读取其 `mods`、`kubejs/server_scripts`、`kubejs/startup_scripts`、数据包以及服务器配置，并在相关注册/脚本完成之后导出：

| 导出字段 | 用途 |
| --- | --- |
| 配方 ID、真实类型、序列化器、来源 | 可追踪与可重载，区分同名来源 |
| 输入谓词、真实数量、是否消耗 | 精确匹配模具、电路、NBT、长数量与原料 |
| 全部输出、概率、范围与 ChanceLogic | 正确产率、任务完成和副产物处理 |
| tick 输入/输出、持续 EU/t、启动 EU、CWU/t | 正确区分开工、每 tick 和完工结算 |
| 时长、电压、安培、温度、研究与其他条件 | 保留技术路线和工艺门槛 |
| 原机相关 modifier/宿主能力 | 标记仅靠 RecipeManager 无法表达的行为 |
| ECO 适配结果与原因 | 分成已可导入、等待某项能力、建议原机执行、不可执行占位 |

最终注册表仍不等于世界动态行为的全集；矿机、钻机、屠宰、天气、ME 任务等要再核对机体逻辑。游戏内验证也应单独记录，不用源码扫描结果代替。

## 11. GTLCore 仓库现有的全部静态 JSON 配方：29 份

下表是这次参考提交实际自带的文件，**不是 GTL 整合包所有配方**。其中 JSON 的 `type` 按序列化器记载；具体原料和有序合成图案完整保留在配套索引的 `static_recipe_json` 中。

| 配方 ID | JSON 序列化器 `type` | 静态结果物品 |
| --- | --- | --- |
| `gtlcore:assembler_matrix_frame` | `minecraft:crafting_shaped` | `expatternprovider:assembler_matrix_frame` × 1 |
| `gtlcore:assembler_matrix_glass` | `minecraft:crafting_shaped` | `expatternprovider:assembler_matrix_glass` × 2 |
| `gtlcore:assembler_matrix_wall` | `minecraft:crafting_shaped` | `expatternprovider:assembler_matrix_wall` × 1 |
| `gtlcore:circuit_infinity_cell` | `minecraft:crafting_shaped` | `gtlcore:circuit_infinity_cell` × 1 |
| `gtlcore:cleaning_gravity_configuration_maintenance_hatch` | `minecraft:crafting_shaped` | `gtceu:cleaning_gravity_configuration_maintenance_hatch` × 1 |
| `gtlcore:emitter_manager_terminal` | `minecraft:crafting_shapeless` | `gtlcore:emitter_manager_terminal` × 1 |
| `gtlcore:fishbig_tm` | `gtlcore:prank_fish` | `gtlcore:fishbig_tm` × 1 |
| `gtlcore:gravity_configuration_hatch` | `minecraft:crafting_shaped` | `gtceu:gravity_configuration_hatch` × 1 |
| `gtlcore:law_cleaning_gravity_configuration_maintenance_hatch` | `minecraft:crafting_shaped` | `gtceu:law_cleaning_gravity_configuration_maintenance_hatch` × 1 |
| `gtlcore:me_chamber_manager_terminal` | `minecraft:crafting_shapeless` | `gtlcore:me_chamber_manager_terminal` × 1 |
| `gtlcore:me_pattern_relay` | `minecraft:crafting_shaped` | `gtlcore:me_pattern_relay` × 2 |
| `gtlcore:pig_plushie` | `minecraft:crafting_shaped` | `gtlcore:pig_plushie` × 1 |
| `gtlcore:pig_plushie_from_slim` | `gtlcore:plushie_conversion` | `gtlcore:pig_plushie` × 1 |
| `gtlcore:quantum_link` | `minecraft:crafting_shaped` | `ae2:quantum_link` × 1 |
| `gtlcore:slim_pig_plushie` | `gtlcore:plushie_conversion` | `gtlcore:slim_pig_plushie` × 1 |
| `gtlcore:sterile_cleaning_gravity_configuration_maintenance_hatch` | `minecraft:crafting_shaped` | `gtceu:sterile_cleaning_gravity_configuration_maintenance_hatch` × 1 |
| `gtlcore:tag_filter_me_stock_bus_part_machine` | `minecraft:crafting_shapeless` | `gtceu:tag_filter_me_stock_bus_part_machine` × 1 |
| `gtlcore:tag_filter_me_stock_hatch_part_machine` | `minecraft:crafting_shapeless` | `gtceu:tag_filter_me_stock_hatch_part_machine` × 1 |
| `gtlcore:tag_view_cell` | `minecraft:crafting_shapeless` | `gtlcore:tag_view_cell` × 1 |
| `gtlcore:throughput_monitor` | `minecraft:crafting_shapeless` | `gtlcore:throughput_monitor` × 1 |
| `gtlcore:throughput_monitor_configurator` | `minecraft:crafting_shaped` | `gtlcore:throughput_monitor_configurator` × 1 |
| `gtlcore:throughput_monitor_terminal` | `minecraft:crafting_shapeless` | `gtlcore:throughput_monitor_terminal` × 1 |
| `gtlcore:wireless_emitter_manager_terminal` | `minecraft:crafting_shaped` | `gtlcore:wireless_emitter_manager_terminal` × 1 |
| `gtlcore:wireless_grid_energy_monitor` | `minecraft:crafting_shaped` | `gtlcore:wireless_grid_energy_monitor` × 25 |
| `gtlcore:wireless_me_chamber_manager_terminal` | `minecraft:crafting_shaped` | `gtlcore:wireless_me_chamber_manager_terminal` × 1 |
| `gtlcore:wireless_throughput_monitor_terminal` | `minecraft:crafting_shaped` | `gtlcore:wireless_throughput_monitor_terminal` × 1 |
| `gtlcore:wireless_universal_terminal/upgrade_emitter_manager` | `ae2wtlib:upgrade` | — |
| `gtlcore:wireless_universal_terminal/upgrade_me_chamber_manager` | `ae2wtlib:upgrade` | — |
| `gtlcore:wireless_universal_terminal/upgrade_throughput_monitor` | `ae2wtlib:upgrade` | — |

## 12. 证据与复查方法

### 关键源码

以下参考路径相对于对应参考仓库根目录，行号应按本节锁定的提交查看，不以本地落后或有修改的检出文件代替。

| 证据 | 路径/符号 | 支持的结论 |
| --- | --- | --- |
| GTM 通用类型 | `src/main/java/com/gregtechceu/gtceu/common/data/GTRecipeTypes.java` | 全部通用类型、条件注册、来源 namespace、默认构建/派生 |
| GCyM 类型 | 旧 `.../common/data/GCyMRecipeTypes.java`；新 `.../common/data/GCYMRecipeTypes.java` | 合金冶炼类型也需计入 GTM |
| GTM 完整合同 | `.../api/recipe/GTRecipe.java`、`.../api/recipe/content/Content.java` | tick 能力、概率、条件、ingredientActions 和 data |
| 新版 EU | `.../api/recipe/ingredient/EnergyStack.java` | 电压与安培分开，不是普通 FE 总量 |
| GTM 条件注册 | `.../common/data/GTRecipeConditions.java` 与 `.../common/recipe/condition/` | 环境、研究、启动 EU 与可选条件 |
| GTL 版本 | `gradle.properties`、`build.gradle` | 声明依赖 GTM 1.4.4 |
| GTL 全类型 | `src/main/java/org/gtlcore/gtlcore/common/data/GTLRecipeTypes.java` | 81 个新增通用类型及 I/O 声明 |
| GTL 派生 | `.../common/recipe/RecipeModify.java`、`.../data/recipe/GenerateDisassembly.java` | 拆解、热交换、激光等派生入口 |
| GTL 大数量 | `.../api/recipe/ingredient/LongIngredient.java` | actualAmount 为 long，显示层 int 不能替代 |
| GTL 工艺/世界/宿主 | `.../common/data/machines/`、`.../common/machine/multiblock/` | 类型表之外的运行规则 |
| GTL 静态 JSON | `src/main/resources/data/gtlcore/recipes/` | 本次全部 29 份静态配方 |

索引中的每个类型和构建点都有 `profile + source + line`。辅助调用仅扫描列明的合成 helper、`copyFrom` 和装备配方构建入口；没有把任意 Java 方法调用推断成配方。参数值保留源码表达式，不在静态审计中执行 Java、展开材料循环或推断脚本效果。

例如查看确切的 GTL 新版类型表：

```powershell
git -C 'E:\Minecraft Project\GTLCore' show '6ca516d0933490ecf42d84d5f91648e5f2a0e48e:src/main/java/org/gtlcore/gtlcore/common/data/GTLRecipeTypes.java'
```

查看对应的旧版 GTM 基础类型：

```powershell
git -C 'E:\Minecraft Project\GregTech-Modern' show 'e3844ba943e2388c0856617ae4f8b473a0ae1b64:src/main/java/com/gregtechceu/gtceu/common/data/GTRecipeTypes.java'
```

本次验证限于源码与文档：核对注册类型完整性、版本差异、参数提取、JSON 完整性和链接。没有修改业务代码、运行 Gradle、启动客户端或进行 GT 实例功能验收。
