package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.recipe.ingredient.FluidIngredient;
import cn.dancingsnow.neoecoae.recipe.ingredient.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.recipe.ingredient.SizedIngredient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public record IntegratedWorkingStationRecipe(
        ResourceLocation id,
        List<SizedIngredient> inputItems,
        SizedFluidIngredient inputFluid,
        ItemStack itemOutput,
        FluidStack fluidOutput,
        int energy)
        implements Recipe<IntegratedWorkingStationRecipe.Input> {
    public static final int MAX_INPUT_ITEMS = 9;

    public static IntegratedWorkingStationRecipeBuilder builder() {
        return new IntegratedWorkingStationRecipeBuilder();
    }

    @Override
    public boolean matches(Input recipeInput, Level level) {
        List<ItemStack> provided = recipeInput.inputs();
        if (ItemIngredientConsumptionPlanner.createPlan(provided, inputItems) == null) {
            return false;
        }

        FluidStack providedFluid = recipeInput.fluid();
        if (inputFluid.ingredient().isEmpty()) {
            return true;
        }
        if (providedFluid == null || providedFluid.isEmpty()) {
            return false;
        }
        if (!inputFluid.test(providedFluid)) {
            return false;
        }
        return providedFluid.getAmount() >= inputFluid.amount();
    }

    @Override
    public ItemStack assemble(Input inv, RegistryAccess registries) {
        return getResultItem(registries).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return itemOutput;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NERecipeTypes.INTEGRATED_WORKING_STATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NERecipeTypes.INTEGRATED_WORKING_STATION.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public boolean hasItemOutput() {
        return !itemOutput.isEmpty();
    }

    public boolean hasFluidOutput() {
        return !fluidOutput.isEmpty();
    }

    public static class Serializer implements RecipeSerializer<IntegratedWorkingStationRecipe> {
        @Override
        public IntegratedWorkingStationRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<SizedIngredient> inputItems = new ArrayList<>();
            if (json.has("inputItems")) {
                if (!json.get("inputItems").isJsonArray()) {
                    throw new JsonParseException("Recipe " + id + " inputItems must be an array");
                }
                JsonArray array = json.getAsJsonArray("inputItems");
                for (int i = 0; i < array.size(); i++) {
                    try {
                        inputItems.add(SizedIngredient.fromJson(array.get(i)));
                    } catch (JsonParseException e) {
                        throw new JsonParseException("Recipe " + id + " inputItems[" + i + "] " + e.getMessage(), e);
                    }
                }
            }

            SizedFluidIngredient inputFluid;
            try {
                inputFluid = json.has("inputFluid")
                        ? SizedFluidIngredient.fromJson(json.get("inputFluid"))
                        : new SizedFluidIngredient(FluidIngredient.empty(), 0);
            } catch (JsonParseException e) {
                throw new JsonParseException("Recipe " + id + " inputFluid " + e.getMessage(), e);
            }

            if (json.has("itemOutput") && !json.get("itemOutput").isJsonObject()) {
                throw new JsonParseException("Recipe " + id + " itemOutput must be an object");
            }
            if (json.has("fluidOutput") && !json.get("fluidOutput").isJsonObject()) {
                throw new JsonParseException("Recipe " + id + " fluidOutput must be an object");
            }
            ItemStack itemOutput = json.has("itemOutput")
                    ? RecipeOutputJson.readItemStack(id, "itemOutput", json.getAsJsonObject("itemOutput"))
                    : ItemStack.EMPTY;
            FluidStack fluidOutput = json.has("fluidOutput")
                    ? RecipeOutputJson.readFluidStack(id, "fluidOutput", json.getAsJsonObject("fluidOutput"))
                    : FluidStack.EMPTY;
            long energyValue = json.has("energy") ? json.get("energy").getAsLong() : 1000L;
            if (energyValue <= 0 || energyValue > Integer.MAX_VALUE) {
                throw new JsonParseException("Recipe " + id + " energy must be positive");
            }
            int energy = (int) energyValue;
            validate(id, inputItems, inputFluid, itemOutput, fluidOutput, energy);
            return new IntegratedWorkingStationRecipe(id, inputItems, inputFluid, itemOutput, fluidOutput, energy);
        }

        private static void validate(
                ResourceLocation id,
                List<SizedIngredient> inputItems,
                SizedFluidIngredient inputFluid,
                ItemStack itemOutput,
                FluidStack fluidOutput,
                int energy) {
            if (inputItems.size() > MAX_INPUT_ITEMS) {
                throw new JsonParseException(
                        "Recipe " + id + " has " + inputItems.size() + " item inputs; maximum is " + MAX_INPUT_ITEMS);
            }
            if (inputItems.isEmpty() && inputFluid.ingredient().isEmpty()) {
                throw new JsonParseException("Recipe " + id + " must have at least one input");
            }
            for (int i = 0; i < inputItems.size(); i++) {
                SizedIngredient input = inputItems.get(i);
                if (input.ingredient() == Ingredient.EMPTY || input.count() <= 0) {
                    throw new JsonParseException(
                            "Recipe " + id + " inputItems[" + i + "] must be non-empty with a positive count");
                }
            }
            if (itemOutput.isEmpty() && fluidOutput.isEmpty()) {
                throw new JsonParseException("Recipe " + id + " must have at least one output");
            }
            if (energy <= 0) {
                throw new JsonParseException("Recipe " + id + " energy must be positive");
            }
        }

        @Override
        public IntegratedWorkingStationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_INPUT_ITEMS) {
                throw new IllegalArgumentException(
                        "Recipe " + id + " item input count must be between 0 and " + MAX_INPUT_ITEMS);
            }
            List<SizedIngredient> inputItems = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                inputItems.add(SizedIngredient.fromNetwork(buffer));
            }
            return new IntegratedWorkingStationRecipe(
                    id,
                    inputItems,
                    SizedFluidIngredient.fromNetwork(buffer),
                    buffer.readItem(),
                    FluidStack.readFromPacket(buffer),
                    buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, IntegratedWorkingStationRecipe recipe) {
            buffer.writeVarInt(recipe.inputItems.size());
            for (SizedIngredient inputItem : recipe.inputItems) {
                inputItem.toNetwork(buffer);
            }
            recipe.inputFluid.toNetwork(buffer);
            buffer.writeItem(recipe.itemOutput);
            recipe.fluidOutput.writeToPacket(buffer);
            buffer.writeVarInt(recipe.energy);
        }
    }

    public static class Input extends SimpleContainer {
        private final List<ItemStack> inputs;

        @Nullable private final FluidStack fluid;

        public Input(List<ItemStack> inputs, @Nullable FluidStack fluid) {
            super(inputs.toArray(ItemStack[]::new));
            this.inputs = inputs;
            this.fluid = fluid;
        }

        public List<ItemStack> inputs() {
            return inputs;
        }

        @Nullable public FluidStack fluid() {
            return fluid;
        }
    }
}
