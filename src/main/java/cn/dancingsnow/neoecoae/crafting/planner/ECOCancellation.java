package cn.dancingsnow.neoecoae.crafting.planner;

@FunctionalInterface
public interface ECOCancellation {
    ECOCancellation NONE = () -> {};
    void checkpoint() throws InterruptedException;
}
