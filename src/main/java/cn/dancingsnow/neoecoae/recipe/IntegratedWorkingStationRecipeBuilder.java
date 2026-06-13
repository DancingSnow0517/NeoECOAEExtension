package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Accessors(fluent = true, chain = true)
public class IntegratedWorkingStationRecipeBuilder implements RecipeBuilder {
    List<SizedIngredient> inputItems = new ArrayList<>();
    @Nullable
    SizedFluidIngredient inputFluid = null;
    @Nullable
    private final HolderGetter<Item> itemLookup;
    @Nullable
    private final HolderGetter<Fluid> fluidLookup;
    @Nullable
    ItemStackTemplate itemOutput = null;
    @Nullable
    FluidStackTemplate fluidOutput = null;
    @Setter
    int energy = 1000;

    public IntegratedWorkingStationRecipeBuilder() {
        this(null, null);
    }

    public IntegratedWorkingStationRecipeBuilder(@Nullable HolderGetter<Item> itemLookup, @Nullable HolderGetter<Fluid> fluidLookup) {
        this.itemLookup = itemLookup;
        this.fluidLookup = fluidLookup;
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
        if (itemLookup == null) {
            throw new IllegalStateException("Item tag ingredients require an item HolderGetter");
        }
        return require(new SizedIngredient(Ingredient.of(itemLookup.getOrThrow(tag)), count));
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
        if (fluidLookup == null) {
            throw new IllegalStateException("Fluid tag ingredients require a fluid HolderGetter");
        }
        return requireFluid(new SizedFluidIngredient(FluidIngredient.of(fluidLookup.getOrThrow(tag)), count));
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
        return requireFluid(new SizedFluidIngredient(FluidIngredient.of(stack), stack.getAmount()));
    }

    public IntegratedWorkingStationRecipeBuilder itemOutput(ItemStack itemStack) {
        this.itemOutput = ItemStackTemplate.fromNonEmptyStack(itemStack);
        return this;
    }

    public IntegratedWorkingStationRecipeBuilder itemOutput(ItemLike item, int count) {
        if (itemLookup == null) {
            throw new IllegalStateException("Item outputs require an item HolderGetter");
        }
        Identifier itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(item.asItem()), "item must be registered");
        Holder<Item> holder = itemLookup.getOrThrow(ResourceKey.create(Registries.ITEM, itemId));
        this.itemOutput = new ItemStackTemplate(holder, count);
        return this;
    }

    public IntegratedWorkingStationRecipeBuilder itemOutput(ItemLike item) {
        return itemOutput(item, 1);
    }

    public IntegratedWorkingStationRecipeBuilder fluidOutput(FluidStack fluidStack) {
        this.fluidOutput = FluidStackTemplate.fromNonEmptyStack(fluidStack);
        return this;
    }

    public IntegratedWorkingStationRecipeBuilder fluidOutput(Fluid fluid, int amount) {
        if (fluidLookup == null) {
            throw new IllegalStateException("Fluid outputs require a fluid HolderGetter");
        }
        Identifier fluidId = Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(fluid), "fluid must be registered");
        Holder<Fluid> holder = fluidLookup.getOrThrow(ResourceKey.create(Registries.FLUID, fluidId));
        this.fluidOutput = new FluidStackTemplate(holder, amount);
        return this;
    }

    public IntegratedWorkingStationRecipeBuilder fluidOutput(Fluid fluid) {
        return fluidOutput(fluid, 1000);
    }

    @Override
    public RecipeBuilder unlockedBy(String name, Criterion<?> criterion) {
        return this;
    }

    @Override
    public RecipeBuilder group(@Nullable String groupName) {
        return this;
    }

    @Override
    public ResourceKey<Recipe<?>> defaultId() {
        return ResourceKey.create(Registries.RECIPE, NeoECOAE.id(itemOutputPath()));
    }

    @Override
    public void save(RecipeOutput recipeOutput) {
        save(recipeOutput, NeoECOAE.id(itemOutputPath()));
    }

    @Override
    public void save(RecipeOutput recipeOutput, ResourceKey<Recipe<?>> id) {
        // check
        if (itemOutput == null && fluidOutput == null) {
            throw new IllegalStateException("Recipe must have at least one output");
        }
        if (inputItems.isEmpty()) {
            throw new IllegalStateException("Recipe must have at least one input");
        }

        IntegratedWorkingStationRecipe recipe = new IntegratedWorkingStationRecipe(inputItems, Optional.ofNullable(inputFluid), itemOutput, fluidOutput, energy);
        recipeOutput.accept(id, recipe, null);
    }

    public void save(RecipeOutput recipeOutput, Identifier id) {
        save(recipeOutput, ResourceKey.create(Registries.RECIPE, id));
    }

    private String itemOutputPath() {
        if (itemOutput == null) {
            throw new IllegalStateException("Recipe ID requires an item output");
        }
        return BuiltInRegistries.ITEM.getKey(itemOutput.item().value()).getPath();
    }
}
