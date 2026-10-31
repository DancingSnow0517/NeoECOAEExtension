package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import java.util.List;
import java.util.Map;

/** Mirrors GTL's original-pattern admission and counted molecular-assembler output queue. */
public class MEMolecularAssemblerIOPartMachine implements ICraftingProvider {
    public IPatternDetails pattern;
    public long queued;
    public boolean toolsPresent = true;
    public boolean reject;
    public int pushes;
    public final KeyCounter received = new KeyCounter();

    @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
    @Override public boolean isBusy() { return false; }

    public boolean gtlcore$canProduceGraphPattern(IPatternDetails candidate) {
        return candidate == pattern && candidate instanceof AEProcessingPattern && toolsPresent;
    }

    public long gtlcore$graphCapacity(IPatternDetails candidate, Map<AEKey, Long> perRun, long requested) {
        if (!gtlcore$canProduceGraphPattern(candidate)) return 0;
        long outputAmount = pattern.getOutputs()[0].amount();
        if (queued < 0 || outputAmount <= 0) return 0;
        return Math.max(0, Math.min(requested, Long.MAX_VALUE / outputAmount - queued));
    }

    @Override public boolean pushPattern(IPatternDetails candidate, KeyCounter[] inputs) {
        if (candidate != pattern || !(candidate instanceof AEProcessingPattern) || reject) return false;
        var first = pattern.getInputs()[0];
        var required = first.getPossibleInputs()[0];
        long crafts = 0;
        for (var counter : inputs) {
            long amount = counter.get(required.what());
            if (amount > 0) crafts = amount / (required.amount() * first.getMultiplier());
        }
        if (crafts == 0) return false;
        pushes++;
        queued = Math.addExact(queued, crafts);
        for (var counter : inputs) received.addAll(counter);
        return true;
    }
}
