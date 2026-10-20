package cn.dancingsnow.neoecoae.platform.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import java.util.Objects;

/** Quantity belongs to the requirement, not to Ingredient's display stacks. */
public record SizedIngredient(Ingredient ingredient, int count) {
    public SizedIngredient {
        Objects.requireNonNull(ingredient);
        if (count <= 0) throw new IllegalArgumentException("Ingredient count must be positive");
    }

    public static SizedIngredient of(ItemLike item, int count) {
        return new SizedIngredient(Ingredient.of(item), count);
    }

    public static SizedIngredient of(TagKey<Item> tag, int count) {
        return new SizedIngredient(Ingredient.of(tag), count);
    }

    public boolean test(ItemStack stack) { return ingredient.test(stack) && stack.getCount() >= count; }

    public ItemStack[] getItems() {
        return java.util.Arrays.stream(ingredient.getItems()).map(stack -> stack.copyWithCount(count)).toArray(ItemStack[]::new);
    }

    public static SizedIngredient fromJson(JsonElement element) {
        JsonObject json = GsonHelper.convertToJsonObject(element, "sized ingredient");
        JsonObject predicate = json.deepCopy();
        predicate.remove("count");
        return new SizedIngredient(Ingredient.fromJson(predicate.has("ingredient") ? predicate.get("ingredient") : predicate, false),
            GsonHelper.getAsInt(json, "count", 1));
    }

    public JsonObject toJson() {
        JsonElement predicate = ingredient.toJson();
        // Arrays are valid Ingredient alternatives; use a nested field to retain that full predicate.
        JsonObject json = predicate.isJsonObject() ? predicate.getAsJsonObject().deepCopy() : new JsonObject();
        if (!predicate.isJsonObject()) json.add("ingredient", predicate);
        json.addProperty("count", count);
        return json;
    }

    public static SizedIngredient read(FriendlyByteBuf buffer) {
        Ingredient predicate = Ingredient.fromNetwork(buffer);
        return new SizedIngredient(predicate, buffer.readVarInt());
    }

    public void write(FriendlyByteBuf buffer) {
        ingredient.toNetwork(buffer);
        buffer.writeVarInt(count);
    }
}
