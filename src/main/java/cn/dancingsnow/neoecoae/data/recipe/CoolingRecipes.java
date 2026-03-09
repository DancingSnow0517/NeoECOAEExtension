package cn.dancingsnow.neoecoae.data.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEFluids;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.tags.MekanismTags;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.conditions.NotCondition;

public class CoolingRecipes {
    public static void init(RegistrateRecipeProvider provider) {
        ICondition mekanismLoaded = new ModLoadedCondition("mekanism");
        RecipeOutput notMekanism = provider.withConditions(new NotCondition(mekanismLoaded));
        RecipeOutput hasMekanism = provider.withConditions(mekanismLoaded);

        CoolingRecipe.builder()
            .input(FluidTags.WATER, 100)
            .coolant(1500)
            .maxOverclock(2)
            .save(notMekanism, NeoECOAE.id("cooling/water"));

        CoolingRecipe.builder()
            .input(FluidTags.WATER, 100)
            .output(MekanismFluids.STEAM, 100)
            .coolant(1500)
            .maxOverclock(2)
            .save(hasMekanism, NeoECOAE.id("cooling/water_with_steam"));

        CoolingRecipe.builder()
            .input(MekanismTags.Fluids.SODIUM, 100)
            .output(MekanismFluids.SUPERHEATED_SODIUM, 100)
            .coolant(5000)
            .maxOverclock(6)
            .save(hasMekanism, NeoECOAE.id("cooling/sodium"));

        CoolingRecipe.builder()
            .input(NEFluids.CRYOTHEUM_SOLUTION.getSource(), 100)
            .coolant(12000)
            .maxOverclock(9)
            .save(provider, NeoECOAE.id("cooling/cryotheum_solution"));
    }
}
