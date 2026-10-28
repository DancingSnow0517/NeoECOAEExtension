# NeoECOAE API 接入指南

[English](API.md) · [源码接口](../src/main/java/cn/dancingsnow/neoecoae/api) · [发布记录](releases/20.5.3.md)

本文面向附属模组作者，适用于 **Minecraft 1.20.1 / Forge / Java 17**。组织方式参考 1.21.1 main 的 `6dfbee3a`，接口、示例与限制按本分支源码核对。模组版本以 [gradle.properties](../gradle.properties) 的 `mod_version` 为准，依赖以 [build.gradle](../build.gradle) 和 [mods.toml](../src/main/resources/META-INF/mods.toml) 为准。请使用与目标 JAR 同一修订的文档。

## 接入与兼容边界

API 随完整模组 JAR 分发，没有独立的 `-api` 产物。本分支没有配置 Maven 发布目标；可在已配置 Forge 47、AE2 15 和 Java 17 的附属项目中引用本地 JAR。当前构建使用 Forge 47.4.28、AE2 15.4.11、LDLib 1.0.52.a 和 GuideME 20.1.15；这些是构建版本，实际允许范围见 `mods.toml`。

```groovy
dependencies {
    modCompileOnly files("libs/neoecoae-20.5.3.jar")
    // 本工程使用的 Legacy ModDevGradle 环境；其他构建插件使用各自的开发运行配置。
    modRuntimeOnly files("libs/neoecoae-20.5.3.jar")
}
```

示例版本替换为实际测试的版本。`modCompileOnly/modRuntimeOnly` 使用本工程的 Legacy ModDevGradle 模组依赖配置，其他构建插件需使用相应的重映射方式。已通过其他方式安装时省略运行依赖；不要把完整 NeoECOAE 再打包进附属 JAR。在附属的 `META-INF/mods.toml` 中声明依赖：

```toml
[[dependencies.examplemod]]
modId = "neoecoae"
mandatory = true
versionRange = "[20.5.3]"
ordering = "AFTER"
side = "BOTH"
```

替换 `examplemod` 与版本范围。可选接入使用 `mandatory = false`，并在确认模组存在后才加载引用 ECO 类型的兼容类。可选依赖声明本身不隔离 Java 类链接。许可证见 [LICENSE](../LICENSE)。

本分支没有 1.21.1 的 `ECOAddonApi.VERSION/supports` 能力探针，也没有该分支的通用扩展注册器。按本文件列出的实际签名接入并固定所测试的版本，不能将 1.21.1 的 API 版本承诺套用到此分支；两个版本的 JAR 不能互换。

[ECOPlanningService](../src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOPlanningService.java) 是本文使用的公开规划入口，但位于实现包，应固定模组版本。求解器、Worker、CPU 内部状态和存档引擎不作为附属扩展入口。已弃用的 `api.me.ECOCraftingCPU`、`ECOCraftingCPULogic`、`ExecutingCraftingJob` 仅保留已有二进制/Mixin 目标，新接入不要创建或继承这些类型。

## 入口索引

