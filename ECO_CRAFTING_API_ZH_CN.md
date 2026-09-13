# NeoECOAE 合成集成 API 使用指南

本文档面向需要与 NeoECOAE 合成 CPU 集成的附属模组。这些 API 用于替代对
<code>ECOCraftingCPULogic</code>、<code>ExecutingCraftingJob</code>、<code>ElapsedTimeTracker</code>、
任务表和等待产物表的反射访问。

本文档对应 NeoForge 1.21.1 分支的当前源码。

## API 包结构

| 包 | 用途 |
| --- | --- |
| <code>cn.dancingsnow.neoecoae.api.me.output</code> | 接收动态产物或替代产物，并完成等待表、路由和进度记账 |
| <code>cn.dancingsnow.neoecoae.api.me.progress</code> | 写入或只读查询合成进度 |
| <code>cn.dancingsnow.neoecoae.api.me.completion</code> | 报告不产生实体产物的虚拟合成完成 |
| <code>cn.dancingsnow.neoecoae.api.me.lifecycle</code> | 监听任务开始、样板发配和任务结束 |
| <code>cn.dancingsnow.neoecoae.api.me.attachment</code> | 为每个 ECO 合成任务挂载并持久化第三方数据 |
| <code>cn.dancingsnow.neoecoae.api.me.dispatch</code> | 对 CPU tick 和样板供应器可用性增加调度策略 |

## 通用约束

1. 会修改任务状态的 API 必须在服务器线程调用，包括 Output Claim、Progress Sink 和 Virtual Completion。
2. 不要缓存或反射 <code>job</code>、<code>tasks</code>、<code>waitingFor</code>、
   <code>timeTracker</code> 或 <code>finishJob</code>。
3. 如果手中持有 AE2 的 <code>ICraftingCPU</code>，应先判断它是否为 ECO CPU：

~~~java
if (!(craftingCpu instanceof ECOCraftingCPU ecoCpu)) {
    return; // 不是 ECO CPU
}
~~~

4. 标有 <code>@ApiStatus.Internal</code> 的方法只供 NeoECOAE 内部调用。
5. 全局监听器、Attachment 工厂和调度策略最好在模组公共初始化阶段注册。

## 1. 动态或替代产物：Output Claim API

### 适用场景

机器实际产生的资源键与 ECO 计划中等待的资源键可能不同，例如动态 NBT 产物、按运行结果变化的产物，
或由第三方机器进行合法替代的产物。此时使用 <code>ECOCraftingOutputClaimSink</code>，不要直接修改等待表。

入口：

~~~java
ECOCraftingOutputClaimSink sink = ecoCpu.getOutputClaimSink();
~~~

### 请求字段

<code>ECOCraftingOutputClaimRequest</code> 包含：

| 字段 | 含义 |
| --- | --- |
| <code>craftingJobId</code> | 产物所属的 AE2 合成任务 UUID，必须与当前 CPU 的任务一致 |
| <code>expectedKey</code> | ECO 计划和等待表中原本保留的资源键 |
| <code>actualKey</code> | 机器实际产生的资源键 |
| <code>amount</code> | 本次尝试认领的数量，必须大于 0 |
| <code>mode</code> | <code>Actionable.SIMULATE</code> 或 <code>Actionable.MODULATE</code> |

<code>expectedKey</code> 与 <code>actualKey</code> 可以相同。即使相同，也可以借助该 API
获得任务 UUID 校验和完整的原子记账。

### 示例

~~~java
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimRequest;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimResult;
import java.util.UUID;

public static ECOCraftingOutputClaimResult deliverDynamicOutput(
        ECOCraftingCPU ecoCpu,
        UUID jobId,
        AEKey expectedKey,
        AEKey actualKey,
        long amount) {
    var request = new ECOCraftingOutputClaimRequest(
            jobId, expectedKey, actualKey, amount, Actionable.MODULATE);
    return ecoCpu.getOutputClaimSink().claimCraftingOutput(request);
}
~~~

需要预检时，可以先提交 <code>SIMULATE</code> 请求，再提交 <code>MODULATE</code> 请求：

