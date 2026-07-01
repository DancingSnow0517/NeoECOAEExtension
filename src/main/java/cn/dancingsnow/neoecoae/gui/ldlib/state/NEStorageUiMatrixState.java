package cn.dancingsnow.neoecoae.gui.ldlib.state;

import java.util.Objects;
import net.minecraft.world.item.ItemStack;

public record NEStorageUiMatrixState(
        int row,
        int column,
        ItemStack stack,
        int tier,
        long usedTypes,
        long totalTypes,
        long usedBytes,
        long totalBytes) {
    public boolean hasMatrix() {
        return !stack.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NEStorageUiMatrixState other)) {
            return false;
        }
        return row == other.row
                && column == other.column
                && tier == other.tier
                && usedTypes == other.usedTypes
                && totalTypes == other.totalTypes
                && usedBytes == other.usedBytes
                && totalBytes == other.totalBytes
                && ItemStack.matches(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                row,
                column,
                stack.getItem(),
                stack.getCount(),
                stack.getTag(),
                tier,
                usedTypes,
                totalTypes,
                usedBytes,
                totalBytes);
    }
}
