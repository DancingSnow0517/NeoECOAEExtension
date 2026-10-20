package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.RegistryManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationUpgradeCardRecipeTest {
    private static final String PREFIX = "neoecoae:integrated_working_station_module_";

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "advanced_ae", "extendedae", "ae2cs", "aelt", "data_energistics", "appgen"})
    void recipesKeepApprovedCostsAndOptionalModConditions(String variant) throws IOException {
        JsonObject json = resource(variant);
        assertEquals("neoecoae:large_integrated_working_station", json.get("type").getAsString());
        assertEquals(PREFIX + variant, json.getAsJsonObject("itemOutput").get("id").getAsString());
        assertEquals(1, json.getAsJsonObject("itemOutput").get("count").getAsInt());
        assertEquals(16_384_000, json.get("energy").getAsInt());
        String expectedFluid = switch (variant) {
            case "advanced_ae" -> "advanced_ae:quantum_infusion_source";
            case "data_energistics" -> "data_energistics:data_corrosion_liquid";
            case "appgen" -> "minecraft:lava";
            default -> null;
        };
        if (expectedFluid == null) {
            assertFalse(json.has("inputFluid"));
        } else {
            var fluid = json.getAsJsonObject("inputFluid");
            assertEquals(expectedFluid, fluid.get("fluid").getAsString());
            assertEquals(16_000, fluid.get("amount").getAsInt());
        }
        Map<String, Integer> expected = new LinkedHashMap<>();
        if (variant.equals("empty")) {
            expected.put("ae2:advanced_card", 1);
            expected.put("neoecoae:energized_superconductive_ingot", 4);
            expected.put("neoecoae:superconducting_processor", 2);
            expected.put("neoecoae:crystal_matrix", 2);
            assertFalse(json.has("neoforge:conditions"));
        } else {
            expected.put(PREFIX + "empty", 1);
            if (!variant.equals("aelt")) {
                expected.put("neoecoae:superconducting_processor", 16);
                expected.put("neoecoae:energized_superconductive_ingot", 64);
                expected.put("neoecoae:crystal_matrix", 16);
                expected.put("ae2:singularity", 16);
            }
            String mod = variant.equals("aelt") ? "ae2lt" : variant;
            var conditions = json.getAsJsonArray("neoforge:conditions");
            assertEquals(1, conditions.size());
            assertEquals("neoforge:mod_loaded", conditions.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals(mod, conditions.get(0).getAsJsonObject().get("modid").getAsString());
            switch (variant) {
                case "advanced_ae" -> {
                    expected.put("advanced_ae:reaction_chamber", 8);
                    expected.put("advanced_ae:quantum_alloy", 64);
                    expected.put("advanced_ae:quantum_processor", 64);
                }
                case "extendedae" -> {
                    expected.put("extendedae:crystal_assembler", 8);
                    expected.put("extendedae:entro_ingot", 256);
                    expected.put("extendedae:concurrent_processor", 128);
                }
                case "ae2cs" -> {
                    expected.put("ae2cs:circuit_etcher", 4);
                    expected.put("ae2cs:crystal_aggregator", 4);
                    expected.put("ae2cs:purified_resonating_crystal", 256);
                    expected.put("ae2cs:resonating_processor", 128);
                }
                case "aelt" -> {
                    expected.put("ae2lt:ultimate_overload_core", 16);
                    expected.put("ae2lt:overload_alloy_plate", 64);
                    expected.put("ae2lt:lightning_collapse_matrix", 16);
                    expected.put("ae2lt:overload_singularity", 16);
                    expected.put("ae2lt:overload_processing_factory", 4);
                    expected.put("ae2lt:lightning_assembly_chamber", 4);
                    expected.put("ae2lt:overload_alloy", 128);
                    expected.put("ae2lt:overload_processor", 128);
                }
                case "data_energistics" -> {
                    expected.put("data_energistics:data_reassembler", 4);
                    expected.put("data_energistics:data_integrated_charger", 4);
                    expected.put("data_energistics:digisidian_memorize_ingot", 64);
                    expected.put("data_energistics:data_processor", 128);
                }
                case "appgen" -> {
                    expected.put("appgen:genesis_synthesizer", 8);
                    expected.put("appgen:budding_ember_flawless", 4);
                    expected.put("appgen:ember_crystal_charged", 256);
                    expected.put("appgen:origination_processor", 128);
                }
                default -> fail("Unexpected card: " + variant);
            }
        }
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (var entry : json.getAsJsonArray("inputItems")) {
            var input = entry.getAsJsonObject();
            assertNull(actual.put(input.get("item").getAsString(), input.get("count").getAsInt()));
        }
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "advanced_ae", "extendedae", "ae2cs", "aelt", "data_energistics", "appgen"})
    void nativeLargeRecipesAreAvailableWithoutAnyCardAndKeepFullBatchCosts(String variant) throws IOException {
        var recipe = decodeWithVanillaItems(resource(variant));
        var id = ResourceLocation.parse("test:card_" + variant);
        var holder = new RecipeHolder<>(id, recipe);
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of(holder));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        boolean previous = NEConfig.largeWorkstationIgnoreUpgradeCards;
        NEConfig.largeWorkstationIgnoreUpgradeCards = false;
        try {
            assertFalse(IntegratedWorkingStationRecipe.class.isInstance(recipe));
            var available = LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR);
            assertEquals(1, available.size());
            var runtime = available.getFirst();
            assertEquals(LargeWorkstationRecipe.NATIVE_TYPE, runtime.sourceType());
            assertEquals(16_384_000, runtime.energy());
            assertSame(runtime, LargeWorkstationRecipes.getById(manager, registries, id));
            for (var module : LargeWorkstationModule.values()) {
                assertEquals(List.of(runtime), LargeWorkstationRecipes.getAvailable(manager, registries, module));
            }

            KeyCounter inputs = new KeyCounter();
            for (var input : recipe.display().inputItems()) {
                inputs.add(AEItemKey.of(input.getItems()[0]), input.count());
            }
            KeyCounter outputs = new KeyCounter();
            outputs.add(AEItemKey.of(Items.DIAMOND), 1);
            var requiredFluid = recipe.display().inputFluid();
            if (!requiredFluid.ingredient().isEmpty()) {
                assertFalse(runtime.matches(inputs, outputs));
                inputs.add(AEFluidKey.of(Fluids.LAVA), requiredFluid.amount());
                assertFalse(runtime.matches(inputs, outputs));
                inputs.remove(AEFluidKey.of(Fluids.LAVA), requiredFluid.amount());
                inputs.add(AEFluidKey.of(Fluids.WATER), requiredFluid.amount() - 1);
                assertFalse(runtime.matches(inputs, outputs));
                inputs.add(AEFluidKey.of(Fluids.WATER), 1);
            }
            assertTrue(runtime.matches(inputs, outputs));
            var required = recipe.display().inputItems().getLast();
            inputs.remove(AEItemKey.of(required.getItems()[0]), 1);
            assertFalse(runtime.matches(inputs, outputs));
            inputs.add(AEItemKey.of(required.getItems()[0]), 1);
            for (var input : recipe.display().inputItems()) {
                inputs.add(AEItemKey.of(input.getItems()[0]), input.count());
            }
            outputs.add(AEItemKey.of(Items.DIAMOND), 1);
            if (!requiredFluid.ingredient().isEmpty()) {
                assertEquals(0, runtime.matchingMultiplier(inputs, outputs));
                inputs.add(AEFluidKey.of(Fluids.WATER), requiredFluid.amount());
            }
            assertEquals(2, runtime.matchingMultiplier(inputs, outputs));
        } finally {
            NEConfig.largeWorkstationIgnoreUpgradeCards = previous;
        }
    }

    @Test void lightningRecipeSurvivesJsonAndNetworkRoundTrips() throws IOException {
        var recipe = decodeWithVanillaItems(resource("aelt"));
        assertEquals(9, recipe.display().inputItems().size());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var json = LargeIntegratedWorkingStationRecipe.Serializer.CODEC.codec().encodeStart(ops, recipe).getOrThrow();
        var restored = LargeIntegratedWorkingStationRecipe.Serializer.CODEC.codec().parse(ops, json).getOrThrow();
        assertEquals(recipe.display().inputItems().stream().map(input -> input.count()).toList(),
            restored.display().inputItems().stream().map(input -> input.count()).toList());

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try (var sync = mockStatic(RegistryManager.class)) {
            LargeIntegratedWorkingStationRecipe.Serializer.STREAM_CODEC.encode(buffer, restored);
            var synced = LargeIntegratedWorkingStationRecipe.Serializer.STREAM_CODEC.decode(buffer);
            assertEquals(9, synced.display().inputItems().size());
            assertEquals(16, synced.display().inputItems().get(1).count());
            assertEquals(64, synced.display().inputItems().get(2).count());
            assertEquals(16, synced.display().inputItems().get(3).count());
            assertEquals(16, synced.display().inputItems().get(4).count());
            assertEquals(4, synced.display().inputItems().get(5).count());
            assertEquals(4, synced.display().inputItems().get(6).count());
            assertEquals(128, synced.display().inputItems().get(7).count());
            assertEquals(128, synced.display().inputItems().getLast().count());
            assertEquals(16_384_000, synced.display().energy());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"advanced_ae", "data_energistics", "appgen"})
    void fluidCostsSurviveJsonAndNetworkRoundTrips(String variant) throws IOException {
        var recipe = decodeWithVanillaItems(resource(variant));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var json = LargeIntegratedWorkingStationRecipe.Serializer.CODEC.codec().encodeStart(ops, recipe).getOrThrow();
        var restored = LargeIntegratedWorkingStationRecipe.Serializer.CODEC.codec().parse(ops, json).getOrThrow();

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try (var sync = mockStatic(RegistryManager.class)) {
            LargeIntegratedWorkingStationRecipe.Serializer.STREAM_CODEC.encode(buffer, restored);
            var synced = LargeIntegratedWorkingStationRecipe.Serializer.STREAM_CODEC.decode(buffer);
            for (var decoded : List.of(recipe, restored, synced)) {
                var fluid = decoded.display().inputFluid();
                assertEquals(16_000, fluid.amount());
                assertTrue(fluid.test(new FluidStack(Fluids.WATER, 16_000)));
                assertFalse(fluid.test(new FluidStack(Fluids.WATER, 15_999)));
                assertFalse(fluid.test(new FluidStack(Fluids.LAVA, 16_000)));
            }
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test void nativeLargeInputLimitMatchesTheTwelveSlotViewer() throws IOException {
        var json = resource("aelt");
        var inputs = json.getAsJsonArray("inputItems");
        while (inputs.size() < LargeIntegratedWorkingStationRecipe.MAX_ITEM_INPUTS) inputs.add(inputs.get(0).deepCopy());
        var recipe = decodeWithVanillaItems(json);
        assertEquals(12, recipe.display().inputItems().size());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var encoded = LargeIntegratedWorkingStationRecipe.Serializer.CODEC.codec().encodeStart(ops, recipe).getOrThrow();
        assertTrue(IntegratedWorkingStationRecipe.Serializer.CODEC.codec().parse(ops, encoded).error().isPresent());
        inputs.add(inputs.get(0).deepCopy());
        assertThrows(IllegalStateException.class, () -> decodeWithVanillaItems(json));
    }

    private static JsonObject resource(String variant) throws IOException {
        var path = Path.of("src/main/resources/data/neoecoae/recipe/large_integrated_working_station/"
            + "integrated_working_station_module_" + variant + ".json");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    // Unit tests have vanilla registries; substitute items and fluids while preserving every recipe quantity.
    private static LargeIntegratedWorkingStationRecipe decodeWithVanillaItems(JsonObject source) {
        var json = source.deepCopy();
        var items = List.of(Items.PAPER, Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE,
            Items.EMERALD, Items.FURNACE, Items.CRAFTING_TABLE, Items.ANVIL, Items.COPPER_INGOT,
            Items.LAPIS_LAZULI, Items.STONE, Items.COBBLESTONE, Items.DIRT);
        var inputs = json.getAsJsonArray("inputItems");
        for (int i = 0; i < inputs.size(); i++) {
            inputs.get(i).getAsJsonObject().addProperty("item", BuiltInRegistries.ITEM.getKey(items.get(i)).toString());
        }
        if (json.has("inputFluid")) {
            json.getAsJsonObject("inputFluid").addProperty("fluid", "minecraft:water");
        }
        json.getAsJsonObject("itemOutput").addProperty("id", "minecraft:diamond");
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return LargeIntegratedWorkingStationRecipe.Serializer.CODEC.codec()
            .parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }
}
