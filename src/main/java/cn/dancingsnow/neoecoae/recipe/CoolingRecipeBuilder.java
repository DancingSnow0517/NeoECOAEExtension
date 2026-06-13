package cn.dancingsnow.neoecoae.recipe;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@Accessors(fluent = true, chain = true)
public class CoolingRecipeBuilder implements RecipeBuilder {

    @Nullable
    private final HolderGetter<Fluid> fluidLookup;
    private SizedFluidIngredient input = null;
    @Setter
    @Nullable
    private FluidStackTemplate output = null;
    @Setter
    private int coolant = 0;
    @Setter
    private int maxOverclock = 9;

    public CoolingRecipeBuilder() {
        this(null);
    }

    public CoolingRecipeBuilder(@Nullable HolderGetter<Fluid> fluidLookup) {
        this.fluidLookup = fluidLookup;
    }

    public CoolingRecipeBuilder input(SizedFluidIngredient ingredient) {
        this.input = ingredient;
        return this;
    }

    public CoolingRecipeBuilder input(TagKey<Fluid> tag, int amount) {
        if (fluidLookup == null) {
            throw new IllegalStateException("Fluid tag ingredients require a fluid HolderGetter");
        }
        return input(new SizedFluidIngredient(FluidIngredient.of(fluidLookup.getOrThrow(tag)), amount));
    }

    public CoolingRecipeBuilder input(FluidStack stack) {
        return input(new SizedFluidIngredient(FluidIngredient.of(stack), stack.getAmount()));
    }

    public CoolingRecipeBuilder input(Fluid fluid, int amount) {
        return input(new SizedFluidIngredient(FluidIngredient.of(HolderSet.direct(fluidHolder(fluid, "Fluid inputs"))), amount));
    }

    public CoolingRecipeBuilder output(Holder<Fluid> fluid, int amount) {
        this.output = new FluidStackTemplate(fluid, amount);
        return this;
    }

    public CoolingRecipeBuilder output(Fluid fluid, int amount) {
        this.output = new FluidStackTemplate(fluidHolder(fluid, "Fluid outputs"), amount);
        return this;
    }

    @Override
    public CoolingRecipeBuilder unlockedBy(String name, Criterion<?> criterion) {
        return this;
    }

    @Override
    public CoolingRecipeBuilder group(@Nullable String groupName) {
        return this;
    }

    @Override
    public ResourceKey<Recipe<?>> defaultId() {
        return ResourceKey.create(Registries.RECIPE, Identifier.parse("neoecoae:cooling"));
    }

    @Override
    public void save(RecipeOutput recipeOutput) {
        throw new IllegalArgumentException("id must not be null");
    }

    @Override
    public void save(RecipeOutput recipeOutput, ResourceKey<Recipe<?>> id) {
        Objects.requireNonNull(input, "input must not be null");
        if (coolant <= 0) {
            throw new IllegalStateException("coolant must be greater than 0");
        }
        if (maxOverclock < 0) {
            throw new IllegalStateException("maxOverclock must not be negative");
        }
        CoolingRecipe recipe = new CoolingRecipe(input, output, coolant, maxOverclock);
        recipeOutput.accept(id, recipe, null);
    }

    public void save(RecipeOutput recipeOutput, Identifier id) {
        save(recipeOutput, ResourceKey.create(Registries.RECIPE, id));
    }

    private Holder<Fluid> fluidHolder(Fluid fluid, String role) {
        if (fluidLookup == null) {
            throw new IllegalStateException(role + " require a fluid HolderGetter");
        }
        Identifier fluidId = Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(fluid), "fluid must be registered");
        return fluidLookup.getOrThrow(ResourceKey.create(Registries.FLUID, fluidId));
    }
}
