package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded LRU cache for immutable planning graphs.
 *
 * <p>The source graph holds the producer index for one materialized recipe set. Target graphs
 * retain only the operations reachable from the requested keys. Neither graph depends on the
 * inventory or requested amount, so they can be shared by otherwise distinct planning requests.</p>
 */
final class ECOPlanningGraphCache {
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<SourceKey, ECOPlanningGraph<?, ?>> SOURCE_GRAPHS =
        new LinkedHashMap<>(16, 0.75F, true);
    private static final LinkedHashMap<ReachableKey, ECOPlanningGraph<?, ?>> REACHABLE_GRAPHS =
        new LinkedHashMap<>(16, 0.75F, true);

    private ECOPlanningGraphCache() {
    }

    static <K, R> ECOPlanningGraph<K, R> targetReachable(
        List<ECOPlanningOperation<K, R>> operations,
        Set<K> requested
    ) {
        SourceKey sourceKey = new SourceKey(List.copyOf(Objects.requireNonNull(operations, "operations")));
        Set<K> requestedKeys = Set.copyOf(Objects.requireNonNull(requested, "requested"));
        synchronized (LOCK) {
            ReachableKey reachableKey = new ReachableKey(sourceKey, requestedKeys);
            ECOPlanningGraph<?, ?> cached = REACHABLE_GRAPHS.get(reachableKey);
            if (cached != null) {
                return cast(cached);
            }
            ECOPlanningGraph<K, R> source = sourceGraph(sourceKey);
            ECOPlanningGraph<K, R> reachable = ECOGraphPruner.targetReachable(source, requestedKeys);
            REACHABLE_GRAPHS.put(reachableKey, reachable);
            trimLocked(REACHABLE_GRAPHS);
            return reachable;
        }
    }

    static void clear() {
        synchronized (LOCK) {
            SOURCE_GRAPHS.clear();
            REACHABLE_GRAPHS.clear();
        }
    }

    private static <K, R> ECOPlanningGraph<K, R> sourceGraph(SourceKey key) {
        ECOPlanningGraph<?, ?> cached = SOURCE_GRAPHS.get(key);
        if (cached != null) {
            return cast(cached);
        }
        ECOPlanningGraph<K, R> source = new ECOPlanningGraph<>(castOperations(key.operations()));
        SOURCE_GRAPHS.put(key, source);
        trimLocked(SOURCE_GRAPHS);
        return source;
    }

    private static void trimLocked(Map<?, ?> cache) {
        int limit = Math.clamp(
            NEConfig.ecoPlannerReachableGraphCacheSize,
            16,
            NEConfig.ECO_PLANNER_CACHE_HARD_MAX
        );
        while (cache.size() > limit) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, R> ECOPlanningGraph<K, R> cast(ECOPlanningGraph<?, ?> graph) {
        return (ECOPlanningGraph<K, R>) graph;
    }

    @SuppressWarnings("unchecked")
    private static <K, R> List<ECOPlanningOperation<K, R>> castOperations(List<?> operations) {
        return (List<ECOPlanningOperation<K, R>>) operations;
    }

    private record SourceKey(List<?> operations) {
    }

    private record ReachableKey(SourceKey source, Set<?> requested) {
    }
}
