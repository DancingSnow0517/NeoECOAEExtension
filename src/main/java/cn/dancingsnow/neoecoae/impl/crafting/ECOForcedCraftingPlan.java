package cn.dancingsnow.neoecoae.impl.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.util.Map;

/** Turns an AE2 simulation plan into a submit-ready plan while retaining diagnostics. */
public final class ECOForcedCraftingPlan implements ICraftingPlan {
    private final ICraftingPlan delegate;

    public ECOForcedCraftingPlan(ICraftingPlan delegate) {
        this.delegate = delegate;
    }

    @Override public GenericStack finalOutput() { return delegate.finalOutput(); }
    @Override public long bytes() { return delegate.bytes(); }
    @Override public boolean simulation() { return false; }
    @Override public boolean multiplePaths() { return delegate.multiplePaths(); }
    @Override public KeyCounter usedItems() { return delegate.usedItems(); }
    @Override public KeyCounter emittedItems() { return delegate.emittedItems(); }
    @Override public KeyCounter missingItems() { return new KeyCounter(); }
    @Override public Map<IPatternDetails, Long> patternTimes() { return delegate.patternTimes(); }
}
