# API 调用与接入指南

[English](API.md) · [文档目录](README.md) · [ECO 规划器说明](ECO_PLANNER_ZH_CN.md)

适用范围：**Minecraft 1.21.1 / NeoForge**，源码核对日期 **2026-10-10**。构建版本以及当前工作区与已发布产物的区别，见[文档目录](README.md)。

## 1. 依赖配置与 API 边界

API 随完整模组 JAR 分发，目前没有独立的 `-api` 产物，也没有长期 API 兼容性承诺。构建中的 Maven 发布地址是本地 `repo/`，没有声明公共 NeoECOAE Maven 仓库。请固定接入方实际拿到的 JAR 及其 Minecraft、NeoForge、AE2 依赖版本。项目许可证为 GPLv3。

在已经配置 NeoForge、AE2 和 Java 21 的附属模组项目中，将对应 JAR 放入 `libs/`：

```groovy
dependencies {
    compileOnly files("libs/neoecoae-21.2.1.jar")
    // ModDevGradle 开发运行使用；若已通过其他方式安装该模组，请省略。
    localRuntime files("libs/neoecoae-21.2.1.jar")
}
```

该示例不会把 NeoECOAE 打包进你的附属模组。如果附属模组离不开 ECO，在 `neoforge.mods.toml` 中声明必需依赖：

```toml
[[dependencies.examplemod]]
modId = "neoecoae"
type = "required"
versionRange = "[21.2.1]"
ordering = "AFTER"
side = "BOTH"
```

将 `examplemod` 和版本范围替换为实际模组 ID 及经过测试的版本。可选兼容使用 `type = "optional"`，并且仅在确认安装了 `neoecoae` 后加载引用 ECO 类型的兼容类。只写可选依赖，不能阻止 Java 类链接错误。

| 需求 | 推荐入口 | 源码 |
| --- | --- | --- |
| 明确使用 ECO 规划 | `ECOPlanningService.begin` | [规划服务](../src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOPlanningService.java) |
| 标记一次 AE2 计算请求 | `ECOPlannerRequester`、`ECOPlannerOptions` | [请求包装器](../src/main/java/cn/dancingsnow/neoecoae/api/me/planning/ECOPlannerRequester.java) |
| 读取网络规划设置 | `ECOCraftingNetworkSettings.of(grid)` | [网络设置](../src/main/java/cn/dancingsnow/neoecoae/api/me/network/ECOCraftingNetworkSettings.java) |
| 监听任务或保存附加状态 | 生命周期监听器、附件工厂 | [生命周期](../src/main/java/cn/dancingsnow/neoecoae/api/me/lifecycle/ECOCraftingLifecycle.java)、[任务附件](../src/main/java/cn/dancingsnow/neoecoae/api/me/attachment/ECOCraftingJobAttachmentRegistry.java) |
| 接入样板供应器 | 普通并行或 FastPath 接口 | [普通并行](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOParallelCraftingProvider.java)、[FastPath](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOFastPathDispatchProvider.java) |
| 交付动态产物 | `ECOCraftingOutputClaimSink` | [产物认领](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOCraftingOutputClaimSink.java) |
| 向网络导入样板 | `IECOPatternStorageService` | [样板服务](../src/main/java/cn/dancingsnow/neoecoae/api/IECOPatternStorageService.java) |
| 接入存储盘 | `ICellHost`、`IECOCellHandler`、精确插入 | [盘槽接口](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ICellHost.java)、[存储辅助接口](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ECOBigIntegerStorage.java) |

下文使用了 `crafting.planner` 中的部分公开入口，这些入口随版本变化。求解器、运行时、Worker、持久化和兼容加载器属于内部实现。已弃用的 `api.me.ECOCraftingCPU`、`ECOCraftingCPULogic`、`ExecutingCraftingJob` 用于保留已有附属模组的二进制和 Mixin 目标；新接入不要自行创建或继承这些类型。

## 2. 线程与所有权约定

规划入口在**所属服务器线程**调用，以便安全读取网络库存快照。入口返回 `Future`；在后续 tick 检查 `isDone()`，完成后再调用 `get()`。提交任务、供应器接收、存储修改和产物记账也在服务器线程执行。

开始计算和提交前，都要由调用方检查玩家或机器权限以及网络访问资格。底层 ECO 接口不会代替调用方鉴权。一次计算使用同一网络、动作来源和请求机器。菜单关闭、机器移除或网络失效时，取消被放弃的计算。处理 `ExecutionException`、取消状态以及执行器队列满时的 `RejectedExecutionException`。

