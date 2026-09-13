package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachment;
import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachmentRegistry;
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
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressSink;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressSnapshot;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressView;
import cn.dancingsnow.neoecoae.api.me.worker.ECOCraftingJobLifecycle;

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
import net.minecraft.resources.ResourceLocation;
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
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOSingleCraftingExecutor;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;

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
    private final ListCraftingInventory inventory = new ListCraftingInventory(ECOCraftingCPULogic.this::postChange);
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    private final Map<ResourceLocation, ECOCraftingJobAttachment> jobAttachments = new LinkedHashMap<>();
    private final Map<ResourceLocation, CompoundTag> unboundJobAttachmentData = new LinkedHashMap<>();
    /** Dynamic final keys that still need to be delivered to the requester/network. */
    private final Map<AEKey, Long> pendingFinalOutputs = new LinkedHashMap<>();
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
        this.dispatchAccounting = new ECOCraftingDispatchAccounting(
                this::postChange, this::markCpuDirty, this::createJobContext,
                ECOCraftingLifecycle::firePatternDispatched);
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
        pendingFinalOutputs.clear();
        initializeJobAttachments();
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
            ECOCraftingLifecycle.fireJobStarted(createJobContext(job));

            return CraftingSubmitResult.successful(linkReq);
        } else {
            ECOCraftingLifecycle.fireJobStarted(createJobContext(job));
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
        var current = job;
        if (current == null) return;
        deliverPendingFinalOutputs(current);
        if (job != current || current.finalOutput == null) return;
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
                // 先保留下一轮增殖所需的回流物料，再交付多余产物。保留启动种子
            PlannerAmount deliverable = PlannerAmount.of(storedFinalOutput)
                .subtract(reserve).max(PlannerAmount.ZERO);
            long amount = deliverable.min(PlannerAmount.of(Math.max(0L, current.remainingAmount))).longValueExact();
            if (amount > 0L) {
                long inserted = deliverFinalOutputToDestination(current, key, amount);
                inventory.extract(key, inserted, Actionable.MODULATE);
                current.remainingAmount = Math.max(0L, current.remainingAmount - inserted);
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
                && tasksDone
                && pendingFinalOutputs.isEmpty();
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
        if (what == null || amount <= 0L || current == null) return 0L;
        if (ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), current.link.getCraftingID())) return 0L;
        if (deliveringFinalOutput && what.matches(current.finalOutput)) return 0L;
        long accepted = current.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (accepted <= 0L) return 0L;
        if (type == Actionable.MODULATE) {
            inventory.insert(what, accepted, Actionable.MODULATE);
            taskScheduler.recordPhysicalInsert(what);
            current.waitingFor.extract(what, accepted, Actionable.MODULATE);
            recordCompletedCraftingWork(accepted, what.getType());
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

    /**
     * Claims an output when the machine's actual key is not necessarily the key reserved by the plan.
     * The waiting ledger is consumed only after the request has been validated; all un-delivered output is retained
     * in this CPU so a requester that accepts only part of the result cannot lose the remainder.
     */
    @Override
    public ECOCraftingOutputClaimResult claimCraftingOutput(ECOCraftingOutputClaimRequest request) {
        long requested = request == null ? 0L : Math.max(0L, request.amount());
        if (request == null || request.craftingJobId() == null || request.expectedKey() == null
                || request.actualKey() == null || request.amount() <= 0L || request.mode() == null) {
            return claimResult(ECOCraftingOutputClaimResult.Status.INVALID_REQUEST, requested, 0L, 0L);
        }

        var current = job;
        if (current == null) {
            return claimResult(ECOCraftingOutputClaimResult.Status.NO_JOB, requested, 0L, 0L);
        }
        if (!request.craftingJobId().equals(current.link.getCraftingID())
                || ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), request.craftingJobId())) {
            return claimResult(ECOCraftingOutputClaimResult.Status.TERMINAL, requested, 0L,
                    Math.max(0L, current.remainingAmount));
        }

        long claim = current.waitingFor.extract(
                request.expectedKey(), request.amount(), Actionable.SIMULATE);
        if (claim <= 0L) {
            return claimResult(ECOCraftingOutputClaimResult.Status.NO_MATCH, requested, 0L,
                    Math.max(0L, current.remainingAmount));
        }

        boolean finalOutputClaim = current.finalOutput != null
                && request.expectedKey().equals(current.finalOutput.what());
        if (request.mode() == Actionable.SIMULATE) {
            OutputRoute route = routeClaimedOutput(
                    current, request.actualKey(), claim, request.mode(), finalOutputClaim);
            long remaining = finalOutputClaim
                    ? Math.max(0L, current.remainingAmount - route.deliveredAmount())
                    : Math.max(0L, current.remainingAmount);
            return new ECOCraftingOutputClaimResult(
                    ECOCraftingOutputClaimResult.Status.ACCEPTED,
                    requested,
                    claim,
                    route.deliveredToRequester(),
                    route.deliveredToNetwork(),
                    route.storedInCpu(),
                    remaining,
                    false);
        }

        long claimed = current.waitingFor.extract(request.expectedKey(), claim, Actionable.MODULATE);
        if (claimed <= 0L) {
            return claimResult(ECOCraftingOutputClaimResult.Status.NO_MATCH, requested, 0L,
                    Math.max(0L, current.remainingAmount));
        }
        // The waiting inventory is server-thread local. A mismatch can only be caused by a re-entrant callback;
        // route no more than the amount actually removed and retain the uncommitted tail in the CPU.
        claim = Math.min(claim, claimed);
        OutputRoute route = routeClaimedOutput(
                current, request.actualKey(), claim, Actionable.MODULATE, finalOutputClaim);

        if (route.storedInCpu() > 0L) {
            inventory.insert(request.actualKey(), route.storedInCpu(), Actionable.MODULATE);
            taskScheduler.recordPhysicalInsert(request.actualKey());
            if (finalOutputClaim && current.finalOutput != null
                    && !request.actualKey().equals(current.finalOutput.what())) {
                pendingFinalOutputs.merge(request.actualKey(), route.storedInCpu(), ECOCraftingCPULogic::saturatingAdd);
            }
        }
        recordCompletedCraftingWork(claim, request.actualKey().getType());
        if (finalOutputClaim) {
            current.remainingAmount = Math.max(0L, current.remainingAmount - route.deliveredAmount());
        }
        if (route.deliveredToRequester() > 0L || route.deliveredToNetwork() > 0L) {
            postChange(request.actualKey());
        }
        markCpuDirty();
        taskScheduler.progress(TickHandler.instance().getCurrentTick());

        boolean finished = finishIfComplete(current);
        long remaining = finished ? 0L : Math.max(0L, current.remainingAmount);
        return new ECOCraftingOutputClaimResult(
                ECOCraftingOutputClaimResult.Status.ACCEPTED,
                requested,
                claim,
                route.deliveredToRequester(),
                route.deliveredToNetwork(),
                route.storedInCpu(),
                remaining,
                finished);
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
            taskEntry.getValue().value -= completedCrafts;
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

    private ECOCraftingOutputClaimResult claimResult(ECOCraftingOutputClaimResult.Status status,
            long requested, long claimed, long remaining) {
        return new ECOCraftingOutputClaimResult(status, requested, claimed, 0L, 0L, 0L, remaining, false);
    }

    private OutputRoute routeClaimedOutput(ExecutingCraftingJob current, AEKey actualKey, long amount,
            Actionable mode, boolean finalOutputClaim) {
        if (!finalOutputClaim) {
            return new OutputRoute(0L, 0L, amount);
        }

        if (!current.link.isStandalone()) {
            try {
                long inserted = current.link.insert(actualKey, amount, mode);
                inserted = clampRoutedAmount(inserted, amount);
                return new OutputRoute(inserted, 0L, amount - inserted);
            } catch (RuntimeException failure) {
                LOGGER.warn("ECO crafting requester rejected a claimed output; retaining it in the CPU", failure);
                return new OutputRoute(0L, 0L, amount);
            }
        }

        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return new OutputRoute(0L, 0L, amount);
        }
        try {
            long inserted = grid.getStorageService().getInventory().insert(
                    actualKey, amount, mode, cpu.getActionSource());
            inserted = clampRoutedAmount(inserted, amount);
            return new OutputRoute(0L, inserted, amount - inserted);
        } catch (RuntimeException failure) {
            LOGGER.warn("ECO crafting network rejected a claimed output; retaining it in the CPU", failure);
            return new OutputRoute(0L, 0L, amount);
        }
    }

    /** Retries dynamic final keys that could not be delivered during their original claim. */
    private void deliverPendingFinalOutputs(ExecutingCraftingJob current) {
        if (pendingFinalOutputs.isEmpty()) return;
        for (var entry : List.copyOf(pendingFinalOutputs.entrySet())) {
            AEKey key = entry.getKey();
            long stored = Math.min(entry.getValue(), inventory.list.get(key));
            if (stored <= 0L) {
                pendingFinalOutputs.remove(key);
                continue;
            }
            long amount = Math.min(stored, Math.max(0L, current.remainingAmount));
            if (amount <= 0L) continue;
            long inserted = deliverFinalOutputToDestination(current, key, amount);
            inventory.extract(key, inserted, Actionable.MODULATE);
            if (inserted > 0L) {
                long remaining = Math.max(0L, stored - inserted);
                if (remaining == 0L) pendingFinalOutputs.remove(key);
                else pendingFinalOutputs.put(key, remaining);
                current.remainingAmount = Math.max(0L, current.remainingAmount - inserted);
                markCpuDirty();
                taskScheduler.progress(TickHandler.instance().getCurrentTick());
            } else {
                taskScheduler.finalDeliveryBlocked(key, amount);
            }
        }
    }

    private long deliverFinalOutputToDestination(ExecutingCraftingJob current, AEKey key, long amount) {
        try {
            deliveringFinalOutput = true;
            long inserted;
            if (current.link.isStandalone()) {
                var grid = cpu.getGrid();
                inserted = grid == null ? 0L : grid.getStorageService().getInventory()
                        .insert(key, amount, Actionable.MODULATE, cpu.getActionSource());
            } else {
                inserted = current.link.insert(key, amount, Actionable.MODULATE);
            }
            return clampRoutedAmount(inserted, amount);
        } catch (RuntimeException failure) {
            LOGGER.error("Final output delivery failed; items remain in the CPU inventory", failure);
            return 0L;
        } finally {
            deliveringFinalOutput = false;
        }
    }

    private static long clampRoutedAmount(long inserted, long offered) {
        return inserted <= 0L ? 0L : Math.min(inserted, offered);
    }

    private boolean finishIfComplete(ExecutingCraftingJob expected) {
        if (job != expected) return true;
        if (expected.remainingAmount > 0L || !expected.waitingFor.list.isEmpty()
                || taskScheduler.hasPendingTasks(expected)
                || !pendingFinalOutputs.isEmpty()
                || (expected.executionRuntime != null && !expected.executionRuntime.isComplete())) {
            return false;
        }
        finishJob(true);
        return true;
    }

    private record OutputRoute(long deliveredToRequester, long deliveredToNetwork, long storedInCpu) {
        long deliveredAmount() {
            return saturatingAdd(deliveredToRequester, deliveredToNetwork);
        }
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
        var finishingJob = job;
        if (finishingJob == null) return;
        var context = createJobContext(finishingJob);
        long remainingAmount = Math.max(0L, finishingJob.remainingAmount);
        long completedAmount = Math.max(0L, context.requestedAmount() - remainingAmount);
        var result = new ECOCraftingJobResult(
                success ? ECOCraftingJobResult.Status.SUCCESS : ECOCraftingJobResult.Status.CANCELLED,
                context.requestedAmount(), completedAmount, remainingAmount);

        ECOCraftingJobLifecycle.finish(cpu.getLevel(), finishingJob.link.getCraftingID(), success);
        if (success) {
            finishingJob.link.markDone();
            ECOCraftingWorkerRecovery.releaseCompletedOutputs(cpu.getGrid(), finishingJob.link.getCraftingID());
        } else {
            finishingJob.link.cancel();
        }

        finishingJob.waitingFor.clear();
        for (var entry : finishingJob.tasks.entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }

        notifyJobOwner(
                finishingJob, success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        clearJobAttachments(result);
        this.job = null;
        taskScheduler.reset();
        this.storeItems();
        pendingFinalOutputs.clear();
        ECOCraftingLifecycle.fireJobFinished(context, result);
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

    /**
     * @deprecated Use {@link #getProgressView()} so integrations cannot mutate or depend on the tracker type.
     */
    @Deprecated(forRemoval = false)
    public ElapsedTimeTracker getElapsedTimeTracker() {
        if (this.job != null) {
            return this.job.timeTracker;
        } else {
            return new ElapsedTimeTracker();
        }
    }

    /** Returns a detached, immutable progress snapshot for external displays and integrations. */
    public ECOCraftingProgressView getProgressView() {
        return this.job == null ? ECOCraftingProgressSnapshot.empty() : this.job.timeTracker.snapshot();
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
        taskScheduler.reset();
        dispatchStrategy.reset();
        jobAttachments.clear();
        unboundJobAttachmentData.clear();
        pendingFinalOutputs.clear();
        energyTransaction.readFromNBT(data);
        this.inventory.readFromNBT(data.getList("inventory", 10), registries);
        if (data.contains("job")) {
            var jobData = data.getCompound("job");
            this.job = new ExecutingCraftingJob(jobData, registries, this::postChange, this);
            loadPendingFinalOutputs(jobData, registries);
            loadJobAttachments(jobData, registries);
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
        } else {
            this.job = null;
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.put("inventory", this.inventory.writeToNBT(registries));
        energyTransaction.writeToNBT(data);
        if (this.job != null) {
            CompoundTag jobData = this.job.writeToNBT(registries);
            writePendingFinalOutputs(jobData, registries);
            writeJobAttachments(jobData, registries);
            data.put("job", jobData);
        } else {
            data.remove("job");
        }
    }

    private void loadPendingFinalOutputs(CompoundTag jobData, HolderLookup.Provider registries) {
        ListTag entries = jobData.getList("pendingFinalOutputs", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            try {
                GenericStack stack = GenericStack.readTag(registries, entries.getCompound(index));
                if (stack != null && stack.amount() > 0L) {
                    pendingFinalOutputs.merge(stack.what(), stack.amount(), ECOCraftingCPULogic::saturatingAdd);
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("Ignoring invalid persisted dynamic final-output delivery entry {}", index, failure);
            }
        }
    }

    private void writePendingFinalOutputs(CompoundTag jobData, HolderLookup.Provider registries) {
        if (pendingFinalOutputs.isEmpty()) {
            jobData.remove("pendingFinalOutputs");
            return;
        }
        ListTag entries = new ListTag();
        for (var entry : pendingFinalOutputs.entrySet()) {
            if (entry.getValue() > 0L) {
                entries.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getValue())));
            }
        }
        if (entries.isEmpty()) jobData.remove("pendingFinalOutputs");
        else jobData.put("pendingFinalOutputs", entries);
    }

    private void initializeJobAttachments() {
        jobAttachments.clear();
        unboundJobAttachmentData.clear();
        if (job == null) return;
        for (var attachment : ECOCraftingJobAttachmentRegistry.createAll(createJobContext(job))) {
            jobAttachments.putIfAbsent(attachment.id(), attachment);
        }
    }

    private void loadJobAttachments(CompoundTag jobData, HolderLookup.Provider registries) {
        initializeJobAttachments();
        CompoundTag persisted = jobData.getCompound("attachments");
        for (var entry : List.copyOf(jobAttachments.entrySet())) {
            String id = entry.getKey().toString();
            if (!persisted.contains(id, Tag.TAG_COMPOUND)) continue;
            try {
                entry.getValue().load(persisted.getCompound(id).copy(), registries);
                // Keep the last known-good payload as a fail-closed fallback if a later save implementation throws.
                unboundJobAttachmentData.put(entry.getKey(), persisted.getCompound(id).copy());
            } catch (RuntimeException failure) {
                LOGGER.error("ECO job attachment {} could not be restored; preserving raw state", id, failure);
                jobAttachments.remove(entry.getKey());
                unboundJobAttachmentData.put(entry.getKey(), persisted.getCompound(id).copy());
            }
        }
        for (String idString : persisted.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(idString);
            if (id == null) {
                LOGGER.warn("Ignoring ECO job attachment with invalid id {}", idString);
            } else if (!jobAttachments.containsKey(id)) {
                unboundJobAttachmentData.put(id, persisted.getCompound(idString).copy());
            }
        }
    }

    private void resolveUnboundJobAttachments(HolderLookup.Provider registries) {
        if (job == null || unboundJobAttachmentData.isEmpty()) return;
        var context = createJobContext(job);
        for (var entry : List.copyOf(unboundJobAttachmentData.entrySet())) {
            if (jobAttachments.containsKey(entry.getKey())) continue;
            var attachment = ECOCraftingJobAttachmentRegistry.create(entry.getKey(), context);
            if (attachment == null) continue;
            try {
                attachment.load(entry.getValue().copy(), registries);
                jobAttachments.put(entry.getKey(), attachment);
                unboundJobAttachmentData.remove(entry.getKey());
            } catch (RuntimeException failure) {
                LOGGER.error("ECO job attachment {} could not be restored", entry.getKey(), failure);
            }
        }
    }

    private void writeJobAttachments(CompoundTag jobData, HolderLookup.Provider registries) {
        resolveUnboundJobAttachments(registries);
        CompoundTag persisted = new CompoundTag();
        for (var entry : jobAttachments.entrySet()) {
            try {
                CompoundTag attachmentData = entry.getValue().save(registries);
                if (attachmentData == null) {
                    throw new IllegalStateException("save returned null");
                }
                persisted.put(entry.getKey().toString(), attachmentData.copy());
                unboundJobAttachmentData.put(entry.getKey(), attachmentData.copy());
            } catch (RuntimeException failure) {
                // Do not silently replace a broken attachment with an empty tag. A previous raw payload can still
                // be carried forward; if none exists, fail the save rather than pretending the state was persisted.
                CompoundTag previous = unboundJobAttachmentData.get(entry.getKey());
                if (previous != null) {
                    persisted.put(entry.getKey().toString(), previous.copy());
                    LOGGER.error("ECO job attachment {} save failed; retaining its previous payload",
                            entry.getKey(), failure);
                } else {
                    throw new IllegalStateException("Unable to persist ECO job attachment " + entry.getKey(), failure);
                }
            }
        }
        for (var entry : unboundJobAttachmentData.entrySet()) {
            String id = entry.getKey().toString();
            if (!persisted.contains(id)) persisted.put(id, entry.getValue().copy());
        }
        if (persisted.isEmpty()) {
            jobData.remove("attachments");
        } else {
            jobData.put("attachments", persisted);
        }
    }

    private void clearJobAttachments(ECOCraftingJobResult result) {
        for (var attachment : List.copyOf(jobAttachments.values())) {
            try {
                attachment.clear(result);
            } catch (RuntimeException failure) {
                LOGGER.warn("ECO job attachment {} failed to clear", attachment.id(), failure);
            }
        }
        jobAttachments.clear();
        unboundJobAttachmentData.clear();
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

    private ECOCraftingJobContext createJobContext(ExecutingCraftingJob target) {
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
