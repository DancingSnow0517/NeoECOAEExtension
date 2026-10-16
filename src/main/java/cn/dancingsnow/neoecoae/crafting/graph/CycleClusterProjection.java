package cn.dancingsnow.neoecoae.crafting.graph;

import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.List;

/** Public adapter from the network snapshot to the portable cycle projection. */
public final class CycleClusterProjection {
    private CycleClusterProjection() {}
    public static List<CycleCluster> derive(CraftingGraphSnapshot snapshot) {
        return cn.dancingsnow.neoecoae.core.graph.presentation.CycleClusterProjection.derive(snapshot,
            InterCycleFlow::new, CycleCluster::new);
    }
}
