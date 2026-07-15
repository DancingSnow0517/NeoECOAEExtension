package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.recipe.ingredient.SizedFluidIngredient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

public record CoolingRecipe(
        ResourceLocation id, SizedFluidIngredient input, FluidStack output, int coolant, int maxOverclock)
        implements Recipe<CoolingRecipe.Input> {
    public static CoolingRecipeBuilder builder() {
        return new CoolingRecipeBuilder();
    }

    @Override
    public boolean matches(Input i, Level l) {
        return input.test(i.input) && (output.isEmpty() || i.output.isEmpty() || output.isFluidEqual(i.output));
    }

    @Override
    public ItemStack assemble(Input input, RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NERecipeTypes.COOLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NERecipeTypes.COOLING.get();
    }

    public int inputAmount() {
        return input.amount();
    }

    public int outputAmount() {
        return output.getAmount();
    }

    public static class Serializer implements RecipeSerializer<CoolingRecipe> {
        @Override
        public CoolingRecipe fromJson(ResourceLocation id, JsonObject json) {
            SizedFluidIngredient input;
            try {
                input = SizedFluidIngredient.fromJson(json.get("input"));
            } catch (JsonParseException e) {
                throw new JsonParseException("Recipe " + id + " input " + e.getMessage(), e);
            }
            if (json.has("output") && !json.get("output").isJsonObject()) {
                throw new JsonParseException("Recipe " + id + " output must be an object");
            }
            FluidStack output = json.has("output")
                    ? RecipeOutputJson.readFluidStack(id, "output", json.getAsJsonObject("output"))
                    : FluidStack.EMPTY;
            if (!json.has("coolant")) {
                throw new JsonParseException("Recipe " + id + " must contain 'coolant'");
            }
            long coolantValue = json.get("coolant").getAsLong();
            long maxOverclockValue =
                    json.has("max_overclock") ? json.get("max_overclock").getAsLong() : 0L;
            if (input.ingredient().isEmpty() || input.amount() <= 0) {
                throw new JsonParseException("Recipe " + id + " input must be non-empty with a positive amount");
            }
            if (coolantValue <= 0 || coolantValue > Integer.MAX_VALUE) {
                throw new JsonParseException("Recipe " + id + " coolant must be positive");
            }
            if (maxOverclockValue < 0 || maxOverclockValue > Integer.MAX_VALUE) {
                throw new JsonParseException("Recipe " + id + " max_overclock must not be negative");
            }
            return new CoolingRecipe(id, input, output, (int) coolantValue, (int) maxOverclockValue);
        }

        @Override
        public CoolingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new CoolingRecipe(
                    id,
                    SizedFluidIngredient.fromNetwork(buffer),
                    FluidStack.readFromPacket(buffer),
                    buffer.readVarInt(),
                    buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CoolingRecipe recipe) {
            recipe.input.toNetwork(buffer);
            recipe.output.writeToPacket(buffer);
            buffer.writeVarInt(recipe.coolant);
            buffer.writeVarInt(recipe.maxOverclock);
        }
    }

    public static class Input extends SimpleContainer {
        private final FluidStack input;
        private final FluidStack output;

        public Input(FluidStack input, FluidStack output) {
            super(0);
            this.input = input;
            this.output = output;
        }
    }
}
