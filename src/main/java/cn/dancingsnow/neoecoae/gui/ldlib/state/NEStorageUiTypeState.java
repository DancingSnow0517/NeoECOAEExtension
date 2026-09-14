package cn.dancingsnow.neoecoae.gui.ldlib.state;

import appeng.api.stacks.AEKeyTypes;
import cn.dancingsnow.neoecoae.all.NERegistries;
import net.minecraft.network.chat.Component;
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

    public Component displayComponent() {
        try {
            for (var keyType : AEKeyTypes.getAll()) {
                if (typeId.equals(keyType.getId())) {
                    return keyType.getDescription();
                }
            }
        } catch (LinkageError | RuntimeException ignored) {
            // AE2 registry is unavailable in isolated clients/tests.
        }
        try {
            var cellTypes = NERegistries.cellTypeRegistry();
            if (cellTypes != null) {
                for (var cellType : cellTypes) {
                    if (typeId.equals(cellType.id())) {
                        return cellType.desc();
                    }
                }
            }
        } catch (LinkageError | RuntimeException ignored) {
            // Registrate is unavailable in isolated clients/tests.
        }
        return Component.literal(displayName);
    }
}
