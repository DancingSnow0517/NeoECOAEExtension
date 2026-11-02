package cn.dancingsnow.neoecoae.integration.kubejs.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.FluidStackComponent;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.SizedFluidIngredientComponent;
import dev.latvian.mods.kubejs.recipe.schema.KubeRecipeFactory;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * KubeJS 8 schema for {@code neoecoae:cooling}; matches {@link cn.dancingsnow.neoecoae.recipe.CoolingRecipe}'s codec:
 * {@code input} (sized fluid ingredient), {@code coolant}, optional {@code output} (FluidStackTemplate shape),
 * optional {@code max_overclock} (default 0).
 */
public interface CoolingRecipeSchema {

    class CoolingKubeRecipe extends KubeRecipe {
        @Override
        public void serialize() {
            super.serialize();
            // Native codec: Optional<FluidStackTemplate>, which rejects the "{}" of an empty fluid stack.
            KubeSchemaUtil.removeIfEmpty(this, OUTPUT);
        }
    }

    RecipeKey<SizedFluidIngredient> INPUT = SizedFluidIngredientComponent.SIZED_FLUID_INGREDIENT.inputKey("input");
    RecipeKey<Integer> COOLANT = NumberComponent.intRange(0, Integer.MAX_VALUE).otherKey("coolant");
    RecipeKey<FluidStack> OUTPUT = FluidStackComponent.OPTIONAL_FLUID_STACK.outputKey("output").defaultOptional();
    RecipeKey<Integer> MAX_OVERCLOCK = NumberComponent.intRange(0, Integer.MAX_VALUE).otherKey("max_overclock").defaultOptional();

    RecipeSchema SCHEMA = new RecipeSchema(INPUT, COOLANT, OUTPUT, MAX_OVERCLOCK)
        .factory(new KubeRecipeFactory(NeoECOAE.id("cooling"), CoolingKubeRecipe.class, CoolingKubeRecipe::new))
        .constructor(INPUT, COOLANT)
        .constructor(INPUT, COOLANT, OUTPUT)
        .constructor(INPUT, COOLANT, OUTPUT, MAX_OVERCLOCK);
}
