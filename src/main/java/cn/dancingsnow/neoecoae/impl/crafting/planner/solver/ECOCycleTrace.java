package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import java.util.Objects;
import java.util.Map;
import java.util.Set;

/** Identifies the operations handled by the cyclic integer solver. */
public record ECOCycleTrace<R>(
    Set<R> operations,
    Set<R> missingSeedStarters,
    Map<?, Long> missingSeedAmounts
) {
    public ECOCycleTrace {
        operations = Set.copyOf(Objects.requireNonNull(operations, "operations"));
        missingSeedStarters = Set.copyOf(Objects.requireNonNull(missingSeedStarters, "missingSeedStarters"));
        missingSeedAmounts = Map.copyOf(Objects.requireNonNull(missingSeedAmounts, "missingSeedAmounts"));
    }

    public ECOCycleTrace(Set<R> operations, Set<R> missingSeedStarters) {
        this(operations, missingSeedStarters, Map.of());
    }

    public static <R> ECOCycleTrace<R> solved(Set<R> operations) {
        return new ECOCycleTrace<>(operations, Set.of(), Map.of());
    }

    public static <R> ECOCycleTrace<R> missingSeed(Set<R> operations, R starter) {
        return new ECOCycleTrace<>(operations, Set.of(starter), Map.of());
    }
}
