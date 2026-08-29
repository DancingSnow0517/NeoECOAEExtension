package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.config.CpuSelectionMode;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingNetworkSettings;
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
import net.minecraft.network.chat.MutableComponent;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ECOCraftingSystemBlockEntity extends NEBlockEntity<NECraftingCluster, ECOCraftingSystemBlockEntity>
    implements ISyncPersistRPCBlockEntity, IGridTickable, MultiBlockBuildController.Host {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    public static final int MAX_COOLANT = 1_000_000;
    private static final int COOLANT_PER_CRAFT = 5;
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
        updateCount();
        updateThreadCount();
        updateOverlockTimes();
    }

    private void updateThreadCount() {
        if (cluster != null && !cluster.getParallelCores().isEmpty()) {
            int baseCrafts = NEConfig.CRAFTING_WORKER_BASE_CRAFTS;
            long calculatedPerWorker;
            if (overclocked) {
                calculatedPerWorker = NEMath.saturatingMultiply(
                    baseCrafts,
                    getTier().getOverclockedCrafterQueueMultiply()
                );
                threadCountPerWorker = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, calculatedPerWorker));
            } else {
                calculatedPerWorker = Math.max(0L, baseCrafts);
                threadCountPerWorker = (int) Math.min(Integer.MAX_VALUE, calculatedPerWorker);
            }
            exactAvailableThreadCount = cluster.getWorkers()
                .stream()
                .mapToLong(worker -> NEMath.saturatingMultiply(
                    calculatedPerWorker,
                    worker.getCapacityMultiplier()
                ))
                .reduce(0L, NEMath::saturatingAdd);
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
            threadCountPerWorker = 0;
            exactAvailableThreadCount = 0L;
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
        if (cluster == null) {
            runningThreadCount = 0;
            return;
        }

        long recalculated = cluster.getWorkers()
            .stream()
            .mapToLong(ECOCraftingWorkerBlockEntity::getRunningThreads)
            .sum();
        runningThreadCount = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, recalculated));
        setChanged();
    }

    public int getThreadCountForWorker(ECOCraftingWorkerBlockEntity worker) {
        long localCapacity = NEMath.saturatingMultiply(
            Math.max(0L, threadCountPerWorker),
            Math.max(1L, worker.getCapacityMultiplier())
        );
        long networkMultiplier = cluster != null && cluster.getNetworkCluster() != null
            ? Math.max(1L, cluster.getNetworkCluster().getCombinedSwitchMultiplier())
            : 1L;
        long capacity = NEMath.saturatingMultiply(localCapacity, networkMultiplier);
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    public int getLocalThreadCountForWorker(ECOCraftingWorkerBlockEntity worker) {
        long capacity = NEMath.saturatingMultiply(
            Math.max(0L, threadCountPerWorker),
            Math.max(1L, worker.getCapacityMultiplier())
        );
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    private void updateOverlockTimes() {
        overlockTimes = calculateOverclockTimes(exactThreadCount, exactAvailableThreadCount);
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

    private int calculateOverclockTimes(long threadCount, long availableThreads) {
        long overflow = threadCount - availableThreads;
        if (threadCount <= 0 || overflow <= 0) {
            return 0;
        }
        double overflowRatio = (double) overflow / (double) threadCount;
        return (int) Math.clamp(Math.round(overflowRatio / 0.05D), 0L, 9L);
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
        setChanged();
        markForUpdate();
        return true;
    }

    public boolean usesTickBasedCoolant() {
        return false;
    }

    public boolean tryConsumeTickBasedCoolant(
        int occupiedThreadSlots,
        int attemptedProgress,
        int effectiveOverclock
    ) {
        return true;
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
        if (!isOverclocked()) {
            return 0;
        }
        if (!isActiveCooling()) {
            return overlockTimes;
        }
        int coolingMaxOverclock = getDisplayedCoolingMaxOverclock();
        if (coolingMaxOverclock < 0) {
            return 0;
        }
        return Math.min(overlockTimes, coolingMaxOverclock);
    }

    public int getDisplayedCoolingMaxOverclock() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getCoolantMaxOverclock() : getCurrentCoolingMaxOverclock();
    }

    public int getLocalCoolingMaxOverclock() {
        return getCurrentCoolingMaxOverclock();
    }

    private int getOverflowThreads() {
        long overflow = Math.max(0L, exactThreadCount - exactAvailableThreadCount);
        return (int) Math.min(Integer.MAX_VALUE, overflow);
    }

    public int getLocalAvailableThreads() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, exactAvailableThreadCount));
    }

    private int getAvailableThreads() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? Math.max(0, getThreadCount() - getRunningThreadCount())
            : getLocalAvailableThreads();
    }

    /**
     * Returns the combined queue capacity of all workers reachable by this host, <em>before</em> subtracting
     * the threads that are currently running. Callers that need the remaining room subtract
     * {@link #getRunningThreadCount()} themselves, so this must never do it for them.
     */
    public int getTotalWorkerThreadCapacity() {
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getTotalCraftingCapability()
            : getLocalAvailableThreads();
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
        if (overclocked && !activeCooling) {
            return (long) getAvailableThreads() * tier.getOverclockedCrafterPowerMultiply() * 100L;
        }
        return getAvailableThreads() * 100L;
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
        long requiredPerTick = (long) getAvailableThreads() * COOLANT_PER_CRAFT;
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
        return cluster != null && cluster.getNetworkCluster() != null
            ? cluster.getNetworkCluster().getTotalParallelism()
            : threadCount;
    }

    public int getPooledCraftingCapability() {
        return getTotalWorkerThreadCapacity();
    }

    private int getPooledWorkerCount() {
        if (cluster == null || cluster.getNetworkCluster() == null) {
            return getWorkerCount();
        }
        long total = cluster.getNetworkCluster().getMembers().stream()
            .mapToLong(member -> member.getWorkers().size())
            .sum();
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private int getPooledActiveWorkerCount() {
        if (cluster == null || cluster.getNetworkCluster() == null) {
            return cluster == null ? 0 : (int) cluster.getWorkers().stream()
                .filter(worker -> worker.getRunningThreads() > 0)
                .count();
        }
        long total = cluster.getNetworkCluster().getMembers().stream()
            .flatMap(member -> member.getWorkers().stream())
            .filter(worker -> worker.getRunningThreads() > 0)
            .count();
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private int getSingleCoreProcessingCapacity() {
        long capacity = getSingleCoreProcessingCapacityLong();
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    private long getSingleCoreProcessingCapacityLong() {
        if (isFullVirtualCraftingMode()) {
            return Long.MAX_VALUE;
        }
        long networkMultiplier = cluster != null && cluster.getNetworkCluster() != null
            ? Math.max(1L, cluster.getNetworkCluster().getCombinedSwitchMultiplier())
            : 1L;
        return NEMath.saturatingMultiply(Math.max(0L, threadCountPerWorker), networkMultiplier);
    }

    private boolean isFullVirtualCraftingMode() {
        return cluster != null
            && cluster.getNetworkCluster() != null
            && cluster.getNetworkCluster().isEndgameEligible();
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
        MutableComponent tooltip = CraftingHostStatsText.detailTitle().copy();
        long totalCapacity = 0L;
        if (cluster != null && cluster.getNetworkCluster() != null) {
            for (NECraftingCluster member : cluster.getNetworkCluster().getMembers()) {
                ECOCraftingSystemBlockEntity controller = member.getController();
                if (controller == null) {
                    continue;
                }
                long capacity = isFullVirtualCraftingMode()
                    ? Long.MAX_VALUE
                    : NEMath.saturatingMultiply(
                        Math.max(0L, controller.threadCountPerWorker),
                        Math.max(1L, cluster.getNetworkCluster().getCombinedSwitchMultiplier())
                    );
                int threads = controller.getLocalThreadCount();
                totalCapacity = NEMath.saturatingAdd(totalCapacity,
                    NEMath.saturatingMultiply(threads, capacity));
                tooltip.append("\n").append(CraftingHostStatsText.hostLine(
                    controller.hasHighEnergyNetworkSwitch(), threads, capacity));
            }
        } else {
            int threads = getLocalThreadCount();
            long capacity = getSingleCoreProcessingCapacityLong();
            totalCapacity = NEMath.saturatingMultiply(threads, capacity);
            tooltip.append("\n").append(CraftingHostStatsText.hostLine(
                hasHighEnergyNetworkSwitch(), threads, capacity));
        }
        tooltip.append("\n").append(CraftingHostStatsText.totalLine(totalCapacity));
        return tooltip;
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
            () -> cluster == null ? 1 : cluster.getNetworkMultiplier(),
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
        for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
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
    public void setSelectedBuildLength(int length) { selectedBuildLength = length; }

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

        private TaskAggregate(ItemStack output) {
            this.output = output;
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
                0L
            );
        }
    }
}

