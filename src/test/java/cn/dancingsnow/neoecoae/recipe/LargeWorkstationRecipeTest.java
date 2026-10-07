package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationRecipeTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private boolean previousCompatEnabled;

    @BeforeEach void saveConfig() {
        previousCompatEnabled = NEConfig.largeWorkstationCompatRecipesEnabled;
    }

    @AfterEach void restoreConfig() {
        NEConfig.largeWorkstationCompatRecipesEnabled = previousCompatEnabled;
    }

    @Test void overlappingTagsCanReassignEarlierAllocations() {
        assertTrue(LargeWorkstationRecipe.matchesQuantities(new long[]{5, 5}, new long[]{5, 5},
            new boolean[][]{{true, true}, {true, false}}));
        assertFalse(LargeWorkstationRecipe.matchesQuantities(new long[]{5, 5}, new long[]{5, 5},
            new boolean[][]{{true, true}, {false, false}}));
    }

    @Test void hugeCountsStayCompactAndRejectSurplus() {
        assertTrue(LargeWorkstationRecipe.matchesQuantities(new long[]{Integer.MAX_VALUE},
            new long[]{Integer.MAX_VALUE}, new boolean[][]{{true}}));
        assertFalse(LargeWorkstationRecipe.matchesQuantities(new long[]{6}, new long[]{5}, new boolean[][]{{true}}));
    }

    @Test void fullOutputContractSelectsBetweenIdenticalInputs() {
        var recipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        KeyCounter inputs = items(Items.IRON_INGOT, 4);
        assertTrue(recipe.matches(inputs, items(Items.DIAMOND, 2)));
        assertFalse(recipe.matches(inputs, items(Items.DIAMOND, 1)));
        assertFalse(recipe.matches(inputs, items(Items.GOLD_INGOT, 2)));
        KeyCounter surplus = items(Items.DIAMOND, 2);
        surplus.add(AEItemKey.of(Items.STONE), 1);
        assertFalse(recipe.matches(inputs, surplus));
        assertFalse(recipe.matches(items(Items.IRON_INGOT, 5), items(Items.DIAMOND, 2)));
    }

    @Test void exactLightningCanBeSuppliedOrAcquiredButWrongTierIsRejected() {
        var high = com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE;
        var extreme = com.moakiee.ae2lt.me.key.LightningKey.EXTREME_HIGH_VOLTAGE;
        var base = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        var recipe = new LargeWorkstationRecipe(base.id(), base.display(), base.energy(), List.of(new GenericStack(extreme, 4)));
        var input = items(Items.IRON_INGOT, 4);
        assertTrue(recipe.matchesInputs(input));
        input.add(high, 4);
        assertFalse(recipe.matchesInputs(input));
        input.remove(high, 4);
        input.add(extreme, 3);
        assertFalse(recipe.matchesInputs(input));
        input.add(extreme, 1);
        assertTrue(recipe.matchesInputs(input));
    }

    @Test void scaledItemAndFluidOutputsMustShareTheSameIntegerMultiplier() {
        var recipe = decode("ae2cs:crystal_aggregator_recipe", """
            {"input_a":{"item":"minecraft:iron_ingot","count":128},
             "result":{"id":"minecraft:diamond","count":8},"energy_cost":12345,
             "fluid_input":{"fluid":"minecraft:water","amount":1000},
             "fluid_output":{"id":"minecraft:lava","amount":250}}
            """);
        var inputs = items(Items.IRON_INGOT, 256);
        inputs.add(AEFluidKey.of(Fluids.WATER), 2000);
        var outputs = items(Items.DIAMOND, 16);
        outputs.add(AEFluidKey.of(Fluids.LAVA), 500);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
        outputs.remove(AEFluidKey.of(Fluids.LAVA), 250);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
        outputs.add(AEFluidKey.of(Fluids.LAVA), 250);
        inputs.remove(AEFluidKey.of(Fluids.WATER), 1);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
        inputs.add(AEFluidKey.of(Fluids.WATER), 1);
        outputs.add(AEItemKey.of(Items.STONE), 1);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
    }

    @Test void fractionalOutputsOrDifferentInputScaleAreRejected() {
        var recipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        assertEquals(0, recipe.matchingMultiplier(items(Items.IRON_INGOT, 6), items(Items.DIAMOND, 3)));
        assertEquals(0, recipe.matchingMultiplier(items(Items.IRON_INGOT, 4), items(Items.DIAMOND, 4)));
        assertEquals(0, recipe.matchingMultiplier(items(Items.IRON_INGOT, 9), items(Items.DIAMOND, 4)));
    }

    @Test void scaledOverlappingIngredientsAllowDifferentConcreteItemsPerRecipeOperation() {
        var recipe = new LargeWorkstationRecipe(ResourceLocation.parse("test:mixed"), new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 4),
                new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.DIAMOND),
            FluidStack.EMPTY, 100), 100, List.of());
        var inputs = items(Items.IRON_INGOT, 3);
        inputs.add(AEItemKey.of(Items.GOLD_INGOT), 7);
        assertEquals(2, recipe.matchingMultiplier(inputs, items(Items.DIAMOND, 2)));
        inputs.remove(AEItemKey.of(Items.IRON_INGOT), 2);
        inputs.add(AEItemKey.of(Items.GOLD_INGOT), 2);
        assertEquals(0, recipe.matchingMultiplier(inputs, items(Items.DIAMOND, 2)));
    }

    @Test void scaledLightningMustBeOmittedOrFullySuppliedAtTheRequiredTier() {
        var base = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        var lightning = com.moakiee.ae2lt.me.key.LightningKey.EXTREME_HIGH_VOLTAGE;
        var wrongTier = com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE;
        var recipe = new LargeWorkstationRecipe(base.id(), base.display(), base.energy(),
            List.of(new GenericStack(lightning, 4)));
        var inputs = items(Items.IRON_INGOT, 8);
        var outputs = items(Items.DIAMOND, 4);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
        inputs.add(lightning, 7);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
        inputs.add(lightning, 1);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
        inputs.remove(lightning, 8);
        inputs.add(wrongTier, 8);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
    }

    @ParameterizedTest
    @ValueSource(strings={"ae2lt:overload_processing", "ae2lt:lightning_assembly", "ae2lt:lightning_simulation"})
    void rebornRecipesKeepLightningTierCountsAndLongEnergy(String type) {
        String output = type.endsWith("overload_processing")
            ? "\"results\":[{\"id\":\"minecraft:diamond\",\"count\":2}],\"inputFluid\":{\"id\":\"minecraft:water\",\"amount\":1000}"
            : "\"result\":{\"id\":\"minecraft:diamond\",\"count\":2}";
        var recipe = decode(type, """
            {"inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":4}],
             "totalEnergy":10000000000,"lightningCost":7,"lightningTier":"extreme_high_voltage",
            """ + output + "}");
        assertTrue(recipe.energy() > Integer.MAX_VALUE);
        assertEquals(7, recipe.extraInputs().getFirst().amount());
        assertEquals(com.moakiee.ae2lt.me.key.LightningKey.EXTREME_HIGH_VOLTAGE, recipe.extraInputs().getFirst().what());
        assertEquals(4, recipe.display().inputItems().getFirst().count());
        if (type.endsWith("overload_processing")) assertEquals(1000, recipe.display().inputFluid().amount());
    }

    @Test void rebornDefaultLightningCostIsNotLostWhenCodecOmitsDefaults() {
        var recipe = decode("ae2lt:lightning_assembly", """
            {"inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":1}],
             "totalEnergy":1000,"result":{"id":"minecraft:diamond"}}
            """);
        assertEquals(4, recipe.extraInputs().getFirst().amount());
        assertEquals(com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE, recipe.extraInputs().getFirst().what());
    }

    @ParameterizedTest @ValueSource(strings={"ae2cs:circuit_etcher_recipe", "ae2cs:crystal_aggregator_recipe"})
    void crystalScienceSupportsSizedIngredientsAndFluids(String type) {
        var recipe = decode(type, """
            {"input_a":{"item":"minecraft:iron_ingot","count":128},
             "result":{"id":"minecraft:diamond","count":8},"energy_cost":12345,
             "fluid_input":{"fluid":"minecraft:water","amount":1000},
             "fluid_output":{"id":"minecraft:lava","amount":250}}
            """);
        assertEquals(128, recipe.display().inputItems().getFirst().count());
        assertEquals(12345, recipe.energy());
        var inputs = items(Items.IRON_INGOT, 128);
        inputs.add(AEFluidKey.of(Fluids.WATER), 1000);
        var outputs = items(Items.DIAMOND, 8);
        outputs.add(AEFluidKey.of(Fluids.LAVA), 250);
        assertTrue(recipe.matches(inputs, outputs));
        outputs.remove(AEFluidKey.of(Fluids.LAVA), 1);
        assertFalse(recipe.matches(inputs, outputs));
    }

    @ParameterizedTest @ValueSource(strings={"appgen:synthesizing", "advanced_ae:reaction"})
    void genericOutputsAndIngredientStackQuantitiesSurvive(String type) {
        try (var keyTypes = mockStatic(AEKeyTypes.class);
             var internal = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class)) {
            internal.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
                .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
            keyTypes.when(() -> AEKeyTypes.get(ResourceLocation.parse("ae2:i"))).thenReturn(AEKeyType.items());
            keyTypes.when(() -> AEKeyTypes.get(ResourceLocation.parse("ae2:f"))).thenReturn(AEKeyType.fluids());
            var recipe = decode(type, """
                {"input_items":[{"ingredient":{"item":"minecraft:iron_ingot"},"amount":64}],
                 "input_fluid":{"ingredient":{"fluid":"minecraft:water"},"amount":500},
                 "input_energy":1300000,"output":{"#t":"ae2:f","id":"minecraft:lava","#":1000}}
                """);
            assertEquals(64, recipe.display().inputItems().getFirst().count());
            assertEquals(500, recipe.display().inputFluid().amount());
            assertEquals(1000, recipe.display().fluidOutput().getAmount());
            assertEquals(1300000, recipe.energy());
        }
    }

    @ParameterizedTest @ValueSource(strings={"extendedae_plus:crystal_assembler_plus", "extendedae:crystal_assembler"})
    void superAssemblerIncludesInheritedRecipeFormat(String type) {
        var recipe = decode(type, """
            {"input_items":[{"ingredient":{"item":"minecraft:iron_ingot"},"amount":256}],
             "output":{"id":"minecraft:diamond","count":64}}
            """);
        assertEquals(256, recipe.display().inputItems().getFirst().count());
        assertEquals(2000, recipe.energy());
        assertTrue(recipe.display().inputFluid().ingredient().isEmpty());
    }

    private static LargeWorkstationRecipe decode(String type, String data) {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var json = JsonParser.parseString(data).getAsJsonObject();
        json = switch (type) {
            case "ae2lt:overload_processing" -> roundTrip(
                new com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe.Serializer().codec().codec(), ops, json);
            case "ae2lt:lightning_assembly" -> roundTrip(
                new com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe.Serializer().codec().codec(), ops, json);
            case "ae2lt:lightning_simulation" -> roundTrip(
                new com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe.Serializer().codec().codec(), ops, json);
            default -> json;
        };
        return LargeWorkstationRecipes.decode(ResourceLocation.parse("test:recipe"), type,
            json, ops);
    }

    private static <T> com.google.gson.JsonObject roundTrip(com.mojang.serialization.Codec<T> codec,
        com.mojang.serialization.DynamicOps<com.google.gson.JsonElement> ops, com.google.gson.JsonObject json) {
        return codec.encodeStart(ops, codec.parse(ops, json).getOrThrow()).getOrThrow().getAsJsonObject();
    }

    @Test void actualGlodiumCodecOmitsDefaultAmounts() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var input = com.glodblock.github.glodium.recipe.stack.IngredientStack.ITEM_CODEC.encodeStart(ops,
            com.glodblock.github.glodium.recipe.stack.IngredientStack.of(new ItemStack(Items.IRON_INGOT))).getOrThrow();
        var fluid = com.glodblock.github.glodium.recipe.stack.IngredientStack.FLUID_CODEC.encodeStart(ops,
            com.glodblock.github.glodium.recipe.stack.IngredientStack.of(new FluidStack(Fluids.WATER, 1))).getOrThrow();
        var recipe = decode("extendedae_plus:crystal_assembler_plus", "{\"input_items\":[" + input
            + "],\"input_fluid\":" + fluid + ",\"output\":{\"id\":\"minecraft:diamond\"}}");
        assertEquals(1, recipe.display().inputItems().getFirst().count());
        assertEquals(1, recipe.display().inputFluid().amount());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ae2lt:overload_processing", "ae2lt:lightning_assembly", "ae2lt:lightning_simulation",
        "ae2cs:circuit_etcher_recipe", "ae2cs:crystal_aggregator_recipe", "appgen:synthesizing",
        "advanced_ae:reaction", "extendedae_plus:crystal_assembler_plus", "extendedae:crystal_assembler"
    })
    void optionalRecipesAreExcludedWithoutReadingTheirSerializers(String typeId) {
        var type = net.minecraft.world.item.crafting.RecipeType.simple(ResourceLocation.parse(typeId));
        var recipe = mock(net.minecraft.world.item.crafting.Recipe.class);
        doReturn(type).when(recipe).getType();
        var holder = new net.minecraft.world.item.crafting.RecipeHolder<>(ResourceLocation.parse("test:unregistered"), recipe);
        var nativeRecipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        var nativeHolder = new net.minecraft.world.item.crafting.RecipeHolder<>(nativeRecipe.id(), nativeRecipe.display());
        var manager = mock(net.minecraft.world.item.crafting.RecipeManager.class);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        when(manager.getRecipes()).thenReturn(List.of(holder, nativeHolder));
        var recipes = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(1, recipes.size());
        assertEquals(nativeRecipe, recipes.getFirst());
        verify(recipe, never()).getType();
        verify(recipe, never()).getSerializer();
    }

    @Test void enablingCompatRecipesAdaptsRealCodecAndSwitchingInvalidatesCache() {
        assertFalse(NEConfig.largeWorkstationCompatRecipesEnabled);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var serializer = new com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe.Serializer();
        var optional = spy(serializer.codec().codec().parse(ops, JsonParser.parseString("""
            {"inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":4}],
             "totalEnergy":1000,"result":{"id":"minecraft:diamond","count":2}}
            """)).getOrThrow());
        doReturn(RecipeType.simple(ResourceLocation.parse("ae2lt:lightning_assembly"))).when(optional).getType();
        doReturn(serializer).when(optional).getSerializer();
        var optionalId = ResourceLocation.parse("test:compat");
        var nativeRecipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(optionalId, optional),
            new RecipeHolder<>(nativeRecipe.id(), nativeRecipe.display())));

        var disabled = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(List.of(nativeRecipe), disabled);
        verify(optional, never()).getSerializer();

        NEConfig.largeWorkstationCompatRecipesEnabled = true;
        var enabled = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(1, enabled.size());
        assertFalse(enabled.contains(nativeRecipe));
        var adapted = enabled.stream().filter(recipe -> recipe.id().equals(optionalId)).findFirst().orElseThrow();
        assertTrue(adapted.matches(items(Items.IRON_INGOT, 4), items(Items.DIAMOND, 2)));
        assertEquals(4, adapted.extraInputs().getFirst().amount());
        assertEquals(ResourceLocation.parse("ae2lt:lightning_assembly"), adapted.sourceType());
        assertSame(enabled, LargeWorkstationRecipes.getAll(manager, registries));
        assertEquals(nativeRecipe, LargeWorkstationRecipes.getById(manager, registries, nativeRecipe.id()));
        assertSame(adapted, LargeWorkstationRecipes.getById(manager, registries, optionalId));

        NEConfig.largeWorkstationCompatRecipesEnabled = false;
        var disabledAgain = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(List.of(nativeRecipe), disabledAgain);
        assertNotSame(enabled, disabledAgain);
        assertSame(disabledAgain, LargeWorkstationRecipes.getAll(manager, registries));
        assertNull(LargeWorkstationRecipes.getById(manager, registries, optionalId));

        NEConfig.largeWorkstationCompatRecipesEnabled = true;
        var enabledAgain = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(enabled.stream().map(LargeWorkstationRecipe::id).toList(),
            enabledAgain.stream().map(LargeWorkstationRecipe::id).toList());
        var adaptedAgain = enabledAgain.stream().filter(recipe -> recipe.id().equals(optionalId)).findFirst().orElseThrow();
        assertTrue(adaptedAgain.matches(items(Items.IRON_INGOT, 4), items(Items.DIAMOND, 2)));
        assertEquals(adapted.energy(), adaptedAgain.energy());
        assertEquals(adapted.extraInputs(), adaptedAgain.extraInputs());
        verify(optional, times(2)).getSerializer();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unrelatedRecipesNeverReadTheirSerializers(boolean compatEnabled) {
        NEConfig.largeWorkstationCompatRecipesEnabled = compatEnabled;
        var recipe = mock(Recipe.class);
        doReturn(RecipeType.simple(ResourceLocation.parse("test:unrelated"))).when(recipe).getType();
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(ResourceLocation.parse("test:unrelated"), recipe)));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        assertTrue(LargeWorkstationRecipes.getAll(manager, registries).isEmpty());
        verify(recipe, never()).getSerializer();
    }

    @Test void discardedOptionalRecipeRemainsAddressableWithOriginalCostsUntilReloadRemovesIt() {
        NEConfig.largeWorkstationCompatRecipesEnabled = true;
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var serializer = new com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe.Serializer();
        var expensive = spy(serializer.codec().codec().parse(ops, JsonParser.parseString("""
            {"inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":4}],
             "totalEnergy":2000,"lightningCost":7,"result":{"id":"minecraft:diamond"}}
            """)).getOrThrow());
        var cheaper = spy(serializer.codec().codec().parse(ops, JsonParser.parseString("""
            {"inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":4}],
             "totalEnergy":1000,"lightningCost":4,"result":{"id":"minecraft:diamond"}}
            """)).getOrThrow());
        var type = RecipeType.simple(ResourceLocation.parse("ae2lt:lightning_assembly"));
        doReturn(type).when(expensive).getType();
        doReturn(serializer).when(expensive).getSerializer();
        doReturn(type).when(cheaper).getType();
        doReturn(serializer).when(cheaper).getSerializer();
        var expensiveId = ResourceLocation.parse("ae2lt:expensive");
        var cheaperId = ResourceLocation.parse("ae2lt:cheaper");
        var manager = mock(RecipeManager.class);
        var cheaperHolder = new RecipeHolder<>(cheaperId, cheaper);
        when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(expensiveId, expensive), cheaperHolder));

        var listed = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(List.of(cheaperId), listed.stream().map(LargeWorkstationRecipe::id).toList());
        var savedBatchRecipe = LargeWorkstationRecipes.getById(manager, registries, expensiveId);
        assertNotNull(savedBatchRecipe);
        assertTrue(savedBatchRecipe.energy() > listed.getFirst().energy());
        assertEquals(7, savedBatchRecipe.extraInputs().getFirst().amount());
        assertSame(savedBatchRecipe, LargeWorkstationRecipes.getById(manager, registries, expensiveId));

        when(manager.getRecipes()).thenReturn(List.of(cheaperHolder));
        assertNull(LargeWorkstationRecipes.getById(manager, registries, expensiveId));
        assertEquals(List.of(cheaperId), LargeWorkstationRecipes.getAll(manager, registries).stream()
            .map(LargeWorkstationRecipe::id).toList());
    }

    @Test void nativeRecipesAreDiscoveredAndReloadReplacesCache() {
        var recipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        var holder = new net.minecraft.world.item.crafting.RecipeHolder<>(recipe.id(), recipe.display());
        var manager = mock(net.minecraft.world.item.crafting.RecipeManager.class);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        when(manager.getRecipes()).thenReturn(List.of(holder));
        var first = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(1, first.size());
        assertEquals(recipe, first.getFirst());
        assertSame(first, LargeWorkstationRecipes.getAll(manager, registries));
        when(manager.getRecipes()).thenReturn(List.of());
        assertTrue(LargeWorkstationRecipes.getAll(manager, registries).isEmpty());
        assertNull(LargeWorkstationRecipes.getById(manager, registries, recipe.id()));
    }

    private static LargeWorkstationRecipe nativeRecipe(ItemStack output) {
        return new LargeWorkstationRecipe(ResourceLocation.parse("test:recipe"), new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 4)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), output, FluidStack.EMPTY, 100), 100, List.of());
    }

    private static KeyCounter items(net.minecraft.world.item.Item item, long amount) {
        var result = new KeyCounter();
        result.add(AEItemKey.of(item), amount);
        return result;
    }
}
