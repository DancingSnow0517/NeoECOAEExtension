package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingCapacity;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingModuleCell;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEBlockEntityUIHolder;
import cn.dancingsnow.neoecoae.multiblock.BuildPreviewState;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingSystemBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingSystemBlockEntity>
        implements IGridTickable, INEMultiblockBuildHost, NEBlockEntityUIHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final boolean DEBUG_THREAD_COUNT = Boolean.getBoolean("neoecoae.debugEcoCraftingThreadCount");
    private static final Comparator<NECraftingModuleCell> MODULE_CELL_ORDER = Comparator.comparingInt(
                    NECraftingModuleCell::column)
            .thenComparingInt(cell -> cell.row().ordinal())
            .thenComparingInt(NECraftingModuleCell::tier);

    /**
     * Internal coolant cache maximum: the crafting controller's own cooling
     * buffer, <em>not</em> the fluid hatch tank capacity.
     * This value is part of the current Forge 1.20.1 controller balance.
     */
    public static final int MAX_COOLANT = 1_000_000;

    private static final int COOLANT_PER_CRAFT = 5;
    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L * 3L;

    @Getter
    private final IECOTier tier;

    @Getter
    private boolean overclocked = false;

    @Getter
    private boolean activeCooling = false;

    @Getter
    private boolean autoClearCoolingWaste = false;

    @Getter
    private int coolant = 0;

    @Getter
    private int coolantMaxOverclock = -1;

    @Nullable private ResourceLocation coolantFluidId;

    private int patternBusCount, parallelCount, workerCount = 0;

    private int runningThreadCount = 0;

    private int simulatedPoolThreadCount = 0;

    private final Map<UUID, Integer> simulatedPoolThreadReservations = new HashMap<>();

    private int threadCount = 0;

    private int threadCountPerWorker = 0;

    private int overlockTimes = 0;
    private boolean structureStatsDirty = true;
    /** Shared preview/build state, delegates NBT sync to {@link BuildPreviewState}. */
    private final BuildPreviewState buildPreview = new BuildPreviewState();

    private long uiRevision = 0L;
    private long lastCoolantConsumeDirtyTick = Long.MIN_VALUE;
    private long lastThreadCountValidationTick = Long.MIN_VALUE;
    private long performanceWindowStartTick = Long.MIN_VALUE;
    private long performanceWindowNanos = 0L;
    private long performanceAverageNanos = 0L;

    public ECOCraftingSystemBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
        getMainNode().addService(IGridTickable.class, this);
    }

    // NBT persistence
    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("overclocked", overclocked);
        tag.putBoolean("activeCooling", activeCooling);
        tag.putBoolean("autoClearCoolingWaste", autoClearCoolingWaste);
        tag.putInt("coolant", coolant);
        tag.putInt("coolantMaxOverclock", coolantMaxOverclock);
        if (coolantFluidId != null) {
            tag.putString("coolantFluid", coolantFluidId.toString());
        }
        tag.putInt("selectedBuildLength", getSelectedBuildLength());
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        overclocked = tag.getBoolean("overclocked");
        activeCooling = tag.getBoolean("activeCooling");
        autoClearCoolingWaste = tag.getBoolean("autoClearCoolingWaste");
        coolant = Mth.clamp(tag.getInt("coolant"), 0, MAX_COOLANT);
        coolantMaxOverclock = tag.getInt("coolantMaxOverclock");
        if (!tag.contains("coolantMaxOverclock")) coolantMaxOverclock = -1;
        coolantFluidId = readCoolantFluidId(tag);
        buildPreview.selectedBuildLength = Math.max(1, tag.getInt("selectedBuildLength"));
        buildPreview.buildInProgress = false;
        buildPreview.resetPreview(BuildPreviewState.DEFAULT_STATUS_KEY);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(64);
    }

    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().executeIfPossible(() -> {
                setChanged();
                markStructureStatsDirty();
                ensureCraftingStatsCurrent();
            });
        }
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (updateExposed) {
            markStructureStatsDirty();
            ensureCraftingStatsCurrent();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 10, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        long startNanos = System.nanoTime();
        try {
            return doTickingRequest(node, ticksSinceLastCall);
        } finally {
            recordPerformanceSample(System.nanoTime() - startNanos);
        }
    }

    private TickRateModulation doTickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!activeCooling) {
            return TickRateModulation.IDLE;
        }
        CoolingRecipe recipe = getCoolingRecipe();
        if (recipe == null) {
            return TickRateModulation.IDLE;
        }
        if (!canRefillWith(recipe)) {
            return TickRateModulation.IDLE;
        }

        int targetCoolant = getTargetCoolantBuffer();
        if (targetCoolant <= coolant) {
            return TickRateModulation.IDLE;
        }

        int refillAmount = refillCoolant(recipe, targetCoolant - coolant);
        if (refillAmount <= 0) {
            return TickRateModulation.IDLE;
        }
        return coolant < targetCoolant ? TickRateModulation.URGENT : TickRateModulation.IDLE;
    }

    void recordPerformanceSample(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            return;
        }

        long currentTick = TickHandler.instance().getCurrentTick();
        if (performanceWindowStartTick == Long.MIN_VALUE) {
            performanceWindowStartTick = currentTick;
        }

        performanceWindowNanos += elapsedNanos;
        long elapsedTicks = currentTick - performanceWindowStartTick;
        if (elapsedTicks >= PERFORMANCE_SAMPLE_WINDOW_TICKS) {
            long nextAverageNanos = performanceWindowNanos / Math.max(1L, elapsedTicks);
            performanceWindowStartTick = currentTick;
            performanceWindowNanos = 0L;
            if (performanceAverageNanos != nextAverageNanos) {
                performanceAverageNanos = nextAverageNanos;
                markUiStateDirty();
            }
        }
    }

    /**
     * Marks the cached crafting structure stats (worker/thread/parallel counts)
     * as stale and increments the UI revision to trigger a menu state resync.
     * Call this when the multiblock cluster changes or workers are added/removed.
     */
    public void markStructureStatsDirty() {
        structureStatsDirty = true;
        markUiStateDirty();
    }

    /** Returns a monotonically increasing revision for UI state duplicate suppression. */
    public long getUiRevision() {
        return uiRevision;
    }

    /** Increments the UI revision so the next menu tick will push a fresh state. */
    private void markUiStateDirty() {
        uiRevision++;
    }

    private void ensureCraftingStatsCurrent() {
        if (!structureStatsDirty) {
            return;
        }
        updateCount();
        updateThreadCount();
        updateOverlockTimes();
        structureStatsDirty = false;
    }

    private void updateThreadCount() {
        if (cluster != null && parallelCount > 0) {
            int perCore = tier.getCrafterParallel();
            if (overclocked) {
                perCore += tier.getOverclockedCrafterParallel();
                threadCountPerWorker = 32 * getTier().getOverclockedCrafterQueueMultiply();
            } else {
                threadCountPerWorker = 32;
            }
            threadCount = parallelCount * perCore;
            recalculateRunningThreadCountFromWorkers();
        } else {
            threadCount = 0;
            threadCountPerWorker = 0;
            runningThreadCount = 0;
            simulatedPoolThreadCount = 0;
            simulatedPoolThreadReservations.clear();
        }
    }

    public void recalculateRunningThreadCountFromWorkers() {
        if (cluster == null) {
            runningThreadCount = 0;
            return;
        }

        runningThreadCount = getWorkerRunningThreadCount() + simulatedPoolThreadCount;
    }

    private void updateCount() {
        if (cluster != null) {
            parallelCount = cluster.getParallelCores().size();
            patternBusCount = cluster.getPatternBuses().size();
            workerCount = cluster.getWorkers().size();
        } else {
            parallelCount = 0;
            patternBusCount = 0;
            workerCount = 0;
        }
    }

    private void updateOverlockTimes() {
        int overflow = Math.max(0, threadCount - threadCountPerWorker * workerCount);
        if (overflow <= 0 || threadCount <= 0) {
            overlockTimes = 0;
            return;
        }
        float radio = (float) threadCount / overflow;
        overlockTimes = net.minecraft.util.Mth.clamp(Math.round(radio / 0.05f), 0, 9);
    }

    public boolean tryConsumeCoolant(int amount, int requiredOverclock) {
        if (!activeCooling) {
            return true;
        }
        if (amount <= 0) {
            return true;
        }
        if (coolant < amount) {
            return false;
        }
        if (requiredOverclock > 0 && coolantMaxOverclock < requiredOverclock) {
            return false;
        }
        coolant -= amount;
        if (coolant <= 0) {
            coolant = 0;
            coolantMaxOverclock = -1;
            coolantFluidId = null;
        }
        markCoolantConsumed();
        return true;
    }

    public int getCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (!activeCooling || requestedCrafts <= 0) {
            return Math.max(0, requestedCrafts);
        }
        if (coolantPerCraft <= 0) {
            return Math.max(0, requestedCrafts);
        }
        if (requiredOverclock > 0 && coolantMaxOverclock < requiredOverclock) {
            return 0;
        }
        return Math.min(requestedCrafts, coolant / coolantPerCraft);
    }

    private void markCoolantConsumed() {
        long currentTick = TickHandler.instance().getCurrentTick();
        if (lastCoolantConsumeDirtyTick == currentTick) {
            return;
        }
        lastCoolantConsumeDirtyTick = currentTick;
        setChanged();
        markUiStateDirty();
    }

    public int getEffectiveOverclockTimes() {
        ensureCraftingStatsCurrent();
        if (!overclocked) {
            return 0;
        }
        if (!activeCooling) {
            return overlockTimes;
        }
        int coolingMaxOverclock = getCurrentCoolingMaxOverclock();
        if (coolingMaxOverclock < 0) {
            return 0;
        }
        return Math.min(overlockTimes, coolingMaxOverclock);
    }

    public int getDisplayedCoolingMaxOverclock() {
        return getCurrentCoolingMaxOverclock();
    }

    public void clearCoolant() {
        coolant = 0;
        coolantMaxOverclock = -1;
        coolantFluidId = null;
        setChanged();
        markUiStateDirty();
    }

    public void toggleOverclocked() {
        overclocked = !overclocked;
        markStructureStatsDirty();
        ensureCraftingStatsCurrent();
        setChanged();
    }

    public void toggleActiveCooling() {
        activeCooling = !activeCooling;
        setChanged();
        markUiStateDirty();
    }

    public void toggleAutoClearCoolingWaste() {
        autoClearCoolingWaste = !autoClearCoolingWaste;
        setChanged();
        markUiStateDirty();
    }

    public int getOverflowThreads() {
        ensureCraftingStatsCurrent();
        return Math.max(0, threadCount - getAvailableThreads());
    }

    public int getAvailableThreads() {
        ensureCraftingStatsCurrent();
        return threadCountPerWorker * workerCount;
    }

    public int getRunningThreadCount() {
        ensureCraftingStatsCurrent();
        return runningThreadCount;
    }

    public int getLiveRunningThreadCount() {
        return getRunningThreadCount();
    }

    public boolean isRunning() {
        return getRunningThreadCount() > 0;
    }

    public int getCurrentBatchSlots() {
        ensureCraftingStatsCurrent();
        return ECOCraftingCapacity.availableCraftSlots(getMaxInFlightCrafts(), runningThreadCount);
    }

    /**
     * Maximum pattern executions that may be in flight at once.
     * The formed structure length is the number of worker segments, while the
     * parallel cores may impose a lower thread limit.
     */
    public int getMaxInFlightCrafts() {
        ensureCraftingStatsCurrent();
        return ECOCraftingCapacity.maxInFlightCrafts(threadCount, getStructureBuildLength(), threadCountPerWorker);
    }

    public int getStructureBuildLength() {
        ensureCraftingStatsCurrent();
        return workerCount;
    }

    public int getProgressPerTick() {
        return Math.min(10 + getEffectiveOverclockTimes() * 10, 100);
    }

    public int getTheoreticalCraftTicks() {
        int progressPerTick = getProgressPerTick();
        if (progressPerTick <= 0) {
            return 0;
        }
        return Mth.ceil((float) cn.dancingsnow.neoecoae.api.me.ECOCraftingThread.MAX_PROGRESS / progressPerTick);
    }

    public int getCraftingPowerMultiplier() {
        if (overclocked && !activeCooling) {
            return tier.getOverclockedCrafterPowerMultiply();
        }
        return 1;
    }

    public long getCurrentEnergyPerTick() {
        return (long) getRunningThreadCount() * getProgressPerTick() * getCraftingPowerMultiplier();
    }

    public double getEnergyMultiplier() {
        return getCraftingPowerMultiplier();
    }

    public double getTimeMultiplier() {
        ensureCraftingStatsCurrent();
        int baseParallel = parallelCount * tier.getCrafterParallel();
        if (baseParallel <= 0 || threadCount <= 0) {
            return 1.0D;
        }
        double baseTicks = cn.dancingsnow.neoecoae.api.me.ECOCraftingThread.MAX_PROGRESS / 10.0D;
        return (getTheoreticalCraftTicks() * (double) baseParallel) / (baseTicks * (double) threadCount);
    }

    public ECOCraftingWorkerBlockEntity.ThreadProgressSummary getThreadProgressSummary() {
        if (cluster == null) {
            return new ECOCraftingWorkerBlockEntity.ThreadProgressSummary(0, 0, 0, 0);
        }
        int busyThreadCount = 0;
        int occupiedSlots = 0;
        int maxProgress = 0;
        long weightedProgress = 0L;
        for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
            ECOCraftingWorkerBlockEntity.ThreadProgressSummary summary = worker.getThreadProgressSummary();
            busyThreadCount += summary.busyThreadCount();
            occupiedSlots += summary.occupiedSlots();
            maxProgress = Math.max(maxProgress, summary.maxProgress());
            weightedProgress += (long) summary.averageProgress() * summary.occupiedSlots();
        }
        int averageProgress = occupiedSlots <= 0 ? 0 : Math.round((float) weightedProgress / occupiedSlots);
        return new ECOCraftingWorkerBlockEntity.ThreadProgressSummary(
                busyThreadCount, occupiedSlots, maxProgress, averageProgress);
    }

    public int getThreadCount() {
        ensureCraftingStatsCurrent();
        return threadCount;
    }

    public int getThreadCountPerWorker() {
        ensureCraftingStatsCurrent();
        return threadCountPerWorker;
    }

    public int getOverlockTimes() {
        ensureCraftingStatsCurrent();
        return overlockTimes;
    }

    public void onWorkerThreadCountChanged(int delta) {
        int previous = runningThreadCount;
        runningThreadCount += delta;
        if (runningThreadCount < 0) {
            LOGGER.warn(
                    "ECO controller runningThreadCount underflow: controller={} delta={} previous={} correctedToZero=true",
                    getBlockPos(),
                    delta,
                    previous);
            runningThreadCount = 0;
        }
        validateRunningThreadCount();
        markUiStateDirty();
    }

    public void setSimulatedPoolThreadCount(UUID owner, int count) {
        int normalizedCount = Math.max(0, count);
        int previous = simulatedPoolThreadReservations.getOrDefault(owner, 0);
        if (previous == normalizedCount) {
            return;
        }
        if (normalizedCount == 0) {
            simulatedPoolThreadReservations.remove(owner);
        } else {
            simulatedPoolThreadReservations.put(owner, normalizedCount);
        }
        simulatedPoolThreadCount += normalizedCount - previous;
        if (simulatedPoolThreadCount < 0) {
            LOGGER.warn(
                    "ECO controller simulatedPoolThreadCount underflow: controller={} owner={} previous={} next={} correctedToZero=true",
                    getBlockPos(),
                    owner,
                    previous,
                    normalizedCount);
            simulatedPoolThreadCount = 0;
        }
        recalculateRunningThreadCountFromWorkers();
        markUiStateDirty();
    }

    private void validateRunningThreadCount() {
        if (!DEBUG_THREAD_COUNT || cluster == null) {
            return;
        }
        long currentTick = TickHandler.instance().getCurrentTick();
        if (currentTick == lastThreadCountValidationTick) {
            return;
        }
        lastThreadCountValidationTick = currentTick;
        int actual = getWorkerRunningThreadCount() + simulatedPoolThreadCount;
        if (actual != runningThreadCount) {
            LOGGER.warn(
                    "ECO controller runningThreadCount mismatch: controller={} cached={} actual={} corrected=true",
                    getBlockPos(),
                    runningThreadCount,
                    actual);
            runningThreadCount = actual;
        }
    }

    private int getWorkerRunningThreadCount() {
        if (cluster == null) {
            return 0;
        }
        return cluster.getWorkers().stream()
                .mapToInt(ECOCraftingWorkerBlockEntity::getRunningThreads)
                .sum();
    }

    public int getPatternBusCount() {
        ensureCraftingStatsCurrent();
        return patternBusCount;
    }

    public int getParallelCount() {
        ensureCraftingStatsCurrent();
        return parallelCount;
    }

    public int getWorkerCount() {
        ensureCraftingStatsCurrent();
        return workerCount;
    }

    // getPreviewStatusComponent() is provided by INEMultiblockBuildHost default

    // INEMultiblockBuildHost implementation
    @Override
    public BlockPos getHostPos() {
        return worldPosition;
    }

    @Override
    public BlockState getHostBlockState() {
        return getBlockState();
    }

    @Override
    public boolean isFormed() {
        return formed;
    }

    public int getPreviewMissingBlocks() {
        return buildPreview.previewMissingBlocks;
    }

    public int getPreviewConflictBlocks() {
        return buildPreview.previewConflictBlocks;
    }

    public int getPreviewReusedBlocks() {
        return buildPreview.previewReusedBlocks;
    }

    public int getPreviewRequiredItems() {
        return buildPreview.previewRequiredItems;
    }

    /**
     * Creates a snapshot of current crafting stats for S2C UI sync.
     * <p>
     * On the server side this reads live cluster data. No business
     * state is modified - this is a pure read-only snapshot.
     * </p>
     */
    public NECraftingUiState createCraftingUiState() {
        // Ensure stats are current before reading ANY field;
        // otherwise threadCount could be stale while getAvailableThreads()
        // triggers a recalculation, making effParallel inconsistent.
        ensureCraftingStatsCurrent();

        int totalParallelism = threadCount;
        int availThreads = getAvailableThreads();
        int effParallel = Math.min(totalParallelism, availThreads);
        int maxRecipeSlots = Math.max(0, availThreads);
        int occupiedRecipeSlots = Math.min(maxRecipeSlots, Math.max(0, runningThreadCount));
        int batchParallel = Math.max(0, effParallel);
        List<NECraftingRecipeUiEntry> recipeEntries = new ArrayList<>();

        // Collect active craft outputs from each worker
        List<ItemStack> craftOutputs = new ArrayList<>();
        // Collect tier level (1/2/3 = L4/L6/L9) for each parallel core
        List<Integer> coreTiers = new ArrayList<>();
        List<NECraftingModuleCell> moduleCells = new ArrayList<>();
        if (cluster != null) {
            int maxWorkerColumn = -1;
            List<WorkerUiEntry> workerEntries = new ArrayList<>();
            for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
                appendWorkerRecipeEntries(recipeEntries, worker);
                int column = moduleColumn(worker.getBlockPos());
                if (column >= 0) {
                    maxWorkerColumn = Math.max(maxWorkerColumn, column);
                    moduleCells.add(new NECraftingModuleCell(
                            column, NECraftingModuleCell.Row.WORKER, tier.getTier(), worker.getBlockPos()));
                }
                workerEntries.add(
                        new WorkerUiEntry(column, worker.getActiveCraftOutput().copy()));
            }
            if (maxWorkerColumn >= 0) {
                for (int i = 0; i <= maxWorkerColumn; i++) {
                    craftOutputs.add(ItemStack.EMPTY);
                }
            }
            workerEntries.sort(Comparator.comparingInt(WorkerUiEntry::column));
            for (WorkerUiEntry worker : workerEntries) {
                if (worker.column() >= 0 && worker.column() < craftOutputs.size()) {
                    craftOutputs.set(worker.column(), worker.output());
                } else {
                    craftOutputs.add(worker.output());
                }
            }

            for (ECOCraftingParallelCoreBlockEntity core : cluster.getParallelCores()) {
                int coreTier = core.getTier().getTier();
                coreTiers.add(coreTier);
                NECraftingModuleCell.Row row = moduleParallelRow(core.getBlockPos());
                int column = moduleColumn(core.getBlockPos());
                if (row != null && column >= 0) {
                    moduleCells.add(new NECraftingModuleCell(column, row, coreTier, core.getBlockPos()));
                }
            }
            coreTiers.sort(Integer::compareTo);
            moduleCells = normalizeModuleCells(moduleCells);
        }

        return new NECraftingUiState(
                worldPosition,
                formed,
                cluster != null && getMainNode().isActive(),
                workerCount,
                parallelCount,
                patternBusCount,
                totalParallelism,
                runningThreadCount,
                isOverclocked(),
                isActiveCooling(),
                isAutoClearCoolingWaste(),
                getSelectedBuildLength(),
                isBuildInProgress(),
                getPreviewMissingBlocks(),
                getPreviewConflictBlocks(),
                getPreviewReusedBlocks(),
                getPreviewRequiredItems(),
                buildPreview.previewStatusKey,
                buildPreview.previewStatusArg1,
                buildPreview.previewStatusArg2,
                getCurrentEnergyPerTick(),
                coolant,
                MAX_COOLANT,
                coolantFluidId == null ? "" : coolantFluidId.toString(),
                coolantMaxOverclock,
                availThreads,
                effParallel,
                maxRecipeSlots,
                occupiedRecipeSlots,
                batchParallel,
                performanceAverageNanos,
                recipeEntries,
                craftOutputs,
                coreTiers,
                moduleCells);
    }

    private static void appendWorkerRecipeEntries(
            List<NECraftingRecipeUiEntry> entries, ECOCraftingWorkerBlockEntity worker) {
        Map<WorkerTaskKey, WorkerTaskAggregate> aggregates = new LinkedHashMap<>();
        for (var thread : worker.getThreadSnapshots()) {
            ItemStack output = thread.outputItem();
            if (output.isEmpty()) {
                continue;
            }
            WorkerTaskKey key = new WorkerTaskKey(thread.craftingJobId(), output);
            aggregates
                    .computeIfAbsent(key, ignored -> new WorkerTaskAggregate(output.copyWithCount(1)))
                    .add(thread);
        }

        int aggregateIndex = 0;
        for (WorkerTaskAggregate aggregate : aggregates.values()) {
            entries.add(aggregate.toEntry(worker.getBlockPos(), aggregateIndex++));
        }
    }

    private int moduleColumn(BlockPos pos) {
        Direction right = moduleRightDirection();
        BlockPos workerStart = worldPosition.relative(right, 2);
        int dx = pos.getX() - workerStart.getX();
        int dy = pos.getY() - workerStart.getY();
        int dz = pos.getZ() - workerStart.getZ();
        int distance = dx * right.getStepX() + dy * right.getStepY() + dz * right.getStepZ();
        return distance;
    }

    @Nullable private NECraftingModuleCell.Row moduleParallelRow(BlockPos pos) {
        Direction top = moduleTopDirection();
        Direction down = top.getOpposite();
        int dx = pos.getX() - worldPosition.getX();
        int dy = pos.getY() - worldPosition.getY();
        int dz = pos.getZ() - worldPosition.getZ();
        if (dx * top.getStepX() + dy * top.getStepY() + dz * top.getStepZ() == 1) {
            return NECraftingModuleCell.Row.UPPER_PARALLEL;
        }
        if (dx * down.getStepX() + dy * down.getStepY() + dz * down.getStepZ() == 1) {
            return NECraftingModuleCell.Row.LOWER_PARALLEL;
        }
        return null;
    }

    private Direction moduleRightDirection() {
        IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
        Direction left = strategy.getSide(getBlockState(), RelativeSide.RIGHT);
        Direction right = left.getOpposite();
        if (cluster != null && cluster.isMirrored()) {
            right = right.getOpposite();
        } else if (getBlockState().hasProperty(NEBlock.MIRRORED)
                && getBlockState().getValue(NEBlock.MIRRORED)) {
            right = right.getOpposite();
        }
        return right;
    }

    private Direction moduleTopDirection() {
        return OrientationStrategies.horizontalFacing().getSide(getBlockState(), RelativeSide.TOP);
    }

    private static List<NECraftingModuleCell> normalizeModuleCells(List<NECraftingModuleCell> cells) {
        if (cells.isEmpty()) {
            return List.of();
        }
        List<NECraftingModuleCell> sorted = new ArrayList<>(cells);
        sorted.sort(MODULE_CELL_ORDER);
        List<NECraftingModuleCell> normalized = new ArrayList<>(sorted.size());
        for (NECraftingModuleCell cell : sorted) {
            int lastIndex = normalized.size() - 1;
            if (lastIndex >= 0) {
                NECraftingModuleCell last = normalized.get(lastIndex);
                if (last.column() == cell.column() && last.row() == cell.row()) {
                    if (cell.tier() > last.tier()) {
                        normalized.set(lastIndex, cell);
                    }
                    continue;
                }
            }
            normalized.add(cell);
        }
        return normalized;
    }

    private record WorkerUiEntry(int column, ItemStack output) {}

    private record WorkerTaskKey(UUID craftingJobId, ItemStack output) {
        private WorkerTaskKey {
            output = output.copyWithCount(1);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WorkerTaskKey that)) {
                return false;
            }
            if (craftingJobId != null || that.craftingJobId != null) {
                return java.util.Objects.equals(craftingJobId, that.craftingJobId)
                        && ItemStack.isSameItemSameTags(output, that.output);
            }
            return ItemStack.isSameItemSameTags(output, that.output);
        }

        @Override
        public int hashCode() {
            int result = craftingJobId != null ? craftingJobId.hashCode() : 0;
            result = 31 * result + output.getItem().hashCode();
            result = 31 * result + (output.hasTag() ? output.getTag().hashCode() : 0);
            return result;
        }
    }

    private static final class WorkerTaskAggregate {
        private final ItemStack output;
        private UUID craftingJobId;
        private long outputAmount;
        private long craftCount;
        private long weightedTotalProgress;
        private long weightedRemainingProgress;
        private boolean waitingOutput = true;

        private WorkerTaskAggregate(ItemStack output) {
            this.output = output;
        }

        private void add(cn.dancingsnow.neoecoae.api.me.ECOCraftingThread.Snapshot thread) {
            if (craftingJobId == null && thread.craftingJobId() != null) {
                craftingJobId = thread.craftingJobId();
            }
            int slots = Math.max(1, thread.occupiedThreadSlots());
            outputAmount += Math.max(1L, thread.outputItem().getCount());
            craftCount += slots;
            weightedTotalProgress += (long) Math.max(1, thread.maxProgress()) * slots;
            weightedRemainingProgress += (long) Math.max(0, thread.maxProgress() - thread.progress()) * slots;
            waitingOutput &= thread.progress() >= thread.maxProgress();
        }

        private NECraftingRecipeUiEntry toEntry(BlockPos workerPos, int aggregateIndex) {
            return new NECraftingRecipeUiEntry(
                    "worker:" + workerPos.asLong() + ":" + aggregateIndex + ":"
                            + (craftingJobId != null ? craftingJobId : "local"),
                    output.copyWithCount(1),
                    Math.max(1L, outputAmount),
                    Math.max(1L, craftCount),
                    Math.max(1L, weightedTotalProgress),
                    Math.max(0L, weightedRemainingProgress),
                    waitingOutput
                            ? NECraftingRecipeUiEntry.Status.WAITING_OUTPUT
                            : NECraftingRecipeUiEntry.Status.RUNNING);
        }
    }

    @Override
    public ModularUI createUI(net.minecraft.world.entity.player.Player player) {
        return NELDLibUis.createCraftingController(this, player);
    }

    @Nullable private CoolingRecipe getCoolingRecipe() {
        if (cluster == null
                || cluster.getInputHatch() == null
                || cluster.getOutputHatch() == null
                || getLevel() == null) {
            return null;
        }
        FluidTank inputHatch = cluster.getInputHatch().tank;
        if (inputHatch.getFluidAmount() <= 0) {
            return null;
        }
        FluidTank outputHatch = cluster.getOutputHatch().tank;
        return getLevel()
                .getRecipeManager()
                .getRecipeFor(
                        NERecipeTypes.COOLING.get(),
                        new CoolingRecipe.Input(inputHatch.getFluid(), outputHatch.getFluid()),
                        getLevel())
                .orElse(null);
    }

    private boolean canRefillWith(CoolingRecipe recipe) {
        if (coolant <= 0 || coolantMaxOverclock < 0 || coolantFluidId == null) {
            return true;
        }
        ResourceLocation inputFluidId = currentCoolingInputFluidId();
        return coolantMaxOverclock == recipe.maxOverclock() && coolantFluidId.equals(inputFluidId);
    }

    private int getCurrentCoolingMaxOverclock() {
        if (coolant > 0 && coolantMaxOverclock >= 0) {
            return coolantMaxOverclock;
        }
        CoolingRecipe recipe = getCoolingRecipe();
        return recipe == null ? -1 : recipe.maxOverclock();
    }

    private int getTargetCoolantBuffer() {
        int requiredPerTick = getAvailableThreads() * COOLANT_PER_CRAFT;
        if (requiredPerTick <= 0) {
            return 0;
        }
        long target = (long) requiredPerTick * 20L;
        target = Math.max(target, 1000L);
        return (int) Math.min(MAX_COOLANT, target);
    }

    private int refillCoolant(CoolingRecipe recipe, int deficit) {
        if (cluster == null || cluster.getInputHatch() == null || cluster.getOutputHatch() == null) {
            return 0;
        }
        FluidTank inputHatch = cluster.getInputHatch().tank;
        FluidTank outputHatch = cluster.getOutputHatch().tank;
        ResourceLocation inputFluidId = fluidId(inputHatch.getFluid());
        int inputAmount = recipe.inputAmount();
        if (deficit <= 0 || inputAmount <= 0 || recipe.coolant() <= 0) {
            return 0;
        }

        long drainAmount = Math.min(inputHatch.getFluidAmount(), getMaxDrainByOutput(recipe, outputHatch));
        if (drainAmount <= 0) {
            return 0;
        }

        int drained = inputHatch
                .drain((int) drainAmount, IFluidHandler.FluidAction.EXECUTE)
                .getAmount();
        if (drained <= 0) {
            return 0;
        }

        FluidStack output = recipe.output();
        if (!output.isEmpty() && !autoClearCoolingWaste) {
            int outputAmount = (int) ((long) drained * recipe.outputAmount() / inputAmount);
            if (outputAmount > 0) {
                outputHatch.fill(new FluidStack(output, outputAmount), IFluidHandler.FluidAction.EXECUTE);
            }
        }

        int coolantGain = (int) ((long) drained * recipe.coolant() / inputAmount);
        if (coolantGain <= 0) {
            return 0;
        }
        coolant = Math.min(MAX_COOLANT, coolant + coolantGain);
        coolantMaxOverclock = recipe.maxOverclock();
        coolantFluidId = inputFluidId;
        setChanged();
        markUiStateDirty();
        return coolantGain;
    }

    private long getMaxDrainByOutput(CoolingRecipe recipe, FluidTank outputHatch) {
        if (autoClearCoolingWaste) {
            return Long.MAX_VALUE;
        }
        FluidStack output = recipe.output();
        if (output.isEmpty()) {
            return Long.MAX_VALUE;
        }
        FluidStack stored = outputHatch.getFluid();
        if (!stored.isEmpty() && !stored.isFluidStackIdentical(output)) {
            return 0;
        }
        int outputAmount = recipe.outputAmount();
        if (outputAmount <= 0) {
            return Long.MAX_VALUE;
        }
        long outputSpace = outputHatch.getCapacity() - outputHatch.getFluidAmount();
        return outputSpace * recipe.inputAmount() / outputAmount;
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        long startNanos = System.nanoTime();
        try {
            tickBuild(level);
        } finally {
            recordPerformanceSample(System.nanoTime() - startNanos);
        }
    }

    // increaseBuildLength / decreaseBuildLength are provided by INEMultiblockBuildHost default

    @Override
    public BuildPreviewState getBuildPreview() {
        return buildPreview;
    }

    @Nullable public MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getCraftingSystemDefinition(tier);
    }

    @Override
    public void markPreviewDirty() {
        setChanged();
        markUiStateDirty();
    }

    // buildPreviewStatusComponent() is provided by INEMultiblockBuildHost default

    // UI sync (Layer 1: chunk-load NBT)
    // getUpdateTag/handleUpdateTag/getUpdatePacket are provided by NEBlockEntity.

    @Override
    protected void writeUiSyncTag(CompoundTag tag) {
        tag.putBoolean("overclocked", overclocked);
        tag.putBoolean("activeCooling", activeCooling);
        tag.putBoolean("autoClearCoolingWaste", autoClearCoolingWaste);
        tag.putInt("coolant", coolant);
        tag.putInt("coolantMaxOverclock", coolantMaxOverclock);
        if (coolantFluidId != null) {
            tag.putString("coolantFluid", coolantFluidId.toString());
        }
        tag.putInt("patternBusCount", patternBusCount);
        tag.putInt("parallelCount", parallelCount);
        tag.putInt("workerCount", workerCount);
        tag.putInt("threadCount", threadCount);
        tag.putInt("runningThreadCount", runningThreadCount);
        buildPreview.writeToTag(tag);
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("overclocked")) overclocked = tag.getBoolean("overclocked");
        if (tag.contains("activeCooling")) activeCooling = tag.getBoolean("activeCooling");
        if (tag.contains("autoClearCoolingWaste")) autoClearCoolingWaste = tag.getBoolean("autoClearCoolingWaste");
        if (tag.contains("coolant")) coolant = Mth.clamp(tag.getInt("coolant"), 0, MAX_COOLANT);
        if (tag.contains("coolantMaxOverclock")) coolantMaxOverclock = tag.getInt("coolantMaxOverclock");
        else coolantMaxOverclock = -1;
        coolantFluidId = readCoolantFluidId(tag);
        if (tag.contains("patternBusCount")) patternBusCount = tag.getInt("patternBusCount");
        if (tag.contains("parallelCount")) parallelCount = tag.getInt("parallelCount");
        if (tag.contains("workerCount")) workerCount = tag.getInt("workerCount");
        if (tag.contains("threadCount")) threadCount = tag.getInt("threadCount");
        if (tag.contains("runningThreadCount")) runningThreadCount = tag.getInt("runningThreadCount");
        buildPreview.readFromTag(tag);
    }

    @Nullable private ResourceLocation currentCoolingInputFluidId() {
        if (cluster == null || cluster.getInputHatch() == null) {
            return null;
        }
        return fluidId(cluster.getInputHatch().tank.getFluid());
    }

    @Nullable private static ResourceLocation fluidId(FluidStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return ForgeRegistries.FLUIDS.getKey(stack.getFluid());
    }

    @Nullable private static ResourceLocation readCoolantFluidId(CompoundTag tag) {
        if (!tag.contains("coolantFluid") || tag.getString("coolantFluid").isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(tag.getString("coolantFluid"));
    }
}
