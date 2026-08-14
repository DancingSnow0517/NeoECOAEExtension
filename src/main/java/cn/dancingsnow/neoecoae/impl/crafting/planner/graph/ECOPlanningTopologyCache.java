package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Bounded cache for immutable SCC indexes shared by equivalent planning graphs. */
final class ECOPlanningTopologyCache {
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<TopologyKey, ECOStrongComponents.Topology<?, ?>> CACHE =
        new LinkedHashMap<>(16, 0.75F, true);

    private ECOPlanningTopologyCache() {
    }

    static <K, R> ECOStrongComponents.Topology<K, R> get(
        ECOPlanningGraph<K, R> graph,
        Set<K> unlimitedResources
    ) {
        TopologyKey key = new TopologyKey(
            graph.revision(),
            graph.recipeBindingVersion(),
            Set.copyOf(unlimitedResources),
            Math.clamp(
                NEConfig.ecoPlannerMaxComponentMaterials,
                1,
                NEConfig.ECO_PLANNER_COMPONENT_MATERIALS_HARD_MAX
            ),
            Math.clamp(
                NEConfig.ecoPlannerMaxComponentOperations,
                1,
                NEConfig.ECO_PLANNER_COMPONENT_OPERATIONS_HARD_MAX
            ),
            graph.operations()
        );
        synchronized (LOCK) {
            ECOStrongComponents.Topology<?, ?> cached = CACHE.get(key);
            if (cached != null) {
                return cast(cached);
            }
            ECOStrongComponents.Topology<K, R> built = ECOStrongComponents.build(graph);
            CACHE.put(key, built);
            trimLocked();
            return built;
        }
    }

    static void clear() {
        synchronized (LOCK) {
            CACHE.clear();
        }
    }

    private static void trimLocked() {
        int limit = Math.clamp(
            NEConfig.ecoPlannerTopologyCacheSize,
            16,
            NEConfig.ECO_PLANNER_CACHE_HARD_MAX
        );
        while (CACHE.size() > limit) {
            CACHE.remove(CACHE.keySet().iterator().next());
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, R> ECOStrongComponents.Topology<K, R> cast(
        ECOStrongComponents.Topology<?, ?> topology
    ) {
        return (ECOStrongComponents.Topology<K, R>) topology;
    }

    private record TopologyKey(
        long graphRevision,
        long recipeBindingVersion,
        Set<?> unlimitedResources,
        int componentMaterialLimit,
        int componentOperationLimit,
        java.util.List<?> operations
    ) {
    }
}
