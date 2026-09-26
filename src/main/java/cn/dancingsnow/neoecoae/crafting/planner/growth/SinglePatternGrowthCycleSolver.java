package cn.dancingsnow.neoecoae.crafting.planner.growth;

import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolver;

/** Compatibility entry point. Cycle planning now shares one state-equation and witness implementation. */
public final class SinglePatternGrowthCycleSolver implements CycleSolver {
    private final SinglePatternGrowthCalculator calculator;
    private final CycleSolver fallback;

    public SinglePatternGrowthCycleSolver(CycleSolver fallback) {
        this(new SinglePatternGrowthCalculator(), fallback);
    }

    public SinglePatternGrowthCycleSolver(SinglePatternGrowthCalculator calculator, CycleSolver fallback) {
        if (fallback == null) throw new IllegalArgumentException("A growth solver needs a bounded fallback");
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
        return fallback.solve(request, cancellation);
    }
}
