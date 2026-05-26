package cn.dancingsnow.neoecoae.compat.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fluids.FluidStack;

public record SizedFluidIngredient(FluidIngredient ingredient, int amount) {
    public boolean test(FluidStack stack) {
        return ingredient.test(stack) && (ingredient.isEmpty() || stack.getAmount() >= amount);
    }

    public static SizedFluidIngredient fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return new SizedFluidIngredient(FluidIngredient.empty(), 0);
        }
        JsonObject object = json.getAsJsonObject();
        int amount = object.has("amount") ? object.get("amount").getAsInt() : 1;
        JsonElement ingredientJson = object.has("ingredient") ? object.get("ingredient") : object;
        return new SizedFluidIngredient(FluidIngredient.fromJson(ingredientJson), amount);
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
}
