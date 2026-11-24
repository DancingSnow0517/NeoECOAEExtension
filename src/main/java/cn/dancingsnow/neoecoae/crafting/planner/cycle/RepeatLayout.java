package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import com.y271727uy.computatio.api.cycle.CycleSolving;
import com.y271727uy.computatio.api.cycle.RepeatRun;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    /** Computatio's {@link CycleSolving#lapMultipliers}; rejects invalid and overlapping circuits. */
    public static PlannerAmount[] plannerMultipliers(List<? extends Run> runs) {
        List<RepeatRun> converted = new ArrayList<>(runs.size());
        for (Run run : runs) {
            int width = run.repeatWidth();
            BigInteger repetitions = run.plannerRepetitions().toBigInteger();
            converted.add(new RepeatRun() {
                @Override public int repeatWidth() { return width; }
                @Override public BigInteger repetitions() { return repetitions; }
            });
        }
        BigInteger[] multipliers = CycleSolving.lapMultipliers(converted);
        PlannerAmount[] result = new PlannerAmount[multipliers.length];
        for (int i = 0; i < multipliers.length; i++) result[i] = PlannerAmount.of(multipliers[i]);
        return result;
    }
}
