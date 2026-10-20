package cn.dancingsnow.neoecoae.crafting.planner.growth;

import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolver;

/** Closed-form growth first, sharing the general solver's exact inventory replay and boundary accounting. */
public final class SinglePatternGrowthCycleSolver implements CycleSolver {
    private final SinglePatternGrowthCalculator calculator;
    private final CycleSolver fallback;

    public SinglePatternGrowthCycleSolver(CycleSolver fallback) {
        this(new SinglePatternGrowthCalculator(), fallback);
    }

    public SinglePatternGrowthCycleSolver(SinglePatternGrowthCalculator calculator, CycleSolver fallback) {
        if (fallback == null) throw new IllegalArgumentException("A growth solver needs a bounded fallback");
        if (calculator == null) throw new IllegalArgumentException("A growth solver needs a calculator");
        this.calculator = calculator;
        this.fallback = fallback;
    }

    /** Compatibility composition over the unified solver. */
    public static SinglePatternGrowthCycleSolver overBoundedSolver() {
        return new SinglePatternGrowthCycleSolver(new BoundedCycleSolver());
    }

    public SinglePatternGrowthCalculator calculator() {
        return calculator;
    }

    public CycleSolver fallback() {
        return fallback;
    }

    @Override
    public CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation)
            throws InterruptedException {
        if (!(cancellation instanceof ECOPlanningBudget)) cancellation = new ECOPlanningBudget(cancellation);
        CycleSolveResult growth = BoundedCycleSolver.solveGrowth(request, calculator, cancellation);
        return growth != null ? growth : fallback.solve(request, cancellation);
    }
}
