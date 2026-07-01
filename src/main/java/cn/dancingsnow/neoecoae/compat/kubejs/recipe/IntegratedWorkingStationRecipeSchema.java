package cn.dancingsnow.neoecoae.compat.kubejs.recipe;

import dev.latvian.mods.kubejs.fluid.FluidStackJS;
import dev.latvian.mods.kubejs.fluid.InputFluid;
import dev.latvian.mods.kubejs.fluid.OutputFluid;
import dev.latvian.mods.kubejs.item.InputItem;
import dev.latvian.mods.kubejs.item.OutputItem;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.FluidComponents;
import dev.latvian.mods.kubejs.recipe.component.ItemComponents;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;

public interface IntegratedWorkingStationRecipeSchema {
    class IntegratedWorkingStationRecipeJS extends RecipeJS {
        public IntegratedWorkingStationRecipeJS require(Object ingredient) {
            setValue(INPUT_ITEMS, add(getValue(INPUT_ITEMS), readInputItem(ingredient)));
            save();
            return this;
        }

        public IntegratedWorkingStationRecipeJS requireFluid(Object ingredient) {
            setValue(INPUT_FLUID, FluidStackJS.of(ingredient));
            save();
            return this;
        }

        public IntegratedWorkingStationRecipeJS itemOutput(Object item) {
            setValue(ITEM_OUTPUT, OutputItem.of(item));
            save();
            return this;
        }

        public IntegratedWorkingStationRecipeJS fluidOutput(Object fluid) {
            setValue(FLUID_OUTPUT, FluidStackJS.of(fluid));
            save();
            return this;
        }

        public IntegratedWorkingStationRecipeJS energy(int energy) {
            setValue(ENERGY, energy);
            save();
            return this;
        }

        private static InputItem[] add(InputItem[] items, InputItem item) {
            if (items == null) {
                return new InputItem[] {item};
            }
            InputItem[] copy = new InputItem[items.length + 1];
            System.arraycopy(items, 0, copy, 0, items.length);
            copy[items.length] = item;
            return copy;
        }
    }

    RecipeKey<InputItem[]> INPUT_ITEMS =
            NERecipeComponents.SIZED_INPUT_ITEM.asArray().key("inputItems").defaultOptional();
    RecipeKey<InputFluid> INPUT_FLUID = FluidComponents.INPUT.key("inputFluid").defaultOptional();
    RecipeKey<OutputItem> ITEM_OUTPUT = ItemComponents.OUTPUT.key("itemOutput").defaultOptional();
    RecipeKey<OutputFluid> FLUID_OUTPUT =
            FluidComponents.OUTPUT.key("fluidOutput").defaultOptional();
    RecipeKey<Integer> ENERGY =
            NumberComponent.intRange(0, Integer.MAX_VALUE).key("energy").defaultOptional();

    RecipeSchema SCHEMA = new RecipeSchema(
                    IntegratedWorkingStationRecipeJS.class,
                    IntegratedWorkingStationRecipeJS::new,
                    INPUT_ITEMS,
                    INPUT_FLUID,
                    ITEM_OUTPUT,
                    FLUID_OUTPUT,
                    ENERGY)
            .constructor(INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT, ENERGY)
            .constructor();
}
