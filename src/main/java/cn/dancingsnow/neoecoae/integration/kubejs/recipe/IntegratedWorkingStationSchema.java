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

public interface IntegratedWorkingStationSchema {

    class IntegratedWorkingStationKubeRecipe extends KubeRecipe {

        public IntegratedWorkingStationKubeRecipe require(SizedIngredient ingredient) {
            if (getValue(INPUT_ITEMS) == null) setValue(INPUT_ITEMS, new ArrayList<>());
            getValue(INPUT_ITEMS).add(ingredient);
            save();
            return this;
        }

        public IntegratedWorkingStationKubeRecipe requireFluid(SizedFluidIngredient ingredient) {
            setValue(INPUT_FLUID, ingredient);
            save();
            return this;
        }

        public IntegratedWorkingStationKubeRecipe itemOutput(ItemStack itemStack) {
            setValue(ITEM_OUTPUT, itemStack);
            save();
            return this;
        }

        public IntegratedWorkingStationKubeRecipe fluidOutput(FluidStack fluidStack) {
            setValue(FLUID_OUTPUT, fluidStack);
            save();
            return this;
        }

        public IntegratedWorkingStationKubeRecipe energy(int energy) {
            setValue(ENERGY, energy);
            save();
            return this;
        }
    }

    ListRecipeComponent<SizedIngredient> LIST_INPUT = ListRecipeComponent.create(SizedIngredientComponent.OPTIONAL_FLAT.instance(), true, false, IntBounds.OPTIONAL, Optional.empty());

    RecipeKey<List<SizedIngredient>> INPUT_ITEMS = LIST_INPUT.inputKey("inputItems").defaultOptional();
    RecipeKey<SizedFluidIngredient> INPUT_FLUID = SizedFluidIngredientComponent.OPTIONAL_FLAT.inputKey("inputFluid").defaultOptional();
    RecipeKey<ItemStack> ITEM_OUTPUT = ItemStackComponent.OPTIONAL_ITEM_STACK.outputKey("itemOutput").defaultOptional();
    RecipeKey<FluidStack> FLUID_OUTPUT = FluidStackComponent.OPTIONAL_FLUID_STACK.outputKey("fluidOutput").defaultOptional();
    RecipeKey<Integer> ENERGY = NumberComponent.intRange(0, Integer.MAX_VALUE).otherKey("energy").defaultOptional();

    RecipeSchema SCHEMA = new RecipeSchema(INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT, ENERGY)
        .factory(new KubeRecipeFactory(NeoECOAE.id("integrated_working_station"), IntegratedWorkingStationKubeRecipe.class, IntegratedWorkingStationKubeRecipe::new))
        .constructor(INPUT_ITEMS, INPUT_FLUID, ITEM_OUTPUT, FLUID_OUTPUT, ENERGY)
        .constructor();
}
