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
    private final long revision;
    private final long recipeBindingVersion;
    private volatile ECOStrongComponents.Topology<K, R> topology;

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
        this.recipeBindingVersion = fingerprint(this.operations);
        this.revision = fingerprintWithMaterials(this.operations, this.materials);
    }

    public static void clearTopologyCache() {
        ECOPlanningTopologyCache.clear();
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

    /** Stable recipe binding fingerprint used by planner caches and revision invalidation. */
    public long revision() {
        return revision;
    }

    public long recipeBindingVersion() {
        return recipeBindingVersion;
    }

    /**
     * Returns the immutable condensation topology for this graph.
     *
     * <p>The topology is lazy because graph pruning creates a short-lived source graph before
     * it creates the target-reachable graph. Once requested, it is published and reused by every
     * solver and scheduler in the planning request.</p>
     */
    public ECOStrongComponents.Topology<K, R> topology() {
        return topology(Set.of());
    }

    /**
     * Returns the same topology while allowing the cache key to include request-scoped
     * unlimited resources. Unlimited resources do not change SCC membership, but they do
     * distinguish planner snapshots in the bounded cross-graph cache.
     */
    public ECOStrongComponents.Topology<K, R> topology(Set<K> unlimitedResources) {
        Objects.requireNonNull(unlimitedResources, "unlimitedResources");
        ECOStrongComponents.Topology<K, R> current = topology;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = topology;
            if (current == null) {
                current = ECOPlanningTopologyCache.get(this, unlimitedResources);
                topology = current;
            }
            return current;
        }
    }

    private static <K, R> long fingerprint(List<ECOPlanningOperation<K, R>> operations) {
        long result = 1_469_598_103_934_665_603L;
        for (var operation : operations) {
            result = result * 1_099_511_628_211L + operation.hashCode();
        }
        return result;
    }

    private static <K, R> long fingerprintWithMaterials(
        List<ECOPlanningOperation<K, R>> operations,
        Set<K> materials
    ) {
        long result = fingerprint(operations);
        for (K material : materials) {
            result = result * 1_099_511_628_211L + material.hashCode();
        }
        return result;
    }
}
