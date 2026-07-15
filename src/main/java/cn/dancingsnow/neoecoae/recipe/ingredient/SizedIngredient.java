package cn.dancingsnow.neoecoae.recipe.ingredient;

import com.google.gson.JsonArray;
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
        long countValue =
                object != null && object.has("count") ? object.get("count").getAsLong() : 1L;
        if (countValue <= 0 || countValue > Integer.MAX_VALUE) {
            throw new JsonParseException("Sized ingredient count must be positive");
        }
        int count = (int) countValue;
        JsonElement ingredientJson = object != null && object.has("ingredient") ? object.get("ingredient") : json;
        ingredientJson = normalizeIngredientJson(ingredientJson);

        if (!ingredientJson.isJsonObject() && !ingredientJson.isJsonArray()) {
            throw new JsonParseException("Sized ingredient must contain 'item', 'id', 'tag', or 'ingredient'");
        }

        if (ingredientJson.isJsonObject()) {
            JsonObject ingredientObject = ingredientJson.getAsJsonObject();
            ingredientObject.remove("count");
            ingredientObject.remove("amount");
            if (!ingredientObject.has("item") && !ingredientObject.has("tag") && !ingredientObject.has("type")) {
                throw new JsonParseException("Sized ingredient must contain 'item', 'id', 'tag', or 'type'");
            }
        }

        return new SizedIngredient(Ingredient.fromJson(ingredientJson), count);
    }

    static JsonElement normalizeIngredientJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return json;
        }

        if (json.isJsonArray()) {
            JsonArray normalized = new JsonArray();
            for (JsonElement element : json.getAsJsonArray()) {
                normalized.add(normalizeIngredientJson(element));
            }
            return normalized;
        }
        if (!json.isJsonObject()) {
            return json;
        }

        JsonObject object = json.getAsJsonObject().deepCopy();
        if (object.has("ingredient")) {
            return normalizeIngredientJson(object.get("ingredient"));
        }

        if (object.has("id") && !object.has("item") && !object.has("tag")) {
            object.add("item", object.remove("id"));
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
