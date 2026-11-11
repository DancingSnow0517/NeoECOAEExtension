package cn.dancingsnow.neoecoae.crafting.execution.batch;

/** Ownership, observed insertion and scheduling feedback for one materialized batch. */
public record ECOBatchAdmission(Status status, long acceptedCrafts, boolean canContinue,
        long fullyInsertedCrafts, StopReason stopReason) {
    public enum Status { ACCEPTED, REJECTED, INDETERMINATE }

    public enum StopReason {
        NONE,
        CAPACITY,
        BUFFERED,
        RESOURCES,
        TARGET_UNAVAILABLE,
        FAIRNESS,
        TRANSPORT_LIMIT,
        UNKNOWN
    }

    /** Legacy queue receipts do not prove physical machine capacity, even when they can continue. */
    public ECOBatchAdmission(Status status, long acceptedCrafts, boolean canContinue) {
        this(status, acceptedCrafts, canContinue, 0L, canContinue ? StopReason.NONE : StopReason.UNKNOWN);
    }

    public ECOBatchAdmission {
        java.util.Objects.requireNonNull(status, "status");
        java.util.Objects.requireNonNull(stopReason, "stopReason");
        if (acceptedCrafts < 0L) throw new IllegalArgumentException("acceptedCrafts must not be negative");
        if (status == Status.ACCEPTED && acceptedCrafts <= 0L) {
            throw new IllegalArgumentException("Accepted batch must contain at least one craft");
        }
        if (status != Status.ACCEPTED && acceptedCrafts != 0L) {
            throw new IllegalArgumentException("Rejected or indeterminate batch cannot report accepted crafts");
        }
        if (fullyInsertedCrafts < 0L || fullyInsertedCrafts > acceptedCrafts) {
            throw new IllegalArgumentException("Insertion proof must be within the accepted batch");
        }
        if (canContinue && (status != Status.ACCEPTED || stopReason != StopReason.NONE)) {
            throw new IllegalArgumentException("Only an accepted batch without a stop reason can continue");
        }
    }

    public static ECOBatchAdmission accepted(long crafts, boolean canContinue) {
        return new ECOBatchAdmission(Status.ACCEPTED, crafts, canContinue);
    }

    /** Only confirmed, fully inserted sub-batches contribute to this conservative capacity proof. */
    public static ECOBatchAdmission accepted(long crafts, long fullyInsertedCrafts, StopReason stopReason) {
        return new ECOBatchAdmission(Status.ACCEPTED, crafts, stopReason == StopReason.NONE,
                fullyInsertedCrafts, stopReason);
    }

    public static ECOBatchAdmission rejected() {
        return rejected(StopReason.UNKNOWN);
    }

    public static ECOBatchAdmission rejected(StopReason stopReason) {
        return new ECOBatchAdmission(Status.REJECTED, 0L, false, 0L, stopReason);
    }

    public static ECOBatchAdmission indeterminate() {
        return new ECOBatchAdmission(Status.INDETERMINATE, 0L, false);
    }
}
