package cn.dancingsnow.neoecoae.api.me;

/** Network-wide control exposed by AE2's crafting service. */
public interface ECOFastPlanningControl {
    boolean isFastPlanningEnabled();

    void setFastPlanningEnabled(boolean enabled);
}
