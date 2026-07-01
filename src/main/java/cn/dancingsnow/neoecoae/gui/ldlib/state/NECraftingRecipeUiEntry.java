package cn.dancingsnow.neoecoae.gui.ldlib.state;

import java.util.Objects;
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NECraftingRecipeUiEntry other)) {
            return false;
        }
        return outputAmount == other.outputAmount
                && craftCount == other.craftCount
                && totalTicks == other.totalTicks
                && remainingTicks == other.remainingTicks
                && Objects.equals(id, other.id)
                && ItemStack.matches(output, other.output)
                && status == other.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                output.getItem(),
                output.getCount(),
                output.getTag(),
                outputAmount,
                craftCount,
                totalTicks,
                remainingTicks,
                status);
    }
}
