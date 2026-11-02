package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import java.util.HashSet;
import java.util.Set;

/** Presence-only overapproximation: a negative answer proves impossibility, a positive one proves nothing. */
final class RouteAvailabilityProof {
    private RouteAvailabilityProof() {}

    static boolean goalUnreachable(CompiledNetwork network, PlannerInventorySnapshot stock,
            ECOCancellation cancellation) throws InterruptedException {
        OptimisticSupplyModel model = OptimisticSupplyModel.compile(network, cancellation);
        if (model == null) return false;
        Set<AEKey> available = new HashSet<>(model.emitted);
        for (var entry : stock.toKeyCounter()) {
            cancellation.checkpoint();
            if (entry.getLongValue() > 0L) available.add(entry.getKey());
        }
        if (available.contains(network.goal())) return false;
        record Slot(int recipe, int input) {}
        int[] missing = new int[model.recipes.size()];
        boolean[][] satisfied = new boolean[model.recipes.size()][];
        var waiting = new java.util.HashMap<AEKey, java.util.List<Slot>>();
        var ready = new java.util.ArrayDeque<Integer>();
        for (int r = 0; r < model.recipes.size(); r++) {
            var recipe = model.recipes.get(r);
            satisfied[r] = new boolean[recipe.inputs().size()];
            for (int i = 0; i < recipe.inputs().size(); i++) {
                cancellation.checkpoint();
                var input = recipe.inputs().get(i);
                if (input.available(available)) satisfied[r][i] = true;
                else {
                    missing[r]++;
                    for (var alternative : input.alternatives()) waiting
                        .computeIfAbsent(alternative.key(), ignored -> new java.util.ArrayList<>()).add(new Slot(r, i));
                }
            }
            if (missing[r] == 0) ready.add(r);
        }
        while (!ready.isEmpty()) {
            cancellation.checkpoint();
            for (AEKey output : model.recipes.get(ready.removeFirst()).outputs().keySet()) {
                if (!available.add(output)) continue;
                if (output.equals(network.goal())) return false;
                for (Slot slot : waiting.getOrDefault(output, java.util.List.of())) {
                    if (satisfied[slot.recipe()][slot.input()]) continue;
                    satisfied[slot.recipe()][slot.input()] = true;
                    if (--missing[slot.recipe()] == 0) ready.add(slot.recipe());
                }
            }
        }
        return true;
    }
}
