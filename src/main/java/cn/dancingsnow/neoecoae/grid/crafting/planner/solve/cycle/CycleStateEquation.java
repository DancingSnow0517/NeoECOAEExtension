package cn.dancingsnow.neoecoae.grid.crafting.planner.solve.cycle;

import cn.dancingsnow.neoecoae.grid.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.grid.crafting.planner.control.ECOCancellation;
import cn.dancingsnow.neoecoae.grid.crafting.planner.control.ECOPlanningBudget;
import com.y271727uy.computatio.api.cycle.CycleBudgetExhaustedException;
import com.y271727uy.computatio.api.cycle.CycleSolving;
import com.y271727uy.computatio.api.cycle.MaterialBalance;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Necessary material balance, independent of firing order: stock + (out - in) x >= target, x >= 0, solved by
 * Computatio's {@link CycleSolving#balance}. Infeasibility is a proof; a feasible vector is never a liveness proof.
 * Boundary ingredients have unlimited imports here; the caller still verifies their real DAG supply.
 */
public final class CycleStateEquation {
    public enum Status { OPTIMAL, FEASIBLE, INFEASIBLE, UNKNOWN }
    public record Result(Status status, PlannerAmount[] counts) {}

    private CycleStateEquation() {}

    /** Sparse net consumption columns; gross inputs still require execution verification. */
    public static Result solveSparseAmounts(List<Map<Integer, PlannerAmount>> columns, boolean[] imports,
            PlannerAmount[] stock, PlannerAmount[] target, boolean firstFeasible,
            ECOCancellation cancellation) throws InterruptedException {
        return solveSparseAmounts(columns, imports, stock, target, Map.of(), firstFeasible, cancellation);
    }

    /** Optional proven or heuristic lower bounds restrict only this candidate search, never the original graph. */
    public static Result solveSparseAmounts(List<Map<Integer, PlannerAmount>> columns, boolean[] imports,
            PlannerAmount[] stock, PlannerAmount[] target, Map<Integer, PlannerAmount> lowerBounds,
            boolean firstFeasible, ECOCancellation cancellation) throws InterruptedException {
        List<Map<Integer, BigInteger>> exact = new ArrayList<>(columns.size());
        for (Map<Integer, PlannerAmount> column : columns) exact.add(bigIntegers(column));
        MaterialBalance balance;
        try {
            balance = CycleSolving.balance(exact, imports, bigIntegers(stock), bigIntegers(target),
                lowerBounds == null ? null : bigIntegers(lowerBounds), firstFeasible, ECOPlanningBudget.check(cancellation),
                BoundedCycleSolver.maxMemoryBytes());
        } catch (CycleBudgetExhaustedException exhausted) {
            throw ECOPlanningBudget.Exhausted.of(exhausted);
        }
        BigInteger[] counts = balance.counts();
        PlannerAmount[] amounts = null;
        if (counts != null) {
            amounts = new PlannerAmount[counts.length];
            for (int i = 0; i < counts.length; i++) amounts[i] = PlannerAmount.of(counts[i]);
        }
        Status status = status(balance.status().name());
        // Old Computatio statuses map one to one; an unknown one carries no vector the planner may trust.
        return new Result(status, status == Status.UNKNOWN ? null : amounts);
    }

    /** An unknown verdict of a newer Computatio is never a proof: it reads as {@link Status#UNKNOWN}. */
    private static Status status(String name) {
        for (Status status : Status.values()) if (status.name().equals(name)) return status;
        return Status.UNKNOWN;
    }

    private static BigInteger[] bigIntegers(PlannerAmount[] values) {
        BigInteger[] result = new BigInteger[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i].toBigInteger();
        return result;
    }

    private static Map<Integer, BigInteger> bigIntegers(Map<Integer, PlannerAmount> values) {
        Map<Integer, BigInteger> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, value.toBigInteger()));
        return result;
    }
}