下列 Java 示例带有完整导入，可以作为接入骨架。抽象方法中的机器事务，以及调用方传入的回调，仍需根据实际机器实现。

## 3. 发起规划、读取结果并只提交一次

`ECOPlanningService.begin(level, grid, source, goal, amount, strategy, options)` 明确选择 ECO，不经过 `ICraftingService.beginCraftingCalculation`，因此不会由其他模组在服务入口处选择规划器。示例采用合成确认界面的资格检查，并确保每个 Future 只消费一次。

使用 `CalculationStrategy.REPORT_MISSING_ITEMS` 保留原请求数量及完整缺料报告。原请求无法执行时，`CRAFT_LESS` 会探测较小数量；读取返回的 `finalOutput().amount()`，不能假设它仍等于原请求。这些探测共享同一会话的库存快照和预算。非 `CRAFT_LESS` 计算已有报告计划时，服务直接返回该计划，不会为了生成模拟报告而重复计算相同数量。

```java
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.*;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import net.minecraft.server.level.ServerLevel;
import java.util.concurrent.*;

public final class EcoPlanningExample {
    private final ServerLevel level;
    private final IGrid grid;
    private final IActionSource source;
    private Future<ICraftingPlan> pending;
    private ECOPlanningResult lastResult;

    public EcoPlanningExample(ServerLevel level, IGrid grid, IActionSource source) {
        this.level = level;
        this.grid = grid;
        this.source = source;
    }

    public void begin(AEKey goal, long amount) {
        checkThread();
        if (amount <= 0 || pending != null) throw new IllegalStateException("Invalid request");
        var settings = ECOCraftingNetworkSettings.of(grid);
        if (settings == null || !settings.neoecoae$isFastPlannerEnabled()
                || !settings.neoecoae$hasComputationHost(source)) {
            throw new IllegalStateException("No eligible ECO planning host");
        }
        lastResult = null;
        pending = ECOPlanningService.begin(level, grid, source, goal, amount,
            CalculationStrategy.REPORT_MISSING_ITEMS, ECOPlannerOptions.from(settings));
    }

    // Call from a server tick, after rechecking access to the original grid.
    // Null means no submission: still pending, cancelled, or a diagnostic-only result.
    public ICraftingSubmitResult poll(ICraftingRequester requester, ICraftingCPU target)
            throws InterruptedException, ExecutionException {
        checkThread();
        var future = pending;
        if (future == null || !future.isDone()) return null;
        pending = null; // Consume once: repeated ticks cannot submit the same plan twice.
        if (future.isCancelled()) return null;
        var plan = future.get();
        if (plan == null) return null;
        lastResult = diagnostics(plan);
        if (plan.simulation() || lastResult != null && lastResult.status() != PlanningStatus.SUCCESS) {
            return null;
        }
        var contract = ECOPlanningResultRegistry.resolveContract(plan, lastResult);
        if (lastResult != null && (contract == null || !contract.executable())) return null;
        return ECOPlanningResultRegistry.withSubmissionAlias(plan, lastResult,
            () -> grid.getCraftingService().submitJob(plan, requester, target, false, source));
    }

    public ECOPlanningResult lastResult() { return lastResult; }

    public void cancel() {
        checkThread();
        if (pending != null) pending.cancel(true);
        pending = null;
    }

    private void checkThread() {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
    }

    public static ECOPlanningResult diagnostics(ICraftingPlan plan) {
        if (plan instanceof ECOCraftingPlanDiagnostics bridge) {
            var attached = bridge.neoecoae$getPlanningResult();
            if (attached != null) return attached;
        }
        return ECOPlanningResultRegistry.find(plan);
    }
}
```

每个请求持有者创建一个实例。检查权限后调用 `begin(goal, amount)`，在后续服务器 tick 调用 `poll(requester, target)`。请求机器为 null 表示独立任务；目标 CPU 为 null 时交由 AE2/ECO 选择。检查返回的 `ICraftingSubmitResult.successful()` 和错误信息：快照之后，材料、容量或网络拓扑都可能发生变化。

通过 `lastResult()` 展示 `status()`、`trace().diagnostics()`、`exactMissingItems()`、`theoreticalBytes()`。其他规划器的计划可能没有 ECO 诊断结果。将诊断集合当作只读数据使用。`executionPlan()` 在不存在阶段计划时可能抛异常；优先查询 `resolveContract`，不要假定所有成功的无环计划都有阶段元数据。

