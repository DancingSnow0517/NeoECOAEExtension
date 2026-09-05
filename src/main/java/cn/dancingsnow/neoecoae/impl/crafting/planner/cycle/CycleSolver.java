package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;

/**
 * Plug-in extension point for cyclic SCC solving.
 *
 * <p>Implementations are called only after {@code ActiveRouteSelector} has proven that every ordinary
 * producer candidate for the route is itself cyclic, and only for a component whose required outputs
 * are non-empty. The acyclic DAG solver and its workspace are never handed to an implementation.
 */
public interface CycleSolver {
    CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation) throws InterruptedException;

    /**
     * Value-returning variant for callers that prefer a {@link CycleSolveStatus#CANCELLED} result over an
     * exception. The interrupt flag is restored so outer loops still observe the cancellation.
     */
    default CycleSolveResult solveOrCancelled(CycleSolveRequest request, ECOCancellation cancellation) {
        try {
            return solve(request, cancellation);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CycleSolveResult.cancelled();
        }
    }
}
