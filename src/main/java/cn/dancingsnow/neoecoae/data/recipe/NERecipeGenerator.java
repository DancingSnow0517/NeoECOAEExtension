package cn.dancingsnow.neoecoae.data.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.tags.MekanismTags;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.tags.FluidTags;
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
            .coolant(500)
            .save(notMekanism, NeoECOAE.id("cooling/water"));

        CoolingRecipe.builder()
            .input(FluidTags.WATER, 100)
            .output(new FluidStack(MekanismFluids.STEAM, 100))
            .coolant(500)
            .save(hasMekanism, NeoECOAE.id("cooling/water_with_steam"));

        CoolingRecipe.builder()
            .input(MekanismTags.Fluids.SODIUM, 100)
            .output(new FluidStack(MekanismFluids.SUPERHEATED_SODIUM, 100))
            .coolant(1500)
            .save(hasMekanism, NeoECOAE.id("cooling/sodium"));
    }
}
