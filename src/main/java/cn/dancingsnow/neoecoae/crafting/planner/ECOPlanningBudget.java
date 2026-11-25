package cn.dancingsnow.neoecoae.crafting.planner;

import cn.dancingsnow.neoecoae.config.NEConfig;
import com.y271727uy.computatio.api.cycle.CycleBudget;
import com.y271727uy.computatio.api.cycle.CycleBudgetExhaustedException;
import com.y271727uy.computatio.api.cycle.CycleCheck;
import java.util.function.LongSupplier;

/**
 * One calculation's shared allowance: recipe discovery, alternate routes, seeds and cycle search. The allowance is a
 * Computatio {@link CycleBudget}, so the cycle algorithms charge the very same budget.
 */
public final class ECOPlanningBudget implements ECOCancellation {
    private final CycleBudget budget;

    public ECOPlanningBudget(ECOCancellation cancellation) {
        this(cancellation, Math.max(1L, NEConfig.ecoPlanningMaxWork),
            Math.max(1L, NEConfig.ecoPlanningMaxMillis) * 1_000_000L, System::nanoTime);
    }

    public ECOPlanningBudget(ECOCancellation cancellation, long maxWork, long maxNanos, LongSupplier clock) {
        if (maxWork < 1 || maxNanos < 1) throw new IllegalArgumentException("Planning budgets must be positive");
        this.budget = CycleBudget.of(check(cancellation), maxWork, maxNanos, clock);
    }

    private ECOPlanningBudget(CycleBudget budget) {
        this.budget = budget;
    }

    @Override
    public boolean improveCompletedPlans() { return budget.improveCompletedPlans(); }

    @Override
    public void checkpoint() throws InterruptedException {
        try {
            budget.checkpoint();
        } catch (CycleBudgetExhaustedException exhausted) {
            throw Exhausted.of(exhausted);
        }
    }

    /** Start once: at most 50 ms after the first executable plan, within the session's first 200 ms. */
    public void beginOptimization() { budget.beginOptimization(); }

    public long work() { return budget.work(); }

    /** Nested allowance for optional searches: never more than half of what remains, so exhausting it leaves this budget usable. */
    public ECOPlanningBudget slice(long maxSliceWork, long maxSliceNanos) {
        return new ECOPlanningBudget(budget.slice(maxSliceWork, maxSliceNanos));
    }

    /**
     * The Computatio view of a cancellation. A planning budget is handed over as its own allowance, so nested budgets
     * and the cycle algorithms share it. Any other cancellation is polled as is; when it reaches a planning budget of
     * its own (a lambda over a shared budget), that budget's exhaustion is reported as Computatio's, so the cycle
     * algorithms abandon optional stages exactly as they do for their own budget.
     */
    public static CycleCheck check(ECOCancellation cancellation) {
        if (cancellation == null) return CycleCheck.NONE;
        if (cancellation instanceof ECOPlanningBudget planning) return planning.budget;
        return new CycleCheck() {
            @Override public void checkpoint() throws InterruptedException {
                try {
                    cancellation.checkpoint();
                } catch (Exhausted exhausted) {
                    throw new CycleBudgetExhaustedException(exhausted.getMessage());
                }
            }
            @Override public boolean improveCompletedPlans() { return cancellation.improveCompletedPlans(); }
        };
    }

    /** Distinct from cancellation, internal errors and a proof of missing materials. */
    public static final class Exhausted extends RuntimeException {
        private Exhausted(String message) { super(message, null, false, false); }

        /** The planner's form of a Computatio budget exhaustion. */
        public static Exhausted of(CycleBudgetExhaustedException exhausted) {
            return new Exhausted(exhausted.getMessage());
        }
    }
}
