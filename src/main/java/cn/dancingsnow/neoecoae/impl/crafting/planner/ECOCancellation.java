package cn.dancingsnow.neoecoae.impl.crafting.planner;

@FunctionalInterface
public interface ECOCancellation {
    ECOCancellation NONE = () -> {};
    void checkpoint() throws InterruptedException;
}
