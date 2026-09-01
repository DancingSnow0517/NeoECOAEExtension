package cn.dancingsnow.neoecoae.client.craftinggraph;

import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
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

    public static List<CycleCluster> derive(CraftingGraphSnapshot snapshot) {
        Set<Integer> components = snapshot.cycleGroups().stream()
            .map(CraftingGraphSnapshot.CycleGroup::componentId).collect(java.util.stream.Collectors.toSet());
        Map<Integer, List<Endpoint>> producers = new LinkedHashMap<>();
        Map<Integer, List<Endpoint>> consumers = new LinkedHashMap<>();
        Map<Integer, Boolean> hasSelectedPattern = new HashMap<>();
        for (var pattern : snapshot.patterns()) if (components.contains(pattern.componentId())
                && (pattern.status() == CraftingGraphSnapshot.CandidateStatus.SELECTED
                    || pattern.firingCount() > 0)) {
            hasSelectedPattern.put(pattern.componentId(), true);
        }
        for (var pattern : snapshot.patterns()) {
            if (!components.contains(pattern.componentId())) continue;
            if (hasSelectedPattern.getOrDefault(pattern.componentId(), false)
                    && pattern.status() != CraftingGraphSnapshot.CandidateStatus.SELECTED
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

        List<InterCycleFlow> flows = new ArrayList<>();
        Set<String> identities = new LinkedHashSet<>();
        for (var entry : producers.entrySet()) {
            int materialId = entry.getKey();
            for (Endpoint producer : entry.getValue()) for (Endpoint consumer
                    : consumers.getOrDefault(materialId, List.of())) {
                if (producer.componentId() == consumer.componentId()) continue;
                String identity = producer.componentId() + ":" + consumer.componentId() + ":" + materialId + ":"
                    + producer.patternId() + ":" + consumer.patternId() + ":" + producer.amount() + ":"
                    + consumer.amount();
                if (identities.add(identity)) flows.add(new InterCycleFlow(producer.componentId(),
                    consumer.componentId(), materialId, producer.patternId(), consumer.patternId(),
                    producer.amount(), consumer.amount()));
            }
        }
        flows.sort(Comparator.comparingInt(InterCycleFlow::fromComponentId)
            .thenComparingInt(InterCycleFlow::toComponentId).thenComparingInt(InterCycleFlow::materialNodeId)
            .thenComparingInt(InterCycleFlow::producerPatternId).thenComparingInt(InterCycleFlow::consumerPatternId));

        Map<Integer, Set<Integer>> undirected = new HashMap<>();
        for (InterCycleFlow flow : flows) {
            undirected.computeIfAbsent(flow.fromComponentId(), ignored -> new LinkedHashSet<>())
                .add(flow.toComponentId());
            undirected.computeIfAbsent(flow.toComponentId(), ignored -> new LinkedHashSet<>())
                .add(flow.fromComponentId());
        }
        List<CycleCluster> result = new ArrayList<>();
        Set<Integer> visited = new LinkedHashSet<>();
        for (int start : components.stream().sorted().toList()) {
            if (visited.contains(start) || !undirected.containsKey(start)) continue;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            List<Integer> members = new ArrayList<>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                members.add(current);
                for (int next : undirected.getOrDefault(current, Set.of())) if (visited.add(next)) queue.addLast(next);
            }
            if (members.size() < 2) continue;
            members.sort(Integer::compareTo);
            Set<Integer> memberSet = Set.copyOf(members);
            List<InterCycleFlow> clusterFlows = flows.stream()
                .filter(flow -> memberSet.contains(flow.fromComponentId()) && memberSet.contains(flow.toComponentId()))
                .toList();
            result.add(new CycleCluster(members.getFirst(), members, clusterFlows));
        }
        return List.copyOf(result);
    }

    private record Endpoint(int componentId, int patternId, long amount) {}
}
