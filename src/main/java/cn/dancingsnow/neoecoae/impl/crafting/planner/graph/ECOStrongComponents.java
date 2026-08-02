package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ECOStrongComponents {
    private ECOStrongComponents() {
    }

    public static <K, R> List<Set<K>> find(ECOPlanningGraph<K, R> graph) {
        Map<K, Set<K>> edges = new LinkedHashMap<>();
        for (K material : graph.materials()) {
            edges.put(material, new LinkedHashSet<>());
        }
        for (var operation : graph.operations()) {
            for (K input : operation.inputs().keySet()) {
                edges.computeIfAbsent(input, ignored -> new LinkedHashSet<>())
                    .addAll(operation.outputs().keySet());
            }
        }
        Map<K, Set<K>> reverse = new LinkedHashMap<>();
        edges.keySet().forEach(node -> reverse.put(node, new LinkedHashSet<>()));
        edges.forEach((from, targets) -> targets.forEach(to ->
            reverse.computeIfAbsent(to, ignored -> new LinkedHashSet<>()).add(from)));

        // Kosaraju's two passes are iterative so a long recipe chain cannot
        // consume the JVM call stack while the graph is being classified.
        Set<K> visited = new HashSet<>();
        List<K> finishOrder = new ArrayList<>(edges.size());
        for (K node : edges.keySet()) {
            if (!visited.add(node)) {
                continue;
            }
            ArrayDeque<Frame<K>> stack = new ArrayDeque<>();
            stack.push(new Frame<>(node, edges.getOrDefault(node, Set.of()).iterator()));
            while (!stack.isEmpty()) {
                Frame<K> frame = stack.peek();
                if (frame.neighbors().hasNext()) {
                    K adjacent = frame.neighbors().next();
                    if (visited.add(adjacent)) {
                        stack.push(new Frame<>(adjacent, edges.getOrDefault(adjacent, Set.of()).iterator()));
                    }
                } else {
                    finishOrder.add(frame.node());
                    stack.pop();
                }
            }
        }

        visited.clear();
        List<Set<K>> components = new ArrayList<>();
        for (int index = finishOrder.size() - 1; index >= 0; index--) {
            K node = finishOrder.get(index);
            if (!visited.add(node)) {
                continue;
            }
            Set<K> component = new LinkedHashSet<>();
            ArrayDeque<K> stack = new ArrayDeque<>();
            stack.push(node);
            while (!stack.isEmpty()) {
                K current = stack.pop();
                component.add(current);
                for (K adjacent : reverse.getOrDefault(current, Set.of())) {
                    if (visited.add(adjacent)) {
                        stack.push(adjacent);
                    }
                }
            }
            components.add(Set.copyOf(component));
        }
        return List.copyOf(components);
    }

    private record Frame<K>(K node, java.util.Iterator<K> neighbors) {
    }
}
