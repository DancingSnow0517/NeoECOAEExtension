package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkstationProcessingContractTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void nativeDurationDefaultsAndSurvivesJsonAndNetworkSerialization() {
        var id = new ResourceLocation("test:duration");
        var json = JsonParser.parseString("""
            {"inputItems":[{"item":"minecraft:iron_ingot","count":4}],
             "itemOutput":{"item":"minecraft:diamond","count":2},"energy":1000}
            """).getAsJsonObject();
        var serializer = new IntegratedWorkingStationRecipe.Serializer();
        assertEquals(100, serializer.fromJson(id, json).processingTicks());
        json.addProperty("processingTicks", 37);
        var recipe = serializer.fromJson(id, json);
        assertEquals(37, serializer.fromJson(id, IntegratedWorkingStationRecipe.Serializer.toJson(recipe)).processingTicks());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            var synced = serializer.fromNetwork(id, buffer);
            assertEquals(37, synced.processingTicks());
            assertEquals(1000, synced.energy());
            assertEquals(2, synced.itemOutput().getCount());
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
        json.addProperty("processingTicks", 0);
        assertThrows(IllegalArgumentException.class, () -> serializer.fromJson(id, json));
    }

    @Test void crystalScienceUsesItsOwnEnergyRateAndNativeRecipesUseTheirDuration() {
        var science = recipe("science", 4, 57_600, "ae2cs:circuit_etcher_recipe");
        assertEquals(200, science.processingTicks());
        var nativeRecipe = recipe("native", 4, 1000, LargeWorkstationRecipe.NATIVE_TYPE.toString());
        assertEquals(100, nativeRecipe.processingTicks());
    }

    @Test void forgeOwnedExtendedRecipeRetainsItsExplicitTimeInTheExecutionCatalog() {
        var id = new ResourceLocation("expatternprovider:custom_duration");
        var json = JsonParser.parseString("""
            {"inputItems":[{"item":"minecraft:iron_ingot","count":4}],
             "itemOutput":{"item":"minecraft:diamond","count":2},"energy":2000,"processingTicks":37}
            """).getAsJsonObject();
        var source = new ExtendedCrystalAssemblerRecipe.Serializer().fromJson(id, json);
        var manager = org.mockito.Mockito.mock(net.minecraft.world.item.crafting.RecipeManager.class);
        org.mockito.Mockito.when(manager.getRecipes()).thenReturn(List.of(source));
        var recipe = LargeWorkstationRecipes.getAll(manager, net.minecraft.core.RegistryAccess.EMPTY).get(0);
        assertEquals(37, recipe.processingTicks());
        assertEquals(ExtendedCrystalAssemblerRecipe.SOURCE_TYPE, recipe.sourceType());
    }

    @Test void proportionalGroupKeepsBothExistingCardsAndTheirIntegerBatchLimits() {
        var small = recipe("small", 4, 2000, "expatternprovider:crystal_assembler");
        var large = recipe("large", 32, 57_600, "ae2cs:circuit_etcher_recipe");
        var groups = LargeWorkstationRecipes.group(List.of(LargeWorkstationRecipeSources.direct(small), LargeWorkstationRecipeSources.direct(large)), JsonOps.INSTANCE);
        assertEquals(1, groups.size());
        var group = groups.get(0);
        assertEquals(List.of(LargeWorkstationModule.EXTENDED_AE, LargeWorkstationModule.CRYSTAL_SCIENCE), group.supportedModules());
        assertSame(small, group.availableVariants(LargeWorkstationModule.EXTENDED_AE).get(0).recipe());
        assertSame(large, group.availableVariants(LargeWorkstationModule.CRYSTAL_SCIENCE).get(0).recipe());
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.IRON_INGOT), 4);
        var outputs = new KeyCounter();
        outputs.add(AEItemKey.of(Items.DIAMOND), 4);
        assertNull(LargeWorkstationRecipes.findScaled(List.of(large), inputs, outputs));
        assertNotNull(LargeWorkstationRecipes.findScaled(List.of(small), inputs, outputs));
    }

    @Test void originalForgeIntegrationScopeRemainsUnchanged() {
        assertEquals(5, LargeWorkstationModule.values().length);
        for (var module : LargeWorkstationModule.values()) {
            assertFalse(module.unlocks(new ResourceLocation("data_energistics:data_reassembler")));
            assertFalse(module.unlocks(new ResourceLocation("appgen:synthesizing")));
            assertFalse(module.unlocks(new ResourceLocation("extendedae_plus:crystal_assembler_plus")));
        }
    }

    @Test void durationCapPreservesShortRecipesAndLeavesTheSourceMachineAndBudgetsUnchanged() {
        var original = new IntegratedWorkingStationRecipe(List.of(), new SizedFluidIngredient(FluidIngredient.empty(), 1),
            ItemStack.EMPTY, FluidStack.EMPTY, 1000, new ResourceLocation("test:long"), 160_000);
        var large = new LargeWorkstationRecipe(original.getId(), original, 1000, List.of());
        assertEquals(200, large.processingTicks());
        assertEquals(160_000, original.processingTicks());
        assertEquals(199, large.processing(199, 2000, true).processingTicks());
        var capped = large.processing(Integer.MAX_VALUE, 2000, true);
        assertEquals(200, capped.processingTicks());
        assertEquals(1000, capped.energy());
        assertEquals(2000, capped.feEnergy());
        assertTrue(capped.feAlternative());
    }

    private static LargeWorkstationRecipe recipe(String id, int count, long energy, String type) {
        var display = new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), count)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.DIAMOND, count), FluidStack.EMPTY, (int) energy);
        return new LargeWorkstationRecipe(new ResourceLocation("test", id), display, energy, List.of(), new ResourceLocation(type));
    }
}
