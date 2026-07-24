package cn.dancingsnow.neoecoae.impl.crafting.planner.schedule;

import java.util.List;
import java.util.Map;

public record ECOInventorySchedule<K, R>(
    boolean executable,
    List<ECOScheduledStep<R>> steps,
    Map<K, Long> remainingInventory,
    Map<K, Long> blockedBy
) {
    public ECOInventorySchedule {
        steps = List.copyOf(steps);
        remainingInventory = Map.copyOf(remainingInventory);
        blockedBy = Map.copyOf(blockedBy);
    }
}