`withSubmissionAlias` 只在这次同步提交期间绑定元数据，不替换传入的计划。不要只复制 `finalOutput()`、缩放样板次数，或因为最终产物相同就给另一份计划套用结果。注册表按完整执行身份匹配，元数据十分钟后过期，不承担任务持久化。

### 参数与网络默认值

| 参数 | 含义 |
| --- | --- |
| `cyclePlanningEnabled` | 允许不可避开的循环组件进入循环求解器。 |
| `ignorePatternSubstitutions` | 只按样板编码的首选输入规划，不考虑替代输入。 |
| `fuzzyPlanningItemIds` | 指定哪些中间物品按忽略组件的方式规划，不等于支持任意模糊配方。 |
| `planningLogEnabled` | 保留的旧兼容字段，日志现在由服务器调试配置控制。 |

`ECOPlannerOptions.from(settings)` 获取当前默认设置。`from(null)` 关闭循环规划和忽略替代模式，并使用空的模糊规划集合。也可传入 `new ECOPlannerOptions(true, false, Set.of())` 明确指定本次请求选项。

网络设置提供 `neoecoae$setFastPlannerEnabled`、`neoecoae$setCyclePlanningEnabled`、`neoecoae$setIgnoringPatternSubstitutions`。修改会影响共享网络及计算主机，因此必须先检查权限。主机可能只接受玩家或机器请求，资格判断使用带来源的 `neoecoae$hasComputationHost(source)`。

### 从 AE2 计算服务进入

如果接入方需要保留正常 AE2 服务入口，可以包装已有的模拟请求者：

```java
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.planning.*;
import net.minecraft.server.level.ServerLevel;
import java.util.concurrent.Future;

public final class EcoRequesterExample {
    public static Future<ICraftingPlan> begin(ServerLevel level, IGrid grid,
            ICraftingSimulationRequester original, AEKey goal, long amount,
            ECOPlannerOptions options) {
        var marked = new ECOPlannerRequester(original, options);
        return grid.getCraftingService().beginCraftingCalculation(
            level, marked, goal, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }
}
```

包装器转发 `getActionSource()`，并标记一次计算。当 AE2 创建 `CraftingCalculation` 时，ECO Mixin 识别该标记。如果其他服务包装器提前返回自己的 Future，ECO 不会取消或替换它。根据实际返回计划的来源，决定是否应用 ECO 规则。

原版合成确认**菜单**在开启快速规划且存在合格计算主机时，转向 ECO 直接入口。单纯打开网络开关，不会把所有未标记的附属模组或 AE2 请求强制改成 ECO。明确选择 ECO 的请求遇到不支持的情况时，返回 ECO 诊断结果，不会自动将同一请求交给原版规划器重算。

## 4. 精确父订单与菜单准入

父订单可以保存超过 `Long.MAX_VALUE` 的正整数数量。当前校验最多接受 1024 位十进制数字。使用 `BigInteger` 构造请求，不要先调用 `longValue()` 截断。

服务器接入若要先异步寻找适合 CPU 字节容量的有界子段，可以调用段探测入口：

```java
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.ECOBigOrderPlanner;
import java.util.concurrent.*;

public final class EcoSegmentExample {
    // Begin on the owning server thread; maximum and bytes describe a single segment.
    public static Future<ECOBigOrderPlanner.Answer> begin(IGrid grid, AEKey goal,
            long maximum, long bytes, ECOPlannerOptions options) {
        return ECOBigOrderPlanner.begin(grid, goal, maximum, bytes, options);
    }

    // Poll from the server tick. The caller must consume a completed answer only once.
    public static ECOBigOrderPlanner.Answer completed(Future<ECOBigOrderPlanner.Answer> future)
            throws InterruptedException, ExecutionException {
        if (!future.isDone() || future.isCancelled()) return null;
        return future.get();
    }
}
```

`ECOBigOrderPlanner.begin` 每次调用都捕获新的库存。`maximumChildCrafts` 和 `cpuBytes` 只描述一个有界子段。需要创建账本并协调连续子段时，提交 `ECOBigOrderRequest`：

