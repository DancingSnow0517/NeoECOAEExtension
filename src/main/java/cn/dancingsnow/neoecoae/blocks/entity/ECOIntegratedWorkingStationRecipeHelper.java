package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts recipe-finding logic from
 * {@link ECOIntegratedWorkingStationBlockEntity}
 * to reduce responsibilities in the main BE class.
 */
final class ECOIntegratedWorkingStationRecipeHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static boolean loggedRecipeCounts = false;

    private ECOIntegratedWorkingStationRecipeHelper() {
    }

    /**
     * Find a matching Integrated Working Station recipe from the current inputs.
     */
    @Nullable
    static IntegratedWorkingStationRecipe findRecipe(Level level, InternalInventory inputInv, FluidStack inputFluid) {
        logRecipeCounts(level);
        List<ItemStack> inputs = new ArrayList<>();
        for (int x = 0; x < inputInv.size(); x++) {
            inputs.add(inputInv.getStackInSlot(x));
        }
        return level.getRecipeManager().getRecipeFor(
                NERecipeTypes.INTEGRATED_WORKING_STATION.get(),
                new IntegratedWorkingStationRecipe.Input(inputs, inputFluid),
                level).orElse(null);
    }

    private static void logRecipeCounts(Level level) {
        if (FMLEnvironment.production || loggedRecipeCounts) {
            return;
        }
        loggedRecipeCounts = true;

        int integratedCount = level.getRecipeManager()
                .getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get()).size();
        int coolingCount = level.getRecipeManager()
                .getAllRecipesFor(NERecipeTypes.COOLING.get()).size();

        LOGGER.info(
                "NeoECOAE recipe counts: integrated_working_station={}, cooling={}",
                integratedCount,
                coolingCount);

        if (integratedCount == 0) {
            LOGGER.warn("Integrated Working Station recipes are not loaded. Check data/neoecoae/recipes path.");
        }
    }
}
