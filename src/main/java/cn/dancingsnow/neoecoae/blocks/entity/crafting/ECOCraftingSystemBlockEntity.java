package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.config.PowerMultiplier;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.CraftingCapabilitySnapshot;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostPanelUI;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostStatsText;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.multiblock.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildController;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.util.ServerTaskUtil;
import cn.dancingsnow.neoecoae.util.NEMath;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ECOCraftingSystemBlockEntity extends NEBlockEntity<NECraftingCluster, ECOCraftingSystemBlockEntity>
    implements ISyncPersistRPCBlockEntity, IGridTickable, MultiBlockBuildController.Host {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    public static final int MAX_COOLANT = 1_000_000;
    private static final int VIRTUAL_COOLANT_PER_LANE_TICK = 10_000;
    /** Highest overclock level the progress model can represent: 10 + 9 * 10 == MAX_PROGRESS. */
    static final int MAX_OVERCLOCK_TIMES = 9;
    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L * 3L;

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @Persisted
    private boolean overclocked = false;

    @Persisted
    private boolean activeCooling = false;

    @Persisted
    private boolean ignorePatternSubstitutions = false;

    @Getter
    @Persisted
    @DescSynced
    private int coolant = 0;
    @Getter
    @Persisted
    @DescSynced
    private int coolantMaxOverclock = -1;
    @Getter
    @Persisted
    @DescSynced
    private FluidStack currentCoolantFluid = FluidStack.EMPTY;

    private int workerCount = 0;

    @Getter(AccessLevel.NONE)
    private int runningThreadCount = 0;

    @Getter(AccessLevel.NONE)
    private int threadCount = 0;
    private long exactThreadCount = 0L;

    /**
     * Runtime capability values are read from the server thread only. Keep one immutable result until a runtime
     * mutation invalidates it; the hot dispatch path uses the separate capacity-only cache for worker slot checks.
     */
    @Nullable
    private CraftingCapabilitySnapshot capabilitySnapshotCache;

    /** Capacity-only view retained while runtime counters and coolant change. */
    @Nullable
    private CraftingCapabilitySnapshot capabilityCapacityCache;

    /** Detects a network regroup without retaining a stale standalone capacity view. */
    @Nullable
    private NECraftingNetworkCluster capabilityNetworkAssociation;

    @Getter
    @DescSynced
    private long performanceAverageNanos = 0L;
    private long performanceWindowStartTick = Long.MIN_VALUE;
    private long performanceWindowNanos = 0L;
    @Persisted
    @DescSynced
    private int selectedBuildLength = NEConfig.craftingSystemMaxLength - 4;
    @Persisted
    @DescSynced
    private boolean mirrorBuild;
    @Persisted
    @DescSynced
    private int networkFrequency = NEFrequencyAllocator.DEFAULT_FREQUENCY;
    @DescSynced
    private boolean buildInProgress;
    private final MultiBlockBuildController buildController = new MultiBlockBuildController(this);
    // Transient derived state rebuilt by the calculator; the BlockState property is render-only persistence.
    @Setter
    private boolean mirrored;

    public ECOCraftingSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState, NECraftingClusterCalculator::new);
        this.tier = tier;
        getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(64);
        updateInfo();
    }

    @Override
    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            ServerTaskUtil.executeIfServerRunning(serverLevel, () -> {
                setChanged();
                markForUpdate();
                updateInfo();
            });
        }
    }

    @Override
    public void updateState(boolean updateExposed) {
        if (isServerStopping()) {
            return;
        }
        super.updateState(updateExposed);
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ECOCraftingSystem.MIRRORED)) {
                BlockState newState = state.setValue(ECOCraftingSystem.MIRRORED, formed && mirrored);
                if (newState != state) {
                    level.setBlock(
                        worldPosition,
                        newState,
                        Block.UPDATE_CLIENTS
                    );
                }
            }
        }
        if (updateExposed) {
            updateInfo();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 10, false);
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
        if (!canRefillWith(recipe.maxOverclock())) {
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
        if (elapsedTicks < PERFORMANCE_SAMPLE_WINDOW_TICKS) {
            return;
        }
        long nextAverageNanos = performanceWindowNanos / Math.max(1L, elapsedTicks);
        performanceWindowStartTick = currentTick;
        performanceWindowNanos = 0L;
        if (performanceAverageNanos == nextAverageNanos) {
            return;
        }
        performanceAverageNanos = nextAverageNanos;
        setChanged();
        markForUpdate();
    }

    private void updateInfo() {
        invalidateCapabilityCapacity();
        updateCount();
        updateThreadCount();
    }

    private void updateThreadCount() {
        if (cluster != null && !cluster.getParallelCores().isEmpty()) {
            exactThreadCount = cluster.getParallelCores()
                .stream()
                .mapToLong(core -> NEMath.saturatingMultiply(
                    getCoreThreadCountLong(core.getTier(), overclocked),
                    1
                ))
                .reduce(0L, NEMath::saturatingAdd);
            threadCount = (int) Math.min(Integer.MAX_VALUE, exactThreadCount);
            recalculateRunningThreadCountFromWorkers();
        } else {
            threadCount = 0;
            exactThreadCount = 0L;
            runningThreadCount = 0;
        }
    }

    private void updateCount() {
        if (cluster != null) {
            workerCount = cluster.getWorkers().size();
        } else {
            workerCount = 0;
        }
    }

    public int getWorkerCount() {
        if (cluster != null) {
            return cluster.getWorkers().size();
        }
        return workerCount;
    }

    public void recalculateRunningThreadCountFromWorkers() {
        invalidateCapabilitySnapshot();
        if (cluster == null) {
            runningThreadCount = 0;
            return;
        }

        long recalculated = cluster.getWorkers()
            .stream()
            .mapToLong(ECOCraftingWorkerBlockEntity::getRunningBatchCount)
            .sum();
        runningThreadCount = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, recalculated));
        setChanged();
    }

    /**
     * Craft slots this worker may hold in flight. A fully virtualized endgame network reports the accounting
     * headroom instead of a hardware-derived number, so one batch can absorb an entire remaining task - that
     * is what "unlimited processing capacity" means, and it is the same number the UI and Jade display.
     *
     * <p>The override is still gated on real hardware: a host without parallel cores has a per-worker craft
     * count of zero and must keep rejecting every dispatch.
     */
    public int getThreadCountForWorker(ECOCraftingWorkerBlockEntity worker) {
        NECraftingNetworkCluster network = refreshCapabilityNetworkAssociation();
        CraftingCapabilitySnapshot.Capacity capacity = network != null
            ? network.getBatchPerFxCapacity()
            : getStandaloneCapabilityCapacitySnapshot().batchPerFx();
        if (capacity.unlimited()) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, capacity.finiteValue());
    }

    /**
     * How many {@link ECOCraftingThread} objects a worker may keep alive. Craft slots are virtual, but every
     * thread object is real memory that is ticked and persisted, so this ceiling is never lifted - not even in
     * the endgame network.
     */
    public int getThreadObjectCapacityForWorker(ECOCraftingWorkerBlockEntity worker) {
        return 1;
    }

    /**
     * Parallel-core thread ceiling a dispatch through this host may fill. It mirrors
     * {@link #getThreadCountForWorker}: both halves of the pattern bus limit have to lift together, otherwise
     * the FT parallel-core total silently caps a network that advertises unlimited capacity.
     */
    public int getDispatchThreadCapacity() {
        NECraftingNetworkCluster network = refreshCapabilityNetworkAssociation();
        CraftingCapabilitySnapshot.Capacity capacity = network != null
            ? network.getTotalBatchCapacity()
            : getStandaloneCapabilityCapacitySnapshot().totalBatchCapacity();
        return capacity.unlimited() ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, capacity.finiteValue());
    }

    public int getLocalThreadCountForWorker(ECOCraftingWorkerBlockEntity worker) {
        long capacity = isLocallyOverclocked()
            ? NEMath.saturatingMultiply(NEConfig.CRAFTING_WORKER_BASE_CRAFTS,
                getTier().getOverclockedCrafterQueueMultiply())
            : NEConfig.CRAFTING_WORKER_BASE_CRAFTS;
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    private static long getCoreThreadCountLong(IECOTier coreTier, boolean overclocked) {
        long threads = Math.max(0L, coreTier.getCrafterParallel());
        if (overclocked) {
            threads = NEMath.saturatingAdd(threads, Math.max(0L, coreTier.getOverclockedCrafterParallel()));
        }
        return threads;
    }

    static int getParallelCoreMultiplier(BlockPos corePos, Map<BlockPos, Integer> workerCapacityMultipliers) {
        return 1;
    }

    /**
     * Overclock level implied by how far the FT parallel cores overshoot the FX worker queue capacity.
     *
     * <p>{@code availableThreads} must be the un-overclocked worker capacity. Feeding it the overclocked
     * capacity makes the ratio shrink as soon as the player enables overclocking, which is exactly the
     * inversion that used to pin every balanced build at level 0.
     */
    static int calculateOverclockTimes(long threadCount, long availableThreads) {
        long overflow = threadCount - availableThreads;
        if (threadCount <= 0 || overflow <= 0) {
            return 0;
        }
        double overflowRatio = (double) overflow / (double) threadCount;
        return (int) Math.clamp(Math.round(overflowRatio / 0.05D), 0L, MAX_OVERCLOCK_TIMES);
    }

    public boolean tryConsumeCoolant(int amount, int requiredOverclock) {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().tryConsumeCoolant(amount, requiredOverclock);
        }
        return tryConsumeLocalCoolant(amount, requiredOverclock);
    }

    public boolean tryConsumeLocalCoolant(int amount, int requiredOverclock) {
        if (amount <= 0) {
            return true;
        }
        ensureCoolantAvailable(amount, requiredOverclock);
        if (coolant < amount) {
            return false;
        }
        if (requiredOverclock > 0 && coolantMaxOverclock < requiredOverclock) {
            return false;
        }
        coolant -= amount;
        if (coolant == 0) {
            coolantMaxOverclock = -1;
            currentCoolantFluid = FluidStack.EMPTY;
        }
        invalidateCapabilitySnapshot();
        setChanged();
        markForUpdate();
        return true;
    }

    public boolean usesTickBasedCoolant() {
        return isFullVirtualCraftingMode();
    }

    public boolean tryConsumeTickBasedCoolant(
        int occupiedThreadSlots,
        int attemptedProgress,
        int effectiveOverclock
    ) {
        return !usesTickBasedCoolant() || tryConsumeVirtualLaneCoolant();
    }

    public int getLocalAvailableCoolant(int requested, int requiredOverclock) {
        if (requested <= 0 || !ensureCoolantAvailable(requested, requiredOverclock)) {
            return 0;
        }
        if (requiredOverclock > 0 && coolantMaxOverclock < requiredOverclock) {
            return 0;
        }
        return Math.min(requested, coolant);
    }

    /** Flat virtual coolant cost per active physical lane; craft count never participates. */
    public boolean tryConsumeVirtualLaneCoolant() {
        return !isActiveCooling() || tryConsumeCoolant(VIRTUAL_COOLANT_PER_LANE_TICK, MAX_OVERCLOCK_TIMES);
    }

    /** Checks the flat lane coolant before paying the once-per-network-tick virtual power charge. */
    public boolean tryStartVirtualLaneTick() {
        boolean hasCoolant = true;
        if (isActiveCooling()) {
            hasCoolant = cluster != null && cluster.getNetworkCluster() != null
                ? cluster.getNetworkCluster().hasCoolant(VIRTUAL_COOLANT_PER_LANE_TICK, MAX_OVERCLOCK_TIMES)
                : getLocalAvailableCoolant(VIRTUAL_COOLANT_PER_LANE_TICK, MAX_OVERCLOCK_TIMES)
                    >= VIRTUAL_COOLANT_PER_LANE_TICK;
        }
        if (!hasCoolant) {
            return false;
        }
        return tryConsumeVirtualCraftingPower() && tryConsumeVirtualLaneCoolant();
    }

    public int getCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getCraftingCoolantCraftLimit(coolantPerCraft, requiredOverclock, requestedCrafts);
        }
        return getLocalCraftingCoolantCraftLimit(coolantPerCraft, requiredOverclock, requestedCrafts);
    }

    public int getLocalCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (!activeCooling || requestedCrafts <= 0) {
            return Math.max(0, requestedCrafts);
        }
        if (usesTickBasedCoolant()) {
            return ensureCoolantAvailable(1, requiredOverclock) ? requestedCrafts : 0;
        }
        if (coolantPerCraft <= 0) {
            return Math.max(0, requestedCrafts);
        }
        int desiredCoolant = (int) Math.min(MAX_COOLANT, (long) coolantPerCraft * requestedCrafts);
        ensureCoolantAvailable(desiredCoolant, requiredOverclock);
        if (requiredOverclock > 0 && coolantMaxOverclock < requiredOverclock) {
            return 0;
        }
        return Math.min(requestedCrafts, coolant / coolantPerCraft);
    }

    public int getEffectiveOverclockTimes() {
        return getCapabilitySnapshot().effectiveOverclock();
    }

    public int getDisplayedCoolingMaxOverclock() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getCoolantMaxOverclock() : getCurrentCoolingMaxOverclock();
    }

    public int getLocalCoolingMaxOverclock() {
        return getCurrentCoolingMaxOverclock();
    }

    /**
     * Returns the combined queue capacity of all workers reachable by this host, <em>before</em> subtracting
     * the threads that are currently running. Callers that need the remaining room subtract
     * {@link #getRunningThreadCount()} themselves, so this must never do it for them.
     */
    public int getTotalWorkerThreadCapacity() {
        CraftingCapabilitySnapshot.Capacity capacity = getCapabilitySnapshot().totalBatchCapacity();
        return capacity.unlimited() ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, capacity.finiteValue());
    }

    /**
     * Identity of the set of workers a dispatch through this host can reach: the Network Switch group when one
     * is formed, otherwise this host alone. Used to search each reachable worker set exactly once per dispatch,
     * since any bus of a group now offers every worker of that group.
     */
    public Object getDispatchScope() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster();
        }
        return this;
    }

    private long getMaxEnergyUsage() {
        return getCapabilitySnapshot().energyUsage();
    }

    /**
     * Pays this tick's flat virtual-crafting draw and reports whether crafting may progress.
     *
     * <p>Charged once per game tick for the whole exchange group, so it replaces - never adds to - the per
     * craft, per batch and per occupied-slot charges. A host outside the fully virtualized mode always answers
     * {@code true}: its threads pay their own scaling cost as before.
     */
    public boolean tryConsumeVirtualCraftingPower() {
        if (!isFullVirtualCraftingMode()) {
            return true;
        }
        NECraftingNetworkCluster network = cluster.getNetworkCluster();
        return network != null
            && network.tryConsumeVirtualCraftingPower(
                TickHandler.instance().getCurrentTick(), this::extractGridPower);
    }

    private boolean extractGridPower(double amount) {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        IEnergyService energyService = grid.getEnergyService();
        // Simulate first: a partial extraction would burn power without buying a single tick of progress.
        if (energyService.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG) < amount - 0.01D) {
            return false;
        }
        return energyService.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG) >= amount - 0.01D;
    }

    @Nullable
    private CoolingRecipe getCoolingRecipe() {
        if (cluster == null || cluster.getInputHatch() == null || cluster.getOutputHatch() == null || getLevel() == null) {
            return null;
        }
        FluidTank inputHatch = cluster.getInputHatch().tank;
        if (inputHatch.getFluidAmount() <= 0) {
            return null;
        }
        FluidTank outputHatch = cluster.getOutputHatch().tank;
        return getLevel().getRecipeManager().getRecipeFor(
            NERecipeTypes.COOLING.get(),
            new CoolingRecipe.Input(inputHatch.getFluid(), outputHatch.getFluid()),
            getLevel()
        ).map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
    }

    private boolean canRefillWith(int maxOverclock) {
        return coolant <= 0 || coolantMaxOverclock < 0 || coolantMaxOverclock == maxOverclock;
    }

    private boolean ensureCoolantAvailable(int requiredCoolant, int requiredOverclock) {
        if (!activeCooling || requiredCoolant <= 0) {
            return true;
        }
        if (coolant >= requiredCoolant && (requiredOverclock <= 0 || coolantMaxOverclock >= requiredOverclock)) {
            return true;
        }
        CoolingRecipe recipe = getCoolingRecipe();
        if (recipe == null || !canRefillWith(recipe.maxOverclock())) {
            return false;
        }
        if (requiredOverclock > 0 && recipe.maxOverclock() < requiredOverclock) {
            return false;
        }
        int targetCoolant = Math.min(MAX_COOLANT, Math.max(requiredCoolant, coolant));
        refillCoolant(recipe, targetCoolant - coolant);
        return coolant >= requiredCoolant && (requiredOverclock <= 0 || coolantMaxOverclock >= requiredOverclock);
    }

    private int getCurrentCoolingMaxOverclock() {
        if (coolant > 0 && coolantMaxOverclock >= 0) {
            return coolantMaxOverclock;
        }
        CoolingRecipe recipe = getCoolingRecipe();
        return recipe == null ? -1 : recipe.maxOverclock();
    }

    private int getTargetCoolantBuffer() {
        if (getCapabilitySnapshot().physicalFxCount() <= 0) {
            return 0;
        }
        return MAX_COOLANT;
    }

    private int refillCoolant(CoolingRecipe recipe, int deficit) {
        if (cluster == null || cluster.getInputHatch() == null || cluster.getOutputHatch() == null) {
            return 0;
        }
        FluidTank inputHatch = cluster.getInputHatch().tank;
        FluidTank outputHatch = cluster.getOutputHatch().tank;
        int inputAmount = recipe.inputAmount();
        if (deficit <= 0 || inputAmount <= 0 || recipe.coolant() <= 0) {
            return 0;
        }

        long requiredInput = ((long) deficit * inputAmount + recipe.coolant() - 1L) / recipe.coolant();
        long drainAmount = Math.min(requiredInput, inputHatch.getFluidAmount());
        drainAmount = Math.min(drainAmount, getMaxDrainByOutput(recipe, outputHatch));
        if (drainAmount <= 0) {
            return 0;
        }

        FluidStack coolantFluid = inputHatch.getFluid().copyWithAmount(1);
        int drained = inputHatch.drain((int) drainAmount, IFluidHandler.FluidAction.EXECUTE).getAmount();
        if (drained <= 0) {
            return 0;
        }

        FluidStack output = recipe.output();
        if (!output.isEmpty()) {
            int outputAmount = (int) ((long) drained * recipe.outputAmount() / inputAmount);
            if (outputAmount > 0) {
                outputHatch.fill(output.copyWithAmount(outputAmount), IFluidHandler.FluidAction.EXECUTE);
            }
        }

        int coolantGain = (int) ((long) drained * recipe.coolant() / inputAmount);
        if (coolantGain <= 0) {
            return 0;
        }
        coolant = Math.min(MAX_COOLANT, coolant + coolantGain);
        coolantMaxOverclock = recipe.maxOverclock();
        currentCoolantFluid = coolantFluid;
        invalidateCapabilitySnapshot();
        setChanged();
        markForUpdate();
        return coolantGain;
    }

    private long getMaxDrainByOutput(CoolingRecipe recipe, FluidTank outputHatch) {
        FluidStack output = recipe.output();
        if (output.isEmpty()) {
            return Long.MAX_VALUE;
        }
        FluidStack stored = outputHatch.getFluid();
        if (!stored.isEmpty() && !FluidStack.isSameFluidSameComponents(stored, output)) {
            return 0;
        }
        int outputAmount = recipe.outputAmount();
        if (outputAmount <= 0) {
            return Long.MAX_VALUE;
        }
        long outputSpace = outputHatch.getCapacity() - outputHatch.getFluidAmount();
        return outputSpace * recipe.inputAmount() / outputAmount;
    }

    public boolean hasNormalNetworkSwitch() {
        BlockState state = getBlockState();
        return state.hasProperty(ECOCraftingSystem.NETWORK_SWITCH) && state.getValue(ECOCraftingSystem.NETWORK_SWITCH);
    }

    public boolean hasHighEnergyNetworkSwitch() {
        BlockState state = getBlockState();
        return state.hasProperty(ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH) && state.getValue(ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH);
    }

    public boolean hasNetworkSwitch() {
        return hasNormalNetworkSwitch() || hasHighEnergyNetworkSwitch();
    }

    public boolean hasNetworkFrequency() {
        return networkFrequency >= 1 && networkFrequency <= NEFrequencyAllocator.FREQUENCY_COUNT;
    }

    public int getLocalThreadCount() { return threadCount; }
    public int getLocalRunningThreadCount() { return runningThreadCount; }
    public int getThreadCount() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getThreadCount() : threadCount;
    }
    public int getRunningThreadCount() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getRunningThreadCount() : runningThreadCount;
    }

    public int getNetworkFrequency() {
        return hasNetworkFrequency() ? networkFrequency : NEFrequencyAllocator.DEFAULT_FREQUENCY;
    }

    /** Manager-only: assigns a frequency to a newly-eligible host that has none yet. */
    public void assignNetworkFrequency(int frequency) {
        if (hasNetworkFrequency()) {
            return;
        }
        this.networkFrequency = NEFrequencyAllocator.normalize(frequency);
        setChanged();
        markForUpdate();
    }

    public void cycleNetworkFrequency(Player player) {
        if (!canPlayerInteract(player)) return;
        adjustNetworkFrequency(1);
    }

    public void adjustNetworkFrequency(int delta) {
        int current = hasNetworkFrequency() ? networkFrequency : NEFrequencyAllocator.DEFAULT_FREQUENCY;
        setNetworkFrequency(NEFrequencyAllocator.normalize(current + delta));
    }

    /** Player-facing: manually reassigns this host's frequency, splitting/rejoining groups. */
    public void setNetworkFrequency(int frequency) {
        this.networkFrequency = NEFrequencyAllocator.normalize(frequency);
        setChanged();
        markForUpdate();
        if (cluster != null) {
            NELogicalNetworkManager.refresh(cluster);
        }
    }

    @Override
    protected void onMainNodeGridChanged() {
        if (cluster != null) {
            NELogicalNetworkManager.refreshAfterGridChange(cluster);
        }
    }

    public int getPooledParallelism() {
        return (int) Math.min(Integer.MAX_VALUE, getCapabilitySnapshot().ftParallelCapacity());
    }

    public int getPooledCraftingCapability() {
        return getTotalWorkerThreadCapacity();
    }

    private int getPooledWorkerCount() {
        return getCapabilitySnapshot().physicalFxCount();
    }

    private int getPooledActiveWorkerCount() {
        return getCapabilitySnapshot().activeFxCount();
    }

    private int getSingleCoreProcessingCapacity() {
        long capacity = getSingleCoreProcessingCapacityLong();
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    private long getSingleCoreProcessingCapacityLong() {
        CraftingCapabilitySnapshot.Capacity capacity = getCapabilitySnapshot().batchPerFx();
        return capacity.unlimited() ? Long.MAX_VALUE : capacity.finiteValue();
    }

    /**
     * The single predicate every "unlimited" claim goes through - dispatch limits, the panel readout, the
     * statistics tooltip and the Jade overlay all read it, so the display can never promise a capacity the
     * dispatch path does not actually grant.
     *
     * <p>FT parallel capacity is deliberately absent from this predicate: virtual eligibility is physical FX
     * topology, while FT remains an ordinary-mode timing input.
     */
    public boolean isFullVirtualCraftingMode() {
        NECraftingNetworkCluster network = refreshCapabilityNetworkAssociation();
        if (network != null) {
            return network.isVirtualMode();
        }
        return getStandaloneCapabilityCapacitySnapshot().virtualMode();
    }

    /** Authoritative server-side capability state consumed by dispatch, GUI, tooltips and Jade. */
    public CraftingCapabilitySnapshot getCapabilitySnapshot() {
        NECraftingNetworkCluster network = refreshCapabilityNetworkAssociation();
        if (network != null) {
            return network.getCapabilitySnapshot();
        }
        if (capabilitySnapshotCache != null) {
            return capabilitySnapshotCache;
        }
        int physicalFx = cluster == null ? 0 : cluster.getWorkers().size();
        int activeFx = 0;
        int runningBatches = 0;
        if (cluster != null) {
            for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
                if (worker.isWorking()) {
                    activeFx++;
                }
                runningBatches = (int) Math.min(Integer.MAX_VALUE,
                    (long) runningBatches + worker.getRunningBatchCount());
            }
        }
        long standaloneOverclockedBatch = NEMath.saturatingMultiply(
            NEConfig.CRAFTING_WORKER_BASE_CRAFTS,
            getTier().getOverclockedCrafterQueueMultiply()
        );
        CraftingCapabilitySnapshot snapshot = CraftingCapabilitySnapshot.calculate(new CraftingCapabilitySnapshot.Input(
            physicalFx,
            activeFx,
            0,
            0,
            standaloneOverclockedBatch,
            exactThreadCount,
            runningBatches,
            overclocked,
            activeCooling,
            getTier().getOverclockedCrafterPowerMultiply(),
            false,
            new CraftingCapabilitySnapshot.CoolantState(
                activeCooling, coolant, MAX_COOLANT, getCurrentCoolingMaxOverclock())
        ));
        capabilitySnapshotCache = snapshot;
        return snapshot;
    }

    /** Invalidates the standalone cache and the shared network cache, if this host belongs to one. */
    void invalidateCapabilitySnapshot() {
        capabilitySnapshotCache = null;
        if (cluster != null && cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().invalidateCapabilitySnapshot();
        }
    }

    /** Invalidates both runtime and capacity views after topology or overclock changes. */
    private void invalidateCapabilityCapacity() {
        capabilitySnapshotCache = null;
        capabilityCapacityCache = null;
        if (cluster != null && cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().invalidateCapabilityCapacity();
        }
    }

    private CraftingCapabilitySnapshot getStandaloneCapabilityCapacitySnapshot() {
        if (capabilityCapacityCache == null) {
            capabilityCapacityCache = getCapabilitySnapshot();
        }
        return capabilityCapacityCache;
    }

    @Nullable
    private NECraftingNetworkCluster refreshCapabilityNetworkAssociation() {
        NECraftingNetworkCluster network = cluster == null ? null : cluster.getNetworkCluster();
        if (network != capabilityNetworkAssociation) {
            capabilityNetworkAssociation = network;
            capabilitySnapshotCache = null;
            capabilityCapacityCache = null;
        }
        return network;
    }

    public long getLocalFtParallelCapacity() {
        return Math.max(0L, exactThreadCount);
    }

    /** Compatibility name for UI integrations; the authoritative value lives in the snapshot. */
    public int getOverlockTimes() {
        return getCapabilitySnapshot().theoreticalOverclock();
    }

    private int getCraftingNetworkMemberCount() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getMembers().size()
            : 1;
    }

    private Component getCraftingDisplayTitle() {
        return getItemFromBlockEntity().getDescription().copy()
            .append(" (")
            .append(Integer.toString(getCraftingNetworkMemberCount()))
            .append("/")
            .append(Integer.toString(NEFrequencyAllocator.HOST_LIMIT))
            .append(")");
    }

    private Component getStatsTooltip() {
        return CraftingHostStatsText.capability(getCapabilitySnapshot());
    }

    public int getDisplayedCoolantAmount() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getCoolantAmount() : coolant;
    }

    public int getDisplayedCoolantCapacity() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getCoolantCapacity() : MAX_COOLANT;
    }

    public FluidStack getDisplayedCoolantFluid() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getCoolantFluid() : currentCoolantFluid;
    }

    public boolean isOverclocked() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().isOverclocked() : overclocked;
    }

    public boolean isActiveCooling() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().isActiveCooling() : activeCooling;
    }

    public boolean isLocallyOverclocked() {
        return overclocked;
    }

    public boolean isLocallyActiveCooling() {
        return activeCooling;
    }

    public boolean isLocallyIgnoringPatternSubstitutions() {
        return ignorePatternSubstitutions;
    }

    public void applyNetworkIgnoringPatternSubstitutions(boolean value) {
        if (ignorePatternSubstitutions == value) return;
        ignorePatternSubstitutions = value;
        setChanged();
        markForUpdate();
    }

    public void applyNetworkOverclocked(boolean value) {
        if (overclocked == value) return;
        overclocked = value;
        updateInfo();
        setChanged();
        markForUpdate();
    }

    public void applyNetworkActiveCooling(boolean value) {
        if (activeCooling == value) return;
        activeCooling = value;
        invalidateCapabilitySnapshot();
        setChanged();
        markForUpdate();
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        long startNanos = System.nanoTime();
        try {
            buildController.tick(level);
        } finally {
            recordPerformanceSample(System.nanoTime() - startNanos);
        }
    }


    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement buildWindow = buildPanel(holder);

        CraftingHostPanelUI.Config panelConfig = createCraftingPanelConfig(holder.player);
        UIElement root = CraftingHostPanelUI.create(panelConfig);
        List<UIElement> sideButtons = new ArrayList<>();
        sideButtons.add(GuideButton.create(holder.player, "neoecoae:neoecoae_intro/crafting_system.md"));
        sideButtons.add(MultiblockBuilderUI.createInlineOpenButton(buildWindow));
        sideButtons.addAll(CraftingHostPanelUI.createToolbarButtons(panelConfig));
        root.addChild(HostSideButtonBar.left(sideButtons));
        root.addChild(buildWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private CraftingHostPanelUI.Config createCraftingPanelConfig(Player player) {
        return new CraftingHostPanelUI.Config(
            this::getCraftingDisplayTitle,
            () -> Math.max(1, getCapabilitySnapshot().networkMultiplier()),
            () -> getMainNode().isOnline() && getMainNode().getGrid() != null,
            () -> formed,
            this::isOverclocked,
            () -> setOverclocked(player, !isOverclocked()),
            this::isActiveCooling,
            () -> setActiveCooling(player, !isActiveCooling()),
            this::getPooledActiveWorkerCount,
            this::getPooledWorkerCount,
            this::getSingleCoreProcessingCapacity,
            this::getEffectiveOverclockTimes,
            () -> getCapabilitySnapshot().virtualMode()
                ? 1 : CraftingHostPanelUI.formatRecipeTimeTicks(getEffectiveOverclockTimes()),
            this::getStatsTooltip,
            this::getPerformanceAverageNanos,
            this::getMaxEnergyUsage,
            this::getDisplayedCoolantAmount,
            this::getDisplayedCoolantCapacity,
            this::getDisplayedCoolingMaxOverclock,
            this::getDisplayedCoolantFluid,
            this::getRegistryAccessForUi,
            this::getActiveTaskEntries,
            this::isIgnoringPatternSubstitutions,
            this::getSubstitutionPatternCount,
            () -> toggleIgnoringPatternSubstitutions(player),
            this::getNetworkFrequency,
            delta -> {
                if (canPlayerInteract(player)) adjustNetworkFrequency(delta);
            }
        );
    }

    private boolean isIgnoringPatternSubstitutions() {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getMainNode().getGrid());
        return settings != null
            ? settings.neoecoae$isIgnoringPatternSubstitutions()
            : ignorePatternSubstitutions;
    }

    private int getSubstitutionPatternCount() {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getMainNode().getGrid());
        return settings == null ? 0 : settings.neoecoae$getSubstitutionPatternCount();
    }

    private void toggleIgnoringPatternSubstitutions(Player player) {
        if (!canPlayerInteract(player)) return;
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getMainNode().getGrid());
        if (settings != null) {
            settings.neoecoae$setIgnoringPatternSubstitutions(
                !settings.neoecoae$isIgnoringPatternSubstitutions());
        } else {
            applyNetworkIgnoringPatternSubstitutions(!ignorePatternSubstitutions);
        }
    }

    private void setOverclocked(Player player, boolean overclocked) {
        if (!canPlayerInteract(player)) return;
        if (cluster != null && cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().setOverclocked(overclocked);
        } else {
            applyNetworkOverclocked(overclocked);
        }
    }

    private void setActiveCooling(Player player, boolean activeCooling) {
        if (!canPlayerInteract(player)) return;
        if (cluster != null && cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().setActiveCooling(activeCooling);
        } else {
            applyNetworkActiveCooling(activeCooling);
        }
    }

    private HolderLookup.Provider getRegistryAccessForUi() {
        if (level != null) {
            return level.registryAccess();
        }
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
            .getServerResources()
            .managers()
            .fullRegistries()
            .get();
    }

    private List<ComputationTaskEntry> getActiveTaskEntries() {
        if (cluster == null) {
            return List.of();
        }
        Map<TaskAggregateKey, TaskAggregate> aggregates = new LinkedHashMap<>();
        for (ECOCraftingWorkerBlockEntity worker : collectDisplayedWorkers()) {
            for (ECOCraftingThread.Snapshot snapshot : worker.getThreadSnapshots()) {
                ItemStack output = snapshot.outputItem();
                if (output.isEmpty()) {
                    continue;
                }
                TaskAggregateKey key = new TaskAggregateKey(snapshot.craftingJobId(), output);
                aggregates.computeIfAbsent(key, ignored -> new TaskAggregate(output.copyWithCount(1))).add(snapshot);
            }
        }
        List<ComputationTaskEntry> entries = new ArrayList<>();
        int index = 0;
        for (TaskAggregate aggregate : aggregates.values()) {
            entries.add(aggregate.toEntry(worldPosition, index++));
        }
        return List.copyOf(entries);
    }

    /**
     * The task list has to describe the same worker population the FX-core readout counts. Both are pooled
     * across the Network Switch group, so a host whose own workers happen to be idle no longer reports "no
     * active tasks" while the group-wide core counter is moving.
     */
    private List<ECOCraftingWorkerBlockEntity> collectDisplayedWorkers() {
        if (cluster == null) {
            return List.of();
        }
        var network = cluster.getNetworkCluster();
        return network != null ? network.collectCandidateWorkers() : cluster.getWorkers();
    }

    private UIElement buildPanel(BlockUIMenuType.BlockUIHolder holder) {
        return MultiblockBuilderUI.createFloatingPanel(new MultiblockBuilderUI.Config(
            holder.player,
            () -> selectedBuildLength,
            () -> mirrorBuild,
            mirror -> buildController.setMirrorBuild(holder.player, mirror),
            () -> buildController.decreaseBuildLength(holder.player),
            () -> buildController.increaseBuildLength(holder.player),
            () -> buildController.autoBuild(holder.player),
            () -> formed,
            () -> buildInProgress,
            buildController::createLocalPreviewPlan
        ));
    }

    @Override
    public @Nullable MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getCraftingSystemDefinition(tier);
    }

    @Override
    public int getMinBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMin();
    }

    @Override
    public int getMaxBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMax();
    }

    @Override
    public boolean canPlayerInteract(Player player) {
        return level != null && ECOCraftingSystem.isPlayerCloseEnough(level, worldPosition, player);
    }

    @Override
    public Level getBuildLevel() { return level; }

    @Override
    public BlockPos getBuildPosition() { return worldPosition; }

    @Override
    public BlockState getBuildState() { return getBlockState(); }

    @Override
    public int getSelectedBuildLength() { return selectedBuildLength; }

    @Override
    public void setSelectedBuildLength(int length) {
        if (selectedBuildLength == length) return;
        selectedBuildLength = length;
        invalidateCapabilityCapacity();
    }

    @Override
    public boolean isMirrorBuild() { return mirrorBuild; }

    @Override
    public void setMirrorBuild(boolean mirrorBuild) { this.mirrorBuild = mirrorBuild; }

    @Override
    public boolean isBuildInProgress() { return buildInProgress; }

    @Override
    public void setBuildInProgress(boolean buildInProgress) { this.buildInProgress = buildInProgress; }

    @Override
    public boolean isFormed() { return formed; }

    @Override
    public void rebuildAfterBuild() { rebuildMultiblock(); }

    @Override
    public void buildStateChanged() {
        setChanged();
        markForUpdate();
    }

    private record TaskAggregateKey(UUID craftingJobId, ItemStack output) {
        private TaskAggregateKey {
            output = output.copyWithCount(1);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TaskAggregateKey that)) {
                return false;
            }
            return java.util.Objects.equals(craftingJobId, that.craftingJobId)
                && ItemStack.isSameItemSameComponents(output, that.output);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(craftingJobId, output.getItem(), output.getComponents());
        }
    }

    private static final class TaskAggregate {
        private final ItemStack output;
        private long outputAmount;
        private long craftCount;
        private long totalProgress;
        private long remainingProgress;
        private boolean waitingOutput = true;
        private boolean fastPathHit;
        private final LinkedHashSet<String> fastPathReasons = new LinkedHashSet<>();

        private TaskAggregate(ItemStack output) {
            this.output = output;
        }

        private void add(ECOCraftingThread.Snapshot snapshot) {
            long crafts = Math.max(1L, snapshot.craftCount());
            int maxProgress = Math.max(1, snapshot.maxProgress());
            int progress = Mth.clamp(snapshot.progress(), 0, maxProgress);
            outputAmount = NEMath.saturatingAdd(outputAmount, Math.max(1L, snapshot.outputAmount()));
            craftCount = NEMath.saturatingAdd(craftCount, crafts);
            totalProgress = NEMath.saturatingAdd(totalProgress,
                NEMath.saturatingMultiply(maxProgress, crafts));
            remainingProgress = NEMath.saturatingAdd(remainingProgress,
                NEMath.saturatingMultiply(Math.max(0, maxProgress - progress), crafts));
            waitingOutput &= snapshot.outputsReady();
            if ("FAST_PATH_HIT".equals(snapshot.fastPathReason())) {
                fastPathHit = true;
            } else if (snapshot.fastPathReason() != null) {
                fastPathReasons.add(snapshot.fastPathReason());
            }
        }

        private ComputationTaskEntry toEntry(BlockPos controllerPos, int index) {
            long safeTotal = Math.max(1L, totalProgress);
            long safeRemaining = Math.max(0L, Math.min(safeTotal, remainingProgress));
            float progress = Mth.clamp((safeTotal - safeRemaining) / (float)safeTotal, 0.0F, 1.0F);
            return new ComputationTaskEntry(
                "crafting:" + controllerPos.asLong() + ":" + index + ":" + output.getItem().hashCode(),
                output.copyWithCount(1),
                Math.max(1L, outputAmount),
                Math.max(1L, craftCount),
                safeTotal,
                safeRemaining,
                waitingOutput ? ComputationTaskEntry.Status.WAITING_OUTPUT : ComputationTaskEntry.Status.RUNNING,
                index + 1,
                Component.translatable("gui.neoecoae.host.crafting.subtitle"),
                0L,
                0,
                CpuSelectionMode.ANY,
                progress,
                0L,
                fastPathHit || fastPathReasons.isEmpty() ? null
                    : fastPathReasons.size() == 1 ? fastPathReasons.iterator().next() : "MULTIPLE"
            );
        }
    }
}

