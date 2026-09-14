package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import java.util.List;
import java.util.Map;

/** Explicit mutually-exclusive dynamic reservation choices. */
public record PendingChoiceGroup(String id, List<Map<?, Long>> branches) {
    public PendingChoiceGroup {
        if (id == null || branches == null || branches.isEmpty()) throw new IllegalArgumentException("Invalid choice group");
        List<Map<?, Long>> copy = new java.util.ArrayList<>(branches.size());
        for (Map<?, Long> branch : branches) copy.add(Map.copyOf(branch));
        branches = List.copyOf(copy);
    }
}
