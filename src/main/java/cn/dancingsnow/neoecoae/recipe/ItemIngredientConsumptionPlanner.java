package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.recipe.ingredient.SizedIngredient;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ItemIngredientConsumptionPlanner {
    private ItemIngredientConsumptionPlanner() {}

    @Nullable public static int[] createPlan(List<ItemStack> stacks, List<SizedIngredient> ingredients) {
        int[] available = new int[stacks.size()];
        boolean[][] matches = new boolean[ingredients.size()][stacks.size()];
        int[] required = new int[ingredients.size()];

        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            available[slot] = stack == null ? 0 : stack.getCount();
        }
        for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
            SizedIngredient requirement = ingredients.get(ingredient);
            required[ingredient] = requirement.count();
            for (int slot = 0; slot < stacks.size(); slot++) {
                ItemStack stack = stacks.get(slot);
                matches[ingredient][slot] =
                        stack != null && requirement.ingredient().test(stack);
            }
        }
        return createPlan(available, matches, required);
    }

    @Nullable public static int[] createPlan(int[] available, boolean[][] matches, int[] required) {
        validateDimensions(available, matches, required);

        long totalAvailable = 0;
        for (int amount : available) {
            if (amount < 0) {
                throw new IllegalArgumentException("Available item counts must not be negative");
            }
            totalAvailable += amount;
        }

        long totalRequired = 0;
        for (int amount : required) {
            if (amount <= 0) {
                return null;
            }
            totalRequired += amount;
        }
        if (totalRequired > totalAvailable) {
            return null;
        }

        int ingredientCount = required.length;
        int slotCount = available.length;
        int source = 0;
        int firstIngredient = 1;
        int firstSlot = firstIngredient + ingredientCount;
        int sink = firstSlot + slotCount;
        long[][] residual = new long[sink + 1][sink + 1];

        for (int ingredient = 0; ingredient < ingredientCount; ingredient++) {
            residual[source][firstIngredient + ingredient] = required[ingredient];
            for (int slot = 0; slot < slotCount; slot++) {
                if (matches[ingredient][slot]) {
                    residual[firstIngredient + ingredient][firstSlot + slot] =
                            Math.min(required[ingredient], available[slot]);
                }
            }
        }
        for (int slot = 0; slot < slotCount; slot++) {
            residual[firstSlot + slot][sink] = available[slot];
        }

        long flow = 0;
        int[] parent = new int[residual.length];
        while (findAugmentingPath(residual, source, sink, parent)) {
            long pathCapacity = Long.MAX_VALUE;
            for (int node = sink; node != source; node = parent[node]) {
                pathCapacity = Math.min(pathCapacity, residual[parent[node]][node]);
            }
            for (int node = sink; node != source; node = parent[node]) {
                residual[parent[node]][node] -= pathCapacity;
                residual[node][parent[node]] += pathCapacity;
            }
            flow += pathCapacity;
        }

        if (flow != totalRequired) {
            return null;
        }

        int[] consumption = new int[slotCount];
        for (int slot = 0; slot < slotCount; slot++) {
            consumption[slot] = (int) (available[slot] - residual[firstSlot + slot][sink]);
        }
        return consumption;
    }

    private static boolean findAugmentingPath(long[][] residual, int source, int sink, int[] parent) {
        Arrays.fill(parent, -1);
        parent[source] = source;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next = 0; next < residual.length; next++) {
                if (parent[next] == -1 && residual[current][next] > 0) {
                    parent[next] = current;
                    if (next == sink) {
                        return true;
                    }
                    queue.addLast(next);
                }
            }
        }
        return false;
    }

    private static void validateDimensions(int[] available, boolean[][] matches, int[] required) {
        if (matches.length != required.length) {
            throw new IllegalArgumentException("Match rows must equal required ingredient count");
        }
        for (boolean[] row : matches) {
            if (row.length != available.length) {
                throw new IllegalArgumentException("Each match row must equal available slot count");
            }
        }
    }
}
