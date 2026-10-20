package cn.dancingsnow.neoecoae.platform.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import java.util.function.Consumer;

public final class ForgeRecipeOutputs {
    private ForgeRecipeOutputs() {}

    public static Consumer<FinishedRecipe> withConditions(Consumer<FinishedRecipe> output, ICondition... conditions) {
        return recipe -> output.accept(new FinishedRecipe() {
            @Override public void serializeRecipeData(JsonObject json) {
                recipe.serializeRecipeData(json);
                JsonArray all = json.has("conditions") ? json.getAsJsonArray("conditions").deepCopy() : new JsonArray();
                for (var condition : conditions) all.add(CraftingHelper.serialize(condition));
                json.add("conditions", all);
            }
            @Override public ResourceLocation getId() { return recipe.getId(); }
            @Override public RecipeSerializer<?> getType() { return recipe.getType(); }
            @Override public JsonObject serializeAdvancement() { return recipe.serializeAdvancement(); }
            @Override public ResourceLocation getAdvancementId() { return recipe.getAdvancementId(); }
        });
    }
}
