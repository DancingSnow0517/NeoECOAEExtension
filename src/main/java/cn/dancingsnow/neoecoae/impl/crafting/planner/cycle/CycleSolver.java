package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;

/** Extension point for a future real SCC solver. */
public interface CycleSolver {
    CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation) throws InterruptedException;
}
