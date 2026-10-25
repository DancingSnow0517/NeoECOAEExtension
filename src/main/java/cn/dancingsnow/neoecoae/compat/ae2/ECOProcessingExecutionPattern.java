package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternWrappers;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchInputScaling;
import java.util.List;
import net.minecraft.world.level.Level;

/** An execution-only view. The multiplier is chosen by ECO, never by provider settings. */
public final class ECOProcessingExecutionPattern implements IPatternDetails, ECOPatternWrappers.Wrapper {
    private final IPatternDetails original;
    private final long copies;
    private final ECOBatchInputScaling inputScaling;

    public ECOProcessingExecutionPattern(IPatternDetails original, long copies) {
        this(original, copies, ECOBatchInputScaling.LINEAR);
    }

    public ECOProcessingExecutionPattern(IPatternDetails original, long copies, ECOBatchInputScaling inputScaling) {
        if (copies < 1) throw new IllegalArgumentException("Non-positive batch");
        this.original = original;
        this.copies = copies;
        this.inputScaling = inputScaling;
    }

    public IPatternDetails providerLookupPattern() { return original; }
    @Override public IPatternDetails ecoProviderPattern() { return original; }
    @Override public AEItemKey getDefinition() { return original.getDefinition(); }
    @Override public IInput[] getInputs() {
        var source = original.getInputs();
        var result = new IInput[source.length];
        for (int i = 0; i < source.length; i++) result[i] = new Input(source[i], copies, inputScaling);
        return result;
    }
    @Override public GenericStack[] getOutputs() {
        return ECOBatchCraftingHelper.multiply(original.getOutputs(), copies);
    }
    @Override public boolean supportsPushInputsToExternalInventory() { return true; }
    @Override public void pushInputsToExternalInventory(KeyCounter[] totals, PatternInputSink sink) {
        // Reconstruct a disposable one-copy input table and let the original pattern emit
        // its sparse order. Scaling the emitted amounts also preserves duplicate slots.
        var single = new KeyCounter[totals.length];
        for (int i = 0; i < totals.length; i++) {
            single[i] = new KeyCounter();
            for (var entry : totals[i]) {
                long multiplier = inputScaling.multiplier(entry.getKey(), copies);
                if (entry.getLongValue() % multiplier != 0) throw new IllegalArgumentException("Invalid scaled inputs");
                single[i].add(entry.getKey(), entry.getLongValue() / multiplier);
            }
        }
        // Validate the entire emission before the first physical insertion.
        var emissions = new java.util.ArrayList<GenericStack>();
        original.pushInputsToExternalInventory(single,
                (key, amount) -> emissions.add(new GenericStack(key,
                        Math.multiplyExact(amount, inputScaling.multiplier(key, copies)))));
        var expected = new KeyCounter();
        for (var counter : totals) expected.addAll(counter);
        for (var entry : emissions) expected.remove(entry.what(), entry.amount());
        expected.removeZeros();
        if (!expected.isEmpty()) throw new IllegalArgumentException("Pattern did not emit the complete batch");
        for (var entry : emissions) sink.pushInput(entry.what(), entry.amount());
    }
    @Override public boolean equals(Object other) {
        return other == original || other instanceof ECOProcessingExecutionPattern p
                && original.equals(p.original) && copies == p.copies && inputScaling.equals(p.inputScaling);
    }
    @Override public int hashCode() { return original.hashCode(); }
    private record Input(IInput original, long copies, ECOBatchInputScaling scaling) implements IInput {
        public GenericStack[] getPossibleInputs() { return original.getPossibleInputs(); }
        public long getMultiplier() {
            if (scaling == ECOBatchInputScaling.LINEAR) return Math.multiplyExact(original.getMultiplier(), copies);
            var possible = original.getPossibleInputs();
            long multiplier = possible.length == 0 ? copies : scaling.multiplier(possible[0].what(), copies);
            return Math.multiplyExact(original.getMultiplier(), multiplier);
        }
        public boolean isValid(AEKey key, Level level) { return original.isValid(key, level); }
        public AEKey getRemainingKey(AEKey key) { return original.getRemainingKey(key); }
    }
}
