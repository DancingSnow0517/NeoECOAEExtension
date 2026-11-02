package cn.dancingsnow.neoecoae.crafting.planner;

@FunctionalInterface
public interface ECOCancellation {
    ECOCancellation NONE = () -> {};
    void checkpoint() throws InterruptedException;

    /** Search for an executable order first; optional improvements run on a separate budget. */
    default boolean improveCompletedPlans() { return true; }

    static ECOCancellation firstSolution(ECOCancellation cancellation) {
        return new ECOCancellation() {
            @Override public void checkpoint() throws InterruptedException { cancellation.checkpoint(); }
            @Override public boolean improveCompletedPlans() { return false; }
        };
    }
}
