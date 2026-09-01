package cn.dancingsnow.neoecoae.compat.thunderbolt;

/** Pure validation shared by optional external batch-provider integrations. */
public final class ECOExternalBatchContracts {
    private ECOExternalBatchContracts() {
    }

    public static long thunderboltRequest(long legalUpper, long providerCapacity, long cpuCopyBudget) {
        return Math.min(Math.max(0L, legalUpper),
            Math.min(Math.max(0L, providerCapacity), Math.max(0L, cpuCopyBudget)));
    }

    public static long acceptedFromLeftover(long requested, long leftover) {
        if (requested <= 0L) {
            throw new IllegalArgumentException("Requested external batch must be positive");
        }
        if (leftover < 0L || leftover > requested) {
            throw new IllegalArgumentException(
                "External batch leftover " + leftover + " is outside 0.." + requested);
        }
        return requested - leftover;
    }
}
