package cn.dancingsnow.neoecoae.integration.kubejs.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.FluidStackComponent;
import dev.latvian.mods.kubejs.recipe.component.ItemStackComponent;
import dev.latvian.mods.kubejs.recipe.component.ListRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.SizedFluidIngredientComponent;
import dev.latvian.mods.kubejs.recipe.component.SizedIngredientComponent;
import dev.latvian.mods.kubejs.recipe.schema.KubeRecipeFactory;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.util.IntBounds;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * KubeJS 8 schema for {@code neoecoae:integrated_working_station}.
 * JSON layout matches {@link cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe#CODEC}:
 * {@code inputItems} (optional list), {@code inputFluid} (optional), {@code itemOutput} / {@code fluidOutput}
 * (optional ItemStackTemplate / FluidStackTemplate shapes), {@code energy}, {@code processingTicks} (default 100).
 */
public interface IntegratedWorkingStationSchema {

    class IntegratedWorkingStationKubeRecipe extends KubeRecipe {

        public IntegratedWorkingStationKubeRecipe require(SizedIngredient ingredient) {
            // Values may come from a script/constructor as immutable lists; never mutate them in place.
            List<SizedIngredient> current = getValue(INPUT_ITEMS);
            List<SizedIngredient> inputs = current == null ? new ArrayList<>() : new ArrayList<>(current);
            inputs.add(ingredient);
            setValue(INPUT_ITEMS, inputs);
            return this;
        }

        public IntegratedWorkingStationKubeRecipe requireFluid(SizedFluidIngredient ingredient) {
            setValue(INPUT_FLUID, ingredient);
            return this;
        }

        public IntegratedWorkingStationKubeRecipe itemOutput(ItemStack itemStack) {
            setValue(ITEM_OUTPUT, itemStack);
            return this;
        }

        public IntegratedWorkingStationKubeRecipe fluidOutput(FluidStack fluidStack) {
            setValue(FLUID_OUTPUT, fluidStack);
            return this;
        }

        public IntegratedWorkingStationKubeRecipe energy(int energy) {
            setValue(ENERGY, energy);
            return this;
        }

        public IntegratedWorkingStationKubeRecipe processingTicks(int ticks) {
            setValue(PROCESSING_TICKS, ticks);
            return this;
        }

        @Override
        public void serialize() {
            super.serialize();
            // The native codec uses Optional<ItemStackTemplate/FluidStackTemplate/SizedFluidIngredient>,
            // which reject the "{}" an empty optional stack encodes to: omit empty values instead.
            KubeSchemaUtil.removeIfEmpty(this, ITEM_OUTPUT);
            KubeSchemaUtil.removeIfEmpty(this, FLUID_OUTPUT);
            KubeSchemaUtil.removeIfEmpty(this, INPUT_FLUID);
        }
    }

    ListRecipeComponent<SizedIngredient> LIST_INPUT = ListRecipeComponent.create(
        SizedIngredientComponent.SIZED_INGREDIENT, true, false, IntBounds.OPTIONAL, Optional.empty());

    RecipeKey<List<SizedIngredient>> INPUT_ITEMS = LIST_INPUT.inputKey("inputItems").defaultOptional();
    RecipeKey<SizedFluidIngredient> INPUT_FLUID = SizedFluidIngredientComponent.OPTIONAL_SIZED_FLUID_INGREDIENT.inputKey("inputFluid").defaultOptional();
    RecipeKey<ItemStack> ITEM_OUTPUT = ItemStackComponent.OPTIONAL_ITEM_STACK.outputKey("itemOutput").defaultOptional();
    RecipeKey<FluidStack> FLUID_OUTPUT = FluidStackComponent.OPTIONAL_FLUID_STACK.outputKey("fluidOutput").defaultOptional();
    RecipeKey<Integer> ENERGY = NumberComponent.intRange(0, Integer.MAX_VALUE).otherKey("energy");
    RecipeKey<Integer> PROCESSING_TICKS = NumberComponent.intRange(1, Integer.MAX_VALUE).otherKey("processingTicks").optional(100);

    RecipeSchema SCHEMA = new RecipeSchema(ENERGY, INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT, PROCESSING_TICKS)
        .factory(new KubeRecipeFactory(NeoECOAE.id("integrated_working_station"), IntegratedWorkingStationKubeRecipe.class, IntegratedWorkingStationKubeRecipe::new))
        .constructor(ENERGY, INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT);
}
