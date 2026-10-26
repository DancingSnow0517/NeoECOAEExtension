# 合成状态页的机器加工进度

适用版本：NeoECOAE 1.21.1。实现范围与源码核对日期：2026-10-26。

状态页的细进度条表示机器**当前加工周期**，整单剩余数量仍由 CPU 的执行账本统计。
机器完成一个周期后进度条可能归零；这不表示整单回退。

## 接入范围

| 执行端 | 目标来源 | 读取内容 | 任务关联 |
| --- | --- | --- | --- |
| ECO 大型集成工作站 | 通讯接口实际选择的工作站 | 当前头批次、分段进度、并行数、暂停原因 | 目标工作站及当前批次产物 |
| FX 工作核心，包括虚拟批量路径 | 样板总线实际派发成功的工作核心 | 线程快照、并行数量、缺电、缺冷却液、等待回填 | 订单 UUID + 产物及组件精确匹配 |
| ExtendedAE 装配矩阵 | 样板核心所属矩阵 → 合成核心 | 合成线程进度、网络暂停、产物缓冲 | 目标矩阵及当前配方产物 |
| ExtendedAE Plus 超级、终极装配矩阵 | 样板核心或控制器 → 唯一主控制器 | 活跃批次、回退线程、排队任务、产物缓冲 | 目标矩阵及当前配方产物 |
| Mekanism 普通配方机器 | AE2 样板供应器实际投料方向 | 已缓存配方、当前周期、暂停及输出空间错误 | 投料目标及当前配方产物 |
| Mekanism 工厂 | 同上 | 按独立加工槽筛选和汇总，不混入其他配方的槽位 | 同上 |
| Mekanism Extras 工厂 | 同上，独立适配其工厂基类 | 各槽缓存配方、进度与活动状态 | 同上 |
| Mekanism MoreMachine 工厂 | 同上，独立适配其工厂基类 | 各槽缓存配方、进度与活动状态 | 同上 |
| 继承上述 MEK 普通机器或工厂基类的附属机器 | 沿用对应目标及读取路径 | 沿用其基类契约 | 同上 |

MEK 的配方输出匹配包含物品及组件、流体、化学品，以及同时包含多种输出的记录类型。
MEK Extras 和 MoreMachine 工厂没有继承原版工厂类，不能仅靠原版工厂的类型判断覆盖。

第三方机器没有提供 ECO 订单 UUID，因此悬浮提示会明确写出“按投料目标及配方关联，可能与其他订单共用”。
两张订单同时使用同一台机器生产相同产物时，看到的可能是同一个物理周期，不能将其当作独占订单的百分比。

## 显示与边界

- 悬浮提示显示当前周期汇总、机器数量、机器状态、位置、并行数和原因；最多同步前三台机器的明细，其余机器仍计入汇总。
- 同一物理工厂中的多个槽位合并为一台机器，按加工量加权；矩阵共享执行核心与环形目标链接会去重。
- 排队配方不继承另一活跃配方的进度。缺乏可读取总时长的机器只显示状态，百分比显示为 `—`。
- MEK 缓存配方已失效、没有匹配产物、没有已加载目标时，不显示推测出来的进度。
- 当前自动目标追踪覆盖 AE2 的 `PatternProviderLogic` 投料路径、继承该路径的供应器、ECO 自有派发以及上述矩阵。重写整条投料路径的其他供应器需要实现目标接口；不会把周围机器一律当作已投料机器。
- 材料先进入箱子、管道或其他中转设施时，不推断材料最终到达哪台机器。此类中转系统需通过目标接口或状态读取器显式提供后续链接。
- 只读取已加载的服务端方块实体，不加载区块，不调用配方处理、试插入或改变机器运行状态的方法。
- 每 CPU 每 tick 最多 32 次目标扫描、全服每 tick 最多 256 次；正常刷新间隔为 10 tick，仅在状态页查看期间采样。矩阵的后续目标也消耗相同预算。
- 单个可选读取器不兼容时会隔离该读取失败，其他目标继续采样，合成执行不受影响。
- 目标随 CPU 订单附件存档；现场进度重新采样。保持原附件 ID，以兼容已有存档。
- 更新前尚未记录目标的旧 MEK、FX 或矩阵投料不能事后追溯；更新后的成功派发开始记录这些目标。

## 附属接入 API

目标捕获接口为 `api.me.diagnostics.ECOProcessingTargetProvider`，返回最近一次成功投料的维度和方块位置。
应在派发成功时更新目标，失败派发不应创建新的观测目标。

读取扩展接口为 `ECOProcessingStatusReader`，注册入口为 `ECOProcessingStatusRegistry.register(id, reader)`：

```java
ECOProcessingStatusRegistry.register(
    ResourceLocation.fromNamespaceAndPath("my_addon", "machine_progress"),
    new MyProcessingStatusReader());
```

读取器通过 `supports(BlockEntity)` 声明适用类型，通过 `read(machine, context)` 返回：

- `context.output()`：此状态条查询的产物键，必须按组件正确筛选。
- `context.jobId()`：ECO 订单 UUID；旧式未绑定调用可为空。机器拥有 UUID 时应精确比较。
- `Observation.sample()`：当前物理机器的只读快照，可为空。
- `Observation.links()`：需要在后续预算步骤中解析的真实执行端，可为空列表。

外部注册读取器优先于内置读取器；注册 ID 不可重复。读取器只能观察已有运行数据，不得触发材料插入、加工、区块加载或修改世界。

## 源码核对依据

已 fetch 对应版本分支；ExtendedAE Plus 的参考工作树从最新远端分支创建，保留原工作树的本地修改。

| 仓库 | 分支及核对提交 | 关键契约 |
| --- | --- | --- |
| Applied-Energistics-2 | `1.21.1` / `db17504a8` | `PatternProviderLogic.pushPattern` 的专用机器调用、投料方向和 `sendStacksOut` 清理顺序 |
| ExtendedAE | `1.21.1-neoforge` / `1f86f927` | `TileAssemblerMatrixCrafter.threads`、`CraftingThread.myPlan`、`getCraftingProgress()`、矩阵成员迭代器 |
| ExtendedAE_Plus | `1.21.1` / `8ba8bd38` | `SuperAssemblerMatrixCluster` 的主核心、`activeBatch`、`fallbackThreads`、`batchQueue`、`outputBuffer` |
| Mekanism | `1.21.x` / `bcd7a8bf59` | 单机及工厂的缓存配方、加工时间、各槽 `activeStates`、缓存错误集合 |
| Mekanism-Extras | `1.21.1` / `eb7afba` | 独立 `TileEntityExtraFactory` 基类的槽位缓存和进度 |
| Mekanism-MoreMachine | `1.21.1` / `0e09a2e` | 独立 `TileEntityMoreMachineFactory` 基类的槽位缓存和进度 |

自动验证覆盖真实依赖中的 MEK 单机、原版工厂、ExtendedAE 核心和线程；可选附属通过与核对源码契约相同的 ABI 测试夹具验证。
另检查实际 AE2 依赖字节码中的两个强制 Mixin 调用点，并验证方向在立即清空发送缓冲前被捕获。
这些检查与客户端真实存档中的交互、渲染验收分开记录。
