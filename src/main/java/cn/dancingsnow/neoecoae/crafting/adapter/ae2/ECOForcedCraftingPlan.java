package cn.dancingsnow.neoecoae.crafting.adapter.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.util.Map;

/** Turns an AE2 simulation plan into a submit-ready plan while retaining diagnostics. */
public final class ECOForcedCraftingPlan implements ICraftingPlan {
    private final ICraftingPlan delegate;
    private final KeyCounter waitingItems = new KeyCounter();

    public ECOForcedCraftingPlan(ICraftingPlan delegate) {
        this.delegate = delegate;
        waitingItems.addAll(delegate.emittedItems());
        // AE2's native waiting inventory already supports insertion, status, cancellation and persistence.
        // Merely hiding missingItems would accept the job but leave it unable to receive its missing inputs.
        for (var entry : delegate.missingItems()) {
            waitingItems.set(entry.getKey(), Math.addExact(waitingItems.get(entry.getKey()), entry.getLongValue()));
        }
    }

    @Override public GenericStack finalOutput() { return delegate.finalOutput(); }
    @Override public long bytes() { return delegate.bytes(); }
    @Override public boolean simulation() { return false; }
    @Override public boolean multiplePaths() { return delegate.multiplePaths(); }
    @Override public KeyCounter usedItems() { return delegate.usedItems(); }
    @Override public KeyCounter emittedItems() {
        var copy = new KeyCounter();
        copy.addAll(waitingItems);
        return copy;
    }
    @Override public KeyCounter missingItems() { return new KeyCounter(); }
    @Override public Map<IPatternDetails, Long> patternTimes() { return delegate.patternTimes(); }
}
