package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.platform.recipe.NERecipeInput;
import cn.dancingsnow.neoecoae.platform.recipe.RecipeJson;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

public record CoolingRecipe(SizedFluidIngredient input, FluidStack output, int coolant, int maxOverclock,
                            ResourceLocation id) implements Recipe<CoolingRecipe.Input> {
    public CoolingRecipe(SizedFluidIngredient input, FluidStack output, int coolant, int maxOverclock) {
        this(input, output, coolant, maxOverclock, new ResourceLocation("neoecoae", "runtime_cooling"));
    }

    public CoolingRecipe {
        if (input.ingredient().isEmpty()) throw new IllegalArgumentException("Coolant input cannot be empty");
        if (coolant <= 0 || maxOverclock < 0) throw new IllegalArgumentException("Invalid coolant or overclock value");
        output = output.copy();
    }

    public static CoolingRecipeBuilder builder() { return new CoolingRecipeBuilder(); }
    @Override public boolean matches(Input i, Level level) {
        return input.test(i.input()) && (i.output().isEmpty() || output.isFluidEqual(i.output()));
    }
    @Override public ItemStack assemble(Input input, RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NERecipeTypes.COOLING_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NERecipeTypes.COOLING.get(); }
    public int inputAmount() { return input.amount(); }
    public int outputAmount() { return output.getAmount(); }

    public static class Serializer implements RecipeSerializer<CoolingRecipe> {
        @Override public CoolingRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new CoolingRecipe(SizedFluidIngredient.fromJson(json.get("input")), RecipeJson.fluid(json.get("output")),
                GsonHelper.getAsInt(json, "coolant"), GsonHelper.getAsInt(json, "max_overclock", 0), id);
        }

        public static JsonObject toJson(CoolingRecipe recipe) {
            JsonObject json = new JsonObject();
            json.add("input", recipe.input().toJson());
            json.add("output", RecipeJson.fluid(recipe.output()));
            json.addProperty("coolant", recipe.coolant());
            json.addProperty("max_overclock", recipe.maxOverclock());
            return json;
        }

        @Override public CoolingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new CoolingRecipe(SizedFluidIngredient.read(buffer), buffer.readFluidStack(),
                buffer.readVarInt(), buffer.readVarInt(), id);
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, CoolingRecipe recipe) {
            recipe.input().write(buffer);
            buffer.writeFluidStack(recipe.output());
            buffer.writeVarInt(recipe.coolant());
            buffer.writeVarInt(recipe.maxOverclock());
        }
    }

    public record Input(FluidStack input, FluidStack output) implements NERecipeInput {
        @Override public ItemStack getItem(int index) { return ItemStack.EMPTY; }
        @Override public int size() { return 0; }
        @Override public boolean isEmpty() { return input.isEmpty(); }
    }
}
