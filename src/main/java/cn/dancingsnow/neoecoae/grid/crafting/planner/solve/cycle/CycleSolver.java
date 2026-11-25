package cn.dancingsnow.neoecoae.grid.crafting.planner.solve.cycle;

import cn.dancingsnow.neoecoae.grid.crafting.planner.control.ECOCancellation;

/** Internal cycle-solving contract, also used to exercise failure and recovery paths in tests. */
public interface CycleSolver {
    CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation) throws InterruptedException;
}
