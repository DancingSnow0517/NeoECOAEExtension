package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runtime-only recipe contract. Never registered in the single-block recipe type. */
public record LargeWorkstationRecipe(ResourceLocation id, IntegratedWorkingStationRecipe display,
                                     long energy, List<GenericStack> extraInputs) {
    public LargeWorkstationRecipe {
        if (energy < 0) throw new IllegalArgumentException("Negative recipe energy");
        extraInputs = List.copyOf(extraInputs);
    }

    public boolean matches(KeyCounter inputs, KeyCounter outputs) {
        return matchesOutputs(outputs) && matchesInputs(inputs);
    }

    public boolean matchesOutputs(KeyCounter outputs) {
        KeyCounter expected = new KeyCounter();
        if (display.hasItemOutput()) expected.add(AEItemKey.of(display.itemOutput()), display.itemOutput().getCount());
        if (display.hasFluidOutput()) expected.add(AEFluidKey.of(display.fluidOutput()), display.fluidOutput().getAmount());
        expected.removeAll(outputs);
        for (var entry : expected) if (entry.getLongValue() != 0) return false;
        return true;
    }

    public boolean matchesInputs(KeyCounter inputs) {
        KeyCounter remaining = new KeyCounter();
        remaining.addAll(inputs);
        for (var extra : extraInputs) {
            long supplied = remaining.get(extra.what());
            // Upstream processing patterns omit lightning; missing costs are acquired from ME.
            if (supplied != 0 && supplied != extra.amount()) return false;
            remaining.remove(extra.what(), supplied);
        }
        List<GenericStack> items = new ArrayList<>();
        long fluidAmount = 0;
        for (var entry : remaining) {
            long amount = entry.getLongValue();
            if (amount == 0) continue;
            if (amount < 0) return false;
            if (entry.getKey() instanceof AEItemKey) {
                items.add(new GenericStack(entry.getKey(), amount));
            } else if (entry.getKey() instanceof AEFluidKey fluid) {
                if (display.inputFluid().ingredient().isEmpty()
                    || !display.inputFluid().ingredient().test(fluid.toStack(1))) return false;
                fluidAmount = Math.addExact(fluidAmount, amount);
            } else return false;
        }
        if (fluidAmount != (display.inputFluid().ingredient().isEmpty() ? 0 : display.inputFluid().amount())) return false;

        // Integral max flow handles overlapping tags without greedy allocation failures, and
        // keeps large ingredient quantities compact instead of expanding them into 64-item stacks.
        var required = display.inputItems();
        long[] supplied = items.stream().mapToLong(GenericStack::amount).toArray();
        long[] needed = required.stream().mapToLong(r -> r.count()).toArray();
        boolean[][] accepts = new boolean[items.size()][required.size()];
        for (int i = 0; i < items.size(); i++) {
            var stack = ((AEItemKey) items.get(i).what()).toStack();
            for (int j = 0; j < required.size(); j++) accepts[i][j] = required.get(j).ingredient().test(stack);
        }
        return matchesQuantities(supplied, needed, accepts);
    }

    static boolean matchesQuantities(long[] supplied, long[] needed, boolean[][] accepts) {
        long supply = 0, demand = 0;
        for (long amount : supplied) { if (amount <= 0) return false; supply = Math.addExact(supply, amount); }
        for (long amount : needed) { if (amount <= 0) return false; demand = Math.addExact(demand, amount); }
        if (supply != demand) return false;
        int sink = supplied.length + needed.length + 1;
        long[][] residual = new long[sink + 1][sink + 1];
        for (int i = 0; i < supplied.length; i++) {
            residual[0][i + 1] = supplied[i];
            for (int j = 0; j < needed.length; j++) {
                if (accepts[i][j]) residual[i + 1][supplied.length + j + 1] = supplied[i];
            }
        }
        for (int j = 0; j < needed.length; j++) residual[supplied.length + j + 1][sink] = needed[j];
        long matched = 0;
        while (matched < demand) {
            int[] parent = new int[sink + 1];
            Arrays.fill(parent, -1);
            parent[0] = 0;
            var queue = new ArrayDeque<Integer>();
            queue.add(0);
            while (!queue.isEmpty() && parent[sink] == -1) {
                int from = queue.removeFirst();
                for (int to = 1; to <= sink; to++) {
                    if (parent[to] == -1 && residual[from][to] > 0) {
                        parent[to] = from;
                        queue.add(to);
                    }
                }
            }
            if (parent[sink] == -1) return false;
            long amount = demand - matched;
            for (int to = sink; to != 0; to = parent[to]) amount = Math.min(amount, residual[parent[to]][to]);
            for (int to = sink; to != 0; to = parent[to]) {
                residual[parent[to]][to] -= amount;
                residual[to][parent[to]] += amount;
            }
            matched += amount;
        }
        return true;
    }
}
