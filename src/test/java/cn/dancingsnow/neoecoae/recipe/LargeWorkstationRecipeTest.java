package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import net.minecraftforge.fluids.FluidStack;
import cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
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

    private boolean previousIgnoreUpgradeCards;

    @BeforeEach void saveConfig() {
        LargeWorkstationRecipes.configure(() -> NEConfig.largeWorkstationIgnoreUpgradeCards);
        previousIgnoreUpgradeCards = NEConfig.largeWorkstationIgnoreUpgradeCards;
        NEConfig.largeWorkstationIgnoreUpgradeCards = false;
    }

    @AfterEach void restoreConfig() {
        NEConfig.largeWorkstationIgnoreUpgradeCards = previousIgnoreUpgradeCards;
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
        var recipe = new LargeWorkstationRecipe(new ResourceLocation("test:fluids"),
            new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 128)),
                SizedFluidIngredient.of(Fluids.WATER, 1000), new ItemStack(Items.DIAMOND, 8),
                new FluidStack(Fluids.LAVA, 250), 12345), 12345, List.of());
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
        var recipe = new LargeWorkstationRecipe(new ResourceLocation("test:mixed"), new IntegratedWorkingStationRecipe(
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
        assertEquals(0, recipe.energy());
        assertEquals(10_000_000_000L, recipe.feEnergy());
        assertEquals(200, recipe.processingTicks());
        assertEquals(7, cn.dancingsnow.neoecoae.platform.JdkCompat.first(recipe.extraInputs()).amount());
        assertEquals(com.moakiee.ae2lt.me.key.LightningKey.EXTREME_HIGH_VOLTAGE, cn.dancingsnow.neoecoae.platform.JdkCompat.first(recipe.extraInputs()).what());
        assertEquals(4, cn.dancingsnow.neoecoae.platform.JdkCompat.first(recipe.display().inputItems()).count());
        if (type.endsWith("overload_processing")) assertEquals(1000, recipe.display().inputFluid().amount());
    }

    @Test void cappedLightningDurationKeepsEvenTheLargestFeBudgetExecutable() {
        var recipe = decode("ae2lt:lightning_assembly", """
            {"inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":1}],
             "totalEnergy":9223372036854775807,"result":{"id":"minecraft:diamond"}}
            """);
        assertEquals(Long.MAX_VALUE, recipe.feEnergy());
        assertEquals(200, recipe.processingTicks());
    }

    @Test void rebornDefaultLightningCostIsNotLostWhenCodecOmitsDefaults() {
        var recipe = decode("ae2lt:lightning_assembly", """
            {"inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":1}],
             "totalEnergy":1000,"result":{"id":"minecraft:diamond"}}
            """);
        assertEquals(4, cn.dancingsnow.neoecoae.platform.JdkCompat.first(recipe.extraInputs()).amount());
        assertEquals(com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE, cn.dancingsnow.neoecoae.platform.JdkCompat.first(recipe.extraInputs()).what());
    }

    @ParameterizedTest
    @ValueSource(strings={"appgen:synthesizing", "data_energistics:data_reassembler", "extendedae_plus:crystal_assembler_plus"})
    void excludedRecipeFamiliesAreRejected(String type) {
        assertThrows(IllegalArgumentException.class,
            () -> LargeWorkstationRecipeAdapters.adapt(mock(Recipe.class), type));
    }

    @ParameterizedTest @ValueSource(booleans={false, true})
    void advancedAeKeepsGenericOutputsAndIngredientQuantities(boolean fluidOutput) {
        var output = fluidOutput ? new GenericStack(AEFluidKey.of(Fluids.LAVA), 1000)
            : new GenericStack(AEItemKey.of(Items.DIAMOND), 2);
        var source = new net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe(
            new ResourceLocation("test:advanced"), output,
            List.of(new net.pedroksl.ae2addonlib.recipes.IngredientStack.Item(Ingredient.of(Items.IRON_INGOT), 64)),
            new net.pedroksl.ae2addonlib.recipes.IngredientStack.Fluid(Fluids.WATER, 500), 1_300_000);
        var recipe = LargeWorkstationRecipeAdapters.adapt(source, "advanced_ae:reaction");
        assertEquals(64, recipe.display().inputItems().get(0).count());
        assertEquals(500, recipe.display().inputFluid().amount());
        assertEquals(1_300_000, recipe.energy());
        if (fluidOutput) {
            assertEquals(1000, recipe.display().fluidOutput().getAmount());
            assertTrue(recipe.display().itemOutput().isEmpty());
        } else {
            assertEquals(2, recipe.display().itemOutput().getCount());
            assertTrue(recipe.display().fluidOutput().isEmpty());
        }
    }

    @Test void extendedAssemblerUsesTheEcoOwnedForgeRecipe() {
        var source = extendedRecipe(new ResourceLocation("test:extended"), 256, 64);
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of(source));
        var recipes = LargeWorkstationRecipes.getAll(manager, RegistryAccess.EMPTY);
        var recipe = recipes.get(0);
        assertEquals(256, recipe.display().inputItems().get(0).count());
        assertEquals(2000, recipe.energy());
        assertEquals(ExtendedCrystalAssemblerRecipe.SOURCE_TYPE, recipe.sourceType());
        assertTrue(recipe.display().inputFluid().ingredient().isEmpty());
    }

    private static LargeWorkstationRecipe decode(String type, String data) {
        return LargeWorkstationRecipeAdapters.adapt(lightning(type, new ResourceLocation("test:recipe"), data), type);
    }

    private static Recipe<?> lightning(String type, ResourceLocation id, String data) {
        var json = JsonParser.parseString(data).getAsJsonObject();
        return switch (type) {
            case "ae2lt:overload_processing" -> roundTrip(
                new com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe.Serializer(), id, json);
            case "ae2lt:lightning_assembly" -> roundTrip(
                new com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe.Serializer(), id, json);
            case "ae2lt:lightning_simulation" -> roundTrip(
                new com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe.Serializer(), id, json);
            default -> throw new IllegalArgumentException("Unsupported test recipe " + type);
        };
    }

    private static <T extends Recipe<?>> T roundTrip(net.minecraft.world.item.crafting.RecipeSerializer<T> serializer,
            ResourceLocation id, com.google.gson.JsonObject json) {
        var recipe = serializer.fromJson(id, json);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try (var registries = mockStatic(net.minecraftforge.registries.RegistryManager.class)) {
            serializer.toNetwork(buffer, recipe);
            var restored = serializer.fromNetwork(id, buffer);
            assertFalse(buffer.isReadable());
            return restored;
        } finally { buffer.release(); }
    }

    private static ExtendedCrystalAssemblerRecipe extendedRecipe(ResourceLocation id, int input, int output) {
        return new ExtendedCrystalAssemblerRecipe(new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), input)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.DIAMOND, output),
            FluidStack.EMPTY, 2000, id));
    }

    @Test void actualAddonLibJsonPreservesSingleItemAndFluidAmounts() {
        var item = new net.pedroksl.ae2addonlib.recipes.IngredientStack.Item(Ingredient.of(Items.IRON_INGOT), 1);
        var fluid = new net.pedroksl.ae2addonlib.recipes.IngredientStack.Fluid(Fluids.WATER, 1);
        var source = new net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe(new ResourceLocation("test:single"),
            new GenericStack(AEItemKey.of(Items.DIAMOND), 1),
            List.of(net.pedroksl.ae2addonlib.recipes.IngredientStack.Item.fromJson(item.toJson())),
            net.pedroksl.ae2addonlib.recipes.IngredientStack.Fluid.fromJson(fluid.toJson()), 100);
        var recipe = LargeWorkstationRecipeAdapters.adapt(source, "advanced_ae:reaction");
        assertEquals(1, recipe.display().inputItems().get(0).count());
        assertEquals(1, recipe.display().inputFluid().amount());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ae2lt:overload_processing", "ae2lt:lightning_assembly", "ae2lt:lightning_simulation",
        "advanced_ae:reaction"
    })
    void malformedSupportedRecipesAreRejectedWithoutRemovingNativeRecipes(String typeId) {
        var type = net.minecraft.world.item.crafting.RecipeType.simple(new ResourceLocation(typeId));
        var recipe = mock(net.minecraft.world.item.crafting.Recipe.class);
        doReturn(type).when(recipe).getType();
        var holder = cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(new ResourceLocation("test:unregistered"), recipe);
        var nativeRecipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        var nativeHolder = cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(nativeRecipe.id(), nativeRecipe.display());
        var manager = mock(net.minecraft.world.item.crafting.RecipeManager.class);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        when(manager.getRecipes()).thenReturn(List.of(holder, nativeHolder));
        try (var mods = mockStatic(net.minecraftforge.fml.ModList.class)) {
            var modList = mock(net.minecraftforge.fml.ModList.class);
            when(modList.isLoaded(anyString())).thenReturn(true);
            mods.when(net.minecraftforge.fml.ModList::get).thenReturn(modList);
            var recipes = LargeWorkstationRecipes.getAll(manager, registries);
            assertEquals(List.of(nativeRecipe), recipes);
            verify(recipe).getType();
            verify(recipe, never()).getSerializer();
        }
    }

    @Test void compatibleRecipesLoadByDefaultAndBypassingCardsOnlyChangesAvailableRecipes() {
        assertFalse(NEConfig.largeWorkstationIgnoreUpgradeCards);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var serializer = new com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe.Serializer();
        var optional = spy(serializer.fromJson(new ResourceLocation("test:compat"), JsonParser.parseString("""
            {"inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":4}],
             "totalEnergy":1000,"result":{"id":"minecraft:diamond","count":2}}
            """).getAsJsonObject()));
        doReturn(RecipeType.simple(new ResourceLocation("ae2lt:lightning_assembly"))).when(optional).getType();
        doReturn(serializer).when(optional).getSerializer();
        var optionalId = new ResourceLocation("test:compat");
        var nativeRecipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        var manager = mock(RecipeManager.class);
        var optionalHolder = cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(optionalId, optional);
        var nativeHolder = cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(nativeRecipe.id(), nativeRecipe.display());
        when(manager.getRecipes()).thenReturn(List.of(optionalHolder, nativeHolder));

        var enabled = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(1, enabled.size());
        assertFalse(enabled.contains(nativeRecipe));
        var adapted = enabled.stream().filter(recipe -> recipe.id().equals(optionalId)).findFirst().orElseThrow();
        assertTrue(adapted.matches(items(Items.IRON_INGOT, 4), items(Items.DIAMOND, 2)));
        assertEquals(4, cn.dancingsnow.neoecoae.platform.JdkCompat.first(adapted.extraInputs()).amount());
        assertEquals(new ResourceLocation("ae2lt:lightning_assembly"), adapted.sourceType());
        assertSame(enabled, LargeWorkstationRecipes.getAll(manager, registries));
        assertEquals(nativeRecipe, LargeWorkstationRecipes.getById(manager, registries, nativeRecipe.id()));
        assertSame(adapted, LargeWorkstationRecipes.getById(manager, registries, optionalId));
        var regularOnly = LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR);
        assertEquals(List.of(nativeRecipe), regularOnly, "An uninstalled lightning card must not hide the native recipe");
        var lightning = LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.LIGHTNING_TECH);
        assertEquals(List.of(adapted, nativeRecipe), lightning,
            "Execution retains all legal routes even when viewers show one material group");
        assertSame(lightning, LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.LIGHTNING_TECH));
        assertSame(regularOnly, LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR));
        assertSame(adapted, LargeWorkstationRecipes.getById(manager, registries, optionalId),
            "Removing a card must not invalidate an already accepted recipe ID");

        NEConfig.largeWorkstationIgnoreUpgradeCards = true;
        var unrestricted = LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR);
        assertEquals(List.of(adapted, nativeRecipe), unrestricted);
        for (var card : LargeWorkstationModule.values()) {
            assertSame(unrestricted, LargeWorkstationRecipes.getAvailable(manager, registries, card),
                "Ignoring cards unlocks all original execution routes and keeps a stable provider cache");
        }
        assertNotSame(regularOnly, LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR),
            "The provider must see a changed recipe list when card requirements are bypassed");
        assertSame(adapted, LargeWorkstationRecipes.getById(manager, registries, optionalId));
        NEConfig.largeWorkstationIgnoreUpgradeCards = false;
        assertSame(regularOnly, LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR));
        assertSame(lightning, LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.LIGHTNING_TECH));
        assertSame(enabled, LargeWorkstationRecipes.getAll(manager, registries), "Recipe viewers keep the full recipe set");
        verify(optional, never()).getSerializer();
    }

    @Test void extendedAeCardWorksWithoutExtendedAePlusInstalled() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var recipe = extendedRecipe(new ResourceLocation("expatternprovider:test_card"), 4, 2);
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of(recipe));
        var all = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(1, all.size());
        assertEquals(2000, cn.dancingsnow.neoecoae.platform.JdkCompat.first(all).energy());
        assertTrue(LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR).isEmpty());
        assertEquals(all, LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.EXTENDED_AE));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unrelatedRecipesNeverReadTheirSerializers(boolean ignoreUpgradeCards) {
        NEConfig.largeWorkstationIgnoreUpgradeCards = ignoreUpgradeCards;
        var recipe = mock(Recipe.class);
        doReturn(RecipeType.simple(new ResourceLocation("test:unrelated"))).when(recipe).getType();
        var manager = mock(RecipeManager.class);
        var holder = cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(new ResourceLocation("test:unrelated"), recipe);
        when(manager.getRecipes()).thenReturn(List.of(holder));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        assertTrue(LargeWorkstationRecipes.getAll(manager, registries).isEmpty());
        verify(recipe, never()).getSerializer();
    }

    @Test void discardedOptionalRecipeRemainsAddressableWithOriginalCostsUntilReloadRemovesIt() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var serializer = new com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe.Serializer();
        var expensive = spy(serializer.fromJson(new ResourceLocation("ae2lt:expensive"), JsonParser.parseString("""
            {"inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":4}],
             "totalEnergy":2000,"lightningCost":7,"result":{"id":"minecraft:diamond"}}
            """).getAsJsonObject()));
        var cheaper = spy(serializer.fromJson(new ResourceLocation("ae2lt:cheaper"), JsonParser.parseString("""
            {"inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":4}],
             "totalEnergy":1000,"lightningCost":4,"result":{"id":"minecraft:diamond"}}
            """).getAsJsonObject()));
        var type = RecipeType.simple(new ResourceLocation("ae2lt:lightning_assembly"));
        doReturn(type).when(expensive).getType();
        doReturn(serializer).when(expensive).getSerializer();
        doReturn(type).when(cheaper).getType();
        doReturn(serializer).when(cheaper).getSerializer();
        var expensiveId = new ResourceLocation("ae2lt:expensive");
        var cheaperId = new ResourceLocation("ae2lt:cheaper");
        var manager = mock(RecipeManager.class);
        var cheaperHolder = cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(cheaperId, cheaper);
        var expensiveHolder = cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(expensiveId, expensive);
        when(manager.getRecipes()).thenReturn(List.of(expensiveHolder, cheaperHolder));

        var listed = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(List.of(cheaperId), listed.stream().map(LargeWorkstationRecipe::id).toList());
        var savedBatchRecipe = LargeWorkstationRecipes.getById(manager, registries, expensiveId);
        assertNotNull(savedBatchRecipe);
        assertTrue(savedBatchRecipe.feEnergy() > cn.dancingsnow.neoecoae.platform.JdkCompat.first(listed).feEnergy());
        assertEquals(7, cn.dancingsnow.neoecoae.platform.JdkCompat.first(savedBatchRecipe.extraInputs()).amount());
        assertSame(savedBatchRecipe, LargeWorkstationRecipes.getById(manager, registries, expensiveId));

        when(manager.getRecipes()).thenReturn(List.of(cheaperHolder));
        assertNull(LargeWorkstationRecipes.getById(manager, registries, expensiveId));
        assertEquals(List.of(cheaperId), LargeWorkstationRecipes.getAll(manager, registries).stream()
            .map(LargeWorkstationRecipe::id).toList());
    }

    @Test void nativeRecipesAreDiscoveredAndReloadReplacesCache() {
        var recipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        var holder = cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(recipe.id(), recipe.display());
        var manager = mock(net.minecraft.world.item.crafting.RecipeManager.class);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        when(manager.getRecipes()).thenReturn(List.of(holder));
        var first = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(1, first.size());
        assertEquals(recipe, cn.dancingsnow.neoecoae.platform.JdkCompat.first(first));
        assertSame(first, LargeWorkstationRecipes.getAll(manager, registries));
        when(manager.getRecipes()).thenReturn(List.of());
        assertTrue(LargeWorkstationRecipes.getAll(manager, registries).isEmpty());
        assertNull(LargeWorkstationRecipes.getById(manager, registries, recipe.id()));
        assertTrue(LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR).isEmpty());
    }

    private static LargeWorkstationRecipe nativeRecipe(ItemStack output) {
        return new LargeWorkstationRecipe(new ResourceLocation("test:recipe"), new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 4)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), output, FluidStack.EMPTY, 100), 100, List.of());
    }

    private static KeyCounter items(net.minecraft.world.item.Item item, long amount) {
        var result = new KeyCounter();
        result.add(AEItemKey.of(item), amount);
        return result;
    }
}
