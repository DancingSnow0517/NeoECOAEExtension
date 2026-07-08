package cn.dancingsnow.neoecoae.integration.kubejs.recipe;

import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.FluidStackComponent;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.SizedFluidIngredientComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public interface CoolingRecipeSchema {
    RecipeKey<SizedFluidIngredient> INPUT = SizedFluidIngredientComponent.FLAT.inputKey("input");
    RecipeKey<Integer> COOLANT = NumberComponent.intRange(0, Integer.MAX_VALUE).otherKey("coolant");
    RecipeKey<Integer> MAX_OVERCLOCK = NumberComponent.intRange(0, Integer.MAX_VALUE).otherKey("max_overclock").defaultOptional();
    RecipeKey<FluidStack> OUTPUT = FluidStackComponent.OPTIONAL_FLUID_STACK.outputKey("output").defaultOptional();

    RecipeSchema SCHEMA = new RecipeSchema(INPUT, COOLANT, OUTPUT, MAX_OVERCLOCK)
        .constructor(INPUT, COOLANT, OUTPUT, MAX_OVERCLOCK)
        .constructor(INPUT, COOLANT, OUTPUT)
        .constructor(INPUT, COOLANT);
}
