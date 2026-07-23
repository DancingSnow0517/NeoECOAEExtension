package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

public record ECOSolveBudget(
    long maxExpandedStates,
    int maxDepth,
    int extraBatchChoices
) {
    public static final ECOSolveBudget DEFAULT = new ECOSolveBudget(50_000, 256, 2);

    public ECOSolveBudget {
        if (maxExpandedStates <= 0 || maxDepth <= 0 || extraBatchChoices < 0) {
            throw new IllegalArgumentException("Invalid ECO solve budget");
        }
    }
}
