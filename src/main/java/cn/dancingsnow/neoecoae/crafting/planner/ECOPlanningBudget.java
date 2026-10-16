package cn.dancingsnow.neoecoae.crafting.planner;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.core.planner.PlanningBudget;
import java.util.function.LongSupplier;

/** Configuration and legacy exception adapter for the common planning allowance. */
public final class ECOPlanningBudget implements ECOCancellation {
    private final PlanningBudget budget;
    public ECOPlanningBudget(ECOCancellation cancellation) {
        this(cancellation, Math.max(1L, NEConfig.ecoPlanningMaxWork),
            Math.max(1L, NEConfig.ecoPlanningMaxMillis) * 1_000_000L, System::nanoTime);
    }
    public ECOPlanningBudget(ECOCancellation cancellation, long maxWork, long maxNanos, LongSupplier clock) {
        this(new PlanningBudget(cancellation, maxWork, maxNanos, clock));
    }
    private ECOPlanningBudget(PlanningBudget budget) { this.budget = budget; }
    @Override public void checkpoint() throws InterruptedException {
        try { budget.checkpoint(); }
        catch (PlanningBudget.Exhausted exhausted) { throw new Exhausted(exhausted.getMessage()); }
    }
    public ECOPlanningBudget slice(long maxSliceWork, long maxSliceNanos) {
        return new ECOPlanningBudget(budget.slice(maxSliceWork, maxSliceNanos));
    }
    /** Preserve the public failure type caught by callers predating the core split. */
    public static final class Exhausted extends PlanningBudget.Exhausted {
        private Exhausted(String message) { super(message); }
    }
}
