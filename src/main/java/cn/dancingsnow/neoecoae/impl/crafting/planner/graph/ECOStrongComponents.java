package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
        Tarjan<K> tarjan = new Tarjan<>(edges);
        return tarjan.run();
    }

    private static final class Tarjan<K> {
        private final Map<K, Set<K>> edges;
        private final Map<K, Integer> indices = new HashMap<>();
        private final Map<K, Integer> lowLinks = new HashMap<>();
        private final ArrayDeque<K> stack = new ArrayDeque<>();
        private final Set<K> onStack = new HashSet<>();
        private final List<Set<K>> components = new ArrayList<>();
        private int nextIndex;

        private Tarjan(Map<K, Set<K>> edges) {
            this.edges = edges;
        }

        private List<Set<K>> run() {
            for (K node : edges.keySet()) {
                if (!indices.containsKey(node)) {
                    visit(node);
                }
            }
            return List.copyOf(components);
        }

        private void visit(K node) {
            int index = nextIndex++;
            indices.put(node, index);
            lowLinks.put(node, index);
            stack.push(node);
            onStack.add(node);

            for (K adjacent : edges.getOrDefault(node, Set.of())) {
                if (!indices.containsKey(adjacent)) {
                    visit(adjacent);
                    lowLinks.put(node, Math.min(lowLinks.get(node), lowLinks.get(adjacent)));
                } else if (onStack.contains(adjacent)) {
                    lowLinks.put(node, Math.min(lowLinks.get(node), indices.get(adjacent)));
                }
            }

            if (lowLinks.get(node).equals(indices.get(node))) {
                Set<K> component = new LinkedHashSet<>();
                K member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (!member.equals(node));
                components.add(Set.copyOf(component));
            }
        }
    }
}
