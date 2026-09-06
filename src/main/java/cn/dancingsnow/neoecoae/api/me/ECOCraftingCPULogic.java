package cn.dancingsnow.neoecoae.api.me;

import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.chargeAcceptedPatternEnergy;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.consumedInputs;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.reinjectPatternInputs;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingAccounting.validateRuntimeConsumption;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.ordinaryProviders;
import static cn.dancingsnow.neoecoae.api.me.ECOCraftingProviders.paysFlatRateCraftingPower;

import java.util.ArrayList;
import java.util.HashSet;
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
import appeng.api.crafting.IPatternDetails;
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
     * Retained for compatibility with integrations that inspect the ordinary dispatch policy. The built-in ordinary
     * loop currently follows AdvancedAE's one-pass-per-provider baseline; FastPath and accounting remain owned by
     * this CPU logic.
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
            // Match AE2/AdvancedAE's scheduler cadence: one pass visits each task once and each provider at most
            // once, then another pass starts from the first task while the CPU operation budget remains. This keeps
            // provider order observable and lets a provider's live busy state decide whether it can receive another
            // craft on the next pass.
            do {
                int pushedPatterns = executeCrafting(remainingOperations, cc, eg, level);
                if (pushedPatterns <= 0) {
                    break;
                }
                remainingOperations -= pushedPatterns;
            } while (remainingOperations > 0 && job != null);
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

    private List<ICraftingProvider> collectAvailableProviders(CraftingService craftingService,
            IPatternDetails details) {
        // Binary Mixin contract: Thunderbolt 1.0.6 wraps this exact invocation in this method.
        // Keep exactly one getProviders call here; a forwarding stub in the CPU is not sufficient.
        List<ICraftingProvider> result = new ArrayList<>();
        for (ICraftingProvider provider : craftingService.getProviders(details)) {
            result.add(provider);
        }
        return result;
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
        batchDispatcher.resetBatchProbeBudgetForCurrentTick();

        statusChanges.beginBatch(job.runtimeExecutionState());
        try {
            List<ExecutingCraftingJob.DispatchTask> readyTasks = job.eligibleDispatchTasks();
            // Keep the task order supplied by the job. Native jobs therefore retain the same HashMap iteration
            // order as AdvancedAE, while runtime plans retain their compiled ready-task order without an extra
            // rotating queue or task requeue policy.
            List<ExecutingCraftingJob.DispatchTask> eligibleTasks = new ArrayList<>(readyTasks);
            int taskIndex = 0;
            taskLoop: while (taskIndex < eligibleTasks.size()) {
                var task = eligibleTasks.get(taskIndex++);
                if (task.progress().value <= 0) {
                    continue;
                }

                var details = task.pattern();
                runnableTasks++;
                if (!job.runtimeSchedulingDegraded() && job.runtimeExecutionState == null && activePhase != null && !ECOPhaseScheduler
                        .canDispatch(activePhase, job.cycleWitnessIndex, details)) {
                    continue;
                }
                // AdvancedAE obtains the provider list for every task pass and traverses it in the service order.
                // Do the same here; live busy state is still read immediately before every provider call.
                List<ICraftingProvider> candidateProviders = collectAvailableProviders(craftingService, details);
                if (candidateProviders.isEmpty()) {
                    continue;
                }
                if (task.progress().value > 0 && pushedPatterns < maxPatterns) {
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
                        level, true);
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
                        continue;
                    }
                    if (batchDispatch instanceof DispatchResult.Rejected) {
                        // Batch dispatchers return the first craft to the CPU before reporting rejection. Re-extract
                        // a fresh ordinary-path container so the fallback cannot double-inject the same inputs.
                        expectedOutputs = new KeyCounter();
                        expectedContainerItems = new KeyCounter();
                        craftingContainer = CraftingCpuHelper.extractPatternInputs(
                            details, inventory, level, expectedOutputs, expectedContainerItems);
                        if (craftingContainer == null) {
                            tasksMissingInputs++;
                            continue taskLoop;
                        }
                        extractedCraft = new ECOExtractedCraft(craftingContainer, expectedOutputs,
                            expectedContainerItems, CraftingCpuHelper.calculatePatternPower(craftingContainer));
                        execution = ECOExtractedPatternExecution.create(details, craftingContainer,
                            expectedOutputs, expectedContainerItems, level);
                    }

                    // Keep the ordinary ICraftingProvider invocation in executeCrafting. External integrations
                    // (notably useless_mod's dynamic-output bridge) wrap this exact call site by descriptor.
                    // Match AdvancedAE: traverse providers once, and let each provider accept at most one craft in
                    // this pass. The outer tick loop starts the next pass from the first task.
                    List<ICraftingProvider> ordinaryCandidates = ordinaryProviders(candidateProviders);
                    var strategyContext = new ECOCraftingDispatchStrategy.DispatchContext(
                        details,
                        task.progress().value,
                        Math.max(0, maxPatterns - pushedPatterns),
                        ordinaryCandidates,
                        ordinaryCandidates.size()
                    );
                    ECOCraftingDispatchStrategy.DispatchDecision strategyDecision;
                    try {
                        strategyDecision = ordinaryDispatchStrategy.choose(strategyContext);
                        if (strategyDecision == null) {
                            throw new IllegalStateException("ordinary dispatch strategy returned null");
                        }
                    } catch (RuntimeException strategyFailure) {
                        LOGGER.error("Ordinary crafting dispatch strategy failed; using provider-order fallback",
                            strategyFailure);
                        strategyDecision = new ECOCraftingDispatchStrategy.DispatchDecision(
                            ordinaryCandidates, ordinaryCandidates.size());
                    }
                    List<ICraftingProvider> dispatchProviders = strategyDecision.providers();
                    long runtimeLimit = job.dispatchLimit(task);
                    int ordinaryAttemptLimit = (int) Math.min(
                        Math.min((long) strategyDecision.maxAttempts(), (long) dispatchProviders.size()),
                        Math.min((long) Math.max(0, maxPatterns - pushedPatterns),
                            Math.min(task.progress().value, runtimeLimit)));
                    if (ordinaryAttemptLimit <= 0 || dispatchProviders.isEmpty()) {
                        reinjectPatternInputs(inventory, craftingContainer);
                        continue taskLoop;
                    }
                    @Nullable KeyCounter[] pendingContainer = craftingContainer;
                    KeyCounter pendingOutputs = extractedCraft.expectedOutputs();
                    KeyCounter pendingContainerItems = extractedCraft.expectedContainerItems();
                    double pendingPower = extractedCraft.patternPower();
                    boolean acceptedAny = false;
                    for (int providerIndex = 0; providerIndex < ordinaryAttemptLimit; providerIndex++) {
                        ICraftingProvider provider = dispatchProviders.get(providerIndex);
                        if (task.progress().value <= 0 || pushedPatterns >= maxPatterns || pendingContainer == null) {
                            break;
                        }
                        final Map<AEKey, Long> pendingConsumed;
                        try {
                            pendingConsumed = consumedInputs(pendingContainer);
                            validateRuntimeConsumption(job, pendingConsumed);
                        } catch (RuntimeException invalidConsumption) {
                            reinjectPatternInputs(inventory, pendingContainer);
                            pendingContainer = null;
                            batchDispatcher.logBatchRejection(1L, task.progress().value, invalidConsumption);
                            break;
                        }
                        if (provider.isBusy()) {
                            continue;
                        }
                        boolean flatRateProvider = paysFlatRateCraftingPower(provider);
                        if (!flatRateProvider && energyService.extractAEPower(pendingPower, Actionable.SIMULATE,
                                PowerMultiplier.CONFIG) < pendingPower - 0.01) {
                            break;
                        }
                        // Reuse the extraction only while it is still the first craft. Every later provider gets a
                        // freshly extracted container; reusing the first execution would hand a provider stale
                        // inputs after the previous provider accepted its craft.
                        @Nullable ECOExtractedPatternExecution pendingExecution = acceptedAny ? null : execution;
                        final boolean accepted;
                        try {
                            // This exact call site is part of the integration contract with dynamic-output provider
                            // mixins. Do not move it into a helper without updating those mixins.
                            if (provider instanceof ECOCraftingPatternBusBlockEntity && pendingExecution == null) {
                                pendingExecution = ECOExtractedPatternExecution.create(details, pendingContainer,
                                    pendingOutputs, pendingContainerItems, level);
                            }
                            accepted = provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                                ? patternBus.pushPattern(pendingExecution, job.link.getCraftingID())
                                : provider.pushPattern(details, pendingContainer);
                        } catch (RuntimeException failure) {
                            LOGGER.error("Crafting provider rejected a pattern with an exception; CPU retains inputs",
                                failure);
                            continue;
                        }
                        if (!accepted) {
                            continue;
                        }
                        // The provider owns this exact extraction from the successful call onward. Clear the
                        // CPU-owned reference before any task bookkeeping can finish the job or exhaust the pass;
                        // otherwise the final fallback below would inject already-transferred inputs a second time.
                        pendingContainer = null;
                        if (!flatRateProvider) chargeAcceptedPatternEnergy(energyService, pendingPower);
                        accounting.recordPushedPattern(job, pendingOutputs, pendingContainerItems);
                        DispatchResult single = new DispatchResult.Accepted(1L, pendingConsumed);
                        pushedPatterns++;
                        acceptedAny = true;
                        if (this.job != job) break taskLoop;
                        job.applyDispatchResultAndGetNewlyReady(task, single);
                        if (task.progress().value <= 0 || pushedPatterns >= maxPatterns) {
                            break;
                        }

                        // A provider call owns this craft now. Prepare the next craft only for the next provider in
                        // this pass, exactly like AdvancedAE's provider loop.
                        KeyCounter nextOutputs = new KeyCounter();
                        KeyCounter nextContainerItems = new KeyCounter();
                        pendingContainer = CraftingCpuHelper.extractPatternInputs(
                            details, inventory, level, nextOutputs, nextContainerItems);
                        pendingOutputs = nextOutputs;
                        pendingContainerItems = nextContainerItems;
                        pendingPower = pendingContainer == null
                            ? 0.0D : CraftingCpuHelper.calculatePatternPower(pendingContainer);
                    }
                    if (this.job == job && pendingContainer != null) {
                        // The final unaccepted craft remains CPU-owned after the last provider was tried. If the
                        // accepted craft finished or replaced the job, the provider already owns this container and
                        // it must never be re-injected here.
                        reinjectPatternInputs(inventory, pendingContainer);
                    }
                }
            }
        } finally {
            statusChanges.endBatch(this.job == job ? job::flushRuntimeTick : null);
        }

        job.recordDynamicCyclePass(pushedPatterns > 0,
            runnableTasks > 0 && tasksMissingInputs == runnableTasks);
        job.recordRuntimeSchedulingPass(pushedPatterns > 0,
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