```java
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import java.math.BigInteger;
import java.util.Objects;

public final class EcoBigOrderExample {
    // ecoTarget must be an eligible ECO CPU or the grid's idle ECO placeholder.
    public static ICraftingSubmitResult submit(IGrid grid, IActionSource source,
            ICraftingRequester requester, ICraftingCPU ecoTarget, AEKey goal,
            BigInteger amount, ECOPlannerOptions options) {
        Objects.requireNonNull(ecoTarget, "Select an ECO CPU");
        var request = new ECOBigOrderRequest(goal, amount, false, options);
        return request.submit(carrier -> grid.getCraftingService().submitJob(
            carrier, requester, ecoTarget, false, source));
    }
}
```

在服务器线程完成权限和 CPU 资格检查后调用。显式目标必须是已有 ECO CPU，或计算主机在网络中公布的空闲 ECO 占位 CPU；原版 CPU 无法解释父订单载体。只有在能够保证请求会进入 ECO 时，接入方才可改用自动选择。

`request.submit(...)` 创建载体，并在同步回调期间将精确请求绑定到**同一个载体对象**。稍后单独提交 `carrier()`、异步执行回调或复制载体，都会丢失绑定。载体中受 long 限制的最终产物数量不代表精确订单总量。

父订单根据新库存规划有界子段，等待材料或容量，并在子段产物完成交付后累计完成量。CPU 暴露进度接口时，通过 `ECOCraftingProgressView.bigOrder()` 读取 `ECOBigOrderProgress`。状态包括 `PLANNING`、`RUNNING_CHILD`、`WAITING_MATERIALS`、`WAITING_CAPACITY`、`COMPLETED`、`CANCELLED`、`FAILED`。

`ECOBigOrderAdmission.allows(result, forced)` 用于转换合格的确认诊断：允许 `PLANNED_BUT_AMOUNT_UNREPRESENTABLE`，或者强制模式下的 `MISSING_ITEMS`，同时检查组件状态。未解、不支持、已解但未生成执行内容的组件不能通过。`ECOBigOrderRequest.fromPlanningResult` 从计划的 long 投影读取目标数量；请求总量本身超过 long 时，使用上面的构造方法。强制模式不生成材料，也不能把未知路线变成可执行路线。

当前确认菜单另有完整精确订单路径。大订单按钮检查准入与选中的 ECO CPU，非强制模式还检查当前库存，然后将内部 `ECOExactCraftingPlan` 直接提交给该 CPU。适配器保留完整精确任务向量以及延后获取的库存、发射数量，通过有界 long 窗口调用 AE2。菜单操作不会绑定 `ECOBigOrderRequest`，也不会创建上述分段父订单。该适配器属于内部实现；需要子段规划和父订单进度约定时，使用父订单 API。

源码：[父订单请求](../src/main/java/cn/dancingsnow/neoecoae/api/me/bigorder/ECOBigOrderRequest.java)、[准入检查](../src/main/java/cn/dancingsnow/neoecoae/api/me/bigorder/ECOBigOrderAdmission.java)、[子段规划器](../src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOBigOrderPlanner.java)、[确认菜单](../src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftConfirmMenuMixin.java)、[精确适配器](../src/main/java/cn/dancingsnow/neoecoae/crafting/adapter/ae2/ECOExactCraftingPlan.java)。

## 5. 任务事件、附件与调度策略

### 监听任务

在通用初始化中注册一次，并保留实例用于卸载：

```java
import cn.dancingsnow.neoecoae.api.me.lifecycle.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EcoLifecycleExample implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("examplemod.eco");
    private final ECOCraftingLifecycleListener listener = new ECOCraftingLifecycleListener() {
        @Override public void onJobStarted(ECOCraftingJobContext job) {
            LOG.info("Job {} started", job.craftingJobId());
        }
        @Override public void onPatternDispatched(ECOCraftingDispatchEvent event) {
            LOG.debug("Job {} accepted {} crafts",
                event.job().craftingJobId(), event.exactDispatchedCrafts());
        }
        @Override public void onJobFinished(ECOCraftingJobContext job, ECOCraftingJobResult result) {
            LOG.info("Job {} ended: {}", job.craftingJobId(), result.status());
        }
    };

    public EcoLifecycleExample() { ECOCraftingLifecycle.register(listener); }
    @Override public void close() { ECOCraftingLifecycle.unregister(listener); }
}
```

