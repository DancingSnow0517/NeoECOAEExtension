package cn.dancingsnow.neoecoae.crafting.planner.route;

import appeng.api.stacks.AEKey;
import java.util.List;

/** Goal-first topological key order for the reachable dependency graph. */
public record AcyclicRoutePlan(List<AEKey> keys) {
    public AcyclicRoutePlan { keys = List.copyOf(keys); }
}
