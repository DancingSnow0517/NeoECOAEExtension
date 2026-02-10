package cn.dancingsnow.neoecoae.data.recipe;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.tags.MekanismTags;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.conditions.NotCondition;
import net.neoforged.neoforge.fluids.FluidStack;

public class NERecipeGenerator {
    public static void accept(RegistrateRecipeProvider provider) {
        ICondition mekanismLoaded = new ModLoadedCondition("mekanism");
        RecipeOutput notMekanism = provider.withConditions(new NotCondition(mekanismLoaded));
        RecipeOutput hasMekanism = provider.withConditions(mekanismLoaded);

        CoolingRecipe.builder()
            .input(FluidTags.WATER, 100)
            .coolant(1500)
            .save(notMekanism, NeoECOAE.id("cooling/water"));

        CoolingRecipe.builder()
            .input(FluidTags.WATER, 100)
            .output(new FluidStack(MekanismFluids.STEAM, 100))
            .coolant(1500)
            .save(hasMekanism, NeoECOAE.id("cooling/water_with_steam"));

        CoolingRecipe.builder()
            .input(MekanismTags.Fluids.SODIUM, 100)
            .output(new FluidStack(MekanismFluids.SUPERHEATED_SODIUM, 100))
            .coolant(5000)
            .save(hasMekanism, NeoECOAE.id("cooling/sodium"));

        IntegratedWorkingStationRecipe.builder()
            .require(AEItems.ITEM_CELL_1K, 4)
            .itemOutput(AEItems.ITEM_CELL_4K.stack())
            .save(provider);
        IntegratedWorkingStationRecipe.builder()
            .requireFluid(Tags.Fluids.WATER, 1000)
            .fluidOutput(new FluidStack(Fluids.LAVA, 1000))
            .save(provider, NeoECOAE.id("integrated_working_station/test"));

        //ECO - CE4
        IntegratedWorkingStationRecipe.builder()
            .require(AEBlocks.CRAFTING_STORAGE_256K, 256)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 4)
            .itemOutput(NEItems.ECO_COMPUTATION_CELL_L4.asStack())
            .save(provider);
        //ECO - CE6
        IntegratedWorkingStationRecipe.builder()
            .require(NEItems.ECO_COMPUTATION_CELL_L4, 4)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 16)
            .itemOutput(NEItems.ECO_COMPUTATION_CELL_L6.asStack())
            .save(provider);
        //ECO - CE9
        IntegratedWorkingStationRecipe.builder()
            .require(NEItems.ECO_COMPUTATION_CELL_L6, 4)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 64)
            .itemOutput(NEItems.ECO_COMPUTATION_CELL_L9.asStack())
            .save(provider);
    }
}
