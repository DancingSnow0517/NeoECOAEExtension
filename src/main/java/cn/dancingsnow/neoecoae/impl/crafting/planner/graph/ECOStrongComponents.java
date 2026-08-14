package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ECOStrongComponents {
    private ECOStrongComponents() {
    }

    /** Builds and freezes every index used by the condensed planners. */
    public static <K, R> Topology<K, R> build(ECOPlanningGraph<K, R> graph) {
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

        // Kosaraju's two passes are iterative so a long recipe chain cannot consume the JVM
        // call stack while the graph is being classified.
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
                        stack.push(new Frame<>(
                            adjacent, edges.getOrDefault(adjacent, Set.of()).iterator()));
                    }
                } else {
                    finishOrder.add(frame.node());
                    stack.pop();
                }
            }
        }

        visited.clear();
        List<Set<K>> rawComponents = new ArrayList<>();
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
            rawComponents.add(component);
        }

        List<Component<K>> components = new ArrayList<>(rawComponents.size());
        Map<K, Component<K>> materialToComponent = new LinkedHashMap<>();
        for (int index = 0; index < rawComponents.size(); index++) {
            Component<K> component = new Component<>(index, rawComponents.get(index));
            components.add(component);
            component.materials().forEach(material -> materialToComponent.put(material, component));
        }

        Map<Component<K>, Set<K>> componentMaterials = new LinkedHashMap<>();
        Map<Component<K>, List<ECOPlanningOperation<K, R>>> localOperations = new LinkedHashMap<>();
        Map<Component<K>, Set<Component<K>>> predecessors = new LinkedHashMap<>();
        Map<Component<K>, Set<Component<K>>> successors = new LinkedHashMap<>();
        components.forEach(component -> {
            componentMaterials.put(component, component.materials());
            localOperations.put(component, new ArrayList<>());
            predecessors.put(component, new LinkedHashSet<>());
            successors.put(component, new LinkedHashSet<>());
        });

        Map<R, Component<K>> operationOwners = new LinkedHashMap<>();
        Set<Component<K>> cyclicComponents = new LinkedHashSet<>();
        for (Component<K> component : components) {
            if (component.materials().size() > 1) {
                cyclicComponents.add(component);
            }
        }

        for (var operation : graph.operations()) {
            Set<Component<K>> inputComponents = componentsOf(operation.inputs().keySet(), materialToComponent);
            Set<Component<K>> outputComponents = componentsOf(operation.outputs().keySet(), materialToComponent);
            Set<Component<K>> selectableOutputComponents = componentsOf(
                operation.selectableOutputs(), materialToComponent);

            // A recipe can feed more than one SCC. Keep it in each affected local operation
            // list, while exposing one stable owner for O(1) operation lookups.
            for (Component<K> component : selectableOutputComponents) {
                localOperations.get(component).add(operation);
            }
            Component<K> owner = selectableOutputComponents.stream().findFirst()
                .orElse(outputComponents.stream().findFirst().orElse(null));
            if (owner != null) {
                operationOwners.put(operation.reference(), owner);
            }

            for (Component<K> input : inputComponents) {
                for (Component<K> output : outputComponents) {
                    if (input.equals(output)) {
                        cyclicComponents.add(input);
                    } else {
                        successors.get(input).add(output);
                        predecessors.get(output).add(input);
                    }
                }
            }
        }

        Set<R> cyclicOperations = new LinkedHashSet<>();
        for (var operation : graph.operations()) {
            Set<Component<K>> inputComponents = componentsOf(operation.inputs().keySet(), materialToComponent);
            Set<Component<K>> outputComponents = componentsOf(
                operation.selectableOutputs(), materialToComponent);
            if (inputComponents.stream().anyMatch(cyclicComponents::contains)
                || outputComponents.stream().anyMatch(cyclicComponents::contains)) {
                cyclicOperations.add(operation.reference());
            }
        }

        Map<Component<K>, List<ECOPlanningOperation<K, R>>> frozenLocalOperations = new LinkedHashMap<>();
        localOperations.forEach((component, operations) ->
            frozenLocalOperations.put(component, List.copyOf(operations)));

        return new Topology<>(
            components,
            componentMaterials,
            materialToComponent,
            frozenLocalOperations,
            operationOwners,
            predecessors,
            successors,
            cyclicComponents,
            cyclicOperations
        );
    }

    /** Compatibility view for callers that only need the material sets. */
    public static <K, R> List<Set<K>> find(ECOPlanningGraph<K, R> graph) {
        return graph.topology().components().stream()
            .map(Component::materials)
            .toList();
    }

    private static <K> Set<Component<K>> componentsOf(
        Set<K> materials,
        Map<K, Component<K>> materialToComponent
    ) {
        Set<Component<K>> result = new LinkedHashSet<>();
        materials.forEach(material -> {
            Component<K> component = materialToComponent.get(material);
            if (component != null) {
                result.add(component);
            }
        });
        return result;
    }

    public record Component<K>(int id, Set<K> materials) {
        public Component {
            materials = immutableSet(materials);
        }

        private static <T> Set<T> immutableSet(Set<T> source) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(source));
        }
    }

    /** Immutable SCC and condensation-DAG indexes for one planning graph. */
    public static final class Topology<K, R> {
        private final List<Component<K>> components;
        private final Map<Component<K>, Set<K>> componentMaterials;
        private final Map<K, Component<K>> materialToComponent;
        private final Map<Component<K>, List<ECOPlanningOperation<K, R>>> localOperations;
        private final Map<R, Component<K>> operationOwners;
        private final Map<Component<K>, Set<Component<K>>> predecessors;
        private final Map<Component<K>, Set<Component<K>>> successors;
        private final Set<Component<K>> cyclicComponents;
        private final Set<R> cyclicOperations;

        private Topology(
            List<Component<K>> components,
            Map<Component<K>, Set<K>> componentMaterials,
            Map<K, Component<K>> materialToComponent,
            Map<Component<K>, List<ECOPlanningOperation<K, R>>> localOperations,
            Map<R, Component<K>> operationOwners,
            Map<Component<K>, Set<Component<K>>> predecessors,
            Map<Component<K>, Set<Component<K>>> successors,
            Set<Component<K>> cyclicComponents,
            Set<R> cyclicOperations
        ) {
            this.components = List.copyOf(components);
            this.componentMaterials = freezeMapOfSets(componentMaterials);
            this.materialToComponent = Map.copyOf(materialToComponent);
            this.localOperations = freezeMapOfLists(localOperations);
            this.operationOwners = Map.copyOf(operationOwners);
            this.predecessors = freezeMapOfSets(predecessors);
            this.successors = freezeMapOfSets(successors);
            this.cyclicComponents = Set.copyOf(cyclicComponents);
            this.cyclicOperations = Set.copyOf(cyclicOperations);
        }

        public List<Component<K>> components() {
            return components;
        }

        public Map<K, Component<K>> materialToComponent() {
            return materialToComponent;
        }

        public Map<Component<K>, Set<K>> componentToMaterials() {
            return componentMaterials;
        }

        public Component<K> componentOf(K material) {
            return materialToComponent.get(material);
        }

        public List<ECOPlanningOperation<K, R>> localOperationsOf(Component<K> component) {
            return localOperations.getOrDefault(component, List.of());
        }

        public Map<Component<K>, List<ECOPlanningOperation<K, R>>> componentToLocalOperations() {
            return localOperations;
        }

        public Component<K> owningComponentOf(R operation) {
            return operationOwners.get(operation);
        }

        public Map<R, Component<K>> operationToOwningComponent() {
            return operationOwners;
        }

        public Set<Component<K>> predecessorsOf(Component<K> component) {
            return predecessors.getOrDefault(component, Set.of());
        }

        public Set<Component<K>> successorsOf(Component<K> component) {
            return successors.getOrDefault(component, Set.of());
        }

        public Map<Component<K>, Set<Component<K>>> componentPredecessors() {
            return predecessors;
        }

        public Map<Component<K>, Set<Component<K>>> componentSuccessors() {
            return successors;
        }

        public boolean isCyclic(Component<K> component) {
            return cyclicComponents.contains(component);
        }

        public Set<Component<K>> cyclicComponents() {
            return cyclicComponents;
        }

        public Set<R> cyclicOperationReferences() {
            return cyclicOperations;
        }

        public boolean hasCycle() {
            return !cyclicComponents.isEmpty();
        }

        private static <K, R> Map<Component<K>, List<ECOPlanningOperation<K, R>>> freezeMapOfLists(
            Map<Component<K>, List<ECOPlanningOperation<K, R>>> source
        ) {
            Map<Component<K>, List<ECOPlanningOperation<K, R>>> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(key, List.copyOf(value)));
            return Collections.unmodifiableMap(result);
        }

        private static <A, B> Map<A, Set<B>> freezeMapOfSets(Map<A, Set<B>> source) {
            Map<A, Set<B>> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(key, Component.immutableSet(value)));
            return Collections.unmodifiableMap(result);
        }
    }

    private record Frame<K>(K node, java.util.Iterator<K> neighbors) {
    }
}
