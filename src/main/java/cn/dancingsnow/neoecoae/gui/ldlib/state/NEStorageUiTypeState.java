package cn.dancingsnow.neoecoae.gui.ldlib.state;

import net.minecraft.resources.ResourceLocation;

public record NEStorageUiTypeState(
        ResourceLocation typeId,
        String displayName,
        long usedTypes,
        long totalTypes,
        long usedBytes,
        long totalBytes,
        String usedAmount) {
    public NEStorageUiTypeState(
            ResourceLocation typeId,
            String displayName,
            long usedTypes,
            long totalTypes,
            long usedBytes,
            long totalBytes) {
        this(typeId, displayName, usedTypes, totalTypes, usedBytes, totalBytes, Long.toString(Math.max(0L, usedBytes)));
    }

    public String safeUsedAmount() {
        return usedAmount == null || usedAmount.isBlank() ? Long.toString(Math.max(0L, usedBytes)) : usedAmount;
    }
}
