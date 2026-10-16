package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** Legacy result/API boundary around the version-independent exact integer solver. */
public final class CycleStateEquation {
    public enum Status { OPTIMAL, FEASIBLE, INFEASIBLE, UNKNOWN }
    public record Result(Status status, PlannerAmount[] counts) {}
    private static Result adapt(cn.dancingsnow.neoecoae.core.planner.cycle.CycleStateEquation.Result result) {
        return new Result(Status.valueOf(result.status().name()), result.counts());
    }
    public static Result solveSparseMaterialBalance(List<Map<Integer, BigInteger>> columns, boolean[] imports,
            PlannerAmount[] stock, PlannerAmount[] target, boolean firstFeasible, ECOCancellation cancellation)
            throws InterruptedException {
        return solveSparseMaterialBalance(columns, imports, stock, target, Map.of(), firstFeasible, cancellation);
    }
    public static Result solveSparseMaterialBalance(List<Map<Integer, BigInteger>> columns, boolean[] imports,
            PlannerAmount[] stock, PlannerAmount[] target, Map<Integer, PlannerAmount> lowerBounds,
            boolean firstFeasible, ECOCancellation cancellation) throws InterruptedException {
        return adapt(cn.dancingsnow.neoecoae.core.planner.cycle.CycleStateEquation.solveSparseMaterialBalance(
            columns, imports, stock, target, lowerBounds, firstFeasible, cancellation, new CycleMemoryBudget()));
    }
    public static Result solveMaterialBalance(long[][] consumed, long[][] produced, boolean[] imports,
            PlannerAmount[] stock, PlannerAmount[] target, ECOCancellation cancellation) throws InterruptedException {
        return adapt(cn.dancingsnow.neoecoae.core.planner.cycle.CycleStateEquation.solveMaterialBalance(
            consumed, produced, imports, stock, target, cancellation, new CycleMemoryBudget()));
    }
    static Result solve(long[][] consumed, long[][] produced, boolean[] boundary, PlannerAmount[] stock,
                        PlannerAmount[] target, ECOCancellation cancellation) throws InterruptedException {
        return solve(consumed, produced, boundary, stock, target, cancellation, new CycleMemoryBudget());
    }
    static Result solve(long[][] consumed, long[][] produced, boolean[] boundary, PlannerAmount[] stock,
                        PlannerAmount[] target, ECOCancellation cancellation, CycleMemoryBudget memory)
                        throws InterruptedException {
        return adapt(cn.dancingsnow.neoecoae.core.planner.cycle.CycleStateEquation.solve(
            consumed, produced, boundary, stock, target, cancellation, memory));
    }
}
