package cn.dancingsnow.neoecoae.api.me;

/** Fairness budgets shared by the crafting CPU ready queue. */
public final class ECOFairReadyQueue {
    public static final int IMMEDIATE_BURST_LIMIT = 8;
    public static final int NORMAL_BURST_LIMIT = 8;

    private ECOFairReadyQueue() {
    }
}
