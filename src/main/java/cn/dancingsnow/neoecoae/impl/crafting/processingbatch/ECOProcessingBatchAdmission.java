package cn.dancingsnow.neoecoae.impl.crafting.processingbatch;

import java.util.Objects;
import java.util.function.BiFunction;

import appeng.api.stacks.KeyCounter;

/** One-shot admission for a prepared processing-provider batch. */
public final class ECOProcessingBatchAdmission {
    private final long count;
    private final KeyCounter[] preparedPrototype;
    private final BiFunction<KeyCounter[], Runnable, Boolean> commitAction;
    private ECOProcessingBatchProposal processingProposal;
    private boolean attempted;
    private boolean inputOwnershipTransferred;

    ECOProcessingBatchAdmission(
            long count,
            KeyCounter[] preparedPrototype,
            BiFunction<KeyCounter[], Runnable, Boolean> commitAction) {
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive");
        }
        this.count = count;
        this.preparedPrototype = Objects.requireNonNull(preparedPrototype, "preparedPrototype");
        this.commitAction = Objects.requireNonNull(commitAction, "commitAction");
    }

    ECOProcessingBatchAdmission(
            ECOProcessingBatchProposal proposal,
            KeyCounter[] preparedPrototype,
            BiFunction<KeyCounter[], Runnable, Boolean> commitAction) {
        this(
                Objects.requireNonNull(proposal, "proposal").requestedCrafts(),
                preparedPrototype,
                commitAction
        );
        this.processingProposal = proposal;
    }

    public long count() {
        return count;
    }

    public long requestedCount() {
        return processingProposal == null ? count : processingProposal.requestedCrafts();
    }

    public long physicalCapacity() {
        return processingProposal == null ? count : processingProposal.physicalCapacityCrafts();
    }

    public boolean hasTransferredInputOwnership() {
        return inputOwnershipTransferred;
    }

    /** Commits this admission at most once and only with its captured prototype. */
    public boolean commit(KeyCounter[] prototype) {
        if (prototype != preparedPrototype) {
            throw new IllegalArgumentException("Admission must use its prepared input prototype");
        }
        if (attempted) {
            throw new IllegalStateException("Processing batch admission has already been committed");
        }
        attempted = true;
        return commitAction.apply(prototype, this::transferInputOwnership);
    }

    private void transferInputOwnership() {
        if (inputOwnershipTransferred) {
            throw new IllegalStateException("Processing batch input ownership was transferred twice");
        }
        inputOwnershipTransferred = true;
    }
}
