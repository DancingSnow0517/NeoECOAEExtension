package cn.dancingsnow.neoecoae.compat.kubejs.recipe;

import dev.latvian.mods.kubejs.fluid.InputFluid;
import dev.latvian.mods.kubejs.fluid.OutputFluid;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;

public interface CoolingRecipeSchema {
    RecipeKey<InputFluid> INPUT = NERecipeComponents.ID_COMPAT_INPUT_FLUID.key("input");
    RecipeKey<Integer> COOLANT = NumberComponent.intRange(0, Integer.MAX_VALUE).key("coolant");
    RecipeKey<OutputFluid> OUTPUT =
            NERecipeComponents.ID_COMPAT_OUTPUT_FLUID.key("output").defaultOptional();
    RecipeKey<Integer> MAX_OVERCLOCK = NumberComponent.intRange(0, Integer.MAX_VALUE)
            .key("max_overclock")
            .defaultOptional()
            .preferred("maxOverclock");

    RecipeSchema SCHEMA = new RecipeSchema(INPUT, COOLANT, OUTPUT, MAX_OVERCLOCK)
            .constructor(INPUT, COOLANT, OUTPUT, MAX_OVERCLOCK)
            .constructor(INPUT, COOLANT, OUTPUT)
            .constructor(INPUT, COOLANT);
}
