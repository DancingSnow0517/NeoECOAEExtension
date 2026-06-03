package cn.dancingsnow.neoecoae.api.me;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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
import appeng.core.sync.BasePacket;
import appeng.core.sync.packets.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingCPULogic {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final boolean DEBUG_EXECUTION_STATS = Boolean.getBoolean("neoecoae.debugEcoCraftingExecution");
    private static final int ECO_PROVIDER_PUSH_BURST_LIMIT = 256;
    private static final int ECO_BATCH_FAST_PATH_LIMIT = Math.max(
            1,
            Integer.getInteger("neoecoae.ecoBatchFastPathLimit", 64));
    private static final int ECO_BATCH_FAST_PATH_TICK_LIMIT = Math.max(
            1,
            Integer.getInteger("neoecoae.ecoBatchFastPathTickLimit", 256));

    final ECOCraftingCPU cpu;

    /**
     * Current job.
     */
    @Getter
    private ExecutingCraftingJob job = null;
    /**
     * Inventory.
     */
    @Getter
    private final ListCraftingInventory inventory = new ListCraftingInventory(ECOCraftingCPULogic.this::postChange);
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    /**
     * True if the CPU is currently trying to clear its inventory but is not able
     * to.
     */
    @Getter
    private boolean cantStoreItems = false;
    @Getter
    private long statusRevision = 0L;

    @Getter
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();

    @Getter
    private boolean markedForDeletion = false;
    private boolean batchingStatusChanges = false;
    private final Set<AEKey> batchedStatusChanges = new HashSet<>();

    // ── NBT restore state ──
    @Getter
    private boolean restoredFromNbt = false;
    private boolean restoreRebindAttempted = false;
    private boolean restoreRebindSuccessful = false;
    @Getter
    private int restoredCancelGraceTicks = 0;
    private boolean restoredLinkRebound = false;
    private static final int RESTORED_CANCEL_GRACE_INITIAL = 100;
    private static final int RESTORE_GRACE_LOG_INTERVAL = 20;

    /**
     * Whether the CPU is still within its NBT-restore grace period.
     * During this period, the cluster MUST NOT prune or kill this CPU,
     * because the job may still be waiting for grid rebind.
     */
    public boolean isInRestoreGrace() {
        return restoredFromNbt && restoredCancelGraceTicks > 0;
    }

    private long debugPushedPatterns;
    private long debugExtractSkippedBecauseProvidersBusy;
    private long debugExtractPatternInputsCalls;
    private long debugPushPatternCalls;
    private long debugAcceptedCraftCount;
    private long debugAcceptedBatchCount;
    private long debugAcceptedBatchCraftCount;
    private long debugMaxBatchSize;
    private long debugExtractPatternInputsNs;
    private long debugExecuteCraftingNs;
    private long debugLastExecutionStatsTick = Long.MIN_VALUE;

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
    }

    public ICraftingSubmitResult trySubmitJob(
            IGrid grid, ICraftingPlan plan, IActionSource src, @Nullable ICraftingRequester requester) {
        // Already have a job.
        if (this.job != null)
            return CraftingSubmitResult.CPU_BUSY;
        // Check that the node is active.
        if (!cpu.isActive())
            return CraftingSubmitResult.CPU_OFFLINE;
        // Check bytes.
        if (cpu.getAvailableStorage() < plan.bytes())
            return CraftingSubmitResult.CPU_TOO_SMALL;

        if (!inventory.list.isEmpty())
            AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");

        // Try to extract required items.
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null)
            return CraftingSubmitResult.missingIngredient(missingIngredient);

        // Set CPU link and job.
        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new ExecutingCraftingJob(plan, this::postChange, linkCpu, playerId);
        markStatusDirty();

        // Crafting Monitor unsupported
        // cpu.updateOutput(plan.finalOutput());
        cpu.markDirty();

        // TODO: post monitor difference?

        notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);

        var craftingService = (CraftingService) grid.getCraftingService();
        // Always register CPU-side link so the job can be tracked and recovered
        craftingService.addLink(linkCpu);
        if (requester != null) {
            var linkReq = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, false, true), requester);
            craftingService.addLink(linkReq);
            return CraftingSubmitResult.successful(linkReq);
        } else {
            return CraftingSubmitResult.successful(null);
        }
    }

    public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {
        if (!cpu.isActive()) {
            setCantStoreItems(false);
            return;
        }
        setCantStoreItems(false);
        if (this.job == null) {
            this.storeItems();
            setCantStoreItems(!this.inventory.list.isEmpty());
            if (this.inventory.list.isEmpty()) {
                if (markedForDeletion) {
                    cpu.deactivate();
                }
            }
            return;
        }

        // ── Restored-from-NBT path: must rebind link BEFORE any cancel check ──
        if (restoredFromNbt && !restoreRebindSuccessful) {
            IGrid grid = cpu.getGrid();
            if (grid != null && !restoreRebindAttempted) {
                onRestoredToGrid(grid);
            }
            if (!restoreRebindSuccessful && restoredCancelGraceTicks > 0) {
                restoredCancelGraceTicks--;
                if (restoredCancelGraceTicks % RESTORE_GRACE_LOG_INTERVAL == 0) {
                    LOGGER.info("ECO CPU waiting for link rebind: grace={} jobId={}",
                            restoredCancelGraceTicks, job.link.getCraftingID());
                }
                return;
            }
            if (!restoreRebindSuccessful && restoredCancelGraceTicks <= 0) {
                safeAbortRestoredJob("link rebind failed after grace period");
                return;
            }
        }

        // ── Normal cancel check (only after restored path is resolved) ──
        if (job.link.isCanceled()) {
            LOGGER.warn("ECO CPU job link canceled — canceling. cpu={} jobId={} wasRestored={}",
                    cpu.getName(), job.link.getCraftingID(), restoredFromNbt);
            cancel();
            return;
        }

        if (job.suspended) {
            return;
        }

        var remainingOperations = getOperationLimit(cc);

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
    }

    /**
     * Re-bind the restored CraftingLink to AE2's CraftingService.
     * Must be called proactively during cluster formation, before the first tick.
     * Returns true if the link is healthy after rebinding.
     */
    public boolean onRestoredToGrid(IGrid grid) {
        if (job == null) {
            return true;
        }
        if (restoredLinkRebound) {
            return true;
        }
        restoreRebindAttempted = true;
        UUID jobId = job.link.getCraftingID();
        boolean wasCanceledBefore = job.link.isCanceled();

        LOGGER.info("ECO CPU onRestoredToGrid: jobId={} wasCanceledBefore={} cpu={}",
                jobId, wasCanceledBefore, cpu.getName());

        CraftingService craftingService = (CraftingService) grid.getCraftingService();
        craftingService.addLink(job.link);

        boolean isCanceledAfter = job.link.isCanceled();
        if (!isCanceledAfter) {
            restoreRebindSuccessful = true;
            restoredLinkRebound = true;
            restoredFromNbt = false;
            restoredCancelGraceTicks = 0;
            LOGGER.info("ECO CPU link rebind SUCCESS. jobId={} cpu={}", jobId, cpu.getName());
            return true;
        }

        LOGGER.warn("ECO CPU link still canceled after rebind. jobId={} cpu={} wasCanceledBefore={}",
                jobId, cpu.getName(), wasCanceledBefore);
        return false;
    }

    /**
     * Safely abort a restored job that could not be rebound.
     * Tries to recover items to network; does NOT silently drop the job.
     */
    private void safeAbortRestoredJob(String reason) {
        if (job == null)
            return;
        UUID jobId = job.link.getCraftingID();
        LOGGER.warn(
                "ECO CPU safeAbortRestoredJob: reason={} jobId={} finalOutput={} remainingAmount={} waitingForSize={} tasksSize={} cpu={}",
                reason, jobId,
                job.finalOutput != null ? job.finalOutput.what().getClass().getSimpleName() : "null",
                job.remainingAmount,
                job.waitingFor.list.size(),
                job.tasks.size(),
                cpu.getName());

        // Try to recover in-flight worker inputs
        recoverInflightWorkerInputs(jobId);

        // Try to dump items to network; if network unavailable, defer cleanup
        IGrid grid = cpu.getGrid();
        if (grid != null) {
            // Temporarily null job so storeItems() works
            var oldJob = this.job;
            this.job = null;
            try {
                this.storeItems();
            } finally {
                this.job = oldJob;
            }
            // If storeItems cleared inventory, we can safely finish
            if (this.inventory.list.isEmpty()) {
                this.job = null;
                markStatusDirty();
                markedForDeletion = true;
                restoredFromNbt = false;
                restoreRebindSuccessful = false;
                restoredLinkRebound = false;
                cpu.getCluster().updateGridForChangedCpu(cpu.getCluster());
                return;
            }
        }

        // Network unavailable or items remain — suspend instead of dropping
        if (job != null) {
            job.suspended = true;
            restoredFromNbt = false;
            restoreRebindAttempted = false;
            restoreRebindSuccessful = false;
            restoredLinkRebound = false;
            restoredCancelGraceTicks = 0;
            LOGGER.warn("ECO CPU restored job suspended (safe abort deferred). jobId={} cpu={}", jobId, cpu.getName());
        }
    }

    private int getOperationLimit(CraftingService craftingService) {
        int baseLimit = Math.max(1, cpu.getCoProcessors() + 1);
        if (job == null) {
            return baseLimit;
        }

        int ecoSlots = 0;
        Set<ECOCraftingSystemBlockEntity> countedControllers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var task : job.tasks.entrySet()) {
            if (task.getValue().value <= 0) {
                continue;
            }
            for (var provider : craftingService.getProviders(task.getKey())) {
                if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus)) {
                    continue;
                }
                ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
                if (controller != null && countedControllers.add(controller)) {
                    ecoSlots += patternBus.getAvailableThreadSlots();
                    if (ecoSlots >= ECO_PROVIDER_PUSH_BURST_LIMIT) {
                        return Math.max(baseLimit, ECO_PROVIDER_PUSH_BURST_LIMIT);
                    }
                }
            }
        }

        return Math.max(baseLimit, Math.min(ecoSlots, ECO_PROVIDER_PUSH_BURST_LIMIT));
    }

    /**
     * Try to push patterns into available interfaces, i.e. do the actual crafting
     * execution.
     *
     * @return How many patterns were successfully pushed.
     */
    public int executeCrafting(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        var job = this.job;
        if (job == null)
            return 0;

        var pushedPatterns = 0;
        long executeStartNs = DEBUG_EXECUTION_STATS ? System.nanoTime() : 0L;
        beginStatusChangeBatch();

        try {
            var it = job.tasks.entrySet().iterator();
            taskLoop: while (it.hasNext()) {
                var task = it.next();
                if (task.getValue().value <= 0) {
                    postPatternOutputsChange(task.getKey());
                    it.remove();
                    continue;
                }

                var details = task.getKey();
                while (task.getValue().value > 0 && pushedPatterns < maxPatterns) {
                    List<ICraftingProvider> providers = collectAvailableProviders(craftingService, details);
                    if (providers.isEmpty()) {
                        debugExtractSkippedBecauseProvidersBusy++;
                        continue taskLoop;
                    }

                    var expectedOutputs = new KeyCounter();
                    var expectedContainerItems = new KeyCounter();
                    long extractStartNs = DEBUG_EXECUTION_STATS ? System.nanoTime() : 0L;
                    if (DEBUG_EXECUTION_STATS) {
                        debugExtractPatternInputsCalls++;
                    }
                    @Nullable
                    var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                            details, inventory, level, expectedOutputs, expectedContainerItems);
                    if (DEBUG_EXECUTION_STATS) {
                        debugExtractPatternInputsNs += System.nanoTime() - extractStartNs;
                    }
                    if (craftingContainer == null) {
                        continue taskLoop;
                    }
                    ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.create(
                            details, craftingContainer, expectedOutputs, expectedContainerItems, level);

                    double patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
                    int batchResult = tryPushVerifiedFastPathBatch(
                            details,
                            execution,
                            craftingContainer,
                            providers,
                            energyService,
                            patternPower,
                            task.getValue().value,
                            maxPatterns - pushedPatterns);
                    if (batchResult > 0) {
                        pushedPatterns += batchResult;
                        task.getValue().value -= batchResult;
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
                        if (provider.isBusy()) {
                            continue;
                        }

                        if (energyService.extractAEPower(patternPower, Actionable.SIMULATE,
                                PowerMultiplier.CONFIG) < patternPower - 0.01) {
                            break;
                        }

                        if (DEBUG_EXECUTION_STATS) {
                            debugPushPatternCalls++;
                        }
                        pushed = provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                                ? patternBus.pushPattern(execution, job.link.getCraftingID())
                                : provider.pushPattern(details, craftingContainer);

                        if (pushed) {
                            energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                            pushedPatterns++;
                            recordPushedPattern(execution, 1);
                            recordAcceptedCrafts(1, false);

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
                    }

                    if (!pushed) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                        continue taskLoop;
                    }
                }
            }
        } finally {
            endStatusChangeBatch();
            if (DEBUG_EXECUTION_STATS) {
                debugPushedPatterns += pushedPatterns;
                debugExecuteCraftingNs += System.nanoTime() - executeStartNs;
                maybeLogExecutionStats();
            }
        }

        return pushedPatterns;
    }

    private void maybeLogExecutionStats() {
        long currentTick = TickHandler.instance().getCurrentTick();
        if (currentTick - debugLastExecutionStatsTick < 100) {
            return;
        }
        debugLastExecutionStatsTick = currentTick;
        double averageBatchSize = debugAcceptedBatchCount <= 0
                ? 0.0D
                : (double) debugAcceptedBatchCraftCount / (double) debugAcceptedBatchCount;
        LOGGER.debug(
                "ECO executeCrafting: pushedPatterns={} extractSkippedBecauseProvidersBusy={} extractPatternInputsCalls={} pushPatternCalls={} acceptedCraftCount={} acceptedBatchCount={} averageBatchSize={} maxBatchSize={} extractPatternInputsNs={} executeCraftingNs={}",
                debugPushedPatterns,
                debugExtractSkippedBecauseProvidersBusy,
                debugExtractPatternInputsCalls,
                debugPushPatternCalls,
                debugAcceptedCraftCount,
                debugAcceptedBatchCount,
                String.format(java.util.Locale.ROOT, "%.2f", averageBatchSize),
                debugMaxBatchSize,
                debugExtractPatternInputsNs,
                debugExecuteCraftingNs);
        debugPushedPatterns = 0;
        debugExtractSkippedBecauseProvidersBusy = 0;
        debugExtractPatternInputsCalls = 0;
        debugPushPatternCalls = 0;
        debugAcceptedCraftCount = 0;
        debugAcceptedBatchCount = 0;
        debugAcceptedBatchCraftCount = 0;
        debugMaxBatchSize = 0;
        debugExtractPatternInputsNs = 0;
        debugExecuteCraftingNs = 0;
    }

    private List<ICraftingProvider> collectAvailableProviders(CraftingService craftingService,
            IPatternDetails details) {
        List<ICraftingProvider> providers = new ArrayList<>();
        for (ICraftingProvider provider : craftingService.getProviders(details)) {
            if (!provider.isBusy()) {
                providers.add(provider);
            }
        }
        return providers;
    }

    private int tryPushVerifiedFastPathBatch(
            IPatternDetails details,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> providers,
            IEnergyService energyService,
            double patternPower,
            long taskRemaining,
            int tickBudgetRemaining) {
        if (!canAttemptBatchFastPath(execution) || taskRemaining <= 1 || tickBudgetRemaining <= 1) {
            return 0;
        }

        int requested = (int) Math.min(
                Math.min(taskRemaining, tickBudgetRemaining),
                Math.min(ECO_BATCH_FAST_PATH_LIMIT, ECO_BATCH_FAST_PATH_TICK_LIMIT));
        ECOCraftingPatternBusBlockEntity selectedPatternBus = null;
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer selectedOffer = null;
        for (ICraftingProvider provider : providers) {
            if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus)) {
                continue;
            }
            var offer = patternBus.findBatchFastPathOffer(execution, requested);
            if (offer != null && offer.maxBatchSize() > 1) {
                selectedPatternBus = patternBus;
                selectedOffer = offer;
                break;
            }
        }
        if (selectedPatternBus == null || selectedOffer == null) {
            return 0;
        }

        ECOCraftingSystemBlockEntity controller = selectedPatternBus.getCraftingController();
        if (controller == null) {
            return 0;
        }

        int batchSize = Math.min(requested, selectedOffer.maxBatchSize());
        batchSize = Math.min(batchSize, maxBatchSizeFromEnergy(energyService, patternPower, batchSize));
        batchSize = controller.getCraftingCoolantCraftLimit(5, controller.getEffectiveOverclockTimes(), batchSize);
        if (batchSize <= 1) {
            return 0;
        }

        int extraCrafts = batchSize - 1;
        int availableExtraCrafts = ECOBatchCraftingHelper.maxCraftsFromInventory(
                inventory,
                execution.inputItems(),
                extraCrafts);
        batchSize = Math.min(batchSize, availableExtraCrafts + 1);
        if (batchSize <= 1) {
            return 0;
        }

        var extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), batchSize - 1);
        var inputTotal = ECOBatchCraftingHelper.multiply(execution.inputItems(), batchSize);
        boolean extraInputsExtracted = false;
        try {
            if (!ECOBatchCraftingHelper.canExtractExact(inventory, extraInputs)) {
                return 0;
            }
            if (energyService.extractAEPower(patternPower * batchSize, Actionable.SIMULATE,
                    PowerMultiplier.CONFIG) < patternPower * batchSize - 0.01) {
                return 0;
            }
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
            extraInputsExtracted = true;
            var request = new ECOBatchCraftingRequest(
                    details,
                    execution.key(),
                    batchSize,
                    execution.inputItems(),
                    execution.expectedOutputs(),
                    execution.expectedContainerItems(),
                    job.link.getCraftingID());
            if (DEBUG_EXECUTION_STATS) {
                debugPushPatternCalls++;
            }
            if (!selectedPatternBus.pushBatch(request)) {
                ECOBatchCraftingHelper.insertAll(inventory, inputTotal);
                return -1;
            }
            energyService.extractAEPower(patternPower * batchSize, Actionable.MODULATE, PowerMultiplier.CONFIG);
            recordPushedPattern(execution, batchSize);
            recordAcceptedCrafts(batchSize, true);
            return batchSize;
        } catch (RuntimeException e) {
            LOGGER.warn("ECO batch fast path failed, reinjecting inputs and falling back to the slow path", e);
            if (extraInputsExtracted) {
                ECOBatchCraftingHelper.insertAll(inventory, inputTotal);
            } else {
                CraftingCpuHelper.reinjectPatternInputs(inventory, firstCraftingContainer);
            }
            selectedOffer.worker().getFastPathCache().recordException();
            return -1;
        }
    }

    private boolean canAttemptBatchFastPath(ECOExtractedPatternExecution execution) {
        return execution.key() != null
                && execution.fastPathEligible()
                && NEConfig.isEcoAe2FastPathEnabled()
                && !NEConfig.postCraftingEvent;
    }

    private int maxBatchSizeFromEnergy(IEnergyService energyService, double patternPower, int requested) {
        if (requested <= 0) {
            return 0;
        }
        if (patternPower <= 0.0D) {
            return requested;
        }
        int batchSize = requested;
        while (batchSize > 0) {
            double totalPower = patternPower * batchSize;
            if (energyService.extractAEPower(totalPower, Actionable.SIMULATE,
                    PowerMultiplier.CONFIG) >= totalPower - 0.01) {
                return batchSize;
            }
            batchSize--;
        }
        return 0;
    }

    private void recordPushedPattern(ECOExtractedPatternExecution execution, int craftCount) {
        int multiplier = Math.max(1, craftCount);
        for (var expectedOutput : execution.expectedOutputs()) {
            job.waitingFor.insert(expectedOutput.what(), expectedOutput.amount() * multiplier, Actionable.MODULATE);
        }
        postGenericStackKeysChange(execution.expectedOutputs());
        for (var expectedContainerItem : execution.expectedContainerItems()) {
            job.waitingFor.insert(
                    expectedContainerItem.what(),
                    expectedContainerItem.amount() * multiplier,
                    Actionable.MODULATE);
            job.timeTracker.addMaxItems(
                    expectedContainerItem.amount() * multiplier,
                    expectedContainerItem.what().getType());
        }
        postGenericStackKeysChange(execution.expectedContainerItems());

        cpu.markDirty();
    }

    private void recordAcceptedCrafts(int craftCount, boolean batch) {
        if (!DEBUG_EXECUTION_STATS) {
            return;
        }
        debugAcceptedCraftCount += craftCount;
        if (batch) {
            debugAcceptedBatchCount++;
            debugAcceptedBatchCraftCount += craftCount;
            debugMaxBatchSize = Math.max(debugMaxBatchSize, craftCount);
        }
    }

    /**
     * Called by the CraftingService with an Integer.MAX_VALUE priority to inject
     * items that are being waited for.
     *
     * @return Consumed amount.
     */
    public long insert(AEKey what, long amount, Actionable type) {
        // also stop accepting items when the job is complete, i.e. to prevent
        // re-insertion when pushing out
        // items during storeItems
        if (what == null || job == null)
            return 0;

        // Only accept items we are waiting for.
        var waitingFor = job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0;
        }

        // Make sure we don't insert more than what we are waiting for.
        if (amount > waitingFor) {
            amount = waitingFor;
        }

        if (type == Actionable.MODULATE) {
            job.timeTracker.decrementItems(amount, what.getType());
            job.waitingFor.extract(what, amount, Actionable.MODULATE);
            postChange(what);
            cpu.markDirty();
        }

        long inserted = amount;
        if (what.matches(job.finalOutput)) {
            // Final output is special: it goes directly into the requester
            inserted = job.link.insert(what, amount, type);

            // Note: we ignore any remainder (could be the entire input if there is no
            // requester),
            // we already marked the items as done, and we might even finish the job.

            // This means that the job can be marked as finished even if some items were not
            // actually inserted.
            // In some cases, repeated failed inserts of a fraction of the final output
            // might prevent some recipes from
            // being pushed.
            // TODO: Look into fixing this, perhaps we could use the network monitor to
            // check how much was really
            // TODO: inserted into the network.
            // TODO: Another solution is to wait until all recipes have been pushed before
            // cancelling the job.

            if (type == Actionable.MODULATE) {
                // Update count and displayed CPU stack, and finish the job if possible.
                postChange(what);
                job.remainingAmount = Math.max(0, job.remainingAmount - amount);

                if (job.remainingAmount <= 0) {
                    finishJob(true);
                }
            }
        } else {
            if (type == Actionable.MODULATE) {
                inventory.insert(what, amount, Actionable.MODULATE);
                // Explicitly notify stored count changed in addition to inventory callback
                postChange(what);
            }
        }

        return inserted;
    }

    /**
     * Finish the current job.
     *
     * @param success True if the job is complete, false if it was cancelled.
     */
    private void finishJob(boolean success) {
        Set<AEKey> waitingKeys = collectWaitingKeys();
        Set<AEKey> pendingKeys = collectPendingOutputKeys();

        if (success) {
            job.link.markDone();
        } else {
            job.link.cancel();
        }

        // TODO: log

        job.waitingFor.clear();
        postKeysChange(waitingKeys);
        // Notify opened menus of cancelled scheduled tasks.
        postKeysChange(pendingKeys);

        notifyJobOwner(
                job, success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        // Finish job.
        this.job = null;
        restoredLinkRebound = false;
        markStatusDirty();
        cpu.getCluster().updateGridForChangedCpu(cpu.getCluster());

        // Store all remaining items.
        this.storeItems();
    }

    /**
     * Cancel the current job.
     */
    public void cancel() {
        // No job to cancel :P
        if (job == null)
            return;

        UUID craftingJobId = job.link.getCraftingID();
        markStatusDirty();
        finishJob(false);
        restoredLinkRebound = false;
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

    /**
     * Tries to dump all locally stored items back into the storage network.
     */
    public void storeItems() {
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        // Short-circuit if there is nothing to do.
        if (this.inventory.list.isEmpty())
            return;

        var g = cpu.getGrid();
        if (g == null)
            return;

        var storage = g.getStorageService().getInventory();

        for (var entry : this.inventory.list) {
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE,
                    cpu.getActionSource());

            // The network was unable to receive all of the items, i.e. no or not enough
            // storage space left
            entry.setValue(entry.getLongValue() - inserted);
            this.postChange(entry.getKey());
        }
        this.inventory.list.removeZeros();

        cpu.markDirty();
    }

    private void postChange(AEKey what) {
        if (batchingStatusChanges) {
            if (what != null) {
                batchedStatusChanges.add(what);
            }
            return;
        }
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        markStatusDirty();
        for (var listener : listeners) {
            listener.accept(what);
        }
    }

    private void beginStatusChangeBatch() {
        batchingStatusChanges = true;
        batchedStatusChanges.clear();
    }

    private void endStatusChangeBatch() {
        batchingStatusChanges = false;
        if (batchedStatusChanges.isEmpty()) {
            return;
        }
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        markStatusDirty();
        for (AEKey key : batchedStatusChanges) {
            for (var listener : listeners) {
                listener.accept(key);
            }
        }
        batchedStatusChanges.clear();
    }

    private void markStatusDirty() {
        statusRevision++;
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
    }

    private void postPatternOutputsChange(IPatternDetails details) {
        Set<AEKey> keys = new HashSet<>();
        for (var output : details.getOutputs()) {
            keys.add(output.what());
        }
        postKeysChange(keys);
    }

    private void postCounterKeysChange(KeyCounter counter) {
        Set<AEKey> keys = new HashSet<>();
        for (var entry : counter) {
            keys.add(entry.getKey());
        }
        postKeysChange(keys);
    }

    private void postGenericStackKeysChange(List<GenericStack> stacks) {
        Set<AEKey> keys = new HashSet<>();
        for (var stack : stacks) {
            keys.add(stack.what());
        }
        postKeysChange(keys);
    }

    private Set<AEKey> collectWaitingKeys() {
        Set<AEKey> keys = new HashSet<>();
        if (this.job != null) {
            for (var entry : this.job.waitingFor.list) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    private Set<AEKey> collectPendingOutputKeys() {
        Set<AEKey> keys = new HashSet<>();
        if (this.job != null) {
            for (var task : this.job.tasks.keySet()) {
                for (var output : task.getOutputs()) {
                    keys.add(output.what());
                }
            }
        }
        return keys;
    }

    private void postKeysChange(Set<AEKey> keys) {
        for (AEKey key : keys) {
            postChange(key);
        }
    }

    public boolean hasJob() {
        return this.job != null;
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        return this.job != null ? this.job.finalOutput : null;
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
            markStatusDirty();
            if (this.job.finalOutput == null) {
                LOGGER.warn("ECO CPU restored with null finalOutput (job NBT may be corrupted). "
                        + "Dropping job and marking CPU for cleanup. cpu={}", cpu.getName());
                this.job = null;
                markedForDeletion = true;
            } else {
                // Mark as restored from NBT — tickCraftingLogic will handle rebind proactively
                this.restoredFromNbt = true;
                this.restoreRebindAttempted = false;
                this.restoreRebindSuccessful = false;
                this.restoredLinkRebound = false;
                this.restoredCancelGraceTicks = RESTORED_CANCEL_GRACE_INITIAL;
                LOGGER.info("ECO CPU job restored from NBT. cpu={} jobId={} finalOutput={} remainingAmount={}",
                        cpu.getName(),
                        this.job.link.getCraftingID(),
                        this.job.finalOutput != null ? this.job.finalOutput.what().getClass().getSimpleName() : "null",
                        this.job.remainingAmount);
            }
        }
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
     * Register a listener that will receive stacks when either the stored items,
     * await items or pending outputs change.
     * This is only used by the menu. Make sure to remove it by calling
     * {@link #removeListener}.
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
                        count += output.amount() * t.getValue().value;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Used by the menu to gather all the kinds of stored items.
     */
    public void getAllItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        if (this.job != null) {
            out.addAll(job.waitingFor.list);
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    out.add(output.what(), output.amount() * t.getValue().value);
                }
            }
        }
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
            markStatusDirty();
            postKeysChange(collectWaitingKeys());
            postKeysChange(collectPendingOutputKeys());
        }
    }

    private void setCantStoreItems(boolean cantStoreItems) {
        if (this.cantStoreItems != cantStoreItems) {
            this.cantStoreItems = cantStoreItems;
            markStatusDirty();
        }
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        var playerId = job.playerId;
        if (playerId == null) {
            return;
        }

        var server = cpu.getLevel().getServer();
        var connectedPlayer = IPlayerRegistry.getConnected(server, playerId);
        if (connectedPlayer != null) {
            var jobId = job.link.getCraftingID();
            BasePacket message = new CraftingJobStatusPacket(
                    jobId, job.finalOutput.what(), job.finalOutput.amount(), job.remainingAmount, status);
            connectedPlayer.connection
                    .send(message.toPacket(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        }
    }

    public void markForDeletion() {
        this.markedForDeletion = true;
    }
}
