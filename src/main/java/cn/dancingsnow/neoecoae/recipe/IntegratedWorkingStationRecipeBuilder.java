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
    int processingTicks = 100;

    public IntegratedWorkingStationRecipeBuilder processingTicks(int ticks) {
        if (ticks <= 0) throw new IllegalArgumentException("Processing time must be positive");
        this.processingTicks = ticks;
        return this;
    }

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
        HolderGetter<Item> lookup = itemLookup != null ? itemLookup : BuiltInRegistries.ITEM;
        // Unbound tags (datagen without a tag-aware lookup) still serialize as "#tag".
        var set = lookup.get(tag).<net.minecraft.core.HolderSet<Item>>map(h -> h)
            .orElseGet(() -> net.minecraft.core.HolderSet.emptyNamed(BuiltInRegistries.ITEM, tag));
        return require(new SizedIngredient(Ingredient.of(set), count));
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
        HolderGetter<Fluid> lookup = fluidLookup != null ? fluidLookup : BuiltInRegistries.FLUID;
        var set = lookup.get(tag).<net.minecraft.core.HolderSet<Fluid>>map(h -> h)
            .orElseGet(() -> net.minecraft.core.HolderSet.emptyNamed(BuiltInRegistries.FLUID, tag));
        return requireFluid(new SizedFluidIngredient(FluidIngredient.of(set), count));
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
            this.itemOutput = new ItemStackTemplate(item.asItem(), count);
            return this;
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
            this.fluidOutput = new FluidStackTemplate(fluid, amount);
            return this;
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
        save(recipeOutput, id, false);
    }

    public void save(RecipeOutput recipeOutput, Identifier id) {
        save(recipeOutput, ResourceKey.create(Registries.RECIPE, id));
    }

    public void saveLarge(RecipeOutput recipeOutput) {
        save(recipeOutput, ResourceKey.create(Registries.RECIPE, NeoECOAE.id(itemOutputPath())), true);
    }

    private void save(RecipeOutput recipeOutput, ResourceKey<Recipe<?>> id, boolean large) {
        // check
        if (itemOutput == null && fluidOutput == null) {
            throw new IllegalStateException("Recipe must have at least one output");
        }
        if (inputItems.isEmpty() && inputFluid == null) {
            throw new IllegalStateException("Recipe must have at least one input");
        }

        IntegratedWorkingStationRecipe recipe = new IntegratedWorkingStationRecipe(
            inputItems, Optional.ofNullable(inputFluid), itemOutput, fluidOutput, energy, processingTicks);
        recipeOutput.accept(id, large ? new LargeIntegratedWorkingStationRecipe(recipe) : recipe, null);
    }

    private String itemOutputPath() {
        if (itemOutput == null) {
            throw new IllegalStateException("Recipe ID requires an item output");
        }
        return BuiltInRegistries.ITEM.getKey(itemOutput.item().value()).getPath();
    }
}
