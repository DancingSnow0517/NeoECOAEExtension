package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import net.neoforged.neoforge.fluids.FluidType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataWorkstationRecipeTest {
    private static final String PREFIX = "data_energistics:";
    private MockedStatic<AEKeyTypes> types;
    private MockedStatic<AEKeyTypesInternal> internal;
    private boolean previousIgnoreCards;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        var fluids = (MappedRegistry<Fluid>) BuiltInRegistries.FLUID;
        var id = ResourceLocation.parse(PREFIX + "data_corrosion_liquid");
        if (!fluids.containsKey(id)) {
            fluids.unfreeze();
            try {
                var fluidType = new FluidType(FluidType.Properties.create()) {};
                Registry.register(fluids, id, new WaterFluid.Source() {
                    @Override public FluidType getFluidType() { return fluidType; }
                });
            } finally {
                fluids.freeze();
            }
        }
    }

    @BeforeEach void setup() {
        types = mockStatic(AEKeyTypes.class);
        internal = mockStatic(AEKeyTypesInternal.class);
        internal.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
        types.when(() -> AEKeyTypes.get(any(ResourceLocation.class)))
            .thenAnswer(call -> LargeWorkstationTestKeys.REGISTRY.get(call.<ResourceLocation>getArgument(0)));
        previousIgnoreCards = NEConfig.largeWorkstationIgnoreUpgradeCards;
        NEConfig.largeWorkstationIgnoreUpgradeCards = false;
    }

    @AfterEach void cleanup() {
        NEConfig.largeWorkstationIgnoreUpgradeCards = previousIgnoreCards;
        internal.close();
        types.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"data_reassembler", "data_charger", "data_integrated_charger", "data_charge_press"})
    void releasedRecipeCodecsAreDiscoveredAndLockedToTheDataCard(String name) throws ReflectiveOperationException {
        String serializerName = switch (name) {
            case "data_reassembler" -> "reassembler.DataRipperReassemblerRecipeSerializer";
            case "data_charger" -> "charger.DataChargerRecipeSerializer";
            case "data_integrated_charger" -> "charger.DataIntegratedChargerRecipeSerializer";
            default -> "chargepress.DataChargePressRecipeSerializer";
        };
        var serializer = (RecipeSerializer<?>) Class.forName(
            "com.fish_dan_.data_energistics.recipe." + serializerName).getConstructor().newInstance();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Recipe<?> source = spy(serializer.codec().codec().parse(ops, fixture(name)).getOrThrow());
        doReturn(RecipeType.simple(ResourceLocation.parse(PREFIX + name))).when(source).getType();
        doReturn(serializer).when(source).getSerializer();
        var manager = mock(RecipeManager.class);
        var id = ResourceLocation.parse(PREFIX + "test_" + name);
        when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(id, source)));

        assertTrue(LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR).isEmpty());
        assertTrue(LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.LIGHTNING_TECH).isEmpty());
        var available = LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.DATA_ENERGISTICS);
        assertEquals(1, available.size());
        var recipe = available.getFirst();
        assertEquals(ResourceLocation.parse(PREFIX + name), recipe.sourceType());
        assertSame(recipe, LargeWorkstationRecipes.getById(manager, registries, id));
        assertEquals(name.equals("data_reassembler") ? 0 : 1600, recipe.energy());
        if (name.equals("data_charger")) {
            assertEquals(List.of(new GenericStack(LargeWorkstationTestKeys.DATA_FLOW, 100)), recipe.extraInputs());
        }
        if (name.equals("data_charge_press")) {
            assertEquals(250, recipe.display().inputFluid().amount());
            assertEquals(72, recipe.display().itemOutput().getCount());
            assertEquals(ResourceLocation.parse(PREFIX + "data_corrosion_liquid"),
                BuiltInRegistries.FLUID.getKey(recipe.display().inputFluid().ingredient().getStacks()[0].getFluid()));
        }
        when(manager.getRecipes()).thenReturn(List.of());
        assertNull(LargeWorkstationRecipes.getById(manager, registries, id));
        assertTrue(LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.DATA_ENERGISTICS).isEmpty());
    }

    @Test void dataFlowCostsScaleExactlyAndCanBeAcquiredFromMe() {
        var data = fixture("data_charger");
        data.addProperty("power", 1600.25);
        var recipe = decode("data_charger", data);
        assertEquals(1601, recipe.energy());
        KeyCounter inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.SNOWBALL), 2);
        KeyCounter outputs = new KeyCounter();
        outputs.add(AEItemKey.of(Items.ENDER_PEARL), 2);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
        inputs.add(LargeWorkstationTestKeys.DATA_FLOW, 199);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
        inputs.add(LargeWorkstationTestKeys.DATA_FLOW, 1);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
    }

    @Test void reassemblyPreservesWrappedIngredientsFluidsComponentsAndCustomResources() {
        var data = fixture("data_reassembler");
        var item = data.getAsJsonObject("inputs").getAsJsonArray("items").get(0).getAsJsonObject();
        item.remove("item");
        item.add("ingredient", JsonParser.parseString("[{\"item\":\"minecraft:iron_ingot\"},{\"item\":\"minecraft:gold_ingot\"}]"));
        data.getAsJsonObject("inputs").add("resource", JsonParser.parseString("""
            {"key_type":"data_energistics:digitalization","resource":"data_energistics:data_flow","amount":9876543210}
            """));
        var recipe = decode("data_reassembler", data);
        assertEquals(4, recipe.display().inputItems().getFirst().count());
        assertTrue(recipe.display().inputItems().getFirst().ingredient().test(new ItemStack(Items.GOLD_INGOT)));
        assertEquals(250, recipe.display().inputFluid().amount());
        assertEquals(125, recipe.display().fluidOutput().getAmount());
        assertEquals(List.of(new GenericStack(LargeWorkstationTestKeys.DATA_FLOW, 9876543210L)), recipe.extraInputs());
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.GOLD_INGOT), 4);
        inputs.add(AEFluidKey.of(Fluids.WATER), 250);
        var outputs = new KeyCounter();
        outputs.add(AEItemKey.of(Items.DIAMOND), 2);
        outputs.add(AEFluidKey.of(Fluids.LAVA), 125);
        assertTrue(recipe.matches(inputs, outputs));
        inputs.remove(AEFluidKey.of(Fluids.WATER), 1);
        assertFalse(recipe.matches(inputs, outputs));
    }

    @Test void differentCustomResourceCostsAreNeverDeduplicatedAsTheSameMaterialContract() {
        var first = decode("data_charger", fixture("data_charger"));
        var data = fixture("data_charger");
        data.addProperty("data_flow", 200);
        var second = decode("data_charger", data);
        var nativeRecipe = new LargeWorkstationRecipe(ResourceLocation.parse("test:native"), first.display(), 1600, List.of());
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        assertEquals(3, LargeWorkstationRecipes.deduplicate(List.of(first, second, nativeRecipe), ops).size());
    }

    @Test void manifestBinaryInputUsesItsOwnKeyAndSurvivesGenericStackPersistence() {
        var data = fixture("data_reassembler");
        data.getAsJsonObject("inputs").add("resource", JsonParser.parseString("""
            {"key_type":"data_energistics:manifest_binary","amount":67}
            """));
        var recipe = decode("data_reassembler", data);
        var extra = recipe.extraInputs().getFirst();
        assertEquals(new GenericStack(LargeWorkstationTestKeys.MANIFEST_DATA, 67), extra);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        assertEquals(extra, GenericStack.readTag(registries, GenericStack.writeTag(registries, extra)));
        var flow = decode("data_charger", fixture("data_charger")).extraInputs().getFirst();
        assertEquals(flow, GenericStack.readTag(registries, GenericStack.writeTag(registries, flow)));
    }

    @ParameterizedTest @ValueSource(strings = {"resource", "modifier"})
    void invalidResourcesAndDynamicOutputSemanticsFailClosed(String kind) {
        var data = fixture("data_reassembler");
        switch (kind) {
            case "resource" -> data.getAsJsonObject("outputs").add("resource", JsonParser.parseString("""
                {"key_type":"data_energistics:digitalization","resource":"data_energistics:data_flow","amount":0}
                """));
            default -> data.getAsJsonObject("outputs").getAsJsonArray("items").get(0).getAsJsonObject()
                .addProperty("modifier", "data_energistics:assign_quantum_frequency");
        }
        assertThrows(IllegalArgumentException.class, () -> decode("data_reassembler", data));
    }

    @Test void multipleFluidsAndOutputsKeepTheirEntireScaledContract() {
        var data = fixture("data_reassembler");
        data.getAsJsonObject("inputs").getAsJsonArray("fluids")
            .add(JsonParser.parseString("{\"fluid\":\"minecraft:lava\",\"amount\":100}"));
        data.getAsJsonObject("outputs").getAsJsonArray("fluids")
            .add(JsonParser.parseString("{\"fluid\":\"minecraft:water\",\"amount\":50}"));
        data.getAsJsonObject("outputs").getAsJsonArray("items")
            .add(JsonParser.parseString("{\"id\":\"minecraft:emerald\",\"count\":3}"));
        data.getAsJsonObject("outputs").add("resource", JsonParser.parseString("""
            {"key_type":"data_energistics:digitalization","resource":"data_energistics:data_flow","amount":67}
            """));
        var recipe = decode("data_reassembler", data);
        assertEquals(2, recipe.inputFluids().size());
        assertEquals(5, recipe.outputs().size());
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.IRON_INGOT), 8);
        inputs.add(AEFluidKey.of(Fluids.WATER), 500);
        inputs.add(AEFluidKey.of(Fluids.LAVA), 200);
        var outputs = new KeyCounter();
        outputs.add(AEItemKey.of(Items.DIAMOND), 4);
        outputs.add(AEItemKey.of(Items.EMERALD), 6);
        outputs.add(AEFluidKey.of(Fluids.LAVA), 250);
        outputs.add(AEFluidKey.of(Fluids.WATER), 100);
        outputs.add(LargeWorkstationTestKeys.DATA_FLOW, 134);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
        inputs.remove(AEFluidKey.of(Fluids.LAVA), 200);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs), "Material fluids cannot be omitted as ME-only extras");
        inputs.add(AEFluidKey.of(Fluids.WATER), 200);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs), "A matching total amount cannot replace a different fluid");
        inputs.remove(AEFluidKey.of(Fluids.WATER), 200);
        inputs.add(AEFluidKey.of(Fluids.LAVA), 200);
        outputs.remove(AEItemKey.of(Items.EMERALD), 6);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
    }

    @Test void splitAndReorderedFluidEntriesDeduplicateButDifferentFluidsAndByproductsRemainDistinct() {
        var firstData = fixture("data_reassembler");
        firstData.getAsJsonObject("inputs").getAsJsonArray("fluids")
            .add(JsonParser.parseString("{\"fluid\":\"minecraft:lava\",\"amount\":100}"));
        var reordered = firstData.deepCopy();
        reordered.getAsJsonObject("inputs").add("fluids", JsonParser.parseString("""
            [{"fluid":"minecraft:lava","amount":100},{"fluid":"minecraft:water","amount":125},
             {"fluid":"minecraft:water","amount":125}]
            """));
        var withByproduct = firstData.deepCopy();
        withByproduct.getAsJsonObject("outputs").getAsJsonArray("items")
            .add(JsonParser.parseString("{\"id\":\"minecraft:emerald\"}"));
        var moreFluid = firstData.deepCopy();
        moreFluid.getAsJsonObject("inputs").getAsJsonArray("fluids").get(1).getAsJsonObject().addProperty("amount", 101);
        var first = decode("data_reassembler", firstData);
        var equivalent = decode("data_reassembler", reordered);
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        assertEquals(1, LargeWorkstationRecipes.deduplicate(List.of(first, equivalent), ops).size());
        assertEquals(3, LargeWorkstationRecipes.deduplicate(List.of(first, decode("data_reassembler", withByproduct),
            decode("data_reassembler", moreFluid)), ops).size());
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.IRON_INGOT), 4);
        inputs.add(AEFluidKey.of(Fluids.WATER), 250);
        inputs.add(AEFluidKey.of(Fluids.LAVA), 100);
        assertTrue(equivalent.matchesInputs(inputs));
    }

    @ParameterizedTest @ValueSource(strings = {"data_flow", "power"})
    void invalidChargingCostsAreRejected(String field) {
        var data = fixture("data_charger");
        data.addProperty(field, 0);
        assertThrows(IllegalArgumentException.class, () -> decode("data_charger", data));
    }

    private static LargeWorkstationRecipe decode(String name, JsonObject data) {
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        return LargeWorkstationRecipes.decode(ResourceLocation.parse("test:" + name), PREFIX + name, data, ops);
    }

    private static JsonObject fixture(String name) {
        String json = switch (name) {
            // Same schemas as the released mod; vanilla items keep the registry independent of mod loading.
            case "data_reassembler" -> """
                {"inputs":{"items":[{"item":"minecraft:iron_ingot","count":4}],
                  "fluids":[{"fluid":"minecraft:water","amount":250}]},
                 "outputs":{"items":[{"id":"minecraft:diamond","count":2}],
                  "fluids":[{"fluid":"minecraft:lava","amount":125}]}}
                """;
            case "data_charger" -> """
                {"ingredient":{"item":"minecraft:snowball"},"data_flow":100,"power":1600.0,
                 "result":{"id":"minecraft:ender_pearl","count":1}}
                """;
            case "data_integrated_charger" -> """
                {"inputs":[{"item":"minecraft:iron_ingot","count":12},{"item":"minecraft:redstone","count":6},
                  {"item":"minecraft:gold_ingot"}],"result":{"id":"minecraft:diamond"}}
                """;
            default -> """
                {"inputs":[{"item":"minecraft:amethyst_shard","count":16},{"item":"minecraft:redstone","count":16}],
                 "fluid_amount":250,"result":{"id":"minecraft:amethyst_shard","count":72}}
                """;
        };
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
