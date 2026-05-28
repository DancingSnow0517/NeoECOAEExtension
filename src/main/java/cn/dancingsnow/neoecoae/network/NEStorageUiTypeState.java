package cn.dancingsnow.neoecoae.network;

import net.minecraft.resources.ResourceLocation;

/**
 * Per-cell-type storage stats within a {@link NEStorageUiState}.
 * <p>
 * The {@code typeId} is a registry key (e.g. {@code neoecoae:items},
 * {@code neoecoae:fluids}) and {@code displayName} is a human-readable
 * string sourced from the ECOCellType description. New cell types added
 * in the future (Mekanism chemicals, etc.) automatically appear as
 * additional entries in the parent state's list.
 * </p>
 */
public record NEStorageUiTypeState(
    ResourceLocation typeId,
    String displayName,
    long usedTypes,
    long totalTypes,
    long usedBytes,
    long totalBytes
) {
}
