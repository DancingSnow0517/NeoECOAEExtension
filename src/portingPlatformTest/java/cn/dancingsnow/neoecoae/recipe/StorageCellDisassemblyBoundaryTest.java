package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageCellDisassemblyBoundaryTest {
    private static final ResourceLocation ID = new ResourceLocation("neoecoae", "disassembly_test");
    @BeforeAll static void bootstrap() { PlatformTestBootstrap.initialize(); }

    @Test void jsonAndNetworkKeepAllDisassemblyProducts() {
        var serializer = new StorageCellDisassemblyRecipe.Serializer();
        var recipe = serializer.fromJson(ID, JsonParser.parseString("""
            {"cell":"minecraft:paper","cell_disassembly_items":[
                {"item":"minecraft:diamond","count":1024,"nbt":"{grade:7}"},
                {"item":"minecraft:iron_ingot","count":4}]}
            """).getAsJsonObject());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            var restored = serializer.fromNetwork(ID, buffer);
            assertEquals(Items.PAPER, restored.getStorageCell());
            assertEquals(2, restored.getCellDisassemblyItems().size());
            assertEquals(1024, restored.getCellDisassemblyItems().get(0).getCount());
            assertEquals(7, restored.getCellDisassemblyItems().get(0).getTag().getInt("grade"));
            assertEquals(4, restored.getCellDisassemblyItems().get(1).getCount());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void lookupReturnsFreshProductsAndTracksRecipeReload() {
        var manager = mock(RecipeManager.class);
        var level = mock(Level.class);
        when(level.getRecipeManager()).thenReturn(manager);
        var recipe = new StorageCellDisassemblyRecipe(Items.PAPER, List.of(new ItemStack(Items.DIAMOND)), ID);
        when(manager.getRecipes()).thenReturn(List.of(recipe));
        var first = StorageCellDisassemblyRecipe.getDisassemblyResult(level, Items.PAPER);
        first.get(0).setCount(42);
        assertEquals(1, StorageCellDisassemblyRecipe.getDisassemblyResult(level, Items.PAPER).get(0).getCount());
        assertTrue(StorageCellDisassemblyRecipe.getDisassemblyResult(level, Items.STONE).isEmpty());
        when(manager.getRecipes()).thenReturn(List.of());
        assertTrue(StorageCellDisassemblyRecipe.getDisassemblyResult(level, Items.PAPER).isEmpty());
    }

    @Test void outputDefinitionsCannotBeMutatedThroughTheirInputOrAccessor() {
        var item = new ItemStack(Items.DIAMOND, 4);
        var recipe = new StorageCellDisassemblyRecipe(Items.PAPER, List.of(item), ID);
        item.setCount(1);
        recipe.disassemblyItems().get(0).setCount(2);
        assertEquals(4, recipe.getCellDisassemblyItems().get(0).getCount());
    }
}
