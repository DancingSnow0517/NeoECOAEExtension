package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
    int getSelectedBuildLength();

    /**
     * Sets the build length. The implementation should clamp to valid range.
     */
    void setSelectedBuildLength(int length);

    /**
     * Returns the minimum allowed build length.
     */
    int getMinBuildLength();

    /**
     * Returns the maximum allowed build length.
     */
    int getMaxBuildLength();

    /**
     * Returns whether a build session is currently in progress.
     */
    boolean isBuildInProgress();

    /**
     * Returns whether the multiblock is already formed.
     */
    boolean isFormed();

    /**
     * Returns the shared {@link BuildPreviewState} for this controller.
     */
    BuildPreviewState getBuildPreview();

    /**
     * Runs a structure preview using the given build length.
     * This is called server-side only.
     */
    void previewStructure(ServerPlayer player, int buildLength);

    default void previewStructure(ServerPlayer player, int buildLength, boolean mirrored) {
        previewStructure(player, buildLength);
    }

    /**
     * Executes auto-build using the given build length.
     * This is called server-side only.
     */
    void autoBuild(ServerPlayer player, int buildLength);

    default void autoBuild(ServerPlayer player, int buildLength, boolean mirrored) {
        autoBuild(player, buildLength);
    }

    /** Resets the preview status to idle and persists. */
    void resetPreview(String statusKey);

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

    // ── Shared builder-length controls ──

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

    default void dismantle(ServerPlayer player) {}

    // ── Deprecated (kept for backward compat) ──

    /** @deprecated Use {@link #previewStructure(ServerPlayer, int)}. */
    @Deprecated
    void previewStructure(ServerPlayer player);

    /** @deprecated Use {@link #autoBuild(ServerPlayer, int)}. */
    @Deprecated
    void autoBuild(ServerPlayer player);
}
