package cn.dancingsnow.neoecoae.data.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEFluids;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import java.util.function.Consumer;
import mekanism.common.registries.MekanismFluids;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.common.crafting.conditions.NotCondition;

public class CoolingRecipes {
    public static void init(RegistrateRecipeProvider provider) {
        ICondition mekanismLoaded = new ModLoadedCondition("mekanism");
        Consumer<FinishedRecipe> notMekanism =
                recipe -> provider.accept(new ConditionalFinishedRecipe(recipe, new NotCondition(mekanismLoaded)));
        Consumer<FinishedRecipe> hasMekanism =
                recipe -> provider.accept(new ConditionalFinishedRecipe(recipe, mekanismLoaded));

        CoolingRecipe.builder()
                .input(Fluids.WATER, 100)
                .coolant(1500)
                .maxOverclock(2)
                .save(notMekanism, NeoECOAE.id("cooling/water"));

        CoolingRecipe.builder()
                .input(Fluids.WATER, 100)
                .output(MekanismFluids.STEAM.getFluid(), 100)
                .coolant(1500)
                .maxOverclock(2)
                .save(hasMekanism, NeoECOAE.id("cooling/water_with_steam"));

        CoolingRecipe.builder()
                .input(MekanismFluids.SODIUM.getFluid(), 100)
                .output(MekanismFluids.SUPERHEATED_SODIUM.getFluid(), 100)
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
