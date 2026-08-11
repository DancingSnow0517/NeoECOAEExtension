package cn.dancingsnow.neoecoae.api.me;

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
import appeng.core.sync.BasePacket;
import appeng.core.sync.packets.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchEnergyReservation;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStage;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOReusableCraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2InputSelection;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOSelectedInputPatternDetails;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingCPULogic {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final Map<UUID, ECOCraftingCPULogic> JOB_OUTPUT_ROUTES = new ConcurrentHashMap<>();
    private static final Map<CraftingService, SlowPathNetworkBudget> SLOW_PATH_NETWORK_BUDGETS =
            Collections.synchronizedMap(new WeakHashMap<>());

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

    /** Revision consumed by the retained 1.20.1 CPU menu synchronization layer. */
    @Getter
    private long statusRevision = 0L;

    @Getter
    private boolean markedForDeletion = false;

    private boolean batchingStatusChanges = false;
    private final Set<AEKey> batchedStatusChanges = new HashSet<>();
    private boolean batchedAnyStatusChange = false;
    private boolean batchedFullStatusChange = false;
    private boolean deliveringBufferedFinalOutput = false;
    private long lastFinalOutputDeliveryFailureLogTick = Long.MIN_VALUE;
    private final IdentityHashMap<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>>
            slowPathDeferredProviders = new IdentityHashMap<>();
    private long slowPathDeferredProvidersTick = Long.MIN_VALUE;

    @Nullable private SlowPathPushBudget tickSlowPathPushBudget;

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
    }

    public ICraftingSubmitResult trySubmitJob(
            IGrid grid, ICraftingPlan plan, IActionSource src, @Nullable ICraftingRequester requester) {
        // 已有任务在运行。
        if (this.job != null) return CraftingSubmitResult.CPU_BUSY;
        // 检查节点是否活跃。
        if (!cpu.isActive()) return CraftingSubmitResult.CPU_OFFLINE;
        // 检查存储字节数。
        if (cpu.getAvailableStorage() < plan.bytes()) return CraftingSubmitResult.CPU_TOO_SMALL;

        if (!inventory.list.isEmpty()) AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");

        // 尝试提取所需物品。
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null) return CraftingSubmitResult.missingIngredient(missingIngredient);
        ECOFastPathDiagnostics.logCpuReservation(
                plan,
                inventory.list,
                cpu.getOwner() == null
                        ? net.minecraft.core.BlockPos.ZERO
                        : cpu.getOwner().getBlockPos(),
                TickHandler.instance().getCurrentTick());

        // 设置 CPU 链接与任务。
        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new ExecutingCraftingJob(plan, this::postChange, linkCpu, playerId);
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
        // 未激活时不 tick。
        if (!cpu.isActive()) return;
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

        // 暂停时不调度更多工作
        if (job.suspended || job.userPaused) {
            return;
        }

        var remainingOperations = getOperationLimit();
        tickSlowPathPushBudget = new SlowPathPushBudget(cc);

        try {
            if (remainingOperations > 0) {
                do {
                    var pushedPatterns = executeCrafting(remainingOperations, cc, eg, cpu.getLevel());

                    if (pushedPatterns > 0) {
                        remainingOperations -= pushedPatterns;
                    } else {
                        break;
                    }
                } while (remainingOperations > 0);
            }
        } finally {
            tickSlowPathPushBudget = null;
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
                    deliverFinalOutput(key, deliverable, Actionable.MODULATE), deliverable, "final-output requester");
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

    static boolean isFinalOutputSatisfied(long remainingAmount) {
        // The buffer may still own recipe-rounding surplus. finishJob preserves that surplus and stores it normally.
        return remainingAmount <= 0L;
    }

    private int getOperationLimit() {
        return calculateOperationLimit(cpu.getCoProcessors(), NEConfig.ecoCpuPushTickLimit);
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
        SlowPathPushBudget slowPathPushBudget =
                tickSlowPathPushBudget != null ? tickSlowPathPushBudget : new SlowPathPushBudget(craftingService);
        var job = this.job;
        if (job == null) return 0;

        long currentTick = TickHandler.instance().getCurrentTick();
        beginSlowPathProviderTick(currentTick);
        var pushedPatterns = 0;

        beginStatusChangeBatch();
        try {
            var it = job.tasks.entrySet().iterator();
            taskLoop:
            while (it.hasNext()) {
                var task = it.next();
                if (task.getValue().value <= 0) {
                    postPatternOutputsChange(task.getKey());
                    it.remove();
                    continue;
                }

                var details = task.getKey();
                // 同一调度轮次内按任务收集一次提供者列表，避免每次推送都重建列表并重复查询。
                List<ICraftingProvider> providers = collectAvailableProviders(craftingService, details);
                if (providers.isEmpty()) {
                    continue;
                }
                List<ECOCraftingPatternBusBlockEntity> patternBuses = collectPatternBuses(providers);
                // FastPath 元数据只有 ECO 智能样板总线能够消费；纯第三方提供者不应支付其构建成本。
                boolean fastPathCandidate = !patternBuses.isEmpty();

                while (task.getValue().value > 0 && pushedPatterns < maxPatterns) {
                    if (!hasReadyProvider(providers, details)) {
                        continue taskLoop;
                    }

                    @Nullable List<ECOAE2InputSelection> plannedInputs = job.peekPlannedInputs(details);
                    long plannedInputCount = plannedInputs == null ? 0L : job.peekPlannedInputCount(details);
                    // ECO workers use the CPU's live inventory and the complete remaining task.
                    // Planned selections are planner bookkeeping and may describe an exact key
                    // that is no longer identical to the reserved stack's components.
                    boolean usePlannedInputs = shouldUsePlannedInputsForDispatch(
                            fastPathCandidate, plannedInputs != null, task.getValue().value, plannedInputCount);
                    @Nullable ECOSelectedInputPatternDetails selectedDetails =
                            usePlannedInputs ? new ECOSelectedInputPatternDetails(details, plannedInputs) : null;
                    boolean runtimeInputFallback = plannedInputs != null && !usePlannedInputs;
                    IPatternDetails extractionDetails = selectedDetails == null ? details : selectedDetails;
                    long batchTaskRemaining = fastPathCandidate || plannedInputs == null
                            ? task.getValue().value
                            : usePlannedInputs
                                    ? Math.min(task.getValue().value, plannedInputCount)
                                    : task.getValue().value;
                    var expectedOutputs = new KeyCounter();
                    var expectedContainerItems = new KeyCounter();
                    @Nullable var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                            extractionDetails, inventory, level, expectedOutputs, expectedContainerItems);
                    if (craftingContainer == null) {
                        if (fastPathCandidate) {
                            ECOFastPathDiagnostics.logCpuPreflightFailure(
                                    details,
                                    cpu.getOwner() == null
                                            ? net.minecraft.core.BlockPos.ZERO
                                            : cpu.getOwner().getBlockPos(),
                                    currentTick,
                                    task.getValue().value,
                                    plannedInputs != null,
                                    plannedInputCount);
                        }
                        continue taskLoop;
                    }
                    if (selectedDetails != null) {
                        craftingContainer = selectedDetails.collapseInputHolder(craftingContainer);
                    }

                    ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.create(
                            details,
                            craftingContainer,
                            expectedOutputs,
                            expectedContainerItems,
                            level,
                            fastPathCandidate);

                    var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer)
                            * cpu.getCluster().getNetworkPowerMultiplier();
                    long batchResult = tryPushVerifiedFastPathBatch(
                            job,
                            details,
                            execution,
                            craftingContainer,
                            patternBuses,
                            energyService,
                            patternPower,
                            batchTaskRemaining);
                    if (batchResult > 0) {
                        // One provider dispatch consumes one CPU scheduling operation regardless of how many
                        // crafts the F-series host accepted in that batch.
                        pushedPatterns++;
                        if (this.job != job) {
                            break taskLoop;
                        }
                        task.getValue().value -= batchResult;
                        if (runtimeInputFallback) {
                            job.discardPlannedInputs(details);
                        }
                        job.consumePlannedInputs(details, batchResult);
                        postPatternOutputsChange(details);
                        if (task.getValue().value <= 0) {
                            it.remove();
                            continue taskLoop;
                        }
                        if (pushedPatterns == maxPatterns) {
                            break taskLoop;
                        }
                        continue;
                    } else if (batchResult < 0) {
                        continue taskLoop;
                    }

                    boolean pushed = false;
                    for (ICraftingProvider provider : providers) {
                        // Batch-capable providers were already offered both ECO and AE2LT batch paths above.
                        // Do not let a high CPU parallelism turn the synchronous fallback into thousands of
                        // third-party inventory insertions in a single server tick.
                        if (provider.isBusy() || shouldSkipSlowPathProvider(provider, details)) {
                            continue;
                        }

                        if (energyService.extractAEPower(patternPower, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                                < patternPower - 0.01) {
                            break;
                        }

                        if (!slowPathPushBudget.tryAcquire()) {
                            CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                            break taskLoop;
                        }

                        try {
                            if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus) {
                                pushed = patternBus.pushPattern(execution, job.link.getCraftingID());
                            } else {
                                // AE2LT wraps this exact interface invocation to register overload outputs.
                                pushed = provider.pushPattern(details, craftingContainer);
                            }
                        } catch (RuntimeException e) {
                            LOGGER.error(
                                    "Crafting provider rejected a pattern with an exception; CPU inputs remain owned locally",
                                    e);
                            pushed = false;
                        }

                        if (!pushed) {
                            deferSlowPathProvider(provider, details);
                            continue;
                        }

                        chargeAcceptedPatternEnergy(energyService, patternPower);
                        pushedPatterns++;
                        if (this.job != job) {
                            break taskLoop;
                        }
                        recordPushedPattern(job, execution, 1);
                        if (runtimeInputFallback) {
                            job.discardPlannedInputs(details);
                        }
                        job.consumePlannedInputs(details);

                        task.getValue().value--;
                        postPatternOutputsChange(details);
                        if (task.getValue().value <= 0) {
                            it.remove();
                            continue taskLoop;
                        }

                        if (pushedPatterns == maxPatterns) {
                            break taskLoop;
                        }

                        break;
                    }

                    if (!pushed) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                        continue taskLoop;
                    }
                }
            }
        } finally {
            endStatusChangeBatchSafely();
        }

        return pushedPatterns;
    }

    private static final class SlowPathPushBudget {
        private final SlowPathNetworkBudget networkBudget;

        private SlowPathPushBudget(CraftingService craftingService) {
            synchronized (SLOW_PATH_NETWORK_BUDGETS) {
                this.networkBudget = SLOW_PATH_NETWORK_BUDGETS.computeIfAbsent(
                        craftingService, ignored -> new SlowPathNetworkBudget());
            }
        }

        private boolean tryAcquire() {
            return networkBudget.tryAcquire(TickHandler.instance().getCurrentTick(), NEConfig.ecoCpuPushTickLimit, 0);
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
                    deadlineNanos = now >= Long.MAX_VALUE - budgetNanos ? Long.MAX_VALUE : now + budgetNanos;
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
            return this == PROVIDER_ACCEPTED || this == INPUT_OWNERSHIP_TRANSFERRED || this == ACCOUNTING_APPLIED;
        }
    }

    private void chargeAcceptedPatternEnergy(IEnergyService energyService, double requiredPower) {
        try {
            double charged = energyService.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (Double.isNaN(charged) || charged < requiredPower - 0.01D) {
                LOGGER.error(
                        "Crafting pattern was accepted, but only {} of {} crafting energy was charged",
                        charged,
                        requiredPower);
            }
        } catch (RuntimeException e) {
            // The provider already owns the inputs. Accounting must continue so this pattern is not scheduled twice.
            LOGGER.error("Crafting pattern was accepted, but its crafting energy could not be charged", e);
        }
    }

    private List<ICraftingProvider> collectAvailableProviders(
            CraftingService craftingService, IPatternDetails details) {
        List<ICraftingProvider> providers = new ArrayList<>();
        for (ICraftingProvider provider : craftingService.getProviders(details)) {
            if (!provider.isBusy() && !shouldSkipSlowPathProvider(provider, details)) {
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

    private boolean hasReadyProvider(List<ICraftingProvider> providers, IPatternDetails details) {
        for (ICraftingProvider provider : providers) {
            if (!provider.isBusy() && !shouldSkipSlowPathProvider(provider, details)) {
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
            long taskRemaining) {
        if (patternBuses.isEmpty()) {
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    ECOFastPathFallbackReason.NO_ECO_PATTERN_BUS,
                    ECOFastPathStage.ELIGIBILITY,
                    cpu.getOwner().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "batch_attempt_has_no_eco_pattern_bus");
            return 0;
        }
        if (!canAttemptBatchFastPath(execution)) {
            ECOFastPathFallbackReason reason = execution.fallbackReason() != null
                    ? execution.fallbackReason()
                    : !NEConfig.isEcoAe2FastPathEnabled()
                            ? ECOFastPathFallbackReason.FAST_PATH_DISABLED
                            : NEConfig.postCraftingEvent
                                    ? ECOFastPathFallbackReason.POST_CRAFTING_EVENT
                                    : ECOFastPathFallbackReason.KEY_BUILD_FAILED;
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    reason,
                    ECOFastPathStage.ELIGIBILITY,
                    cpu.getOwner().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "batch_gate_rejected eligible=" + execution.fastPathEligible() + " keyPresent="
                            + (execution.key() != null));
            return 0;
        }
        if (taskRemaining <= 0) {
            return 0;
        }

        var reusablePlan = ECOReusableCraftingPlan.of(execution.inputItems(), execution.expectedContainerItems());
        boolean reusableLease = !reusablePlan.reusableInputs().isEmpty();
        long minimumBatchSize = reusableLease ? 1L : 2L;
        if (taskRemaining < minimumBatchSize) {
            return 0;
        }
        // Offer the complete remaining task. The selected ECO controller/worker applies its
        // actual lane capacity (for example 4096) and the next scheduler pass fills the next
        // free lane. Dividing by the number of free lanes here makes a 44-slot controller submit
        // batches of 1 when only 44 crafts remain, defeating FastPath batching.
        long requested = calculateBatchRequestSize(taskRemaining);
        ECOCraftingPatternBusBlockEntity selectedPatternBus = null;
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer selectedOffer = null;
        Set<ECOCraftingSystemBlockEntity> visitedControllers = new HashSet<>();
        for (ECOCraftingPatternBusBlockEntity patternBus : patternBuses) {
            ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
            if (controller == null || !visitedControllers.add(controller)) {
                continue;
            }
            var offer = patternBus.findBatchFastPathOffer(execution, requested, job.link.getCraftingID());
            if (offer != null
                    && offer.maxBatchSize() >= minimumBatchSize
                    && (selectedOffer == null || offer.maxBatchSize() > selectedOffer.maxBatchSize())) {
                selectedPatternBus = patternBus;
                selectedOffer = offer;
                if (offer.maxBatchSize() >= requested) {
                    break;
                }
            }
        }
        if (selectedPatternBus == null || selectedOffer == null) {
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    ECOFastPathFallbackReason.NO_BATCH_OFFER,
                    ECOFastPathStage.CACHE_LOOKUP,
                    cpu.getOwner().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "requested=" + requested + " patternBuses=" + patternBuses.size()
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

        long offeredBatchSize = Math.min(requested, selectedOffer.maxBatchSize());
        boolean virtualCrafting = workerController.isVirtualCraftingMode();
        long batchSize = ECOBatchCraftingHelper.maxSafeBatchSize(
                reusablePlan.consumedInputsPerCraft(),
                execution.expectedOutputs(),
                reusablePlan.ordinaryRemainingPerCraft(),
                offeredBatchSize);
        long safeBatchSize = batchSize;
        long energyBatchSize = -1L;
        long coolantBatchSize = -1L;
        if (batchSize < minimumBatchSize) {
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    ECOFastPathFallbackReason.BATCH_AMOUNT_OVERFLOW,
                    ECOFastPathStage.RESOURCE_LIMIT,
                    selectedOffer.worker().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "offered=" + offeredBatchSize + " safe=" + batchSize);
            return 0;
        }
        if (!virtualCrafting) {
            int normalOfferedBatchSize = (int) Math.min(Integer.MAX_VALUE, batchSize);
            batchSize = normalOfferedBatchSize;
            energyBatchSize = maxBatchSizeFromEnergy(energyService, patternPower, normalOfferedBatchSize);
            if (energyBatchSize < minimumBatchSize) {
                selectedOffer.worker().getFastPathCache().recordCoolantReject();
                ECOFastPathDiagnostics.logFailure(
                        execution,
                        ECOFastPathFallbackReason.ENERGY_LIMIT,
                        ECOFastPathStage.RESOURCE_LIMIT,
                        selectedOffer.worker().getBlockPos(),
                        TickHandler.instance().getCurrentTick(),
                        "requested=" + requested + " offered=" + offeredBatchSize + " affordable=" + energyBatchSize
                                + " patternPower=" + patternPower);
                return 0;
            }
            coolantBatchSize = workerController.getCraftingCoolantCraftLimit(
                    5, workerController.getCoolingRequirementForCurrentNetwork(), (int) energyBatchSize);
            batchSize = Math.min(batchSize, Math.min(energyBatchSize, coolantBatchSize));
            if (coolantBatchSize < minimumBatchSize) {
                selectedOffer.worker().getFastPathCache().recordCoolantReject();
                ECOFastPathDiagnostics.logFailure(
                        execution,
                        ECOFastPathFallbackReason.COOLANT_LIMIT,
                        ECOFastPathStage.RESOURCE_LIMIT,
                        selectedOffer.worker().getBlockPos(),
                        TickHandler.instance().getCurrentTick(),
                        "requested=" + requested + " offered=" + offeredBatchSize + " energyLimit=" + energyBatchSize
                                + " coolantLimit=" + coolantBatchSize);
                return 0;
            }
        }
        if (batchSize < minimumBatchSize) {
            return 0;
        }

        long extraCrafts = batchSize - 1L;
        var inventoryBatchLimit = ECOBatchCraftingHelper.inventoryBatchLimit(
                inventory, reusablePlan.consumedInputsPerCraft(), extraCrafts);
        long availableExtraCrafts = inventoryBatchLimit.crafts();
        long inventoryBatchSize;
        try {
            inventoryBatchSize = Math.addExact(availableExtraCrafts, 1L);
        } catch (ArithmeticException e) {
            inventoryBatchSize = Long.MAX_VALUE;
        }
        batchSize = Math.min(batchSize, inventoryBatchSize);
        if (batchSize < minimumBatchSize) {
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    ECOFastPathFallbackReason.INVENTORY_LIMIT,
                    ECOFastPathStage.RESOURCE_LIMIT,
                    selectedOffer.worker().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "requested=" + requested + " resourceBatch=" + (extraCrafts + 1) + " availableExtraCrafts="
                            + availableExtraCrafts);
            return 0;
        }

        ECOFastPathDiagnostics.logBatchDecision(
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
                batchSize);

        var extraInputs = reusablePlan.extraInputs(batchSize - 1);
        boolean extraInputsExtracted = false;
        BatchDispatchState dispatchState = BatchDispatchState.PREPARED;
        ECOBatchEnergyReservation energyReservation = null;
        try {
            double requiredPower = virtualCrafting ? 0.0D : patternPower * batchSize;
            if (!virtualCrafting && !Double.isFinite(requiredPower)) {
                ECOFastPathDiagnostics.logFailure(
                        execution,
                        ECOFastPathFallbackReason.ENERGY_LIMIT,
                        ECOFastPathStage.RESOURCE_LIMIT,
                        selectedOffer.worker().getBlockPos(),
                        TickHandler.instance().getCurrentTick(),
                        "requiredPowerNotFinite=" + requiredPower + " batch=" + batchSize);
                return 0;
            }
            if (!virtualCrafting) {
                try {
                    energyReservation = ECOBatchEnergyReservation.tryReserve(energyService, requiredPower, false);
                } catch (RuntimeException e) {
                    ECOFastPathDiagnostics.logBatchFailure(
                            new ECOBatchCraftingRequest(
                                    details,
                                    execution.key(),
                                    batchSize,
                                    execution.inputItems(),
                                    execution.expectedOutputs(),
                                    execution.expectedContainerItems(),
                                    job.link.getCraftingID()),
                            ECOFastPathFallbackReason.ENERGY_LIMIT,
                            ECOFastPathStage.ENERGY_CHARGE,
                            selectedOffer.worker().getBlockPos(),
                            TickHandler.instance().getCurrentTick(),
                            "energy_service_exception=" + e.getMessage());
                    throw e;
                }
                if (energyReservation == null) {
                    ECOFastPathDiagnostics.logFailure(
                            execution,
                            ECOFastPathFallbackReason.ENERGY_LIMIT,
                            ECOFastPathStage.RESOURCE_LIMIT,
                            selectedOffer.worker().getBlockPos(),
                            TickHandler.instance().getCurrentTick(),
                            "requiredPower=" + requiredPower);
                    // Keep the first craft's inputs for the normal slow path. No extra inputs were taken.
                    return 0;
                }
            }
            try {
                ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
            } catch (RuntimeException e) {
                ECOFastPathDiagnostics.logFailure(
                        execution,
                        ECOFastPathFallbackReason.INPUT_RESERVATION_FAILED,
                        ECOFastPathStage.INPUT_RESERVATION,
                        selectedOffer.worker().getBlockPos(),
                        TickHandler.instance().getCurrentTick(),
                        "batch=" + batchSize + " extraInputs=" + extraInputs + " error=" + e.getMessage());
                throw e;
            }
            extraInputsExtracted = true;
            dispatchState = BatchDispatchState.RESOURCES_RESERVED;
            var request = new ECOBatchCraftingRequest(
                    details,
                    execution.key(),
                    batchSize,
                    execution.inputItems(),
                    execution.expectedOutputs(),
                    execution.expectedContainerItems(),
                    job.link.getCraftingID());
            if (!selectedPatternBus.pushBatch(request, selectedOffer)) {
                ECOFastPathDiagnostics.logBatchFailure(
                        request,
                        ECOFastPathFallbackReason.PROVIDER_REJECTED,
                        ECOFastPathStage.PROVIDER_DISPATCH,
                        selectedPatternBus.getBlockPos(),
                        TickHandler.instance().getCurrentTick(),
                        "pattern_bus_or_network_cluster_returned_false worker="
                                + selectedOffer.worker().getBlockPos().toShortString());
                RuntimeException refundFailure = energyReservation == null ? null : energyReservation.refundSafely();
                if (refundFailure != null) {
                    LOGGER.error("ECO batch energy refund failed after provider rejection", refundFailure);
                }
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, true);
                return -1;
            }
            dispatchState = BatchDispatchState.PROVIDER_ACCEPTED;
            // The worker owns consumed inputs from this point onward. Exact reusable catalysts
            // never enter the worker ledger, so return their single reserved copy to the CPU. This
            // lets the scheduler lease the same immutable catalyst to other free FX workers.
            dispatchState = BatchDispatchState.INPUT_OWNERSHIP_TRANSFERRED;
            ECOBatchCraftingHelper.insertAll(inventory, reusablePlan.reusableInputs());
            if (energyReservation != null) {
                energyReservation.commit();
            }
            try {
                if (this.job == job) {
                    recordPushedPattern(job, execution, batchSize, true);
                    dispatchState = BatchDispatchState.ACCOUNTING_APPLIED;
                }
            } catch (RuntimeException e) {
                selectedOffer.worker().getFastPathCache().recordException();
                ECOFastPathDiagnostics.logBatchFailure(
                        request,
                        ECOFastPathFallbackReason.ACCOUNTING_FAILED,
                        ECOFastPathStage.ACCOUNTING,
                        selectedOffer.worker().getBlockPos(),
                        TickHandler.instance().getCurrentTick(),
                        "record_pushed_pattern_exception=" + e.getMessage());
                LOGGER.error("ECO batch was accepted, but its CPU accounting update failed", e);
            }
            return batchSize;
        } catch (RuntimeException e) {
            selectedOffer.worker().getFastPathCache().recordException();
            boolean providerOwnsInputs = dispatchState.providerOwnsInputs();
            ECOFastPathDiagnostics.logFailure(
                    execution,
                    providerOwnsInputs
                            ? ECOFastPathFallbackReason.ACCOUNTING_FAILED
                            : ECOFastPathFallbackReason.PROVIDER_REJECTED,
                    providerOwnsInputs ? ECOFastPathStage.ACCOUNTING : ECOFastPathStage.PROVIDER_DISPATCH,
                    selectedOffer.worker().getBlockPos(),
                    TickHandler.instance().getCurrentTick(),
                    "batch=" + batchSize + " dispatchState=" + dispatchState + " error=" + e.getMessage());
            if (providerOwnsInputs) {
                LOGGER.error("ECO batch failed after ownership transfer; accounting it as accepted", e);
                return batchSize;
            }
            RuntimeException refundFailure = energyReservation == null ? null : energyReservation.refundSafely();
            if (refundFailure != null) {
                LOGGER.error("ECO batch energy refund failed while rolling back a pre-submit failure", refundFailure);
            }
            rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraInputsExtracted);
            return -1;
        } catch (Error e) {
            selectedOffer.worker().getFastPathCache().recordException();
            if (!dispatchState.providerOwnsInputs()) {
                if (energyReservation != null) {
                    RuntimeException refundFailure = energyReservation.refundSafely();
                    if (refundFailure != null) {
                        LOGGER.error("ECO batch energy refund failed while rolling back an error", refundFailure);
                    }
                }
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraInputsExtracted);
            }
            throw e;
        }
    }

    static long calculateBatchRequestSize(long taskRemaining) {
        return Math.max(0L, taskRemaining);
    }

    static boolean shouldUsePlannedInputs(long taskRemaining, long plannedInputCount) {
        return taskRemaining <= 0L || plannedInputCount >= taskRemaining;
    }

    static boolean shouldUsePlannedInputsForDispatch(
            boolean ecoFastPathCandidate, boolean plannedInputsPresent, long taskRemaining, long plannedInputCount) {
        return !ecoFastPathCandidate
                && plannedInputsPresent
                && shouldUsePlannedInputs(taskRemaining, plannedInputCount);
    }

    private static String describeInventoryConstraint(ECOBatchCraftingHelper.InventoryBatchLimit limit) {
        if (limit.limitingKey() == null) {
            return "none";
        }
        return limit.limitingKey() + " available=" + limit.available() + " perCraft=" + limit.perCraft();
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
        return execution.key() != null
                && execution.fastPathEligible()
                && NEConfig.isEcoAe2FastPathEnabled()
                && !NEConfig.postCraftingEvent;
    }

    private int maxBatchSizeFromEnergy(IEnergyService energyService, double patternPower, int requested) {
        return ECOBatchCraftingHelper.maxAffordableCrafts(
                patternPower,
                requested,
                totalPower -> energyService.extractAEPower(totalPower, Actionable.SIMULATE, PowerMultiplier.CONFIG));
    }

    private void recordPushedPattern(
            ExecutingCraftingJob job, ECOExtractedPatternExecution execution, long craftCount) {
        recordPushedPattern(job, execution, craftCount, false);
    }

    private void recordPushedPattern(
            ExecutingCraftingJob job,
            ECOExtractedPatternExecution execution,
            long craftCount,
            boolean retainReusableInputsOnce) {
        for (var expectedOutput : execution.expectedOutputs()) {
            job.waitingFor.insert(
                    expectedOutput.what(),
                    scaledPatternAmount(expectedOutput.amount(), craftCount),
                    Actionable.MODULATE);
        }
        postGenericStackKeysChange(execution.expectedOutputs());

        List<GenericStack> expectedContainerItems = retainReusableInputsOnce
                ? ECOReusableCraftingPlan.of(execution.inputItems(), execution.expectedContainerItems())
                        .batchRemaining(craftCount)
                : execution.expectedContainerItems();
        for (var expectedContainerItem : expectedContainerItems) {
            long amount = retainReusableInputsOnce
                    ? expectedContainerItem.amount()
                    : scaledPatternAmount(expectedContainerItem.amount(), craftCount);
            job.waitingFor.insert(expectedContainerItem.what(), amount, Actionable.MODULATE);
            job.timeTracker.addMaxItems(amount, expectedContainerItem.what().getType());
        }
        postGenericStackKeysChange(expectedContainerItems);

        cpu.markDirty();
    }

    /**
     * 计算一次批量推送应记入 waitingFor 的数量。
     *
     * <p>饱和而非溢出：负的 waitingFor 记账会让 CPU 误以为产物已经交付，从而丢失产出。
     */
    static long scaledPatternAmount(long perCraftAmount, long craftCount) {
        if (perCraftAmount <= 0L) {
            return 0L;
        }
        long multiplier = Math.max(1L, craftCount);
        try {
            return Math.multiplyExact(perCraftAmount, multiplier);
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
        // 任务完成时也停止接收物品，防止在 storeItems 推出物品时重新插入
        if (what == null || amount <= 0L || job == null) return 0;
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
            }

            long finalAmount = amount - retained;
            long acceptedOwnership =
                    finalAmount <= 0L ? 0L : currentJob.bufferedFinalOutput.accept(finalAmount, Actionable.MODULATE);
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
            }
        }

        return amount;
    }

    private long pendingInputAmount(AEKey what) {
        if (job == null) {
            return 0L;
        }
        long total = 0L;
        for (var task : job.tasks.entrySet()) {
            long batches = task.getValue().value;
            if (batches <= 0L) {
                continue;
            }
            var plannedBatches = job.plannedInputs.get(task.getKey());
            if (plannedBatches != null && !plannedBatches.isEmpty()) {
                for (var plannedBatch : plannedBatches) {
                    long perBatch = 0L;
                    for (var selection : plannedBatch.selectedInputs()) {
                        for (var alternative : selection.alternatives()) {
                            GenericStack selected = alternative.template();
                            if (what.equals(selected.what())) {
                                perBatch = Math.addExact(
                                        perBatch, Math.multiplyExact(selected.amount(), alternative.multiplier()));
                            }
                        }
                    }
                    total = Math.addExact(total, Math.multiplyExact(perBatch, plannedBatch.remaining()));
                }
                continue;
            }
            long perBatch = 0L;
            for (var input : task.getKey().getInputs()) {
                long selectedAmount = 0L;
                for (var possible : input.getPossibleInputs()) {
                    if (possible != null && possible.amount() > 0L && what.equals(possible.what())) {
                        selectedAmount =
                                Math.max(selectedAmount, Math.multiplyExact(possible.amount(), input.getMultiplier()));
                    }
                }
                perBatch = Math.addExact(perBatch, selectedAmount);
            }
            total = Math.addExact(total, Math.multiplyExact(perBatch, batches));
        }
        return total;
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
                    "Invalid insertion result from " + target + ": " + inserted + " for " + requested);
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
        JOB_OUTPUT_ROUTES.remove(craftingJobId, this);

        if (success) {
            releaseInflightWorkerOutputs(craftingJobId);
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
            LOGGER.error("Discarding {} buffered final-output units because their persisted key is invalid", buffered);
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
        postChange(key);
        cpu.markDirty();
    }

    /**
     * 取消当前合成任务。
     */
    public void cancel() {
        // 没有可取消的任务 :P
        if (job == null) return;

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
        for (ECOCraftingPatternBusBlockEntity patternBus : grid.getMachines(ECOCraftingPatternBusBlockEntity.class)) {
            patternBus.recoverJobToNetwork(craftingJobId, storage);
        }
    }

    private void releaseInflightWorkerOutputs(UUID craftingJobId) {
        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return;
        }
        for (ECOCraftingPatternBusBlockEntity patternBus : grid.getMachines(ECOCraftingPatternBusBlockEntity.class)) {
            patternBus.releaseJobOutputsToNetwork(craftingJobId);
        }
    }

    /**
     * 尝试将所有本地存储的物品转存回存储网络。
     */
    public void storeItems() {
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        // 无事可做则快速返回。
        if (this.inventory.list.isEmpty()) return;

        var g = cpu.getGrid();
        if (g == null) return;

        var storage = g.getStorageService().getInventory();

        for (var entry : this.inventory.list) {
            this.postChange(entry.getKey());
            var inserted =
                    storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, cpu.getActionSource());

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

        markStatusDirty();
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

        markStatusDirty();

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

    @Nullable public UUID getCraftingJobId() {
        return job == null ? null : job.link.getCraftingID();
    }

    /** The 1.20.1 cluster calls this after restoring its physical CPU list. */
    public boolean onRestoredToGrid(IGrid grid) {
        if (job == null) {
            return true;
        }
        ((CraftingService) grid.getCraftingService()).addLink(job.link);
        return !job.link.isCanceled();
    }

    /** 1.21.1 has no delayed restore state; rebinding is performed eagerly. */
    public boolean isInRestoreGrace() {
        return false;
    }

    @Nullable public GenericStack getFinalJobOutput() {
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
        this.inventory.readFromNBT(data.getList("inventory", 10));
        if (data.contains("job")) {
            this.job = new ExecutingCraftingJob(data.getCompound("job"), registries, this::postChange, this);
            if (this.job.finalOutput == null) {
                finishJob(false);
            } else {
                registerJobOutputRoute();
            }
        }
    }

    /**
     * Delivers worker output to its owning CPU without depending on CraftingService's rebuilt CPU list.
     */
    public static JobOutputDelivery deliverJobOutput(UUID craftingJobId, AEKey what, long amount, Actionable type) {
        ECOCraftingCPULogic logic = JOB_OUTPUT_ROUTES.get(craftingJobId);
        if (logic != null && logic.job != null && craftingJobId.equals(logic.job.link.getCraftingID())) {
            return new JobOutputDelivery(true, logic.insert(what, amount, type));
        }
        if (logic != null) {
            JOB_OUTPUT_ROUTES.remove(craftingJobId, logic);
        }
        var external = cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.ECOExternalCpuOutputRoutes.deliver(
                craftingJobId, what, amount, type);
        return new JobOutputDelivery(external.routeAvailable(), external.inserted());
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

    public record JobOutputDelivery(boolean routeAvailable, long inserted) {
        private static final JobOutputDelivery UNAVAILABLE = new JobOutputDelivery(false, 0L);
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.put("inventory", this.inventory.writeToNBT());
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

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    public boolean isJobUserPaused() {
        return job != null && job.userPaused;
    }

    public void setJobUserPaused(boolean paused) {
        if (job != null && job.userPaused != paused) {
            job.userPaused = paused;
            cpu.markDirty();
            postChange(null);
        }
    }

    public void toggleJobUserPaused() {
        setJobUserPaused(!isJobUserPaused());
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
            postChange(null);
        }
    }

    private void markStatusDirty() {
        statusRevision++;
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        markStatusDirty();

        var playerId = job.playerId;
        if (playerId == null || job.finalOutput == null) {
            return;
        }

        var server = cpu.getLevel().getServer();
        var connectedPlayer = IPlayerRegistry.getConnected(server, playerId);
        if (connectedPlayer != null) {
            var jobId = job.link.getCraftingID();
            BasePacket message = new CraftingJobStatusPacket(
                    jobId, job.finalOutput.what(), job.finalOutput.amount(), job.remainingAmount, status);
            connectedPlayer.connection.send(
                    message.toPacket(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        }
    }

    /** Read-only compatibility payload consumed by the retained 1.20.1 controller UI adapters. */
    public record AggressiveSimulatedCraftSnapshot(
            BlockPos controllerPos,
            UUID owner,
            GenericStack output,
            int occupiedSlots,
            int progress,
            int maxProgress,
            boolean outputsReady) {
        public AggressiveSimulatedCraftSnapshot {
            Preconditions.checkNotNull(controllerPos, "controllerPos");
            Preconditions.checkNotNull(owner, "owner");
            Preconditions.checkNotNull(output, "output");
            output = new GenericStack(output.what(), Math.max(0L, output.amount()));
            occupiedSlots = Math.max(1, occupiedSlots);
            progress = Math.max(0, progress);
            maxProgress = Math.max(1, maxProgress);
        }
    }

    public void markForDeletion() {
        this.markedForDeletion = true;
    }
}
