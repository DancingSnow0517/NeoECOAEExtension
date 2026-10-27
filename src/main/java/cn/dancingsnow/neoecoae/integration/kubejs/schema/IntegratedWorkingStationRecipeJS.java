package cn.dancingsnow.neoecoae.integration.kubejs.schema;

import dev.latvian.mods.kubejs.item.InputItem;
import dev.latvian.mods.kubejs.recipe.RecipeJS;

public class IntegratedWorkingStationRecipeJS extends RecipeJS {
    public IntegratedWorkingStationRecipeJS require(Object ingredient) {
        setValue(NecoIntegratedWorkingStationSchema.INPUT_ITEMS, add(getValue(NecoIntegratedWorkingStationSchema.INPUT_ITEMS), NecoRecipeComponents.SIZED_INPUT_ITEM.read(this, ingredient)));
        save();
        return this;
    }

    public IntegratedWorkingStationRecipeJS inputItems(Object items) {
        setValue(NecoIntegratedWorkingStationSchema.INPUT_ITEMS, NecoIntegratedWorkingStationSchema.INPUT_ITEMS.component.read(this, items));
        save();
        return this;
    }

    public IntegratedWorkingStationRecipeJS requireFluid(Object ingredient) {
        return inputFluid(ingredient);
    }

    public IntegratedWorkingStationRecipeJS inputFluid(Object fluid) {
        setValue(NecoIntegratedWorkingStationSchema.INPUT_FLUID, NecoIntegratedWorkingStationSchema.INPUT_FLUID.component.read(this, fluid));
        save();
        return this;
    }

    public IntegratedWorkingStationRecipeJS itemOutput(Object item) {
        setValue(NecoIntegratedWorkingStationSchema.ITEM_OUTPUT, NecoIntegratedWorkingStationSchema.ITEM_OUTPUT.component.read(this, item));
        save();
        return this;
    }

    public IntegratedWorkingStationRecipeJS fluidOutput(Object fluid) {
        setValue(NecoIntegratedWorkingStationSchema.FLUID_OUTPUT, NecoIntegratedWorkingStationSchema.FLUID_OUTPUT.component.read(this, fluid));
        save();
        return this;
    }

    public IntegratedWorkingStationRecipeJS energy(int energy) {
        setValue(NecoIntegratedWorkingStationSchema.ENERGY, energy);
        save();
        return this;
    }

    public IntegratedWorkingStationRecipeJS processingTicks(int ticks) {
        setValue(NecoIntegratedWorkingStationSchema.PROCESSING_TICKS, ticks);
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