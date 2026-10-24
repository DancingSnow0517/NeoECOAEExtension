package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkstationProcessingContractTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void nativeDurationDefaultsToTheUnupgradedSingleBlockTimeAndRoundTrips() {
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        var codec = IntegratedWorkingStationRecipe.Serializer.CODEC.codec();
        var json = JsonParser.parseString("{\"energy\":1000}").getAsJsonObject();
        assertEquals(100, codec.parse(ops, json).getOrThrow().processingTicks());
        json.addProperty("processingTicks", 37);
        var parsed = codec.parse(ops, json).getOrThrow();
        assertEquals(37, parsed.processingTicks());
        assertEquals(parsed, codec.parse(ops, codec.encodeStart(ops, parsed).getOrThrow()).getOrThrow());
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            IntegratedWorkingStationRecipe.Serializer.STREAM_CODEC.encode(buffer, parsed);
            assertEquals(parsed, IntegratedWorkingStationRecipe.Serializer.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable(), "The duration is included in the network contract");
        } finally { buffer.release(); }
        json.addProperty("processingTicks", 0);
        assertTrue(codec.parse(ops, json).error().isPresent());
    }

    @Test void zeroEnergyReassemblyStillRetainsItsExplicitDuration() {
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        var json = JsonParser.parseString("""
            {"duration":77,"inputs":{"items":[{"item":"minecraft:iron_ingot","count":1}]},
             "outputs":{"items":[{"id":"minecraft:diamond","count":1}]}}
            """).getAsJsonObject();
        var recipe = DataWorkstationRecipes.decode(ResourceLocation.parse("test:timed"), "data_energistics:data_reassembler", json, ops);
        assertEquals(0, recipe.energy());
        assertEquals(77, recipe.processingTicks());
        json.addProperty("duration", -1);
        assertThrows(IllegalArgumentException.class, () -> DataWorkstationRecipes.decode(ResourceLocation.parse("test:timed"), "data_energistics:data_reassembler", json, ops));
    }

    @Test void crystalScienceDurationUsesItsUnupgradedTwoHundredAePerTickRate() {
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        var json = JsonParser.parseString("{\"input_a\":{\"item\":\"minecraft:iron_ingot\",\"count\":1},\"result\":{\"id\":\"minecraft:diamond\"},\"energy_cost\":57600}").getAsJsonObject();
        var recipe = LargeWorkstationRecipes.decode(ResourceLocation.parse("test:processor"), "ae2cs:circuit_etcher_recipe", json, ops);
        assertEquals(200, recipe.processingTicks());
        assertEquals(57600, recipe.energy());
        assertEquals(0, recipe.feEnergy());
    }

    @Test void durationCapPreservesShortRecipesAndDoesNotChangeTheOriginalMachineOrBudgets() {
        var original = new IntegratedWorkingStationRecipe(java.util.List.of(),
            new net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient(
                net.neoforged.neoforge.fluids.crafting.FluidIngredient.empty(), 1),
            net.minecraft.world.item.ItemStack.EMPTY, net.neoforged.neoforge.fluids.FluidStack.EMPTY, 1000, 160_000);
        var large = new LargeWorkstationRecipe(ResourceLocation.parse("test:long"), original, 1000, java.util.List.of());
        assertEquals(200, large.processingTicks());
        assertEquals(160_000, original.processingTicks());
        var shortRecipe = large.processing(199, 2000, true);
        assertEquals(199, shortRecipe.processingTicks());
        var capped = shortRecipe.processing(Integer.MAX_VALUE, 2000, true);
        assertEquals(200, capped.processingTicks());
        assertEquals(1000, capped.energy());
        assertEquals(2000, capped.feEnergy());
        assertTrue(capped.feAlternative());
    }
}
