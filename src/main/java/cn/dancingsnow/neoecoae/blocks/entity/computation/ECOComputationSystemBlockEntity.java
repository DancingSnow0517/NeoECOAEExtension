package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.IECOComputationHost;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.api.me.ElapsedTimeTracker;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEBlockEntityUIHolder;
import cn.dancingsnow.neoecoae.multiblock.BuildPreviewState;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class ECOComputationSystemBlockEntity extends AbstractComputationBlockEntity<ECOComputationSystemBlockEntity>
        implements INEMultiblockBuildHost, NEBlockEntityUIHolder, IECOComputationHost {

    @Getter
    private final IECOTier tier;

    private int usedThread;
    private int totalThread;
    private int parallelCount;
    private long availableBytes;
    private long totalBytes;
    /** Sum of CPU accelerators from all parallel cores in the cluster. */
    private int acceleratorCount;
    /** Maximum accelerators allowed by the current upgrade and parallel cores. */
    private int acceleratorLimit;

    /** CPU auto-selection mode, persisted in the controller's NBT. */
    private CpuSelectionMode cpuSelectionMode = CpuSelectionMode.ANY;

    /** Persisted logical-network channel; unassigned hosts receive one on first grid join. */
    private int networkFrequency = NEFrequencyAllocator.UNASSIGNED;

    /** Configured parallel accelerators, persisted independently from the upgrade slot. */
    private int parallelAccelerators = NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS;

    private final ItemStackHandler computationUpgradeHandler = new ItemStackHandler(1) {
        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return NEComputationUpgradeRules.requiredCount(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return NEComputationUpgradeRules.INFINITE_COMPONENT_COUNT;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return NEComputationUpgradeRules.isAllowedItem(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            onComputationUpgradeSlotChanged();
        }
    };

    private boolean computationStatsDirty = true;
    /** Shared preview/build state, delegates NBT sync to {@link BuildPreviewState}. */
    private final BuildPreviewState buildPreview = new BuildPreviewState();

    private long uiRevision = 0L;

    /**
     * Counts only edits the player can see: the upgrade slot, the accelerator setting and its limit,
     * the CPU selection mode. Kept apart from {@link #uiRevision}, which also moves with the storage
     * byte churn of running jobs -- an open screen watches this one to sync promptly, and following
     * the churny counter instead would re-encode the whole state, recipe list included, nearly every
     * tick of an active cluster.
     */
    private long configRevision = 0L;

    public ECOComputationSystemBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
        getMainNode().addService(IECOComputationHost.class, this);
    }

    @Override
    public ECOComputationSystemBlockEntity getComputationHost() {
        return this;
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ECOComputationSystem.NETWORK_SWITCH)
                    && state.hasProperty(ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH)) {
                boolean highEnergy = formed && cluster != null && cluster.isHighEnergyNetworkMode();
                BlockState updated = state.setValue(
                                ECOComputationSystem.NETWORK_SWITCH,
                                formed && cluster != null && cluster.isNetworkMode() && !highEnergy)
                        .setValue(ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH, highEnergy);
                if (!state.equals(updated)) {
                    level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
                }
            }
            if (level instanceof ServerLevel serverLevel) {
                if (formed && cluster != null && cluster.isNetworkMode()) {
                    NENetworkSwitchUtil.syncFormed(serverLevel, worldPosition, getBlockState(), cluster.isMirrored());
                } else {
                    NENetworkSwitchUtil.clearFormed(serverLevel, worldPosition, getBlockState());
                }
            }
        }
        if (updateExposed) {
            markComputationStatsDirty();
            updateInfos();
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        markUiStateDirty();
        if (reason != IGridNodeListener.State.GRID_BOOT
                && cluster != null
                && getMainNode().isActive()) {
            cluster.updateGridForChangedCpu(cluster);
        }
    }

    private void recalculateComputationStats() {
        if (cluster != null) {
            availableBytes = cluster.getAvailableStorage();
            totalBytes = cluster.getTotalStorageBytes();
            usedThread = cluster.getActiveCpuCountCached();
            totalThread = cluster.getMaxThreads();
            parallelCount = cluster.getParallelCores().size();
            acceleratorCount = cluster.getCPUAccelerators();
            acceleratorLimit = cluster.getCPUAcceleratorLimit();
        } else {
            usedThread = 0;
            totalThread = 0;
            parallelCount = 0;
            availableBytes = 0;
            totalBytes = 0;
            acceleratorCount = 0;
            acceleratorLimit = hasInfiniteCapacityUpgrade() ? NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS : 0;
        }
    }

    /**
     * Marks the cached computation stats (thread/byte/accelerator counts)
     * as stale and increments the UI revision to trigger a menu state resync.
     * Call this when the multiblock cluster changes or threading cores update.
     */
    public void markComputationStatsDirty() {
        computationStatsDirty = true;
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

    /** Returns a counter that moves only on player-visible configuration edits. */
    public long getConfigRevision() {
        return configRevision;
    }

    /** Flags a configuration edit the open screen should learn about on the next tick, not in a second. */
    private void markConfigDirty() {
        configRevision++;
        markUiStateDirty();
    }

    private void ensureStatsCurrent() {
        if (!computationStatsDirty) {
            return;
        }
        recalculateComputationStats();
        computationStatsDirty = false;
    }

    public void updateInfos() {
        ensureStatsCurrent();
        setChanged();
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        tickBuild(level);
        if (cluster != null) {
            cluster.tick();
        }
    }

    public int getUsedThread() {
        ensureStatsCurrent();
        return usedThread;
    }

    public boolean isFormed() {
        return formed;
    }

    public boolean isRunning() {
        return getUsedThread() > 0;
    }

    public int getTotalThread() {
        ensureStatsCurrent();
        return totalThread;
    }

    public int getParallelCount() {
        ensureStatsCurrent();
        return parallelCount;
    }

    public long getAvailableBytes() {
        ensureStatsCurrent();
        return availableBytes;
    }

    public long getTotalBytes() {
        ensureStatsCurrent();
        return totalBytes;
    }

    public int getAcceleratorCount() {
        ensureStatsCurrent();
        return acceleratorCount;
    }

    public CpuSelectionMode getCpuSelectionMode() {
        return cpuSelectionMode;
    }

    public void setCpuSelectionMode(CpuSelectionMode mode) {
        this.cpuSelectionMode = mode;
        setChanged();
        markConfigDirty();
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
        markConfigDirty();
        markForUpdate();
    }

    public void cycleNetworkFrequency() {
        setNetworkFrequency(hasNetworkFrequency() ? getNetworkFrequency() + 1 : 0);
    }

    public void setNetworkFrequency(int frequency) {
        int next = NEFrequencyAllocator.normalize(frequency);
        if (networkFrequency == next) {
            return;
        }
        networkFrequency = next;
        setChanged();
        markConfigDirty();
        markForUpdate();
        if (cluster != null && cluster.isNetworkMode()) {
            NELogicalNetworkManager.refresh(cluster);
        }
    }

    public int getParallelAccelerators() {
        return Math.min(parallelAccelerators, getParallelAcceleratorLimit());
    }

    public boolean hasInfiniteCapacityUpgrade() {
        return NEComputationUpgradeRules.hasInfiniteCapacity(computationUpgradeHandler.getStackInSlot(0));
    }

    public int getParallelAcceleratorLimit() {
        ensureStatsCurrent();
        return Math.max(0, Math.min(NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS, acceleratorLimit));
    }

    /** Returns the persisted value without applying the current upgrade limit. */
    public int getConfiguredParallelAccelerators() {
        return Math.max(0, parallelAccelerators);
    }

    /**
     * Applies a validated server-side parallel setting from the computation UI.
     * Immediately delegates to the cluster for application.
     */
    public boolean setParallelAccelerators(int value) {
        if (level == null || level.isClientSide) {
            return false;
        }
        if (cluster != null) {
            // Cluster validates and applies immediately
            if (!cluster.setConfiguredAccelerators(value)) {
                return false;
            }
            // Cluster will call onClusterAcceleratorsChanged to persist
            return true;
        } else {
            // No cluster: just store the configuration for when it forms
            if (value < 0 || value > NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS) {
                return false;
            }
            parallelAccelerators = value;
            setChanged();
            markConfigDirty();
            return true;
        }
    }

    /**
     * Called by the cluster when accelerators are changed via setConfiguredAccelerators.
     * Persists the value and marks UI dirty.
     */
    public void onClusterAcceleratorsChanged(int value) {
        parallelAccelerators = value;
        setChanged();
        markConfigDirty();
    }

    /**
     * Called by the cluster when the accelerator limit changes due to upgrade or structure changes.
     * Marks UI dirty to update the widget's max value.
     */
    public void onClusterLimitChanged(int newLimit) {
        markConfigDirty();
    }

    public IItemHandler getComputationUpgradeItemHandler() {
        return computationUpgradeHandler;
    }

    public void onComputationUpgradeSlotChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        ItemStack upgrade = computationUpgradeHandler.getStackInSlot(0);
        boolean isValidUpgrade = NEComputationUpgradeRules.isValid(upgrade);

        markComputationStatsDirty();
        // The slot is a configuration edit in its own right. The cluster paths below usually flag one
        // too, but not when the upgrade only changes something the value and limit don't cover.
        markConfigDirty();
        if (cluster != null) {
            // Store old limit before recalculation
            int oldLimit = cluster.getCPUAcceleratorLimit();
            int oldConfigured = cluster.getConfiguredAccelerators();

            // Cluster recalculates limit immediately
            cluster.recalculateAcceleratorLimit();

            int newLimit = cluster.getCPUAcceleratorLimit();

            // A player parked at the maximum is asking for everything the structure can do, not for
            // that particular number, so track the limit when an upgrade moves it. Without this, a
            // field generator raises the ceiling but leaves the value at the old ceiling, and the
            // upgrade looks like it did nothing.
            boolean wasAtLimit = oldLimit > 0 && oldConfigured >= oldLimit;

            // Auto-set to max when:
            // 1. Inserting a valid upgrade into an empty slot (oldLimit was 0)
            // 2. Inserting infinite capacity upgrade
            // 3. Current config is 0 (first time setup)
            // 4. The configuration was pinned to the previous maximum
            if ((oldLimit == 0 && isValidUpgrade)
                    || NEComputationUpgradeRules.hasInfiniteCapacity(upgrade)
                    || parallelAccelerators == 0
                    || wasAtLimit) {
                cluster.setConfiguredAccelerators(newLimit);
            }
        } else {
            // No cluster: calculate limit and auto-set to max if needed
            int newLimit = getParallelAcceleratorLimit();

            if (isValidUpgrade || NEComputationUpgradeRules.hasInfiniteCapacity(upgrade) || parallelAccelerators == 0) {
                parallelAccelerators = newLimit;
            } else {
                parallelAccelerators = Math.min(parallelAccelerators, newLimit);
            }
        }
        updateInfos();
        setChanged();
        markForUpdate();
    }

    /**
     * Creates a snapshot of current computation stats for S2C UI sync.
     * <p>
     * This reads cached stats. Mutating cluster paths mark the cache dirty and
     * update it before bumping the UI revision.
     * </p>
     */
    public NEComputationUiState createComputationUiState() {
        ensureStatsCurrent();
        CpuSelectionMode mode = cluster != null ? cluster.getSelectionMode() : cpuSelectionMode;
        var network = cluster == null ? null : cluster.getNetworkCluster();
        int networkMemberCount = network == null ? (formed ? 1 : 0) : network.getMemberCount();
        int networkMultiplier = cluster == null ? 1 : cluster.getNetworkMultiplier();
        boolean networkConnected = isMainNodeConnected();
        return new NEComputationUiState(
                worldPosition,
                formed,
                cluster != null && cluster.isActive(),
                networkMemberCount,
                networkMultiplier,
                networkConnected,
                getNetworkFrequency(),
                usedThread,
                totalThread,
                availableBytes,
                totalBytes,
                parallelCount,
                acceleratorCount,
                acceleratorLimit,
                getParallelAccelerators(),
                hasInfiniteCapacityUpgrade(),
                mode,
                collectComputationRecipeEntries());
    }

    private boolean isMainNodeConnected() {
        try {
            return getMainNode().isOnline() && getMainNode().getGrid() != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private List<NECraftingRecipeUiEntry> collectComputationRecipeEntries() {
        if (cluster == null) {
            return List.of();
        }
        if (cluster.getActiveCpuCountCached() <= 0) {
            return List.of();
        }
        List<NECraftingRecipeUiEntry> entries = new ArrayList<>();
        int index = 0;
        for (ECOCraftingCPU cpu : cluster.activeCpusView()) {
            NECraftingRecipeUiEntry entry = createComputationRecipeEntry(cpu, index);
            if (entry != null) {
                entries.add(entry);
            }
            index++;
        }
        return List.copyOf(entries);
    }

    @Nullable private NECraftingRecipeUiEntry createComputationRecipeEntry(ECOCraftingCPU cpu, int index) {
        if (cpu == null) {
            return null;
        }
        ECOCraftingCPULogic logic = cpu.getLogic();
        if (!logic.hasJob()) {
            return null;
        }
        GenericStack finalOutput = logic.getFinalJobOutput();
        long remainingAmount = logic.getRemainingJobOutputAmount();
        if (finalOutput == null || remainingAmount <= 0) {
            return null;
        }
        ItemStack output = finalOutput.what().wrapForDisplayOrFilter();
        if (output.isEmpty()) {
            return null;
        }
        ElapsedTimeTracker tracker = logic.getElapsedTimeTracker();
        long total = Math.max(1L, tracker.getSyntheticStartItemCount());
        long remaining = Math.max(0L, Math.min(total, tracker.getSyntheticRemainingItemCount()));
        NECraftingRecipeUiEntry.Status status =
                logic.isCantStoreItems() || logic.isJobSuspended() || logic.isJobUserPaused()
                        ? NECraftingRecipeUiEntry.Status.WAITING_OUTPUT
                        : NECraftingRecipeUiEntry.Status.RUNNING;
        return new NECraftingRecipeUiEntry(
                computationTaskId(cpu, finalOutput, index),
                output,
                remainingAmount,
                1L,
                total,
                remaining,
                status,
                cpu.getName() != null ? cpu.getName().getString() : cpu.getTier() + " ECO CPU",
                Math.max(0L, cpu.getAvailableStorage()),
                Math.max(0, cpu.getCoProcessors()),
                Math.max(0L, finalOutput.amount()),
                TimeUnit.SECONDS.toNanos(TimeUnit.NANOSECONDS.toSeconds(Math.max(0L, tracker.getElapsedTime()))));
    }

    private static String computationTaskId(ECOCraftingCPU cpu, GenericStack output, int index) {
        BlockPos ownerPos = cpu.getOwner() != null ? cpu.getOwner().getBlockPos() : null;
        String owner = ownerPos != null ? Long.toString(ownerPos.asLong()) : "proxy";
        return "cpu:" + owner + ":" + index + ":" + output.what().hashCode();
    }

    @Override
    public ModularUI createUI(Player player) {
        return NELDLibUis.createComputationController(this, player);
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
    public MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getComputationSystemDefinition(tier);
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

    // Multi-block builder methods invoked by LDLib UI actions.

    // increaseBuildLength / decreaseBuildLength are provided by INEMultiblockBuildHost default

    @Override
    public BuildPreviewState getBuildPreview() {
        return buildPreview;
    }

    @Override
    public void markPreviewDirty() {
        setChanged();
        markUiStateDirty();
    }

    // buildPreviewStatusComponent() is provided by INEMultiblockBuildHost default

    // NBT persistence
    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("selectedBuildLength", getSelectedBuildLength());
        tag.putInt("cpuSelectionMode", cpuSelectionMode.ordinal());
        tag.putInt("networkFrequency", networkFrequency);
        tag.putInt("parallelAccelerators", parallelAccelerators);
        tag.put("computationUpgradeSlot", computationUpgradeHandler.serializeNBT());
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        buildPreview.selectedBuildLength = Math.max(1, tag.getInt("selectedBuildLength"));
        if (tag.contains("cpuSelectionMode")) {
            int ordinal = tag.getInt("cpuSelectionMode");
            CpuSelectionMode[] values = CpuSelectionMode.values();
            if (ordinal >= 0 && ordinal < values.length) {
                cpuSelectionMode = values[ordinal];
            }
        }
        if (tag.contains("networkFrequency")) {
            int savedFrequency = tag.getInt("networkFrequency");
            networkFrequency = savedFrequency >= 0 && savedFrequency < NEFrequencyAllocator.FREQUENCY_COUNT
                    ? savedFrequency
                    : NEFrequencyAllocator.UNASSIGNED;
        }
        if (tag.contains("parallelAccelerators")) {
            int saved = tag.getInt("parallelAccelerators");
            parallelAccelerators = saved < 0 || saved > NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS
                    ? NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS
                    : saved;
        }
        if (tag.contains("computationUpgradeSlot")) {
            computationUpgradeHandler.deserializeNBT(tag.getCompound("computationUpgradeSlot"));
        }
        buildPreview.buildInProgress = false;
        buildPreview.resetPreview(BuildPreviewState.DEFAULT_STATUS_KEY);
    }

    // UI sync (Layer 1: chunk-load NBT)
    // getUpdateTag/handleUpdateTag/getUpdatePacket are provided by NEBlockEntity.

    @Override
    protected void writeUiSyncTag(CompoundTag tag) {
        tag.putInt("neo_usedThread", usedThread);
        tag.putInt("neo_totalThread", totalThread);
        tag.putInt("neo_parallelCount", parallelCount);
        tag.putLong("neo_availableBytes", availableBytes);
        tag.putLong("neo_totalBytes", totalBytes);
        buildPreview.writeToTag(tag);
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("neo_usedThread")) usedThread = tag.getInt("neo_usedThread");
        if (tag.contains("neo_totalThread")) totalThread = tag.getInt("neo_totalThread");
        if (tag.contains("neo_parallelCount")) parallelCount = tag.getInt("neo_parallelCount");
        if (tag.contains("neo_availableBytes")) availableBytes = tag.getLong("neo_availableBytes");
        if (tag.contains("neo_totalBytes")) totalBytes = tag.getLong("neo_totalBytes");
        buildPreview.readFromTag(tag);
    }
}
