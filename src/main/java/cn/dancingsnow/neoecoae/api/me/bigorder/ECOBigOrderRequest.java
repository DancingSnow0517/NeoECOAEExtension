package cn.dancingsnow.neoecoae.api.me.bigorder;

import cn.dancingsnow.neoecoae.crafting.execution.bigorder.ECOBigCraftingOrder;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import java.math.BigInteger;
import java.util.Map;

/** Parent metadata is deliberately separate from AE2's long-only ICraftingPlan. */
public record ECOBigOrderRequest(AEKey goal, BigInteger requested, boolean forced,
        ECOPlannerOptions options, Map<AEKey, BigInteger> pendingPreview) {
    public ECOBigOrderRequest(AEKey goal, BigInteger requested, boolean forced, ECOPlannerOptions options) {
        this(goal, requested, forced, options, Map.of());
    }
    private static final ThreadLocal<Submission> SUBMISSION = new ThreadLocal<>();
    private record Submission(ICraftingPlan carrier, ECOBigOrderRequest request) {}

    public ECOBigOrderRequest {
        java.util.Objects.requireNonNull(goal);
        java.util.Objects.requireNonNull(options);
        pendingPreview = Map.copyOf(pendingPreview);
        ECOBigCraftingOrder.checked(requested);
        if (requested.signum() <= 0) throw new IllegalArgumentException("Empty order");
    }
    public ICraftingPlan carrier() {
        return new appeng.crafting.CraftingPlan(
            new GenericStack(goal, requested.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact()),
            0, false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
    }

    public <T> T submit(java.util.function.Function<ICraftingPlan, T> submitter) {
        var carrier = carrier();
        var previous = SUBMISSION.get();
        SUBMISSION.set(new Submission(carrier, this));
        try { return submitter.apply(carrier); }
        finally { if (previous == null) SUBMISSION.remove(); else SUBMISSION.set(previous); }
    }

    public static ECOBigOrderRequest forSubmission(ICraftingPlan plan) {
        var submission = SUBMISSION.get();
        return submission != null && submission.carrier == plan ? submission.request : null;
    }
}
