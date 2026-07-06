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
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.compat.ae2.ExtendedAEPlusVirtualCraftingCompat;
import cn.dancingsnow.neoecoae.config.NEConfig;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingCPULogic {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

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
    private boolean batchedAnyStatusChange = false;
    private boolean batchedFullStatusChange = false;
    private final List<AggressiveSimulatedCraft> aggressiveSimulatedCrafts = new ArrayList<>();
    private final Set<AggressivePoolReservation> aggressivePoolReservations = new HashSet<>();
    private boolean flushingAggressiveSimulatedOutput = false;
    private boolean recoverAggressiveAfterFlush = false;

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
     * <p>
     * Returns true when: a job exists AND the CPU was restored from NBT
     * ({@code restoredFromNbt} is true). The {@code restoredFromNbt} flag
     * covers the entire window from deserialization through rebind or safe abort.
     */
    public boolean isInRestoreGrace() {
        return this.job != null && this.restoredFromNbt;
    }

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
    }

    public ICraftingSubmitResult trySubmitJob(
            IGrid grid, ICraftingPlan plan, IActionSource src, @Nullable ICraftingRequester requester) {
        // Already have a job.
        if (this.job != null) return CraftingSubmitResult.CPU_BUSY;
        // Check that the node is active.
        if (!cpu.isActive()) return CraftingSubmitResult.CPU_OFFLINE;
        // Check bytes.
        if (cpu.getAvailableStorage() < plan.bytes()) return CraftingSubmitResult.CPU_TOO_SMALL;

        if (!inventory.list.isEmpty()) AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");

        // Try to extract required items.
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null) return CraftingSubmitResult.missingIngredient(missingIngredient);

        // Set CPU link and job.
        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new ExecutingCraftingJob(plan, this::postChange, linkCpu, cpu.getLevel(), playerId);
        markStatusDirty();

        // Crafting Monitor unsupported
        // cpu.updateOutput(plan.finalOutput());
        cpu.markDirty();

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
                    LOGGER.info(
                            "ECO CPU waiting for link rebind: grace={} jobId={}",
                            restoredCancelGraceTicks,
                            job.link.getCraftingID());
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
            LOGGER.warn(
                    "ECO CPU job link canceled — canceling. cpu={} jobId={} wasRestored={}",
                    cpu.getName(),
                    job.link.getCraftingID(),
                    restoredFromNbt);
            cancel();
            return;
        }

        if (job.link.isDone() || job.remainingAmount <= 0) {
            finishJob(true);
            return;
        }

        if (job.suspended) {
            return;
        }

        tickAggressiveSimulatedCrafts(eg);
        if (this.job == null) {
            return;
        }
        if (this.job.userPaused) {
            return;
        }

        int slowPatternBudget = getOperationLimit();
        var batchBudget = new FastPathBatchBudget(effectiveFastPathTickLimit());
        int totalPatternBudget = totalPatternBudget(slowPatternBudget, batchBudget.remaining());
        executeCrafting(slowPatternBudget, totalPatternBudget, cc, eg, cpu.getLevel(), batchBudget);
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

        LOGGER.info(
                "ECO CPU onRestoredToGrid: jobId={} wasCanceledBefore={} cpu={}",
                jobId,
                wasCanceledBefore,
                cpu.getName());

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

        LOGGER.warn(
                "ECO CPU link still canceled after rebind. jobId={} cpu={} wasCanceledBefore={}",
                jobId,
                cpu.getName(),
                wasCanceledBefore);
        return false;
    }

    /**
     * Safely abort a restored job that could not be rebound.
     * Tries to recover items to network; does NOT silently drop the job.
     */
    private void safeAbortRestoredJob(String reason) {
        if (job == null) return;
        UUID jobId = job.link.getCraftingID();
        LOGGER.warn(
                "ECO CPU safeAbortRestoredJob: reason={} jobId={} finalOutput={} remainingAmount={} waitingForSize={} tasksSize={} cpu={}",
                reason,
                jobId,
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
                recoverAggressiveSimulatedCraftsToInventory();
                this.job = null;
                markStatusDirty();
                markedForDeletion = true;
                restoredFromNbt = false;
                restoreRebindSuccessful = false;
                restoredLinkRebound = false;
                this.storeItems();
                setCantStoreItems(!this.inventory.list.isEmpty());
                cpu.getCluster().updateGridForChangedCpu(cpu.getCluster());
                if (this.inventory.list.isEmpty()) {
                    cpu.deactivate();
                }
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

    private int getOperationLimit() {
        int cpuLimit = Math.max(1, cpu.getCoProcessors() + 1);
        return Math.min(cpuLimit, NEConfig.ecoCpuPushTickLimit);
    }

    static int totalPatternBudget(int slowPatternBudget, int batchPatternBudget) {
        return Math.max(Math.max(0, slowPatternBudget), Math.max(0, batchPatternBudget));
    }

    private int executeCrafting(
            int slowPatternBudget,
            int totalPatternBudget,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            FastPathBatchBudget batchBudget) {
        var job = this.job;
        if (job == null) return 0;

        CraftingExecutionProgress executionProgress =
                new CraftingExecutionProgress(slowPatternBudget, totalPatternBudget, batchBudget);
        beginStatusChangeBatch();

        try {
            DispatchPassState passState = new DispatchPassState();
            while (true) {
                int pushedBeforePass = executionProgress.pushedPatterns();
                passState.beginPass();
                var it = job.tasks.entrySet().iterator();
                while (it.hasNext()) {
                    DispatchTaskResult result = tryDispatchTask(
                            job, it.next(), it, passState, executionProgress, craftingService, energyService, level);
                    if (result == DispatchTaskResult.JOB_FINISHED) {
                        return executionProgress.pushedPatterns();
                    }
                    if (result == DispatchTaskResult.STOP_PASS) {
                        break;
                    }
                }

                if (!passState.shouldRunFallbackPass(executionProgress, pushedBeforePass)) {
                    break;
                }

                passState.startFallbackPass();
            }
        } finally {
            endStatusChangeBatchSafely();
        }

        return executionProgress.pushedPatterns();
    }

    private ProviderSelection collectProviders(CraftingService craftingService, IPatternDetails details) {
        List<ICraftingProvider> providers = new ArrayList<>();
        List<ECOCraftingPatternBusBlockEntity> batchBuses = new ArrayList<>();
        for (ICraftingProvider provider : craftingService.getProviders(details)) {
            providers.add(provider);
            if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus) {
                batchBuses.add(patternBus);
            }
        }
        return new ProviderSelection(List.copyOf(providers), List.copyOf(batchBuses));
    }

    private boolean hasPotentialProvider(ProviderSelection providers) {
        if (!providers.batchBuses().isEmpty()) {
            return true;
        }
        for (ICraftingProvider provider : providers.all()) {
            if (!provider.isBusy()) {
                return true;
            }
        }
        return false;
    }

    private DispatchTaskResult tryDispatchTask(
            ExecutingCraftingJob job,
            Map.Entry<IPatternDetails, ExecutingCraftingJob.TaskProgress> task,
            Iterator<Map.Entry<IPatternDetails, ExecutingCraftingJob.TaskProgress>> iterator,
            DispatchPassState passState,
            CraftingExecutionProgress executionProgress,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        var progress = task.getValue();
        if (progress.value <= 0) {
            postPatternOutputsChange(task.getKey());
            iterator.remove();
            return DispatchTaskResult.NEXT_TASK;
        }

        var details = task.getKey();
        var dispatchBlock = job.getDispatchBlock(details);
        if (!passState.allowUnfinishedDependencies()
                && dispatchBlock == ExecutingCraftingJob.DispatchBlock.UNFINISHED_DEPENDENCY) {
            passState.markUnfinishedDependencyBlocked();
        }

        ProviderSelection providers = collectProviders(craftingService, details);
        if (providers.all().isEmpty()) {
            return DispatchTaskResult.NEXT_TASK;
        }

        while (progress.value > 0 && executionProgress.canPushMore()) {
            if (!executionProgress.canPushAnyPath()) {
                return DispatchTaskResult.STOP_PASS;
            }
            if (!hasPotentialProvider(providers)) {
                return DispatchTaskResult.NEXT_TASK;
            }

            if (maxCraftsNeededForFinalOutput(List.of(details.getOutputs())) <= 0) {
                return DispatchTaskResult.NEXT_TASK;
            }

            // Dependency and in-flight counters describe future work, not the
            // inventory that is available right now. If enough intermediate
            // items have already returned, extraction is the authoritative gate
            // and lets dependent patterns fill the remaining batch capacity.
            @Nullable ExtractedPatternAttempt attempt = extractPatternAttempt(details, progress, level);
            if (attempt == null) {
                return DispatchTaskResult.NEXT_TASK;
            }

            if (NEConfig.isEcoAggressiveFastPathEnabled() && canAttemptAggressiveFastPath(attempt.execution())) {
                @Nullable ECOCraftingSystemBlockEntity aggressiveController = firstCraftingController(providers.batchBuses());
                if (aggressiveController != null) {
                    PushResult pushResult = tryScheduleAggressiveSimulatedCraft(
                            progress, aggressiveController, attempt, executionProgress);
                    if (!pushResult.pushed()) {
                        return DispatchTaskResult.NEXT_TASK;
                    }
                    DispatchTaskResult commitResult = commitPushedCrafts(
                            details, progress, iterator, passState, executionProgress, pushResult.craftCount());
                    if (commitResult != DispatchTaskResult.CONTINUE_TASK) {
                        return commitResult;
                    }
                    continue;
                }
            }

            DispatchTaskResult fallbackResult = tryPushFallbackAfterAggressiveMiss(
                    details, progress, providers, attempt, executionProgress, energyService, iterator, passState);
            if (fallbackResult != DispatchTaskResult.CONTINUE_TASK) {
                return fallbackResult;
            }
        }

        return DispatchTaskResult.NEXT_TASK;
    }

    private DispatchTaskResult tryPushFallbackAfterAggressiveMiss(
            IPatternDetails details,
            ExecutingCraftingJob.TaskProgress progress,
            ProviderSelection providers,
            ExtractedPatternAttempt attempt,
            CraftingExecutionProgress executionProgress,
            IEnergyService energyService,
            Iterator<Map.Entry<IPatternDetails, ExecutingCraftingJob.TaskProgress>> iterator,
            DispatchPassState passState) {
        PushResult pushResult =
                tryPushFastPathOrFallback(details, progress, providers, attempt, executionProgress, energyService);
        if (pushResult.jobFinished()) {
            return DispatchTaskResult.JOB_FINISHED;
        }
        if (!pushResult.pushed()) {
            return DispatchTaskResult.NEXT_TASK;
        }

        return commitPushedCrafts(details, progress, iterator, passState, executionProgress, pushResult.craftCount());
    }

    @Nullable private ExtractedPatternAttempt extractPatternAttempt(
            IPatternDetails details, ExecutingCraftingJob.TaskProgress progress, Level level) {
        var expectedOutputs = new KeyCounter();
        var expectedContainerItems = new KeyCounter();
        @Nullable var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                details, inventory, level, expectedOutputs, expectedContainerItems);
        if (craftingContainer == null) {
            return null;
        }

        ECOExtractedPatternExecution execution =
                progress.createPatternExecution(details, craftingContainer, expectedContainerItems, level);
        double patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
        return new ExtractedPatternAttempt(craftingContainer, execution, patternPower);
    }

    private PushResult tryScheduleAggressiveSimulatedCraft(
            ExecutingCraftingJob.TaskProgress progress,
            ECOCraftingSystemBlockEntity controller,
            ExtractedPatternAttempt attempt,
            CraftingExecutionProgress executionProgress) {
        List<GenericStack> inputsPerCraft = aggressiveInputItems(attempt);
        if (attempt.execution().expectedOutputs().isEmpty() || inputsPerCraft.isEmpty()) {
            reinjectExtractedInputs(attempt);
            return PushResult.notPushed();
        }

        int requested = (int) Math.min(
                Math.min(progress.value, executionProgress.totalBudgetRemaining()),
                Math.min(effectiveFastPathBatchLimit(controller), executionProgress.batchBudgetRemaining()));
        requested = Math.min(requested, maxCraftsNeededForFinalOutput(attempt.execution()));
        requested = Math.min(requested, controller.getCurrentBatchSlots());
        requested = controller.getCraftingCoolantCraftLimit(5, controller.getEffectiveOverclockTimes(), requested);
        if (requested <= 0) {
            reinjectExtractedInputs(attempt);
            return PushResult.notPushed();
        }

        int extraCrafts = Math.max(0, requested - 1);
        int availableExtraCrafts =
                ECOBatchCraftingHelper.maxCraftsFromInventory(inventory, inputsPerCraft, extraCrafts);
        int batchSize = Math.max(1, Math.min(requested, availableExtraCrafts + 1));

        PendingPatternAccounting accounting;
        List<GenericStack> inputTotal;
        List<GenericStack> extraInputs;
        try {
            accounting = preparePushedPatternAccounting(attempt.execution(), batchSize);
            inputTotal = ECOBatchCraftingHelper.multiply(inputsPerCraft, batchSize);
            extraInputs = ECOBatchCraftingHelper.multiply(inputsPerCraft, batchSize - 1);
        } catch (RuntimeException e) {
            LOGGER.warn("ECO aggressive fast path accounting failed; not dispatching pattern", e);
            reinjectExtractedInputs(attempt);
            job.suspended = true;
            return PushResult.notPushed();
        }

        boolean extraInputsExtracted = false;
        try {
            if (!extraInputs.isEmpty()) {
                if (!ECOBatchCraftingHelper.canExtractExact(inventory, extraInputs)) {
                    reinjectExtractedInputs(attempt);
                    return PushResult.notPushed();
                }
                ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
                extraInputsExtracted = true;
            }
            if (!controller.tryConsumeCoolant(
                    coolantAmountForCrafts(batchSize), controller.getEffectiveOverclockTimes())) {
                if (extraInputsExtracted) {
                    ECOBatchCraftingHelper.insertAllOrThrow(inventory, extraInputs);
                }
                reinjectExtractedInputs(attempt);
                return PushResult.notPushed();
            }

            aggressiveSimulatedCrafts.add(new AggressiveSimulatedCraft(
                    controller.getBlockPos(),
                    job.link.getCraftingID(),
                    inputTotal,
                    accounting.expectedOutputs(),
                    accounting.expectedContainerItems(),
                    Math.max(1, batchSize),
                    0,
                    false));
            syncAggressivePoolSlots();
            recordPushedPattern(accounting);
            executionProgress.recordBatchPush(batchSize);
            cpu.markDirty();
            return PushResult.pushed(batchSize);
        } catch (RuntimeException e) {
            LOGGER.warn("ECO aggressive fast path failed; reinjecting inputs", e);
            if (extraInputsExtracted) {
                ECOBatchCraftingHelper.insertAllOrThrow(inventory, extraInputs);
            }
            reinjectExtractedInputs(attempt);
            job.suspended = true;
            return PushResult.notPushed();
        }
    }

    static boolean canAttemptAggressiveFastPath(ECOExtractedPatternExecution execution) {
        return ECOFastPathEligibility.canUse(execution);
    }

    static int coolantAmountForCrafts(int craftCount) {
        int normalizedCrafts = Math.max(1, craftCount);
        return normalizedCrafts > Integer.MAX_VALUE / 5 ? Integer.MAX_VALUE : normalizedCrafts * 5;
    }

    private List<GenericStack> aggressiveInputItems(ExtractedPatternAttempt attempt) {
        if (!attempt.execution().inputItems().isEmpty()) {
            return attempt.execution().inputItems();
        }
        return ECOFastPathStacks.copyCounters(attempt.craftingContainer());
    }

    @Nullable private ECOCraftingSystemBlockEntity firstCraftingController(List<ECOCraftingPatternBusBlockEntity> patternBuses) {
        for (ECOCraftingPatternBusBlockEntity patternBus : patternBuses) {
            ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
            if (controller != null) {
                return controller;
            }
        }
        return null;
    }

    private PushResult tryPushFastPathOrFallback(
            IPatternDetails details,
            ExecutingCraftingJob.TaskProgress progress,
            ProviderSelection providers,
            ExtractedPatternAttempt attempt,
            CraftingExecutionProgress executionProgress,
            IEnergyService energyService) {
        int batchResult = tryPushVerifiedFastPathBatch(
                details,
                attempt.execution(),
                attempt.craftingContainer(),
                providers.batchBuses(),
                energyService,
                attempt.patternPower(),
                progress.value,
                executionProgress.totalBudgetRemaining(),
                executionProgress.batchBudgetRemaining());
        if (batchResult > 0) {
            executionProgress.recordBatchPush(batchResult);
            return PushResult.pushed(batchResult);
        }
        if (batchResult < 0) {
            return PushResult.notPushed();
        }
        if (!executionProgress.canPushSlowPath()) {
            reinjectExtractedInputs(attempt);
            return PushResult.notPushed();
        }
        return tryPushSlowPattern(details, progress, providers, attempt, executionProgress, energyService);
    }

    private PushResult tryPushSlowPattern(
            IPatternDetails details,
            ExecutingCraftingJob.TaskProgress progress,
            ProviderSelection providers,
            ExtractedPatternAttempt attempt,
            CraftingExecutionProgress executionProgress,
            IEnergyService energyService) {
        PendingPatternAccounting accounting;
        try {
            accounting = preparePushedPatternAccounting(attempt.execution(), 1);
        } catch (RuntimeException e) {
            LOGGER.error("Unable to account pushed crafting pattern; suspending ECO CPU job", e);
            reinjectExtractedInputs(attempt);
            job.suspended = true;
            return PushResult.notPushed();
        }

        for (ICraftingProvider provider : providers.all()) {
            if (provider.isBusy()) {
                continue;
            }

            if (energyService.extractAEPower(attempt.patternPower(), Actionable.SIMULATE, PowerMultiplier.CONFIG)
                    < attempt.patternPower() - 0.01) {
                break;
            }

            boolean virtualCompletesJob = shouldCompleteVirtualCraftingJob(provider, progress);
            boolean pushed = provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                    ? patternBus.pushPattern(attempt.execution(), job.link.getCraftingID())
                    : provider.pushPattern(details, attempt.craftingContainer());

            if (pushed) {
                energyService.extractAEPower(attempt.patternPower(), Actionable.MODULATE, PowerMultiplier.CONFIG);
                executionProgress.recordSlowPush();
                if (virtualCompletesJob) {
                    finishJob(true);
                    return PushResult.jobFinished(1);
                }
                recordPushedPattern(accounting);
                return PushResult.pushed(1);
            }
        }

        reinjectExtractedInputs(attempt);
        return PushResult.notPushed();
    }

    private DispatchTaskResult commitPushedCrafts(
            IPatternDetails details,
            ExecutingCraftingJob.TaskProgress progress,
            Iterator<Map.Entry<IPatternDetails, ExecutingCraftingJob.TaskProgress>> iterator,
            DispatchPassState passState,
            CraftingExecutionProgress executionProgress,
            int craftCount) {
        progress.value -= craftCount;
        postPatternOutputsChange(details);
        if (progress.value <= 0) {
            iterator.remove();
            return passState.allowUnfinishedDependencies()
                    ? DispatchTaskResult.STOP_PASS
                    : DispatchTaskResult.NEXT_TASK;
        }
        if (passState.allowUnfinishedDependencies() || !executionProgress.canPushMore()) {
            return DispatchTaskResult.STOP_PASS;
        }
        return DispatchTaskResult.CONTINUE_TASK;
    }

    private void reinjectExtractedInputs(ExtractedPatternAttempt attempt) {
        CraftingCpuHelper.reinjectPatternInputs(inventory, attempt.craftingContainer());
    }

    private boolean shouldCompleteVirtualCraftingJob(
            ICraftingProvider provider, ExecutingCraftingJob.TaskProgress matchedProgress) {
        if (!ExtendedAEPlusVirtualCraftingCompat.isVirtualCraftingProvider(provider)) {
            return false;
        }
        if (matchedProgress == null || matchedProgress.value > 1) {
            return false;
        }

        for (ExecutingCraftingJob.TaskProgress progress : job.tasks.values()) {
            long remaining = progress.value;
            if (progress == matchedProgress) {
                remaining--;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private int tryPushVerifiedFastPathBatch(
            IPatternDetails details,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ECOCraftingPatternBusBlockEntity> patternBuses,
            IEnergyService energyService,
            double patternPower,
            long taskRemaining,
            int totalBudgetRemaining,
            int batchBudgetRemaining) {
        if (!ECOFastPathEligibility.canUse(execution)
                || taskRemaining <= 1
                || totalBudgetRemaining <= 1
                || batchBudgetRemaining <= 1) {
            return 0;
        }

        int requested = (int) Math.min(
                Math.min(taskRemaining, totalBudgetRemaining),
                Math.min(effectiveFastPathBatchLimit(null), batchBudgetRemaining));
        requested = Math.min(requested, maxCraftsNeededForFinalOutput(execution));
        if (requested <= 1) {
            return 0;
        }
        ECOCraftingPatternBusBlockEntity selectedPatternBus = null;
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer selectedOffer = null;
        for (ECOCraftingPatternBusBlockEntity patternBus : patternBuses) {
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

        requested = Math.min(requested, effectiveFastPathBatchLimit(controller));
        int batchSize = Math.min(requested, selectedOffer.maxBatchSize());
        batchSize = Math.min(batchSize, maxBatchSizeFromEnergy(energyService, patternPower, batchSize));
        batchSize = controller.getCraftingCoolantCraftLimit(5, controller.getEffectiveOverclockTimes(), batchSize);
        int controllerBatchSlots = controller.getCurrentBatchSlots();
        batchSize = Math.min(batchSize, controllerBatchSlots);
        if (batchSize <= 1) {
            return 0;
        }

        int extraCrafts = batchSize - 1;
        int availableExtraCrafts =
                ECOBatchCraftingHelper.maxCraftsFromInventory(inventory, execution.inputItems(), extraCrafts);
        batchSize = Math.min(batchSize, availableExtraCrafts + 1);
        if (batchSize <= 1) {
            return 0;
        }

        PendingPatternAccounting accounting;
        try {
            accounting = preparePushedPatternAccounting(execution, batchSize);
        } catch (RuntimeException e) {
            LOGGER.warn("ECO batch fast path accounting preflight failed; falling back to the slow path", e);
            selectedOffer.worker().getFastPathCache().recordException();
            return 0;
        }

        var extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), batchSize - 1);
        boolean extraInputsExtracted = false;
        boolean batchAccepted = false;
        try {
            if (!ECOBatchCraftingHelper.canExtractExact(inventory, extraInputs)) {
                return 0;
            }
            if (energyService.extractAEPower(patternPower * batchSize, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                    < patternPower * batchSize - 0.01) {
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
            if (!selectedPatternBus.pushBatch(request, selectedOffer)) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, false, extraInputsExtracted);
                return 0;
            }
            batchAccepted = true;
            energyService.extractAEPower(patternPower * batchSize, Actionable.MODULATE, PowerMultiplier.CONFIG);
            recordPushedPattern(accounting);
            return batchSize;
        } catch (RuntimeException e) {
            if (batchAccepted) {
                LOGGER.error(
                        "ECO batch fast path was accepted but post-accept accounting failed; suspending ECO CPU job",
                        e);
                selectedOffer.worker().getFastPathCache().recordException();
                job.suspended = true;
                return -1;
            }
            LOGGER.warn("ECO batch fast path failed, reinjecting inputs and falling back to the slow path", e);
            rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraInputsExtracted);
            selectedOffer.worker().getFastPathCache().recordException();
            return -1;
        }
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
            ECOBatchCraftingHelper.insertAllOrThrow(inventory, extraInputs);
        }
    }

    private int maxBatchSizeFromEnergy(IEnergyService energyService, double patternPower, int requested) {
        if (requested <= 0) {
            return 0;
        }
        if (patternPower <= 0.0D) {
            return requested;
        }
        double requestedPower = patternPower * requested;
        if (energyService.extractAEPower(requestedPower, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                >= requestedPower - 0.01) {
            return requested;
        }
        int low = 0;
        int high = requested - 1;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            double totalPower = patternPower * middle;
            if (energyService.extractAEPower(totalPower, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                    >= totalPower - 0.01) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private int effectiveFastPathTickLimit() {
        if (!NEConfig.isEcoAggressiveFastPathEnabled()) {
            return NEConfig.getEcoFastPathTickLimit();
        }
        int dynamicLimit = aggressiveFastPathCapacity();
        int configuredLimit = NEConfig.getEcoFastPathTickLimit();
        return dynamicLimit > 0 ? Math.min(dynamicLimit, configuredLimit) : configuredLimit;
    }

    private int effectiveFastPathBatchLimit(@Nullable ECOCraftingSystemBlockEntity controller) {
        if (!NEConfig.isEcoAggressiveFastPathEnabled()) {
            return NEConfig.getEcoFastPathBatchLimit();
        }
        int dynamicLimit = controller == null ? aggressiveFastPathCapacity() : controller.getMaxInFlightCrafts();
        return dynamicLimit > 0 ? dynamicLimit : NEConfig.getEcoFastPathBatchLimit();
    }

    private int aggressiveFastPathCapacity() {
        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return 0;
        }
        Set<ECOCraftingSystemBlockEntity> controllers = new HashSet<>();
        int total = 0;
        for (ECOCraftingPatternBusBlockEntity patternBus : grid.getMachines(ECOCraftingPatternBusBlockEntity.class)) {
            ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
            if (controller == null || !controllers.add(controller)) {
                continue;
            }
            total = saturatedAddInt(total, controller.getMaxInFlightCrafts());
        }
        return total;
    }

    private static int saturatedAddInt(int left, int right) {
        long sum = (long) left + Math.max(0, right);
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private int maxCraftsNeededForFinalOutput(ECOExtractedPatternExecution execution) {
        return maxCraftsNeededForFinalOutput(execution.expectedOutputs());
    }

    private int maxCraftsNeededForFinalOutput(List<GenericStack> outputsPerCraft) {
        if (job == null || job.finalOutput == null) {
            return Integer.MAX_VALUE;
        }
        long finalOutputPerCraft = finalOutputAmountPerCraft(outputsPerCraft);
        if (finalOutputPerCraft <= 0) {
            return Integer.MAX_VALUE;
        }
        long inFlightFinalOutput = job.inFlightOutputs.get(job.finalOutput.what());
        return maxCraftsForFinalOutputDemand(job.remainingAmount, inFlightFinalOutput, finalOutputPerCraft);
    }

    static int maxCraftsForFinalOutputDemand(long remainingAmount, long inFlightAmount, long outputAmountPerCraft) {
        if (outputAmountPerCraft <= 0) {
            return Integer.MAX_VALUE;
        }
        long outstanding = remainingAmount - Math.max(0L, inFlightAmount);
        if (outstanding <= 0) {
            return 0;
        }
        long crafts = 1L + (outstanding - 1L) / outputAmountPerCraft;
        return crafts >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) crafts;
    }

    private long finalOutputAmountPerCraft(List<GenericStack> outputsPerCraft) {
        long amount = 0L;
        for (GenericStack output : outputsPerCraft) {
            if (output != null && output.what().matches(job.finalOutput)) {
                amount = saturatedAddLong(amount, output.amount());
            }
        }
        return amount;
    }

    private static long saturatedAddLong(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        long sum = left + right;
        return sum < 0L ? Long.MAX_VALUE : sum;
    }

    private void tickAggressiveSimulatedCrafts(IEnergyService energyService) {
        if (aggressiveSimulatedCrafts.isEmpty()) {
            syncAggressivePoolSlots();
            return;
        }
        syncAggressivePoolSlots();

        double totalNeed = 0.0D;
        for (AggressiveSimulatedCraft work : aggressiveSimulatedCrafts) {
            ECOCraftingSystemBlockEntity controller = work.controller(cpu.getLevel());
            if (controller != null && !work.outputsReady) {
                totalNeed += work.powerNeed(controller);
            }
        }
        double extracted = totalNeed <= 0.0D
                ? 0.0D
                : energyService.extractAEPower(totalNeed, Actionable.MODULATE, PowerMultiplier.CONFIG);
        double powerRatio = totalNeed <= 0.0D ? 0.0D : extracted / totalNeed;

        for (AggressiveSimulatedCraft work : aggressiveSimulatedCrafts) {
            ECOCraftingSystemBlockEntity controller = work.controller(cpu.getLevel());
            if (controller != null && !work.outputsReady) {
                work.tick(controller, powerRatio);
            }
        }

        boolean jobEndedDuringFlush = false;
        var it = aggressiveSimulatedCrafts.iterator();
        while (it.hasNext()) {
            AggressiveSimulatedCraft work = it.next();
            if (!work.outputsReady) {
                continue;
            }
            boolean flushed;
            flushingAggressiveSimulatedOutput = true;
            try {
                flushed = flushAggressiveSimulatedOutputs(work);
            } finally {
                flushingAggressiveSimulatedOutput = false;
            }
            if (flushed) {
                it.remove();
                cpu.markDirty();
            }
            if (recoverAggressiveAfterFlush || job == null) {
                jobEndedDuringFlush = true;
                break;
            }
        }
        if (jobEndedDuringFlush) {
            recoverAggressiveAfterFlush = false;
            recoverAggressiveSimulatedCraftsToInventory();
            this.storeItems();
            setCantStoreItems(!this.inventory.list.isEmpty());
            if (this.inventory.list.isEmpty() && markedForDeletion) {
                cpu.deactivate();
            }
            return;
        }
        syncAggressivePoolSlots();
    }

    private void syncAggressivePoolSlots() {
        Map<AggressivePoolReservation, Integer> totals = new HashMap<>();
        Map<AggressivePoolReservation, List<AggressiveSimulatedCraftSnapshot>> snapshots = new HashMap<>();
        for (AggressiveSimulatedCraft work : aggressiveSimulatedCrafts) {
            AggressivePoolReservation reservation =
                    new AggressivePoolReservation(work.controllerPos, work.reservationOwner);
            totals.merge(reservation, work.occupiedSlots, Integer::sum);
            snapshots.computeIfAbsent(reservation, ignored -> new ArrayList<>()).addAll(work.uiSnapshots());
        }

        Set<AggressivePoolReservation> nextReservations = new HashSet<>(totals.keySet());
        for (AggressivePoolReservation reservation : aggressivePoolReservations) {
            if (!totals.containsKey(reservation)
                    && (!setAggressivePoolSlots(reservation, 0)
                            || !setAggressivePoolTaskSnapshots(reservation, List.of()))) {
                nextReservations.add(reservation);
            }
        }
        for (Map.Entry<AggressivePoolReservation, Integer> entry : totals.entrySet()) {
            AggressivePoolReservation reservation = entry.getKey();
            setAggressivePoolSlots(reservation, Math.max(1, entry.getValue()));
            setAggressivePoolTaskSnapshots(reservation, snapshots.getOrDefault(reservation, List.of()));
        }
        aggressivePoolReservations.clear();
        aggressivePoolReservations.addAll(nextReservations);
    }

    private boolean setAggressivePoolSlots(AggressivePoolReservation reservation, int slots) {
        ECOCraftingSystemBlockEntity controller = reservation.controller(cpu.getLevel());
        if (controller != null) {
            controller.setSimulatedPoolThreadCount(reservation.owner, Math.max(0, slots));
            return true;
        }
        return false;
    }

    private boolean setAggressivePoolTaskSnapshots(
            AggressivePoolReservation reservation, List<AggressiveSimulatedCraftSnapshot> snapshots) {
        ECOCraftingSystemBlockEntity controller = reservation.controller(cpu.getLevel());
        if (controller != null) {
            controller.setSimulatedPoolTaskSnapshots(reservation.owner, snapshots);
            return true;
        }
        return false;
    }

    private boolean flushAggressiveSimulatedOutputs(AggressiveSimulatedCraft work) {
        List<GenericStack> stacks = work.completionStacks();
        List<GenericStack> remainder = new ArrayList<>();
        @Nullable IGrid grid = cpu.getGrid();
        var storage = grid == null ? null : grid.getStorageService().getInventory();
        for (GenericStack stack : stacks) {
            long remaining = stack.amount();
            if (this.job != null) {
                remaining -= insert(stack.what(), remaining, Actionable.MODULATE);
            }
            if (remaining > 0 && storage != null) {
                remaining -= storage.insert(stack.what(), remaining, Actionable.MODULATE, cpu.getActionSource());
            }
            if (remaining > 0) {
                remainder.add(new GenericStack(stack.what(), remaining));
            }
        }
        if (remainder.isEmpty()) {
            return true;
        }
        work.retainOutputRemainder(remainder);
        return false;
    }

    private void recoverAggressiveSimulatedCraftsToInventory() {
        if (aggressiveSimulatedCrafts.isEmpty()) {
            return;
        }
        for (AggressiveSimulatedCraft work : aggressiveSimulatedCrafts) {
            ECOBatchCraftingHelper.insertAllOrThrow(
                    inventory, work.outputsReady ? work.completionStacks() : work.inputStacks);
        }
        aggressiveSimulatedCrafts.clear();
        syncAggressivePoolSlots();
        cpu.markDirty();
    }

    private void recordPushedPattern(ECOExtractedPatternExecution execution, int craftCount) {
        recordPushedPattern(preparePushedPatternAccounting(execution, craftCount));
    }

    private static PendingPatternAccounting preparePushedPatternAccounting(
            ECOExtractedPatternExecution execution, int craftCount) {
        int multiplier = Math.max(1, craftCount);
        return new PendingPatternAccounting(
                ECOBatchCraftingHelper.multiply(execution.expectedOutputs(), multiplier),
                ECOBatchCraftingHelper.multiply(execution.expectedContainerItems(), multiplier));
    }

    private void recordPushedPattern(PendingPatternAccounting accounting) {
        job.addInFlightOutputs(accounting.expectedOutputs(), 1);
        for (var expectedOutput : accounting.expectedOutputs()) {
            job.waitingFor.insert(expectedOutput.what(), expectedOutput.amount(), Actionable.MODULATE);
        }
        postGenericStackKeysChange(accounting.expectedOutputs());
        job.addInFlightOutputs(accounting.expectedContainerItems(), 1);
        for (var expectedContainerItem : accounting.expectedContainerItems()) {
            job.waitingFor.insert(expectedContainerItem.what(), expectedContainerItem.amount(), Actionable.MODULATE);
            job.timeTracker.addMaxItems(
                    expectedContainerItem.amount(), expectedContainerItem.what().getType());
        }
        postGenericStackKeysChange(accounting.expectedContainerItems());

        cpu.markDirty();
    }

    private record PendingPatternAccounting(
            List<GenericStack> expectedOutputs, List<GenericStack> expectedContainerItems) {}

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

    private record AggressivePoolReservation(BlockPos controllerPos, UUID owner) {
        @Nullable private ECOCraftingSystemBlockEntity controller(Level level) {
            if (level == null) {
                return null;
            }
            BlockEntity blockEntity = level.getBlockEntity(controllerPos);
            return blockEntity instanceof ECOCraftingSystemBlockEntity controller ? controller : null;
        }
    }

    private static final class AggressiveSimulatedCraft {
        private final BlockPos controllerPos;
        private final UUID reservationOwner;
        private List<GenericStack> inputStacks;
        private List<GenericStack> outputStacks;
        private List<GenericStack> remainingStacks;
        private final int occupiedSlots;
        private int progress;
        private boolean outputsReady;

        private AggressiveSimulatedCraft(
                BlockPos controllerPos,
                UUID reservationOwner,
                List<GenericStack> inputStacks,
                List<GenericStack> outputStacks,
                List<GenericStack> remainingStacks,
                int occupiedSlots,
                int progress,
                boolean outputsReady) {
            this.controllerPos = controllerPos;
            this.reservationOwner = reservationOwner;
            this.inputStacks = copyStacks(inputStacks);
            this.outputStacks = copyStacks(outputStacks);
            this.remainingStacks = copyStacks(remainingStacks);
            this.occupiedSlots = Math.max(1, occupiedSlots);
            this.progress = Math.max(0, progress);
            this.outputsReady = outputsReady;
        }

        @Nullable private ECOCraftingSystemBlockEntity controller(Level level) {
            if (level == null) {
                return null;
            }
            BlockEntity blockEntity = level.getBlockEntity(controllerPos);
            return blockEntity instanceof ECOCraftingSystemBlockEntity controller ? controller : null;
        }

        private double powerNeed(ECOCraftingSystemBlockEntity controller) {
            return aggressiveSimulatedCraftPowerNeed(
                    controller.getProgressPerTick(), controller.getCraftingPowerMultiplier(), occupiedSlots);
        }

        private void tick(ECOCraftingSystemBlockEntity controller, double powerRatio) {
            if (outputsReady) {
                return;
            }
            double slotScaledTax = controller.getCraftingPowerMultiplier() * (double) occupiedSlots;
            if (slotScaledTax <= 0.0D) {
                return;
            }
            progress += (int) (powerNeed(controller) * powerRatio / slotScaledTax);
            if (progress >= ECOCraftingThread.MAX_PROGRESS) {
                outputsReady = true;
            }
        }

        private List<GenericStack> completionStacks() {
            List<GenericStack> stacks = new ArrayList<>(outputStacks.size() + remainingStacks.size());
            stacks.addAll(outputStacks);
            stacks.addAll(remainingStacks);
            return List.copyOf(stacks);
        }

        private void retainOutputRemainder(List<GenericStack> remainder) {
            outputStacks = copyStacks(remainder);
            remainingStacks = List.of();
            inputStacks = List.of();
            outputsReady = true;
        }

        private List<AggressiveSimulatedCraftSnapshot> uiSnapshots() {
            List<GenericStack> stacks = outputsReady ? completionStacks() : outputStacks;
            List<AggressiveSimulatedCraftSnapshot> snapshots = new ArrayList<>();
            for (GenericStack stack : stacks) {
                if (stack != null && stack.amount() > 0) {
                    snapshots.add(new AggressiveSimulatedCraftSnapshot(
                            controllerPos,
                            reservationOwner,
                            stack,
                            occupiedSlots,
                            outputsReady ? ECOCraftingThread.MAX_PROGRESS : progress,
                            ECOCraftingThread.MAX_PROGRESS,
                            outputsReady));
                }
            }
            return List.copyOf(snapshots);
        }

        private CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("controllerPos", controllerPos.asLong());
            tag.putUUID("reservationOwner", reservationOwner);
            tag.put("inputs", ECOFastPathStacks.writeGenericStacks(inputStacks));
            tag.put("outputs", ECOFastPathStacks.writeGenericStacks(outputStacks));
            tag.put("remaining", ECOFastPathStacks.writeGenericStacks(remainingStacks));
            tag.putInt("occupiedSlots", occupiedSlots);
            tag.putInt("progress", progress);
            tag.putBoolean("outputsReady", outputsReady);
            return tag;
        }

        private static AggressiveSimulatedCraft read(CompoundTag tag) {
            return new AggressiveSimulatedCraft(
                    BlockPos.of(tag.getLong("controllerPos")),
                    tag.hasUUID("reservationOwner") ? tag.getUUID("reservationOwner") : new UUID(0L, 0L),
                    ECOFastPathStacks.readGenericStacks(tag.getList("inputs", Tag.TAG_COMPOUND)),
                    ECOFastPathStacks.readGenericStacks(tag.getList("outputs", Tag.TAG_COMPOUND)),
                    ECOFastPathStacks.readGenericStacks(tag.getList("remaining", Tag.TAG_COMPOUND)),
                    tag.getInt("occupiedSlots"),
                    tag.getInt("progress"),
                    tag.getBoolean("outputsReady"));
        }

        private static List<GenericStack> copyStacks(List<GenericStack> stacks) {
            List<GenericStack> copy = new ArrayList<>();
            for (GenericStack stack : stacks) {
                if (stack != null && stack.amount() > 0) {
                    copy.add(new GenericStack(stack.what(), stack.amount()));
                }
            }
            return List.copyOf(copy);
        }
    }

    static double aggressiveSimulatedCraftPowerNeed(int progressPerTick, int powerMultiplier, int occupiedSlots) {
        int normalizedProgress = Math.max(0, progressPerTick);
        int normalizedPowerMultiplier = Math.max(1, powerMultiplier);
        int normalizedSlots = Math.max(1, occupiedSlots);
        double uncappedNeed = normalizedProgress * (double) normalizedPowerMultiplier * normalizedSlots;
        return Math.min(uncappedNeed, aggressiveSimulatedCraftPowerCap(normalizedProgress, normalizedPowerMultiplier));
    }

    static double aggressiveSimulatedCraftPowerCap(int progressPerTick, int powerMultiplier) {
        int normalizedProgress = Math.max(0, progressPerTick);
        int normalizedPowerMultiplier = Math.max(1, powerMultiplier);
        int normalizedTickLimit = Math.max(1, NEConfig.ecoAggressiveFastPathTickLimit);
        return normalizedProgress * (double) normalizedPowerMultiplier * normalizedTickLimit;
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
        if (what == null || job == null || amount <= 0) return 0;

        // Only accept items we are waiting for.
        var waitingFor = job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0;
        }

        // Make sure we don't insert more than what we are waiting for.
        if (amount > waitingFor) {
            amount = waitingFor;
        }

        if (what.matches(job.finalOutput)) {
            long accepted = job.link.insert(what, amount, type);
            if (type == Actionable.MODULATE) {
                job.timeTracker.decrementItems(amount, what.getType());
                job.waitingFor.extract(what, amount, Actionable.MODULATE);
                job.removeInFlightOutput(what, amount);
                cpu.markDirty();
                postChange(what);
                job.remainingAmount = Math.max(0, job.remainingAmount - amount);
                if (job.remainingAmount <= 0) {
                    finishJob(true);
                }
            }

            return accepted;
        }

        if (type == Actionable.MODULATE) {
            job.timeTracker.decrementItems(amount, what.getType());
            job.waitingFor.extract(what, amount, Actionable.MODULATE);
            job.removeInFlightOutput(what, amount);
            cpu.markDirty();
            postChange(what);
            inventory.insert(what, amount, Actionable.MODULATE);
            // Explicitly notify stored count changed in addition to inventory callback
            postChange(what);
        }

        return amount;
    }

    /**
     * Finish the current job.
     *
     * @param success True if the job is complete, false if it was cancelled.
     */
    private void finishJob(boolean success) {
        if (this.job == null) {
            return;
        }
        UUID craftingJobId = job.link.getCraftingID();

        if (flushingAggressiveSimulatedOutput) {
            recoverAggressiveAfterFlush = true;
        } else {
            recoverAggressiveSimulatedCraftsToInventory();
        }

        Set<AEKey> waitingKeys = collectWaitingKeys();
        Set<AEKey> pendingKeys = collectPendingOutputKeys();
        Set<AEKey> storedKeys = collectStoredKeys();

        if (success) {
            job.link.markDone();
        } else {
            job.link.cancel();
        }

        job.waitingFor.clear();
        postKeysChange(waitingKeys);
        // Notify opened menus of cancelled scheduled tasks.
        postKeysChange(pendingKeys);
        postKeysChange(storedKeys);

        notifyJobOwner(
                job, success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        // Finish job.
        this.job = null;
        restoredLinkRebound = false;
        markedForDeletion = true;
        markStatusDirty();

        if (success) {
            recoverUnfinishedWorkerInputs(craftingJobId);
        }

        // Store all remaining items.
        this.storeItems();
        postKeysChange(storedKeys);
        setCantStoreItems(!this.inventory.list.isEmpty());

        cpu.getCluster().updateGridForChangedCpu(cpu.getCluster());
        if (this.inventory.list.isEmpty()) {
            cpu.deactivate();
        }
    }

    /**
     * Cancel the current job.
     */
    public void cancel() {
        // No job to cancel :P
        if (job == null) return;

        UUID craftingJobId = job.link.getCraftingID();
        markStatusDirty();
        recoverAggressiveSimulatedCraftsToInventory();
        finishJob(false);
        restoredLinkRebound = false;
        recoverInflightWorkerInputs(craftingJobId);
    }

    private void recoverUnfinishedWorkerInputs(UUID craftingJobId) {
        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return;
        }
        var storage = grid.getStorageService().getInventory();
        for (ECOCraftingPatternBusBlockEntity patternBus : grid.getMachines(ECOCraftingPatternBusBlockEntity.class)) {
            patternBus.recoverUnfinishedJobInputsToNetwork(craftingJobId, storage);
        }
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
        if (this.inventory.list.isEmpty()) return;

        var g = cpu.getGrid();
        if (g == null) return;

        var storage = g.getStorageService().getInventory();

        for (var entry : this.inventory.list) {
            var inserted =
                    storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, cpu.getActionSource());

            // The network was unable to receive all of the items, i.e. no or not enough
            // storage space left
            entry.setValue(entry.getLongValue() - inserted);
            this.postChange(entry.getKey());
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
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
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
        } catch (RuntimeException | Error e) {
            batchingStatusChanges = false;
            batchedStatusChanges.clear();
            batchedAnyStatusChange = false;
            batchedFullStatusChange = false;
            throw e;
        }
    }

    static final class FastPathBatchBudget {
        private int remaining;

        FastPathBatchBudget(int limit) {
            this.remaining = Math.max(0, limit);
        }

        int remaining() {
            return remaining;
        }

        void consume(int amount) {
            if (amount < 0 || amount > remaining) {
                throw new IllegalArgumentException("Invalid fast-path batch budget consumption: " + amount);
            }
            remaining -= amount;
        }
    }

    private record ProviderSelection(List<ICraftingProvider> all, List<ECOCraftingPatternBusBlockEntity> batchBuses) {}

    private record ExtractedPatternAttempt(
            KeyCounter[] craftingContainer, ECOExtractedPatternExecution execution, double patternPower) {}

    private record PushResult(int craftCount, boolean jobFinished) {
        private static PushResult pushed(int craftCount) {
            return new PushResult(craftCount, false);
        }

        private static PushResult jobFinished(int craftCount) {
            return new PushResult(craftCount, true);
        }

        private static PushResult notPushed() {
            return new PushResult(0, false);
        }

        private boolean pushed() {
            return craftCount > 0;
        }
    }

    private enum DispatchTaskResult {
        CONTINUE_TASK,
        NEXT_TASK,
        STOP_PASS,
        JOB_FINISHED
    }

    private static final class DispatchPassState {
        private boolean allowUnfinishedDependencies;
        private boolean fallbackPassUsed;
        private boolean sawUnfinishedDependencyBlock;

        private void beginPass() {
            sawUnfinishedDependencyBlock = false;
        }

        private boolean allowUnfinishedDependencies() {
            return allowUnfinishedDependencies;
        }

        private void markUnfinishedDependencyBlocked() {
            sawUnfinishedDependencyBlock = true;
        }

        private boolean shouldRunFallbackPass(CraftingExecutionProgress executionProgress, int pushedBeforePass) {
            return executionProgress.pushedPatterns() <= pushedBeforePass
                    && !fallbackPassUsed
                    && sawUnfinishedDependencyBlock
                    && executionProgress.canPushMore()
                    && executionProgress.canPushAnyPath();
        }

        private void startFallbackPass() {
            allowUnfinishedDependencies = true;
            fallbackPassUsed = true;
        }
    }

    private static final class CraftingExecutionProgress {
        private final int slowPatternBudget;
        private final int totalPatternBudget;
        private final FastPathBatchBudget batchBudget;
        private int pushedPatterns;
        private int slowPushedPatterns;

        private CraftingExecutionProgress(
                int slowPatternBudget, int totalPatternBudget, FastPathBatchBudget batchBudget) {
            this.slowPatternBudget = slowPatternBudget;
            this.totalPatternBudget = totalPatternBudget;
            this.batchBudget = batchBudget;
        }

        private boolean canPushMore() {
            return pushedPatterns < totalPatternBudget;
        }

        private boolean canPushSlowPath() {
            return slowPushedPatterns < slowPatternBudget;
        }

        private boolean canPushAnyPath() {
            return batchBudget.remaining() > 0 || canPushSlowPath();
        }

        private int totalBudgetRemaining() {
            return totalPatternBudget - pushedPatterns;
        }

        private int batchBudgetRemaining() {
            return batchBudget.remaining();
        }

        private int pushedPatterns() {
            return pushedPatterns;
        }

        private void recordBatchPush(int craftCount) {
            batchBudget.consume(craftCount);
            pushedPatterns += craftCount;
        }

        private void recordSlowPush() {
            pushedPatterns++;
            slowPushedPatterns++;
        }
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

    private Set<AEKey> collectStoredKeys() {
        Set<AEKey> keys = new HashSet<>();
        for (var entry : this.inventory.list) {
            keys.add(entry.getKey());
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

    @Nullable public UUID getCraftingJobId() {
        return this.job == null ? null : this.job.link.getCraftingID();
    }

    @Nullable public GenericStack getFinalJobOutput() {
        return this.job != null ? this.job.finalOutput : null;
    }

    public long getRemainingJobOutputAmount() {
        return this.job == null ? 0L : Math.max(0L, this.job.remainingAmount);
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
        aggressiveSimulatedCrafts.clear();
        ListTag aggressiveWorks = data.getList("aggressiveSimulatedCrafts", Tag.TAG_COMPOUND);
        for (int i = 0; i < aggressiveWorks.size(); i++) {
            aggressiveSimulatedCrafts.add(AggressiveSimulatedCraft.read(aggressiveWorks.getCompound(i)));
        }
        aggressivePoolReservations.clear();
        if (data.contains("job")) {
            this.job = new ExecutingCraftingJob(data.getCompound("job"), registries, this::postChange, this);
            markStatusDirty();
            if (this.job.finalOutput == null) {
                LOGGER.warn(
                        "ECO CPU restored with null finalOutput (job NBT may be corrupted). "
                                + "Dropping job and marking CPU for cleanup. cpu={}",
                        cpu.getName());
                this.job = null;
                markedForDeletion = true;
            } else {
                // Mark as restored from NBT — tickCraftingLogic will handle rebind proactively
                this.restoredFromNbt = true;
                this.restoreRebindAttempted = false;
                this.restoreRebindSuccessful = false;
                this.restoredLinkRebound = false;
                this.restoredCancelGraceTicks = RESTORED_CANCEL_GRACE_INITIAL;
                LOGGER.info(
                        "ECO CPU job restored from NBT. cpu={} jobId={} finalOutput={} remainingAmount={}",
                        cpu.getName(),
                        this.job.link.getCraftingID(),
                        this.job.finalOutput != null
                                ? this.job.finalOutput.what().getClass().getSimpleName()
                                : "null",
                        this.job.remainingAmount);
            }
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.put("inventory", this.inventory.writeToNBT());
        if (this.job != null) {
            data.put("job", this.job.writeToNBT(registries));
        }
        if (!aggressiveSimulatedCrafts.isEmpty()) {
            ListTag aggressiveWorks = new ListTag();
            for (AggressiveSimulatedCraft work : aggressiveSimulatedCrafts) {
                aggressiveWorks.add(work.write());
            }
            data.put("aggressiveSimulatedCrafts", aggressiveWorks);
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

    public boolean isJobUserPaused() {
        return job != null && job.userPaused;
    }

    public void setJobUserPaused(boolean paused) {
        if (job != null && job.userPaused != paused) {
            job.userPaused = paused;
            if (cpu != null) {
                cpu.markDirty();
            }
            postChange(null);
        }
    }

    public void toggleJobUserPaused() {
        setJobUserPaused(!isJobUserPaused());
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
            connectedPlayer.connection.send(
                    message.toPacket(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        }
    }

    public void markForDeletion() {
        this.markedForDeletion = true;
    }
}
