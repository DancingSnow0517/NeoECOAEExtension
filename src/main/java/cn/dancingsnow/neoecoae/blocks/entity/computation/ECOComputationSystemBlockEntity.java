package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.api.me.ElapsedTimeTracker;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.multiblock.BuildPreviewState;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ECOComputationSystemBlockEntity extends AbstractComputationBlockEntity<ECOComputationSystemBlockEntity>
        implements INEMultiblockBuildHost, IUIHolder.BlockEntityUI {

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

    public ECOComputationSystemBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
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
        if (finalOutput == null || finalOutput.amount() <= 0 || !(finalOutput.what() instanceof AEItemKey itemKey)) {
            return null;
        }
        ItemStack output = itemKey.toStack(1);
        if (output.isEmpty()) {
            return null;
        }
        ElapsedTimeTracker tracker = logic.getElapsedTimeTracker();
        long total = Math.max(1L, tracker.getSyntheticStartItemCount());
        long remaining = Math.max(0L, Math.min(total, tracker.getSyntheticRemainingItemCount()));
        NECraftingRecipeUiEntry.Status status = logic.isCantStoreItems() || logic.isJobSuspended()
                ? NECraftingRecipeUiEntry.Status.WAITING_OUTPUT
                : NECraftingRecipeUiEntry.Status.RUNNING;
        return new NECraftingRecipeUiEntry(
                computationTaskId(cpu, finalOutput, index), output, finalOutput.amount(), 1L, total, remaining, status);
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

    @Override
    public void setSelectedBuildLength(int length) {
        this.selectedBuildLength = Mth.clamp(length, getMinBuildLength(), getMaxBuildLength());
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
    public void previewStructure(ServerPlayer player, int displayLength) {
        previewStructure(player, displayLength, false);
    }

    @Override
    public void previewStructure(ServerPlayer player, int displayLength, boolean mirrored) {
        setSelectedBuildLength(displayLength);
        previewStructure((Player) player, mirrored);
    }

    @Override
    public void autoBuild(ServerPlayer player, int displayLength) {
        autoBuild(player, displayLength, false);
    }

    @Override
    public void autoBuild(ServerPlayer player, int displayLength, boolean mirrored) {
        setSelectedBuildLength(displayLength);
        autoBuild((Player) player, mirrored);
    }

    @Deprecated
    @Override
    public void previewStructure(ServerPlayer player) {
        previewStructure((Player) player);
    }

    @Deprecated
    @Override
    public void autoBuild(ServerPlayer player) {
        autoBuild((Player) player);
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

    // Legacy public accessors

    public int getSelectedBuildLength() {
        return selectedBuildLength;
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

    public boolean isBuildInProgress() {
        return buildInProgress;
    }

    // Multi-block builder methods invoked by LDLib UI actions.

    // increaseBuildLength / decreaseBuildLength are provided by INEMultiblockBuildHost default

    @Override
    public BuildPreviewState getBuildPreview() {
        return buildPreview;
    }

    public void previewStructure(Player player) {
        previewStructure(player, false);
    }

    public void previewStructure(Player player, boolean mirrored) {
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
        selectedBuildLength =
                net.minecraft.util.Mth.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
                serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength, mirrored);
        boolean hasMaterials = player instanceof ServerPlayer serverPlayer
                && MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems());
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

    public void autoBuild(Player player) {
        autoBuild(player, false);
    }

    public void autoBuild(Player player, boolean mirrored) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
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

    @Override
    public void resetPreview(String statusKey) {
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
        // Keep BuildPreviewState in sync
        buildPreview.syncPreview(
                missingBlocks, conflictBlocks, reusedBlocks, requiredItems, statusKey, statusArg1, statusArg2);
        setChanged();
        markUiStateDirty();
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
        tag.putInt("selectedBuildLength", selectedBuildLength);
        tag.putInt("cpuSelectionMode", cpuSelectionMode.ordinal());
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        selectedBuildLength = tag.getInt("selectedBuildLength");
        if (selectedBuildLength < 1) selectedBuildLength = 1;
        if (tag.contains("cpuSelectionMode")) {
            int ordinal = tag.getInt("cpuSelectionMode");
            CpuSelectionMode[] values = CpuSelectionMode.values();
            if (ordinal >= 0 && ordinal < values.length) {
                cpuSelectionMode = values[ordinal];
            }
        }
        buildInProgress = false;
        previewMissingBlocks = 0;
        previewConflictBlocks = 0;
        previewReusedBlocks = 0;
        previewRequiredItems = 0;
        previewStatusKey = "gui.neoecoae.multiblock.status.idle";
        previewStatusArg1 = 0;
        previewStatusArg2 = 0;
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
        // Build/preview state is delegated to BuildPreviewState
        // Note: individual preview fields still exist alongside buildPreview;
        // syncPreview()/resetPreview() update both.
        buildPreview.writeToTag(tag);
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("neo_usedThread")) usedThread = tag.getInt("neo_usedThread");
        if (tag.contains("neo_totalThread")) totalThread = tag.getInt("neo_totalThread");
        if (tag.contains("neo_parallelCount")) parallelCount = tag.getInt("neo_parallelCount");
        if (tag.contains("neo_availableBytes")) availableBytes = tag.getLong("neo_availableBytes");
        if (tag.contains("neo_totalBytes")) totalBytes = tag.getLong("neo_totalBytes");
        // Build/preview state is delegated to BuildPreviewState
        // Keep individual field reads for backward compat; buildPreview syncs alongside.
        buildPreview.readFromTag(tag);
        if (tag.contains("selectedBuildLength")) selectedBuildLength = tag.getInt("selectedBuildLength");
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
