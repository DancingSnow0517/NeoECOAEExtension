package cn.dancingsnow.neoecoae.api.me;

import java.util.ArrayDeque;
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
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.core.network.ClientboundPacket;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.inv.ICraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.compat.extendedae.ExtendedAEPlusCraftingPlanCompat;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchEnergyReservation;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStage;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOReusableCraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.execution.ECOFuzzyCraftingInventory;
import cn.dancingsnow.neoecoae.impl.crafting.processingbatch.ECOProcessingBatchAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.processingbatch.ECOProcessingBatchAdmission;
import cn.dancingsnow.neoecoae.impl.crafting.processingbatch.ECOProcessingBatchDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2InputSelection;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOPlannedInputs;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOSelectedInputPatternDetails;
import cn.dancingsnow.neoecoae.integration.ae2lt.AE2LTBatchCraftingBridge;
import cn.dancingsnow.neoecoae.integration.megacells.MEGACellsBatchCraftingBridge;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingCPULogic {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_INPUT_EXTRACTION_ATTEMPTS = 5;
    private static final int MAX_MISSING_INPUT_DIAGNOSTICS = 16;
    private static final int MAX_INPUT_CANDIDATE_DIAGNOSTICS = 8;
    private static final String NBT_MANUAL_WAITING = "manualWaiting";
    private static final String NBT_PENDING_WORKER_RECOVERIES = "pendingWorkerRecoveries";
    private static final String NBT_PENDING_WORKER_RELEASES = "pendingWorkerReleases";
    private static final Map<UUID, ECOCraftingCPULogic> JOB_OUTPUT_ROUTES = new ConcurrentHashMap<>();
    private static final Map<CraftingService, SlowPathNetworkBudget> SLOW_PATH_NETWORK_BUDGETS =
        Collections.synchronizedMap(new WeakHashMap<>());

    final ECOCraftingCPU cpu;
    private final AE2LTBatchCraftingBridge ae2ltBatchBridge = new AE2LTBatchCraftingBridge();
    private final MEGACellsBatchCraftingBridge megacellsBatchBridge = new MEGACellsBatchCraftingBridge();

    /**
     * 当前合成任务。
     */
    @Getter
    private ExecutingCraftingJob job = null;
    /**
     * 库存。
     */
    @Getter
    private final ListCraftingInventory inventory = new ListCraftingInventory(ECOCraftingCPULogic.this::postInventoryChange);
    /** Missing inputs accepted by ExtendedAE Plus's forced-start plan. */
    private final ListCraftingInventory manualWaitingFor = new ListCraftingInventory(ECOCraftingCPULogic.this::postStatusChange);
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    /** Worker job ids waiting for a loaded grid/pattern bus before their in-flight state is recovered. */
    private final Set<UUID> pendingWorkerRecoveries = new HashSet<>();
    /** Successful jobs whose worker output release still needs a loaded grid. */
    private final Set<UUID> pendingWorkerReleases = new HashSet<>();
    /** Patterns that failed first-input extraction at the current inventory revision. */
    private final IdentityHashMap<IPatternDetails, Long> inputExtractionBlockedRevisions =
        new IdentityHashMap<>();
    /** Snapshot of the local CPU inventory immediately after the current job reservation. */
    private final KeyCounter initialReservedItems = new KeyCounter();
    private boolean initialReservationKnown;
    private long inventoryStateRevision;
    private long statusStateRevision;
    private final TaskScheduler<IPatternDetails> taskScheduler = new TaskScheduler<>();
    private final IdentityHashMap<IPatternDetails, Map<AEKey, Long>> pendingInputByTask =
        new IdentityHashMap<>();
    private final Map<AEKey, Long> pendingInputAmounts = new LinkedHashMap<>();
    private final Map<net.minecraft.resources.ResourceLocation, Long> pendingFuzzyInputAmounts =
        new LinkedHashMap<>();
    private boolean pendingInputIndexKnown;
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
    private boolean applyingPendingAccounting = false;
    private long lastFinalOutputDeliveryFailureLogTick = Long.MIN_VALUE;
    private long lastAccountingRecoveryFailureLogTick = Long.MIN_VALUE;
    private final IdentityHashMap<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>>
        slowPathDeferredProviders = new IdentityHashMap<>();
    private long slowPathDeferredProvidersTick = Long.MIN_VALUE;
    @Nullable
    private SlowPathPushBudget tickSlowPathPushBudget;
    private final ECOCraftingDiagnostics diagnostics;

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
        this.diagnostics = new ECOCraftingDiagnostics(cpu);
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
        // Do not consume execution metadata until reservation succeeds: the cluster can retry this
        // plan on another threading core after a local submission failure.
        ICraftingPlan plannedInputPlan = ExtendedAEPlusCraftingPlanCompat.unwrap(plan);
        KeyCounter manualMissing = ExtendedAEPlusCraftingPlanCompat.getManualMissingItems(plan);
        Set<net.minecraft.resources.ResourceLocation> fuzzyItemIds = ECOPlannedInputs.peekFuzzyItemIds(plannedInputPlan);
        initialReservedItems.clear();
        initialReservationKnown = false;
        var missingIngredient = ECOFuzzyCraftingInventory.tryExtractInitialItems(
            plan, grid, inventory, src, fuzzyItemIds);
        if (missingIngredient != null)
            return CraftingSubmitResult.missingIngredient(missingIngredient);
        initialReservedItems.addAll(inventory.list);
        initialReservationKnown = true;
        ECOFastPathDiagnostics.logCpuReservation(
            plan,
            inventory.list,
            cpu.getOwner() == null ? net.minecraft.core.BlockPos.ZERO : cpu.getOwner().getBlockPos(),
            TickHandler.instance().getCurrentTick()
        );

        // 设置 CPU 链接与任务。
        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new ExecutingCraftingJob(
            plan,
            this::postChange,
            linkCpu,
            playerId,
            ECOPlannedInputs.takeFuzzyItemIds(plannedInputPlan),
            plannedInputPlan
        );
        inputExtractionBlockedRevisions.clear();
        rebuildTaskScheduler();
        rebuildPendingInputIndex();
        setManualWaiting(manualMissing);
        registerJobOutputRoute();

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
        // A provider can temporarily reject an energy injection while a rollback is in flight.
        // Retry those remainders before doing more CPU work so the extracted energy remains tracked
        // even when the original reservation object has already left the local call stack.
        ECOBatchEnergyReservation.retryPendingRefunds(eg);
        retryPendingWorkerRecoveries();
        retryPendingWorkerReleases();
        // Tick only active CPUs.
        if (!cpu.isActive())
            return;
        long cpuTick = TickHandler.instance().getCurrentTick();
        diagnostics.startTickTiming(cpuTick, this.job == null ? 0 : this.job.tasks.size());
        try {
            cantStoreItems = false;
            // With no job, only try to store local items.
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
            // Check whether the job was canceled.
            if (job.link.isCanceled()) {
                cancel();
                return;
            }

            if (job.pendingAccounting() != null && !applyPendingAccounting(job)) {
                return;
            }

            long outputRetryStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
            retryBufferedFinalOutput();
            diagnostics.recordOutputRetry(elapsedMicros(outputRetryStarted, true));
            if (job == null) {
                return;
            }

            // 暂停时不调度更多工作
            if (job.pendingAccounting() != null && !applyPendingAccounting(job)) {
                return;
            }
            if (job.suspended || job.pendingAccounting() != null) {
                return;
            }

            long schedulerStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
            var remainingOperations = getOperationLimit();
            tickSlowPathPushBudget = new SlowPathPushBudget(cc);
            diagnostics.recordScheduler(elapsedMicros(schedulerStarted, true));

            try {
                if (remainingOperations > 0) {
                    do {
                        var pushedPatterns = executeCrafting(remainingOperations, cc, eg, cpu.getLevel());

                        if (pushedPatterns > 0) {
                            remainingOperations -= pushedPatterns;
                            if (this.job == null || this.job.suspended || this.job.pendingAccounting() != null) {
                                break;
                            }
                        } else {
                            break;
                        }
                    } while (remainingOperations > 0);
                }
            } finally {
                tickSlowPathPushBudget = null;
            }
        } finally {
            diagnostics.endTickTiming();
        }
    }

    private void retryBufferedFinalOutput() {
        ExecutingCraftingJob currentJob = job;
        if (currentJob == null) {
            return;
        }
        drainBufferedFinalOutput(currentJob);
    }

    private void drainBufferedFinalOutput(ExecutingCraftingJob currentJob) {
        if (job != currentJob || currentJob.finalOutput == null) {
            return;
        }
        if (canFinishJob(currentJob)) {
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
        if (canFinishJob(currentJob)) {
            finishJob(true);
        }
    }

    private static boolean canFinishJob(ExecutingCraftingJob currentJob) {
        return isFinalOutputSatisfied(currentJob.remainingAmount)
            && currentJob.tasks.isEmpty()
            && currentJob.pendingAccounting() == null
            && currentJob.retainedFinalOutputAmount() <= 0L;
    }

    static boolean isFinalOutputSatisfied(long remainingAmount) {
        // The buffer may still own recipe-rounding surplus. finishJob preserves that surplus and stores it normally.
        return remainingAmount <= 0L;
    }

    private int getOperationLimit() {
        return calculateOperationLimit(cpu.getCoProcessors(), NEConfig.ecoCpuPushTickLimit);
    }

    private void setManualWaiting(@Nullable KeyCounter manualMissing) {
        this.manualWaitingFor.clear();
        if (manualMissing == null) {
            return;
        }

        for (var entry : manualMissing) {
            if (entry.getKey() != null && entry.getLongValue() > 0L) {
                this.manualWaitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
        }
        if (!this.manualWaitingFor.list.isEmpty()) {
            this.cpu.markDirty();
        }
    }

    static int calculateOperationLimit(int coProcessors, int configuredLimit) {
        long baseLimit = (long) Math.max(0, coProcessors) + 1L;
        long safeConfiguredLimit = Math.max(0, configuredLimit);
        return (int) Math.min(Integer.MAX_VALUE, Math.min(baseLimit, safeConfiguredLimit));
    }

    /**
     * 尝试将 pattern 推送到可用接口中，即执行实际的合成操作。
     *
     * @return 成功推送的 pattern 数量。
     */
    public int executeCrafting(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        SlowPathPushBudget slowPathPushBudget = tickSlowPathPushBudget != null
            ? tickSlowPathPushBudget
            : new SlowPathPushBudget(craftingService);
        var job = this.job;
        if (job == null)
            return 0;

        long currentTick = TickHandler.instance().getCurrentTick();
        long setupStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
        ae2ltBatchBridge.beginTick(currentTick);
        megacellsBatchBridge.beginTick(currentTick);
        beginSlowPathProviderTick(currentTick);
        var pushedPatterns = 0;

        beginStatusChangeBatch();
        diagnostics.recordSetup(elapsedMicros(setupStarted, true));
        Map<AEKey, Long> pendingInputSnapshot = null;
        InputDiagnosticContext inputDiagnosticContext = null;
        try {
            taskScheduler.beginTick(currentTick);
            int taskPollBudget = (int) Math.max(
                32L, Math.min(4096L, Math.max(1L, (long) maxPatterns) * 8L));
            int taskPolls = 0;
            taskLoop: while (taskPolls++ < taskPollBudget) {
                long taskIterationStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                taskScheduler.releaseLeasedIfUnresolved();
                IPatternDetails nextDetails = taskScheduler.poll();
                if (nextDetails == null) {
                    break;
                }
                var progress = job.tasks.get(nextDetails);
                if (progress == null) {
                    continue;
                }
                var task = Map.entry(nextDetails, progress);
                if (task.getValue().value <= 0) {
                    postPatternOutputsChange(task.getKey());
                    removeTask(task.getKey());
                    diagnostics.recordTaskIteration(elapsedMicros(taskIterationStarted, true));
                    continue;
                }
                diagnostics.recordTaskIteration(elapsedMicros(taskIterationStarted, true));

                var details = task.getKey();
                // 同一调度轮次内按任务收集一次提供者列表，避免每次推送都重建列表并重复查询。
                long schedulerStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                List<ICraftingProvider> providers = collectAvailableProviders(craftingService, details);
                diagnostics.recordScheduler(elapsedMicros(schedulerStarted, true));
                if (providers.isEmpty()) {
                    taskScheduler.deferUntilNextTick(details);
                    continue;
                }
                schedulerStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                List<ECOCraftingPatternBusBlockEntity> patternBuses = collectPatternBuses(providers);
                diagnostics.recordScheduler(elapsedMicros(schedulerStarted, true));
                // FastPath 元数据只有 ECO 智能样板总线能够消费；纯第三方提供者不应支付其构建成本。
                boolean fastPathCandidate = !patternBuses.isEmpty();

                while (task.getValue().value > 0 && pushedPatterns < maxPatterns) {
                    long dependencyStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                    boolean readyProvider = hasReadyProvider(providers, details);
                    diagnostics.recordDependency(elapsedMicros(dependencyStarted, true));
                    if (!readyProvider) {
                        taskScheduler.deferUntilNextTick(details);
                        continue taskLoop;
                    }

                    dependencyStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                    @Nullable List<ECOAE2InputSelection> plannedInputs = job.peekPlannedInputs(details);
                    long plannedInputCount = plannedInputs == null
                        ? 0L
                        : job.peekPlannedInputCount(details);
                    // ECO workers use the CPU's live inventory and the complete remaining task.
                    // Planned selections are planner bookkeeping and may describe an exact key
                    // that is no longer identical to the reserved stack's components.
                    boolean usePlannedInputs = shouldUsePlannedInputsForDispatch(
                        fastPathCandidate,
                        plannedInputs != null,
                        task.getValue().value,
                        plannedInputCount
                    );
                    @Nullable ECOSelectedInputPatternDetails selectedDetails = null;
                    if (usePlannedInputs) {
                        try {
                            selectedDetails = ECOSelectedInputPatternDetails.resolve(
                                details, plannedInputs, inventory, job.fuzzyItemIds
                            );
                        } catch (IllegalStateException unavailableSelection) {
                            // The reservation and the live CPU inventory can diverge after a
                            // restart or provider-side mutation. Drop the stale planned segment
                            // and let the strict AE2 path re-evaluate the original pattern.
                            discardPlannedInputs(details);
                            diagnostics.recordDependency(elapsedMicros(dependencyStarted, true));
                            continue taskLoop;
                        }
                    }
                    diagnostics.recordDependency(elapsedMicros(dependencyStarted, true));
                    long taskStateStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                    boolean runtimeInputFallback = plannedInputs != null && !usePlannedInputs;
                    IPatternDetails extractionDetails = selectedDetails == null ? details : selectedDetails;
                    IPatternDetails executionDetails = selectedDetails != null && fastPathCandidate
                        ? selectedDetails.asMolecularPattern()
                        : details;
                    long batchTaskRemaining = fastPathCandidate || plannedInputs == null
                        ? task.getValue().value
                        : usePlannedInputs ? Math.min(task.getValue().value, plannedInputCount)
                            : task.getValue().value;
                    if (!shouldRetryInputExtraction(
                        inputExtractionBlockedRevisions.get(details), inventoryStateRevision)) {
                        diagnostics.recordTaskState(elapsedMicros(taskStateStarted, true));
                        taskScheduler.block(details, dependenciesFor(details));
                        continue taskLoop;
                    }
                    ICraftingInventory executionInventory = inventory;
                    KeyCounter[] craftingContainer = null;
                    KeyCounter expectedOutputs = new KeyCounter();
                    KeyCounter expectedContainerItems = new KeyCounter();
                    int inputExtractionAttempts = 0;
                    diagnostics.recordTaskState(elapsedMicros(taskStateStarted, true));
                    dependencyStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                    // A failed extraction does not change the CPU inventory. An ECO candidate can
                    // classify the missing input immediately instead of repeating the same failed
                    // reservation five times in one pass; retain the legacy retry budget for the
                    // ordinary AE2 path.
                    int maxExtractionAttempts = fastPathCandidate
                        ? 1
                        : MAX_INPUT_EXTRACTION_ATTEMPTS;
                    while (craftingContainer == null
                            && inputExtractionAttempts < maxExtractionAttempts) {
                        inputExtractionAttempts++;
                        // A failed AE2 extraction reinjects its partial holder. Recreate the expected
                        // output/container counters so a retry cannot retain diagnostics from a partial attempt.
                        expectedOutputs = new KeyCounter();
                        expectedContainerItems = new KeyCounter();
                        craftingContainer = CraftingCpuHelper.extractPatternInputs(
                            extractionDetails, executionInventory, level, expectedOutputs, expectedContainerItems);
                    }
                    diagnostics.recordDependency(elapsedMicros(dependencyStarted, true));
                    if (craftingContainer == null) {
                        inputExtractionBlockedRevisions.put(details, inventoryStateRevision);
                        taskScheduler.block(details, dependenciesFor(details));
                        if (fastPathCandidate && NEConfig.debugEcoFastPath) {
                            if (pendingInputSnapshot == null) {
                                long snapshotStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                                pendingInputSnapshot = buildPendingInputAmounts(job);
                                diagnostics.recordPendingInputSnapshot(elapsedMicros(snapshotStarted, true));
                            }
                            if (inputDiagnosticContext == null) {
                                inputDiagnosticContext = new InputDiagnosticContext(
                                    pendingInputSnapshot, inventory, job);
                            }
                            long diagnosticStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                            MissingPatternInputDiagnostic diagnostic = describeMissingPatternInputs(
                                extractionDetails, level, inputDiagnosticContext
                            );
                            diagnostics.recordInputDiagnostic(elapsedMicros(diagnosticStarted, true));
                            // A downstream task can legitimately wait for an upstream task's output or
                            // for that producer to dispatch it. Neither state is a FastPath failure.
                            if (diagnostic.result() == InputReservationResult.DEPENDENCY_WAIT) {
                                continue taskLoop;
                            }
                            ECOFastPathDiagnostics.logCpuPreflightFailure(
                                details,
                                cpu.getOwner() == null ? net.minecraft.core.BlockPos.ZERO : cpu.getOwner().getBlockPos(),
                                currentTick,
                                task.getValue().value,
                                plannedInputs != null,
                                plannedInputCount,
                                inputExtractionAttempts,
                                diagnostic.description()
                            );
                        }
                        continue taskLoop;
                    }
                    long patternPreparationStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                    inputExtractionBlockedRevisions.remove(details);
                    if (selectedDetails != null) {
                        craftingContainer = selectedDetails.collapseInputHolder(craftingContainer);
                    }
                    consumeRetainedFinalOutputFrom(craftingContainer);

                    ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.create(
                            executionDetails, craftingContainer, expectedOutputs, expectedContainerItems, level,
                            fastPathCandidate);

                    // A normal CPU evaluates the remaining item against each concrete input. A
                    // FastPath batch snapshots that result from its first craft, so it cannot
                    // faithfully reproduce a component-dependent container transformation for a
                    // later fuzzy component variant. Keep those patterns on AE2's per-craft path.
                    boolean fuzzyBatchSafe = canBatchConfiguredFuzzyInputs(execution, job.fuzzyItemIds);

                    // Apply final output demand limit for ALL batch dispatch paths (FastPath and fallback).
                    // This prevents over-delivery when recipes produce multiple final items per craft.
                    // The limit must be applied BEFORE any batch dispatch attempt.
                    long maxNeededForFinalOutput = maxCraftsNeededForFinalOutput(execution);
                    long limitedBatchTaskRemaining = Math.min(batchTaskRemaining, maxNeededForFinalOutput);

                    var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer)
                        * cpu.getCluster().getNetworkPowerMultiplier();
                    diagnostics.recordPatternPreparation(elapsedMicros(patternPreparationStarted, true));
                    long fastPathStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                    long fastPathApplyBaseline = diagnostics.isTimingEnabled()
                        ? ECOFastPathDiagnostics.currentTickApplyMicros(currentTick)
                        : 0L;
                    long batchResult = fuzzyBatchSafe
                        ? tryPushVerifiedFastPathBatch(
                            job,
                            details,
                            execution,
                            craftingContainer,
                            patternBuses,
                            energyService,
                            patternPower,
                            limitedBatchTaskRemaining,
                            job.fuzzyItemIds,
                            runtimeInputFallback)
                        : 0L;
                    if (diagnostics.isTimingEnabled() && fuzzyBatchSafe && batchResult <= 0L) {
                        long fallbackElapsed = elapsedMicros(fastPathStarted, true);
                        diagnostics.recordFallback(fallbackElapsed);
                        diagnostics.recordFallbackFastPath(fallbackElapsed);
                        diagnostics.incrementFastPathFallback();
                    } else if (diagnostics.isTimingEnabled()) {
                        long applyMicros = Math.max(
                            0L,
                            ECOFastPathDiagnostics.currentTickApplyMicros(currentTick)
                                - fastPathApplyBaseline
                        );
                        diagnostics.recordFastPathCoordination(
                            Math.max(0L, elapsedMicros(fastPathStarted, true) - applyMicros)
                        );
                    }
                    if (batchResult > 0) {
                        long accountingStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                        try {
                            // One provider dispatch consumes one CPU scheduling operation regardless of how many
                            // crafts the F-series host accepted in that batch.
                            pushedPatterns++;
                            if (this.job != job) {
                                break taskLoop;
                            }
                            pendingInputSnapshot = null;
                            mergeInputDiagnosticContext(inputDiagnosticContext);
                            inputDiagnosticContext = null;
                            if (task.getValue().value <= 0) {
                                removeTask(task.getKey());
                                continue taskLoop;
                            }
                            if (pushedPatterns == maxPatterns) {
                                break taskLoop;
                            }
                            continue taskLoop;
                        } finally {
                            diagnostics.recordAccounting(elapsedMicros(accountingStarted, true));
                        }
                    } else if (batchResult < 0) {
                        if (job.pendingAccounting() != null) {
                            break taskLoop;
                        }
                        taskScheduler.deferUntilNextTick(details);
                        continue taskLoop;
                    }

                    long fallbackStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                    int ae2ltBatchResult = fuzzyBatchSafe
                        ? job.fuzzyItemIds.isEmpty()
                            ? ae2ltBatchBridge.tryPushBatch(
                                providers,
                                details,
                                craftingContainer,
                                inventory,
                                energyService,
                                patternPower,
                                limitedBatchTaskRemaining
                            )
                            : ae2ltBatchBridge.tryPushFuzzyBatch(
                                providers,
                                details,
                                craftingContainer,
                                inventory,
                                energyService,
                                patternPower,
                                limitedBatchTaskRemaining,
                                job.fuzzyItemIds
                            )
                        : 0;
                    if (diagnostics.isTimingEnabled()) {
                        long fallbackElapsed = elapsedMicros(fallbackStarted, true);
                        diagnostics.recordFallback(fallbackElapsed);
                        if (fuzzyBatchSafe) {
                            diagnostics.recordAe2ltFallback(fallbackElapsed);
                            if (ae2ltBatchResult <= 0) {
                                diagnostics.incrementAe2ltFallback();
                            }
                        }
                        fallbackStarted = System.nanoTime();
                    }
                    if (ae2ltBatchResult > 0) {
                        long accountingStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                        try {
                            pushedPatterns++;
                            if (this.job != job) {
                                break taskLoop;
                            }
                            job.beginPendingAccounting(
                                details,
                                execution.expectedOutputs(),
                                execution.expectedContainerItems(),
                                List.of(),
                                List.of(),
                                ae2ltBatchResult,
                                false,
                                runtimeInputFallback
                            );
                            job.markPendingAccountingOwnershipTransferred();
                            try {
                                if (!applyPendingAccounting(job)) {
                                    break taskLoop;
                                }
                            } catch (RuntimeException e) {
                                suspendAfterAcceptedAccountingFailure(job, e,
                                    "AE2LT batch was accepted, but its CPU output accounting update failed");
                                break taskLoop;
                            }
                            pendingInputSnapshot = null;
                            mergeInputDiagnosticContext(inputDiagnosticContext);
                            inputDiagnosticContext = null;
                            if (task.getValue().value <= 0) {
                                removeTask(task.getKey());
                                continue taskLoop;
                            }
                            if (pushedPatterns == maxPatterns) {
                                break taskLoop;
                            }
                            continue taskLoop;
                        } finally {
                            diagnostics.recordAccounting(elapsedMicros(accountingStarted, true));
                        }
                    }

                    int megacellsBatchResult = fuzzyBatchSafe
                        ? megacellsBatchBridge.tryPushBatch(
                            providers,
                            details,
                            craftingContainer,
                            inventory,
                            energyService,
                            patternPower,
                            limitedBatchTaskRemaining,
                            job.fuzzyItemIds
                        )
                        : 0;
                    if (diagnostics.isTimingEnabled()) {
                        long fallbackElapsed = elapsedMicros(fallbackStarted, true);
                        diagnostics.recordFallback(fallbackElapsed);
                        if (fuzzyBatchSafe) {
                            diagnostics.recordMegacellsFallback(fallbackElapsed);
                            if (megacellsBatchResult <= 0) {
                                diagnostics.incrementMegacellsFallback();
                            }
                        }
                        fallbackStarted = System.nanoTime();
                    }
                    if (megacellsBatchResult > 0) {
                        long accountingStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                        try {
                            pushedPatterns++;
                            if (this.job != job) {
                                break taskLoop;
                            }
                            job.beginPendingAccounting(
                                details,
                                execution.expectedOutputs(),
                                execution.expectedContainerItems(),
                                List.of(),
                                List.of(),
                                megacellsBatchResult,
                                false,
                                runtimeInputFallback
                            );
                            job.markPendingAccountingOwnershipTransferred();
                            try {
                                if (!applyPendingAccounting(job)) {
                                    break taskLoop;
                                }
                            } catch (RuntimeException e) {
                                suspendAfterAcceptedAccountingFailure(job, e,
                                    "MEGACells batch was accepted, but its CPU output accounting update failed");
                                break taskLoop;
                            }
                            pendingInputSnapshot = null;
                            mergeInputDiagnosticContext(inputDiagnosticContext);
                            inputDiagnosticContext = null;
                            if (task.getValue().value <= 0) {
                                removeTask(task.getKey());
                                continue taskLoop;
                            }
                            if (pushedPatterns == maxPatterns) {
                                break taskLoop;
                            }
                            continue taskLoop;
                        } finally {
                            diagnostics.recordAccounting(elapsedMicros(accountingStarted, true));
                        }
                    }

                    boolean pushed = false;
                    try {
                        for (ICraftingProvider provider : providers) {
                        // Batch-capable providers were already offered both ECO and AE2LT batch paths above.
                        // Do not let a high CPU parallelism turn the synchronous fallback into thousands of
                        // third-party inventory insertions in a single server tick.
                        if (provider.isBusy()
                            || shouldSkipSlowPathProvider(provider, details)
                            || ae2ltBatchBridge.shouldSkip(provider, details)
                            || megacellsBatchBridge.shouldSkip(provider, details)) {
                            continue;
                        }

                        long processingBatchResult = fuzzyBatchSafe
                            ? tryPushProcessingBatch(
                                job,
                                details,
                                execution,
                                craftingContainer,
                                provider,
                                energyService,
                                patternPower,
                                limitedBatchTaskRemaining)
                            : 0L;
                        if (processingBatchResult > 0L) {
                            if (diagnostics.isTimingEnabled()) {
                                long fallbackElapsed = elapsedMicros(fallbackStarted, true);
                                diagnostics.recordFallback(fallbackElapsed);
                                diagnostics.recordProviderFallback(fallbackElapsed);
                                fallbackStarted = 0L;
                            }
                            long accountingStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                            try {
                                pushedPatterns++;
                                if (this.job != job) {
                                    break taskLoop;
                                }
                                job.beginPendingAccounting(
                                    details,
                                    execution.expectedOutputs(),
                                    execution.expectedContainerItems(),
                                    List.of(),
                                    List.of(),
                                    processingBatchResult,
                                    false,
                                    runtimeInputFallback
                                );
                                job.markPendingAccountingOwnershipTransferred();
                                try {
                                    if (!applyPendingAccounting(job)) {
                                        break taskLoop;
                                    }
                                } catch (RuntimeException e) {
                                    suspendAfterAcceptedAccountingFailure(job, e,
                                        "Processing-provider batch was accepted, but its CPU output accounting update failed");
                                    break taskLoop;
                                }
                                pendingInputSnapshot = null;
                                mergeInputDiagnosticContext(inputDiagnosticContext);
                                inputDiagnosticContext = null;
                                if (task.getValue().value <= 0) {
                                    removeTask(task.getKey());
                                    continue taskLoop;
                                }
                                if (pushedPatterns == maxPatterns) {
                                    break taskLoop;
                                }
                                pushed = true;
                                break;
                            } finally {
                                diagnostics.recordAccounting(elapsedMicros(accountingStarted, true));
                            }
                        }

                        if (!Double.isFinite(patternPower) || patternPower < 0.0D) {
                            CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                            taskScheduler.deferUntilNextTick(details);
                            continue taskLoop;
                        }

                        ECOBatchEnergyReservation energyReservation =
                            ECOBatchEnergyReservation.tryReserve(energyService, patternPower, false);
                        if (energyReservation == null || !energyReservation.isFullyReserved()) {
                            if (energyReservation != null) {
                                RuntimeException refundFailure = energyReservation.refundSafely();
                                if (refundFailure != null) {
                                    LOGGER.error("Crafting pattern energy refund failed after a partial reservation",
                                        refundFailure);
                                }
                            }
                            taskScheduler.deferUntilNextTick(details);
                            break;
                        }

                        if (!slowPathPushBudget.tryAcquire()) {
                            RuntimeException refundFailure = energyReservation.refundSafely();
                            if (refundFailure != null) {
                                LOGGER.error("Crafting pattern energy refund failed after slow-path throttling",
                                    refundFailure);
                            }
                            CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                            taskScheduler.deferUntilNextTick(details);
                            continue taskLoop;
                        }

                        job.beginPendingAccounting(
                            details,
                            execution.expectedOutputs(),
                            execution.expectedContainerItems(),
                            List.of(),
                            flattenProcessingInputs(craftingContainer),
                            1L,
                            false,
                            runtimeInputFallback
                        );

                        if (diagnostics.isTimingEnabled()) {
                            diagnostics.incrementProviderAttempt();
                        }
                        try {
                            if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus) {
                                pushed = patternBus.pushPattern(execution, job.link.getCraftingID());
                            } else {
                                // AE2LT wraps this exact interface invocation to register overload outputs.
                                pushed = provider.pushPattern(details, craftingContainer);
                            }
                        } catch (RuntimeException e) {
                            boolean ownershipTransferred = provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                                && patternBus.hasInFlightJob(job.link.getCraftingID());
                            if (ownershipTransferred) {
                                job.markPendingAccountingOwnershipTransferred();
                                cpu.markDirty();
                                energyReservation.commit();
                                suspendAfterAcceptedAccountingFailure(
                                    job,
                                    e,
                                    "Crafting provider accepted a pattern before throwing; accounting recovery is pending"
                                );
                                break taskLoop;
                            }
                            LOGGER.error(
                                "Crafting provider rejected a pattern with an exception; CPU inputs remain owned locally",
                                e
                            );
                            pushed = false;
                        }

                        if (!pushed) {
                            job.clearPendingAccounting();
                            RuntimeException refundFailure = energyReservation.refundSafely();
                            if (refundFailure != null) {
                                LOGGER.error("Crafting pattern energy refund failed after provider rejection",
                                    refundFailure);
                            }
                            if (!job.fuzzyItemIds.isEmpty()) {
                                // A normal provider saw the original pattern and rejected the
                                // concrete component. Do not replay the same fuzzy selection on
                                // the next tick; the next attempt must use strict AE2 matching.
                                discardPlannedInputs(details);
                            }
                            deferSlowPathProvider(provider, details);
                            ae2ltBatchBridge.recordFailedSinglePush(provider, details);
                            megacellsBatchBridge.recordFailedSinglePush(provider, details);
                            continue;
                        }

                        job.markPendingAccountingOwnershipTransferred();
                        cpu.markDirty();

                        // Ownership has crossed the provider boundary. The reservation must no longer
                        // be refundable before output/task accounting starts.
                        try {
                            energyReservation.commit();
                        } catch (RuntimeException e) {
                            suspendAfterAcceptedAccountingFailure(
                                job,
                                e,
                                "Crafting provider accepted a pattern, but energy settlement failed"
                            );
                            break taskLoop;
                        }

                        if (diagnostics.isTimingEnabled()) {
                            long fallbackElapsed = elapsedMicros(fallbackStarted, true);
                            diagnostics.recordFallback(fallbackElapsed);
                            diagnostics.recordProviderFallback(fallbackElapsed);
                            fallbackStarted = 0L;
                        }

                        long accountingStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                        try {
                            pushedPatterns++;
                            if (this.job != job) {
                                break taskLoop;
                            }
                            if (!applyPendingAccounting(job)) {
                                break taskLoop;
                            }
                            pendingInputSnapshot = null;
                            mergeInputDiagnosticContext(inputDiagnosticContext);
                            inputDiagnosticContext = null;
                            if (task.getValue().value <= 0) {
                                removeTask(task.getKey());
                                continue taskLoop;
                            }

                            if (pushedPatterns == maxPatterns) {
                                break taskLoop;
                            }

                            break;
                        } finally {
                            diagnostics.recordAccounting(elapsedMicros(accountingStarted, true));
                        }
                        }
                    } finally {
                        if (diagnostics.isTimingEnabled()) {
                            long fallbackElapsed = elapsedMicros(fallbackStarted, true);
                            diagnostics.recordFallback(fallbackElapsed);
                            diagnostics.recordProviderFallback(fallbackElapsed);
                        }
                    }

                    if (!pushed) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                        continue taskLoop;
                    }
                }
                }
                } finally {
                    mergeInputDiagnosticContext(inputDiagnosticContext);
                    long statusStarted = diagnostics.isTimingEnabled() ? System.nanoTime() : 0L;
                    endStatusChangeBatchSafely();
                    diagnostics.recordStatusChange(elapsedMicros(statusStarted, true));
                }

        return pushedPatterns;
    }

    /**
     * Attempts the independent ordinary AE2 processing-provider batch path.
     *
     * <p>The first craft is already held in {@code firstCraftingContainer}. Only extra crafts
     * are extracted here, so a pre-ownership rejection leaves the original slow path intact.</p>
     */
    private long tryPushProcessingBatch(
            ExecutingCraftingJob job,
            IPatternDetails details,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            ICraftingProvider provider,
            IEnergyService energyService,
            double patternPower,
            long taskRemaining) {
        if (!NEConfig.ecoProcessingBatchEnabled || taskRemaining < 2L || !job.fuzzyItemIds.isEmpty()) {
            return 0L;
        }

        List<GenericStack> perCraftInputs = flattenProcessingInputs(firstCraftingContainer);
        if (perCraftInputs.isEmpty()) {
            ECOProcessingBatchDiagnostics.record(
                ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.NO_CAPACITY,
                "processing pattern has no physical input");
            return 0L;
        }

        long requested = Math.min(taskRemaining, Math.max(2L, NEConfig.ecoProcessingBatchMax));
        var inventoryLimit = ECOBatchCraftingHelper.inventoryBatchLimit(
            new ECOFuzzyCraftingInventory(inventory, job.fuzzyItemIds),
            perCraftInputs,
            requested - 1L,
            job.fuzzyItemIds
        );
        if (inventoryLimit.crafts() <= 0L) {
            ECOProcessingBatchDiagnostics.record(
                ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.INPUT_RESERVATION_FAILED,
                "CPU has no complete extra processing craft");
            return 0L;
        }
        requested = Math.min(requested, inventoryLimit.crafts() + 1L);
        if (requested < 2L) {
            return 0L;
        }

        ECOProcessingBatchAdmission admission;
        try {
            admission = ECOProcessingBatchAdapter.prepare(
                provider, details, firstCraftingContainer, requested);
        } catch (RuntimeException e) {
            ECOProcessingBatchDiagnostics.record(
                ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.NO_CAPACITY,
                "provider preparation failed: " + e.getMessage());
            return 0L;
        }
        if (admission == null || admission.count() < 2L) {
            return 0L;
        }

        long batchSize = admission.count();
        List<GenericStack> extraInputs;
        try {
            extraInputs = ECOBatchCraftingHelper.multiply(perCraftInputs, batchSize - 1L);
        } catch (RuntimeException e) {
            ECOProcessingBatchDiagnostics.record(
                ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.INPUT_OVERFLOW,
                "extra input expansion failed: " + e.getMessage());
            return 0L;
        }

        ECOBatchEnergyReservation energyReservation = null;
        List<GenericStack> extractedExtraInputs = List.of();
        try {
            double requiredPower = patternPower * batchSize;
            if (!Double.isFinite(requiredPower) || requiredPower < 0.0D) {
                ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.ENERGY_LIMIT,
                    "requiredPower=" + requiredPower + " batch=" + batchSize);
                return 0L;
            }
            energyReservation = ECOBatchEnergyReservation.tryReserve(energyService, requiredPower, false);
            if (energyReservation == null || !energyReservation.isFullyReserved()) {
                if (energyReservation != null) {
                    RuntimeException refundFailure = energyReservation.refundSafely();
                    if (refundFailure != null) {
                        LOGGER.error("Processing-provider batch energy refund failed after a partial reservation",
                            refundFailure);
                    }
                }
                ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.ENERGY_LIMIT,
                    "requiredPower=" + requiredPower);
                return 0L;
            }

            extractedExtraInputs = ECOBatchCraftingHelper.extractExactReturning(
                new ECOFuzzyCraftingInventory(inventory, job.fuzzyItemIds),
                extraInputs,
                job.fuzzyItemIds
            );
            consumeRetainedFinalOutputFrom(extractedExtraInputs);

            boolean accepted = admission.commit(firstCraftingContainer);
            if (!accepted) {
                if (admission.hasTransferredInputOwnership()) {
                    energyReservation.commit();
                    ECOProcessingBatchDiagnostics.record(
                        ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.OWNERSHIP_AFTER_EXCEPTION,
                        "provider returned false after ownership transfer");
                    return batchSize;
                }
                rollbackProcessingBatch(extractedExtraInputs, energyReservation);
                ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.PROVIDER_REJECTED,
                    "provider admission returned false");
                return 0L;
            }
            if (!admission.hasTransferredInputOwnership()) {
                rollbackProcessingBatch(extractedExtraInputs, energyReservation);
                ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.PROVIDER_REJECTED,
                    "provider accepted without transferring input ownership");
                return 0L;
            }

            energyReservation.commit();
            return batchSize;
        } catch (RuntimeException e) {
            if (admission.hasTransferredInputOwnership()) {
                if (energyReservation != null) {
                    energyReservation.commit();
                }
                ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.OWNERSHIP_AFTER_EXCEPTION,
                    e.getMessage() == null ? e.getClass().getName() : e.getMessage());
                LOGGER.error("Processing-provider batch failed after input ownership transfer", e);
                return batchSize;
            }
            rollbackProcessingBatch(extractedExtraInputs, energyReservation);
            ECOProcessingBatchDiagnostics.record(
                ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.PROVIDER_REJECTED,
                e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            return 0L;
        }
    }

    private static List<GenericStack> flattenProcessingInputs(KeyCounter[] inputHolder) {
        List<GenericStack> inputs = new ArrayList<>();
        for (KeyCounter counter : inputHolder) {
            if (counter == null) {
                continue;
            }
            for (var entry : counter) {
                if (entry.getLongValue() > 0L) {
                    inputs.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
        }
        return ECOBatchCraftingHelper.combine(inputs);
    }

    private void rollbackProcessingBatch(
            List<GenericStack> extractedExtraInputs,
            @Nullable ECOBatchEnergyReservation energyReservation) {
        if (extractedExtraInputs != null && !extractedExtraInputs.isEmpty()) {
            ECOBatchCraftingHelper.insertAll(inventory, extractedExtraInputs, this::postInventoryChange);
        }
        if (energyReservation != null) {
            RuntimeException refundFailure = energyReservation.refundSafely();
            if (refundFailure != null) {
                LOGGER.error("Processing-provider batch energy refund failed", refundFailure);
            }
        }
    }

    private static final class SlowPathPushBudget {
        private final SlowPathNetworkBudget networkBudget;

        private SlowPathPushBudget(CraftingService craftingService) {
            synchronized (SLOW_PATH_NETWORK_BUDGETS) {
                this.networkBudget = SLOW_PATH_NETWORK_BUDGETS.computeIfAbsent(
                    craftingService, ignored -> new SlowPathNetworkBudget()
                );
            }
        }

        private boolean tryAcquire() {
            return networkBudget.tryAcquire(
                TickHandler.instance().getCurrentTick(),
                NEConfig.ecoCpuSlowPathPushTickLimit,
                NEConfig.ecoCpuSlowPathTimeBudgetMicros
            );
        }
    }

    private static final class SlowPathNetworkBudget {
        private static final int TIME_CHECK_INTERVAL = 16;

        private long tick = Long.MIN_VALUE;
        private int attempts;
        private long deadlineNanos = Long.MAX_VALUE;

        private boolean tryAcquire(long currentTick, int configuredLimit, int timeBudgetMicros) {
            if (tick != currentTick) {
                tick = currentTick;
                attempts = 0;
                if (timeBudgetMicros <= 0) {
                    deadlineNanos = Long.MAX_VALUE;
                } else {
                    long budgetNanos = (long) timeBudgetMicros * 1_000L;
                    long now = System.nanoTime();
                    deadlineNanos = now >= Long.MAX_VALUE - budgetNanos
                        ? Long.MAX_VALUE
                        : now + budgetNanos;
                }
            }

            int limit = Math.max(0, configuredLimit);
            if (attempts >= limit) {
                return false;
            }
            if (deadlineNanos != Long.MAX_VALUE
                && attempts % TIME_CHECK_INTERVAL == 0
                && System.nanoTime() >= deadlineNanos) {
                return false;
            }
            attempts++;
            return true;
        }
    }

    private enum BatchDispatchState {
        PREPARED,
        RESOURCES_RESERVED,
        PROVIDER_ACCEPTED,
        INPUT_OWNERSHIP_TRANSFERRED,
        ACCOUNTING_APPLIED;

        private boolean providerOwnsInputs() {
            return this == PROVIDER_ACCEPTED
                || this == INPUT_OWNERSHIP_TRANSFERRED
                || this == ACCOUNTING_APPLIED;
        }
    }

    private List<ICraftingProvider> collectAvailableProviders(CraftingService craftingService,
            IPatternDetails details) {
        List<ICraftingProvider> providers = new ArrayList<>();
        for (ICraftingProvider provider : craftingService.getProviders(details)) {
            if (!provider.isBusy()
                && !shouldSkipSlowPathProvider(provider, details)
                && !ae2ltBatchBridge.shouldSkip(provider, details)
                && !megacellsBatchBridge.shouldSkip(provider, details)) {
                providers.add(provider);
            }
        }
        return providers;
    }

    private void beginSlowPathProviderTick(long tick) {
        if (slowPathDeferredProvidersTick != tick) {
            slowPathDeferredProvidersTick = tick;
            slowPathDeferredProviders.clear();
        }
    }

    private boolean shouldSkipSlowPathProvider(ICraftingProvider provider, IPatternDetails details) {
        IdentityHashMap<ICraftingProvider, Boolean> providers = slowPathDeferredProviders.get(details);
        return providers != null && providers.containsKey(provider);
    }

    private void deferSlowPathProvider(ICraftingProvider provider, IPatternDetails details) {
        slowPathDeferredProviders
            .computeIfAbsent(details, ignored -> new IdentityHashMap<>())
            .put(provider, Boolean.TRUE);
    }

    private static List<ECOCraftingPatternBusBlockEntity> collectPatternBuses(List<ICraftingProvider> providers) {
        List<ECOCraftingPatternBusBlockEntity> patternBuses = null;
        for (ICraftingProvider provider : providers) {
            if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus) {
                if (patternBuses == null) {
                    patternBuses = new ArrayList<>();
                }
                patternBuses.add(patternBus);
            }
        }
        return patternBuses == null ? List.of() : patternBuses;
    }

    private MissingPatternInputDiagnostic describeMissingPatternInputs(
        IPatternDetails details,
        Level level,
        InputDiagnosticContext context) {
        Map<AEKey, Long> consumedInventory = new LinkedHashMap<>();

        List<String> missing = null;
        boolean hasNormalDependencyWait = false;
        boolean hasNonNormalMissingInput = false;
        IPatternDetails.IInput[] inputs = details.getInputs();
        for (int slot = 0;
             slot < inputs.length && (missing == null || missing.size() < MAX_MISSING_INPUT_DIAGNOSTICS);
             slot++) {
            IPatternDetails.IInput input = inputs[slot];
            long remainingUnits = input.getMultiplier();
            if (remainingUnits <= 0L) {
                continue;
            }

            for (InputTemplate template : CraftingCpuHelper.getValidItemTemplates(context.inventory, input, level)) {
                long availableItems = context.availableInventoryAmount(consumedInventory, template.key());
                long availableUnits = availableItems / template.amount();
                long extractedUnits = Math.min(remainingUnits, availableUnits);
                if (extractedUnits > 0L) {
                    context.consumeInventory(
                        consumedInventory,
                        template.key(),
                        scaledPatternAmount(template.amount(), extractedUnits)
                    );
                    remainingUnits -= extractedUnits;
                }
                if (remainingUnits == 0L) {
                    break;
                }
            }

            // The local inventory already satisfied this slot. Do not classify its alternatives
            // as missing inputs merely because another slot in the pattern is still blocked.
            if (remainingUnits <= 0L) {
                continue;
            }

            Set<String> auditCandidates = null;
            Set<String> hardCandidates = null;
            int candidateCount = 0;
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible == null || possible.amount() <= 0L
                        || candidateCount >= MAX_INPUT_CANDIDATE_DIAGNOSTICS) {
                    continue;
                }
                boolean valid = context.isValid(input, possible.what(), level);
                if (!valid) {
                    continue;
                }
                context.uniqueKeys.add(possible.what());
                long cpuAmount = context.availableInventoryAmount(consumedInventory, possible.what());
                long requiredItems = scaledPatternAmount(possible.amount(), remainingUnits);
                long waitingFor = context.waitingFor(possible.what());
                long pendingInput = context.pendingInputAmounts.getOrDefault(possible.what(), 0L);
                long networkAmount = -1L;
                long reservedAtStart = -1L;
                boolean exactKeyMismatch = false;
                boolean exactKeyMismatchKnown = false;
                boolean requiresNetworkFact = cpuAmount <= 0L && waitingFor <= 0L && pendingInput <= 0L;
                if (requiresNetworkFact) {
                    reservedAtStart = initialReservationKnown
                        ? availableFromCounter(initialReservedItems, possible.what(), context.job.fuzzyItemIds)
                        : -1L;
                    networkAmount = context.networkAmount(possible.what());
                    if (networkAmount == 0L
                            && !ECOFuzzyCraftingInventory.isConfiguredFuzzy(
                                possible.what(), context.job.fuzzyItemIds)) {
                        exactKeyMismatchKnown = context.exactVariantKnown();
                        exactKeyMismatch = context.hasDifferentExactVariant(possible.what());
                    }
                }
                String source = classifyInputSource(
                    cpuAmount,
                    networkAmount,
                    reservedAtStart,
                    waitingFor,
                    pendingInput,
                    requiredItems,
                    exactKeyMismatch,
                    exactKeyMismatchKnown
                );
                if (isNormalDependencyWaitSource(source)) {
                    hasNormalDependencyWait = true;
                } else {
                    // Do not hide a real missing-input diagnosis just because another input is
                    // waiting for a normal upstream task in the same pattern.
                    hasNonNormalMissingInput = true;
                    if (hardCandidates == null) {
                        hardCandidates = new java.util.LinkedHashSet<>();
                        auditCandidates = new java.util.LinkedHashSet<>();
                    }
                    String hardCandidate = possible.what()
                        + " required=" + requiredItems
                        + " cpu=" + cpuAmount
                        + " network=" + networkAmount
                        + " reservedAtStart=" + reservedAtStart
                        + " waitingFor=" + waitingFor
                        + " pendingInput=" + pendingInput
                        + " valid=" + valid
                        + " source=" + source;
                    hardCandidates.add(hardCandidate);
                    auditCandidates.add(hardCandidate);
                }
                candidateCount++;
            }
            if (candidateCount == 0) {
                hasNonNormalMissingInput = true;
                hardCandidates = new java.util.LinkedHashSet<>();
                auditCandidates = new java.util.LinkedHashSet<>();
                hardCandidates.add("no_valid_candidate");
                auditCandidates.add("no_valid_candidate");
            }
            if (remainingUnits > 0L && hardCandidates != null && !hardCandidates.isEmpty()) {
                if (missing == null) {
                    missing = new ArrayList<>();
                }
                missing.add("slot=" + slot + " remainingUnits=" + remainingUnits
                    + " candidates=" + hardCandidates
                    + " audit=" + auditCandidates
                    + " networkSnapshot=" + context.networkSnapshot());
            }
        }

        InputReservationResult result = classifyInputReservation(
            hasNormalDependencyWait,
            hasNonNormalMissingInput
        );
        if (result == InputReservationResult.DEPENDENCY_WAIT) {
            context.dependencyWaitCount++;
        } else if (result == InputReservationResult.HARD_FAILURE) {
            context.hardFailureCount++;
        }
        return new MissingPatternInputDiagnostic(
            missing == null ? "unknown" : missing.toString(),
            result
        );
    }

    /** Facts shared by all missing-input diagnoses in one unchanged scheduling round. */
    private final class InputDiagnosticContext {
        private final Map<AEKey, Long> pendingInputAmounts;
        private final Map<AEKey, Long> inventoryAmounts = new LinkedHashMap<>();
        private final ListCraftingInventory inventory;
        private final ExecutingCraftingJob job;
        private final Map<AEKey, Long> networkAmounts = new LinkedHashMap<>();
        private final Map<AEKey, Long> waitingAmounts = new LinkedHashMap<>();
        private final Map<AEKey, Boolean> exactVariantResults = new LinkedHashMap<>();
        private final IdentityHashMap<IPatternDetails.IInput, Map<AEKey, Boolean>> validCandidates =
            new IdentityHashMap<>();
        private final Set<AEKey> uniqueKeys = new HashSet<>();
        @Nullable
        private MEStorage networkStorage;
        @Nullable
        private KeyCounter fuzzyNetworkAvailable;
        @Nullable
        private KeyCounter exactNetworkAvailable;
        private boolean networkStorageLoaded;
        private boolean fuzzyNetworkSnapshotLoaded;
        private boolean exactNetworkSnapshotLoaded;
        private String networkSnapshot = "unavailable";
        private long networkMicros;
        private long cacheHits;
        private long cacheMisses;
        private long dependencyWaitCount;
        private long hardFailureCount;

        private InputDiagnosticContext(
            Map<AEKey, Long> pendingInputAmounts,
            ListCraftingInventory inventory,
            ExecutingCraftingJob job) {
            this.pendingInputAmounts = pendingInputAmounts;
            this.inventory = inventory;
            this.job = job;
            for (var entry : inventory.list) {
                if (entry.getLongValue() > 0L) {
                    inventoryAmounts.put(entry.getKey(), entry.getLongValue());
                }
            }
        }

        private long availableInventoryAmount(Map<AEKey, Long> consumed, AEKey key) {
            long available = inventoryAmounts.getOrDefault(key, 0L);
            long used = consumed.getOrDefault(key, 0L);
            return used >= available ? 0L : available - used;
        }

        private void consumeInventory(Map<AEKey, Long> consumed, AEKey key, long amount) {
            if (amount > 0L) {
                consumed.merge(key, amount, ECOCraftingCPULogic::saturatingAdd);
            }
        }

        private boolean isValid(IPatternDetails.IInput input, AEKey key, Level level) {
            Map<AEKey, Boolean> byKey = validCandidates.computeIfAbsent(
                input, ignored -> new LinkedHashMap<>());
            Boolean cached = byKey.get(key);
            if (cached != null) {
                cacheHits++;
                return cached;
            }
            cacheMisses++;
            boolean valid = input.isValid(key, level);
            byKey.put(key, valid);
            return valid;
        }

        private long waitingFor(AEKey key) {
            Long cached = waitingAmounts.get(key);
            if (cached != null) {
                cacheHits++;
                return cached;
            }
            cacheMisses++;
            long amount = job.waitingFor.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
            waitingAmounts.put(key, amount);
            return amount;
        }

        private long networkAmount(AEKey key) {
            Long cached = networkAmounts.get(key);
            if (cached != null) {
                cacheHits++;
                return cached;
            }
            cacheMisses++;
            long started = System.nanoTime();
            ensureNetworkStorage();
            long amount;
            if (networkStorage == null) {
                amount = -1L;
            } else {
                try {
                    if (ECOFuzzyCraftingInventory.isConfiguredFuzzy(key, job.fuzzyItemIds)) {
                        ensureFuzzyNetworkSnapshot();
                        amount = fuzzyNetworkAvailable == null
                            ? -1L
                            : availableFromCounter(fuzzyNetworkAvailable, key, job.fuzzyItemIds);
                    } else {
                        amount = networkStorage.extract(
                            key, Long.MAX_VALUE, Actionable.SIMULATE, cpu.getActionSource());
                    }
                } catch (RuntimeException e) {
                    amount = -1L;
                    networkSnapshot = "error=" + e.getClass().getSimpleName();
                }
            }
            networkMicros = saturatingAdd(networkMicros, elapsedMicros(started, true));
            networkAmounts.put(key, amount);
            return amount;
        }

        private void ensureNetworkStorage() {
            if (networkStorageLoaded) {
                return;
            }
            networkStorageLoaded = true;
            try {
                IGrid grid = cpu.getGrid();
                if (grid != null) {
                    networkStorage = grid.getStorageService().getInventory();
                    networkSnapshot = "ready";
                } else {
                    networkSnapshot = "no_grid";
                }
            } catch (RuntimeException e) {
                networkSnapshot = "error=" + e.getClass().getSimpleName();
            }
        }

        private void ensureFuzzyNetworkSnapshot() {
            if (fuzzyNetworkSnapshotLoaded) {
                return;
            }
            fuzzyNetworkSnapshotLoaded = true;
            try {
                fuzzyNetworkAvailable = networkStorage == null ? null : networkStorage.getAvailableStacks();
                networkSnapshot = "ready_fuzzy_snapshot";
            } catch (RuntimeException e) {
                networkSnapshot = "error=" + e.getClass().getSimpleName();
            }
        }

        private boolean exactVariantKnown() {
            ensureNetworkStorage();
            if (networkStorage == null) {
                return false;
            }
            if (!exactNetworkSnapshotLoaded) {
                exactNetworkSnapshotLoaded = true;
                long started = System.nanoTime();
                try {
                    exactNetworkAvailable = networkStorage.getAvailableStacks();
                    networkSnapshot = "ready_exact_variant_snapshot";
                } catch (RuntimeException e) {
                    networkSnapshot = "error=" + e.getClass().getSimpleName();
                } finally {
                    networkMicros = saturatingAdd(networkMicros, elapsedMicros(started, true));
                }
            }
            return exactNetworkAvailable != null;
        }

        private boolean hasDifferentExactVariant(AEKey key) {
            Boolean cached = exactVariantResults.get(key);
            if (cached != null) {
                cacheHits++;
                return cached;
            }
            cacheMisses++;
            long started = System.nanoTime();
            boolean different = exactNetworkAvailable != null &&
                ECOCraftingCPULogic.hasDifferentExactVariant(exactNetworkAvailable, key);
            networkMicros = saturatingAdd(networkMicros, elapsedMicros(started, true));
            exactVariantResults.put(key, different);
            return different;
        }

        private int uniqueKeyCount() {
            return uniqueKeys.size();
        }

        private long networkMicros() {
            return networkMicros;
        }

        private String networkSnapshot() {
            return networkSnapshot;
        }
    }

    private void mergeInputDiagnosticContext(@Nullable InputDiagnosticContext context) {
        if (context == null) {
            return;
        }
        diagnostics.recordInputDiagnosticContext(
            context.uniqueKeys,
            context.cacheHits,
            context.cacheMisses,
            context.networkMicros(),
            context.dependencyWaitCount,
            context.hardFailureCount
        );
    }

    private enum InputReservationResult {
        SUCCESS,
        DEPENDENCY_WAIT,
        HARD_FAILURE
    }

    private record MissingPatternInputDiagnostic(
        String description,
        InputReservationResult result
    ) {
    }

    private static long availableFromCounter(
            KeyCounter counter,
            AEKey key,
            Set<net.minecraft.resources.ResourceLocation> fuzzyItemIds) {
        if (!ECOFuzzyCraftingInventory.isConfiguredFuzzy(key, fuzzyItemIds)) {
            return counter.get(key);
        }
        long total = 0L;
        for (var entry : counter.findFuzzy(key, appeng.api.config.FuzzyMode.IGNORE_ALL)) {
            long amount = entry.getLongValue();
            total = total > Long.MAX_VALUE - amount ? Long.MAX_VALUE : total + amount;
        }
        return total;
    }

    /**
     * Builds the pending-input index once for a scheduling pass. The previous diagnostic path
     * called pendingInputAmount for every candidate, which rescanned the complete task DAG for
     * each key and made a dependency-heavy job quadratic in its remaining tasks.
     */
    private Map<AEKey, Long> buildPendingInputAmounts(ExecutingCraftingJob currentJob) {
        if (currentJob == job && pendingInputIndexKnown) {
            return new LinkedHashMap<>(pendingInputAmounts);
        }
        Map<AEKey, Long> totals = new LinkedHashMap<>();
        for (var task : currentJob.tasks.entrySet()) {
            long batches = task.getValue().value;
            if (batches <= 0L) {
                continue;
            }
            var plannedBatches = currentJob.plannedInputs.get(task.getKey());
            if (plannedBatches != null && !plannedBatches.isEmpty()) {
                for (var plannedBatch : plannedBatches) {
                    for (var selection : plannedBatch.selectedInputs()) {
                        for (var alternative : selection.alternatives()) {
                            GenericStack selected = alternative.template();
                            addPendingInputAmount(
                                totals,
                                selected.what(),
                                scaledPatternAmount(
                                    selected.amount(),
                                    alternative.multiplier()
                                ),
                                plannedBatch.remaining()
                            );
                        }
                    }
                }
                continue;
            }

            Map<AEKey, Long> perBatch = new LinkedHashMap<>();
            for (var input : task.getKey().getInputs()) {
                Map<AEKey, Long> inputAmounts = new LinkedHashMap<>();
                for (var possible : input.getPossibleInputs()) {
                    if (possible == null || possible.amount() <= 0L) {
                        continue;
                    }
                    long amount = scaledPatternAmount(possible.amount(), input.getMultiplier());
                    inputAmounts.merge(
                        possible.what(),
                        amount,
                        Math::max
                    );
                }
                for (var entry : inputAmounts.entrySet()) {
                    perBatch.merge(
                        entry.getKey(),
                        entry.getValue(),
                        ECOCraftingCPULogic::saturatingAdd
                    );
                }
            }
            for (var entry : perBatch.entrySet()) {
                addPendingInputAmount(totals, entry.getKey(), entry.getValue(), batches);
            }
        }
        return totals;
    }

    private void rebuildPendingInputIndex() {
        pendingInputByTask.clear();
        pendingInputAmounts.clear();
        pendingFuzzyInputAmounts.clear();
        pendingInputIndexKnown = false;
        if (job == null) {
            return;
        }
        for (IPatternDetails details : job.tasks.keySet()) {
            refreshPendingInputTask(details);
        }
        pendingInputIndexKnown = true;
    }

    private void refreshPendingInputTask(IPatternDetails details) {
        if (job == null) {
            return;
        }
        if (!pendingInputIndexKnown && pendingInputByTask.isEmpty()) {
            // During the initial build there is no old contribution to subtract.
        }
        Map<AEKey, Long> old = pendingInputByTask.remove(details);
        if (old != null) {
            for (var entry : old.entrySet()) {
                subtractPendingAmount(pendingInputAmounts, entry.getKey(), entry.getValue());
                if (ECOFuzzyCraftingInventory.isConfiguredFuzzy(entry.getKey(), job.fuzzyItemIds)) {
                    subtractPendingAmount(
                        pendingFuzzyInputAmounts,
                        entry.getKey().getId(),
                        entry.getValue()
                    );
                }
            }
        }
        ExecutingCraftingJob.TaskProgress progress = job.tasks.get(details);
        if (progress == null || progress.value <= 0L) {
            return;
        }
        Map<AEKey, Long> contribution = pendingContribution(details, progress.value);
        pendingInputByTask.put(details, contribution);
        for (var entry : contribution.entrySet()) {
            pendingInputAmounts.merge(entry.getKey(), entry.getValue(), ECOCraftingCPULogic::saturatingAdd);
            if (ECOFuzzyCraftingInventory.isConfiguredFuzzy(entry.getKey(), job.fuzzyItemIds)) {
                pendingFuzzyInputAmounts.merge(
                    entry.getKey().getId(), entry.getValue(), ECOCraftingCPULogic::saturatingAdd);
            }
        }
    }

    private Map<AEKey, Long> pendingContribution(IPatternDetails details, long batches) {
        Map<AEKey, Long> contribution = new LinkedHashMap<>();
        var plannedBatches = job.plannedInputs.get(details);
        if (plannedBatches != null && !plannedBatches.isEmpty()) {
            for (var plannedBatch : plannedBatches) {
                for (var selection : plannedBatch.selectedInputs()) {
                    for (var alternative : selection.alternatives()) {
                        addPendingInputAmount(
                            contribution,
                            alternative.template().what(),
                            scaledPatternAmount(alternative.template().amount(), alternative.multiplier()),
                            plannedBatch.remaining()
                        );
                    }
                }
            }
            return contribution;
        }
        Map<AEKey, Long> perBatch = new LinkedHashMap<>();
        for (var input : details.getInputs()) {
            Map<AEKey, Long> inputAmounts = new LinkedHashMap<>();
            for (var possible : input.getPossibleInputs()) {
                if (possible != null && possible.amount() > 0L) {
                    inputAmounts.merge(
                        possible.what(),
                        scaledPatternAmount(possible.amount(), input.getMultiplier()),
                        Math::max
                    );
                }
            }
            for (var entry : inputAmounts.entrySet()) {
                perBatch.merge(entry.getKey(), entry.getValue(), ECOCraftingCPULogic::saturatingAdd);
            }
        }
        for (var entry : perBatch.entrySet()) {
            addPendingInputAmount(contribution, entry.getKey(), entry.getValue(), batches);
        }
        return contribution;
    }

    private static <K> void subtractPendingAmount(Map<K, Long> amounts, K key, long amount) {
        if (amount <= 0L) {
            return;
        }
        long current = amounts.getOrDefault(key, 0L);
        if (current <= amount) {
            amounts.remove(key);
        } else {
            amounts.put(key, current - amount);
        }
    }

    private static void addPendingInputAmount(
        Map<AEKey, Long> totals,
        AEKey key,
        long perBatchAmount,
        long batches
    ) {
        if (key == null || perBatchAmount <= 0L || batches <= 0L) {
            return;
        }
        totals.merge(
            key,
            scaledPatternAmount(perBatchAmount, batches),
            ECOCraftingCPULogic::saturatingAdd
        );
    }

    private long safePendingInputAmount(AEKey key) {
        try {
            return pendingInputAmount(key);
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    private static String classifyInputSource(
            long cpuAmount,
            long networkAmount,
            long reservedAtStart,
            long waitingFor,
            long pendingInput,
            long requiredItems,
            boolean exactKeyMismatch,
            boolean exactKeyMismatchKnown) {
        if (cpuAmount > 0L) {
            return "cpu_inventory";
        }
        if (waitingFor > 0L) {
            return "waiting_for_output";
        }
        if (pendingInput > 0L) {
            return "planned_upstream_not_dispatched";
        }
        if (networkAmount > 0L) {
            if (networkAmount < requiredItems) {
                return "insufficient_network_quantity";
            }
            if (reservedAtStart > 0L) {
                return "reserved_at_start_now_gone";
            }
            return "network_only_not_reserved";
        }
        if (networkAmount == 0L) {
            if (exactKeyMismatchKnown && exactKeyMismatch) {
                return "exact_key_mismatch";
            }
            return "network_absent";
        }
        return "network_unknown";
    }

    private static boolean isNormalDependencyWaitSource(String source) {
        return "waiting_for_output".equals(source)
            || "planned_upstream_not_dispatched".equals(source);
    }

    private static InputReservationResult classifyInputReservation(
            boolean hasNormalDependencyWait,
            boolean hasNonNormalMissingInput) {
        return hasNonNormalMissingInput
            ? InputReservationResult.HARD_FAILURE
            : hasNormalDependencyWait
                ? InputReservationResult.DEPENDENCY_WAIT
                : InputReservationResult.SUCCESS;
    }

    static boolean shouldSuppressMissingInputDiagnostic(
            boolean hasNormalDependencyWait,
            boolean hasNonNormalMissingInput) {
        return classifyInputReservation(hasNormalDependencyWait, hasNonNormalMissingInput)
            == InputReservationResult.DEPENDENCY_WAIT;
    }

    private static boolean hasDifferentExactVariant(KeyCounter available, AEKey requested) {
        for (var entry : available) {
            if (entry.getLongValue() <= 0L) {
                continue;
            }
            AEKey candidate = entry.getKey();
            if (candidate != null
                && candidate.getClass() == requested.getClass()
                && Objects.equals(candidate.getPrimaryKey(), requested.getPrimaryKey())
                && !candidate.equals(requested)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasReadyProvider(List<ICraftingProvider> providers, IPatternDetails details) {
        for (ICraftingProvider provider : providers) {
            if (!provider.isBusy()
                && !shouldSkipSlowPathProvider(provider, details)
                && !ae2ltBatchBridge.shouldSkip(provider, details)
                && !megacellsBatchBridge.shouldSkip(provider, details)) {
                return true;
            }
        }
        return false;
    }

    private long tryPushVerifiedFastPathBatch(
            ExecutingCraftingJob job,
            IPatternDetails details,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ECOCraftingPatternBusBlockEntity> patternBuses,
        IEnergyService energyService,
        double patternPower,
        long taskRemaining,
        Set<net.minecraft.resources.ResourceLocation> fuzzyItemIds,
        boolean discardPlannedInputs) {
        if (patternBuses.isEmpty()) {
            ECOFastPathDiagnostics.logIneligible(execution, ECOFastPathFallbackReason.NO_ECO_PATTERN_BUS,
                ECOFastPathStage.ELIGIBILITY, cpuBlockPos(),
                TickHandler.instance().getCurrentTick(), "batch_attempt_has_no_eco_pattern_bus");
            return 0;
        }
        if (!canAttemptBatchFastPath(execution)) {
            ECOFastPathFallbackReason reason = execution.fallbackReason() != null
                ? execution.fallbackReason()
                : !NEConfig.ecoAe2FastPathEnabled
                    ? ECOFastPathFallbackReason.FAST_PATH_DISABLED
                    : NEConfig.postCraftingEvent
                        ? ECOFastPathFallbackReason.POST_CRAFTING_EVENT
                        : ECOFastPathFallbackReason.KEY_BUILD_FAILED;
            ECOFastPathDiagnostics.logFailure(execution, reason, ECOFastPathStage.ELIGIBILITY,
                cpuBlockPos(), TickHandler.instance().getCurrentTick(),
                "batch_gate_rejected eligible=" + execution.fastPathEligible()
                    + " keyPresent=" + (execution.key() != null));
            return 0;
        }
        if (taskRemaining <= 0) {
            return 0;
        }

        boolean timingEnabled = NEConfig.debugEcoFastPath;

        var reusablePlan = ECOReusableCraftingPlan.of(
            execution.inputItems(), execution.expectedContainerItems());
        boolean reusableLease = !reusablePlan.reusableInputs().isEmpty();
        long minimumBatchSize = reusableLease ? 1L : 2L;
        if (taskRemaining < minimumBatchSize) {
            return 0;
        }
        // Offer the remaining task subject to the amount of final output that is still needed.
        // F9 virtual workers can accept the whole request in one lane, so the output-side bound
        // must be applied before selecting a worker; otherwise a recipe that produces two final
        // items per craft can turn a request for 500 into a 1000-item batch.
        long requested = Math.min(
            calculateBatchRequestSize(taskRemaining),
            maxCraftsNeededForFinalOutput(execution)
        );
        if (requested < minimumBatchSize) {
            return 0;
        }
        ECOCraftingPatternBusBlockEntity selectedPatternBus = null;
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer selectedOffer = null;
        Set<ECOCraftingSystemBlockEntity> visitedControllers = new HashSet<>();
        Set<NECraftingNetworkCluster> visitedNetworkClusters =
            Collections.newSetFromMap(new IdentityHashMap<>());
        long lookupStarted = timingEnabled ? System.nanoTime() : 0L;
        int busesScanned = 0;
        int candidateBuses = 0;
        int verifiedCandidates = 0;
        int offerLookups = 0;
        for (ECOCraftingPatternBusBlockEntity patternBus : patternBuses) {
            busesScanned++;
            ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
            if (controller == null) {
                continue;
            }
            candidateBuses++;
            NECraftingNetworkCluster networkCluster = patternBus.getFastPathNetworkCluster();
            if (networkCluster != null
                ? !visitedNetworkClusters.add(networkCluster)
                : !visitedControllers.add(controller)) {
                continue;
            }
            offerLookups++;
            var offer = patternBus.findBatchFastPathOffer(execution, requested, job.link.getCraftingID());
            if (offer != null) {
                verifiedCandidates++;
            }
            if (offer != null && offer.maxBatchSize() >= minimumBatchSize
                && (selectedOffer == null || offer.maxBatchSize() > selectedOffer.maxBatchSize())) {
                selectedPatternBus = patternBus;
                selectedOffer = offer;
                if (offer.maxBatchSize() >= requested) {
                    break;
                }
            }
        }
        long lookupMicros = elapsedMicros(lookupStarted, timingEnabled);
        ECOFastPathDiagnostics.logCacheLookup(
            execution,
            cpuBlockPos(),
            TickHandler.instance().getCurrentTick(),
            patternBuses.size(),
            busesScanned,
            candidateBuses,
            verifiedCandidates,
            offerLookups,
            lookupMicros
        );
        if (selectedPatternBus == null || selectedOffer == null) {
            ECOFastPathDiagnostics.logExpectedFallback(execution, ECOFastPathFallbackReason.NO_BATCH_OFFER,
                ECOFastPathStage.CACHE_LOOKUP, cpuBlockPos(),
                TickHandler.instance().getCurrentTick(),
                "requestedBefore=" + requested + " probeCrafts=1 expectedRemaining=" + Math.max(0L, requested - 1L)
                    + " patternBuses=" + patternBuses.size()
                    + " no_worker_exposed_a_matching_verified_result");
            return 0;
        }

        ECOCraftingSystemBlockEntity controller = selectedPatternBus.getCraftingController();
        if (controller == null) {
            return 0;
        }
        ECOCraftingSystemBlockEntity workerController = selectedOffer.worker().getCluster() == null
            ? null
            : selectedOffer.worker().getCluster().getController();
        if (workerController == null) {
            return 0;
        }

        long totalApplyStarted = timingEnabled ? System.nanoTime() : 0L;
        long resourceLimitMicros = 0L;
        long offeredBatchSize = Math.min(requested, selectedOffer.maxBatchSize());
        boolean virtualCrafting = workerController.isVirtualCraftingMode();
        long resourceLimitStarted = timingEnabled ? System.nanoTime() : 0L;
        long batchSize = ECOBatchCraftingHelper.maxSafeBatchSize(
            reusablePlan.consumedInputsPerCraft(), execution.expectedOutputs(),
            reusablePlan.ordinaryRemainingPerCraft(), offeredBatchSize);
        long safeBatchSize = batchSize;
        long energyBatchSize = -1L;
        long coolantBatchSize = -1L;
        if (batchSize < minimumBatchSize) {
            ECOFastPathDiagnostics.logFailure(execution, ECOFastPathFallbackReason.BATCH_AMOUNT_OVERFLOW,
                ECOFastPathStage.RESOURCE_LIMIT, selectedOffer.worker().getBlockPos(),
                TickHandler.instance().getCurrentTick(), "offered=" + offeredBatchSize + " safe=" + batchSize);
            return 0;
        }
        if (!virtualCrafting) {
            int normalOfferedBatchSize = (int) Math.min(Integer.MAX_VALUE, batchSize);
            batchSize = normalOfferedBatchSize;
            energyBatchSize = maxBatchSizeFromEnergy(energyService, patternPower, normalOfferedBatchSize);
            if (energyBatchSize < minimumBatchSize) {
                selectedOffer.worker().getFastPathCache().recordCoolantReject();
                ECOFastPathDiagnostics.logFailure(execution, ECOFastPathFallbackReason.ENERGY_LIMIT,
                    ECOFastPathStage.RESOURCE_LIMIT, selectedOffer.worker().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "requested=" + requested + " offered=" + offeredBatchSize
                        + " affordable=" + energyBatchSize + " patternPower=" + patternPower);
                return 0;
            }
            coolantBatchSize = workerController.getCraftingCoolantCraftLimit(
                5, workerController.getCoolingRequirementForCurrentNetwork(), (int) energyBatchSize
            );
            batchSize = Math.min(batchSize, Math.min(energyBatchSize, coolantBatchSize));
            if (coolantBatchSize < minimumBatchSize) {
                selectedOffer.worker().getFastPathCache().recordCoolantReject();
                ECOFastPathDiagnostics.logFailure(execution, ECOFastPathFallbackReason.COOLANT_LIMIT,
                    ECOFastPathStage.RESOURCE_LIMIT, selectedOffer.worker().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "requested=" + requested + " offered=" + offeredBatchSize
                        + " energyLimit=" + energyBatchSize + " coolantLimit=" + coolantBatchSize);
                return 0;
            }
        }
        if (batchSize < minimumBatchSize) {
            return 0;
        }

        long extraCrafts = batchSize - 1L;
        long prepareInventorySnapshotStarted = timingEnabled ? System.nanoTime() : 0L;
        var inventoryBatchLimit = ECOBatchCraftingHelper.inventoryBatchLimit(
            new ECOFuzzyCraftingInventory(inventory, job.fuzzyItemIds),
            reusablePlan.consumedInputsPerCraft(), extraCrafts, job.fuzzyItemIds);
        long availableExtraCrafts = inventoryBatchLimit.crafts();
        long inventoryBatchSize;
        try {
            inventoryBatchSize = Math.addExact(availableExtraCrafts, 1L);
        } catch (ArithmeticException e) {
            inventoryBatchSize = Long.MAX_VALUE;
        }
        batchSize = Math.min(batchSize, inventoryBatchSize);
        long prepareInventorySnapshotMicros = elapsedMicros(prepareInventorySnapshotStarted, timingEnabled);
        resourceLimitMicros = elapsedMicros(resourceLimitStarted, timingEnabled);
        long prepareStarted = timingEnabled ? System.nanoTime() : 0L;
        if (batchSize < minimumBatchSize) {
            String context = "requested=" + requested + " resourceBatch=" + (extraCrafts + 1)
                + " availableExtraCrafts=" + availableExtraCrafts;
            if (availableExtraCrafts <= 0L) {
                ECOFastPathDiagnostics.logNotBatched(
                    execution,
                    ECOFastPathFallbackReason.INVENTORY_LIMIT,
                    ECOFastPathStage.RESOURCE_LIMIT,
                    selectedOffer.worker().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    context
                );
            } else {
                ECOFastPathDiagnostics.logFailure(execution, ECOFastPathFallbackReason.INVENTORY_LIMIT,
                    ECOFastPathStage.RESOURCE_LIMIT, selectedOffer.worker().getBlockPos(),
                    TickHandler.instance().getCurrentTick(), context);
            }
            return 0;
        }

        long preparePatternStarted = timingEnabled ? System.nanoTime() : 0L;
        ECOFastPathDiagnostics.PatternPrepareTiming patternPrepareTiming = timingEnabled
            ? ECOFastPathDiagnostics.logBatchDecision(
                execution,
                selectedOffer.worker().getBlockPos(),
                TickHandler.instance().getCurrentTick(),
                taskRemaining,
                requested,
                selectedOffer.maxBatchSize(),
                safeBatchSize,
                energyBatchSize,
                coolantBatchSize,
                inventoryBatchSize,
                describeInventoryConstraint(inventoryBatchLimit),
                true,
                1L,
                inventoryBatchLimit.available(),
                inventoryBatchLimit.perCraft(),
                availableExtraCrafts,
                extraCrafts,
                lookupMicros,
                busesScanned,
                candidateBuses,
                verifiedCandidates,
                offerLookups,
                batchSize
            )
            : ECOFastPathDiagnostics.PatternPrepareTiming.empty();

        long preparePatternMicros = elapsedMicros(preparePatternStarted, timingEnabled);
        long prepareInputsStarted = timingEnabled ? System.nanoTime() : 0L;
        var extraInputs = reusablePlan.extraInputs(batchSize - 1);
        long prepareInputsMicros = elapsedMicros(prepareInputsStarted, timingEnabled);
        List<GenericStack> extractedExtraInputs = List.of();
        boolean extraInputsExtracted = false;
        BatchDispatchState dispatchState = BatchDispatchState.PREPARED;
        ECOBatchEnergyReservation energyReservation = null;
        long energyReservationMicros = 0L;
        long inputExtractionMicros = 0L;
        long workerCommitMicros = 0L;
        long cpuAccountingMicros = 0L;
        long prepareMicros = 0L;
        long prepareMiscMicros = 0L;
        long prepareWorkerStateMicros = 0L;
        long postExtractionMicros = 0L;
        long postCommitMicros = 0L;
        long finalizeMicros = 0L;
        try {
            long prepareMiscStarted = timingEnabled ? System.nanoTime() : 0L;
            double requiredPower = virtualCrafting ? 0.0D : patternPower * batchSize;
            if (!virtualCrafting && !Double.isFinite(requiredPower)) {
                ECOFastPathDiagnostics.logFailure(execution, ECOFastPathFallbackReason.ENERGY_LIMIT,
                    ECOFastPathStage.RESOURCE_LIMIT, selectedOffer.worker().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "requiredPowerNotFinite=" + requiredPower + " batch=" + batchSize);
                return 0;
            }
            prepareMiscMicros = elapsedMicros(prepareMiscStarted, timingEnabled);
            if (!virtualCrafting) {
                prepareMicros = elapsedMicros(prepareStarted, timingEnabled);
                long energyReservationStarted = timingEnabled ? System.nanoTime() : 0L;
                try {
                    energyReservation = ECOBatchEnergyReservation.tryReserve(energyService, requiredPower, false);
                    if (energyReservation == null || !energyReservation.isFullyReserved()) {
                        if (energyReservation != null) {
                            RuntimeException refundFailure = energyReservation.refundSafely();
                            if (refundFailure != null) {
                                LOGGER.error("ECO batch energy refund failed after a partial reservation", refundFailure);
                            }
                        }
                        ECOFastPathDiagnostics.logFailure(execution, ECOFastPathFallbackReason.ENERGY_LIMIT,
                            ECOFastPathStage.RESOURCE_LIMIT, selectedOffer.worker().getBlockPos(),
                            TickHandler.instance().getCurrentTick(), "requiredPower=" + requiredPower);
                        // Keep the first craft's inputs for the normal slow path. No extra inputs were taken.
                        return 0;
                    }
                } catch (RuntimeException e) {
                    ECOFastPathDiagnostics.logBatchFailure(
                        new ECOBatchCraftingRequest(details, execution.key(), batchSize, execution.inputItems(),
                            execution.expectedOutputs(), execution.expectedContainerItems(), job.link.getCraftingID()),
                        ECOFastPathFallbackReason.ENERGY_LIMIT, ECOFastPathStage.ENERGY_CHARGE,
                        selectedOffer.worker().getBlockPos(), TickHandler.instance().getCurrentTick(),
                        "energy_service_exception=" + e.getMessage());
                    throw e;
                }
                finally {
                    energyReservationMicros = elapsedMicros(energyReservationStarted, timingEnabled);
                }
            }
            if (virtualCrafting) {
                prepareMicros = elapsedMicros(prepareStarted, timingEnabled);
            }
            long inputExtractionStarted = timingEnabled ? System.nanoTime() : 0L;
            try {
            extractedExtraInputs = ECOBatchCraftingHelper.extractExactReturning(
                    new ECOFuzzyCraftingInventory(inventory, job.fuzzyItemIds), extraInputs, job.fuzzyItemIds);
            consumeRetainedFinalOutputFrom(extractedExtraInputs);
            } catch (RuntimeException e) {
                ECOFastPathDiagnostics.logFailure(execution, ECOFastPathFallbackReason.INPUT_RESERVATION_FAILED,
                    ECOFastPathStage.INPUT_RESERVATION, selectedOffer.worker().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "batch=" + batchSize + " extraInputs=" + extraInputs + " error=" + e.getMessage());
                throw e;
            } finally {
                inputExtractionMicros = elapsedMicros(inputExtractionStarted, timingEnabled);
            }
            extraInputsExtracted = true;
            dispatchState = BatchDispatchState.RESOURCES_RESERVED;
            long postExtractionStarted = timingEnabled ? System.nanoTime() : 0L;
            long prepareWorkerStateStarted = timingEnabled ? System.nanoTime() : 0L;
            var request = new ECOBatchCraftingRequest(
                    details,
                    execution.key(),
                    batchSize,
                    execution.inputItems(),
                    execution.expectedOutputs(),
                    execution.expectedContainerItems(),
                    ECOBatchCraftingHelper.combine(reusablePlan.consumedInputsPerCraft(), extractedExtraInputs),
                    job.link.getCraftingID());
            job.beginPendingAccounting(
                details,
                execution.expectedOutputs(),
                reusablePlan.batchRemaining(batchSize),
                reusablePlan.reusableInputs(),
                request.consumedInputTotal(),
                batchSize,
                true,
                discardPlannedInputs
            );
            prepareWorkerStateMicros = elapsedMicros(prepareWorkerStateStarted, timingEnabled);
            long workerCommitStarted = timingEnabled ? System.nanoTime() : 0L;
            boolean workerAccepted;
            try {
                postExtractionMicros = elapsedMicros(postExtractionStarted, timingEnabled);
                workerAccepted = selectedPatternBus.pushBatch(request, selectedOffer);
            } finally {
                workerCommitMicros = elapsedMicros(workerCommitStarted, timingEnabled);
            }
            if (!workerAccepted) {
                ECOFastPathDiagnostics.logBatchFailure(request, ECOFastPathFallbackReason.PROVIDER_REJECTED,
                    ECOFastPathStage.PROVIDER_DISPATCH, selectedPatternBus.getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "pattern_bus_or_network_cluster_returned_false worker="
                        + selectedOffer.worker().getBlockPos().toShortString());
                RuntimeException refundFailure = energyReservation == null ? null : energyReservation.refundSafely();
                if (refundFailure != null) {
                    LOGGER.error("ECO batch energy refund failed after provider rejection", refundFailure);
                }
                job.clearPendingAccounting();
                rollbackBatchInputs(inventory, firstCraftingContainer, extractedExtraInputs, true, true);
                return 0;
            }
            long postCommitStarted = timingEnabled ? System.nanoTime() : 0L;
            job.markPendingAccountingOwnershipTransferred();
            cpu.markDirty();
            dispatchState = BatchDispatchState.PROVIDER_ACCEPTED;
            // The worker owns consumed inputs from this point onward. Exact reusable catalysts
            // never enter the worker ledger, so return their single reserved copy to the CPU. This
            // lets the scheduler lease the same immutable catalyst to other free FX workers.
            dispatchState = BatchDispatchState.INPUT_OWNERSHIP_TRANSFERRED;
            try {
                postCommitMicros = elapsedMicros(postCommitStarted, timingEnabled);
                long cpuAccountingStarted = timingEnabled ? System.nanoTime() : 0L;
                try {
                    if (energyReservation != null) {
                        energyReservation.commit();
                    }
                    if (this.job == job) {
                        if (!applyPendingAccounting(job)) {
                            return -1;
                        }
                        dispatchState = BatchDispatchState.ACCOUNTING_APPLIED;
                    }
                } finally {
                    cpuAccountingMicros = elapsedMicros(cpuAccountingStarted, timingEnabled);
                }
            } catch (RuntimeException e) {
                selectedOffer.worker().getFastPathCache().recordException();
                ECOFastPathDiagnostics.logBatchFailure(request, ECOFastPathFallbackReason.ACCOUNTING_FAILED,
                    ECOFastPathStage.ACCOUNTING, selectedOffer.worker().getBlockPos(),
                    TickHandler.instance().getCurrentTick(), "record_pushed_pattern_exception=" + e.getMessage());
                suspendAfterAcceptedAccountingFailure(job, e,
                    "ECO batch was accepted, but its CPU accounting update failed");
                return -1;
            }
            long finalizeStarted = timingEnabled ? System.nanoTime() : 0L;
            long totalApplyMicros = elapsedMicros(totalApplyStarted, timingEnabled);
            finalizeMicros = elapsedMicros(finalizeStarted, timingEnabled);
            long unattributedMicros = unattributedApplyMicros(
                totalApplyMicros,
                resourceLimitMicros,
                energyReservationMicros,
                inputExtractionMicros,
                workerCommitMicros,
                cpuAccountingMicros,
                prepareMicros,
                postExtractionMicros,
                postCommitMicros,
                finalizeMicros
            );
            ECOFastPathDiagnostics.recordBatchApplyTiming(
                TickHandler.instance().getCurrentTick(),
                totalApplyMicros,
                workerCommitMicros,
                prepareMicros,
                unattributedMicros
            );
            ECOFastPathDiagnostics.logBatchApplyTiming(
                execution,
                selectedOffer.worker().getBlockPos(),
                TickHandler.instance().getCurrentTick(),
                batchSize,
                lookupMicros,
                resourceLimitMicros,
                energyReservationMicros,
                inputExtractionMicros,
                workerCommitMicros,
                cpuAccountingMicros,
                prepareMicros,
                preparePatternMicros,
                patternPrepareTiming.patternDetailsMicros(),
                patternPrepareTiming.patternInputsMicros(),
                patternPrepareTiming.patternOutputsMicros(),
                patternPrepareTiming.patternKeyNormalizeMicros(),
                patternPrepareTiming.patternHashOrLookupMicros(),
                patternPrepareTiming.patternMiscMicros(),
                prepareInventorySnapshotMicros,
                prepareInputsMicros,
                prepareWorkerStateMicros,
                prepareMiscMicros,
                postExtractionMicros,
                postCommitMicros,
                finalizeMicros,
                unattributedMicros,
                totalApplyMicros,
                patternPrepareTiming
            );

            // Debug logging for over-delivery investigation
            if (NEConfig.debugEcoFastPath) {
                AELog.info("NeoECO CPU FastPath dispatched: batchSize=%d taskRemaining=%d->%d requested=%d",
                    batchSize, taskRemaining, taskRemaining - batchSize, requested);
            }

            return batchSize;
        } catch (RuntimeException e) {
            selectedOffer.worker().getFastPathCache().recordException();
            boolean providerOwnsInputs = dispatchState.providerOwnsInputs();
            ECOFastPathDiagnostics.logFailure(execution,
                providerOwnsInputs ? ECOFastPathFallbackReason.ACCOUNTING_FAILED
                    : ECOFastPathFallbackReason.PROVIDER_REJECTED,
                providerOwnsInputs ? ECOFastPathStage.ACCOUNTING : ECOFastPathStage.PROVIDER_DISPATCH,
                selectedOffer.worker().getBlockPos(), TickHandler.instance().getCurrentTick(),
                "batch=" + batchSize + " dispatchState=" + dispatchState
                    + " error=" + e.getMessage());
            if (providerOwnsInputs) {
                suspendAfterAcceptedAccountingFailure(job, e,
                    "ECO batch was accepted, but its CPU output accounting update failed");
                // Do not report the batch as completed. The task remains frozen while the accepted
                // worker state and the CPU route are available for recovery instead of scheduling
                // the same physical inputs a second time.
                return -1;
            }
            RuntimeException refundFailure = energyReservation == null ? null : energyReservation.refundSafely();
            if (refundFailure != null) {
                LOGGER.error("ECO batch energy refund failed while rolling back a pre-submit failure", refundFailure);
            }
            rollbackBatchInputs(
                inventory, firstCraftingContainer, extractedExtraInputs, true, extraInputsExtracted
            );
            job.clearPendingAccounting();
            return 0;
        } catch (Error e) {
            selectedOffer.worker().getFastPathCache().recordException();
            if (!dispatchState.providerOwnsInputs()) {
                if (energyReservation != null) {
                    RuntimeException refundFailure = energyReservation.refundSafely();
                    if (refundFailure != null) {
                        LOGGER.error("ECO batch energy refund failed while rolling back an error", refundFailure);
                    }
                }
                rollbackBatchInputs(
                    inventory, firstCraftingContainer, extractedExtraInputs, true, extraInputsExtracted
                );
                job.clearPendingAccounting();
            }
            throw e;
        }
    }

    private long maxCraftsNeededForFinalOutput(ECOExtractedPatternExecution execution) {
        if (job == null || job.finalOutput == null) {
            return Long.MAX_VALUE;
        }
        long waitingForFinalOutput = job.waitingFor.extract(
            job.finalOutput.what(), Long.MAX_VALUE, Actionable.SIMULATE
        );
        long inFlightFinalOutput = saturatingAdd(
            waitingForFinalOutput,
            job.bufferedFinalOutput.amount()
        );

        long maxNeeded = ECOBatchCraftingHelper.limitByFinalOutputDemand(
            job.finalOutput,
            job.remainingAmount,
            inFlightFinalOutput,
            execution.expectedOutputs(),
            Long.MAX_VALUE
        );

        // Debug logging for over-delivery investigation
        if (NEConfig.debugEcoFastPath && maxNeeded != Long.MAX_VALUE) {
            AELog.info("NeoECO CPU maxCraftsNeededForFinalOutput: remainingAmount=%d waitingFor=%d buffered=%d inFlight=%d maxNeeded=%d",
                job.remainingAmount, waitingForFinalOutput, job.bufferedFinalOutput.amount(),
                inFlightFinalOutput, maxNeeded);
        }

        return maxNeeded;
    }

    private long finalOutputAmountPerCraft(List<GenericStack> outputsPerCraft) {
        if (job == null || job.finalOutput == null) {
            return 0L;
        }
        long amount = 0L;
        for (GenericStack output : outputsPerCraft) {
            if (output != null && output.what().matches(job.finalOutput)) {
                amount = saturatingAdd(amount, output.amount());
            }
        }
        return amount;
    }

    static long maxCraftsForFinalOutputDemand(
            long remainingAmount,
            long inFlightAmount,
            long outputAmountPerCraft) {
        if (outputAmountPerCraft <= 0L) {
            return Long.MAX_VALUE;
        }
        long outstanding = remainingAmount - Math.max(0L, inFlightAmount);
        if (outstanding <= 0L) {
            return 0L;
        }
        return 1L + (outstanding - 1L) / outputAmountPerCraft;
    }

    static long calculateBatchRequestSize(long taskRemaining) {
        return Math.max(0L, taskRemaining);
    }

    static boolean shouldUsePlannedInputs(long taskRemaining, long plannedInputCount) {
        return taskRemaining <= 0L || plannedInputCount >= taskRemaining;
    }

    static boolean shouldRetryInputExtraction(@Nullable Long blockedRevision, long currentRevision) {
        return blockedRevision == null || blockedRevision.longValue() != currentRevision;
    }

    /** The single ready/blocked/deferred scheduler used by the production CPU task loop. */
    static final class TaskScheduler<T> {
        private final ArrayDeque<T> ready = new ArrayDeque<>();
        private final Set<T> queued = Collections.newSetFromMap(new IdentityHashMap<>());
        private final IdentityHashMap<T, InputDependencies> blocked = new IdentityHashMap<>();
        private final IdentityHashMap<T, Long> deferredUntil = new IdentityHashMap<>();
        private final Map<AEKey, Set<T>> blockedByExact = new LinkedHashMap<>();
        private final Map<net.minecraft.resources.ResourceLocation, Set<T>> blockedByFuzzy =
            new LinkedHashMap<>();
        private final Set<T> blockedOnAnyChange = Collections.newSetFromMap(new IdentityHashMap<>());
        @Nullable
        private T leased;
        private long currentTick = Long.MIN_VALUE;

        void startJob(List<T> tasks) {
            ready.clear();
            queued.clear();
            blocked.clear();
            deferredUntil.clear();
            blockedByExact.clear();
            blockedByFuzzy.clear();
            blockedOnAnyChange.clear();
            leased = null;
            for (T task : tasks) {
                enqueue(task);
            }
        }

        @Nullable
        T poll() {
            T task = ready.poll();
            if (task != null) {
                queued.remove(task);
                leased = task;
            }
            return task;
        }

        void block(T task, InputDependencies dependencies) {
            if (task == leased) {
                leased = null;
            }
            if (queued.remove(task)) {
                ready.remove(task);
            }
            removeBlockedIndexes(task);
            deferredUntil.remove(task);
            blocked.put(task, dependencies);
            if (dependencies.wakeOnAnyChange()) {
                blockedOnAnyChange.add(task);
            }
            for (AEKey key : dependencies.exactKeys()) {
                blockedByExact.computeIfAbsent(key, ignored ->
                    Collections.newSetFromMap(new IdentityHashMap<>())).add(task);
            }
            for (var fuzzyId : dependencies.fuzzyItemIds()) {
                blockedByFuzzy.computeIfAbsent(fuzzyId, ignored ->
                    Collections.newSetFromMap(new IdentityHashMap<>())).add(task);
            }
        }

        void wake(AEKey key) {
            Set<T> awakened = new java.util.LinkedHashSet<>();
            Set<T> exact = blockedByExact.get(key);
            if (exact != null) {
                awakened.addAll(exact);
            }
            awakened.addAll(blockedOnAnyChange);
            wakeTasks(awakened);
        }

        void wakeFuzzy(net.minecraft.resources.ResourceLocation fuzzyItemId) {
            Set<T> awakened = blockedByFuzzy.get(fuzzyItemId);
            if (awakened != null) {
                wakeTasks(List.copyOf(awakened));
            }
        }

        void beginTick(long tick) {
            currentTick = tick;
            releaseLeasedIfUnresolved();
            List<T> awakened = new ArrayList<>();
            for (var entry : deferredUntil.entrySet()) {
                if (entry.getValue() <= tick) {
                    awakened.add(entry.getKey());
                }
            }
            for (T task : awakened) {
                deferredUntil.remove(task);
                enqueue(task);
            }
        }

        void deferUntilNextTick(T task) {
            if (task == leased) {
                leased = null;
            }
            if (queued.remove(task)) {
                ready.remove(task);
            }
            long nextTick = currentTick == Long.MAX_VALUE ? Long.MAX_VALUE : currentTick + 1L;
            deferredUntil.put(task, nextTick);
        }

        void requeue(T task) {
            if (task == leased) {
                leased = null;
            }
            deferredUntil.remove(task);
            enqueue(task);
        }

        void remove(T task) {
            if (task == leased) {
                leased = null;
            }
            queued.remove(task);
            ready.remove(task);
            deferredUntil.remove(task);
            removeBlockedIndexes(task);
            blocked.remove(task);
        }

        int readySize() {
            return ready.size();
        }

        int blockedSize() {
            return blocked.size();
        }

        private void wakeTasks(Iterable<T> tasks) {
            for (T task : tasks) {
                if (blocked.containsKey(task)) {
                    removeBlockedIndexes(task);
                    blocked.remove(task);
                    enqueue(task);
                }
            }
        }

        void releaseLeasedIfUnresolved() {
            if (leased != null
                && !blocked.containsKey(leased)
                && !deferredUntil.containsKey(leased)
                && !queued.contains(leased)) {
                T previous = leased;
                leased = null;
                enqueue(previous);
            } else {
                leased = null;
            }
        }

        private void removeBlockedIndexes(T task) {
            InputDependencies dependencies = blocked.get(task);
            if (dependencies == null) {
                blockedOnAnyChange.remove(task);
                return;
            }
            blockedOnAnyChange.remove(task);
            for (AEKey key : dependencies.exactKeys()) {
                removeIndexEntry(blockedByExact, key, task);
            }
            for (var fuzzyId : dependencies.fuzzyItemIds()) {
                removeIndexEntry(blockedByFuzzy, fuzzyId, task);
            }
        }

        private static <K, T> void removeIndexEntry(Map<K, Set<T>> index, K key, T task) {
            Set<T> tasks = index.get(key);
            if (tasks != null) {
                tasks.remove(task);
                if (tasks.isEmpty()) {
                    index.remove(key);
                }
            }
        }

        private void enqueue(T task) {
            if (!blocked.containsKey(task) && !deferredUntil.containsKey(task) && queued.add(task)) {
                ready.add(task);
            }
        }
    }

    record InputDependencies(
        Set<AEKey> exactKeys,
        Set<net.minecraft.resources.ResourceLocation> fuzzyItemIds,
        boolean wakeOnAnyChange
    ) {
        InputDependencies {
            exactKeys = Set.copyOf(exactKeys);
            fuzzyItemIds = Set.copyOf(fuzzyItemIds);
        }

        private boolean matches(AEKey key) {
            return wakeOnAnyChange || exactKeys.contains(key);
        }

        private boolean matchesFuzzy(net.minecraft.resources.ResourceLocation fuzzyItemId) {
            return wakeOnAnyChange || fuzzyItemIds.contains(fuzzyItemId);
        }
    }

    static boolean shouldUsePlannedInputsForDispatch(
        boolean ecoFastPathCandidate,
        boolean plannedInputsPresent,
        long taskRemaining,
        long plannedInputCount
    ) {
        // A fuzzy planner selection is still the authoritative physical choice for the next
        // segment. Resolving it before extraction keeps ECO workers and ordinary providers on
        // the same concrete input set; batch sizing is handled separately by the caller.
        return plannedInputsPresent
            && (ecoFastPathCandidate
                ? plannedInputCount > 0L
                : shouldUsePlannedInputs(taskRemaining, plannedInputCount));
    }

    private void rebuildTaskScheduler() {
        taskScheduler.startJob(job == null ? List.of() : List.copyOf(job.tasks.keySet()));
    }

    private void removeTask(IPatternDetails details) {
        if (job == null) {
            return;
        }
        job.tasks.remove(details);
        taskScheduler.remove(details);
        refreshPendingInputTask(details);
        reconcileRetainedFinalOutput();
    }

    private void discardPlannedInputs(IPatternDetails details) {
        if (job != null) {
            job.discardPlannedInputs(details);
            refreshPendingInputTask(details);
            reconcileRetainedFinalOutput();
        }
    }

    private void consumePlannedInputs(IPatternDetails details, long crafts) {
        if (job != null) {
            job.consumePlannedInputs(details, crafts);
            refreshPendingInputTask(details);
            reconcileRetainedFinalOutput();
        }
    }

    private InputDependencies dependenciesFor(IPatternDetails details) {
        Set<AEKey> exactKeys = new HashSet<>();
        for (var input : details.getInputs()) {
            for (var possible : input.getPossibleInputs()) {
                if (possible != null && possible.amount() > 0L) {
                    exactKeys.add(possible.what());
                }
            }
        }
        return new InputDependencies(exactKeys, job == null ? Set.of() : job.fuzzyItemIds, false);
    }

    private static String describeInventoryConstraint(
        ECOBatchCraftingHelper.InventoryBatchLimit limit
    ) {
        if (limit.limitingKey() == null) {
            return "none";
        }
        return limit.limitingKey()
            + " available=" + limit.available()
            + " perCraft=" + limit.perCraft();
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
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs, this::postInventoryChange);
        }
    }

    private boolean canAttemptBatchFastPath(ECOExtractedPatternExecution execution) {
        return execution.key() != null
                && execution.fastPathEligible()
                && NEConfig.ecoAe2FastPathEnabled
                && !NEConfig.postCraftingEvent;
    }

    private net.minecraft.core.BlockPos cpuBlockPos() {
        return cpu.getOwner() == null
            ? net.minecraft.core.BlockPos.ZERO
            : cpu.getOwner().getBlockPos();
    }

    private static boolean canBatchConfiguredFuzzyInputs(
        ECOExtractedPatternExecution execution,
        Set<net.minecraft.resources.ResourceLocation> fuzzyItemIds
    ) {
        // A component-insensitive plan is resolved one concrete craft at a time. Batch snapshots
        // cannot safely reuse the first component-dependent assembly for later variants.
        return fuzzyItemIds.isEmpty();
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

    private static long elapsedMicros(long started, boolean enabled) {
        return enabled && started != 0L
            ? Math.max(0L, (System.nanoTime() - started) / 1_000L)
            : 0L;
    }

    private static long unattributedApplyMicros(
        long totalApplyMicros,
        long resourceLimitMicros,
        long energyReservationMicros,
        long inputExtractionMicros,
        long workerCommitMicros,
        long cpuAccountingMicros,
        long prepareMicros,
        long postExtractionMicros,
        long postCommitMicros,
        long finalizeMicros
    ) {
        long accounted = resourceLimitMicros
            + energyReservationMicros
            + inputExtractionMicros
            + workerCommitMicros
            + cpuAccountingMicros
            + prepareMicros
            + postExtractionMicros
            + postCommitMicros
            + finalizeMicros;
        return Math.max(0L, totalApplyMicros - accounted);
    }

    /**
     * Applies the CPU-side half of a dispatch exactly once. The worker/provider call is the
     * ownership boundary; a pending record is installed before that call and remains persisted
     * until every local accounting stage has completed.
     */
    private boolean applyPendingAccounting(ExecutingCraftingJob acceptedJob) {
        if (job != acceptedJob || acceptedJob.pendingAccounting() == null) {
            return false;
        }
        ExecutingCraftingJob.PendingAccounting pending = acceptedJob.pendingAccounting();
        try {
            if (!pending.ownershipTransferred) {
                if (cpu.getGrid() == null) {
                    return false;
                }
                if (hasInFlightWorkerJob(acceptedJob.link.getCraftingID())) {
                    acceptedJob.markPendingAccountingOwnershipTransferred();
                } else {
                    ECOBatchCraftingHelper.insertAll(inventory, pending.rollbackInputs, this::postInventoryChange);
                    acceptedJob.clearPendingAccounting();
                    cpu.markDirty();
                    return true;
                }
            }
            IPatternDetails details = null;
            for (IPatternDetails candidate : acceptedJob.tasks.keySet()) {
                if (candidate.getDefinition().equals(pending.pattern)) {
                    details = candidate;
                    break;
                }
            }
            if (!pending.taskApplied && details == null) {
                throw new IllegalStateException("Accepted crafting accounting has no matching task");
            }

            if (!pending.returnedInputsApplied) {
                while (pending.returnedInputIndex < pending.returnedInputs.size()) {
                    GenericStack returned = pending.returnedInputs.get(pending.returnedInputIndex);
                    inventory.insert(returned.what(), returned.amount(), Actionable.MODULATE);
                    // Trigger scheduler wake-up for returned reusable inputs
                    postInventoryChange(returned.what());
                    pending.returnedInputIndex++;
                }
                pending.returnedInputsApplied = true;
            }
            if (!pending.outputsApplied) {
                while (pending.outputIndex < pending.outputs.size()) {
                    GenericStack output = pending.outputs.get(pending.outputIndex);
                    job.waitingFor.insert(
                        output.what(), scaledPatternAmount(output.amount(), pending.crafts), Actionable.MODULATE);
                    pending.outputIndex++;
                }
                postGenericStackKeysChange(pending.outputs);
                pending.outputsApplied = true;
            }
            if (!pending.containersApplied) {
                while (pending.containerIndex < pending.containers.size()) {
                    GenericStack container = pending.containers.get(pending.containerIndex);
                    long amount = pending.retainReusableInputsOnce
                        ? container.amount()
                        : scaledPatternAmount(container.amount(), pending.crafts);
                    job.waitingFor.insert(container.what(), amount, Actionable.MODULATE);
                    job.timeTracker.addMaxItems(amount, container.what().getType());
                    pending.containerIndex++;
                }
                postGenericStackKeysChange(pending.containers);
                pending.containersApplied = true;
            }
            if (!pending.taskApplied) {
                ExecutingCraftingJob.TaskProgress progress = acceptedJob.tasks.get(details);
                if (progress == null || progress.value < pending.crafts) {
                    throw new IllegalStateException(
                        "Accepted crafting accounting exceeds remaining task: " + pending.crafts);
                }
                if (pending.discardPlannedInputs) {
                    acceptedJob.discardPlannedInputs(details);
                }
                acceptedJob.consumePlannedInputs(details, pending.crafts);
                progress.value -= pending.crafts;
                pending.taskApplied = true;
                refreshPendingInputTask(details);
                postPatternOutputsChange(details);
                if (progress.value <= 0L) {
                    removeTask(details);
                }
            }
            acceptedJob.clearPendingAccounting();
            reconcileRetainedFinalOutput();
            cpu.markDirty();
            return true;
        } catch (RuntimeException e) {
            long tick = TickHandler.instance().getCurrentTick();
            if (lastAccountingRecoveryFailureLogTick == Long.MIN_VALUE
                || tick - lastAccountingRecoveryFailureLogTick >= 100L) {
                lastAccountingRecoveryFailureLogTick = tick;
                LOGGER.error(
                    "Crafting provider ownership is recorded, but CPU accounting recovery is still pending", e);
            }
            cpu.markDirty();
            return false;
        }
    }

    private void suspendAfterAcceptedAccountingFailure(
            ExecutingCraftingJob acceptedJob, RuntimeException exception, String message) {
        LOGGER.error(message, exception);
        if (this.job == acceptedJob) {
            // The accepted worker state is authoritative. Keep a durable accounting record and
            // retry only that record; dispatch is blocked until it is fully applied.
            cpu.markDirty();
        }
    }

    private boolean hasInFlightWorkerJob(UUID craftingJobId) {
        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return false;
        }
        for (ECOCraftingPatternBusBlockEntity patternBus
                : grid.getMachines(ECOCraftingPatternBusBlockEntity.class)) {
            if (patternBus.hasInFlightJob(craftingJobId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算一次批量推送应记入 waitingFor 的数量。
     *
     * <p>饱和而非溢出：负的 waitingFor 记账会让 CPU 误以为产物已经交付，从而丢失产出。
     */
    static long scaledPatternAmount(long perCraftAmount, long craftCount) {
        if (perCraftAmount <= 0L || craftCount <= 0L) {
            return 0L;
        }
        try {
            return Math.multiplyExact(perCraftAmount, craftCount);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 由 CraftingService 以 Integer.MAX_VALUE 优先级调用，用于注入正在等待的物品。
     *
     * @return 已消耗数量。
     */
    public long insert(AEKey what, long amount, Actionable type) {
        if (what == null || amount <= 0L || job == null) {
            return 0L;
        }
        if (deliveringBufferedFinalOutput && job.finalOutput != null && what.matches(job.finalOutput)) {
            return 0L;
        }

        long accepted = 0L;
        long waitingFor = job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor > 0L) {
            accepted = insertWaitingFor(what, Math.min(amount, waitingFor), type);
        }

        long remaining = amount - accepted;
        if (remaining <= 0L || this.job == null) {
            return accepted;
        }

        long manualWaiting = this.manualWaitingFor.extract(what, remaining, Actionable.SIMULATE);
        if (manualWaiting <= 0L) {
            return accepted;
        }

        long consumed = Math.min(remaining, manualWaiting);
        if (type == Actionable.MODULATE) {
            this.manualWaitingFor.extract(what, consumed, Actionable.MODULATE);
            this.inventory.insert(what, consumed, Actionable.MODULATE);
            // Trigger scheduler wake-up for manual insertions
            postInventoryChange(what);
            this.cpu.markDirty();
        }
        return accepted + consumed;
    }

    private long insertWaitingFor(AEKey what, long amount, Actionable type) {
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
            if (type == Actionable.SIMULATE) {
                return amount;
            }

            // A final-output item can also be the input of a remaining task (for example,
            // A + B -> 2A). Keep the amount needed to continue that task in local storage;
            // only the surplus belongs to the requester.
            long held = inventory.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
            long pendingInput = pendingInputAmount(what);
            long retained = Math.min(amount, Math.max(0L, pendingInput - held));
            if (retained > 0L) {
                currentJob.timeTracker.decrementItems(retained, what.getType());
                currentJob.waitingFor.extract(what, retained, Actionable.MODULATE);
                inventory.insert(what, retained, Actionable.MODULATE);
                currentJob.addRetainedFinalOutput(retained);
                // Trigger scheduler wake-up for retained final output used as intermediate input
                postInventoryChange(what);
            }

            long finalAmount = amount - retained;
            long acceptedOwnership = finalAmount <= 0L
                ? 0L
                : currentJob.bufferedFinalOutput.accept(finalAmount, Actionable.MODULATE);
            if (acceptedOwnership > 0L) {
                // Ownership commits here. Delivery happens separately, so a network callback cannot make the Worker
                // retry or make the same physical output again.
                currentJob.timeTracker.decrementItems(acceptedOwnership, what.getType());
                currentJob.waitingFor.extract(what, acceptedOwnership, Actionable.MODULATE);
            }
            if (retained > 0L || acceptedOwnership > 0L) {
                postChange(what);
                cpu.markDirty();
                if (acceptedOwnership > 0L) {
                    drainBufferedFinalOutput(currentJob);
                }
            }
            return retained + acceptedOwnership;
        } else {
            if (type == Actionable.MODULATE) {
                inventory.insert(what, amount, Actionable.MODULATE);
                // Trigger scheduler wake-up so blocked downstream tasks can resume
                postInventoryChange(what);
                cpu.markDirty();
            }
        }

        return amount;
    }

    private long pendingInputAmount(AEKey what) {
        if (job == null || what == null) {
            return 0L;
        }
        if (!pendingInputIndexKnown) {
            rebuildPendingInputIndex();
        }
        if (ECOFuzzyCraftingInventory.isConfiguredFuzzy(what, job.fuzzyItemIds)) {
            return pendingFuzzyInputAmounts.getOrDefault(what.getId(), 0L);
        }
        return pendingInputAmounts.getOrDefault(what, 0L);
    }

    private void consumeRetainedFinalOutputFrom(Iterable<GenericStack> stacks) {
        if (job == null || job.finalOutput == null) {
            return;
        }
        long consumed = 0L;
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0L && stack.what().matches(job.finalOutput)) {
                consumed = saturatingAdd(consumed, stack.amount());
            }
        }
        job.consumeRetainedFinalOutput(consumed);
    }

    private void consumeRetainedFinalOutputFrom(KeyCounter[] counters) {
        if (counters == null) {
            return;
        }
        List<GenericStack> stacks = new ArrayList<>();
        for (KeyCounter counter : counters) {
            if (counter == null) {
                continue;
            }
            for (var entry : counter) {
                if (entry.getLongValue() > 0L) {
                    stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
        }
        consumeRetainedFinalOutputFrom(stacks);
    }

    private void reconcileRetainedFinalOutput() {
        if (job == null || job.finalOutput == null || job.retainedFinalOutputAmount() <= 0L) {
            return;
        }
        AEKey key = job.finalOutput.what();
        long pending = pendingInputAmount(key);
        long retained = job.retainedFinalOutputAmount();
        long surplus = Math.max(0L, retained - pending);
        if (surplus <= 0L) {
            return;
        }
        long moved = inventory.extract(key, surplus, Actionable.MODULATE);
        if (moved <= 0L) {
            return;
        }
        job.consumeRetainedFinalOutput(moved);
        job.bufferedFinalOutput.accept(moved, Actionable.MODULATE);
        postChange(key);
        cpu.markDirty();
        drainBufferedFinalOutput(job);
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return Math.max(0L, left);
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatedMultiply(long value, long multiplier) {
        if (value <= 0L || multiplier <= 0L) {
            return 0L;
        }
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
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
        UUID craftingJobId = job.link.getCraftingID();
        preserveBufferedFinalOutput();
        if (success) {
            job.link.markDone();
        } else {
            job.link.cancel();
        }

        // TODO: 记录日志

        // 清空等待列表并发送所有相关变更通知。
        job.waitingFor.clear();
        this.manualWaitingFor.clear();
        // 通知已打开菜单关于已取消的调度任务。
        for (var entry : job.tasks.entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }

        notifyJobOwner(
                job, success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        // 结束任务。
        inputExtractionBlockedRevisions.clear();
        taskScheduler.startJob(List.of());
        pendingInputByTask.clear();
        pendingInputAmounts.clear();
        pendingFuzzyInputAmounts.clear();
        pendingInputIndexKnown = false;
        this.job = null;
        JOB_OUTPUT_ROUTES.remove(craftingJobId, this);

        if (success) {
            pendingWorkerReleases.add(craftingJobId);
            retryPendingWorkerReleases();
        } else {
            requestWorkerRecovery(craftingJobId);
        }

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
        Math.addExact(stored, buffered);

        // Move ownership between the two local ledgers before notifying observers.
        inventory.list.add(key, buffered);
        job.bufferedFinalOutput.removeDelivered(buffered);
        postInventoryChange(key);
        cpu.markDirty();
    }

    /**
     * 取消当前合成任务。
     */
    public void cancel() {
        // 没有可取消的任务 :P
        if (job == null)
            return;

        ExecutingCraftingJob.PendingAccounting pending = job.pendingAccounting();
        if (pending != null) {
            if (pending.ownershipTransferred) {
                if (!pending.returnedInputsApplied) {
                    ECOBatchCraftingHelper.insertAll(inventory, pending.returnedInputs, this::postInventoryChange);
                }
            } else {
                ECOBatchCraftingHelper.insertAll(inventory, pending.rollbackInputs, this::postInventoryChange);
            }
            job.clearPendingAccounting();
            cpu.markDirty();
        }

        finishJob(false);
    }

    private void requestWorkerRecovery(UUID craftingJobId) {
        pendingWorkerRecoveries.add(craftingJobId);
        cpu.markDirty();
        retryPendingWorkerRecoveries();
    }

    private void retryPendingWorkerRecoveries() {
        if (pendingWorkerRecoveries.isEmpty()) {
            return;
        }
        for (UUID craftingJobId : List.copyOf(pendingWorkerRecoveries)) {
            if (recoverInflightWorkerInputs(craftingJobId)) {
                pendingWorkerRecoveries.remove(craftingJobId);
                cpu.markDirty();
            }
        }
    }

    private void retryPendingWorkerReleases() {
        if (pendingWorkerReleases.isEmpty()) {
            return;
        }
        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return;
        }
        for (UUID craftingJobId : List.copyOf(pendingWorkerReleases)) {
            boolean foundPatternBus = false;
            boolean releasedAll = true;
            for (ECOCraftingPatternBusBlockEntity patternBus
                    : grid.getMachines(ECOCraftingPatternBusBlockEntity.class)) {
                foundPatternBus = true;
                patternBus.releaseJobOutputsToNetwork(craftingJobId);
                if (patternBus.hasInFlightJob(craftingJobId)) {
                    releasedAll = false;
                }
            }
            if (foundPatternBus && releasedAll) {
                pendingWorkerReleases.remove(craftingJobId);
                cpu.markDirty();
            }
        }
    }

    private boolean recoverInflightWorkerInputs(UUID craftingJobId) {
        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return false;
        }
        var storage = grid.getStorageService().getInventory();
        boolean foundPatternBus = false;
        boolean recoveredAll = true;
        for (ECOCraftingPatternBusBlockEntity patternBus : grid.getMachines(ECOCraftingPatternBusBlockEntity.class)) {
            foundPatternBus = true;
            if (!patternBus.recoverJobToNetwork(craftingJobId, storage)) {
                recoveredAll = false;
            }
        }
        return foundPatternBus && recoveredAll;
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
            long requested = entry.getLongValue();
            if (requested <= 0L) {
                entry.setValue(0L);
                continue;
            }
            final long inserted;
            try {
                inserted = validateInsertionAmount(
                    storage.insert(entry.getKey(), requested, Actionable.MODULATE, cpu.getActionSource()),
                    requested,
                    "crafting CPU network storage"
                );
            } catch (RuntimeException e) {
                LOGGER.error("Crafting CPU could not validate an item transfer to network storage", e);
                continue;
            }

            // 网络无法接收全部物品，即存储空间不足或已满
            entry.setValue(requested - inserted);
        }
        this.inventory.list.removeZeros();

        cpu.markDirty();
    }

    private void postChange(@Nullable AEKey what) {
        postStatusChange(what);
    }

    private void postInventoryChange(@Nullable AEKey what) {
        inventoryStateRevision++;
        if (what != null && job != null) {
            taskScheduler.wake(what);
            for (var fuzzyId : job.fuzzyItemIds) {
                if (ECOFuzzyCraftingInventory.isConfiguredFuzzy(what, Set.of(fuzzyId))) {
                    taskScheduler.wakeFuzzy(fuzzyId);
                }
            }
        }
        postStatusChange(what);
    }

    private void postStatusChange(@Nullable AEKey what) {
        statusStateRevision++;
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
            notifyListenerSafely(listener, what);
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
                notifyListenerSafely(listener, null);
            }
            return;
        }

        var changedKeys = List.copyOf(batchedStatusChanges);
        batchedStatusChanges.clear();
        batchedAnyStatusChange = false;
        batchedFullStatusChange = false;

        for (AEKey key : changedKeys) {
            for (var listener : listeners) {
                notifyListenerSafely(listener, key);
            }
        }
    }

    private void notifyListenerSafely(Consumer<AEKey> listener, @Nullable AEKey what) {
        try {
            listener.accept(what);
        } catch (RuntimeException e) {
            // UI/status listeners are observers. They must not roll back or duplicate a transfer
            // that has already been committed to the CPU inventory.
            LOGGER.error("Crafting CPU status listener failed", e);
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
        this.manualWaitingFor.clear();
        this.pendingWorkerRecoveries.clear();
        this.pendingWorkerReleases.clear();
        ListTag recoveryIds = data.getList(NBT_PENDING_WORKER_RECOVERIES, Tag.TAG_STRING);
        for (int index = 0; index < recoveryIds.size(); index++) {
            try {
                this.pendingWorkerRecoveries.add(UUID.fromString(recoveryIds.getString(index)));
            } catch (IllegalArgumentException ignored) {
                LOGGER.warn("Ignoring malformed pending ECO worker recovery id");
            }
        }
        ListTag releaseIds = data.getList(NBT_PENDING_WORKER_RELEASES, Tag.TAG_STRING);
        for (int index = 0; index < releaseIds.size(); index++) {
            try {
                this.pendingWorkerReleases.add(UUID.fromString(releaseIds.getString(index)));
            } catch (IllegalArgumentException ignored) {
                LOGGER.warn("Ignoring malformed pending ECO worker release id");
            }
        }
        if (data.contains("job")) {
            this.job = new ExecutingCraftingJob(data.getCompound("job"), registries, this::postChange, this);
            if (this.job.finalOutput == null) {
                finishJob(false);
            } else {
                readManualWaitingFromNBT(data, registries);
                registerJobOutputRoute();
                rebuildTaskScheduler();
                rebuildPendingInputIndex();
            }
        } else {
            taskScheduler.startJob(List.of());
            pendingInputByTask.clear();
            pendingInputAmounts.clear();
            pendingFuzzyInputAmounts.clear();
            pendingInputIndexKnown = false;
        }
    }

    /**
     * Delivers worker output to its owning CPU without depending on CraftingService's rebuilt CPU list.
     */
    public static JobOutputDelivery deliverJobOutput(UUID craftingJobId, AEKey what, long amount, Actionable type) {
        ECOCraftingCPULogic logic = JOB_OUTPUT_ROUTES.get(craftingJobId);
        if (logic != null) {
            ExecutingCraftingJob routedJob = logic.job;
            if (routedJob != null && craftingJobId.equals(routedJob.link.getCraftingID())) {
                long inserted = logic.insert(what, amount, type);
                // CRITICAL: Check inserted amount FIRST before accessing logic.job again.
                // If inserted > 0, the CPU successfully took ownership and the delivery succeeded,
                // regardless of whether insert() triggered finishJob() and set logic.job to null.
                // Only retry if nothing was inserted AND the job still exists with pending accounting.
                boolean retryLater = inserted <= 0L
                    && type == Actionable.MODULATE
                    && logic.job == routedJob
                    && routedJob.pendingAccounting() != null;
                return new JobOutputDelivery(true, inserted, retryLater);
            }
        }
        if (logic != null) {
            JOB_OUTPUT_ROUTES.remove(craftingJobId, logic);
        }
        var external = cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.ECOExternalCpuOutputRoutes.deliver(
                craftingJobId, what, amount, type);
        return new JobOutputDelivery(external.routeAvailable(), external.inserted(), false);
    }

    private void registerJobOutputRoute() {
        if (job != null) {
            JOB_OUTPUT_ROUTES.put(job.link.getCraftingID(), this);
        }
    }

    /** Removes this CPU from direct worker routing while its serialized state is detached. */
    public void unregisterJobOutputRoute() {
        if (job != null) {
            JOB_OUTPUT_ROUTES.remove(job.link.getCraftingID(), this);
        }
    }

    public record JobOutputDelivery(boolean routeAvailable, long inserted, boolean retryLater) {
        private static final JobOutputDelivery UNAVAILABLE = new JobOutputDelivery(false, 0L, false);
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.put("inventory", this.inventory.writeToNBT(registries));
        if (!pendingWorkerRecoveries.isEmpty()) {
            ListTag recoveryIds = new ListTag();
            for (UUID craftingJobId : pendingWorkerRecoveries) {
                recoveryIds.add(StringTag.valueOf(craftingJobId.toString()));
            }
            data.put(NBT_PENDING_WORKER_RECOVERIES, recoveryIds);
        }
        if (!pendingWorkerReleases.isEmpty()) {
            ListTag releaseIds = new ListTag();
            for (UUID craftingJobId : pendingWorkerReleases) {
                releaseIds.add(StringTag.valueOf(craftingJobId.toString()));
            }
            data.put(NBT_PENDING_WORKER_RELEASES, releaseIds);
        }
        if (this.job != null) {
            data.put("job", this.job.writeToNBT(registries));
            if (!this.manualWaitingFor.list.isEmpty()) {
                data.put(NBT_MANUAL_WAITING, this.manualWaitingFor.writeToNBT(registries));
            }
        }
    }

    private void readManualWaitingFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        var entries = data.getList(NBT_MANUAL_WAITING, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.getCompound(index);
            var key = AEKey.fromTagGeneric(registries, entry);
            long amount = entry.getLong("#");
            if (key != null && amount > 0L) {
                this.manualWaitingFor.insert(key, amount, Actionable.MODULATE);
            }
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
            long waiting = this.job.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
            long manualWaiting = this.manualWaitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
            return waiting > Long.MAX_VALUE - manualWaiting ? Long.MAX_VALUE : waiting + manualWaiting;
        }
        return 0;
    }

    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        if (this.job != null) {
            for (var entry : this.job.waitingFor.list) {
                waitingFor.add(entry.getKey());
            }
            for (var entry : this.manualWaitingFor.list) {
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
                        count = saturatingAdd(count,
                            scaledPatternAmount(output.amount(), t.getValue().value));
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
            out.addAll(this.manualWaitingFor.list);
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    out.add(output.what(), scaledPatternAmount(output.amount(), t.getValue().value));
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

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
            cpu.markDirty();
        }
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        var playerId = job.playerId;
        if (playerId == null || job.finalOutput == null) {
            return;
        }

        var server = cpu.getLevel().getServer();
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
