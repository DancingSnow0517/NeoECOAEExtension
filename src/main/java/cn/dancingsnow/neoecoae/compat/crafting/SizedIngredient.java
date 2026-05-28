package cn.dancingsnow.neoecoae.compat.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

public record SizedIngredient(Ingredient ingredient, int count) {
    /** Create a SizedIngredient from an ItemLike. */
    public static SizedIngredient of(ItemLike itemLike, int count) {
        return new SizedIngredient(Ingredient.of(itemLike), count);
    }

    /** Create a SizedIngredient from a tag. */
    public static SizedIngredient of(TagKey<Item> tag, int count) {
        return new SizedIngredient(Ingredient.of(tag), count);
    }

    public static SizedIngredient fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return new SizedIngredient(Ingredient.EMPTY, 0);
        }

        JsonObject object = json.isJsonObject() ? json.getAsJsonObject() : null;
        int count = object != null && object.has("count") ? object.get("count").getAsInt() : 1;
        JsonElement ingredientJson = object != null && object.has("ingredient") ? object.get("ingredient") : json;
        ingredientJson = normalizeIngredientJson(ingredientJson);

        if (!ingredientJson.isJsonObject()) {
            throw new JsonParseException("Sized ingredient must contain 'item', 'id', 'tag', or 'ingredient'");
        }

        JsonObject ingredientObject = ingredientJson.getAsJsonObject();
        // Remove count/amount keys so vanilla Ingredient.fromJson does not choke
        if (ingredientObject.has("count")) ingredientObject.remove("count");
        if (ingredientObject.has("amount")) ingredientObject.remove("amount");
        if (!ingredientObject.has("item") && !ingredientObject.has("tag") && !ingredientObject.has("id")) {
            throw new JsonParseException("Sized ingredient must contain 'item', 'id', or 'tag'");
        }

        return new SizedIngredient(Ingredient.fromJson(ingredientObject), count);
    }

    private static JsonElement normalizeIngredientJson(JsonElement json) {
        if (json == null || json.isJsonNull() || !json.isJsonObject()) {
            return json;
        }

        JsonObject object = json.getAsJsonObject();
        if (object.has("ingredient")) {
            return normalizeIngredientJson(object.get("ingredient"));
        }

        if (object.has("id") && !object.has("item") && !object.has("tag")) {
            JsonObject normalized = new JsonObject();
            normalized.addProperty("item", object.get("id").getAsString());
            return normalized;
        }

        return object;
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
