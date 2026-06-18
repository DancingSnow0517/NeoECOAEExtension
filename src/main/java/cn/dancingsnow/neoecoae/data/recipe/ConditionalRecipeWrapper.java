package cn.dancingsnow.neoecoae.data.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;

public record ConditionalRecipeWrapper(FinishedRecipe recipe, ICondition condition) implements FinishedRecipe {
    @Override
    public void serializeRecipeData(JsonObject json) {
        recipe.serializeRecipeData(json);
        json.add("conditions", serializeConditions());
    }

    @Override
    public JsonObject serializeRecipe() {
        JsonObject json = recipe.serializeRecipe();
        json.add("conditions", serializeConditions());
        return json;
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
        JsonObject json = recipe.serializeAdvancement();
        if (json != null) {
            json.add("conditions", serializeConditions());
        }
        return json;
    }

    @Override
    public ResourceLocation getAdvancementId() {
        return recipe.getAdvancementId();
    }

    private JsonArray serializeConditions() {
        JsonArray conditions = new JsonArray();
        conditions.add(CraftingHelper.serialize(condition));
        return conditions;
    }
}
