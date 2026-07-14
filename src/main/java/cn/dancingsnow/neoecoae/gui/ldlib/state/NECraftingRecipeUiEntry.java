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
        Status status,
        String taskHostName,
        long taskStorage,
        int taskCoProcessors,
        long requestedAmount,
        long elapsedNanos) {
    public enum Status {
        RUNNING,
        QUEUED,
        WAITING_OUTPUT
    }

    public NECraftingRecipeUiEntry(
            String id,
            ItemStack output,
            long outputAmount,
            long craftCount,
            long totalTicks,
            long remainingTicks,
            Status status) {
        this(id, output, outputAmount, craftCount, totalTicks, remainingTicks, status, "", 0L, 0, 0L, 0L);
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
                && taskStorage == other.taskStorage
                && taskCoProcessors == other.taskCoProcessors
                && requestedAmount == other.requestedAmount
                && elapsedNanos == other.elapsedNanos
                && Objects.equals(id, other.id)
                && Objects.equals(taskHostName, other.taskHostName)
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
                status,
                taskHostName,
                taskStorage,
                taskCoProcessors,
                requestedAmount,
                elapsedNanos);
    }
}
