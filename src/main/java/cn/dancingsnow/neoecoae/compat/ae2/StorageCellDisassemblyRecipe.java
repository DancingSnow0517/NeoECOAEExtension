package cn.dancingsnow.neoecoae.compat.ae2;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

public final class StorageCellDisassemblyRecipe implements Recipe<SimpleContainer> {
    private final ResourceLocation id;
    private final Item storageCell;
    private final List<ItemStack> disassemblyItems;

    public StorageCellDisassemblyRecipe(ResourceLocation id, Item storageCell, List<ItemStack> disassemblyItems) {
        this.id = id;
        this.storageCell = storageCell;
        this.disassemblyItems = disassemblyItems.stream().map(ItemStack::copy).toList();
    }

    public static void save(
            Consumer<FinishedRecipe> recipeOutput, ResourceLocation id, Item storageCell, List<ItemStack> outputs) {
        recipeOutput.accept(new Result(id, storageCell, outputs));
    }

    public Item getStorageCell() {
        return storageCell;
    }

    public List<ItemStack> getCellDisassemblyItems() {
        return disassemblyItems.stream().map(ItemStack::copy).toList();
    }

    public static List<ItemStack> getDisassemblyResult(Level level, Item item) {
        for (StorageCellDisassemblyRecipe recipe :
                level.getRecipeManager().getAllRecipesFor(NERecipeTypes.STORAGE_CELL_DISASSEMBLY.get())) {
            if (recipe.storageCell == item) {
                return recipe.getCellDisassemblyItems();
            }
        }

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

    private static Item registryItem(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(NeoECOAE.id(path));
        return item == null ? Items.AIR : item;
    }

    @Override
    public boolean matches(SimpleContainer container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(SimpleContainer container, RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NERecipeTypes.STORAGE_CELL_DISASSEMBLY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NERecipeTypes.STORAGE_CELL_DISASSEMBLY.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<StorageCellDisassemblyRecipe> {
        @Override
        public StorageCellDisassemblyRecipe fromJson(ResourceLocation id, JsonObject json) {
            if (!json.has("cell") || !json.get("cell").isJsonPrimitive()) {
                throw new JsonParseException("Recipe " + id + " must contain string field 'cell'");
            }
            if (!json.has("cell_disassembly_items")
                    || !json.get("cell_disassembly_items").isJsonArray()) {
                throw new JsonParseException("Recipe " + id + " must contain array field 'cell_disassembly_items'");
            }
            Item storageCell = readItem(id, json.get("cell").getAsString(), "cell");

            List<ItemStack> outputs = new ArrayList<>();
            JsonArray items = json.getAsJsonArray("cell_disassembly_items");
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isJsonObject()) {
                    throw new JsonParseException(
                            "Recipe " + id + " cell_disassembly_items[" + i + "] must be an object");
                }
                outputs.add(readItemStack(id, items.get(i).getAsJsonObject(), "cell_disassembly_items[" + i + "]"));
            }
            if (outputs.isEmpty()) {
                throw new JsonParseException("Recipe " + id + " must contain at least one disassembly item");
            }
            return new StorageCellDisassemblyRecipe(id, storageCell, outputs);
        }

        @Override
        public StorageCellDisassemblyRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            Item storageCell = readItem(id, buffer.readResourceLocation().toString(), "cell");
            int count = buffer.readVarInt();
            if (count <= 0 || count > 64) {
                throw new IllegalArgumentException("Disassembly recipe output entry count must be between 1 and 64");
            }
            List<ItemStack> outputs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                outputs.add(buffer.readItem());
            }
            return new StorageCellDisassemblyRecipe(id, storageCell, outputs);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, StorageCellDisassemblyRecipe recipe) {
            ResourceLocation cellId = ForgeRegistries.ITEMS.getKey(recipe.storageCell);
            if (cellId == null) {
                throw new IllegalStateException("Cannot serialize unregistered storage cell " + recipe.storageCell);
            }
            buffer.writeResourceLocation(cellId);
            buffer.writeVarInt(recipe.disassemblyItems.size());
            for (ItemStack stack : recipe.disassemblyItems) {
                buffer.writeItem(stack);
            }
        }
    }

    private static Item readItem(ResourceLocation recipeId, String itemName, String field) {
        ResourceLocation itemId = ResourceLocation.tryParse(itemName);
        if (itemId == null) {
            throw new JsonParseException(
                    "Recipe " + recipeId + " has invalid item id in " + field + " '" + itemName + "'");
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == Items.AIR) {
            throw new JsonParseException("Recipe " + recipeId + " has unknown item in " + field + " '" + itemId + "'");
        }
        return item;
    }

    private static ItemStack readItemStack(ResourceLocation recipeId, JsonObject object, String field) {
        String itemField = object.has("item") ? "item" : object.has("id") ? "id" : null;
        if (itemField == null) {
            throw new JsonParseException("Recipe " + recipeId + " " + field + " must contain 'item' or 'id'");
        }
        Item item = readItem(recipeId, object.get(itemField).getAsString(), field);
        long count = object.has("count") ? object.get("count").getAsLong() : 1L;
        if (count <= 0 || count > item.getMaxStackSize()) {
            throw new JsonParseException(
                    "Recipe " + recipeId + " " + field + " count must be between 1 and " + item.getMaxStackSize());
        }
        return new ItemStack(item, (int) count);
    }

    private record Result(ResourceLocation id, Item storageCell, List<ItemStack> disassemblyItems)
            implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            ResourceLocation cellId = ForgeRegistries.ITEMS.getKey(storageCell);
            if (cellId == null) {
                throw new IllegalStateException("Cannot serialize unregistered storage cell " + storageCell);
            }
            json.addProperty("cell", cellId.toString());

            JsonArray items = new JsonArray();
            for (ItemStack stack : disassemblyItems) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (itemId == null) {
                    throw new IllegalStateException(
                            "Cannot serialize unregistered disassembly item " + stack.getItem());
                }
                JsonObject item = new JsonObject();
                item.addProperty("id", itemId.toString());
                item.addProperty("count", stack.getCount());
                items.add(item);
            }
            json.add("cell_disassembly_items", items);
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return NERecipeTypes.STORAGE_CELL_DISASSEMBLY_SERIALIZER.get();
        }

        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }

    private static final Map<String, DisassemblyParts> DISASSEMBLY_PARTS = Map.ofEntries(
            Map.entry(
                    "eco_item_storage_cell_16m",
                    new DisassemblyParts("eco_item_cell_housing", "eco_cell_component_16m")),
            Map.entry(
                    "eco_item_storage_cell_64m",
                    new DisassemblyParts("eco_item_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_item_storage_cell_256m",
                    new DisassemblyParts("eco_item_cell_housing", "eco_cell_component_256m")),
            Map.entry(
                    "eco_fluid_storage_cell_16m",
                    new DisassemblyParts("eco_fluid_cell_housing", "eco_cell_component_16m")),
            Map.entry(
                    "eco_fluid_storage_cell_64m",
                    new DisassemblyParts("eco_fluid_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_fluid_storage_cell_256m",
                    new DisassemblyParts("eco_fluid_cell_housing", "eco_cell_component_256m")),
            Map.entry(
                    "eco_chemical_storage_cell_16m",
                    new DisassemblyParts("eco_chemical_cell_housing", "eco_cell_component_16m")),
            Map.entry(
                    "eco_chemical_storage_cell_64m",
                    new DisassemblyParts("eco_chemical_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_chemical_storage_cell_256m",
                    new DisassemblyParts("eco_chemical_cell_housing", "eco_cell_component_256m")),
            Map.entry("eco_fe_storage_cell_16m", new DisassemblyParts("eco_fe_cell_housing", "eco_cell_component_16m")),
            Map.entry("eco_fe_storage_cell_64m", new DisassemblyParts("eco_fe_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_fe_storage_cell_256m", new DisassemblyParts("eco_fe_cell_housing", "eco_cell_component_256m")),
            Map.entry(
                    "eco_mana_storage_cell_16m",
                    new DisassemblyParts("eco_mana_cell_housing", "eco_cell_component_16m")),
            Map.entry(
                    "eco_mana_storage_cell_64m",
                    new DisassemblyParts("eco_mana_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_mana_storage_cell_256m",
                    new DisassemblyParts("eco_mana_cell_housing", "eco_cell_component_256m")),
            Map.entry(
                    "eco_source_storage_cell_16m",
                    new DisassemblyParts("eco_source_cell_housing", "eco_cell_component_16m")),
            Map.entry(
                    "eco_source_storage_cell_64m",
                    new DisassemblyParts("eco_source_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_source_storage_cell_256m",
                    new DisassemblyParts("eco_source_cell_housing", "eco_cell_component_256m")));

    private record DisassemblyParts(String housing, String component) {}
}
