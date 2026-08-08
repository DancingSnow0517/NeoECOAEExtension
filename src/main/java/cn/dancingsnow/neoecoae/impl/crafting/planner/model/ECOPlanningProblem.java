package cn.dancingsnow.neoecoae.impl.crafting.planner.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable input captured before an ECO planning task leaves the server thread. */
public record ECOPlanningProblem<K, R>(
    List<ECOPlanningOperation<K, R>> operations,
    Map<K, Long> inventory,
    Map<K, Long> requested
) {
    public ECOPlanningProblem {
        operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        inventory = copyNonNegative(inventory, "inventory");
        requested = copyPositive(requested, "requested");
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("A planning problem must request at least one material");
        }
    }

    private static <K> Map<K, Long> copyNonNegative(Map<K, Long> source, String name) {
        Objects.requireNonNull(source, name);
        Map<K, Long> copy = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            K key = Objects.requireNonNull(entry.getKey(), name + " material");
            long amount = Objects.requireNonNull(entry.getValue(), name + " amount");
            if (amount < 0) {
                throw new IllegalArgumentException(name + " amounts cannot be negative");
            }
            if (amount > 0) {
                copy.put(key, amount);
            }
        }
        return Map.copyOf(copy);
    }

    private static <K> Map<K, Long> copyPositive(Map<K, Long> source, String name) {
        // copyNonNegative already omits zero-amount entries, so no further filtering is needed.
        return copyNonNegative(source, name);
    }
}
