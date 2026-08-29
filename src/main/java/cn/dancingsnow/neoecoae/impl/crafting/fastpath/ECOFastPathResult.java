package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;

public final class ECOFastPathResult {
    private final boolean negative;
    private final List<GenericStack> outputEntries;
    private final List<GenericStack> remainingEntries;
    private final List<GenericStack> inputEntries;
    private final long createdTick;

    private ECOFastPathResult(
        boolean negative,
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long lastAccessTick
    ) {
        this.negative = negative;
        this.outputEntries = List.copyOf(outputEntries);
        this.remainingEntries = List.copyOf(remainingEntries);
        this.inputEntries = List.copyOf(inputEntries);
        this.createdTick = lastAccessTick;
    }

    public static ECOFastPathResult positive(
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long tick
    ) {
        return new ECOFastPathResult(false, outputEntries, remainingEntries, inputEntries, tick);
    }

    public static ECOFastPathResult negative(long tick) {
        return new ECOFastPathResult(true, List.of(), List.of(), List.of(), tick);
    }

    public boolean isNegative() {
        return negative;
    }

    public List<GenericStack> outputEntries() {
        return outputEntries;
    }

    public List<GenericStack> remainingEntries() {
        return remainingEntries;
    }

    public List<GenericStack> inputEntries() {
        return inputEntries;
    }

    /**
     * Full value comparison against a dispatch's expected data. Called exactly once per dispatch, from
     * {@link ECOCraftingFastPathCache#lookup}; downstream stages carry the resulting
     * {@link ECOVerifiedFastPathRecipe} instead of repeating it.
     */
    public boolean matchesExecution(ECOExtractedPatternExecution execution) {
        return !negative
            && outputEntries.equals(execution.expectedOutputs())
            && remainingEntries.equals(execution.expectedContainerItems())
            && inputEntries.equals(execution.inputItems());
    }

    public long getCreatedTick() {
        return createdTick;
    }
}