事件分别描述 ECO 任务开始、供应器**已接收**样板批次、任务结束。精确发配次数使用 `exactDispatchedCrafts()`，兼容 long 字段可能是受限投影。任务上下文和结束结果中的数量字段仍是 long 视图。监听器只观察，不在回调中提取输入、推送供应器或修改任务。监听器的运行时异常会被隔离并记录。

### 保存每个任务的附加状态

每个附件只属于一个任务，并保存独立的 NBT 数据：

```java
import cn.dancingsnow.neoecoae.api.me.attachment.*;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class EcoLabelAttachment implements ECOCraftingJobAttachment {
    private static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath("examplemod", "label");
    private String label;

    private EcoLabelAttachment(String label) { this.label = label; }

    public static void register() {
        ECOCraftingJobAttachmentRegistry.register(ID,
            context -> new EcoLabelAttachment(context.craftingJobId().toString()));
    }

    @Override public ResourceLocation id() { return ID; }
    @Override public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("label", label);
        return tag;
    }
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        label = tag.getString("label");
    }
    @Override public void clear(ECOCraftingJobResult result) { label = ""; }
}
```

在通用初始化中调用一次 `EcoLabelAttachment.register()`。注册 ID 必须唯一，工厂返回对象的 `id()` 必须与注册 ID 一致。ECO 调用 `save`、`load`、`clear`；结束状态为 `SUCCESS`、`FAILURE`、`CANCELLED`。未绑定的历史数据会保留，供后续恢复。工厂或载入失败不代表可以丢弃任务物资。创建与绑定附件的方法由 ECO 内部管理。

### 暂停发配

```java
import cn.dancingsnow.neoecoae.api.me.dispatch.*;
import java.util.function.BooleanSupplier;

public final class EcoPolicyExample {
    public static ECOCraftingDispatchPolicy install(BooleanSupplier enabled) {
        var policy = new ECOCraftingDispatchPolicy() {
            @Override public boolean mayTick(ECOCraftingCpuContext cpu) {
                return enabled.getAsBoolean();
            }
        };
        ECOCraftingDispatchPolicyRegistry.register(policy);
        return policy; // Unregister this same instance during teardown.
    }
}
```

所有已注册策略都必须允许当前 tick 或供应器。需要额外筛选时，重写 `isProviderAvailable(cpu, provider)`；其默认实现检查 `provider.isBusy()`。策略异常会拒绝当前操作。回调不扣输入、不推样板、不改任务。使用 `ECOCraftingDispatchPolicyRegistry.unregister(policy)` 移除同一个实例。

## 6. 样板供应器接入

### 普通并行接收

```java
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import java.util.UUID;

public abstract class EcoParallelProviderExample
        implements ICraftingProvider, ECOParallelCraftingProvider {
    @Override public int eco$getAvailableParallelSlots() { return freeLanes(); }

    @Override public boolean eco$pushPatternBatch(IPatternDetails pattern,
            KeyCounter[] inputTotal, long craftCount, UUID jobId) {
        if (craftCount <= 0 || craftCount > freeLanes()) return false;
        return acceptWholeBatch(pattern, inputTotal, craftCount, jobId);
    }

    protected abstract int freeLanes();
    // Implement one atomic reservation/acceptance of the full batch in your machine.
    protected abstract boolean acceptWholeBatch(IPatternDetails pattern,
        KeyCounter[] inputTotal, long craftCount, UUID jobId);
}
```

抽象方法由实际机器的队列事务实现。`inputTotal` 是 **`craftCount` 次配方的全部输入总量**，使用 AE2 Key 数量单位。只有完整接收整批输入后才能返回 true。返回 false 时输入保持不变，不能把部分接收当作完整拒绝。任务 ID 可以为 null。

第三方供应器在初始化时调用 `ECOParallelCraftingProviders.register(providerClass, adapter)`。适配器返回该实例的 `ECOParallelCraftingProvider`，或返回 null。通过 `ECOParallelCraftingProviders.find(provider)` 同时支持直接实现和外部适配器。缓存包装器必须查询实时容量。避免给互相重叠的类重复注册：适配器遍历顺序没有优先级承诺。注册在整个会话期间有效。

### FastPath 供应器约定

`ECOFastPathDispatchProvider` 是经过验证的原子 FastPath 执行约定，与普通并行接收分开。准备探测不能移动资源。供应器负责核对具体配方、返还与容器语义、容量和提交目标。

