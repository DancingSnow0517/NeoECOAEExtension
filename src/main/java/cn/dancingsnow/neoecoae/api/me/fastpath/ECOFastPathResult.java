package cn.dancingsnow.neoecoae.api.me.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;

public final class ECOFastPathResult {
    private final boolean negative;
    private final boolean verified;
    private final List<GenericStack> outputEntries;
    private final List<GenericStack> remainingEntries;
    private final List<GenericStack> inputEntries;
    private final long createdTick;
    private long lastAccessTick;

    private ECOFastPathResult(
        boolean negative,
        boolean verified,
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long lastAccessTick
    ) {
        this.negative = negative;
        this.verified = verified;
        this.outputEntries = List.copyOf(outputEntries);
        this.remainingEntries = List.copyOf(remainingEntries);
        this.inputEntries = List.copyOf(inputEntries);
        this.createdTick = lastAccessTick;
        this.lastAccessTick = lastAccessTick;
    }

    public static ECOFastPathResult positive(
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long tick
    ) {
        return new ECOFastPathResult(false, true, outputEntries, remainingEntries, inputEntries, tick);
    }

    public static ECOFastPathResult negative(long tick) {
        return new ECOFastPathResult(true, false, List.of(), List.of(), List.of(), tick);
    }

    public boolean isNegative() {
        return negative;
    }

    public boolean isVerified() {
        return verified;
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

    public boolean matchesExecution(ECOExtractedPatternExecution execution) {
        return !negative
            && verified
            && outputEntries.equals(execution.expectedOutputs())
            && remainingEntries.equals(execution.expectedContainerItems())
            && inputEntries.equals(execution.inputItems());
    }

    public boolean matchesBatchRequest(ECOBatchCraftingRequest request) {
        return !negative
            && verified
            && outputEntries.equals(request.outputsPerCraft())
            && remainingEntries.equals(request.remainingPerCraft())
            && inputEntries.equals(request.inputsPerCraft());
    }

    public void touch(long tick) {
        this.lastAccessTick = tick;
    }

    public long getLastAccessTick() {
        return lastAccessTick;
    }

    public long getCreatedTick() {
        return createdTick;
    }
}
