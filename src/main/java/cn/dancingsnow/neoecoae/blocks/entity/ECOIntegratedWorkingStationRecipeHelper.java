package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.compat.crafting.SizedIngredient;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts recipe-finding logic from
 * {@link ECOIntegratedWorkingStationBlockEntity}
 * to reduce responsibilities in the main BE class.
 */
final class ECOIntegratedWorkingStationRecipeHelper {

    private ECOIntegratedWorkingStationRecipeHelper() {}

    @Nullable static int[] createItemConsumptionPlan(InternalInventory inputInv, List<SizedIngredient> ingredients) {
        int[] available = new int[inputInv.size()];
        boolean[][] matches = new boolean[ingredients.size()][inputInv.size()];
        int[] required = new int[ingredients.size()];
        for (int slot = 0; slot < inputInv.size(); slot++) {
            available[slot] = inputInv.getStackInSlot(slot).getCount();
        }
        for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
            SizedIngredient requirement = ingredients.get(ingredient);
            required[ingredient] = requirement.count();
            for (int slot = 0; slot < inputInv.size(); slot++) {
                matches[ingredient][slot] = requirement.ingredient().test(inputInv.getStackInSlot(slot));
            }
        }
        return createItemConsumptionPlan(available, matches, required);
    }

    @Nullable static int[] createItemConsumptionPlan(int[] available, boolean[][] matches, int[] required) {
        int[] remainingBySlot = available.clone();
        int[] consumption = new int[available.length];
        for (int ingredient = 0; ingredient < required.length; ingredient++) {
            int remaining = required[ingredient];
            for (int slot = 0; slot < remainingBySlot.length && remaining > 0; slot++) {
                if (remainingBySlot[slot] <= 0 || !matches[ingredient][slot]) {
                    continue;
                }
                int taken = Math.min(remainingBySlot[slot], remaining);
                remainingBySlot[slot] -= taken;
                consumption[slot] += taken;
                remaining -= taken;
            }
            if (remaining > 0) {
                return null;
            }
        }
        return consumption;
    }
}
