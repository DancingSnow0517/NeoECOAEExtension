package cn.dancingsnow.neoecoae.recipe;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Accessors(fluent = true, chain = true)
public class CoolingRecipeBuilder implements RecipeBuilder {

    private SizedFluidIngredient input = null;
    @Setter
    private FluidStack output = FluidStack.EMPTY;
    @Setter
    private int coolant = 0;

    protected final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();

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

    @Override
    public CoolingRecipeBuilder unlockedBy(String name, Criterion<?> criterion) {
        criteria.put(name, criterion);
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
    public void save(RecipeOutput recipeOutput) {
        throw new IllegalArgumentException("id must not be null");
    }

    @Override
    public void save(RecipeOutput recipeOutput, ResourceLocation id) {
        Objects.requireNonNull(input, "input must not be null");
        if (coolant <= 0) {
            throw new IllegalStateException("coolant must be greater than 0");
        }
        Advancement.Builder advancement = recipeOutput.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
            .rewards(AdvancementRewards.Builder.recipe(id))
            .requirements(AdvancementRequirements.Strategy.OR);
        criteria.forEach(advancement::addCriterion);
        CoolingRecipe recipe = new CoolingRecipe(input, output, coolant);
        recipeOutput.accept(id, recipe, advancement.build(id.withPrefix("recipe/")));
    }
}
