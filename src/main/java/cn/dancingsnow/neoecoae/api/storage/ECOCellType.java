package cn.dancingsnow.neoecoae.api.storage;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record ECOCellType(ResourceLocation id, Component desc, int typeCount) {
    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    public static class Builder {
        ResourceLocation id;
        Component desc = Component.translatable("neoecoae.unknown_cell_type");
        int typeCount = 1;

        public ECOCellType build() {
            return new ECOCellType(id, desc, typeCount);
        }
    }
}
