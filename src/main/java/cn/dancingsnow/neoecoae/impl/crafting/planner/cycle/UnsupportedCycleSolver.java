package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;

/** Stage-one implementation: reports capability honestly and never throws for a supported request shape. */
public final class UnsupportedCycleSolver implements CycleSolver {
    @Override
    public CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation)
            throws InterruptedException {
        cancellation.checkpoint();
        return new CycleSolveResult(CyclePlanningStatus.NOT_IMPLEMENTED,
            "Cycle solver is intentionally not implemented in this phase");
    }
}
