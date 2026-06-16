package cn.dancingsnow.neoecoae.gui.host;

import net.minecraft.world.item.ItemStack;

public record NEStorageMatrixCell(
    int row,
    int column,
    ItemStack stack,
    int tier,
    long usedTypes,
    long totalTypes,
    long usedBytes,
    long totalBytes
) {
    public boolean hasCell() {
        return !stack.isEmpty();
    }
}
