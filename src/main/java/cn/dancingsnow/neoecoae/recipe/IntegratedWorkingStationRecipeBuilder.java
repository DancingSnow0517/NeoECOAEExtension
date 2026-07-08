package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.crafting.FluidIngredient;
import cn.dancingsnow.neoecoae.compat.crafting.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.compat.crafting.SizedIngredient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

@Accessors(fluent = true, chain = true)
public class IntegratedWorkingStationRecipeBuilder implements RecipeBuilder {
    List<SizedIngredient> inputItems = new ArrayList<>();
    SizedFluidIngredient inputFluid = new SizedFluidIngredient(FluidIngredient.empty(), 1);
    ItemStack itemOutput = ItemStack.EMPTY;
    FluidStack fluidOutput = FluidStack.EMPTY;

    @Setter
    int energy = 1000;

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
        save(
                recipeOutput,
                NeoECOAE.id(BuiltInRegistries.ITEM.getKey(itemOutput.getItem()).getPath()));
    }

    @Override
    public void save(Consumer<FinishedRecipe> recipeOutput, ResourceLocation id) {
        // check
        if (itemOutput.isEmpty() && fluidOutput.isEmpty()) {
            throw new IllegalStateException("Recipe must have at least one output");
        }
        if (inputItems.isEmpty() && inputFluid.ingredient().isEmpty()) {
            throw new IllegalStateException("Recipe must have at least one input");
        }

        recipeOutput.accept(
                new Result(id, List.copyOf(inputItems), inputFluid, itemOutput.copy(), fluidOutput.copy(), energy));
    }

    private record Result(
            ResourceLocation id,
            List<SizedIngredient> inputItems,
            SizedFluidIngredient inputFluid,
            ItemStack itemOutput,
            FluidStack fluidOutput,
            int energy)
            implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("energy", energy);

            JsonArray inputItemsJson = new JsonArray();
            for (SizedIngredient inputItem : inputItems) {
                inputItemsJson.add(serializeSizedIngredient(inputItem));
            }
            json.add("inputItems", inputItemsJson);

            if (!inputFluid.ingredient().isEmpty()) {
                json.add("inputFluid", inputFluid.toJson());
            }
            if (!itemOutput.isEmpty()) {
                JsonObject output = new JsonObject();
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemOutput.getItem());
                if (itemId == null) {
                    throw new IllegalStateException("Cannot serialize unregistered item " + itemOutput.getItem());
                }
                output.addProperty("item", itemId.toString());
                output.addProperty("count", itemOutput.getCount());
                json.add("itemOutput", output);
            }
            if (!fluidOutput.isEmpty()) {
                JsonObject output = new JsonObject();
                ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluidOutput.getFluid());
                if (fluidId == null) {
                    throw new IllegalStateException("Cannot serialize unregistered fluid " + fluidOutput.getFluid());
                }
                output.addProperty("fluid", fluidId.toString());
                output.addProperty("amount", fluidOutput.getAmount());
                json.add("fluidOutput", output);
            }
        }

        private static JsonElement serializeSizedIngredient(SizedIngredient ingredient) {
            JsonObject json = ingredient.ingredient().toJson().getAsJsonObject();
            json.addProperty("count", ingredient.count());
            return json;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return cn.dancingsnow.neoecoae.all.NERecipeTypes.INTEGRATED_WORKING_STATION_SERIALIZER.get();
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
