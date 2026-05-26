package cn.dancingsnow.neoecoae.compat.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record SizedIngredient(Ingredient ingredient, int count) {
    public static SizedIngredient fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return new SizedIngredient(Ingredient.EMPTY, 0);
        }
        int count = 1;
        JsonElement ingredientJson = json;
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("count")) {
                count = object.get("count").getAsInt();
            }
            if (object.has("ingredient")) {
                ingredientJson = object.get("ingredient");
            }
        }
        return new SizedIngredient(Ingredient.fromJson(ingredientJson), count);
    }

    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        object.add("ingredient", ingredient.toJson());
        object.addProperty("count", count);
        return object;
    }

    public static SizedIngredient fromNetwork(FriendlyByteBuf buffer) {
        return new SizedIngredient(Ingredient.fromNetwork(buffer), buffer.readVarInt());
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        ingredient.toNetwork(buffer);
        buffer.writeVarInt(count);
    }

    public ItemStack[] getItems() {
        return ingredient.getItems();
    }
}