| 方法或数据 | 含义 |
| --- | --- |
| `eco$prepareFastPath(context)` | null 表示不支持，否则返回正容量和发配回调。 |
| `ECOBatchDispatchContext` | **单次样板执行**的具体输入槽、产物及返还物。 |
| `Preparation.statefulCalculator` | 状态变化或复用材料的可选计算器，必须遵守 `statefulCalculatorRequired`。 |
| `Batch.inputTotal/outputTotal/remainingTotal` | 已接收批次的完整总量。 |
| `supportsExactInputs`、`Batch.exactInputTotal` | 显式开启精确输入扣账，不可为旧供应器截断精确输入。 |
| `eco$prepareExactFastPath(context, requested)` | 独立的精确次数约定，默认不支持。 |
| `ECOBatchCapacityProvider` | 已弃用的兼容接口，新接入使用 `ECOFastPathDispatchProvider`。 |

完整接收后返回 true。false 或普通异常保证没有接收，因此 CPU 可以回滚。如果可能已经接收、但无法确定，抛出 `ECOIndeterminateBatchException`；事务保留资源，任务停止并等待核对。不能退款后自动重试不确定的接收。

### 外部 CPU 调用 ECO 事务门面

```java
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import net.minecraft.world.level.Level;
import java.util.UUID;
import java.util.function.Consumer;

public final class EcoFastPathExample {
    // oneCopyInputs/Outputs/Remainders are previews; inputs are still in cpuInventory.
    public static boolean dispatch(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] oneCopyInputs, KeyCounter oneCopyOutputs, KeyCounter oneCopyRemainders,
            ListCraftingInventory cpuInventory, long maxCrafts, double powerPerCraft,
            IEnergyService energy, Level level, UUID jobId,
            ECOFastPathFacade.EnergyAccount energyAccount,
            Consumer<ECOFastPathFacade.PreparedBatch> accountAccepted) {
        var prepared = ECOFastPathFacade.prepare(provider, pattern,
            oneCopyInputs, oneCopyOutputs, oneCopyRemainders, cpuInventory,
            maxCrafts, powerPerCraft, energy, level, jobId);
        if (prepared == null || !prepared.submit(energyAccount)) return false;
        // Provider acceptance already happened. Do not retry if accounting throws.
        accountAccepted.accept(prepared);
        return true;
    }
}
```

准备与提交在同一个服务器线程 tick 完成。物理输入在门面扣账之前仍位于 CPU 库存，CPU 不再自行提取或退还这些输入。CPU 的 `EnergyAccount.reserve` 返回能量预留对象；`commit()` 不能抛异常，`refund()` 必须将网络无法接收的退款保存在 CPU 的持久化能量余额中。

`PreparedBatch` 只能使用一次，拒绝后也不能重复提交。准备返回 null 或提交返回 false 时，可以在回滚完成后走普通路径。不确定异常必须停任务。返回 true 后，CPU 按门面给出的总量，**仅记账一次**样板次数、产物和返还物；记账失败不能撤销供应器已经完成的接收。

`prepareParallel` 适配普通并行接口。`prepareAllocated` 只适用于已经分配好的、无状态且没有返还输入的等量配方，其所有权约定不同于库存模式。使用前阅读[门面契约](../src/main/java/cn/dancingsnow/neoecoae/api/me/ECOFastPathFacade.java)。

`api.fastpath.EcoFastpathHost` 是另一套 inspect/submit 能力协议。请求携带能力 ID、API 版本、nonce、配方定义和单次输入。通过实际接入获得主机实现；这个接口没有提供全局能力查询，也不替代 CPU 事务。

## 7. 产物交付、虚拟完成与进度

```java
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.output.*;
import java.util.UUID;

public final class EcoOutputExample {
    public static ECOCraftingOutputClaimResult deliver(ECOCraftingOutputClaimSink sink,
            UUID jobId, AEKey expected, AEKey actual, long offered) {
        return sink.claimCraftingOutput(new ECOCraftingOutputClaimRequest(
            jobId, expected, actual, offered, Actionable.MODULATE));
    }
}
```

通过接入的 CPU 或逻辑桥获得接收端；旧 ECO CPU 类型暴露 `getOutputClaimSink()`。动态产物的预期 Key 与实际 Key 可以不同。认领操作一起完成任务身份校验、预期需求扣账、实际产物路由和完成记账。

实际调用后，生产者的物理缓存只移除 `claimedAmount()`，未被接收的部分继续保留。模拟调用只报告匹配需求与预计路由，不消耗资源。目的地已满时，产物仍可能被 CPU 接收；分别查看 `deliveredToRequester()`、`deliveredToNetwork()` 与 `storedInCpu()`。不能再通过另一条路径插入已经认领的数量。

