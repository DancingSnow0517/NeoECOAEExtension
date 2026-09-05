package cn.dancingsnow.neoecoae.api.me;

import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.chargeAcceptedPatternEnergy;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.consumedInputs;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.reinjectPatternInputs;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.validateRuntimeConsumption;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.estimateOrdinaryDispatchSlots;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.hasAvailableProvider;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.ordinaryProviders;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.paysFlatRateCraftingPower;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import appeng.api.config.PowerMultiplier;
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
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedCraft;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.RuntimeExecutionState;

public class ECOCraftingCPULogic {
    /**
     * Ordinary dispatch is deliberately policy-driven. The default fills currently visible provider capacity;
     * adaptive policies can be installed without touching extraction, rollback or runtime accounting.
     */
    private volatile ECOCraftingDispatchStrategy ordinaryDispatchStrategy = ECOParallelDispatchStrategy.INSTANCE;
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

    private boolean deliveringBufferedFinalOutput = false;
    private long lastFinalOutputDeliveryFailureLogTick = Long.MIN_VALUE;
    private final ECOCraftingProviders providers = new ECOCraftingProviders();
    private final ECOCraftingAccounting accounting;
    private final ECOCraftingBatchDispatcher batchDispatcher;
    private final ECOCraftingStatusChanges statusChanges;

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
        this.statusChanges = new ECOCraftingStatusChanges(this::notifyListeners,
            () -> lastModifiedOnTick = TickHandler.instance().getCurrentTick(), () -> this.cpu.markDirty());
        this.accounting = new ECOCraftingAccounting(this::postChange, this::markCpuDirty);
        this.batchDispatcher = new ECOCraftingBatchDispatcher(this, accounting);
    }

    public ECOCraftingDispatchStrategy getOrdinaryDispatchStrategy() {
        return ordinaryDispatchStrategy;
    }

    /** Installs the ordinary-path scheduling policy used by subsequent engine passes. */
    public void setOrdinaryDispatchStrategy(ECOCraftingDispatchStrategy strategy) {
        this.ordinaryDispatchStrategy = Objects.requireNonNull(strategy, "strategy");
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
        statusChanges.initialize(this.job.runtimeExecutionState());
        providers.clearTopologyCache();
        batchDispatcher.resetBatchProbeBudgetForCurrentTick();
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
            return;
        }

        Level level = cpu.getLevel();
        if (level == null) {
            return;
        }

        var remainingOperations = getOperationLimit();

        if (remainingOperations > 0) {
            // One engine pass is one tick. The pass snapshots eligible task ids; the ordinary dispatch strategy
            // decides how many one-craft provider calls may fill the currently available parallel lanes.
            executeCrafting(remainingOperations, cc, eg, level);
            // Dispatch normally flushed this projection together with status changes. This covers pre-batch exits.
            if (job != null) job.flushRuntimeTick();
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
        markCpuDirty();
        drainBufferedFinalOutput(currentJob);
    }

    private void drainBufferedFinalOutput(ExecutingCraftingJob currentJob) {
        if (job != currentJob || currentJob.finalOutput == null) {
            return;
        }
        if (isFinalOutputSatisfied(currentJob)) {
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
        if (isFinalOutputSatisfied(currentJob)) {
            finishJob(true);
        }
    }

    private boolean isFinalOutputSatisfied(ExecutingCraftingJob currentJob) {
        // The buffer may still own recipe-rounding surplus. finishJob preserves that surplus and stores it normally.
        return currentJob.remainingAmount <= 0L && currentJob.waitingFor.list.isEmpty();
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
        int runnableTasks = 0;
        int tasksMissingInputs = 0;
        if (componentScheduled && activePhase == null) {
            return 0;
        }

        var pushedPatterns = 0;
        // Provider membership is a topology property, but AE2 exposes no stable generation here. Scope the cache to
        // one engine pass so grid changes can never leave stale providers attached to a long-lived job.
        providers.clearTopologyCache();
        batchDispatcher.resetBatchProbeBudgetForCurrentTick();

        statusChanges.beginBatch(job.runtimeExecutionState());
        try {
            List<ExecutingCraftingJob.DispatchTask> readyTasks = job.eligibleDispatchTasks();
            int fairQuantum = readyTasks.isEmpty() ? 0 : Math.max(1, maxPatterns / readyTasks.size());
            List<ExecutingCraftingJob.DispatchTask> eligibleTasks = new ArrayList<>(providers.fairTaskOrder(readyTasks));
            Set<ExecutingCraftingJob.DispatchTask> finiteFastPathStartedTasks =
                Collections.newSetFromMap(new IdentityHashMap<>());
            int taskIndex = 0;
            taskLoop: while (taskIndex < eligibleTasks.size()) {
                var task = eligibleTasks.get(taskIndex++);
                if (task.progress().value <= 0) {
                    continue;
                }

                var details = task.pattern();
                runnableTasks++;
                if (job.runtimeExecutionState == null && activePhase != null && !ECOPhaseScheduler
                        .canDispatch(activePhase, job.cycleWitnessIndex, details)) {
                    continue;
                }
                // Topology is collected once per task: which providers advertise this pattern at all cannot
                // change while we iterate. Live capacity - busy state, free thread slots, coolant, energy - is
                // deliberately NOT part of this list and is re-measured on every attempt below.
                List<ICraftingProvider> candidateProviders = providers.collectAvailableProviders(craftingService, details);
                if (candidateProviders.isEmpty()) {
                    continue;
                }
                if (task.progress().value > 0 && pushedPatterns < maxPatterns) {
                    if (!hasAvailableProvider(candidateProviders)) {
                        continue taskLoop;
                    }

                    var expectedOutputs = new KeyCounter();
                    var expectedContainerItems = new KeyCounter();
                    @Nullable
                    var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                            details, inventory, level, expectedOutputs, expectedContainerItems);
                    if (craftingContainer == null) {
                        tasksMissingInputs++;
                        continue taskLoop;
                    }

                    var extractedCraft = new ECOExtractedCraft(craftingContainer, expectedOutputs,
                        expectedContainerItems, CraftingCpuHelper.calculatePatternPower(craftingContainer));
                    var batch = batchDispatcher.dispatch(job, task, extractedCraft, candidateProviders,
                        energyService, job.dispatchLimit(task), Math.max(0, maxPatterns - pushedPatterns),
                        level, !finiteFastPathStartedTasks.contains(task));
                    var execution = batch.execution();
                    var batchDispatch = batch.result();
                    if (batchDispatch instanceof DispatchResult.Accepted) {
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
                        if (batch.finiteAccepted()) {
                            finiteFastPathStartedTasks.add(task);
                        }
                        if (!batch.virtualAccepted() && this.job == job) {
                            eligibleTasks.add(task);
                        }
                        continue;
                    } else if (batchDispatch instanceof DispatchResult.Rejected) {
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
                        reinjectPatternInputs(inventory, craftingContainer);
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
                            tasksMissingInputs++;
                            break;
                        }

                        final Map<AEKey, Long> actualConsumed;
                        try {
                            actualConsumed = consumedInputs(attemptContainer);
                            validateRuntimeConsumption(job, actualConsumed);
                        } catch (RuntimeException invalidConsumption) {
                            reinjectPatternInputs(inventory, attemptContainer);
                            batchDispatcher.logBatchRejection(1L, task.progress().value, invalidConsumption);
                            break ordinaryDispatch;
                        }
                        DispatchResult single = new DispatchResult.Waiting(DispatchResult.WaitReason.PROVIDER_BUSY);
                        for (ICraftingProvider provider : dispatchProviders) {
                            if (provider.isBusy()) continue;
                            boolean flatRateProvider = paysFlatRateCraftingPower(provider);
                            if (!flatRateProvider && energyService.extractAEPower(attemptPower, Actionable.SIMULATE,
                                    PowerMultiplier.CONFIG) < attemptPower - 0.01) {
                                single = new DispatchResult.Waiting(DispatchResult.WaitReason.ENERGY_UNAVAILABLE);
                                break;
                            }
                            final boolean accepted;
                            try {
                                // This exact call site is part of the integration contract with dynamic-output
                                // provider mixins. Do not move it into a helper without updating those mixins.
                                if (provider instanceof ECOCraftingPatternBusBlockEntity && attemptExecution == null) {
                                    attemptExecution = ECOExtractedPatternExecution.create(details, attemptContainer,
                                        attemptOutputs, attemptContainerItems, level);
                                }
                                accepted = provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                                    ? patternBus.pushPattern(attemptExecution, job.link.getCraftingID())
                                    : provider.pushPattern(details, attemptContainer);
                            } catch (RuntimeException failure) {
                                LOGGER.error("Crafting provider rejected a pattern with an exception; CPU retains inputs",
                                    failure);
                                continue;
                            }
                            if (!accepted) {
                                single = new DispatchResult.Rejected(DispatchResult.RejectReason.PROVIDER_REJECTED);
                                continue;
                            }
                            if (!flatRateProvider) chargeAcceptedPatternEnergy(energyService, attemptPower);
                            accounting.recordPushedPattern(job, attemptOutputs, attemptContainerItems);
                            single = new DispatchResult.Accepted(1L, actualConsumed);
                            break;
                        }
                        if (single instanceof DispatchResult.Accepted) {
                            pushedPatterns++;
                            if (this.job != job) break taskLoop;
                            eligibleTasks.addAll(job.applyDispatchResultAndGetNewlyReady(task, single));
                            if (task.progress().value <= 0) continue taskLoop;
                            if (pushedPatterns == maxPatterns) break taskLoop;
                            continue;
                        }
                        reinjectPatternInputs(inventory, attemptContainer);
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
            statusChanges.endBatch(this.job == job ? job::flushRuntimeTick : null);
        }

        job.recordDynamicCyclePass(pushedPatterns > 0,
            runnableTasks > 0 && tasksMissingInputs == runnableTasks);
        return pushedPatterns;
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
                ExecutingCraftingJob currentJob = job;
                inventory.insert(what, amount, Actionable.MODULATE);
                long accepted = amount;
                if (currentJob.runtimeExecutionState() != null) {
                    currentJob.runtimeExecutionState().acceptOutput(what, accepted);
                }
                if (job == currentJob && isFinalOutputSatisfied(currentJob)) {
                    finishJob(true);
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
        providers.clearTopologyCache();
        if (!statusChanges.isBatching()) statusChanges.initialize(null);

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
        statusChanges.postChange(what);
    }

    private void notifyListeners(@Nullable AEKey what) {
        for (var listener : listeners) listener.accept(what);
    }

    private void markCpuDirty() {
        statusChanges.markDirty();
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
            providers.clearTopologyCache();
            this.job = new ExecutingCraftingJob(data.getCompound("job"), registries, this::postChange, this);
            initializeRuntimeOwnershipFromPhysicalState(this.job, true);
            statusChanges.initialize(this.job.runtimeExecutionState());
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