~~~java
var preview = sink.claimCraftingOutput(new ECOCraftingOutputClaimRequest(
        jobId, expectedKey, actualKey, amount, Actionable.SIMULATE));

if (preview.accepted() && preview.claimedAmount() > 0) {
    var committed = sink.claimCraftingOutput(new ECOCraftingOutputClaimRequest(
            jobId, expectedKey, actualKey, preview.claimedAmount(), Actionable.MODULATE));
    // 只把 committed.claimedAmount() 视为已被 ECO 接管的数量。
}
~~~

<code>SIMULATE</code> 不修改任务，且返回值中的 <code>jobFinished</code> 始终为
<code>false</code>。模拟与执行之间任务状态仍可能变化，最终必须以 <code>MODULATE</code> 的返回值为准。

### 返回状态

| 状态 | 含义 |
| --- | --- |
| <code>ACCEPTED</code> | 找到了匹配的等待条目，并接受了返回值中的 <code>claimedAmount</code> |
| <code>NO_JOB</code> | CPU 当前没有任务 |
| <code>NO_MATCH</code> | 当前等待表中没有可认领的 <code>expectedKey</code> 数量 |
| <code>INVALID_REQUEST</code> | 请求字段为空或数量不合法 |
| <code>TERMINAL</code> | 任务 UUID 不属于当前任务，或者该任务已经终止 |

结果数量满足：

~~~text
claimedAmount = deliveredToRequester + deliveredToNetwork + storedInCpu
~~~

<code>storedInCpu</code> 表示暂时不能交付的部分已经由 ECO CPU 保管，调用方不得再次插入这部分。
<code>remainingFinalOutput</code> 是最终请求产物尚未交付的数量；
<code>jobFinished</code> 表示这次提交是否令整个任务结束。

### 不要重复更新进度

成功的 <code>MODULATE</code> Output Claim 已经负责：

- 扣除 <code>expectedKey</code> 对应的等待数量；
- 路由或保管 <code>actualKey</code>；
- 更新进度；
- 更新最终产物剩余量；
- 检查任务是否可以结束。

调用后不要再为同一批产物调用 <code>recordCompletedCraftingWork</code>，否则会重复记账。

## 2. 合成进度：Progress API

Progress API 分为写入接口和只读接口。

### 写入完成工作量

<code>ECOCraftingProgressSink</code> 仅记录某种 AE Key 类型已经完成的工作量：

~~~java
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressSink;

if (ecoCpu.getLogic() instanceof ECOCraftingProgressSink progressSink) {
    progressSink.recordCompletedCraftingWork(completedAmount, producedKey.getType());
}
~~~

如果代码本身是注入 <code>ECOCraftingCPULogic</code> 的 Mixin，可以直接按接口访问目标对象：

~~~java
ECOCraftingProgressSink progressSink = (ECOCraftingProgressSink) (Object) this;
progressSink.recordCompletedCraftingWork(completedAmount, producedKey.getType());
~~~

约束：

- <code>amount &lt; 0</code> 或 <code>keyType == null</code> 会抛出参数异常；
- <code>amount == 0</code> 是无操作；
- CPU 没有活动任务时是无操作；
- 它不会扣除等待产物、插入物品、减少最终产物数量或结束任务；
- 只应在外部设备确认工作完成、但产物账本由其他机制处理时调用；
- 如果使用了 Output Claim API，不要再调用此方法。

### 读取进度快照

~~~java
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressView;

ECOCraftingProgressView progress = ecoCpu.getProgressView();
float ratio = progress.progress();          // 0.0 到 1.0
long elapsedNanos = progress.elapsedTimeNanos();
long started = progress.startedWork(producedKey.getType());
long completed = progress.completedWork(producedKey.getType());
~~~

返回对象是与内部 Tracker 脱离的不可变快照，可以安全交给显示层。它不会自动刷新；需要新数据时，应再次
调用 <code>getProgressView()</code>。CPU 没有活动任务时返回空快照。

不要再使用已弃用的 <code>getElapsedTimeTracker()</code>，也不要假设 ECO Tracker 与 AE2 原版 Tracker
是同一个类。

## 3. 虚拟合成完成：Virtual Completion API

