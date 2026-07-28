package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.config.PowerMultiplier;
import appeng.core.localization.Tooltips;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostPanelUI;
import cn.dancingsnow.neoecoae.gui.multiblock.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.util.ServerTaskUtil;
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
import lombok.Setter;
import net.minecraft.core.BlockPos;
import appeng.api.networking.IGridNodeListener;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ECOCraftingSystemBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingSystemBlockEntity>
    implements ISyncPersistRPCBlockEntity, IGridTickable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    public static final int MAX_COOLANT = 1_000_000;
    private static final int COOLANT_PER_CRAFT = 5;
    private static final int NETWORK_COOLANT_PER_SLOT_TICK = 4;
    private static final int HIGH_ENERGY_NETWORK_COOLANT_PER_SLOT_TICK = 16;
    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L * 3L;

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @Persisted
    private boolean overclocked = false;

    @Persisted
    private boolean activeCooling = false;

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

    private int patternBusCount, parallelCount, workerCount = 0;

    private int runningThreadCount = 0;

    private int threadCount = 0;
    private long exactThreadCount = 0L;
    private long exactParallelCapacity = 0L;
    private long exactWorkerCapacity = 0L;

    @Getter
    private int threadCountPerWorker = 0;
    private long exactAvailableThreadCount = 0L;

    @Getter
    private int overlockTimes = 0;
    @Getter
    @DescSynced
    private long performanceAverageNanos = 0L;
    private long performanceWindowStartTick = Long.MIN_VALUE;
    private long performanceWindowNanos = 0L;
    private long lastFullNetworkPowerTick = Long.MIN_VALUE;
    private boolean fullNetworkPowerPaid;
    @Persisted
    @DescSynced
    private int selectedBuildLength = 1;
    @Persisted
    @DescSynced
    private boolean mirrorBuild;
    @DescSynced
    private boolean buildInProgress;
    private transient MultiBlockBuildSession buildSession;
    private transient UUID buildPlayerId;
    @Setter
    private boolean mirrored;

    public ECOCraftingSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
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
        if (level instanceof ServerLevel serverLevel) {
            if (formed) {
                NENetworkSwitchUtil.syncFormed(serverLevel, worldPosition, getBlockState(), mirrored);
            } else {
                NENetworkSwitchUtil.clearFormed(serverLevel, worldPosition, getBlockState());
            }
        }
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ECOCraftingSystem.MIRRORED)
                && state.hasProperty(ECOCraftingSystem.NETWORK_SWITCH)
                && state.hasProperty(ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH)) {
                boolean highEnergyNetworkMode = formed && cluster != null && cluster.isHighEnergyNetworkMode();
                BlockState newState = state
                    .setValue(ECOCraftingSystem.MIRRORED, formed && mirrored)
                    .setValue(ECOCraftingSystem.NETWORK_SWITCH,
                        formed && cluster != null && cluster.isNetworkMode() && !highEnergyNetworkMode)
                    .setValue(ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH, highEnergyNetworkMode);
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

    public boolean isLocalOverclocked() {
        return overclocked;
    }

    public boolean isLocalActiveCooling() {
        return activeCooling;
    }

    public boolean isActiveCooling() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().isActiveCooling()
            : activeCooling;
    }

    public boolean hasLocalCoolingForNetworkMultiplier(int multiplier) {
        return activeCooling && getCurrentCoolingMaxOverclock() >= (multiplier >= 8 ? 9 : 0);
    }

    public boolean isOverclocked() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().isOverclocked()
            : overclocked;
    }

    public void setLocalOverclocked(boolean value) {
        if (overclocked == value) {
            return;
        }
        overclocked = value;
        updateInfo();
        setChanged();
        markForUpdate();
    }

    public void setLocalActiveCooling(boolean value) {
        if (activeCooling == value) {
            return;
        }
        activeCooling = value;
        updateInfo();
        setChanged();
        markForUpdate();
        wakeControllerTicking();
    }

    public void onNetworkStateChanged() {
        updateInfo();
        recalculateRunningThreadCountFromWorkers();
        setChanged();
        markForUpdate();
        wakeControllerTicking();
    }

    public void refreshExchangeThreadCount() {
        int nextThreadCountPerWorker = cluster == null || cluster.getParallelCores().isEmpty()
            || cluster.getWorkers().isEmpty() ? 0 : getExchangeHostCount();
        if (threadCountPerWorker == nextThreadCountPerWorker) {
            return;
        }
        updateThreadCount();
        updateOverlockTimes();
        setChanged();
        markForUpdate();
    }

    private void wakeControllerTicking() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
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
        boolean fullNetworkPowerMode = isFullNetworkPowerMode();
        if (fullNetworkPowerMode) {
            tryPayFullNetworkPowerForCurrentTick();
        }
        if (!activeCooling) {
            return fullNetworkPowerMode ? TickRateModulation.URGENT : TickRateModulation.IDLE;
        }
        CoolingRecipe recipe = getCoolingRecipe();
        if (recipe == null) {
            return fullNetworkPowerMode ? TickRateModulation.URGENT : TickRateModulation.IDLE;
        }
        if (!canRefillWith(recipe.maxOverclock())) {
            return fullNetworkPowerMode ? TickRateModulation.URGENT : TickRateModulation.IDLE;
        }

        int targetCoolant = getTargetCoolantBuffer();
        if (targetCoolant <= coolant) {
            return fullNetworkPowerMode ? TickRateModulation.URGENT : TickRateModulation.IDLE;
        }

        int refillAmount = refillCoolant(recipe, targetCoolant - coolant);
        if (refillAmount <= 0) {
            return fullNetworkPowerMode ? TickRateModulation.URGENT : TickRateModulation.IDLE;
        }
        return coolant < targetCoolant || fullNetworkPowerMode
            ? TickRateModulation.URGENT
            : TickRateModulation.IDLE;
    }

    private boolean isFullNetworkPowerMode() {
        return cluster != null && cluster.getNetworkCluster() != null && cluster.getNetworkMultiplier() > 1;
    }

    public boolean tryPayFullNetworkPowerForCurrentTick() {
        if (!isFullNetworkPowerMode()) {
            return false;
        }
        long currentTick = TickHandler.instance().getCurrentTick();
        if (lastFullNetworkPowerTick == currentTick) {
            return fullNetworkPowerPaid;
        }
        lastFullNetworkPowerTick = currentTick;
        long requiredPower = getLocalMaxEnergyUsage();
        var grid = getMainNode().getGrid();
        if (grid == null || requiredPower <= 0L) {
            fullNetworkPowerPaid = requiredPower <= 0L;
            return fullNetworkPowerPaid;
        }
        double extracted = grid.getEnergyService().extractAEPower(
            requiredPower,
            Actionable.MODULATE,
            PowerMultiplier.CONFIG
        );
        fullNetworkPowerPaid = Double.isFinite(extracted) && extracted >= requiredPower;
        return fullNetworkPowerPaid;
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
        updateCount();
        updateThreadCount();
        updateOverlockTimes();
    }

    private void updateThreadCount() {
        if (cluster != null && !cluster.getParallelCores().isEmpty() && !cluster.getWorkers().isEmpty()) {
            threadCountPerWorker = getExchangeHostCount();
            exactThreadCount = saturatingMultiply(getWorkerCount(), threadCountPerWorker);
            exactAvailableThreadCount = exactThreadCount;
            long perWorkerCapacity = overclocked
                ? saturatingMultiply(
                    NEConfig.getCraftingWorkerBaseCrafts(),
                    getTier().getOverclockedCrafterQueueMultiply()
                )
                : NEConfig.getCraftingWorkerBaseCrafts();
            exactWorkerCapacity = saturatingMultiply(perWorkerCapacity, getWorkerCount());
            exactParallelCapacity = cluster.getParallelCores()
                .stream()
                .mapToLong(core -> getCoreThreadCountLong(core.getTier(), overclocked))
                .reduce(0L, ECOCraftingSystemBlockEntity::saturatingAdd);
            threadCount = (int) Math.min(Integer.MAX_VALUE, exactThreadCount);
            recalculateRunningThreadCountFromWorkers();
        } else {
            threadCount = 0;
            exactThreadCount = 0L;
            threadCountPerWorker = 0;
            exactAvailableThreadCount = 0L;
            exactParallelCapacity = 0L;
            exactWorkerCapacity = 0L;
            runningThreadCount = 0;
        }
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

    public int getWorkerCount() {
        if (cluster != null) {
            return cluster.getWorkers().size();
        }
        return workerCount;
    }

    public int getLocalThreadCount() {
        return threadCount;
    }

    public int getThreadCount() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getThreadCount()
            : threadCount;
    }

    public int getLocalRunningThreadCount() {
        return runningThreadCount;
    }

    public int getLocalEffectiveOverclockTimes() {
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

    public int getEffectiveOverclockTimesForLocalTasks() {
        if (!overclocked) {
            return 0;
        }
        if (!activeCooling || cluster == null || cluster.getNetworkCluster() == null) {
            return getLocalEffectiveOverclockTimes();
        }
        int coolingMaxOverclock = cluster.getNetworkCluster().getCoolingMaxOverclock();
        return coolingMaxOverclock < 0 ? 0 : Math.min(overlockTimes, coolingMaxOverclock);
    }

    public int getRunningThreadCount() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getRunningThreadCount()
            : runningThreadCount;
    }

    public void onWorkerThreadCountChanged(int delta) {
        int before = runningThreadCount;
        long updated = (long) runningThreadCount + delta;
        if (updated < 0L) {
            LOGGER.warn(
                "ECO controller runningThreadCount underflow: controller={} delta={} before correction previous={}",
                getBlockPos(),
                delta,
                before
            );
            updated = 0L;
        } else if (updated > Integer.MAX_VALUE) {
            LOGGER.warn(
                "ECO controller runningThreadCount overflow: controller={} delta={} previous={}",
                getBlockPos(),
                delta,
                before
            );
            updated = Integer.MAX_VALUE;
        }
        runningThreadCount = (int) updated;
        setChanged();
    }

    public void recalculateRunningThreadCountFromWorkers() {
        if (cluster == null) {
            runningThreadCount = 0;
            return;
        }

        long recalculated = cluster.getWorkers()
            .stream()
            .mapToLong(ECOCraftingWorkerBlockEntity::getRunningThreads)
            .sum();
        runningThreadCount = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, recalculated));
    }

    private void updateOverlockTimes() {
        overlockTimes = calculateOverclockTimes(exactParallelCapacity, exactWorkerCapacity);
    }

    static int getCoreThreadCount(IECOTier coreTier, boolean overclocked) {
        return (int) Math.min(Integer.MAX_VALUE, getCoreThreadCountLong(coreTier, overclocked));
    }

    public CraftingLane findAvailableCraftingLane(int requiredBatchSize) {
        List<Integer> capacities = getLocalLaneBatchCapacities();
        if (capacities.isEmpty()) {
            return null;
        }
        LaneOccupancy laneOccupancy = collectLaneOccupancy(capacities.size());
        Set<Integer> occupied = laneOccupancy.occupied();
        int unassignedBusy = laneOccupancy.unassignedBusy();
        for (int index = 0; index < capacities.size() && unassignedBusy > 0; index++) {
            if (occupied.add(index)) {
                unassignedBusy--;
            }
        }

        int required = Math.max(1, requiredBatchSize);
        CraftingLane selected = null;
        for (int index = 0; index < capacities.size(); index++) {
            int capacity = capacities.get(index);
            if (occupied.contains(index) || capacity < required) {
                continue;
            }
            if (selected == null || capacity < selected.batchCapacity()) {
                selected = new CraftingLane(index, capacity);
            }
        }
        return selected;
    }

    public int getLargestAvailableCraftingBatchSize() {
        List<Integer> capacities = getLocalLaneBatchCapacities();
        if (capacities.isEmpty()) {
            return 0;
        }
        LaneOccupancy laneOccupancy = collectLaneOccupancy(capacities.size());
        Set<Integer> occupied = laneOccupancy.occupied();
        int unassignedBusy = laneOccupancy.unassignedBusy();
        for (int index = 0; index < capacities.size() && unassignedBusy > 0; index++) {
            if (occupied.add(index)) {
                unassignedBusy--;
            }
        }
        int largest = 0;
        for (int index = 0; index < capacities.size(); index++) {
            if (!occupied.contains(index)) {
                largest = Math.max(largest, capacities.get(index));
            }
        }
        return largest;
    }

    public int getLocalMaxBatchPerThread() {
        return getLocalLaneBatchCapacities().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private LaneOccupancy collectLaneOccupancy(int laneCount) {
        Set<Integer> occupied = new HashSet<>();
        int unassignedBusy = 0;
        if (cluster != null) {
            for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
                for (int index : worker.getAssignedLaneIndices()) {
                    if (index >= 0 && index < laneCount) {
                        occupied.add(index);
                    } else {
                        unassignedBusy++;
                    }
                }
                unassignedBusy += worker.getUnassignedBusyTaskCount();
            }
        }
        return new LaneOccupancy(occupied, unassignedBusy);
    }

    private List<Integer> getLocalLaneBatchCapacities() {
        if (cluster == null) {
            return List.of();
        }
        int multiplier = Math.max(1, cluster.getNetworkMultiplier());
        int capacity = calculateWorkerBatchCapacity(
            NEConfig.getCraftingWorkerBaseCrafts(),
            getTier().getOverclockedCrafterQueueMultiply(),
            overclocked,
            multiplier
        );
        List<Integer> capacities = new ArrayList<>();
        cluster.getWorkers().stream()
            .sorted(Comparator.comparing(worker -> worker.getBlockPos().asLong()))
            .forEach(worker -> {
                for (int lane = 0; lane < threadCountPerWorker; lane++) {
                    capacities.add(capacity);
                }
            });
        return List.copyOf(capacities);
    }

    static int calculateWorkerBatchCapacity(
        int baseCrafts,
        int overclockMultiplier,
        boolean overclocked,
        int networkMultiplier
    ) {
        long capacity = Math.max(0L, baseCrafts);
        if (overclocked) {
            capacity = saturatingMultiply(capacity, Math.max(1, overclockMultiplier));
        }
        capacity = saturatingMultiply(capacity, Math.max(1, networkMultiplier));
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    public record CraftingLane(int index, int batchCapacity) {
    }

    private record LaneOccupancy(Set<Integer> occupied, int unassignedBusy) {
    }

    private static long getCoreThreadCountLong(IECOTier coreTier, boolean overclocked) {
        long threads = Math.max(0L, coreTier.getCrafterParallel());
        if (overclocked) {
            threads = saturatingAdd(threads, Math.max(0L, coreTier.getOverclockedCrafterParallel()));
        }
        return threads;
    }

    static int calculateOverclockTimes(long threadCount, long availableThreads) {
        long overflow = threadCount - availableThreads;
        if (threadCount <= 0 || overflow <= 0) {
            return 0;
        }
        double overflowRatio = (double) overflow / (double) threadCount;
        return (int) Math.clamp(Math.round(overflowRatio / 0.05D), 0L, 9L);
    }

    private static long saturatingAdd(long left, long right) {
        if (left <= 0L) {
            return Math.max(0L, right);
        }
        if (right <= 0L) {
            return left;
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    public boolean tryConsumeCoolant(int amount, int requiredOverclock) {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().tryConsumeCoolant(amount, requiredOverclock);
        }
        return tryConsumeLocalCoolant(amount, requiredOverclock);
    }

    public int getActiveNetworkCoolingMultiplier() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkMultiplier()
            : 1;
    }

    public boolean canStartNetworkCooledTask(int multiplier) {
        if (multiplier <= 1 || cluster == null || cluster.getNetworkCluster() == null) {
            return true;
        }
        int requiredOverclock = Math.max(getEffectiveOverclockTimesForLocalTasks(), multiplier >= 8 ? 9 : 0);
        return cluster.getNetworkCluster().getCraftingCoolantCraftLimit(
            1, requiredOverclock, getNetworkCoolantPerSlotTick(multiplier)
        ) >= getNetworkCoolantPerSlotTick(multiplier);
    }

    public boolean tryConsumeNetworkCoolantTick(int multiplier, int ticksSinceLastCall) {
        if (multiplier <= 1 || cluster == null || cluster.getNetworkCluster() == null) {
            return true;
        }
        int ticks = Math.max(1, ticksSinceLastCall);
        int rate = getNetworkCoolantPerSlotTick(multiplier);
        int amount = (int) Math.min(Integer.MAX_VALUE, (long) rate * ticks);
        int requiredOverclock = Math.max(getEffectiveOverclockTimesForLocalTasks(), multiplier >= 8 ? 9 : 0);
        return cluster.getNetworkCluster().tryConsumeCoolant(amount, requiredOverclock);
    }

    private static int getNetworkCoolantPerSlotTick(int multiplier) {
        return multiplier >= 8
            ? HIGH_ENERGY_NETWORK_COOLANT_PER_SLOT_TICK
            : NETWORK_COOLANT_PER_SLOT_TICK;
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
        setChanged();
        markForUpdate();
        if (coolant == 0 && cluster != null && cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().onCoolingAvailabilityChanged();
        }
        return true;
    }

    public int getCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            int multiplier = cluster.getNetworkMultiplier();
            if (multiplier > 1) {
                return canStartNetworkCooledTask(multiplier) ? Math.max(0, requestedCrafts) : 0;
            }
            return cluster.getNetworkCluster().getCraftingCoolantCraftLimit(
                coolantPerCraft, requiredOverclock, requestedCrafts
            );
        }
        return getLocalCraftingCoolantCraftLimit(coolantPerCraft, requiredOverclock, requestedCrafts);
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

    public int getEffectiveOverclockTimes() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getEffectiveOverclockTimes()
            : getLocalEffectiveOverclockTimes();
    }

    public int getCoolingRequirementForCurrentNetwork() {
        int multiplier = cluster == null ? 1 : cluster.getNetworkMultiplier();
        return Math.max(getEffectiveOverclockTimesForLocalTasks(), multiplier >= 8 ? 9 : 0);
    }

    public int getLocalCoolingMaxOverclock() {
        return getCurrentCoolingMaxOverclock();
    }

    public int getDisplayedCoolingMaxOverclock() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getCoolingMaxOverclock()
            : getCurrentCoolingMaxOverclock();
    }

    public void clearCoolant() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().clearCoolant();
            return;
        }
        clearLocalCoolant();
    }

    public void clearLocalCoolant() {
        coolant = 0;
        coolantMaxOverclock = -1;
        currentCoolantFluid = FluidStack.EMPTY;
        setChanged();
        markForUpdate();
    }

    public int getDisplayedCoolantAmount() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getCoolantAmount()
            : coolant;
    }

    public int getDisplayedCoolantCapacity() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getCoolantCapacity()
            : MAX_COOLANT;
    }

    public FluidStack getDisplayedCoolantFluid() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getDisplayedCoolantFluid()
            : currentCoolantFluid.copy();
    }

    public int getOverflowThreads() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getStructuralOverflow(getMainNode().getGrid());
        }
        return getLocalOverflowThreads();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (cluster != null && cluster.isNetworkMode()) {
            NELogicalNetworkManager.refresh(cluster);
        }
    }

    private int getExchangeHostCount() {
        if (cluster == null || cluster.getNetworkCluster() == null || cluster.getNetworkMultiplier() <= 1) {
            return 1;
        }
        return Math.max(1, cluster.getNetworkCluster().getMemberCount());
    }

    public int getLocalOverflowThreads() {
        long overflow = Math.max(0L, exactParallelCapacity - exactWorkerCapacity);
        return (int) Math.min(Integer.MAX_VALUE, overflow);
    }

    public int getAvailableThreads() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getAvailableThreads();
        }
        return getLocalAvailableThreads();
    }

    public int getRecipeSlotCount() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getCraftingSlotCount(getMainNode().getGrid());
        }
        return getAvailableThreads();
    }

    public int getOccupiedRecipeSlots() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getOccupiedCraftingSlots(getMainNode().getGrid());
        }
        return Math.min(getAvailableThreads(), Math.max(0, getRunningThreadCount()));
    }

    public int getLogicalThreadCount() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getRecipeSlotCount(getMainNode().getGrid());
        }
        return formed ? 1 : 0;
    }

    public int getMaxBatchPerThread() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getMaxBatchPerThread(getMainNode().getGrid());
        }
        return getLocalMaxBatchPerThread();
    }

    public int getOccupiedLogicalThreadCount() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getOccupiedRecipeSlots(getMainNode().getGrid());
        }
        return formed && getRunningThreadCount() > 0 ? 1 : 0;
    }

    public int getLocalAvailableThreads() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, exactAvailableThreadCount));
    }

    public long getMaxEnergyUsage() {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            return cluster.getNetworkCluster().getMaxEnergyUsage();
        }
        return getLocalMaxEnergyUsage();
    }

    public long getLocalMaxEnergyUsage() {
        int networkPowerMultiplier = cluster == null ? 1 : cluster.getNetworkPowerMultiplier();
        if (overclocked && !activeCooling) {
            return (long)getLocalAvailableThreads()
                * tier.getOverclockedCrafterPowerMultiply()
                * networkPowerMultiplier
                * 100L;
        }
        return (long)getLocalAvailableThreads() * networkPowerMultiplier * 100L;
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
        long requiredPerTick = (long) getLocalAvailableThreads() * COOLANT_PER_CRAFT;
        if (requiredPerTick <= 0) {
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
        setChanged();
        markForUpdate();
        if (cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().onCoolingAvailabilityChanged();
        }
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

    public void tick(Level level, BlockPos pos, BlockState state) {
        long startNanos = System.nanoTime();
        try {
            tickBuild(level, pos, state);
        } finally {
            recordPerformanceSample(System.nanoTime() - startNanos);
        }
    }

    private void tickBuild(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !buildInProgress || buildSession == null) {
            return;
        }

        ServerPlayer buildPlayer = buildPlayerId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(buildPlayerId);
        if (buildPlayer == null) {
            buildSession = null;
            buildPlayerId = null;
            buildInProgress = false;
            setChanged();
            markForUpdate();
            return;
        }

        switch (MultiBlockPlacementService.tickBuild(serverLevel, buildSession, buildPlayer)) {
            case WAITING, ADVANCED -> {
            }
            case COMPLETED -> {
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                rebuildMultiblock();
                setChanged();
                markForUpdate();
            }
            case BLOCKED -> {
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                setChanged();
                markForUpdate();
            }
        }
    }


    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement buildWindow = buildPanel(holder);

        UIElement root = CraftingHostPanelUI.create(createCraftingPanelConfig());
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(buildWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private CraftingHostPanelUI.Config createCraftingPanelConfig() {
        return new CraftingHostPanelUI.Config(
            () -> getItemFromBlockEntity().getDescription(),
            () -> cluster == null ? 1 : cluster.getNetworkMultiplier(),
            () -> getMainNode().isOnline() && getMainNode().getGrid() != null,
            this::isOverclocked,
            () -> setOverclocked(!isOverclocked()),
            this::isActiveCooling,
            () -> setActiveCooling(!isActiveCooling()),
            this::getOccupiedRecipeSlots,
            this::getRecipeSlotCount,
            this::getMaxBatchPerThread,
            this::getOverflowThreads,
            this::getEffectiveOverclockTimes,
            this::getPerformanceAverageNanos,
            this::getMaxEnergyUsage,
            this::getDisplayedCoolantAmount,
            this::getDisplayedCoolantCapacity,
            this::getDisplayedCoolingMaxOverclock,
            this::getDisplayedCoolantFluid,
            this::getRegistryAccessForUi,
            this::getActiveTaskEntries
        );
    }

    private void setOverclocked(boolean overclocked) {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().setOverclocked(overclocked);
            return;
        }
        setLocalOverclocked(overclocked);
    }

    private void setActiveCooling(boolean activeCooling) {
        if (cluster != null && cluster.getNetworkCluster() != null) {
            cluster.getNetworkCluster().setActiveCooling(activeCooling);
            return;
        }
        setLocalActiveCooling(activeCooling);
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
        List<ECOCraftingWorkerBlockEntity> workers = cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getWorkers()
            : cluster.getWorkers();
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            NECraftingCluster physicalCluster = worker.getCluster();
            ECOCraftingSystemBlockEntity physicalController = physicalCluster == null
                ? null
                : physicalCluster.getController();
            long hostPosition = physicalController == null ? worker.getBlockPos().asLong() : physicalController.getBlockPos().asLong();
            for (ECOCraftingThread.Snapshot snapshot : worker.getThreadSnapshots()) {
                ItemStack output = snapshot.outputItem();
                if (output.isEmpty()) {
                    continue;
                }
                TaskAggregateKey key = new TaskAggregateKey(hostPosition, snapshot.craftingJobId(), output);
                aggregates.computeIfAbsent(key, ignored -> new TaskAggregate(output.copyWithCount(1), hostPosition))
                    .add(snapshot);
            }
        }
        List<ComputationTaskEntry> entries = new ArrayList<>();
        int index = 0;
        for (TaskAggregate aggregate : aggregates.values()) {
            entries.add(aggregate.toEntry(worldPosition, index++));
        }
        return List.copyOf(entries);
    }

    private UIElement buildPanel(BlockUIMenuType.BlockUIHolder holder) {
        return MultiblockBuilderUI.createFloatingPanel(new MultiblockBuilderUI.Config(
            holder.player,
            () -> selectedBuildLength,
            () -> mirrorBuild,
            mirror -> setMirrorBuild(holder.player, mirror),
            () -> decreaseBuildLength(holder.player),
            () -> increaseBuildLength(holder.player),
            () -> autoBuild(holder.player),
            () -> formed,
            () -> buildInProgress,
            this::createLocalPreviewPlan
        ));
    }

    private void increaseBuildLength(Player player) {
        if (buildInProgress) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength + 1, getMinBuildLength(), getMaxBuildLength());
        setChanged();
        markForUpdate();
    }

    private void decreaseBuildLength(Player player) {
        if (buildInProgress) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength - 1, getMinBuildLength(), getMaxBuildLength());
        setChanged();
        markForUpdate();
    }

    private void autoBuild(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (formed) {
            return;
        }
        if (buildInProgress) {
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength, mirrorBuild);
        if (!plan.getConflictPositions().isEmpty()) {
            return;
        }
        if (!serverPlayer.isCreative() && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            return;
        }
        if (plan.getMissingBlocks().isEmpty()) {
            rebuildMultiblock();
            serverPlayer.closeContainer();
            return;
        }
        if (serverPlayer.isCreative()) {
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan, serverPlayer)) {
                return;
            }
            rebuildMultiblock();
            serverPlayer.closeContainer();
            return;
        }
        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUUID();
        buildInProgress = true;
        setChanged();
        markForUpdate();
        serverPlayer.closeContainer();
    }

    private @Nullable MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getCraftingSystemDefinition(tier);
    }

    private int getMinBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMin();
    }

    private int getMaxBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMax();
    }

    private void setMirrorBuild(Player player, boolean mirrorBuild) {
        if (buildInProgress) {
            return;
        }
        this.mirrorBuild = mirrorBuild;
        setChanged();
        markForUpdate();
    }

    private @Nullable MultiBlockPlacementPlan createLocalPreviewPlan() {
        if (level == null || formed) {
            return null;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            return null;
        }
        int buildLength = Math.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        return MultiBlockPlacementService.preview(level, worldPosition, getBlockState(), definition, buildLength, mirrorBuild);
    }

    private Component buildOverclockSummaryComponent() {
        int displayedMaxOverclock = getCurrentCoolingMaxOverclock();
        return Component.translatable(
            "gui.neoecoae.host.crafting.overclock_summary",
            overlockTimes,
            getEffectiveOverclockTimes(),
            displayedMaxOverclock < 0 ? "-" : Tooltips.ofNumber(displayedMaxOverclock)
        );
    }

    private record TaskAggregateKey(long hostPosition, UUID craftingJobId, ItemStack output) {
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
            return hostPosition == that.hostPosition
                && java.util.Objects.equals(craftingJobId, that.craftingJobId)
                && ItemStack.isSameItemSameComponents(output, that.output);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(hostPosition, craftingJobId, output.getItem(), output.getComponents());
        }
    }

    private static final class TaskAggregate {
        private final ItemStack output;
        private final long hostPosition;
        private long outputAmount;
        private long craftCount;
        private long totalProgress;
        private long remainingProgress;
        private boolean waitingOutput = true;

        private TaskAggregate(ItemStack output, long hostPosition) {
            this.output = output;
            this.hostPosition = hostPosition;
        }

        private void add(ECOCraftingThread.Snapshot snapshot) {
            int slots = Math.max(1, snapshot.occupiedThreadSlots());
            int maxProgress = Math.max(1, snapshot.maxProgress());
            int progress = Mth.clamp(snapshot.progress(), 0, maxProgress);
            outputAmount += Math.max(1L, snapshot.outputAmount());
            craftCount += slots;
            totalProgress += (long) maxProgress * slots;
            remainingProgress += (long) Math.max(0, maxProgress - progress) * slots;
            waitingOutput &= snapshot.outputsReady();
        }

        private ComputationTaskEntry toEntry(BlockPos controllerPos, int index) {
            long safeTotal = Math.max(1L, totalProgress);
            long safeRemaining = Math.max(0L, Math.min(safeTotal, remainingProgress));
            float progress = Mth.clamp((safeTotal - safeRemaining) / (float)safeTotal, 0.0F, 1.0F);
            return new ComputationTaskEntry(
                "crafting:" + controllerPos.asLong() + ":" + hostPosition + ":" + index + ":" + output.getItem().hashCode(),
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
                0L
            );
        }
    }
}

