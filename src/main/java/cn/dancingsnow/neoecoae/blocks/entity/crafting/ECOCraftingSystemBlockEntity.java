package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingHostBatchInfo;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingModuleCell;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEBlockEntityUIHolder;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.multiblock.BuildPreviewState;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.CoolingTransferMath;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.level.block.Block;
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

    private static final int BASE_CRAFTS_PER_WORKER = 32;
    private static final int COOLANT_PER_CRAFT = 5;
    private static final int NETWORK_COOLANT_PER_SLOT_TICK = 4;
    private static final int HIGH_ENERGY_NETWORK_COOLANT_PER_SLOT_TICK = 16;
    public static final int VIRTUAL_CRAFTING_REQUIRED_HOSTS = 8;
    public static final int VIRTUAL_CRAFTING_COOLANT_PER_TICK = 100;
    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L * 3L;

    private final ECOCraftingFastPathCache fastPathCache = new ECOCraftingFastPathCache();

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
    private final Map<UUID, List<ECOCraftingCPULogic.AggressiveSimulatedCraftSnapshot>> simulatedPoolTaskReservations =
            new HashMap<>();

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
    private long lastFullNetworkPowerTick = Long.MIN_VALUE;
    private boolean fullNetworkPowerPaid;

    /** Persisted logical-network channel; unassigned hosts receive one on first grid join. */
    private int networkFrequency = NEFrequencyAllocator.UNASSIGNED;

    public ECOCraftingSystemBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
        getMainNode().addService(IGridTickable.class, this);
    }

    public boolean hasNetworkFrequency() {
        return networkFrequency >= 0 && networkFrequency < NEFrequencyAllocator.FREQUENCY_COUNT;
    }

    public int getNetworkFrequency() {
        return hasNetworkFrequency() ? networkFrequency : 0;
    }

    /** Called by the logical network manager only for a previously unassigned host. */
    public void assignNetworkFrequency(int frequency) {
        if (hasNetworkFrequency()) {
            return;
        }
        networkFrequency = NEFrequencyAllocator.normalize(frequency);
        setChanged();
        markUiStateDirty();
        markForUpdate();
    }

    public void cycleNetworkFrequency() {
        adjustNetworkFrequency(1);
    }

    public void adjustNetworkFrequency(int delta) {
        if (!hasNetworkFrequency() && delta > 0) {
            setNetworkFrequency(0);
            return;
        }
        setNetworkFrequency((hasNetworkFrequency() ? getNetworkFrequency() : 0) + delta);
    }

    public void setNetworkFrequency(int frequency) {
        int next = NEFrequencyAllocator.normalize(frequency);
        if (networkFrequency == next) {
            return;
        }
        networkFrequency = next;
        setChanged();
        markUiStateDirty();
        markForUpdate();
        if (cluster != null && cluster.isNetworkMode()) {
            NELogicalNetworkManager.refresh(cluster);
        }
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
        tag.putInt("networkFrequency", networkFrequency);
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
        if (tag.contains("networkFrequency")) {
            int savedFrequency = tag.getInt("networkFrequency");
            networkFrequency = savedFrequency >= 0 && savedFrequency < NEFrequencyAllocator.FREQUENCY_COUNT
                    ? savedFrequency
                    : NEFrequencyAllocator.UNASSIGNED;
        }
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

    /** Physical-host state, unaffected by a logical network's shared toggle. */
    public boolean isLocalOverclocked() {
        return overclocked;
    }

    /** Physical-host cooling state, used when admitting a worker task. */
    public boolean isLocalActiveCooling() {
        return activeCooling;
    }

    /** Direct physical-host update used while a logical F9 network is being configured. */
    public void setLocalOverclocked(boolean value) {
        setNetworkOverclocked(value);
    }

    /** Direct physical-host update used while a logical F9 network is being configured. */
    public void setLocalActiveCooling(boolean value) {
        setNetworkActiveCooling(value);
    }

    public void onNetworkStateChanged() {
        markStructureStatsDirty();
        recalculateRunningThreadCountFromWorkers();
        setChanged();
        markUiStateDirty();
        markForUpdate();
    }

    public int getEffectiveOverclockTimesForLocalTasks() {
        ensureCraftingStatsCurrent();
        if (!overclocked) {
            return 0;
        }
        var network = cluster == null ? null : cluster.getNetworkCluster();
        if (!activeCooling || network == null) {
            return getEffectiveOverclockTimes();
        }
        int coolingMaxOverclock = network.getCoolingMaxOverclock();
        return coolingMaxOverclock < 0 ? 0 : Math.min(overlockTimes, coolingMaxOverclock);
    }

    public int getActiveNetworkCoolingMultiplier() {
        return cluster != null && cluster.getNetworkCluster() != null ? getNetworkMultiplier() : 1;
    }

    public int getCoolingRequirementForCurrentNetwork() {
        int multiplier = getActiveNetworkCoolingMultiplier();
        return Math.max(getEffectiveOverclockTimesForLocalTasks(), multiplier >= 8 ? 9 : 0);
    }

    public boolean canStartNetworkCooledTask(int multiplier) {
        var network = cluster == null ? null : cluster.getNetworkCluster();
        if (multiplier <= 1 || network == null) {
            return true;
        }
        int rate = multiplier >= 8 ? HIGH_ENERGY_NETWORK_COOLANT_PER_SLOT_TICK : NETWORK_COOLANT_PER_SLOT_TICK;
        int requiredOverclock = getCoolingRequirementForCurrentNetwork();
        return network.getCraftingCoolantCraftLimit(1, requiredOverclock, rate) >= rate;
    }

    /** Charges the physical host's full network-power share once per server tick. */
    public boolean tryPayFullNetworkPowerForCurrentTick() {
        if (getActiveNetworkCoolingMultiplier() <= 1) {
            return true;
        }
        long tick = TickHandler.instance().getCurrentTick();
        if (lastFullNetworkPowerTick == tick) {
            return fullNetworkPowerPaid;
        }
        lastFullNetworkPowerTick = tick;
        long requiredPower = getLocalMaxEnergyUsage();
        IGrid grid = getMainNode().getGrid();
        if (requiredPower == 0L || grid == null) {
            fullNetworkPowerPaid = requiredPower == 0L;
            return fullNetworkPowerPaid;
        }
        double extracted =
                grid.getEnergyService().extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
        fullNetworkPowerPaid = !Double.isNaN(extracted) && extracted + 0.01D >= requiredPower;
        return fullNetworkPowerPaid;
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
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ECOCraftingSystem.NETWORK_SWITCH)
                    && state.hasProperty(ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH)) {
                boolean highEnergy = formed && cluster != null && cluster.isHighEnergyNetworkMode();
                BlockState updated = state.setValue(
                                ECOCraftingSystem.NETWORK_SWITCH,
                                formed && cluster != null && cluster.isNetworkMode() && !highEnergy)
                        .setValue(ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH, highEnergy);
                if (!state.equals(updated)) {
                    level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
                }
            }
            if (level instanceof ServerLevel serverLevel) {
                if (formed && cluster != null && cluster.isNetworkMode()) {
                    NENetworkSwitchUtil.syncFormed(serverLevel, worldPosition, getBlockState());
                } else {
                    NENetworkSwitchUtil.clearFormed(serverLevel, worldPosition, getBlockState());
                }
            }
        }
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

    /** Refreshes the logical exchange lane count when network cooling capability changes. */
    public void refreshExchangeThreadCount() {
        int nextThreadCountPerWorker = cluster == null
                        || cluster.getParallelCores().isEmpty()
                        || cluster.getWorkers().isEmpty()
                ? 0
                : getExchangeHostCount();
        if (threadCountPerWorker == nextThreadCountPerWorker) {
            return;
        }
        updateThreadCount();
        updateOverlockTimes();
        setChanged();
        markUiStateDirty();
        markForUpdate();
    }

    private void updateThreadCount() {
        if (cluster != null && parallelCount > 0 && workerCount > 0) {
            if (cluster.getNetworkCluster() != null) {
                // Every FX worker receives one physical crafting thread for every host in
                // the exchange. The x2/x8 switch still affects batch capacity separately.
                threadCountPerWorker = getExchangeHostCount();
                threadCount = calculateWorkerThreadCount(workerCount, threadCountPerWorker);
            } else {
                // A standalone FX worker owns one task thread. Parallel cores and overclocking
                // increase that thread's batch capacity, not the number of concurrent tasks.
                threadCountPerWorker = 1;
                threadCount = calculateWorkerThreadCount(workerCount, threadCountPerWorker);
            }
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
        if (cluster == null || parallelCount <= 0 || workerCount <= 0) {
            overlockTimes = 0;
            return;
        }
        overlockTimes = calculateOverclockTimes(getParallelCapacity(), getMaxSynthesisEfficiency());
    }

    /**
     * Parallel-core capacity is measured in the same network-exchange units as a worker's
     * batched capacity. Consequently the x2/x8 exchange multiplier applies to this side of
     * the overflow calculation as well.
     */
    private long getParallelCapacity() {
        return calculateParallelCapacity(
                parallelCount,
                tier.getCrafterParallel(),
                tier.getOverclockedCrafterParallel(),
                overclocked,
                getNetworkMultiplier());
    }

    static long calculateParallelCapacity(
            int parallelCoreCount,
            int baseParallelPerCore,
            int overclockedParallelPerCore,
            boolean overclocked,
            int networkMultiplier) {
        long perCore = Math.max(0, baseParallelPerCore);
        if (overclocked) {
            perCore = saturatingAdd(perCore, Math.max(0, overclockedParallelPerCore));
        }
        return saturatingMultiply(
                saturatingMultiply(Math.max(0, parallelCoreCount), perCore), Math.max(1, networkMultiplier));
    }

    private long getMaxSynthesisEfficiency() {
        return calculateMaxSynthesisEfficiency(
                workerCount,
                calculateWorkerBatchCapacity(
                        BASE_CRAFTS_PER_WORKER,
                        getTier().getOverclockedCrafterQueueMultiply(),
                        overclocked,
                        Math.max(1, getNetworkMultiplier())));
    }

    static long calculateMaxSynthesisEfficiency(int workerCount, int maxBatchPerWorker) {
        return saturatingMultiply(Math.max(0, workerCount), Math.max(0, maxBatchPerWorker));
    }

    static int calculateOverclockTimes(long threadCount, long availableThreads) {
        long overflow = threadCount - availableThreads;
        if (threadCount <= 0L || overflow <= 0L) {
            return 0;
        }
        double overflowRatio = (double) overflow / (double) threadCount;
        return Mth.clamp((int) Math.round(overflowRatio / 0.05D), 0, 9);
    }

    public boolean tryConsumeCoolant(int amount, int requiredOverclock) {
        var network = cluster == null ? null : cluster.getNetworkCluster();
        if (network != null) {
            return network.tryConsumeCoolant(amount, requiredOverclock);
        }
        return tryConsumeLocalCoolant(amount, requiredOverclock);
    }

    public boolean tryConsumeNetworkCoolantTick(int ticksSinceLastCall) {
        return tryConsumeNetworkCoolantTick(getNetworkMultiplier(), ticksSinceLastCall);
    }

    public boolean tryConsumeNetworkCoolantTick(int multiplier, int ticksSinceLastCall) {
        var network = cluster == null ? null : cluster.getNetworkCluster();
        if (!activeCooling || network == null || multiplier <= 1) {
            return true;
        }
        int ticks = Math.max(1, ticksSinceLastCall);
        int rate = getNetworkCoolantPerSlotTick(multiplier);
        int amount = (int) Math.min(Integer.MAX_VALUE, (long) rate * ticks);
        int requiredOverclock = Math.max(getEffectiveOverclockTimesForLocalTasks(), multiplier >= 8 ? 9 : 0);
        return network.tryConsumeCoolant(amount, requiredOverclock);
    }

    /** Consumes the fixed coolant cost for one complete eight-host virtual crafting thread. */
    public boolean tryConsumeVirtualNetworkCoolantTick(int ticksSinceLastCall) {
        var network = cluster == null ? null : cluster.getNetworkCluster();
        if (!activeCooling || network == null || !isVirtualCraftingMode()) {
            return true;
        }
        int ticks = Math.max(1, ticksSinceLastCall);
        int amount = (int) Math.min(Integer.MAX_VALUE, (long) VIRTUAL_CRAFTING_COOLANT_PER_TICK * ticks);
        return network.tryConsumeCoolant(amount, Math.max(getEffectiveOverclockTimesForLocalTasks(), 9));
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
        if (coolant <= 0) {
            coolant = 0;
            coolantMaxOverclock = -1;
            coolantFluidId = null;
        }
        markCoolantConsumed();
        if (coolant == 0 && cluster != null && cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().onCoolingAvailabilityChanged();
        }
        return true;
    }

    public int getCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (!activeCooling || requestedCrafts <= 0) {
            return Math.max(0, requestedCrafts);
        }
        var network = cluster == null ? null : cluster.getNetworkCluster();
        if (network != null) {
            int multiplier = getNetworkMultiplier();
            if (multiplier > 1) {
                return canStartNetworkCooledTask(multiplier) ? Math.max(0, requestedCrafts) : 0;
            }
            return network.getCraftingCoolantCraftLimit(coolantPerCraft, requiredOverclock, requestedCrafts);
        }
        return getLocalCraftingCoolantCraftLimit(coolantPerCraft, requiredOverclock, requestedCrafts);
    }

    private int getNetworkCoolantPerSlotTick(int multiplier) {
        if (isVirtualCraftingMode()) {
            return VIRTUAL_CRAFTING_COOLANT_PER_TICK;
        }
        return multiplier >= 8 ? HIGH_ENERGY_NETWORK_COOLANT_PER_SLOT_TICK : NETWORK_COOLANT_PER_SLOT_TICK;
    }

    public int getLocalCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (!activeCooling || requestedCrafts <= 0) {
            return Math.max(0, requestedCrafts);
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
        var network = cluster == null ? null : cluster.getNetworkCluster();
        int coolingMaxOverclock = network == null ? getCurrentCoolingMaxOverclock() : network.getCoolingMaxOverclock();
        if (coolingMaxOverclock < 0) {
            return 0;
        }
        return Math.min(overlockTimes, coolingMaxOverclock);
    }

    public int getDisplayedCoolingMaxOverclock() {
        var network = cluster == null ? null : cluster.getNetworkCluster();
        return network == null ? getCurrentCoolingMaxOverclock() : network.getCoolingMaxOverclock();
    }

    public int getRunStatus() {
        if (!isActiveCooling()) {
            return 0;
        }
        ensureCraftingStatsCurrent();
        int coolantMax = getDisplayedCoolingMaxOverclock();
        if (getNetworkMultiplier() >= 8 && coolantMax >= 0 && coolantMax < 9) {
            return 4;
        }
        int requiredOverclock = isOverclocked() ? overlockTimes : 0;
        if (requiredOverclock > 0 && coolantMax >= 0 && coolantMax < requiredOverclock) {
            return 3;
        }
        var network = cluster == null ? null : cluster.getNetworkCluster();
        int displayedCoolant = network == null ? coolant : network.getCoolantAmount();
        return displayedCoolant <= 0 ? 1 : 0;
    }

    public void clearCoolant() {
        var network = cluster == null ? null : cluster.getNetworkCluster();
        if (network != null) {
            network.clearCoolant();
            return;
        }
        clearLocalCoolant();
    }

    public void clearLocalCoolant() {
        coolant = 0;
        coolantMaxOverclock = -1;
        coolantFluidId = null;
        setChanged();
        markUiStateDirty();
    }

    public void toggleOverclocked() {
        var network = cluster == null ? null : cluster.getNetworkCluster();
        if (network != null) {
            network.setOverclocked(!network.isOverclocked());
            return;
        }
        setNetworkOverclocked(!overclocked);
    }

    public void setNetworkOverclocked(boolean value) {
        if (overclocked == value) {
            return;
        }
        overclocked = value;
        markStructureStatsDirty();
        ensureCraftingStatsCurrent();
        setChanged();
        markUiStateDirty();
    }

    public void toggleActiveCooling() {
        var network = cluster == null ? null : cluster.getNetworkCluster();
        if (network != null) {
            network.setActiveCooling(!network.isActiveCooling());
            return;
        }
        setNetworkActiveCooling(!activeCooling);
    }

    public void setNetworkActiveCooling(boolean value) {
        if (activeCooling == value) {
            return;
        }
        activeCooling = value;
        markStructureStatsDirty();
        setChanged();
        markUiStateDirty();
    }

    public int getNetworkMultiplier() {
        return cluster == null ? 1 : cluster.getNetworkMultiplier();
    }

    /**
     * The only topology allowed to use the optional virtual FastPath scheduler.
     * Logical networks are formed per AE grid, so the member count here is also
     * scoped to the grid which owns this controller.
     */
    public boolean isFullEightHostExchange() {
        return isVirtualCraftingMode();
    }

    /** A complete eight-host exchange executes one whole recipe task as virtual ledger work. */
    public boolean isVirtualCraftingMode() {
        return cluster != null
                && cluster.getNetworkCluster() != null
                && cluster.getNetworkCluster().isActiveCooling()
                && cluster.getNetworkCluster().getMemberCount() >= VIRTUAL_CRAFTING_REQUIRED_HOSTS;
    }

    public int getNetworkPowerMultiplier() {
        return cluster == null ? 1 : cluster.getNetworkPowerMultiplier();
    }

    /** Returns whether this host can sustain the requested network cooling tier. */
    public boolean hasLocalCoolingForNetworkMultiplier(int multiplier) {
        return activeCooling && getCurrentCoolingMaxOverclock() >= (multiplier >= 8 ? 9 : 0);
    }

    public int getLocalCoolingMaxOverclock() {
        return getCurrentCoolingMaxOverclock();
    }

    @Nullable public ResourceLocation getLocalCoolantFluidId() {
        return coolantFluidId;
    }

    public void toggleAutoClearCoolingWaste() {
        autoClearCoolingWaste = !autoClearCoolingWaste;
        setChanged();
        markUiStateDirty();
    }

    public int getOverflowThreads() {
        ensureCraftingStatsCurrent();
        long overflow = Math.max(0L, getParallelCapacity() - getMaxSynthesisEfficiency());
        return (int) Math.min(Integer.MAX_VALUE, overflow);
    }

    public int getLocalOverflowThreads() {
        return getOverflowThreads();
    }

    public int getAvailableThreads() {
        ensureCraftingStatsCurrent();
        return (int) Math.min(Integer.MAX_VALUE, saturatingMultiply(threadCountPerWorker, workerCount));
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
        return Math.max(0, getMaxInFlightCrafts() - runningThreadCount);
    }

    /**
     * Selects the smallest currently free FastPath lane that can hold the requested batch.
     *
     * <p>The lane index is stable for the lifetime of a running task and lets a batch offer
     * account for other workers before dispatching. A batch occupies one logical thread; its
     * craft count is bounded by the selected lane capacity.
     */
    public CraftingLane findAvailableCraftingLane(int requiredBatchSize) {
        if (getCurrentBatchSlots() <= 0) {
            return null;
        }
        int laneCount = getLocalLaneCount();
        int capacity = getLocalLaneBatchCapacity();
        if (laneCount <= 0 || capacity < Math.max(1, requiredBatchSize)) {
            return null;
        }

        LaneOccupancy laneOccupancy = collectLaneOccupancy(laneCount);
        BitSet occupied = laneOccupancy.occupied();
        int unassignedBusy = laneOccupancy.unassignedBusy();
        for (int index = occupied.nextClearBit(0);
                index < laneCount && unassignedBusy > 0;
                index = occupied.nextClearBit(index + 1)) {
            if (!occupied.get(index)) {
                occupied.set(index);
                unassignedBusy--;
            }
        }

        int selected = occupied.nextClearBit(0);
        return selected < laneCount ? new CraftingLane(selected, capacity) : null;
    }

    /** Returns the largest batch that can be accepted by one currently free lane. */
    public int getLargestAvailableCraftingBatchSize() {
        if (getCurrentBatchSlots() <= 0) {
            return 0;
        }
        int laneCount = getLocalLaneCount();
        int capacity = getLocalLaneBatchCapacity();
        if (laneCount <= 0 || capacity <= 0) {
            return 0;
        }

        LaneOccupancy laneOccupancy = collectLaneOccupancy(laneCount);
        BitSet occupied = laneOccupancy.occupied();
        int unassignedBusy = laneOccupancy.unassignedBusy();
        for (int index = occupied.nextClearBit(0);
                index < laneCount && unassignedBusy > 0;
                index = occupied.nextClearBit(index + 1)) {
            if (!occupied.get(index)) {
                occupied.set(index);
                unassignedBusy--;
            }
        }
        return occupied.nextClearBit(0) < laneCount ? capacity : 0;
    }

    public int getLocalMaxBatchPerThread() {
        return getLocalLaneCount() > 0 ? getLocalLaneBatchCapacity() : 0;
    }

    public int getMaxBatchPerThread() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster()
                    .getMaxBatchPerThread(getMainNode().getGrid());
        }
        return getLocalMaxBatchPerThread();
    }

    /** Per-host runtime batch values for the crafting statistics tooltip. */
    public List<NECraftingHostBatchInfo> getHostBatchInfos() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getHostBatchInfos().stream()
                    .map(info -> new NECraftingHostBatchInfo(
                            info.highEnergy(), info.threadCount(), info.maxBatchPerThread()))
                    .toList();
        }
        if (!formed) {
            return List.of();
        }
        return List.of(new NECraftingHostBatchInfo(
                cluster != null && cluster.isHighEnergyNetworkMode(),
                getLocalThreadCount(),
                getLocalMaxBatchPerThread()));
    }

    private LaneOccupancy collectLaneOccupancy(int laneCount) {
        BitSet occupied = new BitSet(laneCount);
        int unassignedBusy = simulatedPoolThreadCount;
        if (cluster != null) {
            for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
                unassignedBusy += worker.collectLaneOccupancy(occupied, laneCount);
            }
        }
        return new LaneOccupancy(occupied, Math.max(0, unassignedBusy));
    }

    private int getLocalLaneCount() {
        if (cluster == null || threadCountPerWorker <= 0 || cluster.getWorkers().isEmpty()) {
            return 0;
        }
        return calculateWorkerThreadCount(cluster.getWorkers().size(), threadCountPerWorker);
    }

    private int getLocalLaneBatchCapacity() {
        // Overclocking increases the craft count of each lane. It must not turn one worker into
        // extra task lanes; network exchange contributes lanes independently.
        if (isVirtualCraftingMode()) {
            return Integer.MAX_VALUE;
        }
        return calculateWorkerBatchCapacity(
                BASE_CRAFTS_PER_WORKER,
                getTier().getOverclockedCrafterQueueMultiply(),
                overclocked,
                Math.max(1, getNetworkMultiplier()));
    }

    private int getExchangeHostCount() {
        if (cluster == null || cluster.getNetworkCluster() == null || getNetworkMultiplier() <= 1) {
            return 1;
        }
        return Math.max(1, cluster.getNetworkCluster().getMemberCount());
    }

    static int calculateWorkerBatchCapacity(
            int baseCrafts, int overclockMultiplier, boolean overclocked, int networkMultiplier) {
        long capacity = Math.max(0L, baseCrafts);
        if (overclocked) {
            capacity = saturatingMultiply(capacity, Math.max(1, overclockMultiplier));
        }
        capacity = saturatingMultiply(capacity, Math.max(1, networkMultiplier));
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    /**
     * Network exchange gives every FX worker one task thread for each participating host.
     * For example, an F9 with 11 FX workers in a two-host exchange has 11 * 2 = 22
     * local task threads; the shared network totals those local values across both hosts.
     */
    static int calculateWorkerThreadCount(int fxWorkerCount, int threadSlotsPerFxWorker) {
        return (int) Math.min(
                Integer.MAX_VALUE, saturatingMultiply(Math.max(0, fxWorkerCount), Math.max(0, threadSlotsPerFxWorker)));
    }

    public record CraftingLane(int index, int batchCapacity) {}

    private record LaneOccupancy(BitSet occupied, int unassignedBusy) {}

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatingAdd(long left, long right) {
        if (left >= Long.MAX_VALUE - Math.max(0L, right)) {
            return Long.MAX_VALUE;
        }
        return left + Math.max(0L, right);
    }

    /**
     * Maximum pattern executions that may be in flight at once.
     * The formed structure length is the number of worker segments, while the
     * parallel cores may impose a lower thread limit.
     */
    public int getMaxInFlightCrafts() {
        ensureCraftingStatsCurrent();
        if (cluster != null && cluster.getNetworkCluster() != null) {
            // Exchange lanes already carry the x2/x8 batch multiplier. Keep this
            // count in logical tasks so the multiplier is not applied twice.
            return Math.max(0, threadCount);
        }
        return (int) Math.min(
                Integer.MAX_VALUE, saturatingMultiply(Math.max(0, threadCount), Math.max(1, getNetworkMultiplier())));
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
        long multiplier = getNetworkPowerMultiplier();
        if (overclocked && !activeCooling) {
            multiplier *= tier.getOverclockedCrafterPowerMultiply();
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, multiplier));
    }

    public long getCurrentEnergyPerTick() {
        return (long) getRunningThreadCount() * getProgressPerTick() * getCraftingPowerMultiplier();
    }

    /** Maximum AE power reserved by this physical host in network-exchange mode. */
    public long getLocalMaxEnergyUsage() {
        long networkPowerMultiplier = Math.max(1L, getNetworkPowerMultiplier());
        long perThread = overclocked && !activeCooling ? tier.getOverclockedCrafterPowerMultiply() : 1L;
        return saturatingMultiply(
                saturatingMultiply(Math.max(0, getLocalAvailableThreads()), perThread),
                saturatingMultiply(networkPowerMultiplier, 100L));
    }

    public double getEnergyMultiplier() {
        return getCraftingPowerMultiplier();
    }

    /** Returns the structural parallel capacity contributed by this physical host. */
    public long getLocalBaseParallelCapacity() {
        ensureCraftingStatsCurrent();
        return saturatingMultiply(Math.max(0, parallelCount), Math.max(0, tier.getCrafterParallel()));
    }

    /** The reference duration for one recipe at the normal 10 progress/tick rate. */
    public static double getBaseCraftTicks() {
        return cn.dancingsnow.neoecoae.api.me.ECOCraftingThread.MAX_PROGRESS / 10.0D;
    }

    public static double calculateTimeMultiplier(int theoreticalCraftTicks) {
        if (theoreticalCraftTicks <= 0) {
            return 1.0D;
        }
        return theoreticalCraftTicks / getBaseCraftTicks();
    }

    /** Per-recipe duration ratio; parallel capacity is reported separately as throughput. */
    public double getTimeMultiplier() {
        ensureCraftingStatsCurrent();
        if (threadCount <= 0) {
            return 1.0D;
        }
        return calculateTimeMultiplier(getTheoreticalCraftTicks());
    }

    /**
     * Returns the estimated maximum recipe throughput represented by FX workers.
     * This is intentionally separate from the legacy time-ratio metric because x2/x8 exchange
     * multiplies batch capacity, not the duration of one logical recipe.
     */
    public double getEffectiveCraftsPerTick() {
        ensureCraftingStatsCurrent();
        int progressPerTick = Math.max(0, getProgressPerTick());
        if (progressPerTick <= 0) {
            return 0.0D;
        }

        long batchCapacity = 0L;
        if (cluster != null && cluster.getNetworkCluster() != null) {
            for (var host : cluster.getNetworkCluster().getHostBatchInfos()) {
                if (host.maxBatchPerThread() == Long.MAX_VALUE) {
                    return Double.MAX_VALUE;
                }
                long hostCapacity = saturatingMultiply(host.threadCount(), host.maxBatchPerThread());
                batchCapacity = saturatingAdd(batchCapacity, hostCapacity);
            }
        } else {
            // The local scheduler's internal 32 lanes per FX worker must not be counted as
            // independent displayed throughput. The UI and overflow calculation both define
            // one worker's capacity as its maximum batch, e.g. 11 * 512 = 5,632 for F9.
            batchCapacity = getMaxSynthesisEfficiency();
        }
        return batchCapacity <= 0
                ? 0.0D
                : (batchCapacity * (double) progressPerTick)
                        / (double) cn.dancingsnow.neoecoae.api.me.ECOCraftingThread.MAX_PROGRESS;
    }

    /** Number of physical hosts in the active logical exchange, or one for local mode. */
    public int getNetworkHostCount() {
        return cluster == null || cluster.getNetworkCluster() == null
                ? 1
                : Math.max(1, cluster.getNetworkCluster().getMemberCount());
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

    public int getLocalThreadCount() {
        ensureCraftingStatsCurrent();
        return threadCount;
    }

    public int getLocalRunningThreadCount() {
        ensureCraftingStatsCurrent();
        return runningThreadCount;
    }

    public int getLocalAvailableThreads() {
        ensureCraftingStatsCurrent();
        return Math.max(0, threadCount - runningThreadCount);
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

    public void setSimulatedPoolTaskSnapshots(
            UUID owner, List<ECOCraftingCPULogic.AggressiveSimulatedCraftSnapshot> snapshots) {
        List<ECOCraftingCPULogic.AggressiveSimulatedCraftSnapshot> normalized =
                snapshots == null || snapshots.isEmpty() ? List.of() : List.copyOf(snapshots);
        List<ECOCraftingCPULogic.AggressiveSimulatedCraftSnapshot> previous =
                simulatedPoolTaskReservations.getOrDefault(owner, List.of());
        if (previous.equals(normalized)) {
            return;
        }
        if (normalized.isEmpty()) {
            simulatedPoolTaskReservations.remove(owner);
        } else {
            simulatedPoolTaskReservations.put(owner, normalized);
        }
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

    public ECOCraftingFastPathCache getFastPathCache() {
        return fastPathCache;
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
        recoverOrphanedCraftingTasks();
        // Ensure stats are current before reading ANY field;
        // otherwise threadCount could be stale while getAvailableThreads()
        // triggers a recalculation, making effParallel inconsistent.
        ensureCraftingStatsCurrent();

        var network = cluster == null ? null : cluster.getNetworkCluster();
        int networkMemberCount = network == null ? (formed ? 1 : 0) : network.getMemberCount();
        int networkMultiplier = cluster == null ? 1 : cluster.getNetworkMultiplier();
        boolean networkConnected = isMainNodeConnected();
        int displayedCoolant = network == null ? coolant : network.getCoolantAmount();
        int displayedCoolantCapacity = network == null ? MAX_COOLANT : network.getCoolantCapacity();
        String displayedCoolantFluidId = network == null
                ? (coolantFluidId == null ? "" : coolantFluidId.toString())
                : java.util.Objects.requireNonNullElse(network.getDisplayedCoolantFluidId(), "");
        int displayedCoolingMaxOverclock = network == null ? coolantMaxOverclock : network.getCoolingMaxOverclock();
        int totalParallelism = network == null ? threadCount : network.getThreadCount();
        int totalRunningThreads = network == null ? runningThreadCount : network.getRunningThreadCount();
        int availThreads = Math.max(0, totalParallelism - totalRunningThreads);
        int effParallel = Math.min(totalParallelism, availThreads);
        // The statistics panel reports FX execution threads, rather than the internal queue
        // capacity used by the crafting scheduler. A local FX core contributes one thread;
        // network exchange changes that contribution to the number of participating hosts.
        int displayWorkerCount =
                network == null ? workerCount : network.getWorkers().size();
        int displayThreadsPerWorker = network == null ? 1 : Math.max(1, networkMemberCount);
        int maxRecipeSlots = (int) Math.min(
                Integer.MAX_VALUE, saturatingMultiply(Math.max(0, displayWorkerCount), displayThreadsPerWorker));
        int occupiedRecipeSlots = Math.min(maxRecipeSlots, Math.max(0, totalRunningThreads));
        int batchParallel = Math.max(0, effParallel);
        long maxBatchPerThread = getMaxBatchPerThread();
        List<NECraftingHostBatchInfo> hostBatchInfos = getDisplayedHostBatchInfos(network, networkMemberCount);
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
        appendSimulatedPoolRecipeEntries(recipeEntries);

        return new NECraftingUiState(
                worldPosition,
                formed,
                cluster != null && getMainNode().isActive(),
                networkMemberCount,
                networkMultiplier,
                networkConnected,
                getNetworkFrequency(),
                workerCount,
                parallelCount,
                patternBusCount,
                totalParallelism,
                totalRunningThreads,
                isOverclocked(),
                isActiveCooling(),
                isAutoClearCoolingWaste(),
                getRunStatus(),
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
                displayedCoolant,
                displayedCoolantCapacity,
                displayedCoolantFluidId,
                displayedCoolingMaxOverclock,
                availThreads,
                effParallel,
                maxRecipeSlots,
                occupiedRecipeSlots,
                batchParallel,
                maxBatchPerThread,
                hostBatchInfos,
                getOverflowThreads(),
                performanceAverageNanos,
                recipeEntries,
                craftOutputs,
                coreTiers,
                moduleCells);
    }

    /** Converts scheduler thread capacity to the per-host FX thread count shown in the tooltip. */
    private List<NECraftingHostBatchInfo> getDisplayedHostBatchInfos(
            @Nullable NECraftingNetworkCluster network, int networkMemberCount) {
        List<NECraftingHostBatchInfo> runtimeInfos = getHostBatchInfos();
        if (runtimeInfos.isEmpty()) {
            return runtimeInfos;
        }
        return runtimeInfos.stream()
                .map(info -> new NECraftingHostBatchInfo(
                        info.highEnergy(),
                        network == null ? Math.max(0, workerCount) : Math.max(0, info.threadCount()),
                        info.maxBatchPerThread()))
                .toList();
    }

    private boolean isMainNodeConnected() {
        try {
            return getMainNode().isOnline() && getMainNode().getGrid() != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void recoverOrphanedCraftingTasks() {
        if (level == null || level.isClientSide || cluster == null) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }

        Set<UUID> activeJobIds = new HashSet<>();
        for (ICraftingCPU cpu : grid.getCraftingService().getCpus()) {
            if (cpu instanceof ECOCraftingCPU ecoCpu) {
                UUID jobId = ecoCpu.getLogic().getCraftingJobId();
                if (jobId != null) {
                    activeJobIds.add(jobId);
                }
            }
        }

        clearOrphanedSimulatedPoolReservations(activeJobIds);

        var storage = grid.getStorageService().getInventory();
        for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
            worker.recoverOrphanedWorkToNetwork(activeJobIds, storage);
        }
    }

    private void clearOrphanedSimulatedPoolReservations(Set<UUID> activeJobIds) {
        if (simulatedPoolThreadReservations.isEmpty() && simulatedPoolTaskReservations.isEmpty()) {
            return;
        }

        Set<UUID> owners = new HashSet<>();
        owners.addAll(simulatedPoolThreadReservations.keySet());
        owners.addAll(simulatedPoolTaskReservations.keySet());
        for (UUID owner : owners) {
            if (!activeJobIds.contains(owner)) {
                setSimulatedPoolThreadCount(owner, 0);
                setSimulatedPoolTaskSnapshots(owner, List.of());
            }
        }
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

    private void appendSimulatedPoolRecipeEntries(List<NECraftingRecipeUiEntry> entries) {
        if (simulatedPoolTaskReservations.isEmpty()) {
            return;
        }
        Map<SimulatedTaskKey, SimulatedTaskAggregate> aggregates = new LinkedHashMap<>();
        for (List<ECOCraftingCPULogic.AggressiveSimulatedCraftSnapshot> snapshots :
                simulatedPoolTaskReservations.values()) {
            for (ECOCraftingCPULogic.AggressiveSimulatedCraftSnapshot snapshot : snapshots) {
                GenericStack output = snapshot.output();
                if (output == null || output.amount() <= 0 || !(output.what() instanceof AEItemKey itemKey)) {
                    continue;
                }
                ItemStack outputItem = itemKey.toStack(1);
                if (outputItem.isEmpty()) {
                    continue;
                }
                SimulatedTaskKey key = new SimulatedTaskKey(snapshot.owner(), outputItem);
                aggregates
                        .computeIfAbsent(key, ignored -> new SimulatedTaskAggregate(snapshot.owner(), outputItem))
                        .add(snapshot);
            }
        }

        int aggregateIndex = 0;
        for (SimulatedTaskAggregate aggregate : aggregates.values()) {
            entries.add(aggregate.toEntry(worldPosition, aggregateIndex++));
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

    private record SimulatedTaskKey(UUID craftingJobId, ItemStack output) {
        private SimulatedTaskKey {
            output = output.copyWithCount(1);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SimulatedTaskKey that)) {
                return false;
            }
            return java.util.Objects.equals(craftingJobId, that.craftingJobId)
                    && ItemStack.isSameItemSameTags(output, that.output);
        }

        @Override
        public int hashCode() {
            int result = craftingJobId != null ? craftingJobId.hashCode() : 0;
            result = 31 * result + output.getItem().hashCode();
            result = 31 * result + (output.hasTag() ? output.getTag().hashCode() : 0);
            return result;
        }
    }

    private static final class SimulatedTaskAggregate {
        private final UUID craftingJobId;
        private final ItemStack output;
        private long outputAmount;
        private long craftCount;
        private long weightedTotalProgress;
        private long weightedRemainingProgress;
        private boolean waitingOutput = true;

        private SimulatedTaskAggregate(UUID craftingJobId, ItemStack output) {
            this.craftingJobId = craftingJobId;
            this.output = output.copyWithCount(1);
        }

        private void add(ECOCraftingCPULogic.AggressiveSimulatedCraftSnapshot snapshot) {
            int slots = Math.max(1, snapshot.occupiedSlots());
            int maxProgress = Math.max(1, snapshot.maxProgress());
            int progress = Mth.clamp(snapshot.progress(), 0, maxProgress);
            outputAmount =
                    saturatedAdd(outputAmount, Math.max(1L, snapshot.output().amount()));
            craftCount = saturatedAdd(craftCount, slots);
            weightedTotalProgress = saturatedAdd(weightedTotalProgress, (long) maxProgress * slots);
            weightedRemainingProgress =
                    saturatedAdd(weightedRemainingProgress, (long) Math.max(0, maxProgress - progress) * slots);
            waitingOutput &= snapshot.outputsReady();
        }

        private NECraftingRecipeUiEntry toEntry(BlockPos controllerPos, int aggregateIndex) {
            return new NECraftingRecipeUiEntry(
                    "aggressive:" + controllerPos.asLong() + ":" + aggregateIndex + ":"
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

        private static long saturatedAdd(long left, long right) {
            if (right <= 0L) {
                return left;
            }
            long sum = left + right;
            return sum < 0L ? Long.MAX_VALUE : sum;
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
            long nextOutputAmount = outputAmount + Math.max(1L, thread.outputAmount());
            outputAmount = nextOutputAmount < 0L ? Long.MAX_VALUE : nextOutputAmount;
            craftCount += slots;
            weightedTotalProgress += (long) Math.max(1, thread.maxProgress()) * slots;
            weightedRemainingProgress += (long) Math.max(0, thread.maxProgress() - thread.progress()) * slots;
            waitingOutput &= thread.outputsReady();
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
        int targetCoolant = Math.min(MAX_COOLANT, Math.max(requiredCoolant, getTargetCoolantBuffer()));
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
        return calculateCoolantBufferTarget(getLocalThreadCount());
    }

    static int calculateCoolantBufferTarget(int localThreadCount) {
        return localThreadCount > 0 ? MAX_COOLANT : 0;
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

        long requiredInput = CoolingTransferMath.inputForDeficit(deficit, inputAmount, recipe.coolant());
        long drainAmount = Math.min(requiredInput, inputHatch.getFluidAmount());
        drainAmount = Math.min(drainAmount, getMaxDrainByOutput(recipe, outputHatch));
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
            int outputAmount = CoolingTransferMath.scaleAmount(drained, recipe.outputAmount(), inputAmount);
            if (outputAmount > 0) {
                outputHatch.fill(new FluidStack(output, outputAmount), IFluidHandler.FluidAction.EXECUTE);
            }
        }

        int coolantGain = CoolingTransferMath.scaleAmount(drained, recipe.coolant(), inputAmount);
        if (coolantGain <= 0) {
            return 0;
        }
        coolant = Math.min(MAX_COOLANT, coolant + coolantGain);
        coolantMaxOverclock = recipe.maxOverclock();
        coolantFluidId = inputFluidId;
        setChanged();
        markUiStateDirty();
        if (cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().onCoolingAvailabilityChanged();
        }
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
        if (!stored.isEmpty() && !stored.isFluidEqual(output)) {
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
