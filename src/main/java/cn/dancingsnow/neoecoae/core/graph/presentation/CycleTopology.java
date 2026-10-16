package cn.dancingsnow.neoecoae.core.graph.presentation;

import java.util.List;

/** Read-only topology shared by trace snapshots and cycle projections, independent of wire encoding and keys. */
public interface CycleTopology {
    interface Relationship {
        int materialNodeId();
        long amount();
    }
    interface Pattern {
        int patternNodeId();
        int componentId();
        long firingCount();
        boolean selected();
        List<? extends Relationship> inputs();
        List<? extends Relationship> outputs();
    }
    interface Cycle {
        int componentId();
        List<Integer> memberNodeIds();
    }
    interface Flow {
        int fromComponentId();
        int toComponentId();
        int materialNodeId();
        int producerPatternId();
        int consumerPatternId();
        long outputAmount();
        long inputAmount();
    }
    interface Cluster {
        int clusterId();
        List<Integer> componentIds();
        List<? extends Flow> flows();
    }
    List<? extends Cycle> cycleGroups();
    List<? extends Pattern> patterns();
}
