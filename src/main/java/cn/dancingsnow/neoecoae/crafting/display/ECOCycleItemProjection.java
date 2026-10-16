package cn.dancingsnow.neoecoae.crafting.display;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Projects server diagnostics for the cycle list without depending on menu or mixin state. */
public final class ECOCycleItemProjection {
    private ECOCycleItemProjection() {}

    public static ECOCycleItemList project(ECOPlanningResult planningResult, CraftingGraphSnapshot snapshot) {
        LinkedHashMap<AEKey, ECOCycleItemList.Entry> cycleItems = new LinkedHashMap<>();
        for (var cycle : snapshot.cycleGroups()) {
            java.util.Map<AEKey, Long> seedParallelism = planningResult.components().stream()
                .filter(component -> component.componentId() == cycle.componentId() && component.cycleResult() != null
                    && component.status() == cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.PLANNED)
                .findFirst().map(component -> component.cycleResult().seedParallelism(component.cycleResult().requiredSeed()))
                .orElse(java.util.Map.of());
            LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
            cycle.exactSingleNetOutputs().forEach(value -> keys.add(value.key()));
            cycle.exactTotalNetOutputs().forEach(value -> keys.add(value.key()));
            cycle.availableAmounts().forEach(value -> keys.add(value.key()));
            cycle.externalInputs().forEach(value -> keys.add(value.key()));
            cycle.requiredOutputs().forEach(value -> keys.add(value.key()));
            for (int memberId : cycle.memberNodeIds()) {
                snapshot.nodes().stream().filter(node -> node.nodeId() == memberId).findFirst().ifPresent(node ->
                    keys.add(node.key()));
            }
            snapshot.patterns().stream().filter(pattern -> pattern.componentId() == cycle.componentId()
                    && pattern.firingCount() > 0L)
                .flatMap(pattern -> java.util.stream.Stream.concat(pattern.inputs().stream(),
                    pattern.outputs().stream()))
                .map(CraftingGraphSnapshot.Relationship::materialNodeId)
                .forEach(materialId -> snapshot.nodes().stream()
                    .filter(node -> node.nodeId() == materialId).findFirst().ifPresent(node -> keys.add(node.key())));
            // A legacy/partially populated diagnostic may not have net-output entries yet. The member list
            // still needs a selectable row so every unresolved SCC remains openable from the report.
            planningResult.components().stream()
                .filter(component -> component.componentId() == cycle.componentId())
                .forEach(component -> keys.addAll(component.externalMissingItems().keySet()));
            for (AEKey key : keys) {
                // MaterialNode is built from the final executable plan. PatternNode firing counts are only
                // structural cycle metadata and can legitimately be zero for a populated plan.
                CraftingGraphSnapshot.MaterialNode material = materialFor(snapshot, key);
                var entry = new ECOCycleItemList.Entry(key,
                    material == null ? java.math.BigInteger.ZERO : material.consumedBigInteger(),
                    material == null ? java.math.BigInteger.ZERO : material.producedBigInteger(),
                    exactAmountFor(cycle.exactSingleNetOutputs(), key),
                    exactAmountFor(cycle.exactTotalNetOutputs(), key),
                    material == null ? java.math.BigInteger.ZERO : material.missingBigInteger(),
                    cycle.executionCountKnowledge(),
                    cycle.solveStatus(), cycle.componentId(), seedParallelism.getOrDefault(key, -1L));
                // An unresolved structural cycle has no authoritative total. Do not project
                // the ordinary plan's consumed/produced fields into the cycle list: doing so
                // makes an unused cycle look as if it consumed a real amount and falls back
                // to the misleading "quantity unknown" row. Keep concrete startup shortages,
                // which remain actionable even when the cycle itself was not solved.
                if (entry.totalNetOutputKnown() || entry.exactMissing().signum() > 0) {
                    cycleItems.putIfAbsent(key, entry);
                }
            }
            // A cycle can be unresolved before it produces any output. Keep its required startup seeds in
            // the left-hand list so the report remains actionable instead of showing an empty plan.
            for (var seed : cycle.requiredSeed()) {
                CraftingGraphSnapshot.MaterialNode material = materialFor(snapshot, seed.key());
                var entry = new ECOCycleItemList.Entry(seed.key(),
                    material == null ? java.math.BigInteger.ZERO : material.consumedBigInteger(),
                    material == null ? java.math.BigInteger.ZERO : material.producedBigInteger(),
                    java.math.BigInteger.ZERO, java.math.BigInteger.ZERO,
                    material == null ? java.math.BigInteger.ZERO : material.missingBigInteger(),
                    cycle.executionCountKnowledge(), cycle.solveStatus(), cycle.componentId(),
                    seedParallelism.getOrDefault(seed.key(), -1L));
                if (entry.totalNetOutputKnown() || entry.exactMissing().signum() > 0) {
                    cycleItems.putIfAbsent(seed.key(), entry);
                }
            }
        }
        return new ECOCycleItemList(new java.util.ArrayList<>(cycleItems.values()));
    }

    private static java.math.BigInteger exactAmountFor(
            List<cn.dancingsnow.neoecoae.crafting.planner.snapshot.ExactKeyAmount> values, AEKey key) {
        return values.stream().filter(value -> value.key().equals(key)).map(value -> value.amount().value())
            .findFirst().orElse(java.math.BigInteger.ZERO);
    }

    private static @Nullable CraftingGraphSnapshot.MaterialNode materialFor(
            CraftingGraphSnapshot snapshot, AEKey key) {
        return snapshot.nodes().stream().filter(node -> node.key().equals(key)).findFirst().orElse(null);
    }

}
