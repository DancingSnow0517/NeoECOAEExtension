package cn.dancingsnow.neoecoae.api.me;

/** Network-wide control for virtual F-series batch round-robin scheduling. */
public interface ECOBatchFairSchedulingControl {
    boolean isBatchFairSchedulingEnabled();

    void setBatchFairSchedulingEnabled(boolean enabled);
}
