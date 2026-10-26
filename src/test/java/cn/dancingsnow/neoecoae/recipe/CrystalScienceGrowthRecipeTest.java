package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.mojang.serialization.Codec;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the released CrystalSeedItem ABI rather than a local imitation of the optional mod. */
class CrystalScienceGrowthRecipeTest {
    private static Item defaultSeed;
    private static Item shortSeed;
    private static Item invalidSeed;
    private static Item emptySeed;
    private static DataComponentType<Integer> progress;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void bootstrap() throws ReflectiveOperationException {
        InventoryTestBootstrap.initialize();
        var components = (MappedRegistry<DataComponentType<?>>) BuiltInRegistries.DATA_COMPONENT_TYPE;
        var id = ResourceLocation.parse("ae2cs:grow_process");
        if (!components.containsKey(id)) {
            components.unfreeze();
            try {
                Registry.register(components, id, DataComponentType.<Integer>builder()
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
            } finally {
                components.freeze();
            }
        }
        progress = (DataComponentType<Integer>) components.get(id);
        var seedClass = Class.forName("io.github.lounode.ae2cs.common.item.CrystalSeedItem").asSubclass(Item.class);
        defaultSeed = registerSeed(seedClass, "default", Items.DIAMOND, 600);
        shortSeed = registerSeed(seedClass, "short", Items.EMERALD, 25);
        invalidSeed = registerSeed(seedClass, "invalid", Items.DIAMOND, 0);
        emptySeed = registerSeed(seedClass, "empty", Items.AIR, 600);
    }

    private static Item registerSeed(Class<? extends Item> type, String name, Item output, int ticks)
            throws ReflectiveOperationException {
        var items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        var id = ResourceLocation.parse("test:workstation_growth_seed_" + name);
        items.unfreeze();
        try {
            var seed = type.getConstructor(Item.Properties.class, Supplier.class, int.class)
                .newInstance(new Item.Properties(), (Supplier<Item>) () -> output, ticks);
            return Registry.register(items, id, seed);
        } finally {
            items.freeze();
        }
    }

    @Test void releasedSeedApiPreservesOneToOneOutputAndNeedsNoWater() {
        var recipe = CrystalScienceWorkstationRecipes.growthRecipes(List.of(defaultSeed)).getFirst();
        assertEquals(CrystalScienceWorkstationRecipes.GROWTH, recipe.sourceType());
        assertEquals(6000, recipe.energy());
        assertEquals(200, recipe.processingTicks());
        assertEquals(600, recipe.display().processingTicks());
        assertEquals(0, recipe.feEnergy());
        assertTrue(recipe.inputFluids().isEmpty());
        assertTrue(recipe.extraInputs().isEmpty());
        assertTrue(recipe.display().itemOutput().is(Items.DIAMOND));
        assertEquals(1, recipe.display().itemOutput().getCount());
        assertEquals(1, recipe.matchingMultiplier(items(defaultSeed, 1), items(Items.DIAMOND, 1)));
        assertFalse(recipe.matches(items(defaultSeed, 1), items(Items.DIAMOND, 2)));
        assertTrue(LargeWorkstationModule.CRYSTAL_SCIENCE.unlocks(recipe.sourceType()));
        assertFalse(LargeWorkstationModule.REGULAR.unlocks(recipe.sourceType()));
    }

    @Test void customSeedDurationRoundsUpToWholeChamberWorkCycles() {
        var recipe = CrystalScienceWorkstationRecipes.growthRecipes(List.of(shortSeed)).getFirst();
        assertEquals(300, recipe.energy());
        assertEquals(30, recipe.processingTicks());
        assertTrue(recipe.matches(items(shortSeed, 1), items(Items.EMERALD, 1)));
    }

    @Test void partiallyGrownSeedKeepsTheFullCycleBudgetAndOutput() {
        var recipe = CrystalScienceWorkstationRecipes.growthRecipes(List.of(defaultSeed)).getFirst();
        var stack = new ItemStack(defaultSeed);
        assertEquals(0, stack.get(progress));
        stack.set(progress, 300);
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(stack), 4);
        assertEquals(4, recipe.matchingMultiplier(inputs, items(Items.DIAMOND, 4)));
        assertEquals(6000, recipe.energy());
    }

    @Test void scaledPatternRetainsMaterialRatioAndPaysEverySeedOperation() {
        var recipe = CrystalScienceWorkstationRecipes.growthRecipes(List.of(defaultSeed)).getFirst();
        var match = LargeWorkstationRecipes.findScaled(List.of(recipe), items(defaultSeed, 64), items(Items.DIAMOND, 64));
        assertNotNull(match);
        assertEquals(64, match.multiplier());
        assertEquals(384_000, Math.multiplyExact(match.recipe().energy(), match.multiplier()));
        assertNull(LargeWorkstationRecipes.findScaled(List.of(recipe), items(defaultSeed, 32), items(Items.DIAMOND, 64)));
    }

    @Test void malformedAndNonSeedItemsDoNotHideValidGrowthRecipes() {
        var recipes = CrystalScienceWorkstationRecipes.growthRecipes(
            List.of(invalidSeed, Items.STONE, defaultSeed, emptySeed, shortSeed));
        assertEquals(2, recipes.size());
        assertEquals(List.of(6000L, 300L), recipes.stream().map(LargeWorkstationRecipe::energy).toList());
        assertThrows(IllegalArgumentException.class, () -> CrystalScienceWorkstationRecipes.growth(defaultSeed, Items.AIR, 600));
        assertThrows(IllegalArgumentException.class, () -> CrystalScienceWorkstationRecipes.growth(defaultSeed, Items.DIAMOND, -1));
    }

