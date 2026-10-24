package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.platform.recipe.NEFinishedRecipe;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import net.minecraftforge.fluids.FluidStack;
import cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class IntegratedWorkingStationRecipeBuilder implements RecipeBuilder {
    List<SizedIngredient> inputItems = new ArrayList<>();
    SizedFluidIngredient inputFluid = new SizedFluidIngredient(FluidIngredient.empty(), 1);
    ItemStack itemOutput = ItemStack.EMPTY;
    FluidStack fluidOutput = FluidStack.EMPTY;
    int energy = 1000;
    int processingTicks = 100;

    public IntegratedWorkingStationRecipeBuilder processingTicks(int ticks) {
        if (ticks <= 0) throw new IllegalArgumentException("Processing time must be positive");
        this.processingTicks = ticks;
        return this;
    }

    public IntegratedWorkingStationRecipeBuilder energy(int energy) {
        this.energy = energy;
        return this;
    }

    public IntegratedWorkingStationRecipeBuilder require(SizedIngredient ingredient) {
        inputItems.add(ingredient);
        return this;
    }

    public IntegratedWorkingStationRecipeBuilder require(ItemLike itemLike, int count) {
        return require(SizedIngredient.of(itemLike, count));
    }

    public IntegratedWorkingStationRecipeBuilder require(ItemLike itemLike) {
        return require(itemLike, 1);
    }

    public IntegratedWorkingStationRecipeBuilder require(TagKey<Item> tag, int count) {
        return require(SizedIngredient.of(tag, count));
    }

    public IntegratedWorkingStationRecipeBuilder require(TagKey<Item> tag) {
        return require(tag, 1);
    }

    public IntegratedWorkingStationRecipeBuilder require(ItemStack itemStack) {
        return require(SizedIngredient.of(itemStack.getItem(), itemStack.getCount()));
    }

    public IntegratedWorkingStationRecipeBuilder requireFluid(SizedFluidIngredient ingredient) {
        inputFluid = ingredient;
        return this;
    }

    public IntegratedWorkingStationRecipeBuilder requireFluid(TagKey<Fluid> tag, int count) {

        return requireFluid(SizedFluidIngredient.of(tag, count));
    }

    public IntegratedWorkingStationRecipeBuilder requireFluid(TagKey<Fluid> tag) {
        return requireFluid(tag, 1);
    }

    public IntegratedWorkingStationRecipeBuilder requireFluid(Fluid fluid, int count) {
        return requireFluid(SizedFluidIngredient.of(fluid, count));
    }

    public IntegratedWorkingStationRecipeBuilder requireFluid(Fluid fluid) {
        return requireFluid(fluid, 1);
    }

    public IntegratedWorkingStationRecipeBuilder requireFluid(FluidStack stack) {
        return requireFluid(SizedFluidIngredient.of(stack));
    }

    public IntegratedWorkingStationRecipeBuilder itemOutput(ItemStack itemStack) {
        this.itemOutput = itemStack;
        return this;
    }

    public IntegratedWorkingStationRecipeBuilder itemOutput(ItemLike item, int count) {
        return itemOutput(new ItemStack(item, count));
    }

    public IntegratedWorkingStationRecipeBuilder itemOutput(ItemLike item) {
        return itemOutput(item, 1);
    }

    public IntegratedWorkingStationRecipeBuilder fluidOutput(FluidStack fluidStack) {
        this.fluidOutput = fluidStack;
        return this;
    }

    public IntegratedWorkingStationRecipeBuilder fluidOutput(Fluid fluid, int amount) {
        return fluidOutput(new FluidStack(fluid, amount));
    }

    public IntegratedWorkingStationRecipeBuilder fluidOutput(Fluid fluid) {
        return fluidOutput(fluid, 1000);
    }

    @Override
    public RecipeBuilder unlockedBy(String name, CriterionTriggerInstance criterion) {
        return this;
    }

    @Override
    public RecipeBuilder group(@Nullable String groupName) {
        return this;
    }

    @Override
    public Item getResult() {
        return itemOutput.getItem();
    }

    @Override
    public void save(Consumer<FinishedRecipe> recipeOutput) {
        save(recipeOutput, new ResourceLocation("neoecoae", BuiltInRegistries.ITEM.getKey(itemOutput.getItem()).getPath()));
    }

    @Override
    public void save(Consumer<FinishedRecipe> recipeOutput, ResourceLocation id) {
        saveWithSerializer(recipeOutput, id, NERecipeTypes.INTEGRATED_WORKING_STATION_SERIALIZER.get());
    }

    public void saveWithSerializer(Consumer<FinishedRecipe> recipeOutput, ResourceLocation id, RecipeSerializer<?> serializer) {
        // check
        if (itemOutput.isEmpty() && fluidOutput.isEmpty()) {
            throw new IllegalStateException("Recipe must have at least one output");
        }
        if (inputItems.isEmpty() && inputFluid.ingredient().isEmpty()) {
            throw new IllegalStateException("Recipe must have at least one input");
        }

        IntegratedWorkingStationRecipe recipe = new IntegratedWorkingStationRecipe(inputItems, inputFluid, itemOutput, fluidOutput, energy, id, processingTicks);
        recipeOutput.accept(new NEFinishedRecipe(id, serializer, IntegratedWorkingStationRecipe.Serializer.toJson(recipe)));
    }
}
