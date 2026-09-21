package cn.dancingsnow.neoecoae.api.me.bigorder;

import cn.dancingsnow.neoecoae.crafting.execution.bigorder.ECOBigCraftingOrder;

import java.math.BigInteger;
import java.util.UUID;

public record ECOBigOrderProgress(UUID orderId, ECOBigOrderState state, BigInteger requested,
        BigInteger completed, BigInteger remaining, long childTarget, long childRemaining, String waitingReason) {
    public ECOBigOrderProgress {
        java.util.Objects.requireNonNull(orderId);
        java.util.Objects.requireNonNull(state);
        ECOBigCraftingOrder.checked(requested);
        ECOBigCraftingOrder.checked(completed);
        ECOBigCraftingOrder.checked(remaining);
        if (!completed.add(remaining).equals(requested) || childTarget < 0 || childRemaining < 0
                || childRemaining > childTarget || BigInteger.valueOf(childTarget).compareTo(remaining) > 0
                || waitingReason == null || waitingReason.length() > 256)
            throw new IllegalArgumentException("Invalid parent progress");
    }
}
