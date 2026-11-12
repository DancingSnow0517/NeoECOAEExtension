package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;

/** Internal cycle-solving contract, also used to exercise failure and recovery paths in tests. */
public interface CycleSolver {
    CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation) throws InterruptedException;
}
