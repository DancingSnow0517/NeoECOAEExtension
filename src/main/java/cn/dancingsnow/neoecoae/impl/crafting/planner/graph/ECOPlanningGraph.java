package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ECOPlanningGraph<K, R> {
    private final List<ECOPlanningOperation<K, R>> operations;
    private final Map<K, List<ECOPlanningOperation<K, R>>> producers;
    private final Set<K> materials;

    public ECOPlanningGraph(List<ECOPlanningOperation<K, R>> operations) {
        this.operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        Map<K, List<ECOPlanningOperation<K, R>>> producerIndex = new LinkedHashMap<>();
        Set<K> allMaterials = new LinkedHashSet<>();
        Set<R> references = new LinkedHashSet<>();
        for (var operation : this.operations) {
            if (!references.add(operation.reference())) {
                throw new IllegalArgumentException("Planning operation references must be unique");
            }
            allMaterials.addAll(operation.inputs().keySet());
            allMaterials.addAll(operation.outputs().keySet());
            for (K output : operation.selectableOutputs()) {
                producerIndex.computeIfAbsent(output, ignored -> new ArrayList<>()).add(operation);
            }
        }
        Map<K, List<ECOPlanningOperation<K, R>>> frozenIndex = new LinkedHashMap<>();
        producerIndex.forEach((key, value) -> frozenIndex.put(key, List.copyOf(value)));
        this.producers = Map.copyOf(frozenIndex);
        this.materials = Set.copyOf(allMaterials);
    }

    public List<ECOPlanningOperation<K, R>> operations() {
        return operations;
    }

    public List<ECOPlanningOperation<K, R>> producersOf(K material) {
        return producers.getOrDefault(material, List.of());
    }

    public Set<K> materials() {
        return materials;
    }
}