某个集成在逻辑上直接完成样板执行，并且不会再产生需要插入 ECO CPU 的实体产物时，使用
<code>ECOVirtualCraftingCompletionSink</code>。

~~~java
import cn.dancingsnow.neoecoae.api.me.completion.ECOVirtualCraftingCompletionSink;

ECOVirtualCraftingCompletionSink completion = ecoCpu.getVirtualCraftingCompletionSink();
boolean accepted = completion.tryCompleteVirtualCrafting(pattern, completedCrafts);
if (!accepted) {
    // 不要假装成功；保留外部工作状态，或者回退到原本的交付路径。
}
~~~

返回 <code>false</code> 的常见原因：

- CPU 没有活动任务；
- <code>pattern</code> 为空或 <code>completedCrafts &lt;= 0</code>；
- 当前任务已终止；
- 该样板不属于当前任务；
- 完成数量超过剩余任务量；
- ECO Planner 当前阶段或批次数量不允许这次完成。

返回 <code>true</code> 只表示任务账本接受了这次虚拟完成，不保证整个任务已经结束。只有任务、等待产物和
Planner 运行时都完成后，ECO 才会结束整个任务。

不要用此 API 报告仍会产生实体产物的普通机器工作，否则后续真实产物可能成为重复产物。

## 4. 合成生命周期：Lifecycle API

生命周期监听器是进程级注册，通常在模组初始化时注册一次：

~~~java
import cn.dancingsnow.neoecoae.api.me.lifecycle.*;
import java.util.UUID;

public final class ExampleLifecycleListener implements ECOCraftingLifecycleListener {
    @Override
    public void onJobStarted(ECOCraftingJobContext context) {
        UUID jobId = context.craftingJobId();
    }

    @Override
    public void onPatternDispatched(ECOCraftingDispatchEvent event) {
        long crafts = event.dispatchedCrafts();
        var provider = event.provider();
    }

    @Override
    public void onJobFinished(ECOCraftingJobContext context, ECOCraftingJobResult result) {
        if (result.successful()) {
            // 任务正常完成。
        }
    }
}

private static final ExampleLifecycleListener LISTENER = new ExampleLifecycleListener();

public static void initializeIntegration() {
    ECOCraftingLifecycle.register(LISTENER);
}
~~~

| 回调 | 时机 |
| --- | --- |
| <code>onJobStarted</code> | 新任务成功安装到 ECO CPU 后；当前不会为读档恢复任务再次触发 |
| <code>onPatternDispatched</code> | 供应器接受普通或批量发配，并且 ECO 已更新任务账本后 |
| <code>onJobFinished</code> | ECO 已处理链接、附件和当前任务清理后 |

<code>ECOCraftingJobContext</code> 包含 CPU、任务 UUID、最终产物、请求数量和剩余数量。
<code>ECOCraftingJobResult.Status</code> 定义了 <code>SUCCESS</code>、<code>FAILURE</code> 和
<code>CANCELLED</code>。当前 CPU 完成路径会发送 <code>SUCCESS</code> 或 <code>CANCELLED</code>；
<code>FAILURE</code> 为显式失败结果预留。

监听器异常会被 ECO 捕获并记录，不会中断 CPU；监听器仍应保持轻量，不要阻塞服务器线程，也不要从回调中
反向修改 ECO 任务。只需要部分事件时，只覆写对应的默认方法即可。

## 5. 每任务扩展数据：Job Attachment API

第三方模组需要让自有状态跟随 ECO 合成任务创建、存档、读档和清理时，注册
<code>ECOCraftingJobAttachment</code>。它替代向任务类注入字段和注入 NBT/结束方法的做法。

~~~java
import cn.dancingsnow.neoecoae.api.me.attachment.*;
import cn.dancingsnow.neoecoae.api.me.lifecycle.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class ExampleJobAttachment implements ECOCraftingJobAttachment {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("examplemod", "eco_job_state");

    private long completedOperations;

    public ExampleJobAttachment(ECOCraftingJobContext context) {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("completedOperations", completedOperations);
        return tag;
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        completedOperations = Math.max(0L, tag.getLong("completedOperations"));
    }

    @Override
    public void clear(ECOCraftingJobResult result) {
        // 释放只属于该任务的外部资源。
    }
}

