package cn.dancingsnow.neoecoae.crafting.execution.batch;

/** The provider's synchronous ownership receipt for one batch attempt. */
public record ECOBatchAdmission(Status status, long acceptedCrafts, boolean canContinue) {
    public enum Status {
        ACCEPTED,
        REJECTED,
        INDETERMINATE
    }

    public ECOBatchAdmission {
        if (status == null
                || acceptedCrafts < 0L
                || status == Status.ACCEPTED && acceptedCrafts == 0L
                || status != Status.ACCEPTED && acceptedCrafts != 0L) {
            throw new IllegalArgumentException("Invalid batch admission");
        }
    }

    public static ECOBatchAdmission accepted(long count, boolean canContinue) {
        return new ECOBatchAdmission(Status.ACCEPTED, count, canContinue);
    }

    public static ECOBatchAdmission rejected() {
        return new ECOBatchAdmission(Status.REJECTED, 0L, false);
    }

    public static ECOBatchAdmission indeterminate() {
        return new ECOBatchAdmission(Status.INDETERMINATE, 0L, false);
    }
}
