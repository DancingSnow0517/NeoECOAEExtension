package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.compat.gregtech.ECOGregTechProcessingDispatch;
import java.util.List;

/** Test fixture for native GT lookup/worker identity and the adapted physical-input boundary. */
public class MEPatternBufferPartMachine implements ICraftingProvider, ECOGregTechProcessingDispatch.BatchInputProvider {
    public IPatternDetails pattern;
    public IPatternDetails workerPattern;
    public final KeyCounter stored = new KeyCounter();
    public int pushes;

    @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
    @Override public boolean isBusy() { return false; }

    @Override public boolean pushPattern(IPatternDetails candidate, KeyCounter[] inputs) {
        if (candidate != pattern) return false;
        workerPattern = candidate;
        pushes++;
        // Models the native invocation after GTMEPatternBufferMixin has adapted its argument.
        new InternalSlot().pushPattern(ECOGregTechProcessingDispatch.bufferExecutionPattern(candidate, inputs), inputs);
        return true;
    }

    public class InternalSlot {
        public void pushPattern(IPatternDetails execution, KeyCounter[] inputs) {
            execution.pushInputsToExternalInventory(inputs, stored::add);
        }
    }
}
