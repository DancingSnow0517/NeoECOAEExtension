package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingCapacity;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.network.NECraftingUiState;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingSystemBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingSystemBlockEntity>
        implements IGridTickable, INEMultiblockBuildHost {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final boolean DEBUG_THREAD_COUNT = Boolean.getBoolean("neoecoae.debugEcoCraftingThreadCount");

    /**
     * Internal coolant cache maximum — the crafting controller's own cooling
     * buffer, <em>not</em> the fluid hatch tank capacity.
     * Maintains the 1.21.1 value of 1,000,000.
     */
    public static final int MAX_COOLANT = 1_000_000;

    private static final int COOLANT_PER_CRAFT = 5;

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

    private int patternBusCount, parallelCount, workerCount = 0;

    private int runningThreadCount = 0;

    private int threadCount = 0;

    private int threadCountPerWorker = 0;

    private int overlockTimes = 0;
    private boolean structureStatsDirty = true;
    private long uiRevision = 0L;
    private int selectedBuildLength = 1;
    private int previewMissingBlocks;
    private int previewConflictBlocks;
    private int previewReusedBlocks;
    private int previewRequiredItems;
    private String previewStatusKey = "gui.neoecoae.multiblock.status.idle";
    private int previewStatusArg1;
    private int previewStatusArg2;
    private boolean buildInProgress;
    private transient MultiBlockBuildSession buildSession;
    private transient UUID buildPlayerId;
    private long lastCoolantConsumeDirtyTick = Long.MIN_VALUE;
    private long lastThreadCountValidationTick = Long.MIN_VALUE;

    public ECOCraftingSystemBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
        getMainNode().addService(IGridTickable.class, this);
    }

    // ── NBT persistence ──

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("overclocked", overclocked);
        tag.putBoolean("activeCooling", activeCooling);
        tag.putBoolean("autoClearCoolingWaste", autoClearCoolingWaste);
        tag.putInt("coolant", coolant);
        tag.putInt("coolantMaxOverclock", coolantMaxOverclock);
        tag.putInt("selectedBuildLength", selectedBuildLength);
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
        selectedBuildLength = tag.getInt("selectedBuildLength");
        if (selectedBuildLength < 1) selectedBuildLength = 1;
        // Safety: build session is transient; reset in-progress state
        buildInProgress = false;
        previewMissingBlocks = 0;
        previewConflictBlocks = 0;
        previewReusedBlocks = 0;
        previewRequiredItems = 0;
        previewStatusKey = "gui.neoecoae.multiblock.status.idle";
        previewStatusArg1 = 0;
        previewStatusArg2 = 0;
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

    private void updateInfo() {
        markStructureStatsDirty();
        ensureCraftingStatsCurrent();
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
        }
    }

    public void recalculateRunningThreadCountFromWorkers() {
        if (cluster == null) {
            runningThreadCount = 0;
            return;
        }

        runningThreadCount = cluster.getWorkers().stream()
                .mapToInt(ECOCraftingWorkerBlockEntity::getRunningThreads)
                .sum();
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

    private double getOverflowThreadsPercentage() {
        ensureCraftingStatsCurrent();
        double totalThread = threadCount;
        return totalThread > 0 ? getOverflowThreads() / totalThread : 0.0;
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

    private void validateRunningThreadCount() {
        if (!DEBUG_THREAD_COUNT || cluster == null) {
            return;
        }
        long currentTick = TickHandler.instance().getCurrentTick();
        if (currentTick == lastThreadCountValidationTick) {
            return;
        }
        lastThreadCountValidationTick = currentTick;
        int actual = cluster.getWorkers().stream()
                .mapToInt(ECOCraftingWorkerBlockEntity::getRunningThreads)
                .sum();
        if (actual != runningThreadCount) {
            LOGGER.warn(
                    "ECO controller runningThreadCount mismatch: controller={} cached={} actual={} corrected=true",
                    getBlockPos(),
                    runningThreadCount,
                    actual);
            runningThreadCount = actual;
        }
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

    public Component getPreviewStatusComponent() {
        return buildPreviewStatusComponent();
    }

    // ── INEMultiblockBuildHost implementation ──

    @Override
    public BlockPos getHostPos() {
        return worldPosition;
    }

    @Override
    public BlockState getHostBlockState() {
        return getBlockState();
    }

    @Override
    public int getSelectedBuildLength() {
        return selectedBuildLength;
    }

    @Override
    public void setSelectedBuildLength(int length) {
        this.selectedBuildLength = net.minecraft.util.Mth.clamp(length, getMinBuildLength(), getMaxBuildLength());
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
    public boolean isBuildInProgress() {
        return buildInProgress;
    }

    @Override
    public boolean isFormed() {
        return formed;
    }

    public NEStructureTerminalUiState createBuildUiState() {
        MultiBlockDefinition def = getBuildDefinition();
        return new NEStructureTerminalUiState(
                worldPosition,
                def != null ? def.getName().getString() : "",
                formed,
                buildInProgress,
                selectedBuildLength,
                getMinBuildLength(),
                getMaxBuildLength(),
                previewMissingBlocks,
                previewConflictBlocks,
                previewReusedBlocks,
                previewRequiredItems,
                buildSession != null ? buildSession.getPlacedBlockCount() : 0,
                buildSession != null ? buildSession.getTotalBlocks() : 0,
                previewStatusKey,
                previewStatusArg1,
                previewStatusArg2,
                List.of());
    }

    public void sendBuildUiState(ServerPlayer player) {
        NEStructureTerminalUiState state = createBuildUiState();
        NENetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new NENetwork.NEStructureTerminalUiStatePacket(state));
    }

    @Override
    public void previewStructure(ServerPlayer player, int buildLength) {
        previewStructure(player, buildLength, false);
    }

    @Override
    public void previewStructure(ServerPlayer player, int buildLength, boolean mirrored) {
        setSelectedBuildLength(buildLength);
        previewStructure(player, mirrored);
    }

    @Override
    public void autoBuild(ServerPlayer player, int buildLength) {
        autoBuild(player, buildLength, false);
    }

    @Override
    public void autoBuild(ServerPlayer player, int buildLength, boolean mirrored) {
        setSelectedBuildLength(buildLength);
        autoBuild(player, mirrored);
    }

    @Override
    public void dismantle(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        player.closeContainer();
        boolean dismantled = MultiBlockPlacementService.dismantle(serverLevel, this, player);
        syncPreview(
                0,
                0,
                0,
                0,
                dismantled
                        ? "gui.neoecoae.multiblock.status.dismantled"
                        : "gui.neoecoae.multiblock.status.dismantle_failed");
    }

    public int getPreviewMissingBlocks() {
        return previewMissingBlocks;
    }

    public int getPreviewConflictBlocks() {
        return previewConflictBlocks;
    }

    public int getPreviewReusedBlocks() {
        return previewReusedBlocks;
    }

    public int getPreviewRequiredItems() {
        return previewRequiredItems;
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

        int totalParallelism = threadCount; // FT 理论并行
        int availThreads = getAvailableThreads(); // FX 工作核心承载上限
        int effParallel = Math.min(totalParallelism, availThreads); // 实际有效并行

        // Collect active craft outputs from each worker
        List<ItemStack> craftOutputs = new ArrayList<>();
        // Collect tier level (1/2/3 = L4/L6/L9) for each parallel core
        List<Integer> coreTiers = new ArrayList<>();
        if (cluster != null) {
            for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
                craftOutputs.add(worker.getActiveCraftOutput());
            }
            for (ECOCraftingParallelCoreBlockEntity core : cluster.getParallelCores()) {
                coreTiers.add(core.getTier().getTier());
            }
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
                previewStatusKey,
                previewStatusArg1,
                previewStatusArg2,
                getCurrentEnergyPerTick(),
                coolant,
                MAX_COOLANT,
                availThreads,
                effParallel,
                craftOutputs,
                coreTiers);
    }

    private long getMaxEnergyUsage() {
        if (overclocked && !activeCooling) {
            return getAvailableThreads() * tier.getOverclockedCrafterPowerMultiply() * 100L;
        }
        return getAvailableThreads() * 100L;
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

    private int getRequiredCoolingOverclock() {
        return getEffectiveOverclockTimes();
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
        if (!(level instanceof ServerLevel serverLevel) || !buildInProgress || buildSession == null) {
            return;
        }

        ServerPlayer buildPlayer = buildPlayerId == null
                ? null
                : serverLevel.getServer().getPlayerList().getPlayer(buildPlayerId);
        if (buildPlayer == null) {
            int remainingBlocks = buildSession.getRemainingBlockCount();
            buildSession = null;
            buildPlayerId = null;
            buildInProgress = false;
            syncPreview(
                    remainingBlocks,
                    0,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.builder_unavailable");
            return;
        }

        switch (MultiBlockPlacementService.tickBuild(serverLevel, buildSession, buildPlayer)) {
            case WAITING -> {}
            case ADVANCED -> syncPreview(
                    buildSession.getRemainingBlockCount(),
                    0,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.building",
                    buildSession.getPlacedBlockCount(),
                    buildSession.getTotalBlocks());
            case COMPLETED -> {
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                rebuildMultiblock();
                syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            }
            case BLOCKED -> {
                int remainingBlocks = buildSession.getRemainingBlockCount();
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                syncPreview(
                        remainingBlocks,
                        1,
                        previewReusedBlocks,
                        previewRequiredItems,
                        "gui.neoecoae.multiblock.status.build_interrupted");
            }
        }
    }

    public void increaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        setSelectedBuildLength(selectedBuildLength + 1);
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    public void decreaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        setSelectedBuildLength(selectedBuildLength - 1);
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    @Override
    public void previewStructure(ServerPlayer player) {
        previewStructure(player, false);
    }

    public void previewStructure(ServerPlayer player, boolean mirrored) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (formed) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }
        if (buildInProgress && buildSession != null) {
            syncPreview(
                    buildSession.getRemainingBlockCount(),
                    0,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.building",
                    buildSession.getPlacedBlockCount(),
                    buildSession.getTotalBlocks());
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }
        setSelectedBuildLength(selectedBuildLength);
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
                serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength, mirrored);
        boolean hasMaterials = MultiBlockPlacementService.hasRequiredItems(player, plan.getRequiredItems());
        String statusKey = plan.getConflictPositions().isEmpty()
                ? (plan.getMissingBlocks().isEmpty()
                        ? "gui.neoecoae.multiblock.status.structure_ready"
                        : (hasMaterials
                                ? "gui.neoecoae.multiblock.status.ready_to_build"
                                : "gui.neoecoae.multiblock.status.not_enough_items"))
                : "gui.neoecoae.multiblock.status.conflicts_detected";
        syncPreview(
                plan.getMissingBlocks().size(),
                plan.getConflictPositions().size(),
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                statusKey);
    }

    @Override
    public void autoBuild(ServerPlayer serverPlayer) {
        autoBuild(serverPlayer, false);
    }

    public void autoBuild(ServerPlayer serverPlayer, boolean mirrored) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverPlayer.closeContainer();
        if (formed) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }
        if (buildInProgress) {
            syncPreview(
                    previewMissingBlocks,
                    previewConflictBlocks,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.build_already_in_progress");
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }
        selectedBuildLength =
                net.minecraft.util.Mth.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
                serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength, mirrored);
        if (!plan.getConflictPositions().isEmpty()) {
            syncPreview(
                    plan.getMissingBlocks().size(),
                    plan.getConflictPositions().size(),
                    plan.getReusedBlockCount(),
                    plan.getRequiredItemCount(),
                    "gui.neoecoae.multiblock.status.conflicts_detected");
            return;
        }
        if (!serverPlayer.isCreative()
                && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            syncPreview(
                    plan.getMissingBlocks().size(),
                    0,
                    plan.getReusedBlockCount(),
                    plan.getRequiredItemCount(),
                    "gui.neoecoae.multiblock.status.not_enough_items");
            return;
        }
        if (plan.getMissingBlocks().isEmpty()) {
            rebuildMultiblock();
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            return;
        }
        if (serverPlayer.isCreative()) {
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan)) {
                syncPreview(
                        plan.getMissingBlocks().size(),
                        plan.getConflictPositions().size(),
                        plan.getReusedBlockCount(),
                        plan.getRequiredItemCount(),
                        "gui.neoecoae.multiblock.status.build_failed");
                return;
            }
            rebuildMultiblock();
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            return;
        }
        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUUID();
        buildInProgress = true;
        syncPreview(
                plan.getMissingBlocks().size(),
                0,
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                "gui.neoecoae.multiblock.status.building",
                buildSession.getPlacedBlockCount(),
                buildSession.getTotalBlocks());
    }

    @Nullable public MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getCraftingSystemDefinition(tier);
    }

    private void resetPreview(String statusKey) {
        syncPreview(0, 0, 0, 0, statusKey);
    }

    private void syncPreview(
            int missingBlocks, int conflictBlocks, int reusedBlocks, int requiredItems, String statusKey) {
        syncPreview(missingBlocks, conflictBlocks, reusedBlocks, requiredItems, statusKey, 0, 0);
    }

    private void syncPreview(
            int missingBlocks,
            int conflictBlocks,
            int reusedBlocks,
            int requiredItems,
            String statusKey,
            int statusArg1,
            int statusArg2) {
        previewMissingBlocks = missingBlocks;
        previewConflictBlocks = conflictBlocks;
        previewReusedBlocks = reusedBlocks;
        previewRequiredItems = requiredItems;
        previewStatusKey = statusKey;
        previewStatusArg1 = statusArg1;
        previewStatusArg2 = statusArg2;
        setChanged();
        markUiStateDirty();
    }

    private Component buildPreviewStatusComponent() {
        if ("gui.neoecoae.multiblock.status.building".equals(previewStatusKey)) {
            return Component.translatable(previewStatusKey, previewStatusArg1, previewStatusArg2);
        }
        return Component.translatable(previewStatusKey);
    }

    private Component buildCoolantSupportComponent() {
        int displayedMaxOverclock = getCurrentCoolingMaxOverclock();
        if (displayedMaxOverclock < 0) {
            return Component.translatable("gui.neoecoae.crafting.coolant_max_overclock.none");
        }
        return Component.translatable("gui.neoecoae.crafting.coolant_max_overclock", displayedMaxOverclock);
    }

    private Component buildOverclockStatusComponent() {
        if (!overclocked) {
            return Component.translatable("gui.neoecoae.crafting.overclock_status.disabled");
        }
        return Component.translatable(
                "gui.neoecoae.crafting.overclock_status", overlockTimes, getEffectiveOverclockTimes());
    }

    private int getDisplayedCoolingRecipeMaxOverclock() {
        CoolingRecipe recipe = getCoolingRecipe();
        return recipe == null ? -1 : recipe.maxOverclock();
    }

    // ── Client sync via BE update tags (chunk load / block update) ──

    @Override
    public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag();
        writeUiSyncTag(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        readUiSyncTag(tag);
    }

    @Override
    @Nullable public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void writeUiSyncTag(CompoundTag tag) {
        tag.putBoolean("overclocked", overclocked);
        tag.putBoolean("activeCooling", activeCooling);
        tag.putBoolean("autoClearCoolingWaste", autoClearCoolingWaste);
        tag.putInt("coolant", coolant);
        tag.putInt("coolantMaxOverclock", coolantMaxOverclock);
        tag.putInt("selectedBuildLength", selectedBuildLength);
        tag.putInt("patternBusCount", patternBusCount);
        tag.putInt("parallelCount", parallelCount);
        tag.putInt("workerCount", workerCount);
        tag.putInt("threadCount", threadCount);
        tag.putInt("runningThreadCount", runningThreadCount);
        tag.putInt("previewMissingBlocks", previewMissingBlocks);
        tag.putInt("previewConflictBlocks", previewConflictBlocks);
        tag.putInt("previewReusedBlocks", previewReusedBlocks);
        tag.putInt("previewRequiredItems", previewRequiredItems);
        tag.putString(
                "previewStatusKey",
                previewStatusKey != null ? previewStatusKey : "gui.neoecoae.multiblock.status.idle");
        tag.putInt("previewStatusArg1", previewStatusArg1);
        tag.putInt("previewStatusArg2", previewStatusArg2);
        tag.putBoolean("buildInProgress", buildInProgress && buildSession != null);
    }

    private void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("overclocked")) overclocked = tag.getBoolean("overclocked");
        if (tag.contains("activeCooling")) activeCooling = tag.getBoolean("activeCooling");
        if (tag.contains("autoClearCoolingWaste")) autoClearCoolingWaste = tag.getBoolean("autoClearCoolingWaste");
        if (tag.contains("coolant")) coolant = Mth.clamp(tag.getInt("coolant"), 0, MAX_COOLANT);
        if (tag.contains("coolantMaxOverclock")) coolantMaxOverclock = tag.getInt("coolantMaxOverclock");
        else coolantMaxOverclock = -1;
        if (tag.contains("selectedBuildLength")) selectedBuildLength = tag.getInt("selectedBuildLength");
        if (tag.contains("patternBusCount")) patternBusCount = tag.getInt("patternBusCount");
        if (tag.contains("parallelCount")) parallelCount = tag.getInt("parallelCount");
        if (tag.contains("workerCount")) workerCount = tag.getInt("workerCount");
        if (tag.contains("threadCount")) threadCount = tag.getInt("threadCount");
        if (tag.contains("runningThreadCount")) runningThreadCount = tag.getInt("runningThreadCount");
        if (tag.contains("previewMissingBlocks")) previewMissingBlocks = tag.getInt("previewMissingBlocks");
        if (tag.contains("previewConflictBlocks")) previewConflictBlocks = tag.getInt("previewConflictBlocks");
        if (tag.contains("previewReusedBlocks")) previewReusedBlocks = tag.getInt("previewReusedBlocks");
        if (tag.contains("previewRequiredItems")) previewRequiredItems = tag.getInt("previewRequiredItems");
        if (tag.contains("previewStatusKey")) previewStatusKey = tag.getString("previewStatusKey");
        if (tag.contains("previewStatusArg1")) previewStatusArg1 = tag.getInt("previewStatusArg1");
        if (tag.contains("previewStatusArg2")) previewStatusArg2 = tag.getInt("previewStatusArg2");
        if (tag.contains("buildInProgress")) buildInProgress = tag.getBoolean("buildInProgress");
        if (buildInProgress && buildSession == null) {
            buildInProgress = false;
        }
    }
}