| 需求 | 入口 |
| --- | --- |
| 规划、网络选项 | [ECOPlanningService](../src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOPlanningService.java)、[ECOPlannerRequester](../src/main/java/cn/dancingsnow/neoecoae/api/me/planning/ECOPlannerRequester.java)、[ECOCraftingNetworkSettings](../src/main/java/cn/dancingsnow/neoecoae/api/me/network/ECOCraftingNetworkSettings.java) |
| 精确父订单 | [ECOBigOrderRequest](../src/main/java/cn/dancingsnow/neoecoae/api/me/bigorder/ECOBigOrderRequest.java)、[ECOBigOrderAdmission](../src/main/java/cn/dancingsnow/neoecoae/api/me/bigorder/ECOBigOrderAdmission.java) |
| 任务事件、附件、暂停策略 | [ECOCraftingLifecycle](../src/main/java/cn/dancingsnow/neoecoae/api/me/lifecycle/ECOCraftingLifecycle.java)、[ECOCraftingJobAttachmentRegistry](../src/main/java/cn/dancingsnow/neoecoae/api/me/attachment/ECOCraftingJobAttachmentRegistry.java)、[ECOCraftingDispatchPolicyRegistry](../src/main/java/cn/dancingsnow/neoecoae/api/me/dispatch/ECOCraftingDispatchPolicyRegistry.java) |
| 普通并行、FastPath | [ECOParallelCraftingProvider](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOParallelCraftingProvider.java)、[ECOFastPathDispatchProvider](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOFastPathDispatchProvider.java)、[ECOFastPathFacade](../src/main/java/cn/dancingsnow/neoecoae/api/me/ECOFastPathFacade.java) |
| 产物、虚拟完成、进度 | [ECOCraftingOutputClaimSink](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOCraftingOutputClaimSink.java)、[ECOVirtualCraftingCompletionSink](../src/main/java/cn/dancingsnow/neoecoae/api/me/completion/ECOVirtualCraftingCompletionSink.java)、[ECOCraftingProgressView](../src/main/java/cn/dancingsnow/neoecoae/api/me/progress/ECOCraftingProgressView.java) |
| 样板语义、执行授权、导入 | [ECOPatternSemanticsRegistry](../src/main/java/cn/dancingsnow/neoecoae/api/me/pattern/ECOPatternSemanticsRegistry.java)、[ECOExecutionOptimizationRegistry](../src/main/java/cn/dancingsnow/neoecoae/api/me/pattern/ECOExecutionOptimizationRegistry.java)、[IECOPatternStorageService](../src/main/java/cn/dancingsnow/neoecoae/api/IECOPatternStorageService.java) |
| 存储盘、槽位、精确插入 | [ECOStorageCells](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ECOStorageCells.java)、[ICellHost](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ICellHost.java)、[ECOBigIntegerStorage](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ECOBigIntegerStorage.java) |
| 工作站配方、固定模块 | [工作站配方与模块](#工作站配方与模块) |
| 主机面板外观 | [IECOTier](../src/main/java/cn/dancingsnow/neoecoae/api/IECOTier.java)、[IHostPanelTextureProvider](../src/main/java/cn/dancingsnow/neoecoae/api/IHostPanelTextureProvider.java)；见[面板与模型](#面板与模型) |

## 合成流体仓

[ECOFluidInputHatchBlockEntity](../src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOFluidInputHatchBlockEntity.java) 与 [ECOFluidOutputHatchBlockEntity](../src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOFluidOutputHatchBlockEntity.java) 提供 `public FluidTank getFluidTankHandler()`，返回 Forge 的 `net.minecraftforge.fluids.capability.templates.FluidTank`。输入仓另提供 `public long getStoredFluidAmount()`，以 mB 返回存量。

本分支两种仓均使用 16,000 mB 的 int 容量储罐；输入仓的 long 返回类型不意味着超 int 存储能力。`tank` 字段仍为 `FluidTank`，不提供 1.21.1 的 `LongFluidTank` 或双字段描述符兼容层。新附属通过 getter 访问，不替换储罐引用。

普通管道集成查询 `blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side)`，并遵循 `LazyOptional` 失效生命周期。输出仓对外能力仅允许抽取；不要用 getter 绕过限制。处理器操作遵循下文服务器线程、模拟/执行和实际接收量规则。

## 线程、注册与资源所有权

规划入口先读取库存快照，因此在所属服务器线程调用。提交、供应器接收、存储变更和产物认领也在服务器线程执行。调用方在开始计算和提交前检查玩家/机器的网络访问权限；底层接口不代替鉴权。

规划返回 `Future`：后续 tick 检查 `isDone()`，完成后才 `get()`，消费前清除持有的请求引用，确保只提交一次。菜单关闭、机器移除或网络失效时取消请求。处理取消、`ExecutionException`、中断与执行器队列满时的 `RejectedExecutionException`；不要在 tick 中阻塞等待。

注册通常在初始化阶段完成一次，跨世界保留。需要撤销的监听器/策略保留原实例，注册项使用附属自己的稳定 ID。描述与探测回调只读；样板语义回调可在规划线程执行，不读写世界或移动资源。客户端模型注册在模型烘焙前完成，公共入口不能提前加载客户端专用类。

所有数量采用对应 AEKey 的计量单位。模拟不改变物资；实际操作按返回的已接收量移交所有权。保留拒收部分，不能根据请求量、预览量或进度显示直接扣减物资。

## 规划与提交

以下示例展示明确选择 ECO、验证结果和提交精确父订单的调用边界。调用方负责权限、请求生命周期与 CPU 选择。

```java
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.*;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import net.minecraft.server.level.ServerLevel;
import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.Future;

public final class EcoApiExample {
    public static Future<ICraftingPlan> begin(ServerLevel level, IGrid grid,
            IActionSource source, AEKey goal, long amount) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Server thread required");
        }
        if (amount <= 0) throw new IllegalArgumentException("Positive amount required");
        var settings = ECOCraftingNetworkSettings.of(grid);
        if (settings == null || !settings.neoecoae$isFastPlannerEnabled()
                || !settings.neoecoae$hasComputationHost(source)) {
            throw new IllegalStateException("No eligible ECO computation host");
        }
        return ECOPlanningService.begin(level, grid, source, goal, amount,
            CalculationStrategy.REPORT_MISSING_ITEMS, ECOPlannerOptions.from(settings));
    }

    // Call once on the server thread after the Future completes and access is rechecked.
    public static ICraftingSubmitResult submit(IGrid grid, IActionSource source,
            ICraftingRequester requester, ICraftingCPU target, ICraftingPlan plan) {
        var result = plan instanceof ECOCraftingPlanDiagnostics bridge
            ? bridge.neoecoae$getPlanningResult() : null;
        if (result == null) result = ECOPlanningResultRegistry.find(plan);
        if (result == null || plan.simulation() || result.status() != PlanningStatus.SUCCESS) {
            throw new IllegalArgumentException("An executable ECO plan is required");
        }
        var contract = ECOPlanningResultRegistry.resolveContract(plan, result);
        if (contract == null || !contract.executable()) {
            throw new IllegalArgumentException("Missing execution contract");
        }
        return ECOPlanningResultRegistry.withSubmissionAlias(plan, result,
            () -> grid.getCraftingService().submitJob(plan, requester, target, false, source));
    }

    // ecoTarget must be an eligible ECO CPU or its idle ECO placeholder.
    public static ICraftingSubmitResult submitBigOrder(IGrid grid, IActionSource source,
            ICraftingRequester requester, ICraftingCPU ecoTarget, AEKey goal,
            BigInteger amount, ECOPlannerOptions options) {
        Objects.requireNonNull(ecoTarget, "Select an ECO CPU");
        return new ECOBigOrderRequest(goal, amount, false, options).submit(
            carrier -> grid.getCraftingService().submitJob(
                carrier, requester, ecoTarget, false, source));
    }
}
```

`begin` 返回后，在服务器 tick 消费完成的 Future，再调用 `submit`，并检查 `ICraftingSubmitResult.successful()`。快照后库存、容量和网络拓扑仍可能改变。普通提交的 `target == null` 允许服务选择 CPU，`requester == null` 表示独立任务。

直接调用 `ECOPlanningService.begin` 不经过 AE2 的服务入口。若要保留 AE2 服务链，使用 `new ECOPlannerRequester(originalRequester, options)` 包装 `ICraftingSimulationRequester`，再调用 `beginCraftingCalculation`。其他规划器提前返回的 Future 不会被 ECO 取消或替换；未标记的第三方请求不会仅因网络开关开启就全部改用 ECO。

| 选项 | 约定 |
| --- | --- |
| `cyclePlanningEnabled` | 允许不可避开的循环组件进入求解 |
| `ignorePatternSubstitutions` | 仅按编码首选输入规划 |
| `fuzzyPlanningItemIds` | 对指定物品忽略 NBT 规划，实际输入仍须通过样板校验 |
| `planningLogEnabled` | 旧兼容字段；日志由全局 Calculating 调试配置控制 |

`ECOPlannerOptions.from(settings)` 捕获当前选项；`from(null)` 关闭循环与忽略替代模式，模糊集合为空。共享网络设置的修改仍须鉴权。使用带动作来源的 `neoecoae$hasComputationHost(source)` 检查玩家/自动任务资格。

`REPORT_MISSING_ITEMS` 保留原请求及完整缺料报告；`CRAFT_LESS` 可能返回较小数量，读取 `plan.finalOutput().amount()`。非空计划可能只是模拟报告，不能直接提交。通过 [ECOCraftingPlanDiagnostics](../src/main/java/cn/dancingsnow/neoecoae/api/me/diagnostics/ECOCraftingPlanDiagnostics.java) 或 [ECOPlanningResultRegistry.find](../src/main/java/cn/dancingsnow/neoecoae/api/me/planning/ECOPlanningResultRegistry.java) 读取状态、`exactMissingItems()`、`theoreticalBytes()` 与 `trace().diagnostics()`；提交前检查 `resolveContract(...).executable()`。不假定每份计划都有阶段 `executionPlan()`。

`withSubmissionAlias` 只在同步提交回调期间绑定元数据；保持同一计划的执行身份，不复制或缩放任务向量来复用元数据。该注册表有容量与有效期限制，不用于持久化。

### 精确父订单

`ECOBigOrderRequest` 接受正 `BigInteger`，当前上限为 1024 位十进制数字。使用上例的 `submitBigOrder`，显式目标必须是合格 ECO CPU 或网络公布的空闲 ECO 占位 CPU。普通 AE2 CPU 不解释父订单载体。

`request.submit(callback)` 在同步回调中把请求绑定到同一个载体对象。不能另取 `carrier()` 延后提交、复制载体或异步调用回调。载体的 long 数量是受限视图，不是精确总量。`fromPlanningResult` 从 long 计划投影读取数量，超 long 总量必须直接构造请求。

父订单按新库存规划有界子段。[ECOBigOrderPlanner.begin](../src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOBigOrderPlanner.java) 仅探测单个子段，不自行创建父订单。[ECOBigOrderAdmission.allows](../src/main/java/cn/dancingsnow/neoecoae/api/me/bigorder/ECOBigOrderAdmission.java) 检查诊断与强制模式准入；强制模式不生成材料，也不接受未知/不支持的路线。通过 `ECOCraftingProgressView.bigOrder()` 读取父订单状态与精确进度。

确认菜单的完整精确计划路径与分段父订单 API 各有提交合同；不要从菜单内部适配器自行拼装父订单。

## 任务事件、附件与调度

| 能力 | 注册与约定 |
| --- | --- |
| 生命周期 | `ECOCraftingLifecycle.register(listener)`；用同实例 `unregister`。回调为 `onJobStarted`、`onPatternDispatched`、`onJobFinished` |
| 持久化附件 | `ECOCraftingJobAttachmentRegistry.register(id, factory)`；实现 [ECOCraftingJobAttachment](../src/main/java/cn/dancingsnow/neoecoae/api/me/attachment/ECOCraftingJobAttachment.java) 的 `id()`、`save()`、`load(CompoundTag)`、`clear(result)`，每任务独立实例 |
| 发配策略 | `ECOCraftingDispatchPolicyRegistry.register(policy)`；实现 `mayTick(cpu)`、`isProviderAvailable(cpu, provider)`，用同实例撤销 |

发配事件表示供应器已经接收，精确次数读取 `exactDispatchedCrafts()`，不是完工通知。监听器异常会被隔离；回调只观察，不再次扣输入、推样板或修改任务。

附件 ID 必须唯一且与工厂返回对象一致。ECO 管理保存、载入和清理；未绑定历史数据保留供恢复，不能因工厂异常丢弃物资。

所有发配策略均须放行。默认供应器检查包含 `isBusy()`；策略异常拒绝当前操作。策略暂停不能修改已接收批次的所有权或重新交付其输入。

## 供应器与 FastPath

普通 AE2 样板供应器继续实现 `ICraftingProvider`。需要整批并行时，实现 `ECOParallelCraftingProvider`：

- `eco$getAvailableParallelSlots()` 返回实时可用并行槽位。
- `eco$pushPatternBatch(pattern, inputTotal, craftCount, jobId)` 中输入是整批总量；`jobId` 可空。
- 完整接收才返回 true，false 保证输入不变，不能部分接收后返回 false。

外部类通过 [ECOParallelCraftingProviders.register(providerClass, adapter)](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOParallelCraftingProviders.java) 接入，适配器返回能力或 null。适配器不能缓存过期容量；重叠类型没有优先级承诺。

FastPath 使用独立的 `ECOFastPathDispatchProvider`：

| 阶段 | 约定 |
| --- | --- |
| `eco$prepareFastPath(context)` | 只读探测；不支持返回 null，否则返回正 long 容量与提交回调 |
| `ECOBatchDispatchContext.inputs()` | 单次执行输入 |
| `Batch.inputTotal/outputTotal/remainingTotal` | 整批总量，不能修改用于回滚的集合 |
| `supportsExactInputs`、`exactInputTotal` | 显式启用精确输入，不向旧供应器截断 |
| `eco$prepareExactFastPath(context, requested)` | 独立精确次数能力，默认不支持 |

本分支的门面只解析直接实现与内置 ExtendedAE Plus 桥；`ECOParallelCraftingProviders` 注册的适配器不参与 FastPath 解析。`prepareAllocated/prepareAllocatedExact` 还要求供应器直接实现 FastPath 接口。`ECOBatchCapacityProvider` 为旧兼容入口，新接入使用上述接口。

FastPath true 表示整批接收；false 或普通异常保证未接收，可由 CPU 回滚。如果是否接收已不确定，抛出 [ECOIndeterminateBatchException](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOIndeterminateBatchException.java)，停止任务核对，不能先退款再重试。供应器不自行扣减或退款 CPU 的输入与能量。

外部 CPU 使用 `ECOFastPathFacade.prepare(...)` 与一次性的 `PreparedBatch.submit(energyAccount)`。准备/提交在同一服务器 tick；物资在门面扣账前仍归 CPU 库存。返回 true 后仅按门面总量记账一次，之后记账失败不能重放已经接收的批次。`EnergyAccount` 的 `commit()` 不抛异常，无法退回网络的能量保存在 CPU 持久化余额。`prepareAllocated` 用于已分配输入，所有权不同，使用前阅读门面 Javadoc。

[EcoFastpathHost](../src/main/java/cn/dancingsnow/neoecoae/api/fastpath/EcoFastpathHost.java) 是另外的 inspect/submit 协议，需要通过实际接入获取实现，不提供全局能力查询，也不替代 CPU 事务。

## 产物、进度与机器诊断

[ECOCraftingOutputClaimSink.claimCraftingOutput](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOCraftingOutputClaimSink.java) 接收 `ECOCraftingOutputClaimRequest(jobId, expected, actual, amount, mode)`，统一完成任务身份、预期需求、实际产物路由与记账。预期 Key 和实际 Key 可以不同。

生产者只从物理缓存移除返回的 `claimedAmount()`，未认领部分保留。查看 `deliveredToRequester()`、`deliveredToNetwork()`、`storedInCpu()` 区分去向；已认领部分不能再从另一条路径交付。`SIMULATE` 不交付物资。

| 入口 | 责任 |
| --- | --- |
| [ECOCraftingOutputRouter](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOCraftingOutputRouter.java) | 按任务 ID 向 CPU 路由产物 |
| [ECOJobOutputReceiver](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOJobOutputReceiver.java) | 接收 Worker 产物与多余产物 |
| [ECOVirtualCraftingCompletionSink](../src/main/java/cn/dancingsnow/neoecoae/api/me/completion/ECOVirtualCraftingCompletionSink.java) | 接受虚拟完成次数，不等于整个任务结束 |
| [ECOCraftingProgressSink](../src/main/java/cn/dancingsnow/neoecoae/api/me/progress/ECOCraftingProgressSink.java) | 报告已完成工作，不交付资源 |
| [ECOCraftingProgressView](../src/main/java/cn/dancingsnow/neoecoae/api/me/progress/ECOCraftingProgressView.java) | 读取进度、耗时、各 Key 类型工作量及可选父订单进度 |

产物路径已经记录完成量时，不再重复报告进度。本分支没有可注册的机器状态读取器；供应器可通过 [ECOProcessingTargetProvider](../src/main/java/cn/dancingsnow/neoecoae/api/me/diagnostics/ECOProcessingTargetProvider.java) 提供加工目标。

[ECOVirtualCraftingCoolantRegistry.register(fluidId)](../src/main/java/cn/dancingsnow/neoecoae/api/me/cooling/ECOVirtualCraftingCoolantRegistry.java) 扩展虚拟合成流体允许列表。还须提供合格的 `neoecoae:cooling` 配方并满足冷却、结构与供能条件。注册可重复；`unregister` 仅移除自定义条目，内置条目保留。该表为进程级状态，不写入存档。

## 样板语义与导入

标准样板仅需 AE2 `IPatternDetails`。特殊语义通过 `ECOPatternSemanticsRegistry.register(id, priority, adapter)` 注册 [ECOPatternSemanticAdapter](../src/main/java/cn/dancingsnow/neoecoae/api/me/pattern/ECOPatternSemanticAdapter.java)：

- 回调基于通用解析结果补充语义，保留同一个物理样板实例。
- 无法准确分析时返回 `ECOPatternSemantics.unsupported(...)`；不能编造可执行结果。
- 高优先级先匹配，同级按 ID 字典序，只使用首个匹配项；分析失败不会降级到宽松适配器。
- 归一化输入/反馈使用精确数量；实际输入仍须满足样板的 `IInput.isValid`。

执行优化另由 `ECOExecutionOptimizationRegistry` 授权。`VERIFIED_ASSEMBLER` 不将任意加工样板变成虚拟合成。包装样板实现 [ECOPatternWrappers.Wrapper](../src/main/java/cn/dancingsnow/neoecoae/api/me/pattern/ECOPatternWrappers.java)，或注册明确的 contract/accessor，不猜测委托方法名。语义/优化注册改变会撤销相关缓存与旧凭证，初始化后避免在计算中途改动。

导入编码物品时，从同一网络取得 `grid.getService(IECOPatternStorageService.class)`，用编码物品、对应解析结果和 `AEItemKey.of(encoded)` 构造 [ECOPreparedPattern](../src/main/java/cn/dancingsnow/neoecoae/api/ECOPreparedPattern.java)，调用 `insertPreparedPatternReporting`。

| 返回结果 | 所有权 |
| --- | --- |
| `INSERTED` | 按 [ECOPatternInsertion](../src/main/java/cn/dancingsnow/neoecoae/api/ECOPatternInsertion.java) 报告完成源物品转移 |
| `ALREADY_PRESENT` | 已存在配方，不代表源物品被接收 |
| `NO_SPACE` | 保留源物品，容量变化后重试 |
| `INCOMPATIBLE/NO_TARGET` | 保留源物品，报告无兼容目标 |

保存为真实样板物品时不返还空白；容器吸收配方时 `consumedSource()` 为 true，必须交付 `blankReplacement()` 后才清空源槽。部分完成的转移保存事务状态，避免重复插入或返还。

可写节点服务实现 [IECOPatternStorage](../src/main/java/cn/dancingsnow/neoecoae/api/IECOPatternStorage.java)，`KnownUnique` 方法仅供已证明无重复的调用使用。目录宿主通过 [PatternStorageHost](../src/main/java/cn/dancingsnow/neoecoae/api/PatternStorageHost.java)、[AuxiliaryPatternHolder](../src/main/java/cn/dancingsnow/neoecoae/api/AuxiliaryPatternHolder.java) 提供真实槽位、修订号与稳定内容顺序。本分支的宿主接口没有 `allowsPatternMigrationSource()` 迁移来源授权方法。

外部候选通过 owner UUID 认领；所有退出路径释放认领，源槽清空后删除候选。扫描未 ready 不阻止处理已发现候选；拓扑变化后丢弃旧槽位引用。

## 存储盘与精确数量

`ECOStorageCells.register(IECOCellHandler)` 注册盘处理器；`getCellInventory(stack, saveProvider)` 可能返回 null。宿主更换盘或卸载时调用 `releaseCellInventory`，处理器按实际所有权保存并释放状态。[IECOStorageCell](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOStorageCell.java) 为库存契约，[IECOStorageCellItem](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOStorageCellItem.java) / [IBasicECOCellItem](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IBasicECOCellItem.java) 提供物品能力。

`ICellHost` 的安装参数为恰好一张有效盘，移除参数为 null；`ItemStack.EMPTY` 不是移除请求。先查询 `isItemValid/canExtractCell`，调用 void 的 `setCellStack` 后重读槽位确认，再转移源/旧盘。不要直接修改 `getCellStack()` 返回的活栈。[IECOStoragePriorityHost.setStoragePriority](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOStoragePriorityHost.java) 在服务端保存并刷新挂载。

`ECOBigIntegerStorage.insert(storage, key, amount, mode, source)` 返回**已插入数量**，剩余量为提供量减去返回值。long 范围使用普通 AE2 插入；超 long 时优先精确库存，否则仅尝试一段 `Long.MAX_VALUE`，不会自动循环搬完。精确实现返回值必须在 0 与提供量之间。`extract(...)` 返回实际提取量，优先调用 `extractBigInteger`；默认实现只执行至多 `Long.MAX_VALUE` 的普通提取，不保证一次取完。

| 可选能力 | 条件 |
| --- | --- |
| [IECOStorageMigrationCell](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOStorageMigrationCell.java) | 无损枚举、清空、模拟、持久化与恢复均实现后才参与迁移；超 long 库存还需实现精确数量读取和插入 |
| [IECOBulkMarkableCellItem](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOBulkMarkableCellItem.java) | 开启压缩标记 UI，不注册库存后端 |
| [IECOBulkDisplayCell](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOBulkDisplayCell.java) | 提供完整压缩链与实际生效的显示截止等级 |

本分支没有 `INativeMEStorageRestrictedItem` 通用插入限制接口；具体盘的原版 ME 驱动器兼容以其实际实现为准。无限盘仍须具备完整迁移实现与明确资格。

## 工作站配方与模块

[LargeWorkstationRecipes](../src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationRecipes.java) 的 `getAll(level)` 返回展示代表，`getGroups(level)/getGroup(level, id)` 提供配方组，`getAvailable(level, LargeWorkstationModule)` 返回当前模块可执行的变体。组的 `availableVariants(module)` 保留各来源的原始批量与费用；`findScaled` 返回的实际变体 ID 用于保存任务。展示代表不代替执行权限，也不能将大批量配方缩为任意小批量。

[LargeWorkstationRecipe](../src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationRecipe.java) 保留源 ID/类型、输入输出、加工时间与 AE/FE 成本，`processing(ticks, feEnergy, feAlternative)` 描述来源配方的时间/FE 合同。`energy()` 为 AE 预算；AE 与 FE 均非零且非 alternative 时同时支付，alternative 时二选一，FE-only 的 AE 设零。自 20.5.3 起，大型集成工作站实际新批次固定为 1 tick；JEI/EMI 继续展示来源时间，吞吐由并行数量决定。来源时间不能用作运行进度时长。

运行模块由固定枚举 [LargeWorkstationModule](../src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationModule.java) 与内置配方适配器决定。移植约定中的大集成范围为常规、AdvancedAE、ExtendedAE、AE2LT 四类；源码中的其他枚举占位不构成已接入承诺。本分支没有公开的 `ECOWorkstationRecipeAdapters` / `ECOWorkstationModules` 动态注册入口，也没有 1.21.1 的功耗、结构、独立晶阵或存储通道扩展注册器。接入额外来源需另行实现并验证对应业务适配。

## 面板与模型

在 Forge 注册事件中，将自定义档位与盘类型注册到 [NERegistries.Keys](../src/main/java/cn/dancingsnow/neoecoae/all/NERegistries.java) 对应同步注册表中，以两端相同 ID 注册。实现 `IECOTier` 的必需参数，组件兼容用 `supportsComponentTier`。此接口没有 `supportsFeature` 或建造定义重载。注册表在初始化后才可用，不在静态初始化中读取。

`IECOTier.getPanelTierName()` 提供名称，`getHostMarkerTexture()` 提供标记，`getPanelAtlas(String)` 为当前档位替换完整图集。返回 null 使用默认外观。图集优先级：档位 → 本分支 UI 兼容层的全局 `ui-eco` provider → 原图。替换图保持原尺寸与裁剪布局。

| atlas 参数（不带 `ui-eco:`） | 默认文件（`assets/neoecoae/textures/guis/`） |
| --- | --- |
| `Computation-BACKGROUND_ATLAS` | `eco_cpu_controller.png` |
| `Computation-WIDGETS_ATLAS` | `cpu_widget.png` |
| `Storage-CONTROLLER_ATLAS` | `storage/storage_controller.png` |
| `Storage-ATLAS` | `storage/storage_widget.png` |
| `Crafting-CONTROLLER_ATLAS` | `crafting_controller.png` |
| `Crafting-WIDGETS_ATLAS` | `crafting_widgets.png` |

方块实现 `IHostPanelTextureProvider.getPanelTexture(BlockState, Part)` 提供成员/徽章/标记。五参数重载追加 `index, active, hovered`，用于状态灯与按钮；默认委托两参数方法。读取真实放置态，返回 null 按状态回落。

| Part | 独立纹理布局 |
| --- | --- |
| `COMPUTATION_UPPER` | 10×5 |
| `COMPUTATION_CORE/COMPUTATION_LOWER` | 10×6 |
| `STORAGE_TIER/COMPUTATION_TIER/CRAFTING_TIER` | 13×18 |
| `STORAGE_MARKER/COMPUTATION_MARKER/CRAFTING_MARKER` | 13×16，上下两个 13×8 |
| `COMPUTATION_STATUS` | 12×12 |
| `COMPUTATION_SWITCH/CRAFTING_MODE` | 15×26 |

状态灯 index 0～5 为连接、替代、循环、快速规划、普通交换机、高能交换机；`hovered` 恒 false。高能状态使用原蓝色列 5，粉色状态使用列 4。计算开关 0～2 为替代、循环、快速规划；合成按钮 0～1 为主动冷却、超频，`active/hovered` 选择对应状态图。

自行创建 [StorageHostUI](../src/main/java/cn/dancingsnow/neoecoae/gui/storage/StorageHostUI.java) 等面板时，使用接收 `Supplier<IECOTier>` 和 `Supplier<BlockState>` 的重载；服务器捕获放置态纹理并同步到客户端。旧工厂签名保留。

`ECOCellModels.register`、`ECOComputationModels.registerCellModel/registerCableModel` 在客户端注册模型。物品延迟注册使用 `Supplier<? extends Item>`；线缆注册签名为 `(IECOTier, ResourceLocation normalModel, ResourceLocation formedModel)`。本分支没有按宿主方块的线缆注册重载、动态模型 provider 或 `registerAdditionalModel` 入口。额外模型使用 Forge 客户端模型事件注册。面板 provider 只更换图片，不改变按钮或业务语义。

## 接入诊断

[ECOCraftingServiceDiagnostics.neoecoae$describeCpuSelection](../src/main/java/cn/dancingsnow/neoecoae/api/me/diagnostics/ECOCraftingServiceDiagnostics.java) 查询 CPU 资格，[ECOPatternPushDiagnostics.neoecoae$getPushDiagnostics](../src/main/java/cn/dancingsnow/neoecoae/api/me/diagnostics/ECOPatternPushDiagnostics.java) 查询最近推送失败。诊断只读，不授权重放、退款或跳过校验。

报告兼容问题时保留实际 JAR/依赖版本、动作来源、请求选项、规划状态、提交结果与供应器接收/交付日志。签名和契约测试验证调用关系；真实机器的事务、资源外观与整合包联动需要实际运行验证。

本分支的专项记录：[ECO CPU 提交反馈排查](diagnostics/ECO_CPU_SUBMISSION_INVESTIGATION_ZH_CN.md)、[GTLCore 样板供应器兼容](compatibility/GTLCore_PATTERN_PROVIDER_ZH_CN.md)。其中的验证结论仅对应记录时的版本和环境，不代表任意整合包均已验收。
