package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOStrongComponents;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Client-facing explanation of one ojAlgo cycle result. */
public record ECOCyclePlanningDiagnostics(
    Map<AEKey, MaterialStats> materials,
    Map<AEKey, Long> missingSeeds
) {
    public static final ECOCyclePlanningDiagnostics EMPTY = new ECOCyclePlanningDiagnostics(Map.of(), Map.of());

    public ECOCyclePlanningDiagnostics {
        materials = Map.copyOf(materials);
        missingSeeds = Map.copyOf(missingSeeds);
    }

    public boolean isEmpty() {
        return materials.isEmpty() && missingSeeds.isEmpty();
    }

    public static ECOCyclePlanningDiagnostics from(
        ECOAE2PlanningSnapshot snapshot,
        ECOHyperflowResult<IPatternDetails> result
    ) {
        var trace = result.cycleTrace();
        if (trace.isEmpty()) {
            return EMPTY;
        }

        Set<AEKey> cycleMaterials = tracedCycleMaterials(snapshot, trace.get().operations());
        if (cycleMaterials.isEmpty()) {
            return EMPTY;
        }

        Map<AEKey, Long> missingSeeds = new LinkedHashMap<>();
        if (!trace.get().missingSeedStarters().isEmpty()) {
            snapshot.problem().operations().stream()
                .filter(operation -> trace.get().missingSeedStarters().contains(operation.reference()))
                .forEach(operation -> operation.inputs().forEach((key, amount) -> {
                long deficit = Math.max(0L, amount - snapshot.problem().inventory().getOrDefault(key, 0L));
                if (deficit > 0L) {
                    missingSeeds.merge(key, deficit, Math::max);
                }
            }));
        }

        Map<AEKey, long[]> totals = new LinkedHashMap<>();
        for (var operation : snapshot.problem().operations()) {
            if (!trace.get().operations().contains(operation.reference())) {
                continue;
            }
            long count = result.candidate().executions().getOrDefault(operation.reference(), 0L);
            if (count <= 0L) {
                continue;
            }
            operation.inputs().forEach((key, amount) -> {
                if (cycleMaterials.contains(key)) add(totals, key, 0, amount, count);
            });
            operation.outputs().forEach((key, amount) -> {
                if (cycleMaterials.contains(key)) add(totals, key, 1, amount, count);
            });
        }

        Map<AEKey, MaterialStats> materials = new LinkedHashMap<>();
        for (AEKey material : cycleMaterials) {
            long[] amounts = totals.getOrDefault(material, new long[2]);
            long initial = snapshot.problem().inventory().getOrDefault(material, 0L);
            long remaining = saturatedAdd(saturatedAdd(initial, -amounts[0]), amounts[1]);
            materials.put(material, new MaterialStats(initial, amounts[0], amounts[1], Math.max(0L, remaining)));
        }
        return new ECOCyclePlanningDiagnostics(materials, missingSeeds);
    }

    private static Set<AEKey> tracedCycleMaterials(
        ECOAE2PlanningSnapshot snapshot,
        Set<IPatternDetails> tracedOperations
    ) {
        ECOPlanningGraph<AEKey, IPatternDetails> graph = new ECOPlanningGraph<>(snapshot.problem().operations());
        Set<AEKey> materials = new java.util.LinkedHashSet<>();
        ECOStrongComponents.find(graph).stream()
            .filter(component -> component.size() > 1 || graph.operations().stream().anyMatch(operation ->
                component.stream().anyMatch(material -> operation.inputs().containsKey(material)
                    && operation.outputs().containsKey(material))))
            .filter(component -> graph.operations().stream().anyMatch(operation ->
                tracedOperations.contains(operation.reference())
                    && operation.selectableOutputs().stream().anyMatch(component::contains)))
            .forEach(materials::addAll);
        return Set.copyOf(materials);
    }

    private static void add(Map<AEKey, long[]> totals, AEKey key, int index, long amount, long count) {
        long value = saturatedMultiply(amount, count);
        long[] material = totals.computeIfAbsent(key, ignored -> new long[2]);
        material[index] = saturatedAdd(material[index], value);
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    private static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    public record MaterialStats(long initial, long consumed, long produced, long remaining) {
    }
}
