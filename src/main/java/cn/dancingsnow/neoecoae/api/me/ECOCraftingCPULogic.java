package cn.dancingsnow.neoecoae.api.me;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
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
    enum BatchMode { FINITE, VIRTUAL }

    /** Common batch dispatch entry point; mode-specific implementations retain their capability checks. */
    private DispatchResult dispatchBatch(ExecutingCraftingJob job, ECOExtractedPatternExecution execution,
            KeyCounter[] container, List<ICraftingProvider> providers, IEnergyService energy, double power,
            long remaining, BatchMode mode) {
        long accepted = mode == BatchMode.VIRTUAL
            ? tryPushVerifiedVirtualBatch(job, execution, container, providers, remaining)
            : tryPushVerifiedFastPathBatch(job, execution, container, providers, energy, power, remaining);
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
    private final Set<AEKey> batchedStatusChanges = new HashSet<>();
    private boolean batchedAnyStatusChange = false;
    private boolean batchedFullStatusChange = false;
    private boolean deliveringBufferedFinalOutput = false;
    private long lastFinalOutputDeliveryFailureLogTick = Long.MIN_VALUE;
    private static final long BATCH_REJECTION_LOG_INTERVAL_TICKS = 100L;
    private static final long STALLED_DISPATCH_LOG_INTERVAL_TICKS = 100L;
    private long lastBatchRejectionLogTick = Long.MIN_VALUE;
    private long lastStalledDispatchLogTick = Long.MIN_VALUE;

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
        providerTopologyCache.clear();
        this.lastStalledDispatchLogTick = Long.MIN_VALUE;
        logSubmittedJob(craftId, this.job);
        // A newly submitted job already has pending pattern outputs even when its initial inventory is empty.
        // Publish those keys now; otherwise the status table stays empty until the first machine event, and AE2
        // disables the cancel button because it derives that button from the visible status entries.
        var initialStatusItems = new KeyCounter();
        getAllItems(initialStatusItems);
        for (var entry : initialStatusItems) postChange(entry.getKey());

        // 合成监视器暂不支持
        // cpu.updateOutput(plan.finalOutput());
        cpu.markDirty();

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
        // dispatch path on every tick; retain the job for inspection/cancellation and report the reason once.
        if (job.hasPermanentExecutionError()) {
            logCycleMetadataFailureOnce(job);
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
        if (surplus <= 0L) return;
        long transferable = currentJob.bufferedFinalOutput.accept(surplus, Actionable.SIMULATE);
        if (transferable <= 0L) return;
        long extracted = inventory.extract(key, transferable, Actionable.MODULATE);
        long accepted = currentJob.bufferedFinalOutput.accept(extracted, Actionable.MODULATE);
        if (accepted != extracted) throw new IllegalStateException("Final-output buffer rejected CPU-owned surplus");
        postChange(key);
        cpu.markDirty();
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
        currentJob.remainingAmount = Math.max(0L, currentJob.remainingAmount - accepted);
        postChange(key);
        cpu.markDirty();
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
            logCycleMetadataFailureOnce(job);
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

        beginStatusChangeBatch();
        try {
            List<Map.Entry<IPatternDetails, ExecutingCraftingJob.TaskProgress>> eligibleTasks = job.eligiblePatterns()
                .stream().map(job::taskFor).filter(java.util.Objects::nonNull).toList();
            java.util.Iterator<Map.Entry<IPatternDetails, ExecutingCraftingJob.TaskProgress>> it = eligibleTasks.iterator();
            taskLoop: while (it.hasNext()) {
                var task = it.next();
                if (task.getValue().value <= 0) {
                    postPatternOutputsChange(task.getKey());
                    continue;
                }

                var details = task.getKey();
                diagnostics.runnableTasks++;
                if (job.runtimeExecutionState == null && activePhase != null && !cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler
                        .canDispatch(activePhase, job.cycleWitnessIndex, details)) {
                    diagnostics.phaseRejectedTasks++;
                    continue;
                }
                // Topology is collected once per task: which providers advertise this pattern at all cannot
                // change while we iterate. Live capacity - busy state, free thread slots, coolant, energy - is
                // deliberately NOT part of this list and is re-measured on every attempt below.
                List<ICraftingProvider> candidateProviders = collectCandidateProviders(craftingService, details);
                if (candidateProviders.isEmpty()) {
                    diagnostics.tasksWithoutProviders++;
                    continue;
                }
                if (task.getValue().value > 0 && pushedPatterns < maxPatterns) {
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

                    // The single normalization point for this dispatch: fast-path key, slot signatures, expected
                    // outputs/remainders/inputs and the arithmetic batch ceiling are built exactly once here and
                    // reused by the offer search, the verification and the batch push.
                    ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.create(
                            details, craftingContainer, expectedOutputs, expectedContainerItems, level);

                    var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
                    long batchResult = 0L;
                    long dispatchLimit = job.dispatchLimit(details);
                    DispatchResult batchDispatch = dispatchBatch(job, execution, craftingContainer,
                        candidateProviders, energyService, patternPower, dispatchLimit, BatchMode.VIRTUAL);
                    if (batchDispatch instanceof DispatchResult.Waiting) {
                        batchDispatch = dispatchBatch(job, execution, craftingContainer, candidateProviders,
                            energyService, patternPower, dispatchLimit, BatchMode.FINITE);
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
                        job.applyDispatchResult(details, batchDispatch);
                        postPatternOutputsChange(details);
                        if (task.getValue().value <= 0) {
                            continue taskLoop;
                        }
                        if (pushedPatterns == maxPatterns) {
                            break taskLoop;
                        }
                        continue;
                    } else if (batchResult < 0) {
                        continue taskLoop;
                    }

                    // Keep the ordinary ICraftingProvider invocation in executeCrafting. External integrations
                    // (notably useless_mod's dynamic-output bridge) wrap this exact call site by descriptor.
                    // The strategy only chooses order and attempt budget; this loop retains the transaction.
                    var strategyContext = new ECOCraftingDispatchStrategy.DispatchContext(
                        details,
                        task.getValue().value,
                        Math.max(0, maxPatterns - pushedPatterns),
                        candidateProviders,
                        estimateOrdinaryDispatchSlots(candidateProviders, Math.max(0, maxPatterns - pushedPatterns))
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
                    int ordinaryAttemptLimit = Math.min(strategyDecision.maxAttempts(), strategyContext.dispatchBudget());
                    if (task.getValue().value < ordinaryAttemptLimit) {
                        ordinaryAttemptLimit = (int) task.getValue().value;
                    }
                    List<ICraftingProvider> dispatchProviders = strategyDecision.providers();
                    if (ordinaryAttemptLimit <= 0 || dispatchProviders.isEmpty()) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                        continue taskLoop;
                    }

                    ordinaryDispatch: for (int attempt = 0; attempt < ordinaryAttemptLimit; attempt++) {
                        if (task.getValue().value <= 0 || pushedPatterns >= maxPatterns) {
                            break;
                        }
                        if (!hasAvailableProvider(dispatchProviders)) {
                            diagnostics.tasksWithBusyProviders++;
                            break;
                        }

                        KeyCounter attemptOutputs;
                        KeyCounter attemptContainerItems;
                        @Nullable KeyCounter[] attemptContainer;
                        ECOExtractedPatternExecution attemptExecution;
                        double attemptPower;
                        if (attempt == 0) {
                            // The first craft was already extracted for the shared fast-path offer search. Reuse
                            // that exact container; extracting it a second time would leak one craft on fallback.
                            attemptOutputs = expectedOutputs;
                            attemptContainerItems = expectedContainerItems;
                            attemptContainer = craftingContainer;
                            attemptExecution = execution;
                            attemptPower = patternPower;
                        } else {
                            attemptOutputs = new KeyCounter();
                            attemptContainerItems = new KeyCounter();
                            attemptContainer = CraftingCpuHelper.extractPatternInputs(
                                details, inventory, level, attemptOutputs, attemptContainerItems);
                            attemptExecution = attemptContainer == null ? null : ECOExtractedPatternExecution.create(
                                details, attemptContainer, attemptOutputs, attemptContainerItems, level);
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
                            recordPushedPattern(job, attemptExecution, 1L);
                            single = new DispatchResult.Accepted(1L);
                            break;
                        }
                        if (!sawAvailable) {
                            single = new DispatchResult.Waiting(DispatchResult.WaitReason.PROVIDER_BUSY);
                        }
                        if (single instanceof DispatchResult.Accepted) {
                            pushedPatterns++;
                            if (this.job != job) break taskLoop;
                            job.applyDispatchResult(details, single);
                            postPatternOutputsChange(details);
                            if (task.getValue().value <= 0) continue taskLoop;
                            if (pushedPatterns == maxPatterns) break taskLoop;
                            continue;
                        }
                        CraftingCpuHelper.reinjectPatternInputs(inventory, attemptContainer);
                        break ordinaryDispatch;
                    }
                }
            }
        } finally {
            endStatusChangeBatchSafely();
        }

        if (pushedPatterns == 0) {
            logStalledDispatch(job, activePhase, maxPatterns, diagnostics, "dispatch-returned-zero");
        }
        return pushedPatterns;
    }

    private void logCycleMetadataFailureOnce(ExecutingCraftingJob job) {
        if (job.cycleMetadataErrorLogged) return;
        LOGGER.error("Ordered cycle metadata is missing; refusing cycle dispatch permanently (fail-safe) "
            + "error={} finalOutput={} craftId={} executionMode={} "
            + "componentScheduled={} schedulePresent={} phaseCount={} cyclePhaseCount={}",
            job.permanentExecutionError, job.finalOutput, job.link == null ? null : job.link.getCraftingID(),
            job.executionMode, job.phased(),
            job.executionSchedule != null,
            job.executionSchedule == null ? 0 : job.executionSchedule.phases().size(),
            job.executionSchedule == null ? 0 : job.executionSchedule.phases().stream()
            .filter(phase -> phase.type()
                == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule.Type.CYCLE).count());
        job.cycleMetadataErrorLogged = true;
    }

    private void logSubmittedJob(UUID craftId, ExecutingCraftingJob submittedJob) {
        var schedule = submittedJob.executionSchedule;
        var phases = schedule == null ? List.of() : schedule.phases().stream()
            .map(phase -> phase.componentId() + ":" + phase.type()
                + "(patterns=" + phase.patternSet().size() + ",witness=" + phase.cycleWitness().size() + ")")
            .toList();
        int phaseCount = schedule == null ? 0 : schedule.phases().size();
        long cyclePhaseCount = schedule == null ? 0L : schedule.phases().stream()
            .filter(phase -> phase.type() == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule.Type.CYCLE)
            .count();
        long remainingTasks = remainingTaskCount(submittedJob);
        LOGGER.info(
            "[ECO-EXEC] submitted job={} finalOutput={} remainingOutput={} tasks={} taskExecutions={} "
                + "componentScheduled={} cycleExpected={} metadataMissing={} phaseCount={} cyclePhaseCount={} phases={}",
            craftId,
            submittedJob.finalOutput,
            submittedJob.remainingAmount,
            submittedJob.tasks.size(),
            remainingTasks,
            submittedJob.phased(),
            submittedJob.orderedCycle(),
            submittedJob.permanentExecutionError != null,
            phaseCount,
            cyclePhaseCount,
            phases
        );
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
        LOGGER.warn(
            "[ECO-EXEC] stalled job={} reason={} tick={} maxPatterns={} suspended={} phaseIndex={} phase={} "
                + "witnessIndex={} expectedWitness={} taskKinds={} taskExecutions={} runnableTasks={} "
                + "phaseRejected={} noProviders={} providersBusy={} missingInputs={} energyBlocked={} "
                + "providerRejected={} missingInputPattern={} requiredInputs={} inventoryKinds={} waitingKinds={}",
            stalledJob.link.getCraftingID(),
            reason,
            tick,
            maxPatterns,
            stalledJob.suspended,
            stalledJob.currentComponentIndex,
            phaseDescription,
            stalledJob.cycleWitnessIndex,
            expectedWitness,
            stalledJob.tasks.size(),
            remainingTaskCount(stalledJob),
            diagnostics.runnableTasks,
            diagnostics.phaseRejectedTasks,
            diagnostics.tasksWithoutProviders,
            diagnostics.tasksWithBusyProviders,
            diagnostics.tasksMissingInputs,
            diagnostics.energyBlockedProviders,
            diagnostics.providerRejections,
            missingInputPattern,
            requiredInputs,
            inventory.list.size(),
            stalledJob.waitingFor.list.size()
        );
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
    private List<ICraftingProvider> collectCandidateProviders(CraftingService craftingService,
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

    /** Live availability check over the reusable candidate set; allocates nothing. */
    private boolean hasAvailableProvider(List<ICraftingProvider> candidateProviders) {
        for (int i = 0; i < candidateProviders.size(); i++) {
            if (!candidateProviders.get(i).isBusy()) {
                return true;
            }
        }
        return false;
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
                // Generic providers expose no numeric capacity. Their isBusy() contract is the live gate, so allow
                // the policy to probe up to the remaining CPU budget and stop as soon as the provider is saturated.
                contribution = Math.max(1L, dispatchBudget);
            }
            slots = slots > Integer.MAX_VALUE - contribution
                ? Integer.MAX_VALUE : slots + contribution;
            if (slots >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) slots;
    }

    private int tryPushVerifiedFastPathBatch(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> candidateProviders,
            IEnergyService energyService,
            double patternPower,
            long taskRemaining) {
        if (!canAttemptBatchFastPath(execution) || taskRemaining <= 1) {
            return 0;
        }

        // Ask providers for the full remaining task. The selected F-series host and worker cap the
        // offer to their live thread capacity; inventory, energy and coolant apply further bounds.
        int requested = calculateBatchRequestSize(execution, taskRemaining);
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
        // The credential must have been minted for this very execution context, never for an earlier one.
        if (!selectedOffer.recipe().isVerifiedFor(execution)) {
            return 0;
        }

        ECOCraftingSystemBlockEntity controller = selectedPatternBus.getCraftingController();
        if (controller == null) {
            return 0;
        }

        // A fully virtualized host pays one flat group-wide draw per tick instead of a per-craft charge, so the
        // batch must be neither sized by nor billed for pattern power here.
        boolean flatRatePower = controller.isFullVirtualCraftingMode();

        int batchSize = Math.min(requested, selectedOffer.maxBatchSize());
        if (!flatRatePower) {
            batchSize = Math.min(batchSize, maxBatchSizeFromEnergy(energyService, patternPower, batchSize));
        }
        batchSize = controller.getCraftingCoolantCraftLimit(5, controller.getEffectiveOverclockTimes(), batchSize);
        if (batchSize <= 1) {
            return 0;
        }

        int extraCrafts = batchSize - 1;
        int availableExtraCrafts = ECOBatchCraftingHelper.maxCraftsFromInventory(inventory, execution.inputItems(),
                extraCrafts);
        batchSize = Math.min(batchSize, availableExtraCrafts + 1);
        if (batchSize <= 1) {
            return 0;
        }

        var extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), batchSize - 1);
        boolean extraInputsExtracted = false;
        boolean ownershipTransferred = false;
        try {
            double requiredPower = flatRatePower ? 0.0D : patternPower * batchSize;
            if (!Double.isFinite(requiredPower)) {
                return 0;
            }
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
            extraInputsExtracted = true;
            // Bind the already-verified recipe credential to this batch size. No stack list is re-copied and no
            // stack list is compared again from here on.
            var verified = selectedOffer.recipe().withBatch(batchSize, job.link.getCraftingID());
            if (verified == null || !selectedPatternBus.pushBatch(verified, selectedOffer)) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, true);
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
                    recordPushedPattern(job, execution, batchSize);
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
            rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraInputsExtracted);
            logBatchRejection(batchSize, taskRemaining, e);
            return -1;
        } catch (Error e) {
            // Error is included so extracted inputs are returned before the failure escapes.
            selectedOffer.worker().getFastPathCache().recordException();
            if (!ownershipTransferred) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraInputsExtracted);
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

        long extraRequested = taskRemaining - 1L;
        long availableExtra = ECOBatchCraftingHelper.maxCraftsFromInventory(
            inventory, execution.inputItems(), extraRequested);
        long craftCount = Math.min(taskRemaining, availableExtra + 1L);
        if (craftCount <= 0L) {
            return 0L;
        }
        List<GenericStack> extraInputs;
        try {
            extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), craftCount - 1L);
        } catch (RuntimeException e) {
            return 0L;
        }
        boolean extracted = false;
        boolean ownershipTransferred = false;
        try {
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
            extracted = true;
            var verified = selectedOffer.recipe().withVirtualBatch(craftCount, job.link.getCraftingID());
            if (verified == null || !selectedBus.pushVirtualBatch(verified, selectedOffer)) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, true);
                return -1L;
            }
            ownershipTransferred = true;
            if (this.job == job) {
                recordPushedPattern(job, execution, craftCount);
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
    private int calculateBatchRequestSize(ECOExtractedPatternExecution execution, long taskRemaining) {
        long requested = Math.min(Integer.MAX_VALUE, Math.max(0L, taskRemaining));
        // The arithmetic ceiling was computed once when the execution context was built.
        return (int) Math.min(requested, execution.arithmeticBatchLimit());
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
            job.waitingFor.insert(expectedOutput.what(), Math.multiplyExact(expectedOutput.amount(), multiplier),
                Actionable.MODULATE);
        }
        postGenericStackKeysChange(execution.expectedOutputs());

        for (var expectedContainerItem : execution.expectedContainerItems()) {
            job.waitingFor.insert(
                    expectedContainerItem.what(), Math.multiplyExact(expectedContainerItem.amount(), multiplier),
                    Actionable.MODULATE);
            job.timeTracker.addMaxItems(
                    Math.multiplyExact(expectedContainerItem.amount(), multiplier),
                    expectedContainerItem.what().getType());
        }
        postGenericStackKeysChange(execution.expectedContainerItems());

        cpu.markDirty();
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
            cpu.markDirty();
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
                postChange(what);
                cpu.markDirty();
                drainBufferedFinalOutput(currentJob);
            }
            return acceptedOwnership;
        } else {
            if (type == Actionable.MODULATE) {
                inventory.insert(what, amount, Actionable.MODULATE);
            }
        }

        return amount;
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

        // 结束任务。
        this.job = null;
        providerTopologyCache.clear();

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
            cpu.markDirty();
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
        cpu.markDirty();
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

        cpu.markDirty();
    }

    private void postChange(@Nullable AEKey what) {
        if (batchingStatusChanges) {
            batchedAnyStatusChange = true;
            if (what == null) {
                batchedFullStatusChange = true;
            } else {
                batchedStatusChanges.add(what);
            }
            return;
        }

        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (var listener : listeners) {
            listener.accept(what);
        }
    }

    private void beginStatusChangeBatch() {
        batchingStatusChanges = true;
        batchedStatusChanges.clear();
        batchedAnyStatusChange = false;
        batchedFullStatusChange = false;
    }

    private void endStatusChangeBatch() {
        batchingStatusChanges = false;

        if (!batchedAnyStatusChange) {
            return;
        }

        lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        if (batchedFullStatusChange) {
            batchedStatusChanges.clear();
            batchedAnyStatusChange = false;
            batchedFullStatusChange = false;

            for (var listener : listeners) {
                listener.accept(null);
            }
            return;
        }

        var changedKeys = List.copyOf(batchedStatusChanges);
        batchedStatusChanges.clear();
        batchedAnyStatusChange = false;
        batchedFullStatusChange = false;

        for (AEKey key : changedKeys) {
            for (var listener : listeners) {
                listener.accept(key);
            }
        }
    }

    private void endStatusChangeBatchSafely() {
        try {
            endStatusChangeBatch();
        } catch (RuntimeException e) {
            batchingStatusChanges = false;
            batchedStatusChanges.clear();
            batchedAnyStatusChange = false;
            batchedFullStatusChange = false;
            throw e;
        } catch (Error e) {
            // Error is included so status-batching bookkeeping is reset before the failure escapes.
            batchingStatusChanges = false;
            batchedStatusChanges.clear();
            batchedAnyStatusChange = false;
            batchedFullStatusChange = false;
            throw e;
        }
    }

    private void postPatternOutputsChange(IPatternDetails details) {
        for (var output : details.getOutputs()) {
            postChange(output.what());
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
