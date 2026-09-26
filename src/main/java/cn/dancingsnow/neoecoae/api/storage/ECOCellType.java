package cn.dancingsnow.neoecoae.api.storage;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @param visible whether to show this type in the storage host's type information list
 */
public record ECOCellType(Component desc, int typeCount, boolean visible) {
    public ECOCellType(Component desc, int typeCount) {
        this(desc, typeCount, true);
    }

    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    public static class Builder {
        Component desc = Component.translatable("neoecoae.unknow_cell_type");
        int typeCount = 1;
        boolean visible = true;

        public ECOCellType build() {
            return new ECOCellType(desc, typeCount, visible);
        }
    }
}
