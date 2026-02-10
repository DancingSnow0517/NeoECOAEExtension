package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Accessors(fluent = true, chain = true)
public class IntegratedWorkingStationRecipeBuilder implements RecipeBuilder {
    List<SizedIngredient> inputItems = new ArrayList<>();
    SizedFluidIngredient inputFluid = new SizedFluidIngredient(FluidIngredient.empty(), 1);
    ItemStack itemOutput = ItemStack.EMPTY;
    FluidStack fluidOutput = FluidStack.EMPTY;
    @Setter
    int energy = 1000;

    protected final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();

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
    public RecipeBuilder unlockedBy(String name, Criterion<?> criterion) {
        criteria.put(name, criterion);
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
    public void save(RecipeOutput recipeOutput) {
        save(recipeOutput, NeoECOAE.id(BuiltInRegistries.ITEM.getKey(itemOutput.getItem()).getPath()));
    }

    @Override
    public void save(RecipeOutput recipeOutput, ResourceLocation id) {
        // check
        if (itemOutput.isEmpty() && fluidOutput.isEmpty()) {
            throw new IllegalStateException("Recipe must have at least one output");
        }
        if (inputItems.isEmpty() && inputFluid.ingredient().isEmpty()) {
            throw new IllegalStateException("Recipe must have at least one input");
        }

        Advancement.Builder advancement = recipeOutput.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
            .rewards(AdvancementRewards.Builder.recipe(id))
            .requirements(AdvancementRequirements.Strategy.OR);
        criteria.forEach(advancement::addCriterion);

        IntegratedWorkingStationRecipe recipe = new IntegratedWorkingStationRecipe(inputItems, inputFluid, itemOutput, fluidOutput, energy);
        recipeOutput.accept(id, recipe, advancement.build(id.withPrefix("recipe/")));
    }
}
