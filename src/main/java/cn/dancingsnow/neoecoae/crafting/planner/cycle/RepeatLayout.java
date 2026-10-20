package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import java.util.Arrays;
import java.util.List;

/** Flat, non-nested recipe circuits. The last run carries the width and total lap count. */
public final class RepeatLayout {
    private RepeatLayout() {}

    public interface Run {
        int repeatWidth();
        long repetitions();
    }

    public static long[] multipliers(List<? extends Run> runs) {
        long[] result = new long[runs.size()];
        Arrays.fill(result, 1L);
        int previousEnd = -1;
        for (int end = 0; end < runs.size(); end++) {
            Run run = runs.get(end);
            if (run.repetitions() < 1 || run.repeatWidth() < 1
                    || run.repetitions() == 1 && run.repeatWidth() != 1) {
                throw new IllegalArgumentException("Invalid recipe circuit repeat");
            }
            if (run.repetitions() == 1) continue;
            int start = end - run.repeatWidth() + 1;
            if (start < 0 || start <= previousEnd) {
                throw new IllegalArgumentException("Overlapping recipe circuit repeats");
            }
            Arrays.fill(result, start, end + 1, run.repetitions());
            previousEnd = end;
        }
        return result;
    }
}
