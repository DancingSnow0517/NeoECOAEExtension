package cn.dancingsnow.neoecoae.compat.ae2;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

public class StorageCellDisassemblyRecipe implements FinishedRecipe {
    private final Item output;
    private final List<ItemStack> ingredients;

    public StorageCellDisassemblyRecipe(Item output, List<ItemStack> ingredients) {
        this.output = output;
        this.ingredients = ingredients;
    }

    public static List<ItemStack> getDisassemblyResult(Level level, Item item) {
        ResourceLocation cellId = ForgeRegistries.ITEMS.getKey(item);
        if (cellId == null || !NeoECOAE.MOD_ID.equals(cellId.getNamespace())) {
            return List.of();
        }

        DisassemblyParts parts = DISASSEMBLY_PARTS.get(cellId.getPath());
        if (parts == null) {
            return List.of();
        }

        Item housing = registryItem(parts.housing());
        Item component = registryItem(parts.component());
        if (housing == Items.AIR || component == Items.AIR) {
            return List.of();
        }
        return List.of(new ItemStack(housing), new ItemStack(component));
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

    private static Item registryItem(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(NeoECOAE.id(path));
        return item == null ? Items.AIR : item;
    }

    private static final Map<String, DisassemblyParts> DISASSEMBLY_PARTS = Map.ofEntries(
            Map.entry("eco_item_storage_cell_16m",
                    new DisassemblyParts("eco_item_cell_housing", "eco_cell_component_16m")),
            Map.entry("eco_item_storage_cell_64m",
                    new DisassemblyParts("eco_item_cell_housing", "eco_cell_component_64m")),
            Map.entry("eco_item_storage_cell_256m",
                    new DisassemblyParts("eco_item_cell_housing", "eco_cell_component_256m")),
            Map.entry("eco_fluid_storage_cell_16m",
                    new DisassemblyParts("eco_fluid_cell_housing", "eco_cell_component_16m")),
            Map.entry("eco_fluid_storage_cell_64m",
                    new DisassemblyParts("eco_fluid_cell_housing", "eco_cell_component_64m")),
            Map.entry("eco_fluid_storage_cell_256m",
                    new DisassemblyParts("eco_fluid_cell_housing", "eco_cell_component_256m")),
            Map.entry("eco_chemical_storage_cell_16m",
                    new DisassemblyParts("eco_chemical_cell_housing", "eco_cell_component_16m")),
            Map.entry("eco_chemical_storage_cell_64m",
                    new DisassemblyParts("eco_chemical_cell_housing", "eco_cell_component_64m")),
            Map.entry("eco_chemical_storage_cell_256m",
                    new DisassemblyParts("eco_chemical_cell_housing", "eco_cell_component_256m")));

    private record DisassemblyParts(String housing, String component) {
    }
}
