package cn.dancingsnow.neoecoae.grid.crafting.execution.dispatch;

/** Mutable per-pass ordinary-path accounting; verified batches deliberately do not consume it. */
public final class ECOCraftingDispatchBudget {
    private final int ordinaryLimit;
    private final int probeLimit;
    private int normalProbes;
    private int acceptedNormalPushes;

    public ECOCraftingDispatchBudget(int ordinaryLimit, int probeLimit) {
        this.ordinaryLimit = Math.max(0, ordinaryLimit);
        this.probeLimit = Math.max(0, probeLimit);
    }

    public boolean canAttemptOrdinary() {
        return ordinaryLimit > 0
                && acceptedNormalPushes < ordinaryLimit
                && normalProbes < probeLimit;
    }

    public void recordNormalProbe() {
        normalProbes++;
    }

    public void recordAcceptedNormalPush() {
        acceptedNormalPushes++;
    }

    public int normalProbes() {
        return normalProbes;
    }

    public int acceptedNormalPushes() {
        return acceptedNormalPushes;
    }
}
