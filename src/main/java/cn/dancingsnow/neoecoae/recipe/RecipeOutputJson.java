package cn.dancingsnow.neoecoae.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

final class RecipeOutputJson {
    private RecipeOutputJson() {}

    static ItemStack readItemStack(ResourceLocation recipeId, String fieldName, JsonObject object) {
        JsonObject normalized = object.deepCopy();
        if (!normalized.has("item") && normalized.has("id")) {
            normalized.add("item", normalized.get("id"));
        }
        if (!normalized.has("item")) {
            throw new JsonParseException("Recipe " + recipeId + " " + fieldName + " must contain 'item' or 'id'");
        }
        try {
            return CraftingHelper.getItemStack(normalized, true);
        } catch (JsonParseException e) {
            throw new JsonParseException("Recipe " + recipeId + " " + fieldName + " " + e.getMessage(), e);
        }
    }

    static FluidStack readFluidStack(ResourceLocation recipeId, String fieldName, JsonObject object) {
        if (object.size() == 0) {
            return FluidStack.EMPTY;
        }
        if (object.has("tag")) {
            throw new JsonParseException("Recipe " + recipeId + " " + fieldName + " cannot use a fluid tag");
        }
        String idField = object.has("fluid") ? "fluid" : object.has("id") ? "id" : null;
        if (idField == null) {
            throw new JsonParseException("Recipe " + recipeId + " " + fieldName + " must contain 'fluid' or 'id'");
        }
        ResourceLocation fluidId = ResourceLocation.parse(object.get(idField).getAsString());
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluid == null || fluid == Fluids.EMPTY) {
            throw new JsonParseException("Recipe " + recipeId + " has unknown fluid output '" + fluidId + "'");
        }
        int amount = object.has("amount") ? object.get("amount").getAsInt() : 1000;
        if (amount <= 0) {
            throw new JsonParseException("Recipe " + recipeId + " " + fieldName + " amount must be positive");
        }
        return new FluidStack(fluid, amount);
    }
}
