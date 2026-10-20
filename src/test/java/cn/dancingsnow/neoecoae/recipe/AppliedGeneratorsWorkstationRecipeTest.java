package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.jar.JarFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppliedGeneratorsWorkstationRecipeTest {
    private static final ResourceLocation TYPE = ResourceLocation.parse("appgen:synthesizing");
    private MockedStatic<AEKeyTypes> types;
    private MockedStatic<AEKeyTypesInternal> internal;
    private boolean previousIgnoreCards;
    private Object previousMissingContentItem;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @BeforeEach void setup() throws ReflectiveOperationException {
        types = mockStatic(AEKeyTypes.class);
        internal = mockStatic(AEKeyTypesInternal.class);
        internal.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
        types.when(() -> AEKeyTypes.get(any(ResourceLocation.class)))
            .thenAnswer(call -> LargeWorkstationTestKeys.REGISTRY.get(call.<ResourceLocation>getArgument(0)));
        previousIgnoreCards = NEConfig.largeWorkstationIgnoreUpgradeCards;
        NEConfig.largeWorkstationIgnoreUpgradeCards = false;
        // AEKey's encode fallback consults this deferred item; isolated tests have no mod item registration.
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        previousMissingContentItem = itemField.get(appeng.core.definitions.AEItems.MISSING_CONTENT);
        var missingItem = mock(net.neoforged.neoforge.registries.DeferredItem.class);
        when(missingItem.get()).thenReturn(Items.BARRIER);
        when(missingItem.asItem()).thenReturn(Items.BARRIER);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, missingItem);
    }

    @AfterEach void cleanup() throws ReflectiveOperationException {
        NEConfig.largeWorkstationIgnoreUpgradeCards = previousIgnoreCards;
        internal.close();
        types.close();
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, previousMissingContentItem);
    }

    @Test void released030CodecIsDiscoveredAndLockedToTheAppliedGeneratorsCard() throws ReflectiveOperationException {
        var serializer = (RecipeSerializer<?>) Class.forName(
            "io.github.sapporo1101.appgen.recipe.GenesisSynthesizerRecipeSerializer").getField("INSTANCE").get(null);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Recipe<?> source = serializer.codec().codec().parse(ops, fixture(true)).getOrThrow();
        var manager = mock(RecipeManager.class);
        var id = ResourceLocation.parse("appgen:test_stack_smelting_card");
        when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(id, source)));
        for (var module : LargeWorkstationModule.values()) {
            if (module != LargeWorkstationModule.APPLIED_GENERATORS) {
                assertTrue(LargeWorkstationRecipes.getAvailable(manager, registries, module).isEmpty(), module.name());
            }
        }
        var available = LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.APPLIED_GENERATORS);
        assertEquals(1, available.size());
        var recipe = available.getFirst();
        assertEquals(TYPE, recipe.sourceType());
        assertEquals(500_000, recipe.energy());
        assertEquals(List.of(200, 1, 1), recipe.display().inputItems().stream().map(input -> input.count()).toList());
        assertEquals(5000, recipe.display().inputFluid().amount());
        assertSame(recipe, LargeWorkstationRecipes.getById(manager, registries, id));
        NEConfig.largeWorkstationIgnoreUpgradeCards = true;
        assertEquals(available, LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR));
        when(manager.getRecipes()).thenReturn(List.of());
        assertNull(LargeWorkstationRecipes.getById(manager, registries, id));
    }

    @Test void everySynthesizerRecipeInTheReleasedJarPreservesItsCompleteContract() throws Exception {
        var serializerClass = Class.forName("io.github.sapporo1101.appgen.recipe.GenesisSynthesizerRecipeSerializer");
        var serializer = (RecipeSerializer<?>) serializerClass.getField("INSTANCE").get(null);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var standIns = List.of(Items.BLAZE_POWDER, Items.PAPER, Items.NETHERITE_BLOCK, Items.GOLD_INGOT);
        var jarPath = Path.of(serializerClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var jar = new JarFile(jarPath.toFile())) {
            var entries = jar.stream().filter(entry -> entry.getName().startsWith("data/appgen/recipe/synthesizer/")
                && entry.getName().endsWith(".json")).toList();
            assertEquals(22, entries.size());
            for (var entry : entries) {
                JsonObject data;
                try (var stream = jar.getInputStream(entry)) {
                    data = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                }
                var inputs = new KeyCounter();
                var expectedCounts = new java.util.ArrayList<Integer>();
                var itemInputs = data.getAsJsonArray("item_inputs");
                for (int i = 0; i < itemInputs.size(); i++) {
                    var input = itemInputs.get(i).getAsJsonObject();
                    var ingredient = new JsonObject();
                    ingredient.addProperty("item", BuiltInRegistries.ITEM.getKey(standIns.get(i)).toString());
                    input.add("ingredient", ingredient);
                    int count = input.has("amount") ? input.get("amount").getAsInt() : 1;
                    expectedCounts.add(count);
                    inputs.add(AEItemKey.of(standIns.get(i)), count);
                }
                int expectedFluid = 0;
                if (data.has("fluid_input")) {
                    var fluid = data.getAsJsonObject("fluid_input");
                    expectedFluid = fluid.has("amount") ? fluid.get("amount").getAsInt() : 1;
                    inputs.add(AEFluidKey.of(BuiltInRegistries.FLUID.get(ResourceLocation.parse(
                        fluid.getAsJsonObject("ingredient").get("fluid").getAsString()))), expectedFluid);
                }
                var output = data.getAsJsonObject("output");
                boolean fluidOutput = output.get("#t").getAsString().equals("ae2:f");
                if (!fluidOutput) output.addProperty("id", "minecraft:diamond");
                int outputCount = output.get("#").getAsInt();
                var outputs = new KeyCounter();
                outputs.add(fluidOutput ? AEFluidKey.of(Fluids.LAVA) : AEItemKey.of(Items.DIAMOND), outputCount);
                Recipe<?> source = serializer.codec().codec().parse(ops, data).getOrThrow();
                var manager = mock(RecipeManager.class);
                when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(ResourceLocation.parse("appgen:test_release"), source)));
                var adapted = LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.APPLIED_GENERATORS);
                assertEquals(1, adapted.size(), entry.getName());
                var recipe = adapted.getFirst();
                assertEquals(data.get("energy_input").getAsLong(), recipe.energy(), entry.getName());
                assertEquals(expectedCounts, recipe.display().inputItems().stream().map(input -> input.count()).toList());
                if (expectedFluid == 0) assertTrue(recipe.display().inputFluid().ingredient().isEmpty());
                else assertEquals(expectedFluid, recipe.display().inputFluid().amount());
                assertTrue(recipe.matches(inputs, outputs), entry.getName());
                var doubledInputs = new KeyCounter();
                for (var input : inputs) doubledInputs.add(input.getKey(), input.getLongValue() * 2);
                var doubledOutputs = new KeyCounter();
                for (var result : outputs) doubledOutputs.add(result.getKey(), result.getLongValue() * 2);
                assertEquals(2, recipe.matchingMultiplier(doubledInputs, doubledOutputs), entry.getName());
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void bothFormatsRequireAllChargedCrystalsAndFluidAtEveryPatternScale(boolean modern) {
        var recipe = decode(fixture(modern));
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.PAPER), 1);
        inputs.add(AEItemKey.of(Items.NETHERITE_BLOCK), 1);
        inputs.add(AEFluidKey.of(Fluids.LAVA), 5000);
        var outputs = new KeyCounter();
        outputs.add(AEItemKey.of(Items.DIAMOND), 1);
        assertFalse(recipe.matches(inputs, outputs));
        inputs.add(AEItemKey.of(Items.BLAZE_POWDER), 200);
        assertTrue(recipe.matches(inputs, outputs));
        inputs.add(AEItemKey.of(Items.PAPER), 1);
        inputs.add(AEItemKey.of(Items.NETHERITE_BLOCK), 1);
        outputs.add(AEItemKey.of(Items.DIAMOND), 1);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
        inputs.add(AEItemKey.of(Items.BLAZE_POWDER), 200);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
        inputs.add(AEFluidKey.of(Fluids.LAVA), 5000);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
    }

    @Test void modernFormatSupportsDryRecipesFluidOutputsDefaultAmountsAndLongEnergy() throws ReflectiveOperationException {
        var data = fixture(true);
        data.remove("fluid_input");
        data.getAsJsonArray("item_inputs").get(0).getAsJsonObject().remove("amount");
        data.addProperty("energy_input", 4_000_000_000L);
        data.add("output", JsonParser.parseString("{\"#t\":\"ae2:f\",\"id\":\"minecraft:lava\",\"#\":1000}"));
        var serializer = (RecipeSerializer<?>) Class.forName(
            "io.github.sapporo1101.appgen.recipe.GenesisSynthesizerRecipeSerializer").getField("INSTANCE").get(null);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var source = serializer.codec().codec().parse(registries.createSerializationContext(JsonOps.INSTANCE), data).getOrThrow();
        assertNotNull(source);
        var recipe = decode(data);
        assertTrue(recipe.display().inputFluid().ingredient().isEmpty());
        assertEquals(1, recipe.display().inputItems().getFirst().count());
        assertEquals(1000, recipe.display().fluidOutput().getAmount());
        assertEquals(4_000_000_000L, recipe.energy());
        assertEquals(Integer.MAX_VALUE, recipe.display().energy());
    }

    @ParameterizedTest
    @ValueSource(strings = {"zero_item", "negative_item", "overflow_item", "fractional_item", "zero_fluid",
        "negative_energy", "overflow_energy", "overflow_output", "empty_input", "too_many_inputs", "mixed_schema"})
    void malformedRecipesCannotLoseCostsOrTruncateQuantities(String problem) {
        var data = fixture(true);
        var input = data.getAsJsonArray("item_inputs").get(0).getAsJsonObject();
        switch (problem) {
            case "zero_item" -> input.addProperty("amount", 0);
            case "negative_item" -> input.addProperty("amount", -1);
            case "overflow_item" -> input.addProperty("amount", 4_000_000_000L);
            case "fractional_item" -> input.addProperty("amount", 1.5);
            case "zero_fluid" -> data.getAsJsonObject("fluid_input").addProperty("amount", 0);
            case "negative_energy" -> data.addProperty("energy_input", -1);
            case "overflow_energy" -> data.addProperty("energy_input", new java.math.BigInteger("9223372036854775808"));
            case "overflow_output" -> data.getAsJsonObject("output").addProperty("#", 4_000_000_000L);
            case "empty_input" -> {
                data.getAsJsonArray("item_inputs").asList().clear();
                data.remove("fluid_input");
            }
            case "too_many_inputs" -> {
                var inputs = data.getAsJsonArray("item_inputs");
                while (inputs.size() <= LargeIntegratedWorkingStationRecipe.MAX_ITEM_INPUTS) inputs.add(input.deepCopy());
            }
            case "mixed_schema" -> data.add("input_energy", data.remove("energy_input"));
            default -> fail(problem);
        }
        assertThrows(RuntimeException.class, () -> decode(data));
    }

    private static LargeWorkstationRecipe decode(JsonObject data) {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return LargeWorkstationRecipes.decode(ResourceLocation.parse("test:synthesizing"), TYPE.toString(),
            data, registries.createSerializationContext(JsonOps.INSTANCE));
    }

    private static JsonObject fixture(boolean modern) {
        // Vanilla stand-ins retain the screenshot's 200 charged crystals and 5 buckets of lava.
        String json = """
            {"item_inputs":[{"ingredient":{"item":"minecraft:blaze_powder"},"amount":200},
                {"ingredient":{"item":"minecraft:paper"}},
                {"ingredient":{"item":"minecraft:netherite_block"}}],
             "fluid_input":{"ingredient":{"fluid":"minecraft:lava"},"amount":5000},
             "energy_input":500000,"output":{"#t":"ae2:i","id":"minecraft:diamond","#":1}}
            """;
        if (!modern) json = json.replace("item_inputs", "input_items")
            .replace("fluid_input", "input_fluid").replace("energy_input", "input_energy");
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
