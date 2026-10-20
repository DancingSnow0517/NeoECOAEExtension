package cn.dancingsnow.neoecoae.data.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEFluids;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import net.minecraft.tags.FluidTags;

public class CoolingRecipes {
    public static void init(RegistrateRecipeProvider provider) {
        CoolingRecipe.builder().input(FluidTags.WATER, 100).coolant(1500).maxOverclock(2)
            .save(provider, NeoECOAE.id("cooling/water"));
        CoolingRecipe.builder().input(NEFluids.CRYOTHEUM_SOLUTION.getSource(), 100).coolant(12000).maxOverclock(9)
            .save(provider, NeoECOAE.id("cooling/cryotheum_solution"));
    }
}
