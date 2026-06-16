package cn.dancingsnow.neoecoae.gui.host;

import net.minecraft.world.item.ItemStack;

public record NECraftingTaskEntry(
    String id,
    ItemStack output,
    long outputAmount,
    long craftCount,
    long totalTicks,
    long remainingTicks,
    Status status
) {
    public enum Status {
        RUNNING,
        QUEUED,
        WAITING_OUTPUT
    }
}
