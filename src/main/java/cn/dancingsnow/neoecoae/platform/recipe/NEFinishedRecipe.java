package cn.dancingsnow.neoecoae.platform.recipe;

import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Custom machine recipes do not create a vanilla recipe-book advancement. */
public record NEFinishedRecipe(ResourceLocation id, RecipeSerializer<?> serializer, JsonObject data) implements FinishedRecipe {
    public NEFinishedRecipe { data = data.deepCopy(); }
    @Override public void serializeRecipeData(JsonObject json) {
        data.entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue().deepCopy()));
    }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getType() { return serializer; }
    @Override public JsonObject serializeAdvancement() { return null; }
    @Override public ResourceLocation getAdvancementId() { return null; }
}
