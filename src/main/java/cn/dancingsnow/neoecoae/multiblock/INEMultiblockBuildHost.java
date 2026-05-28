package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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
    @Nullable
    MultiBlockDefinition getBuildDefinition();

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
     * Runs a structure preview and updates internal preview state.
     * This is called server-side only.
     */
    void previewStructure(ServerPlayer player);

    /**
     * Executes auto-build: closes the player's current container,
     * validates materials and conflicts, then starts or instantly
     * completes the build.
     * This is called server-side only.
     */
    void autoBuild(ServerPlayer player);

    /**
     * Creates a UI state snapshot for the structure terminal.
     * Includes build progress, preview stats, and material info.
     */
    NEStructureTerminalUiState createBuildUiState();

    /**
     * Notifies the host that its UI state should be pushed to the
     * given player. Used after state-changing operations.
     */
    void sendBuildUiState(ServerPlayer player);
}
