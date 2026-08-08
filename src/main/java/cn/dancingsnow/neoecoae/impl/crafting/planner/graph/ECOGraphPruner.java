package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

/** Builds the target-reachable maximal structure used by the ECO solvers. */
public final class ECOGraphPruner {
    private ECOGraphPruner() {
    }

    public static <K, R> ECOPlanningGraph<K, R> targetReachable(
        ECOPlanningGraph<K, R> source,
        Set<K> requested
    ) {
        ArrayDeque<K> pending = new ArrayDeque<>(requested);
        Set<K> visitedMaterials = new LinkedHashSet<>();
        Set<ECOPlanningOperation<K, R>> retained = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            K material = pending.removeFirst();
            if (!visitedMaterials.add(material)) {
                continue;
            }
            for (var producer : source.producersOf(material)) {
                if (retained.add(producer)) {
                    pending.addAll(producer.inputs().keySet());
                }
            }
        }
        return new ECOPlanningGraph<>(retained.stream().toList());
    }

    /** Convenience overload that extracts operations and requested keys from a problem. */
    public static <K, R> ECOPlanningGraph<K, R> targetReachable(ECOPlanningProblem<K, R> problem) {
        return targetReachable(new ECOPlanningGraph<>(problem.operations()), problem.requested().keySet());
    }
}
