package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveDiagnostic;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveMetrics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cycle-layer entry point: try the exact single-pattern net growth algebra first, otherwise hand the
 * component to the bounded solver untouched.
 *
 * <pre>
 *   CycleComponent
 *        ↓
 *   eligible for SinglePatternGrowthCalculator?
 *        ├─ yes → algebraic solve → SUCCESS
 *        └─ no  → BoundedCycleSolver
 * </pre>
 *
 * <p>Only {@link SinglePatternGrowthStatus#SUCCESS} is adopted. Anything else — not applicable, a proven
 * seed shortfall, an overflow — falls through to {@link BoundedCycleSolver} with no side effect, and the
 * reason is recorded as a {@link CycleSolveDiagnostic.Code#NET_GROWTH_NOT_APPLICABLE} annotation on the
 * bounded answer. A declined fast path never turns a cycle into a missing or unsupported verdict.
 */
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

    /** Canonical composition: the algebraic fast path over the stage-one bounded search. */
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
        cancellation.checkpoint();
        SinglePatternGrowthResult growth = calculator.evaluate(request);
        if (growth.status().solved()) return adopt(growth);
        return fallback.solve(request, cancellation).withAdditionalDiagnostics(List.of(new CycleSolveDiagnostic(
            CycleSolveDiagnostic.Code.NET_GROWTH_NOT_APPLICABLE, growth.summary())));
    }

    /** Maps an exact growth answer onto the cycle contract, with compact execution metadata. */
    private static CycleSolveResult adopt(SinglePatternGrowthResult growth) {
        IPatternDetails details = growth.details();
        List<CycleSolveDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.SINGLE_PATTERN_NET_GROWTH,
            growth.diagnostic()));
        diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.SEED_COVERED_BY_STOCK,
            "Start-up seed " + growth.consumePerFiring() + " of " + growth.feedbackKey()
                + " is covered by the stock snapshot and is not consumed again by later firings"));
        int keys = growth.profile() == null ? 0 : growth.profile().touchedKeys().size();
        // One transition, one algebraic evaluation: nothing was searched and nothing was truncated.
        CycleSolveMetrics metrics = new CycleSolveMetrics(keys, 1, 1, 1, 0, 0, false, false, false);
        return new CycleSolveResult(
            CycleSolveStatus.SUCCESS,
            Map.of(details, growth.firings()),
            growth.externalDemand(),
            growth.requiredSeed(),
            Map.of(),
            growth.producedOutputs(),
            growth.deliverableOutputs(),
            List.of(),
            growth.executionPlan(),
            diagnostics,
            metrics
        );
    }
}
