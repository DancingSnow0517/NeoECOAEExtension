package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.recipe.ingredient.SizedFluidIngredient;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.core.Holder;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

@Accessors(fluent = true, chain = true)
public class CoolingRecipeBuilder implements RecipeBuilder {

    private SizedFluidIngredient input = null;

    @Setter
    private FluidStack output = FluidStack.EMPTY;

    @Setter
    private int coolant = 0;

    @Setter
    private int maxOverclock = 9;

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
        if (input.ingredient().isEmpty() || input.amount() <= 0) {
            throw new IllegalStateException("input must contain a fluid or fluid tag with a positive amount");
        }
        if (!output.isEmpty() && output.getAmount() <= 0) {
            throw new IllegalStateException("output amount must be positive");
        }
        if (coolant <= 0) {
            throw new IllegalStateException("coolant must be greater than 0");
        }
        if (maxOverclock < 0) {
            throw new IllegalStateException("maxOverclock must not be negative");
        }
        recipeOutput.accept(new Result(id, input, output, coolant, maxOverclock));
    }

    private record Result(
            ResourceLocation id, SizedFluidIngredient input, FluidStack output, int coolant, int maxOverclock)
            implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            json.add("input", input.toJson());
            if (!output.isEmpty()) {
                ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(output.getFluid());
                if (fluidId == null) {
                    throw new IllegalStateException("Cannot serialize unregistered fluid " + output.getFluid());
                }
                JsonObject outputJson = new JsonObject();
                outputJson.addProperty("fluid", fluidId.toString());
                outputJson.addProperty("amount", output.getAmount());
                json.add("output", outputJson);
            }
            json.addProperty("coolant", coolant);
            json.addProperty("max_overclock", maxOverclock);
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return cn.dancingsnow.neoecoae.all.NERecipeTypes.COOLING_SERIALIZER.get();
        }

        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }
}
