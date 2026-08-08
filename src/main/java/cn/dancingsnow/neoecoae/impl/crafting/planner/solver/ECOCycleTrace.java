package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import java.util.Objects;
import java.util.Set;

/** Identifies the operations handled by the cyclic integer solver. */
public record ECOCycleTrace<R>(Set<R> operations, Set<R> missingSeedStarters) {
    public ECOCycleTrace {
        operations = Set.copyOf(Objects.requireNonNull(operations, "operations"));
        missingSeedStarters = Set.copyOf(Objects.requireNonNull(missingSeedStarters, "missingSeedStarters"));
    }

    public static <R> ECOCycleTrace<R> solved(Set<R> operations) {
        return new ECOCycleTrace<>(operations, Set.of());
    }

    public static <R> ECOCycleTrace<R> missingSeed(Set<R> operations, R starter) {
        return new ECOCycleTrace<>(operations, Set.of(starter));
    }
}
