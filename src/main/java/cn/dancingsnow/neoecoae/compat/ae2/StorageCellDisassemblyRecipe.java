package cn.dancingsnow.neoecoae.compat.ae2;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

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
    public JsonObject serializeRecipe() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ae2:storage_cell_disassembly");
        serializeRecipeData(json);
        return json;
    }

    @Override
    public void serializeRecipeData(JsonObject json) {
        json.addProperty("cell", itemId(output).toString());
        JsonArray stacks = new JsonArray();
        for (ItemStack ingredient : ingredients) {
            JsonObject stack = new JsonObject();
            stack.addProperty("count", ingredient.getCount());
            stack.addProperty("id", itemId(ingredient.getItem()).toString());
            stacks.add(stack);
        }
        json.add("cell_disassembly_items", stacks);
    }

    @Override
    public ResourceLocation getId() {
        return NeoECOAE.id("disassembly/" + itemId(output).getPath());
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

    private static ResourceLocation itemId(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            throw new IllegalStateException("Cannot serialize unregistered item " + item);
        }
        return id;
    }
}
