package cn.dancingsnow.neoecoae.core.execution;

/** Per-pass admission ledger. Verified batches deliberately do not debit ordinary pushes. */
public class DispatchBudget {
    private final int ordinaryLimit;
    private final int probeLimit;
    private int normalProbes;
    private int acceptedNormalPushes;

    public DispatchBudget(int ordinaryLimit, int probeLimit) {
        this.ordinaryLimit = Math.max(0, ordinaryLimit);
        this.probeLimit = Math.max(0, probeLimit);
    }

    public boolean canAttemptOrdinary() {
        return ordinaryLimit > 0 && acceptedNormalPushes < ordinaryLimit && normalProbes < probeLimit;
    }

    public void recordNormalProbe() { normalProbes++; }
    public void recordAcceptedNormalPush() { acceptedNormalPushes++; }
    public int normalProbes() { return normalProbes; }
    public int acceptedNormalPushes() { return acceptedNormalPushes; }
}
