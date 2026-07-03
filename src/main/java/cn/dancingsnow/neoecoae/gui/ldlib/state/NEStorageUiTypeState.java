package cn.dancingsnow.neoecoae.gui.ldlib.state;

import java.util.Locale;
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
        String path = typeId.getPath().toLowerCase(Locale.ROOT);
        if (path.equals("items") || path.equals("item")) {
            return Component.translatable("gui.neoecoae.storage.items");
        }
        if (path.equals("fluids") || path.equals("fluid")) {
            return Component.translatable("gui.neoecoae.storage.fluids");
        }
        if (path.equals("infinite")) {
            return Component.translatable("gui.neoecoae.storage.infinite_domain");
        }
        return Component.translatable("cell_type." + typeId.getNamespace() + "." + typeId.getPath());
    }
}
