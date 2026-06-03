package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts recipe-finding logic from
 * {@link ECOIntegratedWorkingStationBlockEntity}
 * to reduce responsibilities in the main BE class.
 */
final class ECOIntegratedWorkingStationRecipeHelper {

    private ECOIntegratedWorkingStationRecipeHelper() {
    }

    /**
     * Find a matching Integrated Working Station recipe from the current inputs.
     */
    @Nullable
    static IntegratedWorkingStationRecipe findRecipe(Level level, InternalInventory inputInv, FluidStack inputFluid) {
        List<ItemStack> inputs = new ArrayList<>();
        for (int x = 0; x < inputInv.size(); x++) {
            inputs.add(inputInv.getStackInSlot(x));
        }
        return level.getRecipeManager().getRecipeFor(
                NERecipeTypes.INTEGRATED_WORKING_STATION.get(),
                new IntegratedWorkingStationRecipe.Input(inputs, inputFluid),
                level).orElse(null);
    }

}
