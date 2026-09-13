package cn.dancingsnow.neoecoae.api.me.lifecycle;

import java.util.Objects;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

/** Stable description of an accepted provider dispatch. */
public record ECOCraftingDispatchEvent(
        ECOCraftingJobContext job,
        IPatternDetails pattern,
        long dispatchedCrafts,
        ICraftingProvider provider) {

    public ECOCraftingDispatchEvent {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(provider, "provider");
        if (dispatchedCrafts <= 0L) {
            throw new IllegalArgumentException("dispatchedCrafts must be positive");
        }
    }
}
