package cn.dancingsnow.neoecoae.compat.ae2;

import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;

public class StorageCellDisassemblyRecipe implements FinishedRecipe {
    private final Item output;
    private final List<ItemStack> ingredients;

    public StorageCellDisassemblyRecipe(Item output, List<ItemStack> ingredients) {
        this.output = output;
        this.ingredients = ingredients;
    }

    public static List<ItemStack> getDisassemblyResult(Level level, Item item) {
        return List.of();
    }

    @Override
    public void serializeRecipeData(JsonObject json) {
    }

    @Override
    public ResourceLocation getId() {
        return new ResourceLocation("ae2", "compat_disassembly/" + output);
    }

    @Override
    public RecipeSerializer<?> getType() {
        return RecipeSerializer.SHAPELESS_RECIPE;
    }

    @Override
    public JsonObject serializeAdvancement() {
        return null;
    }

    @Override
    public ResourceLocation getAdvancementId() {
        return null;
    }

    public List<ItemStack> ingredients() {
        return ingredients;
    }
}