| 接口 | 调用与责任 |
| --- | --- |
| `ECOCraftingOutputClaimSink` | `claimCraftingOutput(request)`，读取状态和实际认领数量。 |
| `ECOCraftingOutputRouter` | `neoecoae$insertIntoCpuForJob(jobId, key, amount, mode)`，按任务路由，并保留未接收部分。 |
| `ECOJobOutputReceiver` | `neoecoae$insertWorkerOutput(...)`，CPU 接收 Worker 产物，包含多余产物。 |
| `ECOVirtualCraftingCompletionSink` | `tryCompleteVirtualCrafting(pattern, completedCrafts)`，接受逻辑完成次数不代表整个任务结束。 |
| `ECOCraftingProgressSink` | `recordCompletedCraftingWork(amount, keyType)`，只报告已经完成的工作量。 |
| `ECOCraftingProgressView` | 读取 `progress()`、`elapsedTimeNanos()`、各 Key 类型工作量和可选父订单进度。 |

进度记账不交付物理资源。如果产物认领路径已经更新进度，不要重复报告完成。模拟、实际交付和虚拟完成是不同操作。

源码：[认领结果](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOCraftingOutputClaimResult.java)、[虚拟完成](../src/main/java/cn/dancingsnow/neoecoae/api/me/completion/ECOVirtualCraftingCompletionSink.java)、[进度视图](../src/main/java/cn/dancingsnow/neoecoae/api/me/progress/ECOCraftingProgressView.java)。

## 8. 样板导入与容器所有权

```java
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.*;
import net.minecraft.world.item.ItemStack;

public final class EcoPatternExample {
    public static ECOPatternInsertion insert(IGrid grid, ItemStack encoded,
            IPatternDetails decoded) {
        var service = grid.getService(IECOPatternStorageService.class);
        var prepared = new ECOPreparedPattern(encoded, decoded, AEItemKey.of(encoded));
        return service.insertPreparedPatternReporting(prepared);
    }
}
```

传入与编码物品一致的已解析样板，网络服务负责路由和查重：

| 结果 | 调用方行为 |
| --- | --- |
| `INSERTED` | 按报告结果提交源物品的转移事务。 |
| `ALREADY_PRESENT` | 配方已经存在，不能据此认定源物品已被搬走。 |
| `NO_SPACE` | 保留源物品，容量改变后重试。 |
| `INCOMPATIBLE`、`NO_TARGET` | 保留源物品，报告不支持或没有目标。 |

目标保存编码**物品**时，不需要额外返还空白样板。`consumedSource()` 为 true 表示容器吸收了配方，此时 `blankReplacement()` 必须非空。确认完整替代物已经交付后，再清空源槽。部分完成的迁移必须保存事务状态，避免重试时重复插入或重复返还空白。

`IECOPatternStorage` 是供可写目标使用的 AE2 节点服务，在 managed node 上注册。它的 `KnownUnique` 方法要求调用方已经证明没有重复，不是通用快速插入入口。`canAcceptIntoAuxiliary` 不得有副作用。目录索引的主机实现 `PatternStorageHost` 和 `AuxiliaryPatternHolder`，提供真实槽位、便宜的修订号、稳定的容器内容顺序和刷新后的公布样板。

外部迁移通过 `claimExternalPatternCandidates` 使用 owner UUID 认领候选。扫描的 `ready` 为 false 时也可以处理已经发现的候选。所有退出路径释放认领；临时无空间时释放单个候选，源槽清空后删除候选。不要在拓扑变化后继续使用旧的源槽引用。

源码：[导入报告](../src/main/java/cn/dancingsnow/neoecoae/api/ECOPatternInsertion.java)、[可写样板存储](../src/main/java/cn/dancingsnow/neoecoae/api/IECOPatternStorage.java)、[目录主机](../src/main/java/cn/dancingsnow/neoecoae/api/PatternStorageHost.java)。

## 9. 存储调用与存储盘接入

