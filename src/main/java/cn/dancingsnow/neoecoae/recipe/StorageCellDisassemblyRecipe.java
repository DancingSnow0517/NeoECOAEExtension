package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.platform.recipe.NEFinishedRecipe;
import cn.dancingsnow.neoecoae.platform.recipe.NERecipeInput;
import cn.dancingsnow.neoecoae.platform.recipe.RecipeJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** AE2 19's item-use disassembly metadata on AE2 15. ECOStorageCellItem owns the actual empty-cell check and exchange. */
public record StorageCellDisassemblyRecipe(Item storageCell, List<ItemStack> disassemblyItems, ResourceLocation id)
        implements Recipe<NERecipeInput> {
    private static final int MAX_OUTPUTS = 64;
    public StorageCellDisassemblyRecipe(Item cell, List<ItemStack> outputs) {
        this(cell, outputs, new ResourceLocation("neoecoae", "disassembly/" + BuiltInRegistries.ITEM.getKey(cell).getPath()));
    }
    public StorageCellDisassemblyRecipe {
        if (storageCell == net.minecraft.world.item.Items.AIR) throw new IllegalArgumentException("Disassembly cell cannot be air");
        if (disassemblyItems.size() > MAX_OUTPUTS || disassemblyItems.stream().anyMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("Invalid disassembly outputs");
        }
        disassemblyItems = disassemblyItems.stream().map(ItemStack::copy).toList();
    }

    public Item getStorageCell() { return storageCell; }
    @Override public List<ItemStack> disassemblyItems() { return disassemblyItems.stream().map(ItemStack::copy).toList(); }
    public List<ItemStack> getCellDisassemblyItems() { return disassemblyItems.stream().map(ItemStack::copy).toList(); }
    public boolean canDisassemble() { return !disassemblyItems.isEmpty(); }
    public static List<ItemStack> getDisassemblyResult(Level level, Item cell) {
        for (var source : level.getRecipeManager().getRecipes()) {
            if (source instanceof StorageCellDisassemblyRecipe recipe && recipe.storageCell == cell) {
                return recipe.getCellDisassemblyItems();
            }
        }
        return List.of();
    }

    public void save(Consumer<FinishedRecipe> output, ResourceLocation id) {
        output.accept(new NEFinishedRecipe(id, NERecipeTypes.CELL_DISASSEMBLY_SERIALIZER.get(), Serializer.toJson(this)));
    }

    // This metadata is consumed through the alternate-use action, never a crafting-grid recipe.
    @Override public boolean matches(NERecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(NERecipeInput input, RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NERecipeTypes.CELL_DISASSEMBLY_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NERecipeTypes.CELL_DISASSEMBLY.get(); }

    public static final class Serializer implements RecipeSerializer<StorageCellDisassemblyRecipe> {
        @Override public StorageCellDisassemblyRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonObject cellJson = new JsonObject();
            cellJson.addProperty("item", GsonHelper.getAsString(json, "cell"));
            ItemStack cell = RecipeJson.item(cellJson);
            var array = GsonHelper.getAsJsonArray(json, "cell_disassembly_items");
            if (array.size() > MAX_OUTPUTS) throw new IllegalArgumentException("Too many disassembly outputs");
            List<ItemStack> outputs = new ArrayList<>();
            for (var value : array) outputs.add(RecipeJson.item(value));
            return new StorageCellDisassemblyRecipe(cell.getItem(), outputs, id);
        }
        public static JsonObject toJson(StorageCellDisassemblyRecipe recipe) {
            JsonObject json = new JsonObject();
            json.addProperty("cell", BuiltInRegistries.ITEM.getKey(recipe.storageCell()).toString());
            JsonArray outputs = new JsonArray();
            for (var output : recipe.disassemblyItems()) outputs.add(RecipeJson.item(output));
            json.add("cell_disassembly_items", outputs);
            return json;
        }
        @Override public StorageCellDisassemblyRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            JsonObject json = new JsonObject();
            json.addProperty("item", buffer.readResourceLocation().toString());
            Item cell = RecipeJson.item(json).getItem();
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_OUTPUTS) throw new IllegalArgumentException("Invalid disassembly output count");
            List<ItemStack> outputs = new ArrayList<>();
            for (int i = 0; i < size; i++) outputs.add(RecipeJson.readItem(buffer));
            return new StorageCellDisassemblyRecipe(cell, outputs, id);
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, StorageCellDisassemblyRecipe recipe) {
            buffer.writeResourceLocation(BuiltInRegistries.ITEM.getKey(recipe.storageCell()));
            buffer.writeVarInt(recipe.disassemblyItems().size());
            for (var output : recipe.disassemblyItems()) RecipeJson.writeItem(buffer, output);
        }
    }
}
