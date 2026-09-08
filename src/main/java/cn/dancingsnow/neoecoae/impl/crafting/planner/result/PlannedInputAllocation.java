package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;

/** One slot's compressed, ordered concrete inputs. Counts are crafts, amounts are per craft. */
public record PlannedInputAllocation(int slot, List<Run> runs) {
    public PlannedInputAllocation {
        if (slot < 0) throw new IllegalArgumentException("Negative input slot");
        runs = List.copyOf(runs);
        if (runs.isEmpty()) throw new IllegalArgumentException("Empty allocation");
    }

    public record Run(AEKey key, long amount, long crafts) {
        public Run {
            Objects.requireNonNull(key);
            if (amount <= 0 || crafts <= 0) throw new IllegalArgumentException("Invalid input run");
        }
    }

    public long totalCrafts() {
        long total = 0;
        for (var run : runs) total = Math.addExact(total, run.crafts());
        return total;
    }

    public Run at(long completed) {
        if (completed < 0) throw new IllegalArgumentException("Negative progress");
        for (var run : runs) {
            if (completed < run.crafts()) return new Run(run.key(), run.amount(), run.crafts() - completed);
            completed -= run.crafts();
        }
        throw new IllegalStateException("Input allocation exhausted");
    }
}
