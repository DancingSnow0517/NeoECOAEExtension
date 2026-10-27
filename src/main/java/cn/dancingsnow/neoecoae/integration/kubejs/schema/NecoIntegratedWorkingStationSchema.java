package cn.dancingsnow.neoecoae.integration.kubejs.schema;

import dev.latvian.mods.kubejs.fluid.InputFluid;
import dev.latvian.mods.kubejs.fluid.OutputFluid;
import dev.latvian.mods.kubejs.item.InputItem;
import dev.latvian.mods.kubejs.item.OutputItem;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;

public interface NecoIntegratedWorkingStationSchema {
    RecipeKey<InputItem[]> INPUT_ITEMS = NecoRecipeComponents.SIZED_INPUT_ITEM.asArray().key("inputItems").defaultOptional();
    RecipeKey<InputFluid> INPUT_FLUID = NecoRecipeComponents.ID_COMPAT_INPUT_FLUID.key("inputFluid").defaultOptional();
    RecipeKey<OutputItem> ITEM_OUTPUT = NecoRecipeComponents.ID_COMPAT_OUTPUT_ITEM.key("itemOutput").defaultOptional();
    RecipeKey<OutputFluid> FLUID_OUTPUT = NecoRecipeComponents.ID_COMPAT_OUTPUT_FLUID.key("fluidOutput").defaultOptional();
    RecipeKey<Integer> ENERGY = NumberComponent.intRange(1, Integer.MAX_VALUE).key("energy").optional(1000);
    RecipeKey<Integer> PROCESSING_TICKS = NumberComponent.intRange(1, Integer.MAX_VALUE).key("processingTicks").optional(100);

    RecipeSchema SCHEMA = new RecipeSchema(IntegratedWorkingStationRecipeJS.class, IntegratedWorkingStationRecipeJS::new,
            INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT, ENERGY, PROCESSING_TICKS)
            .constructor(INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT, ENERGY, PROCESSING_TICKS)
            .constructor(INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT, ENERGY)
            .constructor();
}