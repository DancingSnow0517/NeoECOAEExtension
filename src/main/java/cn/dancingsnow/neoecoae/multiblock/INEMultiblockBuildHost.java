package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for multiblock controllers that can be operated by the
 * Structure Terminal item.
 * <p>
 * Any block entity that implements this interface can be targeted
 * by the Structure Terminal for preview and auto-build operations.
 * Crafting, Storage, and Computation controllers are expected to
 * implement this interface.
 * </p>
 */
public interface INEMultiblockBuildHost {

    /**
     * Returns the block position of this host.
     */
    BlockPos getHostPos();

    /**
     * Returns the current block state of this host.
     */
    BlockState getHostBlockState();

    /**
     * Returns the multi-block definition for building, or {@code null}
     * if this host has no buildable structure.
     */
    @Nullable MultiBlockDefinition getBuildDefinition();

    /**
     * Returns the currently selected build length (repeat count).
     */
    default int getSelectedBuildLength() {
        return getBuildPreview().selectedBuildLength;
    }

    /**
     * Sets the build length. The implementation should clamp to valid range.
     */
    default void setSelectedBuildLength(int length) {
        getBuildPreview().selectedBuildLength = Mth.clamp(length, getMinBuildLength(), getMaxBuildLength());
    }

    /**
     * Returns the minimum allowed build length.
     */
    default int getMinBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMin();
    }

    /**
     * Returns the maximum allowed build length.
     */
    default int getMaxBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMax();
    }

    /**
     * Returns whether a build session is currently in progress.
     */
    default boolean isBuildInProgress() {
        return getBuildPreview().buildInProgress;
    }

    /**
     * Returns whether the multiblock is already formed.
     */
    boolean isFormed();

    /**
     * Returns the shared {@link BuildPreviewState} for this controller.
     */
    BuildPreviewState getBuildPreview();

    void rebuildMultiblock();

    @Nullable default Level getHostLevel() {
        return this instanceof BlockEntity blockEntity ? blockEntity.getLevel() : null;
    }

    /**
     * Runs a structure preview using the given build length.
     * This is called server-side only.
     */
    default void previewStructure(ServerPlayer player, int buildLength) {
        previewStructure(player, buildLength, false);
    }

    default void previewStructure(ServerPlayer player, int buildLength, boolean mirrored) {
        setSelectedBuildLength(buildLength);
        previewStructure(player, mirrored);
    }

    /**
     * Executes auto-build using the given build length.
     * This is called server-side only.
     */
    default void autoBuild(ServerPlayer player, int buildLength) {
        autoBuild(player, buildLength, false);
    }

    default void autoBuild(ServerPlayer player, int buildLength, boolean mirrored) {
        setSelectedBuildLength(buildLength);
        autoBuild(player, mirrored);
    }

    /** Resets the preview status to idle and persists. */
    default void resetPreview(String statusKey) {
        syncPreview(0, 0, 0, 0, statusKey);
    }

    /**
     * Called when preview state changes. Implementations should call
     * {@code setChanged()} plus either {@code markForUpdate()} (Storage)
     * or {@code markUiStateDirty()} (Crafting, Computation).
     */
    void markPreviewDirty();

    /** Delegates to {@link #buildPreviewStatusComponent()} so subclasses don't duplicate. */
    default Component getPreviewStatusComponent() {
        return buildPreviewStatusComponent();
    }

    // Shared builder-length controls.

    /** Increases the selected build length by one, clamped to valid range. */
    default void increaseBuildLength() {
        if (isBuildInProgress()) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        setSelectedBuildLength(Mth.clamp(getSelectedBuildLength() + 1, getMinBuildLength(), getMaxBuildLength()));
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    /** Decreases the selected build length by one, clamped to valid range. */
    default void decreaseBuildLength() {
        if (isBuildInProgress()) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        setSelectedBuildLength(Mth.clamp(getSelectedBuildLength() - 1, getMinBuildLength(), getMaxBuildLength()));
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    /** Builds a translatable status component from the shared preview state. */
    default Component buildPreviewStatusComponent() {
        return getBuildPreview().buildStatusComponent();
    }

    default void tickBuild(Level level) {
        BuildPreviewState preview = getBuildPreview();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                || !preview.buildInProgress
                || preview.buildSession == null) {
            return;
        }

        ServerPlayer buildPlayer = preview.buildPlayerId == null
                ? null
                : serverLevel.getServer().getPlayerList().getPlayer(preview.buildPlayerId);
        if (buildPlayer == null) {
            int remainingBlocks = preview.buildSession.getRemainingBlockCount();
            clearBuildSession();
            syncPreview(
                    remainingBlocks,
                    0,
                    preview.previewReusedBlocks,
                    preview.previewRequiredItems,
                    "gui.neoecoae.multiblock.status.builder_unavailable");
            return;
        }

        switch (MultiBlockPlacementService.tickBuild(serverLevel, preview.buildSession, buildPlayer)) {
            case WAITING -> {}
            case ADVANCED -> syncPreview(
                    preview.buildSession.getRemainingBlockCount(),
                    preview.buildSession.getSkippedBlockCount(),
                    preview.previewReusedBlocks,
                    preview.previewRequiredItems,
                    "gui.neoecoae.multiblock.status.building",
                    preview.buildSession.getPlacedBlockCount(),
                    preview.buildSession.getTotalBlocks());
            case COMPLETED -> {
                int skippedBlocks = preview.buildSession.getSkippedBlockCount();
                clearBuildSession();
                rebuildMultiblock();
                syncPreview(
                        0,
                        skippedBlocks,
                        preview.previewReusedBlocks,
                        preview.previewRequiredItems,
                        skippedBlocks > 0
                                ? "gui.neoecoae.multiblock.status.conflicts_detected"
                                : "gui.neoecoae.multiblock.status.build_complete");
            }
            case BLOCKED -> {
                int remainingBlocks = preview.buildSession.getRemainingBlockCount();
                clearBuildSession();
                syncPreview(
                        remainingBlocks,
                        1,
                        preview.previewReusedBlocks,
                        preview.previewRequiredItems,
                        "gui.neoecoae.multiblock.status.build_interrupted");
            }
        }
    }

    default void previewStructure(ServerPlayer player, boolean mirrored) {
        Level level = getHostLevel();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        if (isFormed()) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }

        BuildPreviewState preview = getBuildPreview();
        if (preview.buildInProgress && preview.buildSession != null) {
            syncPreview(
                    preview.buildSession.getRemainingBlockCount(),
                    0,
                    preview.previewReusedBlocks,
                    preview.previewRequiredItems,
                    "gui.neoecoae.multiblock.status.building",
                    preview.buildSession.getPlacedBlockCount(),
                    preview.buildSession.getTotalBlocks());
            return;
        }

        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }

        setSelectedBuildLength(getSelectedBuildLength());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
                serverLevel, getHostPos(), getHostBlockState(), definition, getSelectedBuildLength(), mirrored);
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

    default void autoBuild(ServerPlayer serverPlayer, boolean mirrored) {
        Level level = getHostLevel();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        serverPlayer.closeContainer();
        if (isFormed()) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }

        BuildPreviewState preview = getBuildPreview();
        if (preview.buildInProgress) {
            syncPreview(
                    preview.previewMissingBlocks,
                    preview.previewConflictBlocks,
                    preview.previewReusedBlocks,
                    preview.previewRequiredItems,
                    "gui.neoecoae.multiblock.status.build_already_in_progress");
            return;
        }

        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }

        setSelectedBuildLength(getSelectedBuildLength());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
                serverLevel, getHostPos(), getHostBlockState(), definition, getSelectedBuildLength(), mirrored);
        if (!serverPlayer.isCreative()
                && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            syncPreview(
                    plan.getMissingBlocks().size(),
                    plan.getConflictPositions().size(),
                    plan.getReusedBlockCount(),
                    plan.getRequiredItemCount(),
                    "gui.neoecoae.multiblock.status.not_enough_items");
            return;
        }
        if (plan.getMissingBlocks().isEmpty()) {
            completeInstantBuild(plan);
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
            completeInstantBuild(plan);
            return;
        }

        preview.buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        preview.buildPlayerId = serverPlayer.getUUID();
        preview.buildInProgress = true;
        syncPreview(
                plan.getMissingBlocks().size(),
                plan.getConflictPositions().size(),
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                "gui.neoecoae.multiblock.status.building",
                preview.buildSession.getPlacedBlockCount(),
                preview.buildSession.getTotalBlocks());
    }

    private void completeInstantBuild(MultiBlockPlacementPlan plan) {
        rebuildMultiblock();
        int conflicts = plan.getConflictPositions().size();
        syncPreview(
                0,
                conflicts,
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                conflicts > 0
                        ? "gui.neoecoae.multiblock.status.conflicts_detected"
                        : "gui.neoecoae.multiblock.status.build_complete");
    }

    private void clearBuildSession() {
        BuildPreviewState preview = getBuildPreview();
        preview.buildSession = null;
        preview.buildPlayerId = null;
        preview.buildInProgress = false;
    }

    default void syncPreview(
            int missingBlocks, int conflictBlocks, int reusedBlocks, int requiredItems, String statusKey) {
        syncPreview(missingBlocks, conflictBlocks, reusedBlocks, requiredItems, statusKey, 0, 0);
    }

    default void syncPreview(
            int missingBlocks,
            int conflictBlocks,
            int reusedBlocks,
            int requiredItems,
            String statusKey,
            int statusArg1,
            int statusArg2) {
        getBuildPreview()
                .syncPreview(
                        missingBlocks, conflictBlocks, reusedBlocks, requiredItems, statusKey, statusArg1, statusArg2);
        markPreviewDirty();
    }

    default void dismantle(ServerPlayer player) {
        Level level = getHostLevel();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
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

    // Deprecated methods kept for backward compatibility.

    /** @deprecated Use {@link #previewStructure(ServerPlayer, int)}. */
    @Deprecated
    void previewStructure(ServerPlayer player);

    /** @deprecated Use {@link #autoBuild(ServerPlayer, int)}. */
    @Deprecated
    void autoBuild(ServerPlayer player);
}
