package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.IECOComputationHost;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.api.me.ElapsedTimeTracker;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEBlockEntityUIHolder;
import cn.dancingsnow.neoecoae.multiblock.BuildPreviewState;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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

    /** CPU auto-selection mode, persisted in the controller's NBT. */
    private CpuSelectionMode cpuSelectionMode = CpuSelectionMode.ANY;

    private boolean computationStatsDirty = true;
    /** Shared preview/build state, delegates NBT sync to {@link BuildPreviewState}. */
    private final BuildPreviewState buildPreview = new BuildPreviewState();

    private long uiRevision = 0L;

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
        } else {
            usedThread = 0;
            totalThread = 0;
            parallelCount = 0;
            availableBytes = 0;
            totalBytes = 0;
            acceleratorCount = 0;
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
        markUiStateDirty();
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
        return new NEComputationUiState(
                worldPosition,
                formed,
                cluster != null && cluster.isActive(),
                usedThread,
                totalThread,
                availableBytes,
                totalBytes,
                parallelCount,
                acceleratorCount,
                mode,
                collectComputationRecipeEntries());
    }

    private List<NECraftingRecipeUiEntry> collectComputationRecipeEntries() {
        if (cluster == null) {
            return List.of();
        }
        List<ECOCraftingCPU> activeCpus = cluster.getActiveCPUs();
        if (activeCpus.isEmpty()) {
            return List.of();
        }
        List<NECraftingRecipeUiEntry> entries = new ArrayList<>(activeCpus.size());
        int index = 0;
        for (ECOCraftingCPU cpu : activeCpus) {
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
        if (finalOutput == null || remainingAmount <= 0 || !(finalOutput.what() instanceof AEItemKey itemKey)) {
            return null;
        }
        ItemStack output = itemKey.toStack(1);
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
                computationTaskId(cpu, finalOutput, index), output, remainingAmount, 1L, total, remaining, status);
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
