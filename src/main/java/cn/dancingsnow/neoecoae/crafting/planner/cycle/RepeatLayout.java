package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import java.util.Arrays;
import java.util.List;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

/** Flat, non-nested recipe circuits. The last run carries the width and total lap count. */
public final class RepeatLayout {
    private RepeatLayout() {}

    public interface Run {
        int repeatWidth();
        long repetitions();
        default PlannerAmount plannerRepetitions() { return PlannerAmount.of(repetitions()); }
    }

    public static long[] multipliers(List<? extends Run> runs) {
        return Arrays.stream(plannerMultipliers(runs)).mapToLong(PlannerAmount::longValueSaturated).toArray();
    }

    public static PlannerAmount[] plannerMultipliers(List<? extends Run> runs) {
        PlannerAmount[] result = new PlannerAmount[runs.size()];
        Arrays.fill(result, PlannerAmount.ONE);
        int previousEnd = -1;
        for (int end = 0; end < runs.size(); end++) {
            Run run = runs.get(end);
            if (run.plannerRepetitions().signum() <= 0 || run.repeatWidth() < 1
                    || run.plannerRepetitions().equals(PlannerAmount.ONE) && run.repeatWidth() != 1) {
                throw new IllegalArgumentException("Invalid recipe circuit repeat");
            }
            if (run.plannerRepetitions().equals(PlannerAmount.ONE)) continue;
            int start = end - run.repeatWidth() + 1;
            if (start < 0 || start <= previousEnd) {
                throw new IllegalArgumentException("Overlapping recipe circuit repeats");
            }
            Arrays.fill(result, start, end + 1, run.plannerRepetitions());
            previousEnd = end;
        }
        return result;
    }
}
