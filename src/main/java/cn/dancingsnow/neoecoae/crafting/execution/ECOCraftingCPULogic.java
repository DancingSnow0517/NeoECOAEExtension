package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachments;
import cn.dancingsnow.neoecoae.api.me.completion.ECOVirtualCraftingCompletionSink;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingCpuContext;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingDispatchPolicyRegistry;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingLifecycle;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimRequest;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimResult;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimSink;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressSink;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressSnapshot;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressView;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.core.network.ClientboundPacket;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOSingleCraftingExecutor;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;

public class ECOCraftingCPULogic implements ECOCraftingProgressSink,
        ECOCraftingOutputClaimSink, ECOVirtualCraftingCompletionSink {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    final ECOCraftingCPU cpu;

    /**
     * 当前合成任务。
     */
    @Getter
    private ExecutingCraftingJob job = null;
    /**
     * 库存。
     */
    @Getter
    private final ListCraftingInventory inventory = new cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory(ECOCraftingCPULogic.this::postChange);
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    private final ECOCraftingJobAttachments jobAttachments = new ECOCraftingJobAttachments();
    /**
     * 如果 CPU 正在尝试清空库存但无法完成，则为 true。
     */
    @Getter
    private boolean cantStoreItems = false;

    @Getter
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();

    @Getter
    private boolean markedForDeletion = false;

    private final ECOCraftingDispatchStrategy dispatchStrategy = new ECOCraftingDispatchStrategy();
    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting dispatchAccounting;
    private final ECOCraftingFastPathDispatcher fastPathDispatcher;
    private final ECOCraftingProviderDispatcher providerDispatcher;
    private final ECOCraftingTaskScheduler taskScheduler;
    private final ECOCraftingOutputDelivery outputDelivery;
    private final ECOCraftingCpuView view;
    private final ECOCraftingCpuPersistence persistence;
    private final ECOCraftingJobLifecycleController lifecycle;
    final ECOBigOrderController bigOrder = new ECOBigOrderController(this);

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
        this.energyTransaction = new ECOCraftingEnergyTransaction(
                this::markCpuDirty, TickHandler.instance()::getCurrentTick);
        this.dispatchAccounting = new ECOCraftingDispatchAccounting(
                this::postChange, this::markCpuDirty, this::createJobContext,
                ECOCraftingLifecycle::firePatternDispatched);
        this.fastPathDispatcher = new ECOCraftingFastPathDispatcher(this, energyTransaction, dispatchAccounting);
        this.providerDispatcher = new ECOCraftingProviderDispatcher(
                this, fastPathDispatcher, energyTransaction, dispatchAccounting);
        this.taskScheduler = new ECOCraftingTaskScheduler(providerDispatcher);
        this.outputDelivery = new ECOCraftingOutputDelivery(this);
        this.view = new ECOCraftingCpuView(this);
        this.persistence = new ECOCraftingCpuPersistence(this);
        this.lifecycle = new ECOCraftingJobLifecycleController(this);
    }

    public ICraftingSubmitResult trySubmitJob(
            IGrid grid, ICraftingPlan plan, IActionSource src, @Nullable ICraftingRequester requester) {
        return lifecycle.submit(grid, plan, src, requester);
    }

    public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {
        if (job != null && ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), job.link.getCraftingID())) {
            cancel();
            return;
        }
        if (!cpu.isActive()) {
            return;
        }
        if (bigOrder.tick()) return;
        cantStoreItems = false;
        if (this.job == null) {
            energyTransaction.returnIdleCredit(eg);
            this.storeItems();
            if (!this.inventory.list.isEmpty()) {
                cantStoreItems = true;
            } else {
                if (markedForDeletion) {
                    cpu.deactivate();
                }
            }
            return;
        }
        if (job.link.isCanceled()) {
            cancel();
            return;
        }

        if (!job.suspended) refillExactMaterials();

        long currentTick = TickHandler.instance().getCurrentTick();
        taskScheduler.bindDiagnostics(job.link.getCraftingID(), currentTick);
        taskScheduler.beginResolveTick(currentTick);
        deliverStoredFinalOutput();
        if (job == null || job.suspended) {
            return;
        }
        Level level = cpu.getLevel();
        if (level == null) {
            return;
        }

        if (!ECOCraftingDispatchPolicyRegistry.mayTick(createCpuContext(currentTick))) {
            dispatchStrategy.finishTick(0);
            taskScheduler.finishResolveTick();
            return;
        }

        int operationLimit = dispatchStrategy.beginTick(cpu.getCoProcessors(), NEConfig.ecoCpuPushTickLimit);
        int acceptedNormalPushes = 0;
        // Thunderbolt 会包装此处的 executeCrafting 调用；快速路径的批量操作不消耗此慢速路径额度。
        taskScheduler.beginSharedProbeBudget(operationLimit);
        try {
            while (job != null) {
                int pushed = executeCrafting(operationLimit, cc, eg, level);
                var pass = taskScheduler.lastPass();
                taskScheduler.consumeSharedProbeBudget(pass.normalProbes());
                operationLimit = Math.max(0, operationLimit - pass.acceptedNormalPushes());
                acceptedNormalPushes += pass.acceptedNormalPushes();
                if (pushed == 0) break;
            }
        } finally {
            taskScheduler.endSharedProbeBudget();
            taskScheduler.finishResolveTick();
        }
        // 只有普通推送计入滚动操作窗口。
        dispatchStrategy.finishTick(acceptedNormalPushes);
        if (job != null) {
            taskScheduler.check(job);
        }
    }

    /** 从所有配方输入共用的实际库存中重试交付产物。 */
    private void deliverStoredFinalOutput() {
        outputDelivery.deliverStoredFinalOutput();
    }

    static int calculateOperationLimit(int coProcessors, int configuredLimit) {
        long baseLimit = 64L;
        long safeConfiguredLimit = Math.min(
            (long) NEConfig.MAX_ECO_CPU_PUSH_TICK_LIMIT,
            Math.max(0L, configuredLimit)
        );
        return (int) Math.min(Integer.MAX_VALUE, Math.min(baseLimit, safeConfiguredLimit));
    }

    private Iterable<ICraftingProvider> collectAvailableProviders(CraftingService craftingService,
            IPatternDetails details) {
        // 此处必须且只能保留一次 getProviders 调用；仅在 CPU 中提供转发桩方法不足以满足兼容要求。
        return craftingService.getProviders(details);
    }

    /**
     * 尝试将样板推送到可用接口中，即执行实际的合成操作。
     *
     * @param maxPatterns 剩余可接受的普通推送次数；已验证的批量操作不消耗此额度。
     * @return 成功推送的样板数量。
     */
    public int executeCrafting(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        // Mixin 二进制兼容锚点：Thunderbolt 会包装本方法中的这一调用，必须保留其调用位置。
        // 实际调度实现拆分到下方各策略对应的方法中；此处的守卫条件在运行时刻意保持为假。
        // 这里仅用于提供兼容锚点，绝不会访问合成提供者。
        if (thunderboltMixinAnchorEnabled()) {
            ICraftingProvider anchorProvider = null;
            anchorProvider.pushPattern(null, null);
        }
        return executeNormalCrafting(maxPatterns, craftingService, energyService, level);
    }

    private static boolean thunderboltMixinAnchorEnabled() {
        return false;
    }

    /** 执行一轮调度。 */
    public int executeNormalCrafting(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        var current = job;
        if (current == null) return 0;
        var pass = taskScheduler.execute(
                maxPatterns,
                craftingService,
                energyService,
                level,
                current,
                inventory,
                pattern -> collectAvailableProviders(craftingService, pattern),
                () -> job == current,
                createCpuContext(TickHandler.instance().getCurrentTick()),
                this::invokeNormalProvider);
        return pass.totalPushed();
    }

    /**
     * 普通合成提供者调用的兼容适配器。候选项和提供者的顺序由调度器管理；
     * 此处将各提供者特有的 AE2、Thunderbolt 和 Useless 调用行为保留在所属 CPU 中。
     */
    private boolean invokeNormalProvider(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        if (provider instanceof ECOCraftingPatternBusBlockEntity) {
            return ECOSingleCraftingExecutor.pushPattern(
                    provider,
                    request.pattern(),
                    request.inputs(),
                    request.outputs(),
                    request.remainders(),
                    request.level(),
                    request.job().link.getCraftingID());
        }

        if (provider instanceof ECOParallelCraftingProvider parallelProvider) {
            return parallelProvider.eco$pushPatternBatch(
                    request.pattern(), request.inputs(), 1L, request.job().link.getCraftingID());
        }

        // 兼容性约定：
        // 必须在 ECOCraftingCPULogic 中原样保留此 ICraftingProvider.pushPattern 调用。
        // Useless 和 Thunderbolt 的集成可能针对其所属类、方法描述符或调用点进行包装。
        return provider.pushPattern(request.pattern(), request.inputs());
    }

    /** 将尚待接收的产物存入 CPU 自有的实际库存。 */
    public long insert(AEKey what, long amount, Actionable type) {
        var current = job;
        if (current == null || what == null || amount <= 0L) {
            return outputDelivery.insert(what, amount, type);
        }

        // AE2 Utility 通过此方法中的真实调用点重定向带 NBT 物品的等待库存匹配；
        // 结果继续交给输出交付组件，避免破坏当前的任务校验、记账和完成流程。
        long accepted = current.waitingFor.extract(what, amount, Actionable.SIMULATE);
        // AE2 Utility 还会重定向此方法中的真实 matches 调用，用于判断最终产物交付阶段。
        boolean matchesFinalOutput = current.finalOutput != null && what.matches(current.finalOutput);
        return outputDelivery.insert(what, amount, type, accepted, matchesFinalOutput);
    }

    /**
     * 仅在此 CPU 仍持有所指定的合成任务时接收工作单元的产物，并将多余产物保留在本地。
     *
     * <p>工作单元的产物携带任务标识，但 AE2 的旧版 {@code insertIntoCpus} 接口不携带该信息。
     * 在 CPU 边界保留此检查，可防止产物被分配给恰好也在等待相同资源键的其他 CPU。</p>
     */
    public long insertForJob(UUID craftingJobId, AEKey what, long amount, Actionable type) {
        return outputDelivery.insertForJob(craftingJobId, what, amount, type);
    }

    /**
     * Claims an output when the machine's actual key is not necessarily the key reserved by the plan.
     * The waiting ledger is consumed only after the request has been validated; all un-delivered output is retained
     * in this CPU so a requester that accepts only part of the result cannot lose the remainder.
     */
    @Override
    public ECOCraftingOutputClaimResult claimCraftingOutput(ECOCraftingOutputClaimRequest request) {
        return outputDelivery.claimCraftingOutput(request);
    }

    /**
     * Completes a virtual execution while retaining ECO's task, phase and physical-output guards.
     */
    @Override
    public boolean tryCompleteVirtualCrafting(IPatternDetails pattern, long completedCrafts) {
        var current = job;
        if (current == null || pattern == null || completedCrafts <= 0L
                || ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), current.link.getCraftingID())) {
            return false;
        }

        Map.Entry<IPatternDetails, ExecutingCraftingJob.TaskProgress> taskEntry;
        try {
            taskEntry = current.tasks.entrySet().stream()
                    .filter(entry -> entry.getKey() == pattern
                            || ECOPhaseScheduler.samePattern(entry.getKey(), pattern))
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException rejected) {
            return false;
        }
        if (taskEntry == null || taskEntry.getValue() == null || taskEntry.getValue().value < completedCrafts) {
            return false;
        }

        if (current.executionRuntime != null) {
            ECOExecutionRuntime.DispatchCandidate candidate;
            try {
                candidate = current.executionRuntime.candidates().stream()
                        .filter(value -> ECOPhaseScheduler.samePattern(value.pattern(), pattern))
                        .findFirst()
                        .orElse(null);
            } catch (RuntimeException rejected) {
                return false;
            }
            if (candidate == null || completedCrafts > candidate.maxDispatchCount()) {
                return false;
            }
            try {
                current.executionRuntime.onVirtualAccepted(candidate, completedCrafts);
            } catch (RuntimeException rejected) {
                return false;
            }
        } else {
            taskEntry.getValue().accept(completedCrafts);
        }

        taskScheduler.progress(TickHandler.instance().getCurrentTick());
        markCpuDirty();

        if (!taskScheduler.hasPendingTasks(current)
                && current.waitingFor.list.isEmpty()
                && (current.executionRuntime == null || current.executionRuntime.isComplete())) {
            // A virtual provider has already accounted for the requested final output; no physical output callback
            // will arrive to decrement this field later.
            current.remainingAmount = 0L;
            finishJob(true);
        }
        return true;
    }

    public boolean hasCraftingJob(UUID craftingJobId) {
        return craftingJobId != null && job != null && craftingJobId.equals(job.link.getCraftingID());
    }

    /**
     * 完成当前合成任务。
     *
     * @param success 任务完成则为 true，取消则为 false。
     */
    void finishJob(boolean success) {
        lifecycle.finish(success);
    }

    /**
     * 取消当前合成任务。
     */
    public void cancel() {
        lifecycle.cancel();
    }

    /**
     * 尝试将所有本地存储的物品转存回存储网络。
     */
    public void storeItems() {
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        if (this.inventory.list.isEmpty())
            return;

        var g = cpu.getGrid();
        if (g == null)
            return;

        var storage = g.getStorageService().getInventory();

        boolean exact = exactInventory().isEnabled();
        var storedWindow = this.inventory.list;
        if (exact) {
            storedWindow = new KeyCounter();
            storedWindow.addAll(this.inventory.list);
        }
        for (var entry : storedWindow) {
            this.postChange(entry.getKey());
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE,
                    cpu.getActionSource());

            if (exact) this.inventory.extract(entry.getKey(), inserted, Actionable.MODULATE);
            else entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();

        markCpuDirty();
    }

    void postChange(@Nullable AEKey what) {
        taskScheduler.invalidateInputTemplates(what);
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        notifyListeners(what);
    }

    private void notifyListeners(@Nullable AEKey what) {
        for (var listener : listeners) listener.accept(what);
    }

    void markCpuDirty() {
        cpu.markDirty();
    }

    ECOCraftingTaskScheduler taskSchedulerForOutput() {
        return taskScheduler;
    }

    ECOCraftingTaskScheduler taskSchedulerForPersistence() {
        return taskScheduler;
    }

    ECOCraftingTaskScheduler taskSchedulerForLifecycle() {
        return taskScheduler;
    }

    ECOCraftingDispatchStrategy dispatchStrategyForPersistence() {
        return dispatchStrategy;
    }

    ECOCraftingJobAttachments jobAttachmentsForPersistence() {
        return jobAttachments;
    }

    ECOCraftingJobAttachments jobAttachmentsForLifecycle() {
        return jobAttachments;
    }

    ECOCraftingEnergyTransaction energyTransactionForPersistence() {
        return energyTransaction;
    }

    ECOCraftingOutputDelivery outputDeliveryForPersistence() {
        return outputDelivery;
    }

    void setJobFromPersistence(@Nullable ExecutingCraftingJob restoredJob) {
        this.job = restoredJob;
        exactInventory().setEnabled(restoredJob != null && restoredJob.exactOrder);
    }

    void setJobFromLifecycle(@Nullable ExecutingCraftingJob nextJob) {
        this.job = nextJob;
        exactInventory().setEnabled(nextJob != null && nextJob.exactOrder);
    }

    public cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory exactInventory() {
        return (cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory) inventory;
    }

    public boolean hasJob() {
        return view.hasJob();
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        return view.getFinalJobOutput();
    }

    public long getRemainingJobOutputAmount() {
        return view.getRemainingJobOutputAmount();
    }

    /**
     * @deprecated Use {@link #getProgressView()} so integrations cannot mutate or depend on the tracker type.
     */
    @Deprecated(forRemoval = false)
    public ElapsedTimeTracker getElapsedTimeTracker() {
        return view.getElapsedTimeTracker();
    }

    /** Returns a detached, immutable progress snapshot for external displays and integrations. */
    public ECOCraftingProgressView getProgressView() {
        var base = view.getProgressView();
        var parent = bigOrder.progress();
        if (parent == null) return base;
        return new ECOCraftingProgressView() {
            public float progress() { return base.progress(); }
            public long elapsedTimeNanos() { return base.elapsedTimeNanos(); }
            public long startedWork(AEKeyType type) { return base.startedWork(type); }
            public long completedWork(AEKeyType type) { return base.completedWork(type); }
            public java.util.Optional<cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress> bigOrder() {
                return java.util.Optional.of(parent);
            }
        };
    }

    @Override
    public void recordCompletedCraftingWork(long amount, AEKeyType keyType) {
        Preconditions.checkArgument(amount >= 0, "Completed crafting work must not be negative");
        Preconditions.checkNotNull(keyType, "keyType");
        if (this.job != null && amount != 0) {
            this.job.timeTracker.decrementItems(amount, keyType);
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        persistence.read(data, registries);
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        persistence.write(data, registries);
    }

    void loadJobAttachments(CompoundTag jobData, HolderLookup.Provider registries) {
        if (job != null) jobAttachments.load(jobData, registries, createJobContext(job));
    }

    void writeJobAttachments(CompoundTag jobData, HolderLookup.Provider registries) {
        if (job != null) jobAttachments.save(jobData, registries, createJobContext(job));
    }

    public ICraftingLink getLastLink() {
        if (this.job != null) {
            return this.job.link;
        }
        return null;
    }

    /**
     * 注册一个监听器，当存储物品、等待物品或待处理输出发生变化时接收通知。
     * 仅供菜单使用。务必通过 {@link #removeListener} 来移除。
     */
    public void addListener(Consumer<AEKey> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<AEKey> listener) {
        listeners.remove(listener);
    }

    public long getStored(AEKey template) {
        return view.getStored(template);
    }

    public long getWaitingFor(AEKey template) {
        return view.getWaitingFor(template);
    }

    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        view.getAllWaitingFor(waitingFor);
    }

    public long getPendingOutputs(AEKey template) {
        return view.getPendingOutputs(template);
    }

    public java.util.Map<AEKey, java.math.BigInteger> getExactPendingPreview() {
        if (job != null && job.exactOrder) {
            var amounts = new java.util.HashMap<AEKey, java.math.BigInteger>();
            job.tasks.forEach((pattern, progress) -> pattern.getOutputs().forEach(output ->
                amounts.merge(output.what(), progress.remainingExact().multiply(
                    java.math.BigInteger.valueOf(output.amount())), java.math.BigInteger::add)));
            job.deferredEmitted.forEach((key, amount) -> amounts.merge(key, amount, java.math.BigInteger::add));
            return java.util.Map.copyOf(amounts);
        }
        return bigOrder.exactPendingPreview();
    }

    public java.util.Map<AEKey, java.math.BigInteger> getExactStoredPreview() {
        return job != null && job.exactOrder
            ? ((cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory) inventory).snapshot() : java.util.Map.of();
    }

    public java.util.Map<AEKey, java.math.BigInteger> getExactActivePreview() {
        return job != null && job.exactOrder
            ? ((cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory) job.waitingFor).snapshot() : java.util.Map.of();
    }

    /** Refill physical buffers within one job, without creating or replanning child orders. */
    private void refillExactMaterials() {
        if (!job.exactOrder) return;
        var grid = cpu.getGrid();
        if (grid == null) return;
        var source = cpu.getActionSource();
        if (job.playerId != null) {
            var level = cpu.getLevel();
            var player = level == null ? null : appeng.api.features.IPlayerRegistry.getConnected(
                level.getServer(), job.playerId);
            if (player == null) return;
            source = appeng.api.networking.security.IActionSource.ofPlayer(player, source.machine().orElse(null));
        }
        for (var entry : job.deferredStock.entrySet()) {
            // One network call per key/tick bounds work while exact inventory accumulates multiple windows.
            long wanted = cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(entry.getValue());
            if (wanted <= 0) continue;
            long extracted = grid.getStorageService().getInventory().extract(entry.getKey(), wanted,
                appeng.api.config.Actionable.MODULATE, source);
            if (extracted > 0) {
                inventory.insert(entry.getKey(), extracted, appeng.api.config.Actionable.MODULATE);
                entry.setValue(entry.getValue().subtract(java.math.BigInteger.valueOf(extracted)));
                taskScheduler.recordPhysicalInsert(entry.getKey());
                postChange(entry.getKey());
                markCpuDirty();
            }
        }
        job.deferredStock.values().removeIf(amount -> amount.signum() == 0);
        for (var entry : job.deferredEmitted.entrySet()) {
            ((cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory) job.waitingFor)
                .restore(java.util.Map.of(entry.getKey(), entry.getValue()));
            entry.setValue(java.math.BigInteger.ZERO);
            postChange(entry.getKey());
            markCpuDirty();
        }
        job.deferredEmitted.values().removeIf(amount -> amount.signum() == 0);
    }

    /**
     * 供菜单使用，收集所有类型的存储物品。
     */
    public void getAllItems(KeyCounter out) {
        view.getAllItems(out);
    }

    /** 仅收集此 CPU 实际持有的物品，不包括计划产物和尚未返回的产物。 */
    public void getOwnedItems(KeyCounter out) {
        view.getOwnedItems(out);
    }

    /** {@link #getOwnedItems(KeyCounter)} 对应的无内存分配检查；必须覆盖相同的库存记录。 */
    public boolean hasOwnedItems() {
        return view.hasOwnedItems();
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    /** 供 CPU 菜单和集成使用的稳定诊断接口；返回 null 表示任务仍可执行。 */
    public @Nullable String getPermanentExecutionError() {
        return job == null ? null : job.permanentExecutionError;
    }

    public void setJobSuspended(boolean suspended) {
        if (!suspended && job != null && job.permanentExecutionError != null) {
            return;
        }
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
            taskScheduler.progress(TickHandler.instance().getCurrentTick());
            markCpuDirty();
        }
    }

    void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        var playerId = job.playerId;
        if (playerId == null || job.finalOutput == null) {
            return;
        }

        Level level = cpu.getLevel();
        if (level == null) {
            return;
        }

        var server = level.getServer();
        var connectedPlayer = IPlayerRegistry.getConnected(server, playerId);
        if (connectedPlayer != null) {
            var jobId = job.link.getCraftingID();
            ClientboundPacket message = new CraftingJobStatusPacket(
                    jobId, job.finalOutput.what(), job.finalOutput.amount(), job.remainingAmount, status);
            connectedPlayer.connection.send(message);
        }
    }

    ECOCraftingJobContext createJobContext(ExecutingCraftingJob target) {
        GenericStack output = target.finalOutput;
        long requested = output == null ? 0L : Math.max(0L, output.amount());
        return new ECOCraftingJobContext(
                cpu,
                target.link.getCraftingID(),
                output,
                requested,
                Math.max(0L, target.remainingAmount));
    }

    private ECOCraftingCpuContext createCpuContext(long gameTick) {
        UUID craftingJobId = job == null ? null : job.link.getCraftingID();
        return new ECOCraftingCpuContext(
                cpu,
                craftingJobId,
                cpu.isActive(),
                Math.max(0, cpu.getCoProcessors()),
                gameTick);
    }

    public void markForDeletion() {
        this.markedForDeletion = true;
    }
}
