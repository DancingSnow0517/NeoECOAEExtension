package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.Arrays;

public record CycleCircuitSummary(PlannerAmount[] required, PlannerAmount[] delta,
        PlannerAmount[] counts) {
    public static CycleCircuitSummary empty(int keys, int transitions) {
        return new CycleCircuitSummary(zeroes(keys), zeroes(keys), zeroes(transitions));
    }

    public static CycleCircuitSummary run(PlannerAmount[] consumed, PlannerAmount[] produced,
            int transitions, int transition, PlannerAmount count) {
        CycleCircuitSummary result = empty(consumed.length, transitions);
        for (int key = 0; key < consumed.length; key++) {
            result.required[key] = batchDeficit(consumed[key], produced[key], count);
            result.delta[key] = produced[key].subtract(consumed[key]).multiply(count);
        }
        result.counts[transition] = count;
        return result;
    }

    public CycleCircuitSummary then(CycleCircuitSummary next) {
        PlannerAmount[] combinedRequired = required.clone();
        PlannerAmount[] combinedDelta = delta.clone();
        PlannerAmount[] combinedCounts = counts.clone();
        for (int key = 0; key < combinedRequired.length; key++) {
            combinedRequired[key] = combinedRequired[key].max(next.required[key].subtract(combinedDelta[key]));
            combinedDelta[key] = combinedDelta[key].add(next.delta[key]);
        }
        for (int index = 0; index < combinedCounts.length; index++) {
            combinedCounts[index] = combinedCounts[index].add(next.counts[index]);
        }
        return new CycleCircuitSummary(combinedRequired, combinedDelta, combinedCounts);
    }

    public CycleCircuitSummary repeated(PlannerAmount times) {
        if (times.signum() <= 0) throw new IllegalArgumentException("Circuit repetitions must be positive");
        PlannerAmount[] repeatedRequired = required.clone();
        PlannerAmount[] repeatedDelta = delta.clone();
        PlannerAmount[] repeatedCounts = counts.clone();
        for (int key = 0; key < repeatedRequired.length; key++) {
            if (repeatedDelta[key].signum() < 0) {
                repeatedRequired[key] = repeatedRequired[key].subtract(repeatedDelta[key].multiply(times.subtract(1L)));
            }
            repeatedDelta[key] = repeatedDelta[key].multiply(times);
        }
        for (int index = 0; index < repeatedCounts.length; index++) {
            repeatedCounts[index] = repeatedCounts[index].multiply(times);
        }
        return new CycleCircuitSummary(repeatedRequired, repeatedDelta, repeatedCounts);
    }

    private static PlannerAmount batchDeficit(PlannerAmount consumed, PlannerAmount produced, PlannerAmount count) {
        if (consumed.signum() <= 0 || count.signum() <= 0) return PlannerAmount.ZERO;
        return produced.compareTo(consumed) >= 0 ? consumed
            : consumed.multiply(count).subtract(produced.multiply(count.subtract(1L))).max(PlannerAmount.ZERO);
    }

    public static PlannerAmount[] zeroes(int size) {
        PlannerAmount[] result = new PlannerAmount[size];
        Arrays.fill(result, PlannerAmount.ZERO);
        return result;
    }
}
