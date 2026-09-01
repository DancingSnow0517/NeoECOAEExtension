package cn.dancingsnow.neoecoae.client.craftinggraph;

import java.util.List;

/** Client-only weakly connected group of SCCs; component IDs remain authoritative and distinct. */
public record CycleCluster(int clusterId, List<Integer> componentIds, List<InterCycleFlow> flows) {
    public CycleCluster {
        componentIds = List.copyOf(componentIds);
        flows = List.copyOf(flows);
    }
}
