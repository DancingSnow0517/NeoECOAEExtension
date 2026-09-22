package cn.dancingsnow.neoecoae.crafting.execution.batch;

/** Provider ownership receipt for the current materialized batch, never a capacity prediction. */
public record ECOBatchAdmission(Status status, long acceptedCrafts, boolean canContinue) {
    public enum Status { ACCEPTED, REJECTED, INDETERMINATE }

    public ECOBatchAdmission {
        java.util.Objects.requireNonNull(status, "status");
        if (acceptedCrafts < 0L) throw new IllegalArgumentException("acceptedCrafts must not be negative");
        if (status == Status.ACCEPTED && acceptedCrafts <= 0L) {
            throw new IllegalArgumentException("Accepted batch must contain at least one craft");
        }
        if (status != Status.ACCEPTED && acceptedCrafts != 0L) {
            throw new IllegalArgumentException("Rejected or indeterminate batch cannot report accepted crafts");
        }
    }

    public static ECOBatchAdmission accepted(long crafts, boolean canContinue) {
        return new ECOBatchAdmission(Status.ACCEPTED, crafts, canContinue);
    }

    public static ECOBatchAdmission rejected() {
        return new ECOBatchAdmission(Status.REJECTED, 0L, false);
    }

    public static ECOBatchAdmission indeterminate() {
        return new ECOBatchAdmission(Status.INDETERMINATE, 0L, false);
    }
}
