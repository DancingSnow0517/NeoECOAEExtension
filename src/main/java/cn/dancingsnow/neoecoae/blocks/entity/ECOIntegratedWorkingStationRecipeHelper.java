package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.recipe.ItemIngredientConsumptionPlanner;
import cn.dancingsnow.neoecoae.recipe.ingredient.SizedIngredient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts recipe-finding logic from
 * {@link ECOIntegratedWorkingStationBlockEntity}
 * to reduce responsibilities in the main BE class.
 */
final class ECOIntegratedWorkingStationRecipeHelper {

    private ECOIntegratedWorkingStationRecipeHelper() {}

    @Nullable static int[] createItemConsumptionPlan(InternalInventory inputInv, List<SizedIngredient> ingredients) {
        List<ItemStack> stacks = new ArrayList<>(inputInv.size());
        for (int slot = 0; slot < inputInv.size(); slot++) {
            stacks.add(inputInv.getStackInSlot(slot));
        }
        return ItemIngredientConsumptionPlanner.createPlan(stacks, ingredients);
    }

    @Nullable static int[] createItemConsumptionPlan(int[] available, boolean[][] matches, int[] required) {
        return ItemIngredientConsumptionPlanner.createPlan(available, matches, required);
    }
}
