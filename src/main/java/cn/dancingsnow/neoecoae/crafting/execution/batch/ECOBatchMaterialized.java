package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import java.util.Objects;

/** One-shot materialized dispatch attempt. It cannot be reused after commit or rollback. */
public final class ECOBatchMaterialized {
    private final ECOPatternIdentity identity;
    private final IPatternDetails originalPattern;
    private final long craftCount;
    private final KeyCounter[] inputCounters;
    private final KeyCounter outputCounter;
    private final KeyCounter remainderCounter;
    private final ECOBatchInputLease inputLease;
    private final ECOBatchExecutionView executionView;
    private boolean settled;

    public ECOBatchMaterialized(ECOPatternIdentity identity, long craftCount, KeyCounter[] inputCounters,
            KeyCounter outputCounter, KeyCounter remainderCounter, ECOBatchInputLease inputLease,
            ECOBatchExecutionView executionView) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.originalPattern = identity.originalPattern();
        this.craftCount = craftCount;
        this.inputCounters = inputCounters.clone();
        this.outputCounter = outputCounter;
        this.remainderCounter = remainderCounter;
        this.inputLease = Objects.requireNonNull(inputLease, "inputLease");
        this.executionView = Objects.requireNonNull(executionView, "executionView");
    }

    public ECOPatternIdentity identity() { return identity; }
    public IPatternDetails originalPattern() { return originalPattern; }
    public long craftCount() { return craftCount; }
    public KeyCounter[] inputCounters() { ensureOpen(); return inputCounters.clone(); }
    public KeyCounter outputCounter() { ensureOpen(); return outputCounter; }
    public KeyCounter remainderCounter() { ensureOpen(); return remainderCounter; }
    public ECOBatchInputLease inputLease() { ensureOpen(); return inputLease; }
    public ECOBatchExecutionView executionView() { ensureOpen(); return executionView; }

    public void commit() {
        ensureOpen();
        inputLease.commit();
        settled = true;
    }

    public void rollback() {
        ensureOpen();
        inputLease.rollback();
        settled = true;
    }

    private void ensureOpen() {
        if (settled) throw new IllegalStateException("Materialized batch is already settled");
    }
}
