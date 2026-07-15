package cn.dancingsnow.neoecoae.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

public record SizedFluidIngredient(FluidIngredient ingredient, int amount) {
    /** Create a SizedFluidIngredient from a Fluid. */
    public static SizedFluidIngredient of(Fluid fluid, int amount) {
        return new SizedFluidIngredient(new FluidIngredient(fluid, null), amount);
    }

    /** Create a SizedFluidIngredient from a FluidStack. */
    public static SizedFluidIngredient of(FluidStack stack) {
        return of(stack.getFluid(), stack.getAmount());
    }

    /** Create a SizedFluidIngredient from a tag. */
    public static SizedFluidIngredient of(TagKey<Fluid> tag, int amount) {
        return new SizedFluidIngredient(new FluidIngredient(null, tag), amount);
    }

    public boolean test(FluidStack stack) {
        return ingredient.test(stack) && (ingredient.isEmpty() || stack.getAmount() >= amount);
    }

    public static SizedFluidIngredient fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return new SizedFluidIngredient(FluidIngredient.empty(), 0);
        }
        if (!json.isJsonObject()) {
            throw new JsonParseException("Sized fluid ingredient must be an object");
        }
        JsonObject object = json.getAsJsonObject();
        long amountValue = object.has("amount") ? object.get("amount").getAsLong() : 1L;
        if (amountValue <= 0 || amountValue > Integer.MAX_VALUE) {
            throw new JsonParseException("Sized fluid ingredient amount must be positive");
        }
        JsonElement ingredientJson = object.has("ingredient") ? object.get("ingredient") : object;
        FluidIngredient ingredient = FluidIngredient.fromJson(ingredientJson);
        if (ingredient.isEmpty()) {
            throw new JsonParseException("Sized fluid ingredient must not be empty");
        }
        return new SizedFluidIngredient(ingredient, (int) amountValue);
    }

    public JsonElement toJson() {
        JsonObject object = ingredient.toJson().getAsJsonObject();
        object.addProperty("amount", amount);
        return object;
    }

    public static SizedFluidIngredient fromNetwork(FriendlyByteBuf buffer) {
        return new SizedFluidIngredient(FluidIngredient.fromNetwork(buffer), buffer.readVarInt());
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        ingredient.toNetwork(buffer);
        buffer.writeVarInt(amount);
    }

    /** Returns FluidStack array for EMI display. */
    public FluidStack[] getFluids() {
        return ingredient.getFluids();
    }
}
