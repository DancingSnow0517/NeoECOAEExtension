package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.PlanningComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SCC contraction result. Its component graph is validated and topologically ordered. */
public final class CondensationGraph {
    private record Pair(int from, int to) {}

    private final CraftingDependencyGraph source;
    private final Map<Integer, PlanningComponent> components;
    private final Map<AEKey, Integer> componentByKey;
    private final List<ComponentDependency> dependencies;
    private final List<PlanningComponent> topologicalOrder;

    private CondensationGraph(CraftingDependencyGraph source, Map<Integer, PlanningComponent> components,
            Map<AEKey, Integer> componentByKey, List<ComponentDependency> dependencies,
            List<PlanningComponent> topologicalOrder) {
        this.source = source;
        this.components = Map.copyOf(components);
        this.componentByKey = Map.copyOf(componentByKey);
        this.dependencies = List.copyOf(dependencies);
        this.topologicalOrder = List.copyOf(topologicalOrder);
    }

    public static CondensationGraph build(CraftingDependencyGraph graph, List<SccComponent> sccs,
            ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, Integer> componentByKey = new HashMap<>();
        for (SccComponent scc : sccs) {
            cancellation.checkpoint();
            for (AEKey member : scc.members()) componentByKey.put(member, scc.componentId());
        }

        Map<Pair, List<CraftingGraphEdge>> grouped = new LinkedHashMap<>();
        for (CraftingGraphEdge edge : graph.edges()) {
            cancellation.checkpoint();
            int from = componentByKey.get(edge.producer());
            int to = componentByKey.get(edge.requiredInput());
            if (from != to) grouped.computeIfAbsent(new Pair(from, to), ignored -> new ArrayList<>()).add(edge);
        }
        List<ComponentDependency> dependencies = grouped.entrySet().stream()
            .map(entry -> new ComponentDependency(entry.getKey().from(), entry.getKey().to(), entry.getValue()))
            .toList();

        Map<Integer, List<ComponentDependency>> incoming = new HashMap<>();
        Map<Integer, List<ComponentDependency>> outgoing = new HashMap<>();
        for (ComponentDependency dependency : dependencies) {
            outgoing.computeIfAbsent(dependency.fromComponentId(), ignored -> new ArrayList<>()).add(dependency);
            incoming.computeIfAbsent(dependency.toComponentId(), ignored -> new ArrayList<>()).add(dependency);
        }

        Map<Integer, PlanningComponent> components = new LinkedHashMap<>();
        for (SccComponent scc : sccs) {
            cancellation.checkpoint();
            LinkedHashSet<CompiledPattern> patterns = new LinkedHashSet<>();
            if (scc.cyclic()) {
                for (CraftingGraphEdge edge : scc.internalEdges()) patterns.add(edge.pattern());
            } else {
                for (AEKey member : scc.members()) patterns.addAll(graph.nodes().get(member).candidatePatterns());
            }
            PlanningComponent component = scc.cyclic()
                ? new CycleComponent(scc.componentId(), scc.members(), List.copyOf(patterns), scc.internalEdges(),
                    incoming.getOrDefault(scc.componentId(), List.of()),
                    outgoing.getOrDefault(scc.componentId(), List.of()))
                : new AcyclicComponent(scc.componentId(), scc.members().getFirst(), List.copyOf(patterns));
            components.put(scc.componentId(), component);
        }

        Map<Integer, Integer> indegree = new HashMap<>();
        for (Integer id : components.keySet()) indegree.put(id, 0);
        for (ComponentDependency edge : dependencies) indegree.merge(edge.toComponentId(), 1, Integer::sum);
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (Integer id : components.keySet()) if (indegree.get(id) == 0) ready.addLast(id);
        List<PlanningComponent> order = new ArrayList<>(components.size());
        while (!ready.isEmpty()) {
            cancellation.checkpoint();
            int id = ready.removeFirst();
            order.add(components.get(id));
            for (ComponentDependency edge : outgoing.getOrDefault(id, List.of())) {
                int remaining = indegree.merge(edge.toComponentId(), -1, Integer::sum);
                if (remaining == 0) ready.addLast(edge.toComponentId());
            }
        }
        if (order.size() != components.size()) {
            throw new IllegalStateException("SCC condensation graph must be acyclic");
        }
        return new CondensationGraph(graph, components, componentByKey, dependencies, order);
    }

    public CraftingDependencyGraph source() { return source; }
    public Map<Integer, PlanningComponent> components() { return components; }
    public PlanningComponent componentFor(AEKey key) { return components.get(componentByKey.get(key)); }
    public List<ComponentDependency> dependencies() { return dependencies; }
    public List<PlanningComponent> topologicalOrder() { return topologicalOrder; }
    /** Execution order follows supplier -> consumer, opposite of producer->required-input edges. */
    public List<PlanningComponent> executionOrder() {
        return topologicalOrder.reversed();
    }
    public List<CycleComponent> cycles() {
        return topologicalOrder.stream().filter(CycleComponent.class::isInstance).map(CycleComponent.class::cast).toList();
    }
}
