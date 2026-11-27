package cn.dancingsnow.neoecoae.api.storage;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @param visible   whether to show this type in the storage host's type information list
 * @param countOnly whether cells of this type hold a count rather than bytes - a pattern matrix holds so many
 *                  patterns out of so many. The storage host then shows that count alone and leaves the byte
 *                  figures out, which would otherwise read as an empty 0 / 0
 */
public record ECOCellType(Component desc, int typeCount, boolean visible, boolean countOnly) {
    public ECOCellType(Component desc, int typeCount, boolean visible) {
        this(desc, typeCount, visible, false);
    }

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
        boolean countOnly = false;

        public ECOCellType build() {
            return new ECOCellType(desc, typeCount, visible, countOnly);
        }
    }
}
