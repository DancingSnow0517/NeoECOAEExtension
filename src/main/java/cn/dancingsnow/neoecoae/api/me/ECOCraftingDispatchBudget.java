package cn.dancingsnow.neoecoae.api.me;

/** Mutable per-pass ordinary-path accounting; verified batches deliberately do not consume it. */
final class ECOCraftingDispatchBudget {
    private final int ordinaryLimit;
    private final int probeLimit;
    private int normalProbes;
    private int acceptedNormalPushes;

    ECOCraftingDispatchBudget(int ordinaryLimit, int probeLimit) {
        this.ordinaryLimit = Math.max(0, ordinaryLimit);
        this.probeLimit = Math.max(0, probeLimit);
    }

    boolean canAttemptOrdinary() {
        return ordinaryLimit > 0
                && acceptedNormalPushes < ordinaryLimit
                && normalProbes < probeLimit;
    }

    void recordNormalProbe() {
        normalProbes++;
    }

    void recordAcceptedNormalPush() {
        acceptedNormalPushes++;
    }

    int normalProbes() {
        return normalProbes;
    }

    int acceptedNormalPushes() {
        return acceptedNormalPushes;
    }
}
