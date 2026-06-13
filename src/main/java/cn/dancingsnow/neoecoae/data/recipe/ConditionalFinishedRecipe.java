package cn.dancingsnow.neoecoae.data.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;

record ConditionalFinishedRecipe(FinishedRecipe recipe, ICondition condition) implements FinishedRecipe {
    @Override
    public JsonObject serializeRecipe() {
        JsonObject json = recipe.serializeRecipe();
        JsonArray conditions = new JsonArray();
        conditions.add(CraftingHelper.serialize(condition));
        json.add("conditions", conditions);
        return json;
    }

    @Override
    public void serializeRecipeData(JsonObject json) {
        recipe.serializeRecipeData(json);
    }

    @Override
    public ResourceLocation getId() {
        return recipe.getId();
    }

    @Override
    public RecipeSerializer<?> getType() {
        return recipe.getType();
    }

    @Override
    public JsonObject serializeAdvancement() {
        return recipe.serializeAdvancement();
    }

    @Override
    public ResourceLocation getAdvancementId() {
        return recipe.getAdvancementId();
    }
}
