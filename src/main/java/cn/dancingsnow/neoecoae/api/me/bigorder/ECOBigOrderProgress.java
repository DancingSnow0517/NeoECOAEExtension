package cn.dancingsnow.neoecoae.api.me.bigorder;

import cn.dancingsnow.neoecoae.crafting.execution.bigorder.ECOBigCraftingOrder;

import java.math.BigInteger;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.UUID;

public record ECOBigOrderProgress(UUID orderId, ECOBigOrderState state, BigInteger requested,
        BigInteger completed, BigInteger remaining, long childTarget, long childRemaining, String waitingReason) {
    public ECOBigOrderProgress {
        java.util.Objects.requireNonNull(orderId);
        java.util.Objects.requireNonNull(state);
        ECOBigCraftingOrder.checked(requested);
        ECOBigCraftingOrder.checked(completed);
        ECOBigCraftingOrder.checked(remaining);
        PlannerAmount remainingQuantity = PlannerAmount.of(remaining);
        if (!PlannerAmount.of(completed).add(remainingQuantity).equals(PlannerAmount.of(requested)) || childTarget < 0 || childRemaining < 0
                || childRemaining > childTarget || PlannerAmount.of(childTarget).compareTo(remainingQuantity) > 0
                || waitingReason == null || waitingReason.length() > 256)
            throw new IllegalArgumentException("Invalid parent progress");
    }
}
