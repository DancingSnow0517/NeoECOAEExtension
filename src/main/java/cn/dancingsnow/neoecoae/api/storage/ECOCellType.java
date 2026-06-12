package cn.dancingsnow.neoecoae.api.storage;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record ECOCellType(Component desc, int typeCount) {
    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    public static class Builder {
        Component desc = Component.translatable("neoecoae.unknow_cell_type");
        int typeCount = 1;

        public ECOCellType build() {
            return new ECOCellType(desc, typeCount);
        }
    }
}
