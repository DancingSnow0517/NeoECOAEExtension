package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import cn.dancingsnow.neoecoae.compat.crafting.SizedFluidIngredient;
import net.minecraftforge.registries.ForgeRegistries;

public record CoolingRecipe(
        ResourceLocation id,
        SizedFluidIngredient input,
        FluidStack output,
        int coolant,
        int maxOverclock) implements Recipe<CoolingRecipe.Input> {

    @Override
    public boolean matches(Input i, Level l) {
        return input.test(i.input) && (i.output.isEmpty() || output.isFluidEqual(i.output));
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
            FluidStack output = json.has("output")
                    ? readFluidStack(id, json.getAsJsonObject("output"))
                    : FluidStack.EMPTY;
            int coolant = json.get("coolant").getAsInt();
            int maxOverclock = json.has("max_overclock") ? json.get("max_overclock").getAsInt() : 0;
            return new CoolingRecipe(id, input, output, coolant, maxOverclock);
        }

        private static FluidStack readFluidStack(ResourceLocation recipeId, JsonObject object) {
            if (object.size() == 0) {
                return FluidStack.EMPTY;
            }
            if (object.has("tag")) {
                throw new JsonParseException("Recipe " + recipeId + " output cannot use a fluid tag");
            }
            String field = object.has("fluid") ? "fluid" : object.has("id") ? "id" : null;
            if (field == null) {
                throw new JsonParseException("Recipe " + recipeId + " output must contain 'fluid' or 'id'");
            }
            ResourceLocation fluidId = new ResourceLocation(object.get(field).getAsString());
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
            if (fluid == null || fluid == Fluids.EMPTY) {
                throw new JsonParseException("Recipe " + recipeId + " has unknown fluid output '" + fluidId + "'");
            }
            int amount = object.has("amount") ? object.get("amount").getAsInt() : 1000;
            return new FluidStack(fluid, amount);
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
