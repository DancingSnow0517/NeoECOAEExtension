package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Shared mutable preview/build state for multiblock controllers.
 * <p>
 * All three controller types (Storage, Crafting, Computation) carry identical
 * preview fields. This record consolidates the NBT read/write and status
 * component logic so that adding a new preview field requires only one edit.
 * </p>
 * <p>
 * Instances are mutable by design — the controller BE owns the state and
 * mutates fields via {@link #syncPreview(int, int, int, int, String, int, int)}
 * during build ticks.
 * </p>
 */
public final class BuildPreviewState implements Cloneable {

    public static final String DEFAULT_STATUS_KEY = "gui.neoecoae.multiblock.status.idle";

    public int selectedBuildLength = 1;
    public int previewMissingBlocks;
    public int previewConflictBlocks;
    public int previewReusedBlocks;
    public int previewRequiredItems;
    public String previewStatusKey = DEFAULT_STATUS_KEY;
    public int previewStatusArg1;
    public int previewStatusArg2;
    public boolean buildInProgress;

    @Nullable public transient MultiBlockBuildSession buildSession;

    @Nullable public transient UUID buildPlayerId;

    // ── NBT keys (centralised so they stay consistent across all controllers) ──
    private static final String NBT_SELECTED_BUILD_LENGTH = "selectedBuildLength";
    private static final String NBT_PREVIEW_MISSING = "previewMissingBlocks";
    private static final String NBT_PREVIEW_CONFLICT = "previewConflictBlocks";
    private static final String NBT_PREVIEW_REUSED = "previewReusedBlocks";
    private static final String NBT_PREVIEW_REQUIRED = "previewRequiredItems";
    private static final String NBT_PREVIEW_STATUS_KEY = "previewStatusKey";
    private static final String NBT_PREVIEW_STATUS_ARG1 = "previewStatusArg1";
    private static final String NBT_PREVIEW_STATUS_ARG2 = "previewStatusArg2";
    private static final String NBT_BUILD_IN_PROGRESS = "buildInProgress";
    private static final String BUILDING_STATUS_KEY = "gui.neoecoae.multiblock.status.building";

    /** Writes all preview fields into {@code tag} using the shared NBT keys. */
    public void writeToTag(CompoundTag tag) {
        tag.putInt(NBT_SELECTED_BUILD_LENGTH, selectedBuildLength);
        tag.putInt(NBT_PREVIEW_MISSING, previewMissingBlocks);
        tag.putInt(NBT_PREVIEW_CONFLICT, previewConflictBlocks);
        tag.putInt(NBT_PREVIEW_REUSED, previewReusedBlocks);
        tag.putInt(NBT_PREVIEW_REQUIRED, previewRequiredItems);
        tag.putString(NBT_PREVIEW_STATUS_KEY, previewStatusKey != null ? previewStatusKey : DEFAULT_STATUS_KEY);
        tag.putInt(NBT_PREVIEW_STATUS_ARG1, previewStatusArg1);
        tag.putInt(NBT_PREVIEW_STATUS_ARG2, previewStatusArg2);
        tag.putBoolean(NBT_BUILD_IN_PROGRESS, buildInProgress && buildSession != null);
    }

    /** Reads all preview fields from {@code tag}. Safe to call on client or server. */
    public void readFromTag(CompoundTag tag) {
        if (tag.contains(NBT_SELECTED_BUILD_LENGTH)) {
            selectedBuildLength = tag.getInt(NBT_SELECTED_BUILD_LENGTH);
        }
        if (tag.contains(NBT_PREVIEW_MISSING)) {
            previewMissingBlocks = tag.getInt(NBT_PREVIEW_MISSING);
        }
        if (tag.contains(NBT_PREVIEW_CONFLICT)) {
            previewConflictBlocks = tag.getInt(NBT_PREVIEW_CONFLICT);
        }
        if (tag.contains(NBT_PREVIEW_REUSED)) {
            previewReusedBlocks = tag.getInt(NBT_PREVIEW_REUSED);
        }
        if (tag.contains(NBT_PREVIEW_REQUIRED)) {
            previewRequiredItems = tag.getInt(NBT_PREVIEW_REQUIRED);
        }
        if (tag.contains(NBT_PREVIEW_STATUS_KEY)) {
            previewStatusKey = tag.getString(NBT_PREVIEW_STATUS_KEY);
        }
        if (tag.contains(NBT_PREVIEW_STATUS_ARG1)) {
            previewStatusArg1 = tag.getInt(NBT_PREVIEW_STATUS_ARG1);
        }
        if (tag.contains(NBT_PREVIEW_STATUS_ARG2)) {
            previewStatusArg2 = tag.getInt(NBT_PREVIEW_STATUS_ARG2);
        }
        if (tag.contains(NBT_BUILD_IN_PROGRESS)) {
            buildInProgress = tag.getBoolean(NBT_BUILD_IN_PROGRESS);
        }
        if (buildInProgress && buildSession == null) {
            buildInProgress = false;
        }
    }

    /** Resets preview fields to idle and persists the change. */
    public void resetPreview(String statusKey) {
        syncPreview(0, 0, 0, 0, statusKey);
    }

    /** Updates all preview fields from call arguments. */
    public void syncPreview(
            int missingBlocks, int conflictBlocks, int reusedBlocks, int requiredItems, String statusKey) {
        syncPreview(missingBlocks, conflictBlocks, reusedBlocks, requiredItems, statusKey, 0, 0);
    }

    /** Full-argument variant called during build ticks. */
    public void syncPreview(
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
    }

    /** Builds a translatable status component for display in UI screens. */
    public Component buildStatusComponent() {
        if (BUILDING_STATUS_KEY.equals(previewStatusKey)) {
            return Component.translatable(previewStatusKey, previewStatusArg1, previewStatusArg2);
        }
        return Component.translatable(previewStatusKey);
    }

    /**
     * Creates a shallow copy so the caller can snapshot without
     * affecting the live build session reference.
     */
    @Override
    public BuildPreviewState clone() {
        try {
            return (BuildPreviewState) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("BuildPreviewState is Cloneable", e);
        }
    }
}
