package cn.dancingsnow.neoecoae.core.graph.presentation;

import cn.dancingsnow.neoecoae.core.graph.GraphAdjacency;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives inter-SCC material flow and display clusters solely from the immutable client snapshot. */
public final class CycleClusterProjection {
    private CycleClusterProjection() {}

    public static <F extends CycleTopology.Flow, C extends CycleTopology.Cluster> List<C> derive(
            CycleTopology snapshot, FlowFactory<F> flowFactory, ClusterFactory<F, C> clusterFactory) {
        Set<Integer> components = snapshot.cycleGroups().stream()
            .map(CycleTopology.Cycle::componentId).collect(java.util.stream.Collectors.toSet());
        Map<Integer, List<Endpoint>> producers = new LinkedHashMap<>();
        Map<Integer, List<Endpoint>> consumers = new LinkedHashMap<>();
        Map<Integer, Boolean> hasSelectedPattern = new HashMap<>();
        for (var pattern : snapshot.patterns()) if (components.contains(pattern.componentId())
                && (pattern.selected()
                    || pattern.firingCount() > 0)) {
            hasSelectedPattern.put(pattern.componentId(), true);
        }
        for (var pattern : snapshot.patterns()) {
            if (!components.contains(pattern.componentId())) continue;
            if (hasSelectedPattern.getOrDefault(pattern.componentId(), false)
                    && !pattern.selected()
                    && pattern.firingCount() <= 0) continue;
            for (var output : pattern.outputs()) {
                producers.computeIfAbsent(output.materialNodeId(), ignored -> new ArrayList<>())
                    .add(new Endpoint(pattern.componentId(), pattern.patternNodeId(), output.amount()));
            }
            for (var input : pattern.inputs()) {
                consumers.computeIfAbsent(input.materialNodeId(), ignored -> new ArrayList<>())
                    .add(new Endpoint(pattern.componentId(), pattern.patternNodeId(), input.amount()));
            }
        }

        List<F> flows = new ArrayList<>();
        Set<String> identities = new LinkedHashSet<>();
        for (var entry : producers.entrySet()) {
            int materialId = entry.getKey();
            for (Endpoint producer : entry.getValue()) for (Endpoint consumer
                    : consumers.getOrDefault(materialId, List.of())) {
                if (producer.componentId() == consumer.componentId()) continue;
                String identity = producer.componentId() + ":" + consumer.componentId() + ":" + materialId + ":"
                    + producer.patternId() + ":" + consumer.patternId() + ":" + producer.amount() + ":"
                    + consumer.amount();
                if (identities.add(identity)) flows.add(flowFactory.create(producer.componentId(),
                    consumer.componentId(), materialId, producer.patternId(), consumer.patternId(),
                    producer.amount(), consumer.amount()));
            }
        }
        flows.sort(Comparator.comparingInt(CycleTopology.Flow::fromComponentId)
            .thenComparingInt(CycleTopology.Flow::toComponentId).thenComparingInt(CycleTopology.Flow::materialNodeId)
            .thenComparingInt(CycleTopology.Flow::producerPatternId).thenComparingInt(CycleTopology.Flow::consumerPatternId));

        // Connectivity only depends on membership, and members are sorted below, so neighbour order is irrelevant.
        GraphAdjacency adjacency = GraphAdjacency.of(flows, CycleTopology.Flow::fromComponentId,
            CycleTopology.Flow::toComponentId);
        List<C> result = new ArrayList<>();
        Set<Integer> visited = new LinkedHashSet<>();
        for (int start : components.stream().sorted().toList()) {
            if (visited.contains(start) || adjacency.neighborhood(start).isEmpty()) continue;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            List<Integer> members = new ArrayList<>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                members.add(current);
                for (int next : adjacency.neighborhood(current)) if (visited.add(next)) queue.addLast(next);
            }
            if (members.size() < 2) continue;
            members.sort(Integer::compareTo);
            Set<Integer> memberSet = new LinkedHashSet<>(members);
            List<F> clusterFlows = flows.stream()
                .filter(flow -> memberSet.contains(flow.fromComponentId()) && memberSet.contains(flow.toComponentId()))
                .toList();
            result.add(clusterFactory.create(members.getFirst(), members, clusterFlows));
        }
        return result;
    }

    @FunctionalInterface
    public interface FlowFactory<F extends CycleTopology.Flow> {
        F create(int from, int to, int material, int producer, int consumer, long output, long input);
    }
    @FunctionalInterface
    public interface ClusterFactory<F extends CycleTopology.Flow, C extends CycleTopology.Cluster> {
        C create(int id, List<Integer> components, List<F> flows);
    }
    private record Endpoint(int componentId, int patternId, long amount) {}
}
