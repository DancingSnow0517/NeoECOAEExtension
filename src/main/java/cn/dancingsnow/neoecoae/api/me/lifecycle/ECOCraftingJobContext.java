package cn.dancingsnow.neoecoae.api.me.lifecycle;

import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.GenericStack;

/** Stable, immutable information about one ECO crafting job. */
public record ECOCraftingJobContext(
        ICraftingCPU cpu,
        UUID craftingJobId,
        @Nullable GenericStack finalOutput,
        long requestedAmount,
        long remainingAmount) {

    public ECOCraftingJobContext {
        Objects.requireNonNull(cpu, "cpu");
        Objects.requireNonNull(craftingJobId, "craftingJobId");
        if (requestedAmount < 0L || remainingAmount < 0L) {
            throw new IllegalArgumentException("Job amounts must not be negative");
        }
    }

    public long completedAmount() {
        return Math.max(0L, requestedAmount - remainingAmount);
    }
}
