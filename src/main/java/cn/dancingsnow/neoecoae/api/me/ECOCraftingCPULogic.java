package cn.dancingsnow.neoecoae.api.me;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Objects;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
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
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedCraft;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.RuntimeExecutionState;
import cn.dancingsnow.neoecoae.compat.dataenergistics.ECODataEnergisticsCountedBridge;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOThunderboltBatchBridge;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOExternalBatchContracts;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessDynamicOutputBridge;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingCPULogic {
    private final Map<cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity.PatternIdentity,
        List<ICraftingProvider>> providerTopologyCache = new HashMap<>();
    /**
     * Ordinary dispatch is deliberately policy-driven. The default fills currently visible provider capacity;
     * adaptive policies can be installed without touching extraction, rollback or runtime accounting.
     */
    private volatile ECOCraftingDispatchStrategy ordinaryDispatchStrategy = ECOParallelDispatchStrategy.INSTANCE;
    private final Map<BatchProbeKey, BatchCapacityProbeState> batchProbeStates = new HashMap<>();
    enum BatchMode { FINITE, VIRTUAL }

    /** Common batch dispatch entry point; mode-specific implementations retain their capability checks. */
    private DispatchResult dispatchBatch(ExecutingCraftingJob job, ECOExtractedPatternExecution execution,
            KeyCounter[] container, List<ICraftingProvider> providers, IEnergyService energy, double power,
            long remaining, BatchMode mode, boolean firstBatch) {
        long accepted = mode == BatchMode.VIRTUAL
            ? tryPushVerifiedVirtualBatch(job, execution, container, providers, remaining)
            : tryPushVerifiedFastPathBatch(job, execution, container, providers, energy, power, remaining, firstBatch);
        if (accepted > 0) return new DispatchResult.Accepted(accepted);
        if (accepted < 0) return new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED);
        return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
    }
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

    private boolean batchingStatusChanges = false;
    private final Set<AEKey> fallbackStatusChanges = new HashSet<>();
    private RuntimeExecutionState dirtyResourceState;
    private int[] dirtyResourceQueue = new int[0];
    private boolean[] dirtyResourcePresent = new boolean[0];
    private int dirtyResourceCount;
    private boolean batchedAnyStatusChange = false;
    private boolean batchedFullStatusChange = false;
    private boolean cpuDirtyRequested = false;
    private boolean deliveringBufferedFinalOutput = false;
    private long lastFinalOutputDeliveryFailureLogTick = Long.MIN_VALUE;
    private static final long BATCH_REJECTION_LOG_INTERVAL_TICKS = 100L;
    private static final long STALLED_DISPATCH_LOG_INTERVAL_TICKS = 100L;
    /** 4 列表格分隔线，长度与单元格总宽严格对齐（133 个 '-'）。 */
    private static final String DISPATCH_TABLE_SEPARATOR = "-".repeat(133);
    private long lastBatchRejectionLogTick = Long.MIN_VALUE;
    private long lastStalledDispatchLogTick = Long.MIN_VALUE;
    private long batchProbeBudgetTick = Long.MIN_VALUE;
    private int batchProbesUsedThisTick;
    private final Set<Object> batchProbedTasksThisTick = new HashSet<>();
    private int taskDispatchCursor;


    private static final class BatchProbeKey {
        private final Object scope;
        private final Object pattern;
        private final int hash;

        private BatchProbeKey(Object scope, Object pattern) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.pattern = Objects.requireNonNull(pattern, "pattern");
            this.hash = 31 * System.identityHashCode(scope) + pattern.hashCode();
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof BatchProbeKey key && scope == key.scope && pattern.equals(key.pattern);
        }
    }

    private record ProbeDispatchOutcome(DispatchResult result, int probeCount) {}

    private record TaskProbeKey(ExecutingCraftingJob job, Object taskIdentity) {
        @Override public boolean equals(Object other) {
            return other instanceof TaskProbeKey key && job == key.job && taskIdentity.equals(key.taskIdentity);
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(job) + taskIdentity.hashCode();
        }
    }

    private static final class DispatchDiagnostics {
        int runnableTasks;
        int phaseRejectedTasks;
        int tasksWithoutProviders;
        int tasksWithBusyProviders;
        int tasksMissingInputs;
        int energyBlockedProviders;
        int providerRejections;
        @Nullable IPatternDetails firstMissingInputPattern;
    }

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
    }

    public ECOCraftingDispatchStrategy getOrdinaryDispatchStrategy() {
        return ordinaryDispatchStrategy;
    }

    /** Installs the ordinary-path scheduling policy used by subsequent engine passes. */
    public void setOrdinaryDispatchStrategy(ECOCraftingDispatchStrategy strategy) {
        this.ordinaryDispatchStrategy = java.util.Objects.requireNonNull(strategy, "strategy");
    }

    public ICraftingSubmitResult trySubmitJob(
            IGrid grid, ICraftingPlan plan, IActionSource src, @Nullable ICraftingRequester requester) {
        // 已有任务在运行。
        if (this.job != null)
            return CraftingSubmitResult.CPU_BUSY;
        // 检查节点是否活跃。
        if (!cpu.isActive())
            return CraftingSubmitResult.CPU_OFFLINE;
        // 检查存储字节数。
        if (cpu.getAvailableStorage() < plan.bytes())
            return CraftingSubmitResult.CPU_TOO_SMALL;

        if (!inventory.list.isEmpty())
            AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");

        // 尝试提取所需物品。
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null)
            return CraftingSubmitResult.missingIngredient(missingIngredient);

        // 设置 CPU 链接与任务。
        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new ExecutingCraftingJob(plan, this::postChange, linkCpu, playerId);
        initializeRuntimeOwnershipFromPhysicalState(this.job, false);
        initializeDirtyResourceQueue(this.job.runtimeExecutionState());
        providerTopologyCache.clear();
        resetBatchProbeBudgetForCurrentTick();
        this.lastStalledDispatchLogTick = Long.MIN_VALUE;
        // A newly submitted job already has pending pattern outputs even when its initial inventory is empty.
        // Publish those keys now; otherwise the status table stays empty until the first machine event, and AE2
        // disables the cancel button because it derives that button from the visible status entries.
        var initialStatusItems = new KeyCounter();
        getAllItems(initialStatusItems);
        for (var entry : initialStatusItems) postChange(entry.getKey());

        // 合成监视器暂不支持
        // cpu.updateOutput(plan.finalOutput());
        markCpuDirty();

        // TODO: 发送监视器差异？

        notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);

        // 非独立任务需要为请求者创建另一个链接，两个链接都需要提交到缓存。
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
        // 未激活时不 tick。
        if (!cpu.isActive()) {
            if (job != null) {
                logStalledDispatch(job, job.activePhase(), 0, new DispatchDiagnostics(), "cpu-inactive");
            }
            return;
        }
        cantStoreItems = false;
        // 无任务时只需尝试清空物品。
        if (this.job == null) {
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
        // 检查任务是否已被取消。
        if (job.link.isCanceled()) {
            cancel();
            return;
        }

        retryBufferedFinalOutput();
        if (job == null) {
            return;
        }

        // Missing metadata for a planner-confirmed cycle is permanent for this job. Do not repeatedly enter the
        // dispatch path on every tick; retain the job for inspection/cancellation.
        if (job.hasPermanentExecutionError()) {
            return;
        }

        // 暂停时不调度更多工作
        if (job.suspended) {
            logStalledDispatch(job, job.activePhase(), 0, new DispatchDiagnostics(), "job-suspended");
            return;
        }

        Level level = cpu.getLevel();
        if (level == null) {
            logStalledDispatch(job, job.activePhase(), 0, new DispatchDiagnostics(), "level-unavailable");
            return;
        }

        var remainingOperations = getOperationLimit();

        if (remainingOperations > 0) {
            // One engine pass is one tick. The pass snapshots eligible task ids; the ordinary dispatch strategy
            // decides how many one-craft provider calls may fill the currently available parallel lanes.
            executeCrafting(remainingOperations, cc, eg, level);
            // Dispatch normally flushed this projection together with status changes. This covers pre-batch exits.
            if (job != null) job.flushRuntimeTick();
        } else {
            logStalledDispatch(job, job.activePhase(), remainingOperations, new DispatchDiagnostics(),
                "operation-limit-zero");
        }
    }

    private void retryBufferedFinalOutput() {
        ExecutingCraftingJob currentJob = job;
        if (currentJob == null) {
            return;
        }
        drainBufferedFinalOutput(currentJob);
    }

    /** Releases final-output units retained as cycle feedback once the current gate no longer needs them. */
    private void releaseSurplusFinalOutput(ExecutingCraftingJob currentJob) {
        if (currentJob.finalOutput == null || currentJob.finalOutput.what() == null) return;
        AEKey key = currentJob.finalOutput.what();
        long stored = inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
        long reserve = currentJob.finalOutputFeedbackReserve(key);
        long surplus = Math.max(0L, stored - Math.min(stored, reserve));
        if (surplus <= 0L && stored > 0L) {
            LOGGER.debug("[ECO-CYCLE-RESOURCE] blocked external consumption cycle output item={} stored={} reserve={} reason=kept_cycle_reserve",
                key, stored, reserve);
        }
        if (surplus <= 0L) return;
        long transferable = currentJob.bufferedFinalOutput.accept(surplus, Actionable.SIMULATE);
        if (transferable <= 0L) return;
        long extracted = inventory.extract(key, transferable, Actionable.MODULATE);
        long accepted = currentJob.bufferedFinalOutput.accept(extracted, Actionable.MODULATE);
        if (accepted != extracted) throw new IllegalStateException("Final-output buffer rejected CPU-owned surplus");
        postChange(key);
        markCpuDirty();
        drainBufferedFinalOutput(currentJob);
    }

    private void drainBufferedFinalOutput(ExecutingCraftingJob currentJob) {
        if (job != currentJob || currentJob.finalOutput == null) {
            return;
        }
        if (isFinalOutputSatisfied(currentJob.remainingAmount)) {
            finishJob(true);
            return;
        }
        AEKey key = currentJob.finalOutput.what();
        long buffered = currentJob.bufferedFinalOutput.amount();
        if (buffered <= 0L) {
            return;
        }
        long deliverable = Math.min(buffered, Math.max(0L, currentJob.remainingAmount));
        if (deliverable <= 0L) {
            return;
        }
        final long accepted;
        try {
            deliveringBufferedFinalOutput = true;
            accepted = validateInsertionAmount(
                deliverFinalOutput(key, deliverable, Actionable.MODULATE),
                deliverable,
                "final-output requester"
            );
        } catch (RuntimeException e) {
            logFinalOutputDeliveryFailure(e);
            return;
        } finally {
            deliveringBufferedFinalOutput = false;
        }
        if (accepted <= 0L) {
            return;
        }
        if (job != currentJob) {
            // The target already accepted these items. Never throw back into the Worker after that ownership
            // transfer, since a retry would duplicate the physical output.
            LOGGER.error("Crafting job changed after accepting {} buffered final-output items", accepted);
            return;
        }
        currentJob.bufferedFinalOutput.removeDelivered(accepted);
        if (currentJob.runtimeExecutionState() != null) {
            currentJob.runtimeExecutionState().releaseExternal(key, accepted);
        }
        currentJob.remainingAmount = Math.max(0L, currentJob.remainingAmount - accepted);
        postChange(key);
        markCpuDirty();
        if (isFinalOutputSatisfied(currentJob.remainingAmount)) {
            finishJob(true);
        }
    }

    private boolean isFinalOutputSatisfied(long remainingAmount) {
        // The buffer may still own recipe-rounding surplus. finishJob preserves that surplus and stores it normally.
        return remainingAmount <= 0L;
    }

    private int getOperationLimit() {
        return calculateOperationLimit(cpu.getCoProcessors(), NEConfig.ecoCpuPushTickLimit);
    }

    static int calculateOperationLimit(int coProcessors, int configuredLimit) {
        long baseLimit = (long) Math.max(0, coProcessors) + 1L;
        long safeConfiguredLimit = Math.min(
            (long) NEConfig.MAX_ECO_CPU_PUSH_TICK_LIMIT,
            Math.max(0L, configuredLimit)
        );
        return (int) Math.min(Integer.MAX_VALUE, Math.min(baseLimit, safeConfiguredLimit));
    }

    /**
     * 尝试将 pattern 推送到可用接口中，即执行实际的合成操作。
     *
     * @return 成功推送的 pattern 数量。
     */
    public int executeCrafting(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        var job = this.job;
        if (job == null)
            return 0;
        if (job.hasPermanentExecutionError()) {
            return 0;
        }
        // Materialize the shared runtime cursor once the immutable execution metadata is available.
        job.runtimeExecutionState();
        job.advanceCompletedPhases();
        releaseSurplusFinalOutput(job);
        if (this.job != job) return 0;
        var activePhase = job.activePhase();
        boolean componentScheduled = job.phased();
        var diagnostics = new DispatchDiagnostics();
        if (componentScheduled && activePhase == null) {
            logStalledDispatch(job, activePhase, maxPatterns, diagnostics, "no-active-phase");
            return 0;
        }

        var pushedPatterns = 0;
        // Provider membership is a topology property, but AE2 exposes no stable generation here. Scope the cache to
        // one engine pass so grid changes can never leave stale providers attached to a long-lived job.
        providerTopologyCache.clear();
        resetBatchProbeBudgetForCurrentTick();

        beginStatusChangeBatch(job);
        try {
            List<ExecutingCraftingJob.DispatchTask> readyTasks = job.eligibleDispatchTasks();
            int fairQuantum = readyTasks.isEmpty() ? 0 : Math.max(1, maxPatterns / readyTasks.size());
            List<ExecutingCraftingJob.DispatchTask> eligibleTasks = new ArrayList<>(fairTaskOrder(readyTasks));
            Set<ExecutingCraftingJob.DispatchTask> finiteFastPathStartedTasks =
                Collections.newSetFromMap(new IdentityHashMap<>());
            int taskIndex = 0;
            taskLoop: while (taskIndex < eligibleTasks.size()) {
                var task = eligibleTasks.get(taskIndex++);
                if (task.progress().value <= 0) {
                    continue;
                }

                var details = task.pattern();
                diagnostics.runnableTasks++;
                if (job.runtimeExecutionState == null && activePhase != null && !cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler
                        .canDispatch(activePhase, job.cycleWitnessIndex, details)) {
                    diagnostics.phaseRejectedTasks++;
                    continue;
                }
                // Topology is collected once per task: which providers advertise this pattern at all cannot
                // change while we iterate. Live capacity - busy state, free thread slots, coolant, energy - is
                // deliberately NOT part of this list and is re-measured on every attempt below.
                List<ICraftingProvider> candidateProviders = collectAvailableProviders(craftingService, details);
                if (candidateProviders.isEmpty()) {
                    diagnostics.tasksWithoutProviders++;
                    continue;
                }
                if (task.progress().value > 0 && pushedPatterns < maxPatterns) {
                    if (!hasAvailableProvider(candidateProviders)) {
                        diagnostics.tasksWithBusyProviders++;
                        continue taskLoop;
                    }

                    var expectedOutputs = new KeyCounter();
                    var expectedContainerItems = new KeyCounter();
                    @Nullable
                    var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                            details, inventory, level, expectedOutputs, expectedContainerItems);
                    if (craftingContainer == null) {
                        diagnostics.tasksMissingInputs++;
                        if (diagnostics.firstMissingInputPattern == null) {
                            diagnostics.firstMissingInputPattern = details;
                        }
                        continue taskLoop;
                    }

                    var extractedCraft = new ECOExtractedCraft(craftingContainer, expectedOutputs,
                        expectedContainerItems, CraftingCpuHelper.calculatePatternPower(craftingContainer));
                    long batchResult = 0L;
                    long dispatchLimit = job.dispatchLimit(task);
                    ECOExtractedPatternExecution execution = null;
                    boolean virtualBatchAccepted = false;
                    boolean finiteBatchAccepted = false;
                    DispatchResult batchDispatch = new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
                    if (hasFastPathProvider(candidateProviders)
                            || hasExternalCountedProvider(candidateProviders)
                            || hasBatchProbeProvider(candidateProviders)) {
                        execution = ECOExtractedPatternExecution.create(details, craftingContainer,
                            expectedOutputs, expectedContainerItems, level);
                        if (hasFastPathProvider(candidateProviders)) {
                            batchDispatch = dispatchBatch(job, execution, craftingContainer, candidateProviders,
                                energyService, extractedCraft.patternPower(), dispatchLimit, BatchMode.VIRTUAL, true);
                            virtualBatchAccepted = batchDispatch instanceof DispatchResult.Accepted;
                            if (batchDispatch instanceof DispatchResult.Waiting) {
                                batchDispatch = dispatchBatch(job, execution, craftingContainer, candidateProviders,
                                    energyService, extractedCraft.patternPower(), dispatchLimit, BatchMode.FINITE,
                                    !finiteFastPathStartedTasks.contains(task));
                                finiteBatchAccepted = batchDispatch instanceof DispatchResult.Accepted;
                            }
                        }
                        if (batchDispatch instanceof DispatchResult.Waiting
                                && hasExternalCountedProvider(candidateProviders)) {
                            batchDispatch = tryPushExternalCountedBatch(job, execution, craftingContainer,
                                candidateProviders, energyService, extractedCraft.patternPower(), dispatchLimit,
                                task.progress().value, Math.max(0, maxPatterns - pushedPatterns), level);
                        }
                        int remainingBatchProbeBudget = Math.max(0,
                            ECOBatchProbeScheduler.MAX_BATCH_PROBES_PER_CPU_PER_TICK - batchProbesUsedThisTick);
                        if (batchDispatch instanceof DispatchResult.Waiting && remainingBatchProbeBudget > 0) {
                            Object taskIdentity = task.taskId() >= 0
                                ? Integer.valueOf(task.taskId())
                                : patternIdentityOrObject(details);
                            Object taskProbeIdentity = new TaskProbeKey(job, taskIdentity);
                            if (batchProbedTasksThisTick.add(taskProbeIdentity)) {
                                ProbeDispatchOutcome probe = tryPushProbedBatch(job, execution, craftingContainer,
                                    candidateProviders, energyService, extractedCraft.patternPower(), dispatchLimit,
                                    task.progress().value, remainingBatchProbeBudget, task.taskId());
                                batchProbesUsedThisTick += probe.probeCount();
                                batchDispatch = probe.result();
                            }
                        }
                    }
                    if (batchDispatch instanceof DispatchResult.Accepted accepted) batchResult = accepted.count();
                    else if (batchDispatch instanceof DispatchResult.Rejected) batchResult = -1L;
                    if (batchResult > 0) {
                        // One provider dispatch consumes one CPU scheduling operation regardless of how many
                        // crafts the F-series host accepted in that batch.
                        pushedPatterns++;
                        if (this.job != job) {
                            break taskLoop;
                        }
                        eligibleTasks.addAll(job.applyDispatchResultAndGetNewlyReady(task, batchDispatch));
                        if (task.progress().value <= 0) {
                            continue taskLoop;
                        }
                        if (pushedPatterns == maxPatterns) {
                            break taskLoop;
                        }
                        // A finite FastPath batch consumes one CPU operation, but it does not consume the
                        // whole task. Put the task back at the tail so the next pass can acquire another live FX
                        // lane. With one ready task this fills every idle worker; with multiple tasks this is a
                        // real round-robin queue instead of the old fixed two-copy task list. Virtual dispatch is
                        // deliberately excluded: its existing one-lane, long-count semantics stay unchanged.
                        if (finiteBatchAccepted) {
                            finiteFastPathStartedTasks.add(task);
                        }
                        if (!virtualBatchAccepted && this.job == job) {
                            eligibleTasks.add(task);
                        }
                        continue;
                    } else if (batchResult < 0) {
                        continue taskLoop;
                    }

                    // Keep the ordinary ICraftingProvider invocation in executeCrafting. External integrations
                    // (notably useless_mod's dynamic-output bridge) wrap this exact call site by descriptor.
                    // The strategy only chooses order and attempt budget; this loop retains the transaction.
                    List<ICraftingProvider> ordinaryProviders = ordinaryProviders(candidateProviders);
                    var strategyContext = new ECOCraftingDispatchStrategy.DispatchContext(
                        details,
                        task.progress().value,
                        Math.min(fairQuantum, Math.max(0, maxPatterns - pushedPatterns)),
                        ordinaryProviders,
                        estimateOrdinaryDispatchSlots(ordinaryProviders, Math.max(0, maxPatterns - pushedPatterns))
                    );
                    ECOCraftingDispatchStrategy.DispatchDecision strategyDecision;
                    try {
                        strategyDecision = ordinaryDispatchStrategy.choose(strategyContext);
                        if (strategyDecision == null) {
                            throw new IllegalStateException("ordinary dispatch strategy returned null");
                        }
                    } catch (RuntimeException strategyFailure) {
                        // A policy failure must not strand a crafting job. Fall back to the conservative built-in
                        // policy while retaining the failure in the log for the policy implementation author.
                        LOGGER.error("Ordinary crafting dispatch strategy failed; using parallel fallback", strategyFailure);
                        strategyDecision = ECOParallelDispatchStrategy.INSTANCE.choose(strategyContext);
                    }
                    long runtimeLimit = job.dispatchLimit(task);
                    int ordinaryAttemptLimit = (int) Math.min(
                        Math.min((long) strategyDecision.maxAttempts(), strategyContext.dispatchBudget()),
                        Math.min(task.progress().value, runtimeLimit));
                    List<ICraftingProvider> dispatchProviders = strategyDecision.providers();
                    if (ordinaryAttemptLimit <= 0 || dispatchProviders.isEmpty()) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                        continue taskLoop;
                    }

                    int ordinaryPushedBefore = pushedPatterns;
                    ordinaryDispatch: for (int attempt = 0; attempt < ordinaryAttemptLimit; attempt++) {
                        if (task.progress().value <= 0 || pushedPatterns >= maxPatterns) {
                            break;
                        }

                        KeyCounter attemptOutputs;
                        KeyCounter attemptContainerItems;
                        @Nullable KeyCounter[] attemptContainer;
                        @Nullable ECOExtractedPatternExecution attemptExecution = null;
                        double attemptPower;
                        if (attempt == 0) {
                            // The first craft was already extracted for the shared fast-path offer search. Reuse
                            // that exact container; extracting it a second time would leak one craft on fallback.
                            attemptOutputs = extractedCraft.expectedOutputs();
                            attemptContainerItems = extractedCraft.expectedContainerItems();
                            attemptContainer = extractedCraft.craftingContainer();
                            attemptExecution = execution;
                            attemptPower = extractedCraft.patternPower();
                        } else {
                            attemptOutputs = new KeyCounter();
                            attemptContainerItems = new KeyCounter();
                            attemptContainer = CraftingCpuHelper.extractPatternInputs(
                                details, inventory, level, attemptOutputs, attemptContainerItems);
                            attemptPower = attemptContainer == null
                                ? 0.0D : CraftingCpuHelper.calculatePatternPower(attemptContainer);
                        }
                        if (attemptContainer == null) {
                            diagnostics.tasksMissingInputs++;
                            if (diagnostics.firstMissingInputPattern == null) {
                                diagnostics.firstMissingInputPattern = details;
                            }
                            break;
                        }

                        DispatchResult single = new DispatchResult.Waiting(DispatchResult.WaitReason.PROVIDER_BUSY);
                        boolean sawAvailable = false;
                        for (ICraftingProvider provider : dispatchProviders) {
                            if (provider.isBusy()) continue;
                            sawAvailable = true;
                            boolean flatRateProvider = paysFlatRateCraftingPower(provider);
                            if (!flatRateProvider && energyService.extractAEPower(attemptPower, Actionable.SIMULATE,
                                    PowerMultiplier.CONFIG) < attemptPower - 0.01) {
                                diagnostics.energyBlockedProviders++;
                                single = new DispatchResult.Waiting(DispatchResult.WaitReason.ENERGY_UNAVAILABLE);
                                break;
                            }
                            final boolean accepted;
                            try {
                                // This exact call site is part of the integration contract with dynamic-output
                                // provider mixins. Do not move it into a helper without updating those mixins.
                            if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus && attemptExecution == null) {
                                attemptExecution = ECOExtractedPatternExecution.create(details, attemptContainer,
                                    attemptOutputs, attemptContainerItems, level);
                            }
                            accepted = provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                                    ? patternBus.pushPattern(attemptExecution, job.link.getCraftingID())
                                    : provider.pushPattern(details, attemptContainer);
                            } catch (RuntimeException failure) {
                                diagnostics.providerRejections++;
                                LOGGER.error("Crafting provider rejected a pattern with an exception; CPU retains inputs",
                                    failure);
                                continue;
                            }
                            if (!accepted) {
                                diagnostics.providerRejections++;
                                single = new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED);
                                continue;
                            }
                            if (!flatRateProvider) chargeAcceptedPatternEnergy(energyService, attemptPower);
                            recordPushedPattern(job, attemptOutputs, attemptContainerItems, 1L);
                            single = new DispatchResult.Accepted(1L);
                            break;
                        }
                        if (!sawAvailable) {
                            diagnostics.tasksWithBusyProviders++;
                            single = new DispatchResult.Waiting(DispatchResult.WaitReason.PROVIDER_BUSY);
                        }
                        if (single instanceof DispatchResult.Accepted) {
                            pushedPatterns++;
                            if (this.job != job) break taskLoop;
                            eligibleTasks.addAll(job.applyDispatchResultAndGetNewlyReady(task, single));
                            if (task.progress().value <= 0) continue taskLoop;
                            if (pushedPatterns == maxPatterns) break taskLoop;
                            continue;
                        }
                        CraftingCpuHelper.reinjectPatternInputs(inventory, attemptContainer);
                        break ordinaryDispatch;
                    }
                    if (this.job == job && pushedPatterns > ordinaryPushedBefore
                            && task.progress().value > 0 && pushedPatterns < maxPatterns) {
                        // The strategy may have used its fair quantum in this visit. Requeue the task so the
                        // remaining CPU budget can be used without relying on a hard-coded number of rounds.
                        eligibleTasks.add(task);
                    }
                }
            }
        } finally {
            endStatusChangeBatchSafely(job);
        }

        if (pushedPatterns == 0) {
            logStalledDispatch(job, activePhase, maxPatterns, diagnostics, "dispatch-returned-zero");
        }
        job.recordDynamicCyclePass(pushedPatterns > 0,
            diagnostics.runnableTasks > 0 && diagnostics.tasksMissingInputs == diagnostics.runnableTasks);
        return pushedPatterns;
    }

    private void logStalledDispatch(ExecutingCraftingJob stalledJob,
            @Nullable cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule.ComponentExecutionPhase phase,
            int maxPatterns, DispatchDiagnostics diagnostics, String reason) {
        long tick = TickHandler.instance().getCurrentTick();
        long elapsed = tick - lastStalledDispatchLogTick;
        if (lastStalledDispatchLogTick != Long.MIN_VALUE && elapsed >= 0L
                && elapsed < STALLED_DISPATCH_LOG_INTERVAL_TICKS) {
            return;
        }
        lastStalledDispatchLogTick = tick;

        String phaseDescription = phase == null ? "none"
            : phase.componentId() + ":" + phase.type()
                + "(patterns=" + phase.patternSet().size() + ",witness=" + phase.cycleWitness().size() + ")";
        IPatternDetails expectedWitnessPattern = null;
        if (stalledJob.runtimeExecutionState != null && stalledJob.runtimeExecutionState.activePhase() != null) {
            var runtimePhase = stalledJob.runtimeExecutionState.activePhase();
            if (stalledJob.runtimeExecutionState.stepIndex() < runtimePhase.steps().size()) {
                int taskId = runtimePhase.steps().get(stalledJob.runtimeExecutionState.stepIndex()).taskId();
                expectedWitnessPattern = stalledJob.runtimeExecutionState.plan().task(taskId).pattern();
            }
        } else if (phase != null && stalledJob.cycleWitnessIndex >= 0
                && stalledJob.cycleWitnessIndex < phase.cycleWitness().size()) {
            expectedWitnessPattern = phase.cycleWitness().get(stalledJob.cycleWitnessIndex);
        }
        Object expectedWitness = expectedWitnessPattern == null ? "none" : expectedWitnessPattern.getDefinition();
        Object missingInputPattern = diagnostics.firstMissingInputPattern == null ? "none"
            : diagnostics.firstMissingInputPattern.getDefinition();
        Object requiredInputs = diagnostics.firstMissingInputPattern == null ? List.of()
            : describePatternInputs(diagnostics.firstMissingInputPattern);
        // 以 4 列表格形式输出停滞发配诊断，便于观测
        // 列布局：name1 | value1 | name2 | value2（每个单元格 30 字符，整行宽 133 字符）
        LOGGER.warn(DISPATCH_TABLE_SEPARATOR);
        LOGGER.warn("Stalled crafting job=" + stalledJob.link.getCraftingID() + "  reason=" + reason);
        LOGGER.warn(DISPATCH_TABLE_SEPARATOR);
        LOGGER.warn(formatStalledDispatchRow("tick", tick, "maxPatterns", maxPatterns));
        LOGGER.warn(formatStalledDispatchRow("suspended", stalledJob.suspended, "phaseIndex", stalledJob.currentComponentIndex));
        LOGGER.warn(formatStalledDispatchRow("phase", truncate(phaseDescription, 60), "witnessIndex", stalledJob.cycleWitnessIndex));
        LOGGER.warn(formatStalledDispatchRow("expectedWitness", truncate(expectedWitness, 60), "taskKinds", stalledJob.tasks.size()));
        LOGGER.warn(formatStalledDispatchRow("taskExecutions", remainingTaskCount(stalledJob), "inventoryKinds", inventory.list.size()));
        LOGGER.warn(DISPATCH_TABLE_SEPARATOR);
        LOGGER.warn("[ crafting diagnostics ]");
        LOGGER.warn(DISPATCH_TABLE_SEPARATOR);
        LOGGER.warn(formatStalledDispatchRow("runnableTasks", diagnostics.runnableTasks, "phaseRejected", diagnostics.phaseRejectedTasks));
        LOGGER.warn(formatStalledDispatchRow("noProviders", diagnostics.tasksWithoutProviders, "providersBusy", diagnostics.tasksWithBusyProviders));
        LOGGER.warn(formatStalledDispatchRow("missingInputs", diagnostics.tasksMissingInputs, "energyBlocked", diagnostics.energyBlockedProviders));
        LOGGER.warn(formatStalledDispatchRow("providerRejected", diagnostics.providerRejections, "waitingKinds", stalledJob.waitingFor.list.size()));
        LOGGER.warn(DISPATCH_TABLE_SEPARATOR);
        LOGGER.warn("[ missing inputs ]");
        LOGGER.warn(DISPATCH_TABLE_SEPARATOR);
        LOGGER.warn(formatStalledDispatchRow("missingInputPattern", truncate(missingInputPattern, 60), "requiredInputs", truncate(requiredInputs, 60)));
        LOGGER.warn(DISPATCH_TABLE_SEPARATOR);
    }

    /** 将两个 name/value 对格式化为同一行："| name1 | value1 | name2 | value2 |"，每个单元格宽 30 字符。 */
    private static String formatStalledDispatchRow(String name1, Object value1, String name2, Object value2) {
        return String.format("| %-30s | %-30s | %-30s | %-30s |",
            truncate(name1, 30), truncate(String.valueOf(value1), 30),
            truncate(name2, 30), truncate(String.valueOf(value2), 30));
    }

    /** 截断过长的值，避免表格行宽失控。值为 null 时显示 "null"。 */
    private static String truncate(Object value, int maxLen) {
        if (value == null) return "null";
        String s = String.valueOf(value);
        if (s.length() <= maxLen) return s;
        return s.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private List<String> describePatternInputs(IPatternDetails pattern) {
        var result = new ArrayList<String>();
        for (var input : pattern.getInputs()) {
            if (input == null) continue;
            var alternatives = new ArrayList<String>();
            var possibleInputs = input.getPossibleInputs();
            if (possibleInputs != null) {
                for (var possible : possibleInputs) {
                    if (possible == null || possible.what() == null) continue;
                    long required;
                    try {
                        required = Math.multiplyExact(possible.amount(), input.getMultiplier());
                    } catch (ArithmeticException overflow) {
                        required = Long.MAX_VALUE;
                    }
                    long available = inventory.extract(possible.what(), Long.MAX_VALUE, Actionable.SIMULATE);
                    alternatives.add(possible.what() + " required=" + required + " available=" + available);
                }
            }
            result.add(alternatives.toString());
        }
        return result;
    }

    private static long remainingTaskCount(ExecutingCraftingJob inspectedJob) {
        long total = 0L;
        for (var progress : inspectedJob.tasks.values()) {
            if (progress.value <= 0L) continue;
            if (Long.MAX_VALUE - total < progress.value) return Long.MAX_VALUE;
            total += progress.value;
        }
        return total;
    }

    /**
     * Whether this provider's crafting energy is already covered by the flat per-tick draw of a fully
     * virtualized exchange group. Only ECO hosts can be: any other provider keeps paying per pattern.
     */
    private static boolean paysFlatRateCraftingPower(ICraftingProvider provider) {
        if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus)) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
        return controller != null && controller.isFullVirtualCraftingMode();
    }

    private void chargeAcceptedPatternEnergy(IEnergyService energyService, double requiredPower) {
        try {
            double charged = energyService.extractAEPower(
                requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG
            );
            if (Double.isNaN(charged) || charged < requiredPower - 0.01D) {
                LOGGER.error(
                    "Crafting pattern was accepted, but only {} of {} crafting energy was charged",
                    charged,
                    requiredPower
                );
            }
        } catch (RuntimeException e) {
            // The provider already owns the inputs. Accounting must continue so this pattern is not scheduled twice.
            LOGGER.error("Crafting pattern was accepted, but its crafting energy could not be charged", e);
        }
    }

    /**
     * Topological candidates for one pattern: every provider that advertises it. Collected once per task so the
     * inner dispatch loop stops rebuilding the same {@link ArrayList} and rescanning a fixed topology, and
     * copied because the grid may add or remove providers while a dispatch runs.
     *
     * <p>Availability is intentionally excluded. A provider's busy state and free capacity change with every
     * dispatch, so they are re-read from the provider on each attempt.
     */
    private List<ICraftingProvider> collectAvailableProviders(CraftingService craftingService,
            IPatternDetails details) {
        var identity = cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity.patternIdentityFor(details);
        if (identity != null) {
            var cached = providerTopologyCache.get(identity);
            if (cached != null) return cached;
        }
        List<ICraftingProvider> providers = new ArrayList<>();
        for (ICraftingProvider provider : craftingService.getProviders(details)) {
            providers.add(provider);
        }
        List<ICraftingProvider> result = List.copyOf(providers);
        if (identity != null && !result.isEmpty()) providerTopologyCache.put(identity, result);
        return result;
    }

    /** Live availability check over the reusable candidate set; it does not mutate provider state. */
    private boolean hasAvailableProvider(List<ICraftingProvider> candidateProviders) {
        for (int i = 0; i < candidateProviders.size(); i++) {
            if (!candidateProviders.get(i).isBusy()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Seeds the dispatch queue in a rotating order. A task is re-added after a successful dispatch, so the queue
     * remains fair for the whole CPU pass and is not capped by a fixed number of copies.
     */
    List<ExecutingCraftingJob.DispatchTask> fairTaskOrder(
            List<ExecutingCraftingJob.DispatchTask> readyTasks) {
        if (readyTasks.size() <= 1) return readyTasks;
        int offset = Math.floorMod(taskDispatchCursor++, readyTasks.size());
        List<ExecutingCraftingJob.DispatchTask> result = new ArrayList<>(readyTasks.size());
        for (int i = 0; i < readyTasks.size(); i++) result.add(readyTasks.get((offset + i) % readyTasks.size()));
        return result;
    }

    private boolean hasFastPathProvider(List<ICraftingProvider> candidateProviders) {
        for (var provider : candidateProviders) {
            if (provider instanceof ECOCraftingPatternBusBlockEntity) return true;
        }
        return false;
    }

    private boolean hasExternalCountedProvider(List<ICraftingProvider> candidateProviders) {
        for (var provider : candidateProviders) {
            // Thunderbolt can optionally mix a bridge onto NeoECO's own bus. Its native verified fast paths retain
            // stronger recipe and ownership guarantees, so external contracts are considered only for other hosts.
            if (provider instanceof ECOCraftingPatternBusBlockEntity) continue;
            if (ECOThunderboltBatchBridge.supports(provider)
                    || ECODataEnergisticsCountedBridge.supports(provider)) return true;
        }
        return false;
    }

    private boolean hasBatchProbeProvider(List<ICraftingProvider> candidateProviders) {
        for (var provider : candidateProviders) {
            if (isUnknownBatchProbeProvider(provider)) return true;
        }
        return false;
    }

    static boolean isUnknownBatchProbeProvider(ICraftingProvider provider) {
        return provider != null && isUnknownBatchProbeProviderType(provider.getClass());
    }

    static boolean isUnknownBatchProbeProviderType(Class<?> providerType) {
        return ECOBatchProbeCraftingProvider.class.isAssignableFrom(providerType)
            && !ECOCraftingPatternBusBlockEntity.class.isAssignableFrom(providerType);
    }

    private static Object patternIdentityOrObject(IPatternDetails pattern) {
        Object identity = cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity
            .patternIdentityFor(pattern);
        return identity == null ? pattern : identity;
    }

    /** Probe-batch providers are a distinct ownership contract and never enter the ordinary push loop. */
    private static List<ICraftingProvider> ordinaryProviders(List<ICraftingProvider> providers) {
        boolean filtered = false;
        for (var provider : providers) {
            if (provider instanceof ECOBatchProbeCraftingProvider) {
                filtered = true;
                break;
            }
        }
        if (!filtered) return providers;
        List<ICraftingProvider> ordinary = new ArrayList<>(providers.size());
        for (var provider : providers) {
            if (!(provider instanceof ECOBatchProbeCraftingProvider)) ordinary.add(provider);
        }
        return List.copyOf(ordinary);
    }

    /**
     * Conservative ordinary-path capacity hint supplied to the policy layer.
     *
     * <p>ECO pattern buses expose the number of reachable worker slots, while generic AE2 providers only expose
     * the busy predicate. Generic providers therefore contribute the remaining CPU budget and stop naturally when
     * their live {@code isBusy()} gate closes (or a push is rejected). Network-switch buses are deduplicated by
     * dispatch scope so the same pooled workers are not counted once per physical bus.</p>
     */
    private int estimateOrdinaryDispatchSlots(List<ICraftingProvider> candidateProviders, int dispatchBudget) {
        var visitedScopes = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        long slots = 0L;
        for (ICraftingProvider provider : candidateProviders) {
            if (provider.isBusy()) continue;
            long contribution;
            if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus) {
                ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
                if (controller == null || !visitedScopes.add(controller.getDispatchScope())) continue;
                contribution = Math.max(1L, patternBus.getAvailableThreadSlots());
            } else {
                // A plain ICraftingProvider did not opt in to any numeric capacity contract. It may consume the
                // remaining global CPU budget and naturally stops on isBusy(), rejection, or task/runtime limits.
                contribution = Math.max(0, dispatchBudget);
            }
            slots = slots > Integer.MAX_VALUE - contribution
                ? Integer.MAX_VALUE : slots + contribution;
            if (slots >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) slots;
    }

    /**
     * Dispatches through external counted contracts without teaching ECO any third-party machine internals.
     * Native Thunderbolt providers take precedence over Data Energistics' registry because a subclass may inherit
     * Data Energistics' conservative single-copy fallback while exposing a stronger native batch contract itself.
     */
    private DispatchResult tryPushExternalCountedBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> candidateProviders,
            IEnergyService energyService,
            double patternPower,
            long runtimeDispatchLimit,
            long taskRemaining,
            long cpuCopyBudget,
            Level level) {
        long legalUpper = calculateExternalBatchLegalUpper(
            execution, energyService, patternPower, runtimeDispatchLimit, taskRemaining);
        if (legalUpper <= 1L) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        for (ICraftingProvider candidate : candidateProviders) {
            if (candidate instanceof ECOCraftingPatternBusBlockEntity || candidate.isBusy()) continue;
            if (ECOThunderboltBatchBridge.supports(candidate)) {
                DispatchResult result = tryPushThunderboltBatch(job, execution, firstCraftingContainer,
                    candidate, energyService, patternPower,
                    legalUpper, cpuCopyBudget, level);
                if (!(result instanceof DispatchResult.Waiting)) return result;
            }
        }

        String patternIdentity = String.valueOf(patternIdentityOrObject(executionDetails(execution)));
        long tick = Math.max(0L, TickHandler.instance().getCurrentTick());
        for (ICraftingProvider provider : candidateProviders) {
            if (provider instanceof ECOCraftingPatternBusBlockEntity
                    || ECOThunderboltBatchBridge.supports(provider)
                    || provider.isBusy()
                    || !ECODataEnergisticsCountedBridge.supports(provider)) continue;
            var admission = ECODataEnergisticsCountedBridge.prepare(
                provider, executionDetails(execution), firstCraftingContainer, legalUpper, patternIdentity, tick);
            if (admission == null) continue;
            DispatchResult result = tryCommitDataEnergisticsAdmission(job, execution, firstCraftingContainer,
                provider, admission, energyService, patternPower, legalUpper);
            if (!(result instanceof DispatchResult.Waiting)) return result;
        }
        return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
    }

    private long calculateExternalBatchLegalUpper(
            ECOExtractedPatternExecution execution,
            IEnergyService energyService,
            double patternPower,
            long runtimeDispatchLimit,
            long taskRemaining) {
        long initial = calculateBatchRequestSize(execution, Math.min(taskRemaining, runtimeDispatchLimit));
        long energyLimit = maxBatchSizeFromEnergy(energyService, patternPower, initial);
        long availableExtra = ECOBatchCraftingHelper.maxCraftsFromInventory(
            inventory, execution.inputItems(), Math.max(0L, initial - 1L));
        long extractableLimit = availableExtra == Long.MAX_VALUE ? Long.MAX_VALUE : availableExtra + 1L;
        return calculateProbeLegalUpperBound(taskRemaining, runtimeDispatchLimit,
            execution.arithmeticBatchLimit(), extractableLimit, energyLimit);
    }

    private DispatchResult tryPushThunderboltBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            ICraftingProvider provider,
            IEnergyService energyService,
            double patternPower,
            long legalUpper,
            long cpuCopyBudget,
            Level level) {
        final long capacity;
        try {
            capacity = Math.max(0L, ECOThunderboltBatchBridge.capacity(provider, executionDetails(execution)));
        } catch (RuntimeException failure) {
            LOGGER.error("Thunderbolt batch provider failed while reporting capacity: " + provider, failure);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        long requested = ECOExternalBatchContracts.thunderboltRequest(legalUpper, capacity, cpuCopyBudget);
        if (requested <= 1L) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        List<GenericStack> extraInputs;
        try {
            extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), requested - 1L);
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
        } catch (RuntimeException extractionFailure) {
            LOGGER.error("Thunderbolt batch inputs could not be extracted", extractionFailure);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        final long leftover;
        try {
            leftover = ECOThunderboltBatchBridge.pushBatch(provider, executionDetails(execution),
                firstCraftingContainer, requested, level, job.link.getCraftingID());
        } catch (RuntimeException providerFailure) {
            // Thunderbolt's own dispatcher treats an exception as a full leftover. The provider contract requires
            // failure before ownership when it cannot return a valid leftover count.
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
            LOGGER.error("Thunderbolt batch provider failed; treating the complete batch as leftover: " + provider,
                providerFailure);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        final long accepted;
        try {
            accepted = ECOExternalBatchContracts.acceptedFromLeftover(requested, leftover);
        } catch (IllegalArgumentException invalidContract) {
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
            LOGGER.error("Thunderbolt batch provider {} returned invalid leftover {} for requested {}",
                provider, leftover, requested);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        if (accepted <= 0L) {
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        if (leftover > 0L) {
            ECOBatchCraftingHelper.insertAll(
                inventory, ECOBatchCraftingHelper.multiply(execution.inputItems(), leftover));
        }
        chargeCountedBatchEnergy(energyService, patternPower, accepted);
        if (this.job == job) recordPushedPattern(job, execution, accepted);
        return new DispatchResult.Accepted(accepted);
    }

    private DispatchResult tryCommitDataEnergisticsAdmission(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            ICraftingProvider provider,
            ECODataEnergisticsCountedBridge.PreparedAdmission admission,
            IEnergyService energyService,
            double patternPower,
            long requestedCount) {
        long count = admission.count();
        final ECOUselessDynamicOutputBridge.Registration dynamicRegistration;
        try {
            dynamicRegistration = ECOUselessDynamicOutputBridge.prepare(
                this, executionDetails(execution), count);
        } catch (RuntimeException compatibilityFailure) {
            LOGGER.error("Useless dynamic-output compatibility preparation failed", compatibilityFailure);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        if (dynamicRegistration == null) {
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }
        List<GenericStack> extraInputs;
        try {
            extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), count - 1L);
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
        } catch (RuntimeException extractionFailure) {
            LOGGER.error("Data Energistics counted inputs could not be extracted", extractionFailure);
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        boolean accepted = false;
        RuntimeException providerFailure = null;
        try {
            accepted = admission.commit(firstCraftingContainer);
        } catch (RuntimeException failure) {
            providerFailure = failure;
        }
        boolean ownershipTransferred = accepted || admission.hasTransferredInputOwnership();
        if (!ownershipTransferred) {
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
            if (providerFailure != null) {
                LOGGER.error("Data Energistics counted provider failed before ownership: " + provider, providerFailure);
            }
            return new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE);
        }

        if (providerFailure != null) {
            LOGGER.error("Data Energistics counted provider failed after taking ownership; accounting as accepted: "
                + provider, providerFailure);
        } else if (!accepted) {
            LOGGER.error("Data Energistics counted provider returned false after taking ownership; accounting {} crafts as accepted",
                count);
        }
        try {
            dynamicRegistration.commit(
                job.link.getCraftingID(), job.finalOutput == null ? null : job.finalOutput.what());
        } catch (RuntimeException compatibilityFailure) {
            // The provider owns the inputs already. Preserve counted accounting and surface the missing dynamic
            // registration loudly; rolling inputs back here would duplicate the physical batch.
            LOGGER.error("Useless dynamic outputs could not be registered after counted ownership transfer",
                compatibilityFailure);
        }
        chargeCountedBatchEnergy(energyService, patternPower, count);
        if (this.job == job) recordPushedPattern(job, execution, count);
        return new DispatchResult.Accepted(count);
    }

    private void chargeCountedBatchEnergy(IEnergyService energyService, double patternPower, long count) {
        double requiredPower = patternPower * count;
        if (Double.isFinite(requiredPower)) chargeAcceptedPatternEnergy(energyService, requiredPower);
    }

    private ProbeDispatchOutcome tryPushProbedBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> candidateProviders,
            IEnergyService energyService,
            double patternPower,
            long runtimeDispatchLimit,
            long taskRemaining,
            int remainingCpuProbeBudget,
            int taskId) {
        ECOBatchProbeCraftingProvider selected = null;
        for (var provider : candidateProviders) {
            if (isUnknownBatchProbeProvider(provider) && provider instanceof ECOBatchProbeCraftingProvider capable
                    && !provider.isBusy()) {
                selected = capable;
                break;
            }
        }
        if (selected == null) {
            return new ProbeDispatchOutcome(
                new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE), 0);
        }

        long legalUpper = calculateBatchRequestSize(execution, Math.min(taskRemaining, runtimeDispatchLimit));
        long energyLimit = maxBatchSizeFromEnergy(energyService, patternPower, legalUpper);
        long availableExtra = ECOBatchCraftingHelper.maxCraftsFromInventory(
            inventory, execution.inputItems(), Math.max(0L, legalUpper - 1L));
        long extractableLimit = availableExtra == Long.MAX_VALUE ? Long.MAX_VALUE : availableExtra + 1L;
        legalUpper = calculateProbeLegalUpperBound(taskRemaining, runtimeDispatchLimit,
            execution.arithmeticBatchLimit(), extractableLimit, energyLimit);
        if (legalUpper <= 0L) {
            return new ProbeDispatchOutcome(
                new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE), 0);
        }

        final Object scope;
        try {
            scope = Objects.requireNonNull(selected.eco$getBatchProbeScope(),
                "batch probe provider returned a null scope");
        } catch (RuntimeException contractViolation) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, firstCraftingContainer);
            LOGGER.error("Batch probe provider returned an invalid dispatch scope", contractViolation);
            return new ProbeDispatchOutcome(
                new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED), 0);
        }
        BatchProbeKey key = new BatchProbeKey(scope, patternIdentityOrObject(executionDetails(execution)));
        BatchCapacityProbeState state = batchProbeStates.computeIfAbsent(key, ignored -> new BatchCapacityProbeState());
        int requiredBudget = ECOBatchProbeScheduler.candidates(state.startingUpperBound(legalUpper)).length;
        int probesAlreadyUsed = ECOBatchProbeScheduler.MAX_BATCH_PROBES_PER_CPU_PER_TICK
            - remainingCpuProbeBudget;
        if (!ECOBatchProbeScheduler.canStartCpuProbeWindow(probesAlreadyUsed, requiredBudget)) {
            return new ProbeDispatchOutcome(
                new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE), 0);
        }

        ECOBatchProbeCraftingProvider provider = selected;
        ECOBatchProbeScheduler.ProbeResult probe;
        int[] attemptedProbes = { 0 };
        try {
            probe = ECOBatchProbeScheduler.probe(state, legalUpper, remainingCpuProbeBudget,
                candidate -> {
                    attemptedProbes[0]++;
                    return provider.eco$simulateBatch(execution, candidate);
                });
        } catch (RuntimeException contractViolation) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, firstCraftingContainer);
            LOGGER.error("Batch probe side-effect-free simulation failed", contractViolation);
            return new ProbeDispatchOutcome(
                new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED), attemptedProbes[0]);
        }
        if (probe.selected() <= 0L) {
            return new ProbeDispatchOutcome(
                new DispatchResult.Waiting(DispatchResult.WaitReason.CAPACITY_UNAVAILABLE), probe.probeCount());
        }

        long craftCount = probe.selected();
        List<GenericStack> extraInputs;
        try {
            extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), craftCount - 1L);
        } catch (RuntimeException invalidBatch) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, firstCraftingContainer);
            return new ProbeDispatchOutcome(
                new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED), probe.probeCount());
        }
        boolean extraExtracted = false;
        boolean ownershipTransferred = false;
        try {
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
            extraExtracted = true;
            if (!provider.eco$commitBatch(execution, craftCount, job.link.getCraftingID())) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, true);
                return new ProbeDispatchOutcome(
                    new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED), probe.probeCount());
            }
            ownershipTransferred = true;
            double requiredPower = patternPower * craftCount;
            if (Double.isFinite(requiredPower)) chargeAcceptedPatternEnergy(energyService, requiredPower);
            if (this.job == job) recordPushedPattern(job, execution, craftCount);
            return new ProbeDispatchOutcome(new DispatchResult.Accepted(craftCount), probe.probeCount());
        } catch (RuntimeException failure) {
            if (ownershipTransferred) {
                LOGGER.error("Batch probe commit failed after ownership transfer; accounting as accepted", failure);
                return new ProbeDispatchOutcome(new DispatchResult.Accepted(craftCount), probe.probeCount());
            }
            rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraExtracted);
            LOGGER.error("Batch probe commit failed before ownership transfer; inputs were rolled back", failure);
            return new ProbeDispatchOutcome(
                new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED), probe.probeCount());
        } catch (Error failure) {
            if (!ownershipTransferred) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraExtracted);
            }
            throw failure;
        }
    }

    private static IPatternDetails executionDetails(ECOExtractedPatternExecution execution) {
        return execution.details();
    }

    private void resetBatchProbeBudgetForCurrentTick() {
        long currentTick = TickHandler.instance().getCurrentTick();
        if (batchProbeBudgetTick == currentTick) return;
        batchProbeBudgetTick = currentTick;
        batchProbesUsedThisTick = 0;
        batchProbedTasksThisTick.clear();
    }

    private int tryPushVerifiedFastPathBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> candidateProviders,
            IEnergyService energyService,
            double patternPower,
            long taskRemaining,
            boolean firstBatch) {
        if (!canAttemptBatchFastPath(execution) || taskRemaining <= 1) {
            return 0;
        }

        // Ask providers for the full remaining task. The selected F-series host and worker cap the
        // offer to their live thread capacity; inventory, energy and coolant apply further bounds.
        int requested = (int) Math.min(Integer.MAX_VALUE, calculateBatchRequestSize(execution, taskRemaining));
        if (requested <= 1) {
            return 0;
        }
        ECOCraftingPatternBusBlockEntity selectedPatternBus = null;
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer selectedOffer = null;
        // One offer search per dispatch scope. A Network Switch group answers as a single scope now, because
        // one of its buses already searches every worker of the group.
        List<Object> visitedScopes = new ArrayList<>(4);
        for (ICraftingProvider provider : candidateProviders) {
            if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus) || provider.isBusy()) {
                continue;
            }
            ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
            if (controller == null) {
                continue;
            }
            Object scope = controller.getDispatchScope();
            if (containsIdentity(visitedScopes, scope)) {
                continue;
            }
            visitedScopes.add(scope);
            var offer = patternBus.findBatchFastPathOffer(execution, requested);
            if (offer != null && offer.maxBatchSize() > 1
                    && (selectedOffer == null || offer.maxBatchSize() > selectedOffer.maxBatchSize())) {
                selectedPatternBus = patternBus;
                selectedOffer = offer;
                if (offer.maxBatchSize() >= requested) {
                    break;
                }
            }
        }
        if (selectedPatternBus == null || selectedOffer == null) {
            return 0;
        }
        var verifiedRecipe = selectedOffer.recipe();
        // The credential must have been minted for this very execution context, never for an earlier one.
        if (!verifiedRecipe.isVerifiedFor(execution)) {
            return 0;
        }

        ECOCraftingSystemBlockEntity controller = selectedPatternBus.getCraftingController();
        if (controller == null) {
            return 0;
        }

        // A fully virtualized host pays one flat group-wide draw per tick instead of a per-craft charge, so the
        // batch must be neither sized by nor billed for pattern power here.
        boolean flatRatePower = controller.isFullVirtualCraftingMode();
        if (!firstBatch) {
            // The probe craft was extracted before the offer search. Put it back before sizing a complete
            // subsequent batch, otherwise the first input would be counted twice or falsely appear unavailable.
            CraftingCpuHelper.reinjectPatternInputs(inventory, firstCraftingContainer);
        }

        int batchSize = 0;
        List<GenericStack> batchInputs = List.of();
        boolean batchInputsExtracted = false;
        boolean ownershipTransferred = false;
        try {
            batchSize = Math.min(requested, selectedOffer.maxBatchSize());
            if (!flatRatePower) {
                batchSize = Math.min(batchSize, maxBatchSizeFromEnergy(energyService, patternPower, batchSize));
            }
            batchSize = controller.getCraftingCoolantCraftLimit(5, controller.getEffectiveOverclockTimes(), batchSize);
            if (batchSize <= 1) {
                if (!firstBatch) reclaimProbeCraftingInputs(firstCraftingContainer);
                return 0;
            }

            if (verifiedRecipe.reusableStateModel() == null) {
                int requestedInventoryCrafts = firstBatch ? batchSize - 1 : batchSize;
                int availableCrafts = ECOBatchCraftingHelper.maxCraftsFromInventory(
                    inventory, execution.inputItems(), requestedInventoryCrafts);
                batchSize = Math.min(batchSize, firstBatch ? availableCrafts + 1 : availableCrafts);
            } else {
                batchSize = (int) (firstBatch
                    ? ECOBatchCraftingHelper.maxBatchSizeFromAdditionalInputs(
                        inventory, (long) batchSize,
                        (java.util.function.LongFunction<List<GenericStack>>) verifiedRecipe::additionalInputs)
                    : ECOBatchCraftingHelper.maxBatchSizeFromBatchInputs(
                        inventory, (long) batchSize,
                        (java.util.function.LongFunction<List<GenericStack>>) verifiedRecipe::batchInputs));
            }
            if (batchSize <= 1) {
                if (!firstBatch) reclaimProbeCraftingInputs(firstCraftingContainer);
                return 0;
            }

            double requiredPower = flatRatePower ? 0.0D : patternPower * batchSize;
            if (!Double.isFinite(requiredPower)) {
                if (!firstBatch) reclaimProbeCraftingInputs(firstCraftingContainer);
                return 0;
            }
            if (firstBatch) {
                // The first craft was extracted before the offer search. Only the remainder of this batch is
                // still CPU-owned at this point.
                batchInputs = verifiedRecipe.additionalInputs(batchSize);
            } else {
                // This task already dispatched a finite batch earlier in the same CPU pass. The probe craft was
                // returned above, so extract the complete next batch from a clean per-batch ownership boundary.
                batchInputs = verifiedRecipe.batchInputs(batchSize);
            }
            ECOBatchCraftingHelper.extractExact(inventory, batchInputs);
            batchInputsExtracted = true;
            // Bind the already-verified recipe credential to this batch size. No stack list is re-copied and no
            // stack list is compared again from here on.
            var verified = verifiedRecipe.withBatch(batchSize, job.link.getCraftingID());
            if (verified == null || !selectedPatternBus.pushBatch(verified, selectedOffer)) {
                if (firstBatch) {
                    rollbackBatchInputs(inventory, firstCraftingContainer, batchInputs, true, true);
                } else if (batchInputsExtracted) {
                    ECOBatchCraftingHelper.insertAll(inventory, batchInputs);
                    reclaimProbeCraftingInputs(firstCraftingContainer);
                }
                return -1;
            }
            // The worker owns every input from this point onward. Never reinject them into the CPU.
            ownershipTransferred = true;
            if (!flatRatePower) {
                try {
                    double chargedPower = energyService.extractAEPower(
                        requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG
                    );
                    if (Double.isNaN(chargedPower) || chargedPower < requiredPower - 0.01D) {
                        selectedOffer.worker().getFastPathCache().recordException();
                        LOGGER.error(
                            "ECO batch was accepted, but only {} of {} crafting energy was charged",
                            chargedPower,
                            requiredPower
                        );
                    }
                } catch (RuntimeException e) {
                    selectedOffer.worker().getFastPathCache().recordException();
                    LOGGER.error("ECO batch was accepted, but its crafting energy could not be charged", e);
                }
            }
            try {
                if (this.job == job) {
                    recordPushedBatchPattern(job, verifiedRecipe, batchSize);
                }
            } catch (RuntimeException e) {
                selectedOffer.worker().getFastPathCache().recordException();
                LOGGER.error("ECO batch was accepted, but its CPU accounting update failed", e);
            }
            return batchSize;
        } catch (RuntimeException e) {
            selectedOffer.worker().getFastPathCache().recordException();
            if (ownershipTransferred) {
                LOGGER.error("ECO batch failed after ownership transfer; accounting it as accepted", e);
                return batchSize;
            }
            if (firstBatch) {
                rollbackBatchInputs(inventory, firstCraftingContainer, batchInputs, true, batchInputsExtracted);
            } else {
                if (batchInputsExtracted) {
                    ECOBatchCraftingHelper.insertAll(inventory, batchInputs);
                }
                reclaimProbeCraftingInputs(firstCraftingContainer);
            }
            logBatchRejection(batchSize, taskRemaining, e);
            return -1;
        } catch (Error e) {
            // Error is included so extracted inputs are returned before the failure escapes.
            selectedOffer.worker().getFastPathCache().recordException();
            if (!ownershipTransferred) {
                if (firstBatch) {
                    rollbackBatchInputs(inventory, firstCraftingContainer, batchInputs, true, batchInputsExtracted);
                } else {
                    if (batchInputsExtracted) {
                        ECOBatchCraftingHelper.insertAll(inventory, batchInputs);
                    }
                    reclaimProbeCraftingInputs(firstCraftingContainer);
                }
            }
            throw e;
        }
    }

    /**
     * Virtual dispatch is intentionally separate from the finite int fast path. One free physical FX lane owns
     * the complete remaining long task and materializes long-valued GenericStack totals without creating one
     * thread object per craft.
     */
    private long tryPushVerifiedVirtualBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> candidateProviders,
            long taskRemaining) {
        if (!canAttemptBatchFastPath(execution) || taskRemaining <= 0L) {
            return 0L;
        }
        ECOCraftingPatternBusBlockEntity selectedBus = null;
        ECOCraftingPatternBusBlockEntity.VirtualFastPathOffer selectedOffer = null;
        List<Object> visitedScopes = new ArrayList<>(4);
        for (ICraftingProvider provider : candidateProviders) {
            if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus) || provider.isBusy()) {
                continue;
            }
            ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
            if (controller == null || !controller.getCapabilitySnapshot().virtualMode()) {
                continue;
            }
            Object scope = controller.getDispatchScope();
            if (containsIdentity(visitedScopes, scope)) {
                continue;
            }
            visitedScopes.add(scope);
            var offer = patternBus.findVirtualFastPathOffer(execution);
            if (offer != null) {
                selectedBus = patternBus;
                selectedOffer = offer;
                break;
            }
        }
        if (selectedBus == null || selectedOffer == null || !selectedOffer.recipe().isVerifiedFor(execution)) {
            return 0L;
        }
        var verifiedRecipe = selectedOffer.recipe();

        long requestedBatchSize = Math.min(taskRemaining, verifiedRecipe.arithmeticBatchLimit());
        long craftCount;
        if (verifiedRecipe.reusableStateModel() == null) {
            long availableExtra = ECOBatchCraftingHelper.maxCraftsFromInventory(
                inventory, execution.inputItems(), requestedBatchSize - 1L);
            craftCount = Math.min(requestedBatchSize, availableExtra + 1L);
        } else {
            craftCount = ECOBatchCraftingHelper.maxBatchSizeFromAdditionalInputs(
                inventory, requestedBatchSize,
                (java.util.function.LongFunction<List<GenericStack>>) verifiedRecipe::additionalInputs);
        }
        if (craftCount <= 0L) {
            return 0L;
        }
        List<GenericStack> extraInputs;
        try {
            extraInputs = verifiedRecipe.additionalInputs(craftCount);
        } catch (RuntimeException e) {
            return 0L;
        }
        boolean extracted = false;
        boolean ownershipTransferred = false;
        try {
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
            extracted = true;
            var verified = verifiedRecipe.withVirtualBatch(craftCount, job.link.getCraftingID());
            if (verified == null || !selectedBus.pushVirtualBatch(verified, selectedOffer)) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, true);
                return -1L;
            }
            ownershipTransferred = true;
            if (this.job == job) {
                recordPushedBatchPattern(job, verifiedRecipe, craftCount);
            }
            return craftCount;
        } catch (RuntimeException e) {
            selectedOffer.worker().getFastPathCache().recordException();
            if (ownershipTransferred) {
                LOGGER.error("Virtual ECO batch failed after ownership transfer; accounting it as accepted", e);
                return craftCount;
            }
            rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extracted);
            logBatchRejection(craftCount, taskRemaining, e);
            return -1L;
        } catch (Error e) {
            selectedOffer.worker().getFastPathCache().recordException();
            if (!ownershipTransferred) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extracted);
            }
            throw e;
        }
    }

    /**
     * How many crafts this CPU would like to hand over in one batch. There is deliberately no fixed batch
     * ceiling: the request is the whole remaining task, bounded only by what the multiplied stack amounts
     * can still represent. The accepted size is decided by the F-series host in
     * {@link ECOCraftingPatternBusBlockEntity#findBatchFastPathOffer}, which reports its live thread
     * capability.
     */
    private long calculateBatchRequestSize(ECOExtractedPatternExecution execution, long taskRemaining) {
        long requested = Math.max(0L, taskRemaining);
        // The arithmetic ceiling was computed once when the execution context was built.
        return Math.min(requested, execution.arithmeticBatchLimit());
    }

    static long calculateProbeLegalUpperBound(long taskRemaining, long runtimeDispatchLimit,
            long arithmeticLimit, long extractableInputLimit, long energyResourceLimit) {
        return Math.min(Math.min(Math.max(0L, taskRemaining), Math.max(0L, runtimeDispatchLimit)),
            Math.min(Math.max(0L, arithmeticLimit),
                Math.min(Math.max(0L, extractableInputLimit), Math.max(0L, energyResourceLimit))));
    }

    private static boolean containsIdentity(List<Object> visited, Object candidate) {
        for (int i = 0; i < visited.size(); i++) {
            if (visited.get(i) == candidate) {
                return true;
            }
        }
        return false;
    }

    /**
     * A batch that fails before ownership transfer aborts the whole task for this tick, so it must never be
     * silent: an unnoticed rejection here looks exactly like "the host stopped dispatching".
     */
    private void logBatchRejection(long batchSize, long taskRemaining, RuntimeException e) {
        long tick = TickHandler.instance().getCurrentTick();
        if (tick - lastBatchRejectionLogTick < BATCH_REJECTION_LOG_INTERVAL_TICKS
            && lastBatchRejectionLogTick != Long.MIN_VALUE) {
            return;
        }
        lastBatchRejectionLogTick = tick;
        LOGGER.error(
            "ECO batch of {} crafts was rejected before ownership transfer (task remaining {}); inputs were returned to the CPU",
            batchSize,
            taskRemaining,
            e
        );
    }

    private void rollbackBatchInputs(
            ListCraftingInventory inventory,
            KeyCounter[] firstCraftingContainer,
            List<GenericStack> extraInputs,
            boolean firstInputsOwned,
            boolean extraInputsExtracted) {
        if (firstInputsOwned) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, firstCraftingContainer);
        }

        if (extraInputsExtracted) {
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
        }
    }

    /** Returns a temporarily reinserted probe craft to the caller's original ownership state. */
    private void reclaimProbeCraftingInputs(KeyCounter[] firstCraftingContainer) {
        if (firstCraftingContainer == null || firstCraftingContainer.length == 0) return;
        ECOBatchCraftingHelper.extractExact(inventory, ECOFastPathStacks.copyCounters(firstCraftingContainer));
    }

    private boolean canAttemptBatchFastPath(ECOExtractedPatternExecution execution) {
        return execution.canUseFastPath();
    }

    private int maxBatchSizeFromEnergy(IEnergyService energyService, double patternPower, int requested) {
        return ECOBatchCraftingHelper.maxAffordableCrafts(
            patternPower,
            requested,
            totalPower -> energyService.extractAEPower(
                totalPower, Actionable.SIMULATE, PowerMultiplier.CONFIG
            )
        );
    }

    private void recordPushedPattern(
            ExecutingCraftingJob job, ECOExtractedPatternExecution execution, long craftCount) {
        long multiplier = Math.max(1L, craftCount);
        for (var expectedOutput : execution.expectedOutputs()) {
            long dispatchedAmount = Math.multiplyExact(expectedOutput.amount(), multiplier);
            job.waitingFor.insert(expectedOutput.what(), dispatchedAmount,
                Actionable.MODULATE);
        }
        postGenericStackKeysChange(execution.expectedOutputs());
        for (var expectedContainerItem : execution.expectedContainerItems()) {
            job.waitingFor.insert(expectedContainerItem.what(),
                Math.multiplyExact(expectedContainerItem.amount(), multiplier), Actionable.MODULATE);
            job.timeTracker.addMaxItems(Math.multiplyExact(expectedContainerItem.amount(), multiplier),
                expectedContainerItem.what().getType());
        }
        postGenericStackKeysChange(execution.expectedContainerItems());
        markCpuDirty();
    }

    /**
     * Records a verified batch using its actual aggregate remainder contract. A durability tool is returned once
     * after the whole batch (or not at all when it breaks), so multiplying the one-craft remainder would reserve
     * the wrong key and leave the CPU waiting forever for items the worker can never emit.
     */
    private void recordPushedBatchPattern(
            ExecutingCraftingJob job, ECOVerifiedFastPathRecipe recipe, long craftCount) {
        long multiplier = Math.max(1L, craftCount);
        for (var output : recipe.outputsPerCraft()) {
            long dispatchedAmount = Math.multiplyExact(output.amount(), multiplier);
            job.waitingFor.insert(output.what(), dispatchedAmount, Actionable.MODULATE);
            postGenericStackKeysChange(List.of(output));
        }
        for (var remainder : recipe.batchRemainders(multiplier)) {
            job.waitingFor.insert(remainder.what(), remainder.amount(), Actionable.MODULATE);
            job.timeTracker.addMaxItems(remainder.amount(), remainder.what().getType());
            postGenericStackKeysChange(List.of(remainder));
        }
        markCpuDirty();
    }

    private long maxBatchSizeFromEnergy(IEnergyService energyService, double patternPower, long requested) {
        return ECOBatchCraftingHelper.maxAffordableCrafts(
            patternPower,
            requested,
            totalPower -> energyService.extractAEPower(
                totalPower, Actionable.SIMULATE, PowerMultiplier.CONFIG
            )
        );
    }

    private void recordPushedPattern(
            ExecutingCraftingJob job, KeyCounter expectedOutputs, KeyCounter expectedContainerItems, long craftCount) {
        long multiplier = Math.max(1L, craftCount);
        for (var expectedOutput : expectedOutputs) {
            long dispatchedAmount = Math.multiplyExact(expectedOutput.getLongValue(), multiplier);
            job.waitingFor.insert(expectedOutput.getKey(), dispatchedAmount,
                Actionable.MODULATE);
            postChange(expectedOutput.getKey());
        }

        for (var expectedContainerItem : expectedContainerItems) {
            job.waitingFor.insert(
                    expectedContainerItem.getKey(), Math.multiplyExact(expectedContainerItem.getLongValue(), multiplier),
                    Actionable.MODULATE);
            job.timeTracker.addMaxItems(
                    Math.multiplyExact(expectedContainerItem.getLongValue(), multiplier),
                    expectedContainerItem.getKey().getType());
            postChange(expectedContainerItem.getKey());
        }

        markCpuDirty();
    }

    private static boolean isPeriodicLogDue(long previousTick, long tick, long intervalTicks) {
        if (previousTick == Long.MIN_VALUE) return true;
        long elapsed = tick - previousTick;
        return elapsed < 0L || elapsed >= intervalTicks;
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0L) return left;
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 由 CraftingService 以 Integer.MAX_VALUE 优先级调用，用于注入正在等待的物品。
     *
     * @return 已消耗数量。
     */
    public long insert(AEKey what, long amount, Actionable type) {
        // 任务完成时也停止接收物品，防止在 storeItems 推出物品时重新插入
        if (what == null || amount <= 0L || job == null)
            return 0;
        if (deliveringBufferedFinalOutput && job.finalOutput != null && what.matches(job.finalOutput)) {
            return 0L;
        }

        // 只接收正在等待的物品。
        var waitingFor = job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0;
        }

        // 确保不接收超出等待数量的物品。
        if (amount > waitingFor) {
            amount = waitingFor;
        }

        if (type == Actionable.MODULATE && !what.matches(job.finalOutput)) {
            job.timeTracker.decrementItems(amount, what.getType());
            job.waitingFor.extract(what, amount, Actionable.MODULATE);
            markCpuDirty();
        }

        if (what.matches(job.finalOutput)) {
            ExecutingCraftingJob currentJob = job;
            long reserveTarget = currentJob.finalOutputFeedbackReserve(what);
            long alreadyReserved = inventory.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
            long toReserve = Math.min(amount, Math.max(0L, reserveTarget - alreadyReserved));
            inventory.insert(what, toReserve, type);
            long reserved = toReserve;
            long acceptedOwnership = Math.addExact(reserved,
                currentJob.bufferedFinalOutput.accept(amount - reserved, type));
            if (type == Actionable.MODULATE && acceptedOwnership > 0L) {
                // Ownership commits here. Delivery happens separately, so a network callback cannot make the Worker
                // retry or make this CPU accept the same physical output again.
                currentJob.timeTracker.decrementItems(acceptedOwnership, what.getType());
                currentJob.waitingFor.extract(what, acceptedOwnership, Actionable.MODULATE);
                // A CPU ownership event is emitted only after the inventory/buffer accepted the physical stack.
                if (currentJob.runtimeExecutionState() != null) {
                    currentJob.runtimeExecutionState().acceptOutput(what, acceptedOwnership);
                }
                postChange(what);
                markCpuDirty();
                drainBufferedFinalOutput(currentJob);
            }
            return acceptedOwnership;
        } else {
            if (type == Actionable.MODULATE) {
                inventory.insert(what, amount, Actionable.MODULATE);
                long accepted = amount;
                if (job.runtimeExecutionState() != null) {
                    job.runtimeExecutionState().acceptOutput(what, accepted);
                }
                return accepted;
            }
        }

        return amount;
    }

    /**
     * Accepts a worker output only when this CPU still owns the supplied crafting job.
     *
     * <p>Worker outputs carry the job id, but AE2's legacy {@code insertIntoCpus} API does not. Keeping this
     * guard at the CPU boundary prevents an output from being assigned to another CPU that happens to wait for
     * the same key.</p>
     */
    public long insertForJob(UUID craftingJobId, AEKey what, long amount, Actionable type) {
        if (craftingJobId == null || job == null || !craftingJobId.equals(job.link.getCraftingID())) {
            return 0L;
        }
        return insert(what, amount, type);
    }

    public boolean hasCraftingJob(UUID craftingJobId) {
        return craftingJobId != null && job != null && craftingJobId.equals(job.link.getCraftingID());
    }

    private long deliverFinalOutput(AEKey what, long amount, Actionable mode) {
        if (job == null || amount <= 0L) {
            return 0L;
        }
        if (!job.link.isStandalone()) {
            return job.link.insert(what, amount, mode);
        }
        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory().insert(what, amount, mode, cpu.getActionSource());
    }

    private static long validateInsertionAmount(long inserted, long requested, String target) {
        if (inserted < 0L || inserted > requested) {
            throw new IllegalStateException(
                "Invalid insertion result from " + target + ": " + inserted + " for " + requested
            );
        }
        return inserted;
    }

    private void initializeRuntimeOwnershipFromPhysicalState(ExecutingCraftingJob currentJob, boolean recovering) {
        RuntimeExecutionState state = currentJob.runtimeExecutionState();
        if (state == null) return;
        Map<AEKey, Long> physical = new LinkedHashMap<>();
        for (var entry : inventory.list) physical.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
        if (currentJob.finalOutput != null && currentJob.bufferedFinalOutput.amount() > 0L) {
            physical.merge(currentJob.finalOutput.what(), currentJob.bufferedFinalOutput.amount(), Math::addExact);
        }
        if (recovering && !state.ownershipSnapshot().equals(physical)) {
            LOGGER.error("Persisted ownership does not match CPU inventory and final-output buffer; blocking recovery");
            currentJob.blockRecovery();
            return;
        }
        state.restoreOwnership(physical);
    }

    private void logFinalOutputDeliveryFailure(RuntimeException e) {
        long tick = TickHandler.instance().getCurrentTick();
        long elapsed = tick - lastFinalOutputDeliveryFailureLogTick;
        if (lastFinalOutputDeliveryFailureLogTick == Long.MIN_VALUE || elapsed < 0L || elapsed >= 100L) {
            lastFinalOutputDeliveryFailureLogTick = tick;
            LOGGER.error("ECO final-output delivery failed; the CPU-owned output remains buffered", e);
        }
    }

    /**
     * 完成当前合成任务。
     *
     * @param success 任务完成则为 true，取消则为 false。
     */
    private void finishJob(boolean success) {
        preserveBufferedFinalOutput();
        if (success) {
            job.link.markDone();
        } else {
            job.link.cancel();
        }

        // TODO: 记录日志

        // 清空等待列表并发送所有相关变更通知。
        job.waitingFor.clear();
        // 通知已打开菜单关于已取消的调度任务。
        for (var entry : job.tasks.entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }

        notifyJobOwner(
                job, success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        RuntimeExecutionState runtimeState = job.runtimeExecutionState();
        if (runtimeState != null) {
            for (var owned : runtimeState.ownershipSnapshot().entrySet()) {
                if (owned.getValue() > 0L) runtimeState.releaseExternal(owned.getKey(), owned.getValue());
            }
        }

        // 结束任务。
        this.job = null;
        providerTopologyCache.clear();
        if (!batchingStatusChanges) initializeDirtyResourceQueue(null);

        // 存储所有剩余物品。
        this.storeItems();
    }

    private void preserveBufferedFinalOutput() {
        long buffered = job.bufferedFinalOutput.amount();
        if (buffered <= 0L) {
            return;
        }
        if (job.finalOutput == null) {
            LOGGER.error(
                "Discarding {} buffered final-output units because their persisted key is invalid",
                buffered
            );
            job.bufferedFinalOutput.removeDelivered(buffered);
            markCpuDirty();
            return;
        }
        AEKey key = job.finalOutput.what();
        long stored = inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
        // Overflow guard only: fail loudly rather than silently wrapping the CPU's own item ledger.
        Math.addExact(stored, buffered);

        // Move ownership between the two local ledgers before notifying observers.
        inventory.list.add(key, buffered);
        job.bufferedFinalOutput.removeDelivered(buffered);
        postChange(key);
        markCpuDirty();
    }

    /**
     * 取消当前合成任务。
     */
    public void cancel() {
        // 没有可取消的任务 :P
        if (job == null)
            return;

        UUID craftingJobId = job.link.getCraftingID();
        finishJob(false);
        recoverInflightWorkerInputs(craftingJobId);
    }

    private void recoverInflightWorkerInputs(UUID craftingJobId) {
        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return;
        }
        var storage = grid.getStorageService().getInventory();
        // A batch may have been accepted by a worker in another Network Switch member. Recover by job id from
        // every worker still on this AE grid, rather than following today's switch topology through Pattern
        // Buses: the group may have split or been rebuilt since ownership was transferred.
        for (ECOCraftingWorkerBlockEntity worker : grid.getMachines(ECOCraftingWorkerBlockEntity.class)) {
            worker.recoverJobToNetwork(craftingJobId, storage);
        }
    }

    /**
     * 尝试将所有本地存储的物品转存回存储网络。
     */
    public void storeItems() {
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        // 无事可做则快速返回。
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

            // 网络无法接收全部物品，即存储空间不足或已满
            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();

        markCpuDirty();
    }

    private void postChange(@Nullable AEKey what) {
        if (batchingStatusChanges) {
            batchedAnyStatusChange = true;
            if (what == null) {
                batchedFullStatusChange = true;
                clearQueuedStatusChanges();
            } else if (!batchedFullStatusChange) {
                int resourceId = dirtyResourceState == null ? -1 : dirtyResourceState.resourceIdIfKnown(what);
                if (resourceId >= 0 && resourceId < dirtyResourcePresent.length) {
                    if (!dirtyResourcePresent[resourceId]) {
                        dirtyResourcePresent[resourceId] = true;
                        dirtyResourceQueue[dirtyResourceCount++] = resourceId;
                    }
                } else {
                    fallbackStatusChanges.add(what);
                }
            }
            return;
        }

        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (var listener : listeners) {
            listener.accept(what);
        }
    }

    private void beginStatusChangeBatch(ExecutingCraftingJob dispatchJob) {
        RuntimeExecutionState runtimeState = dispatchJob.runtimeExecutionState();
        if (dirtyResourceState != runtimeState) initializeDirtyResourceQueue(runtimeState);
        clearStatusBatchState();
        batchingStatusChanges = true;
    }

    private void endStatusChangeBatch(ExecutingCraftingJob dispatchJob) {
        batchingStatusChanges = false;
        boolean anyStatusChange = batchedAnyStatusChange;
        boolean fullStatusChange = batchedFullStatusChange;
        boolean markDirty = cpuDirtyRequested;
        RuntimeExecutionState resourceState = dirtyResourceState;
        int[] changedResourceIds = fullStatusChange
            ? new int[0] : Arrays.copyOf(dirtyResourceQueue, dirtyResourceCount);
        AEKey[] fallbackKeys = fullStatusChange
            ? new AEKey[0] : fallbackStatusChanges.toArray(AEKey[]::new);

        clearStatusBatchState();
        if (this.job != dispatchJob) initializeDirtyResourceQueue(null);

        if (this.job == dispatchJob) dispatchJob.flushRuntimeTick();
        if (markDirty) cpu.markDirty();
        if (!anyStatusChange) return;

        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        if (fullStatusChange) {
            for (var listener : listeners) listener.accept(null);
            return;
        }
        for (int resourceId : changedResourceIds) {
            AEKey key = resourceState.keyByResourceId(resourceId);
            for (var listener : listeners) listener.accept(key);
        }
        for (AEKey key : fallbackKeys) {
            for (var listener : listeners) listener.accept(key);
        }
    }

    private void endStatusChangeBatchSafely(ExecutingCraftingJob dispatchJob) {
        try {
            endStatusChangeBatch(dispatchJob);
        } catch (RuntimeException e) {
            batchingStatusChanges = false;
            clearStatusBatchState();
            throw e;
        } catch (Error e) {
            // Error is included so status-batching bookkeeping is reset before the failure escapes.
            batchingStatusChanges = false;
            clearStatusBatchState();
            throw e;
        }
    }

    private void initializeDirtyResourceQueue(@Nullable RuntimeExecutionState runtimeState) {
        clearStatusBatchState();
        dirtyResourceState = runtimeState;
        int resourceCount = runtimeState == null ? 0 : runtimeState.resourceCount();
        dirtyResourceQueue = new int[resourceCount];
        dirtyResourcePresent = new boolean[resourceCount];
        dirtyResourceCount = 0;
    }

    private void clearQueuedStatusChanges() {
        for (int i = 0; i < dirtyResourceCount; i++) {
            dirtyResourcePresent[dirtyResourceQueue[i]] = false;
        }
        dirtyResourceCount = 0;
        fallbackStatusChanges.clear();
    }

    private void clearStatusBatchState() {
        clearQueuedStatusChanges();
        batchedAnyStatusChange = false;
        batchedFullStatusChange = false;
        cpuDirtyRequested = false;
    }

    private void markCpuDirty() {
        if (batchingStatusChanges) {
            cpuDirtyRequested = true;
        } else {
            cpu.markDirty();
        }
    }

    private void postGenericStackKeysChange(List<GenericStack> stacks) {
        for (var stack : stacks) {
            postChange(stack.what());
        }
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
        this.inventory.readFromNBT(data.getList("inventory", 10), registries);
        if (data.contains("job")) {
            providerTopologyCache.clear();
            this.job = new ExecutingCraftingJob(data.getCompound("job"), registries, this::postChange, this);
            initializeRuntimeOwnershipFromPhysicalState(this.job, true);
            initializeDirtyResourceQueue(this.job.runtimeExecutionState());
            if (this.job.finalOutput == null) {
                finishJob(false);
            }
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.put("inventory", this.inventory.writeToNBT(registries));
        if (this.job != null) {
            data.put("job", this.job.writeToNBT(registries));
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
        long stored = this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
        if (job != null && job.finalOutput != null && template.matches(job.finalOutput)) {
            long buffered = job.bufferedFinalOutput.amount();
            return stored > Long.MAX_VALUE - buffered ? Long.MAX_VALUE : stored + buffered;
        }
        return stored;
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
                        count += output.amount() * t.getValue().value;
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
        out.addAll(this.inventory.list);
        if (this.job != null) {
            if (job.finalOutput != null && job.bufferedFinalOutput.amount() > 0L) {
                out.add(job.finalOutput.what(), job.bufferedFinalOutput.amount());
            }
            out.addAll(job.waitingFor.list);
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    out.add(output.what(), output.amount() * t.getValue().value);
                }
            }
        }
    }

    /** Collects only items physically owned by this CPU, excluding planned and in-flight outputs. */
    public void getOwnedItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        if (this.job != null && this.job.finalOutput != null && this.job.bufferedFinalOutput.amount() > 0L) {
            out.add(this.job.finalOutput.what(), this.job.bufferedFinalOutput.amount());
        }
    }

    /** Allocation-free counterpart of {@link #getOwnedItems(KeyCounter)}; must cover the same ledgers. */
    public boolean hasOwnedItems() {
        if (!this.inventory.list.isEmpty()) {
            return true;
        }
        return this.job != null && this.job.finalOutput != null && this.job.bufferedFinalOutput.amount() > 0L;
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    /** Stable diagnostic hook for CPU menus/integrations; null means the job is still executable. */
    public @Nullable String getPermanentExecutionError() {
        return job == null || job.permanentExecutionError == null
            ? null
            : job.permanentExecutionError.name();
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
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
