package cn.dancingsnow.neoecoae.gui.ldlib.state;

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
}
