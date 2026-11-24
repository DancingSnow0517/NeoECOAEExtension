package cn.dancingsnow.neoecoae.grid.crafting.graph.cycle;

import java.util.List;

/** Client-only weakly connected group of SCCs; component IDs remain authoritative and distinct. */
public record CycleCluster(int clusterId, List<Integer> componentIds, List<InterCycleFlow> flows) {
    public CycleCluster {


    }
}
