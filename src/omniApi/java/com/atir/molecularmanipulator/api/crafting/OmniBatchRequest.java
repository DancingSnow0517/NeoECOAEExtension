package com.atir.molecularmanipulator.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.UUID;

/** Compile-only ABI declaration. Never shipped. */
public record OmniBatchRequest(UUID dispatchId, UUID craftingJobId, IPatternDetails pattern,
        long craftCount, List<Input> inputs, List<GenericStack> expectedOutputs) {
    public record Input(int slot, AEKey key, long amount) {}
}
