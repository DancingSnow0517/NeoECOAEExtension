package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.List;
import java.util.Objects;

/** One slot's compressed, ordered concrete inputs. Counts are crafts, amounts are per craft. */
public record PlannedInputAllocation(int slot, List<Run> runs) {
    public PlannedInputAllocation {
        if (slot < 0) throw new IllegalArgumentException("Negative input slot");

        if (runs.isEmpty()) throw new IllegalArgumentException("Empty allocation");
    }

    public record Run(AEKey key, PlannerAmount plannerAmount, PlannerAmount plannerCrafts) {
        public Run(AEKey key, long amount, long crafts) {
            this(key, PlannerAmount.of(amount), PlannerAmount.of(crafts));
        }
        public Run {
            Objects.requireNonNull(key);
            if (plannerAmount.signum() <= 0 || plannerCrafts.signum() <= 0) throw new IllegalArgumentException("Invalid input run");
        }
        public long amount() { return plannerAmount.longValueSaturated(); }
        public long crafts() { return plannerCrafts.longValueSaturated(); }
    }

    public long totalCrafts() {
        return plannerTotalCrafts().longValueSaturated();
    }

    public PlannerAmount plannerTotalCrafts() {
        PlannerAmount total = PlannerAmount.ZERO;
        for (var run : runs) total = total.add(run.plannerCrafts());
        return total;
    }

    public Run at(long completed) {
        return at(PlannerAmount.of(completed));
    }

    public Run at(PlannerAmount completed) {
        if (completed.signum() < 0) throw new IllegalArgumentException("Negative progress");
        for (var run : runs) {
            if (completed.compareTo(run.plannerCrafts()) < 0)
                return new Run(run.key(), run.plannerAmount(), run.plannerCrafts().subtract(completed));
            completed = completed.subtract(run.plannerCrafts());
        }
        throw new IllegalStateException("Input allocation exhausted");
    }
}