public static void registerAttachments() {
    ECOCraftingJobAttachmentRegistry.register(
            ExampleJobAttachment.ID,
            ExampleJobAttachment::new);
}
~~~

约束：

- 注册 ID 必须唯一，重复注册会抛出异常；
- 工厂结果不能为 <code>null</code>，且 <code>attachment.id()</code> 必须等于注册 ID；
- 每个任务必须拥有独立 Attachment 实例，不要返回全局单例；
- <code>save</code> 必须返回非空 <code>CompoundTag</code>；
- <code>load</code> 应能处理字段缺失和旧版本数据；
- <code>clear</code> 在任务成功完成或取消时调用；
- 不要调用标有 <code>@ApiStatus.Internal</code> 的 <code>create</code> 或 <code>createAll</code>。

附属模组暂时未加载时，ECO 会保留无法绑定的原始 Attachment 数据。因此不要复用其他模组的命名空间，
也不要随意更改已经发布的 ID。

## 6. 调度策略：Dispatch Policy API

第三方模组需要暂停某个 ECO CPU 的调度，或者在 ECO 原有检查之外否决某个供应器时，注册
<code>ECOCraftingDispatchPolicy</code>。

~~~java
import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.api.me.dispatch.*;

public final class ExampleDispatchPolicy implements ECOCraftingDispatchPolicy {
    @Override
    public boolean mayTick(ECOCraftingCpuContext cpu) {
        return !ExternalController.isPaused(cpu.craftingJobId());
    }

    @Override
    public boolean isProviderAvailable(
            ECOCraftingCpuContext cpu,
            ICraftingProvider provider) {
        return !provider.isBusy() && ExternalController.mayUse(provider);
    }
}

private static final ExampleDispatchPolicy POLICY = new ExampleDispatchPolicy();

public static void registerPolicy() {
    ECOCraftingDispatchPolicyRegistry.register(POLICY);
}
~~~

所有策略按“共同否决”处理：任何一个策略返回 <code>false</code>，本 tick 或该供应器就不会继续调度。
策略抛出 <code>RuntimeException</code> 时，注册表会 fail-closed，并拒绝相应调度。

策略只负责回答是否允许：

- 不要在回调中抽取输入；
- 不要调用 <code>pushPattern</code>；
- 不要修改任务、等待表或供应器状态；
- 不要长时间阻塞；
- 不要把它当作替换 ECO 调度器的入口。

<code>ECOCraftingCpuContext</code> 提供当前 CPU、可空任务 UUID、CPU 活跃状态、协处理器数量和游戏 tick。
如果只需要观察发配结果，应使用 Lifecycle API 的 <code>onPatternDispatched</code>。

## 常见集成选择

| 需求 | 应使用的 API |
| --- | --- |
| 机器产生与计划不同的实际产物 | Output Claim |
| 只补记已完成工作量 | Progress Sink |
| GUI、监控器读取进度 | Progress View |
| 虚拟样板不再产生实体产物 | Virtual Completion |
| 观察任务开始、发配和结束 | Lifecycle |
| 保存第三方每任务状态 | Job Attachment |
| 暂停 tick 或否决供应器 | Dispatch Policy |

## 从反射迁移

| 原做法 | 替代 API |
| --- | --- |
| 反射 <code>ElapsedTimeTracker.decrementItems</code> | <code>ECOCraftingProgressSink.recordCompletedCraftingWork</code> |
| 反射等待表、库存、最终数量和结束方法处理动态产物 | <code>ECOCraftingOutputClaimSink</code> |
| 反射任务表并调用结束方法完成虚拟任务 | <code>ECOVirtualCraftingCompletionSink</code> |
| 注入发配或结束方法观察事件 | <code>ECOCraftingLifecycleListener</code> |
| 注入任务 NBT 保存第三方字段 | <code>ECOCraftingJobAttachment</code> |
| Redirect 供应器 busy 或 CPU tick | <code>ECOCraftingDispatchPolicy</code> |

迁移完成后，兼容代码不应再依赖 ECO 内部字段名、内部 Tracker 的具体类型或私有结束方法。
