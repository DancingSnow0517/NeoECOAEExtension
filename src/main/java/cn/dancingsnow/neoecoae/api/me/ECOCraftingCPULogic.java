package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.api.me.worker.ECOCraftingJobLifecycle;

import java.util.HashSet;
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
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOSingleCraftingExecutor;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;

public class ECOCraftingCPULogic {
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
    private final ListCraftingInventory inventory = new ListCraftingInventory(ECOCraftingCPULogic.this::postChange);
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    /**
     * 如果 CPU 正在尝试清空库存但无法完成，则为 true。
     */
    @Getter
    private boolean cantStoreItems = false;

    @Getter
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();

    @Getter
    private boolean markedForDeletion = false;

    private boolean deliveringFinalOutput;
    private final ECOCraftingDispatchStrategy dispatchStrategy = new ECOCraftingDispatchStrategy();
    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting dispatchAccounting;
    private final ECOCraftingFastPathDispatcher fastPathDispatcher;
    private final ECOCraftingProviderDispatcher providerDispatcher;
    private final ECOCraftingTaskScheduler taskScheduler;

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
        this.energyTransaction = new ECOCraftingEnergyTransaction(
                this::markCpuDirty, TickHandler.instance()::getCurrentTick);
        this.dispatchAccounting = new ECOCraftingDispatchAccounting(this::postChange, this::markCpuDirty);
        this.fastPathDispatcher = new ECOCraftingFastPathDispatcher(this, energyTransaction, dispatchAccounting);
        this.providerDispatcher = new ECOCraftingProviderDispatcher(
                fastPathDispatcher, energyTransaction, dispatchAccounting);
        this.taskScheduler = new ECOCraftingTaskScheduler(providerDispatcher);
    }

    public ICraftingSubmitResult trySubmitJob(
            IGrid grid, ICraftingPlan plan, IActionSource src, @Nullable ICraftingRequester requester) {
        if (this.job != null)
            return CraftingSubmitResult.CPU_BUSY;
        if (!cpu.isActive())
            return CraftingSubmitResult.CPU_OFFLINE;
        if (cpu.getAvailableStorage() < plan.bytes())
            return CraftingSubmitResult.CPU_TOO_SMALL;

        if (!inventory.list.isEmpty())
            AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");

        var executionPlan = ECOPlanningResultRegistry.resolveExecutionPlan(plan);

        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null) {
            return CraftingSubmitResult.missingIngredient(missingIngredient);
        }

        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new ExecutingCraftingJob(plan, executionPlan, this::postChange, linkCpu, playerId);
        taskScheduler.resetDispatchState();
        taskScheduler.bindDiagnostics(craftId, TickHandler.instance().getCurrentTick());
        // 立即发布计划产物，使 AE2 能在首次机器事件发生前显示并取消新任务。
        var initialStatusItems = new KeyCounter();
        getAllItems(initialStatusItems);
        for (var entry : initialStatusItems) postChange(entry.getKey());

        markCpuDirty();

        notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);

        if (requester != null) {
            var linkReq = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, false, true), requester);

            var craftingService = (CraftingService) grid.getCraftingService();
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkReq);

            return CraftingSubmitResult.successful(linkReq);
        } else {
            return CraftingSubmitResult.successful(null);
        }
    }

    public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {
        if (job != null && ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), job.link.getCraftingID())) {
            cancel();
            return;
        }
        if (!cpu.isActive()) {
            return;
        }
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
        var current = job;
        if (current == null) return;
        AEKey key = current.finalOutput.what();
        long storedFinalOutput = inventory.list.get(key);
        if (storedFinalOutput > 0L) {
            PlannerAmount reserve = PlannerAmount.ZERO;
            for (var task : current.tasks.entrySet()) {
                reserve = reserve.add(ECOPhaseScheduler.growingPatternFeedbackReserveExact(
                    task.getKey(), task.getValue().value, key));
            }
            if (current.executionRuntime != null) {
                reserve = reserve.max(PlannerAmount.of(current.executionRuntime.reservedInputAmount(key)));
            }
                // 先保留下一轮增殖所需的回流物料，再交付多余产物。
            PlannerAmount deliverable = PlannerAmount.of(storedFinalOutput)
                .subtract(reserve).max(PlannerAmount.ZERO);
            long amount = deliverable.min(PlannerAmount.of(Math.max(0L, current.remainingAmount))).longValueExact();
            if (amount > 0L) {
                long inserted;
                try {
                    deliveringFinalOutput = true;
                    if (current.link.isStandalone()) {
                        var grid = cpu.getGrid();
                        inserted = grid == null ? 0L : grid.getStorageService().getInventory()
                            .insert(key, amount, Actionable.MODULATE, cpu.getActionSource());
                    } else {
                        inserted = current.link.insert(key, amount, Actionable.MODULATE);
                    }
                } catch (RuntimeException e) {
                    LOGGER.error("Final output delivery failed; items remain in the CPU inventory", e);
                    return;
                } finally {
                    deliveringFinalOutput = false;
                }
                inventory.extract(key, inserted, Actionable.MODULATE);
                current.remainingAmount -= inserted;
                markCpuDirty();
                if (inserted > 0L) {
                    taskScheduler.progress(TickHandler.instance().getCurrentTick());
                } else {
                    taskScheduler.finalDeliveryBlocked(key, amount);
                }
            }
        }
        boolean tasksDone = !taskScheduler.hasPendingTasks(current);
        boolean physicallyComplete = current.remainingAmount <= 0L
                && current.waitingFor.list.isEmpty()
                && tasksDone;
        if (physicallyComplete) {
            if (current.executionRuntime != null && !current.executionRuntime.isComplete()) {
                LOGGER.warn("ECO crafting job {} reached terminal crafting state "
                                + "but execution runtime is incomplete; forcing finalization",
                        current.link.getCraftingID());
            }
            finishJob(true);
        }
    }

    static int calculateOperationLimit(int coProcessors, int configuredLimit) {
        long baseLimit = (long) Math.max(0, coProcessors) + 1L;
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
        if (what == null || amount <= 0L || current == null) return 0L;
        if (ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), current.link.getCraftingID())) return 0L;
        if (deliveringFinalOutput && what.matches(current.finalOutput)) return 0L;
        long accepted = current.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (accepted <= 0L) return 0L;
        if (type == Actionable.MODULATE) {
            inventory.insert(what, accepted, Actionable.MODULATE);
            taskScheduler.recordPhysicalInsert(what);
            current.waitingFor.extract(what, accepted, Actionable.MODULATE);
            current.timeTracker.decrementItems(accepted, what.getType());
            markCpuDirty();
            taskScheduler.progress(TickHandler.instance().getCurrentTick());
        }
        return accepted;
    }

    /**
     * 仅在此 CPU 仍持有所指定的合成任务时接收工作单元的产物，并将多余产物保留在本地。
     *
     * <p>工作单元的产物携带任务标识，但 AE2 的旧版 {@code insertIntoCpus} 接口不携带该信息。
     * 在 CPU 边界保留此检查，可防止产物被分配给恰好也在等待相同资源键的其他 CPU。</p>
     */
    public long insertForJob(UUID craftingJobId, AEKey what, long amount, Actionable type) {
        if (what == null || amount <= 0L || craftingJobId == null || job == null
                || !craftingJobId.equals(job.link.getCraftingID())
                || ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), craftingJobId)) {
            return 0L;
        }
        long accepted = insert(what, amount, type);
        if (accepted < 0L || accepted > amount) {
            throw new IllegalStateException("Invalid CPU insertion amount: " + accepted + " for " + amount);
        }
        // 定向交付给该任务的多余产物归此 CPU 所有，但不得扣减无关的等待条目。
        if (type == Actionable.MODULATE && accepted < amount) {
            inventory.insert(what, amount - accepted, Actionable.MODULATE);
            taskScheduler.recordPhysicalInsert(what);
            markCpuDirty();
        }
        return amount;
    }

    public boolean hasCraftingJob(UUID craftingJobId) {
        return craftingJobId != null && job != null && craftingJobId.equals(job.link.getCraftingID());
    }

    /**
     * 完成当前合成任务。
     *
     * @param success 任务完成则为 true，取消则为 false。
     */
    private void finishJob(boolean success) {
        ECOCraftingJobLifecycle.finish(cpu.getLevel(), job.link.getCraftingID(), success);
        if (success) {
            job.link.markDone();
            ECOCraftingWorkerRecovery.releaseCompletedOutputs(cpu.getGrid(), job.link.getCraftingID());
        } else {
            job.link.cancel();
        }

        job.waitingFor.clear();
        for (var entry : job.tasks.entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }

        notifyJobOwner(
                job, success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        this.job = null;
        taskScheduler.reset();
        this.storeItems();
    }

    /**
     * 取消当前合成任务。
     */
    public void cancel() {
        if (job == null)
            return;

        UUID craftingJobId = job.link.getCraftingID();
        finishJob(false);
        ECOCraftingWorkerRecovery.recoverTerminatedInputs(cpu.getLevel(), craftingJobId);
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

        for (var entry : this.inventory.list) {
            this.postChange(entry.getKey());
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE,
                    cpu.getActionSource());

            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();

        markCpuDirty();
    }

    private void postChange(@Nullable AEKey what) {
        taskScheduler.invalidateInputTemplates(what);
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        notifyListeners(what);
    }

    private void notifyListeners(@Nullable AEKey what) {
        for (var listener : listeners) listener.accept(what);
    }

    private void markCpuDirty() {
        cpu.markDirty();
    }

    public boolean hasJob() {
        return this.job != null;
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        return this.job != null ? this.job.finalOutput : null;
    }

    public long getRemainingJobOutputAmount() {
        return this.job != null ? this.job.remainingAmount : 0L;
    }

    public ElapsedTimeTracker getElapsedTimeTracker() {
        if (this.job != null) {
            return this.job.timeTracker;
        } else {
            return new ElapsedTimeTracker();
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        taskScheduler.reset();
        dispatchStrategy.reset();
        energyTransaction.readFromNBT(data);
        this.inventory.readFromNBT(data.getList("inventory", 10), registries);
        if (data.contains("job")) {
            var jobData = data.getCompound("job");
            this.job = new ExecutingCraftingJob(jobData, registries, this::postChange, this);
            IGrid grid = cpu.getGrid();
            if (grid != null) {
                // 仅在整个任务成功解码后发布恢复的链接。恢复失败的任务会继续隔离在多线程核心中，
                // 不得在合成服务中留下孤立链接。
                ((CraftingService) grid.getCraftingService()).addLink(this.job.link);
            }
            // 将旧版独立最终产物缓冲区中保存的实际物品迁移一次。
            long buffered = jobData.getLong("bufferedFinalOutput");
            if (buffered > 0L && job.finalOutput != null) {
                inventory.insert(job.finalOutput.what(), buffered, Actionable.MODULATE);
            }
            if (this.job.finalOutput == null) {
                finishJob(false);
            }
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.put("inventory", this.inventory.writeToNBT(registries));
        energyTransaction.writeToNBT(data);
        if (this.job != null) {
            data.put("job", this.job.writeToNBT(registries));
        } else {
            data.remove("job");
        }
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
        return this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    public long getWaitingFor(AEKey template) {
        if (this.job != null) {
            return this.job.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
        }
        return 0;
    }

    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        if (this.job != null) {
            for (var entry : this.job.waitingFor.list) {
                waitingFor.add(entry.getKey());
            }
        }
    }

    public long getPendingOutputs(AEKey template) {
        long count = 0;
        if (this.job != null) {
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    if (template.matches(output)) {
                        count = saturatingAdd(count, saturatingMultiply(output.amount(), t.getValue().value));
                    }
                }
            }
        }
        return count;
    }

    /**
     * 供菜单使用，收集所有类型的存储物品。
     */
    public void getAllItems(KeyCounter out) {
        addAllSaturating(out, this.inventory.list);
        if (this.job != null) {
            addAllSaturating(out, job.waitingFor.list);
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    long amount = saturatingMultiply(output.amount(), t.getValue().value);
                    out.set(output.what(), saturatingAdd(out.get(output.what()), amount));
                }
            }
        }
    }

    /** 仅收集此 CPU 实际持有的物品，不包括计划产物和尚未返回的产物。 */
    public void getOwnedItems(KeyCounter out) {
        out.addAll(this.inventory.list);
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static long saturatingAdd(long left, long right) {
        if (left <= 0L) return Math.max(0L, right);
        if (right <= 0L) return left;
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static void addAllSaturating(KeyCounter target, KeyCounter source) {
        for (var entry : source) {
            target.set(entry.getKey(), saturatingAdd(target.get(entry.getKey()), entry.getLongValue()));
        }
    }

    /** {@link #getOwnedItems(KeyCounter)} 对应的无内存分配检查；必须覆盖相同的库存记录。 */
    public boolean hasOwnedItems() {
        return !this.inventory.list.isEmpty();
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    /** 供 CPU 菜单和集成使用的稳定诊断接口；返回 null 表示任务仍可执行。 */
    public @Nullable String getPermanentExecutionError() {
        return null;
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
            taskScheduler.progress(TickHandler.instance().getCurrentTick());
            markCpuDirty();
        }
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
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

    public void markForDeletion() {
        this.markedForDeletion = true;
    }
}
