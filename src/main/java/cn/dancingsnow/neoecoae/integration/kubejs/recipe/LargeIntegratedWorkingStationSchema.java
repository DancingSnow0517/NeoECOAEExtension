package cn.dancingsnow.neoecoae.integration.kubejs.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import dev.latvian.mods.kubejs.recipe.schema.KubeRecipeFactory;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;

import static cn.dancingsnow.neoecoae.integration.kubejs.recipe.IntegratedWorkingStationSchema.*;

/** Uses the native multiblock recipe type while sharing the workstation's fields and fluent methods. */
public interface LargeIntegratedWorkingStationSchema {
    RecipeSchema SCHEMA = new RecipeSchema(ENERGY, INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT, PROCESSING_TICKS)
        .factory(new KubeRecipeFactory(NeoECOAE.id("large_integrated_working_station"),
            IntegratedWorkingStationKubeRecipe.class, IntegratedWorkingStationKubeRecipe::new))
        .constructor(ENERGY)
        .constructor(ENERGY, INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT)
        .constructor(ENERGY, INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT, PROCESSING_TICKS);
}
