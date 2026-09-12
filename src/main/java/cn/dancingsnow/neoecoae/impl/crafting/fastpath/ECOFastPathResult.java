package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class ECOFastPathResult {
    private final boolean negative;
    private final List<GenericStack> outputEntries;
    private final List<GenericStack> remainingEntries;
    private final List<GenericStack> inputEntries;
    private final ECOReusableStateModel reusableStateModel;
    private final String rejectReason;
    private final long createdTick;

    private ECOFastPathResult(
        boolean negative,
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long lastAccessTick,
        @Nullable ECOReusableStateModel reusableStateModel,
        String rejectReason
    ) {
        this.negative = negative;
        this.outputEntries = List.copyOf(outputEntries);
        this.remainingEntries = List.copyOf(remainingEntries);
        this.inputEntries = List.copyOf(inputEntries);
        this.reusableStateModel = reusableStateModel;
        this.rejectReason = rejectReason == null ? "" : rejectReason;
        this.createdTick = lastAccessTick;
    }

    public static ECOFastPathResult positive(
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long tick
    ) {
        return positive(outputEntries, remainingEntries, inputEntries, tick, null);
    }

    public static ECOFastPathResult positive(
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long tick,
        ECOReusableStateModel reusableStateModel
    ) {
        return new ECOFastPathResult(false, outputEntries, remainingEntries, inputEntries, tick,
            reusableStateModel, "");
    }

    public static ECOFastPathResult negative(long tick, String rejectReason) {
        return new ECOFastPathResult(true, List.of(), List.of(), List.of(), tick, null, rejectReason);
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

    @Nullable
    public ECOReusableStateModel reusableStateModel() { return reusableStateModel; }

    @Nullable
    public ECODurabilityBatchModel durabilityModel() {
        return reusableStateModel instanceof ECODurabilityBatchModel durability ? durability : null;
    }

    public String rejectReason() { return rejectReason; }

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
