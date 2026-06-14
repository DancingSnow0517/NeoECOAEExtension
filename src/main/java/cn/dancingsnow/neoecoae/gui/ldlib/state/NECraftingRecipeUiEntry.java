package cn.dancingsnow.neoecoae.gui.ldlib.state;

import net.minecraft.world.item.ItemStack;

public record NECraftingRecipeUiEntry(
        String id,
        ItemStack output,
        long outputAmount,
        long craftCount,
        long totalTicks,
        long remainingTicks,
        Status status) {
    public enum Status {
        RUNNING,
        QUEUED,
        WAITING_OUTPUT
    }
}