```java
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.ISaveProvider;
import net.minecraft.world.item.ItemStack;
import cn.dancingsnow.neoecoae.api.storage.*;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import java.math.BigInteger;

public final class EcoStorageExample {
    public static void register(FMLCommonSetupEvent event, IECOCellHandler handler) {
        event.enqueueWork(() -> ECOStorageCells.register(handler));
    }

    public static IECOStorageCell inventory(ItemStack cell, ISaveProvider host) {
        return ECOStorageCells.getCellInventory(cell, host);
    }

    public static void release(ItemStack cell, ISaveProvider host) {
        ECOStorageCells.releaseCellInventory(cell, host);
    }

    public static void setPriority(Object host, int priority) {
        if (host instanceof IECOStoragePriorityHost target) target.setStoragePriority(priority);
    }

    public static BigInteger insertExact(MEStorage storage, AEKey key,
            BigInteger offered, IActionSource source) {
        return ECOBigIntegerStorage.insert(storage, key, offered, Actionable.MODULATE, source);
    }
}
```

在入队的通用初始化工作中，将每个 `IECOCellHandler` 注册一次。第一个返回非空库存的处理器生效。识别、库存创建、主机缓存释放和运行时清理必须一致。库存实现 `IECOStorageCell`，盘物品元数据实现 `IECOStorageCellItem` 或 `IBasicECOCellItem`。

直接调用处理器门面时，在有主机的情况下传入拥有该盘的主机，并在主机销毁时释放：

`EcoStorageExample.inventory` 获取主机绑定的库存，主机移除时由 `release` 释放绑定。

门面自身提供同步访问，但不转移物品所有权，也不代替调用方或终端执行权限检查。

### 盘槽与优先级

通过 `blockEntity instanceof ICellHost` 查询。`getCellStack()` 空槽返回 null，也可能返回主机正在持有的栈，因此不能直接修改。安装时传一个有效盘；移除时传 **null**。`ItemStack.EMPTY` 会被拒绝。先检查 `isItemValid`、`canExtractCell` 及可选的 `getCellExtractionBlockReasonText()`。void setter 可能静默拒绝，因此在转移物品所有权之前重新读取盘槽。先移除并处理旧盘，再安装替代盘。

`IECOStoragePriorityHost.setStoragePriority(int)` 接受有符号配置优先级，保存、同步并刷新存储挂载。调用方先鉴权，再在服务器线程修改；客户端或没有 level 的主机忽略调用。

### 精确插入与迁移

`ECOBigIntegerStorage.insert` 返回**已插入数量**，不是剩余数量。调用方用提供数量减去返回值。long 范围内走普通 AE2 插入；超出 long 后，优先使用精确存储实现，否则只提供一段 `Long.MAX_VALUE`。它不保证一次完全插入，也不会循环插入所有分段。区分 `Actionable.SIMULATE` 与 `MODULATE`。

只有能无损枚举、清空、模拟插入、持久化和恢复的库存，才实现 `IECOStorageMigrationCell` 参与可恢复迁移。`IECOBulkMarkableCellItem` 只开启手动压缩标记面板，不注册存储后端，也不开启自动转移。`IECOBulkDisplayCell` 独立控制压缩显示截止等级。

### 注册表与客户端模型

自定义等级与盘类型使用 `NERegistries.Keys` 暴露的同步注册表 `neoecoae:eco_tier`、`neoecoae:cell_type`，通过 NeoForge 注册事件在两端注册相同 ID。实现 `IECOTier` 所有必需参数，等级比较决定组件兼容性。

客户端接入通过 `ECOCellModels.register`、`ECOComputationModels.registerCellModel/registerCableModel` 注册盘与线缆模型。涉及客户端 GUI 或渲染的引用放在客户端初始化中。不要在游戏运行中直接改模型映射或调用内部延迟注册方法。

## 10. 诊断与接入限制

`ECOCraftingServiceDiagnostics.neoecoae$describeCpuSelection(plan, source)` 用于查询 CPU 资格；`ECOPatternPushDiagnostics.neoecoae$getPushDiagnostics()` 用于读取最近一次推送失败。诊断只描述状态，不授权重放或退款。

缺材料、不支持、未解或数量无法表示时，仍可能返回模拟壳计划。非空计划本身不足以提交。参阅[规划状态表](ECO_PLANNER_ZH_CN.md#8-结果与诊断)，保留阻止执行的元数据。

可复现的问题报告应包含实际 JAR 版本、动作来源、选项、规划 ID、状态与诊断、CPU 提交结果以及供应器和产物日志。规划、CPU 预留、供应器接收和最终交付属于不同阶段。这些示例用于验证调用签名，不能代替游戏内兼容性验证。
