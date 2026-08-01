package cn.dancingsnow.neoecoae.impl.crafting.planner.schedule;

import java.util.List;
import java.util.Map;

public record ECOInventorySchedule<K, R>(
    boolean executable,
    List<ECOScheduledStep<R>> steps,
    Map<K, Long> remainingInventory,
    Map<K, Long> blockedBy,
    Map<K, Long> syntheticSources
) {
    public ECOInventorySchedule {
        steps = List.copyOf(steps);
        remainingInventory = Map.copyOf(remainingInventory);
        blockedBy = Map.copyOf(blockedBy);
        syntheticSources = Map.copyOf(syntheticSources);
    }

    public ECOInventorySchedule(
        boolean executable,
        List<ECOScheduledStep<R>> steps,
        Map<K, Long> remainingInventory,
        Map<K, Long> blockedBy
    ) {
        this(executable, steps, remainingInventory, blockedBy, Map.of());
    }
}
