package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.networking.IGridNodeListener;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.network.NEComputationUiState;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOComputationSystemBlockEntity extends AbstractComputationBlockEntity<ECOComputationSystemBlockEntity>
        implements INEMultiblockBuildHost {

    @Getter
    private final IECOTier tier;

    private int usedThread;
    private int totalThread;
    private int parallelCount;
    private long availableBytes;
    private long totalBytes;
    /** Sum of CPU accelerators from all parallel cores in the cluster. */
    private int acceleratorCount;

    private boolean computationStatsDirty = true;
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

    public void markComputationStatsDirty() {
        computationStatsDirty = true;
        markUiStateDirty();
    }

    public long getUiRevision() {
        return uiRevision;
    }

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
        syncUiToClient();
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

    /**
     * Creates a snapshot of current computation stats for S2C UI sync.
     * <p>
     * This reads cached stats. Mutating cluster paths mark the cache dirty and
     * update it before bumping the UI revision.
     * </p>
     */
    public NEComputationUiState createComputationUiState() {
        return new NEComputationUiState(
                worldPosition,
                formed,
                cluster != null && cluster.isActive(),
                usedThread,
                totalThread,
                availableBytes,
                totalBytes,
                parallelCount,
                acceleratorCount);
    }

    public Component getPreviewStatusComponent() {
        return buildPreviewStatusComponent();
    }

    // ── INEMultiblockBuildHost interface ──

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

    // ── Legacy public accessors ──

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

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
    // Multi-block builder methods (called from native Screen buttons)
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

    public void increaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        selectedBuildLength =
                net.minecraft.util.Mth.clamp(selectedBuildLength + 1, getMinBuildLength(), getMaxBuildLength());
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    public void decreaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        selectedBuildLength =
                net.minecraft.util.Mth.clamp(selectedBuildLength - 1, getMinBuildLength(), getMaxBuildLength());
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
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

    // ── NBT persistence ──

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("selectedBuildLength", selectedBuildLength);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        selectedBuildLength = tag.getInt("selectedBuildLength");
        if (selectedBuildLength < 1) selectedBuildLength = 1;
        buildInProgress = false;
        previewMissingBlocks = 0;
        previewConflictBlocks = 0;
        previewReusedBlocks = 0;
        previewRequiredItems = 0;
        previewStatusKey = "gui.neoecoae.multiblock.status.idle";
        previewStatusArg1 = 0;
        previewStatusArg2 = 0;
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

    private void syncUiToClient() {
        if (level != null && !level.isClientSide && getBlockPos() != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private void writeUiSyncTag(CompoundTag tag) {
        tag.putInt("neo_usedThread", usedThread);
        tag.putInt("neo_totalThread", totalThread);
        tag.putInt("neo_parallelCount", parallelCount);
        tag.putLong("neo_availableBytes", availableBytes);
        tag.putLong("neo_totalBytes", totalBytes);
        // Build/preview state
        tag.putInt("selectedBuildLength", selectedBuildLength);
        tag.putInt("previewMissingBlocks", previewMissingBlocks);
        tag.putInt("previewConflictBlocks", previewConflictBlocks);
        tag.putInt("previewReusedBlocks", previewReusedBlocks);
        tag.putInt("previewRequiredItems", previewRequiredItems);
        tag.putString(
                "previewStatusKey",
                previewStatusKey != null ? previewStatusKey : "gui.neoecoae.multiblock.status.idle");
        tag.putInt("previewStatusArg1", previewStatusArg1);
        tag.putInt("previewStatusArg2", previewStatusArg2);
        tag.putBoolean("buildInProgress", buildInProgress);
    }

    private void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("neo_usedThread")) usedThread = tag.getInt("neo_usedThread");
        if (tag.contains("neo_totalThread")) totalThread = tag.getInt("neo_totalThread");
        if (tag.contains("neo_parallelCount")) parallelCount = tag.getInt("neo_parallelCount");
        if (tag.contains("neo_availableBytes")) availableBytes = tag.getLong("neo_availableBytes");
        if (tag.contains("neo_totalBytes")) totalBytes = tag.getLong("neo_totalBytes");
        // Build/preview state
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
