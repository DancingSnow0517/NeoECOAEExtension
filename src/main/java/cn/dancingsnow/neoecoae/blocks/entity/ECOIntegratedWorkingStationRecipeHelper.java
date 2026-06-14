package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.compat.crafting.SizedIngredient;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts recipe-finding logic from
 * {@link ECOIntegratedWorkingStationBlockEntity}
 * to reduce responsibilities in the main BE class.
 */
final class ECOIntegratedWorkingStationRecipeHelper {

    private ECOIntegratedWorkingStationRecipeHelper() {}

    /**
     * Find a matching Integrated Working Station recipe from the current inputs.
     */
    @Nullable static IntegratedWorkingStationRecipe findRecipe(Level level, InternalInventory inputInv, FluidStack inputFluid) {
        List<ItemStack> inputs = new ArrayList<>();
        for (int x = 0; x < inputInv.size(); x++) {
            inputs.add(inputInv.getStackInSlot(x));
        }
        return level.getRecipeManager()
                .getRecipeFor(
                        NERecipeTypes.INTEGRATED_WORKING_STATION.get(),
                        new IntegratedWorkingStationRecipe.Input(inputs, inputFluid),
                        level)
                .orElse(null);
    }

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
