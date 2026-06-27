package cn.dancingsnow.neoecoae.api.me.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;

public final class ECOFastPathResult {
    private final boolean negative;
    private final boolean verified;
    private final List<GenericStack> outputEntries;
    private final List<GenericStack> remainingEntries;
    private final List<GenericStack> inputEntries;
    private long lastAccessTick;
    private static final int INITIAL_RECOMMENDED_BATCH_SIZE = 4;
    private int recommendedBatchSize = INITIAL_RECOMMENDED_BATCH_SIZE;

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

    public void touch(long tick) {
        this.lastAccessTick = tick;
    }

    public long getLastAccessTick() {
        return lastAccessTick;
    }

    public int getRecommendedBatchSize(int requested) {
        return Math.max(1, Math.min(requested, recommendedBatchSize));
    }

    public void recordBatchSuccess(int batchSize, int requested) {
        if (batchSize <= 1) {
            return;
        }
        int next = batchSize >= recommendedBatchSize ? batchSize * 2 : recommendedBatchSize;
        recommendedBatchSize = Math.max(1, Math.min(requested, next));
    }

    public void recordBatchFailure(int attemptedBatchSize) {
        if (attemptedBatchSize <= 1) {
            recommendedBatchSize = INITIAL_RECOMMENDED_BATCH_SIZE;
            return;
        }
        recommendedBatchSize = Math.max(1, attemptedBatchSize / 2);
    }
}
