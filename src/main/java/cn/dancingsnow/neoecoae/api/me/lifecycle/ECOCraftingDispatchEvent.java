package cn.dancingsnow.neoecoae.api.me.lifecycle;

import java.util.Objects;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

/** Stable description of an accepted provider dispatch. */
public record ECOCraftingDispatchEvent(
        ECOCraftingJobContext job,
        IPatternDetails pattern,
        long dispatchedCrafts,
        ICraftingProvider provider,
        java.math.BigInteger exactDispatchedCrafts) {

    public ECOCraftingDispatchEvent(ECOCraftingJobContext job, IPatternDetails pattern,
            long dispatchedCrafts, ICraftingProvider provider) {
        this(job, pattern, dispatchedCrafts, provider, java.math.BigInteger.valueOf(dispatchedCrafts));
    }

    public ECOCraftingDispatchEvent(ECOCraftingJobContext job, IPatternDetails pattern,
            java.math.BigInteger dispatchedCrafts, ICraftingProvider provider) {
        this(job, pattern, cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(dispatchedCrafts),
            provider, dispatchedCrafts);
    }

    public ECOCraftingDispatchEvent {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(provider, "provider");
        if (dispatchedCrafts <= 0L || exactDispatchedCrafts.signum() <= 0) {
            throw new IllegalArgumentException("dispatchedCrafts must be positive");
        }
    }
}
