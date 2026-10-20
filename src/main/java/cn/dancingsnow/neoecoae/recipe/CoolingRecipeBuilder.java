package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.platform.recipe.NEFinishedRecipe;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.core.Holder;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

public class CoolingRecipeBuilder implements RecipeBuilder {

    private SizedFluidIngredient input = null;
    private FluidStack output = FluidStack.EMPTY;
    private int coolant = 0;
    private int maxOverclock = 9;

    public CoolingRecipeBuilder output(FluidStack output) { this.output = output.copy(); return this; }
    public CoolingRecipeBuilder coolant(int coolant) { this.coolant = coolant; return this; }
    public CoolingRecipeBuilder maxOverclock(int maxOverclock) { this.maxOverclock = maxOverclock; return this; }

    public CoolingRecipeBuilder input(SizedFluidIngredient ingredient) {
        this.input = ingredient;
        return this;
    }

    public CoolingRecipeBuilder input(TagKey<Fluid> tag, int amount) {
        return input(SizedFluidIngredient.of(tag, amount));
    }

    public CoolingRecipeBuilder input(FluidStack stack) {
        return input(SizedFluidIngredient.of(stack));
    }

    public CoolingRecipeBuilder input(Fluid fluid, int amount) {
        return input(new FluidStack(fluid, amount));
    }

    public CoolingRecipeBuilder output(Holder<Fluid> fluid, int amount) {
        return output(fluid.value(), amount);
    }

    public CoolingRecipeBuilder output(Fluid fluid, int amount) {
        return output(new FluidStack(fluid, amount));
    }

    @Override
    public CoolingRecipeBuilder unlockedBy(String name, CriterionTriggerInstance criterion) {
        return this;
    }

    @Override
    public CoolingRecipeBuilder group(@Nullable String groupName) {
        return this;
    }

    @Override
    public Item getResult() {
        return Items.AIR;
    }

    @Override
    public void save(Consumer<FinishedRecipe> recipeOutput) {
        throw new IllegalArgumentException("id must not be null");
    }

    @Override
    public void save(Consumer<FinishedRecipe> recipeOutput, ResourceLocation id) {
        Objects.requireNonNull(input, "input must not be null");
        if (coolant <= 0) {
            throw new IllegalStateException("coolant must be greater than 0");
        }
        if (maxOverclock < 0) {
            throw new IllegalStateException("maxOverclock must not be negative");
        }
        CoolingRecipe recipe = new CoolingRecipe(input, output, coolant, maxOverclock, id);
        recipeOutput.accept(new NEFinishedRecipe(id, NERecipeTypes.COOLING_SERIALIZER.get(), CoolingRecipe.Serializer.toJson(recipe)));
    }
}
