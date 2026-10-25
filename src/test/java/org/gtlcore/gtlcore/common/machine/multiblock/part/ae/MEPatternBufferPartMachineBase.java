package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.Map;

/** Test fixture for GTL's canonical-pattern admission, long counters and graph capacity contract. */
public class MEPatternBufferPartMachineBase implements ICraftingProvider {
    public IPatternDetails pattern;
    public final KeyCounter stored = new KeyCounter();
    public boolean reject;
    public boolean failAfterInsertion;
    public int pushes;
    public int probes;

    @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
    @Override public boolean isBusy() { return false; }

    @Override public boolean pushPattern(IPatternDetails candidate, KeyCounter[] inputs) {
        if (candidate != pattern || reject) return false;
        pushes++;
        for (var counter : inputs) stored.addAll(counter);
        if (failAfterInsertion) throw new IllegalStateException("Notification failed after native insertion");
        return true;
    }

    public long gtlcore$graphCapacity(IPatternDetails candidate, Map<AEKey, Long> perRun, long requested) {
        probes++;
        if (candidate != pattern) return 0;
        for (var input : perRun.entrySet()) {
            long held = stored.get(input.getKey());
            if (held < 0) return 0;
            requested = Math.min(requested, (Long.MAX_VALUE - held) / input.getValue());
        }
        return requested;
    }
}
