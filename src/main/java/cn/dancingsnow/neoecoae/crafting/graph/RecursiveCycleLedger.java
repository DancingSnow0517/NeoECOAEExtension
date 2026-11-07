package cn.dancingsnow.neoecoae.crafting.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.CycleGroup;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.KeyAmount;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Exact, client-side quantities shared by recursive cards and the material ledger. */
public final class RecursiveCycleLedger {
    public record Balance(AEKey key, PlannerAmount seed, PlannerAmount external, PlannerAmount delivery,
            PlannerAmount initialStock, @Nullable PlannerAmount remaining, @Nullable PlannerAmount netChange,
            @Nullable PlannerAmount seedShortfall) {}

    private RecursiveCycleLedger() {}

    public static List<Balance> summarize(CycleGroup cycle) {
        var seed = amounts(cycle.requiredSeed());
        var external = amounts(cycle.externalInputs());
        var output = amounts(cycle.requiredOutputs());
        var initial = amounts(cycle.recursion().initialInventory());
        var available = amounts(cycle.availableAmounts());
        var keys = new LinkedHashSet<>(seed.keySet());
        keys.addAll(initial.keySet());
        keys.addAll(external.keySet());
        for (var layer : cycle.recursion().layers()) {
            layer.addedInputs().forEach(value -> keys.add(value.key()));
            layer.inventory().forEach(value -> keys.add(value.key()));
        }
        keys.addAll(output.keySet());
        var layers = cycle.recursion().layers();
        var ending = layers.isEmpty() ? initial : amounts(layers.get(layers.size() - 1).inventory());
        List<Balance> result = new ArrayList<>();
        for (AEKey key : keys) {
            var needed = seed.getOrDefault(key, PlannerAmount.ZERO);
            var delivered = output.getOrDefault(key, PlannerAmount.ZERO);
            var retained = cycle.recursion().complete()
                ? ending.getOrDefault(key, PlannerAmount.ZERO).subtract(delivered) : null;
            result.add(new Balance(key, needed, external.getOrDefault(key, PlannerAmount.ZERO), delivered,
                initial.getOrDefault(key, PlannerAmount.ZERO).subtract(needed).max(PlannerAmount.ZERO), retained,
                retained == null ? null : retained.subtract(needed),
                cycle.availableAmounts().isEmpty() ? null
                    : needed.subtract(available.getOrDefault(key, PlannerAmount.ZERO)).max(PlannerAmount.ZERO)));
        }
        return List.copyOf(result);
    }

    static Map<AEKey, PlannerAmount> amounts(List<KeyAmount> values) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        values.forEach(value -> result.merge(value.key(), value.plannerAmount(), PlannerAmount::add));
        return result;
    }
}