    @Test void extremeDurationKeepsTheLongEnergyBudgetWithoutOverflow() {
        var recipe = CrystalScienceWorkstationRecipes.growth(Items.SUGAR, Items.DIAMOND, Integer.MAX_VALUE);
        assertEquals(21_474_836_500L, recipe.energy());
        assertEquals(200, recipe.processingTicks());
        assertEquals(Integer.MAX_VALUE, recipe.display().energy());
    }

    @Test void absentCrystalScienceLoadsTheAdapterWithoutAnyOptionalClasses() throws Exception {
        String owner = CrystalScienceWorkstationRecipes.class.getName();
        var parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("io.github.lounode.ae2cs.")) throw new ClassNotFoundException(name);
                if (!name.equals(owner)) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try (var stream = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (stream == null) throw new ClassNotFoundException(name);
                        byte[] bytes = stream.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        };
        var type = Class.forName(owner, true, loader);
        var method = type.getDeclaredMethod("growthRecipes");
        method.setAccessible(true);
        assertEquals(List.of(), method.invoke(null));
    }

    @Test void growthCatalogUsesTheCrystalCardAndRetainsStableIdsForSavedBatches() {
        var recipe = CrystalScienceWorkstationRecipes.growthRecipes(List.of(defaultSeed)).getFirst();
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        boolean previous = NEConfig.largeWorkstationIgnoreUpgradeCards;
        try (var growth = mockStatic(CrystalScienceWorkstationRecipes.class, CALLS_REAL_METHODS)) {
            NEConfig.largeWorkstationIgnoreUpgradeCards = false;
            growth.when(CrystalScienceWorkstationRecipes::growthRecipes).thenReturn(List.of(recipe));
            assertEquals(List.of(recipe), LargeWorkstationRecipes.getAll(manager, registries));
            assertEquals(List.of(recipe), LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.CRYSTAL_SCIENCE));
            assertTrue(LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR).isEmpty());
            assertSame(recipe, LargeWorkstationRecipes.getById(manager, registries, recipe.id()));
            assertEquals(ResourceLocation.parse("neoecoae:crystal_growth/test/workstation_growth_seed_default"), recipe.id());
        } finally {
            NEConfig.largeWorkstationIgnoreUpgradeCards = previous;
            LargeWorkstationRecipes.invalidate();
        }
    }

    @Test void tagInvalidationRebuildsGrowthCatalogAndPreservesTheSeedIdentity() {
        var oldRecipe = CrystalScienceWorkstationRecipes.growth(defaultSeed, Items.DIAMOND, 600);
        var newRecipe = CrystalScienceWorkstationRecipes.growth(defaultSeed, Items.EMERALD, 25);
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        try (var growth = mockStatic(CrystalScienceWorkstationRecipes.class, CALLS_REAL_METHODS)) {
            growth.when(CrystalScienceWorkstationRecipes::growthRecipes).thenReturn(List.of(oldRecipe));
            assertSame(oldRecipe, LargeWorkstationRecipes.getById(manager, registries, oldRecipe.id()));
            growth.when(CrystalScienceWorkstationRecipes::growthRecipes).thenReturn(List.of(newRecipe));
            assertSame(oldRecipe, LargeWorkstationRecipes.getById(manager, registries, oldRecipe.id()));
            LargeWorkstationRecipes.invalidate();
            assertSame(newRecipe, LargeWorkstationRecipes.getById(manager, registries, oldRecipe.id()));
            growth.when(CrystalScienceWorkstationRecipes::growthRecipes).thenReturn(List.of());
            LargeWorkstationRecipes.invalidate();
            assertNull(LargeWorkstationRecipes.getById(manager, registries, oldRecipe.id()));
        } finally {
            LargeWorkstationRecipes.invalidate();
        }
    }

    @Test void datapackRecipeKeepsItsIdentityWhenAGrowthAliasWouldCollide() {
        var recipe = CrystalScienceWorkstationRecipes.growthRecipes(List.of(defaultSeed)).getFirst();
        var nativeRecipe = new IntegratedWorkingStationRecipe(List.of(), CrystalScienceWorkstationRecipes.noFluid(),
            new ItemStack(Items.EMERALD), net.neoforged.neoforge.fluids.FluidStack.EMPTY, 100);
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(recipe.id(), nativeRecipe)));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        try (var growth = mockStatic(CrystalScienceWorkstationRecipes.class, CALLS_REAL_METHODS)) {
            growth.when(CrystalScienceWorkstationRecipes::growthRecipes).thenReturn(List.of(recipe));
            assertEquals(1, LargeWorkstationRecipes.getAll(manager, registries).size());
            var retained = LargeWorkstationRecipes.getById(manager, registries, recipe.id());
            assertNotNull(retained);
            assertEquals(LargeWorkstationRecipe.NATIVE_TYPE, retained.sourceType());
            assertTrue(retained.display().itemOutput().is(Items.EMERALD));
        } finally {
            LargeWorkstationRecipes.invalidate();
        }
    }

    private static KeyCounter items(Item item, long amount) {
        var result = new KeyCounter();
        result.add(AEItemKey.of(item), amount);
        return result;
    }
}
