package cn.dancingsnow.neoecoae.crafting.execution.batch;

import java.util.Objects;

/** Pure planning output. Creating this object must not consume, mutate, or cache execution state. */
public record ECOBatchPlan(ECOPatternIdentity identity, long craftCount, ECOBatchMode mode, ECOBatchLimits limits) {
    public ECOBatchPlan {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(limits, "limits");
        if (craftCount <= 0L) throw new IllegalArgumentException("craftCount must be positive");
        if (craftCount > limits.minimum()) throw new IllegalArgumentException("craftCount exceeds planned limits");
    }
}
