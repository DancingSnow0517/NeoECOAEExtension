package cn.dancingsnow.neoecoae.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LargeWorkstationRecipeIndexTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @AfterEach void clearCache() { LargeWorkstationRecipes.invalidate(); }

    @Test
    void outputsArePreparedOnceAndSharedAcrossAvailableModuleLists() {
        var recipe = spy(recipe("shared", Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), 10));
        var compiled = new IdentityHashMap<LargeWorkstationRecipe, LargeWorkstationRecipe.Prepared>();
        var first = new LargeWorkstationRecipeIndex(List.of(recipe), compiled);
        var second = new LargeWorkstationRecipeIndex(List.of(recipe), compiled);
        var inputs = amount(AEItemKey.of(Items.IRON_INGOT), 1);
        var outputs = amount(AEItemKey.of(Items.DIAMOND), 1);
        var match = first.find(inputs, outputs, true);
        assertNotNull(match);
        assertSame(match, first.find(inputs, outputs, true));
        assertSame(recipe, second.find(inputs, outputs, true).recipe());
        verify(recipe, times(1)).outputs();
    }

    @Test
    void mutableQueriesDoNotPoisonCachedMatchesOrNegativeResults() {
        var recipe = recipe("amounts", Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), 10);
        var index = new LargeWorkstationRecipeIndex(List.of(recipe));
        AEKey iron = AEItemKey.of(Items.IRON_INGOT), diamond = AEItemKey.of(Items.DIAMOND);
        var inputs = amount(iron, 1);
        var outputs = amount(diamond, 1);
        var first = index.find(inputs, outputs, true);
        assertEquals(1, first.multiplier());
        inputs.add(iron, 1);
        assertNull(index.find(inputs, outputs, true));
        outputs.add(diamond, 1);
        assertEquals(2, index.find(inputs, outputs, true).multiplier());
        inputs.remove(iron, 1);
        outputs.remove(diamond, 1);
        assertSame(first, index.find(inputs, outputs, true));
        outputs.add(AEItemKey.of(Items.STONE), 1);
        assertNull(index.find(inputs, outputs, true), "Every byproduct is part of the key");
        outputs.remove(AEItemKey.of(Items.STONE), 1);
        assertSame(first, index.find(inputs, outputs, true), "Zero entries do not create a new contract");
        assertEquals(1, inputs.get(iron));
        assertEquals(1, outputs.get(diamond));
    }

    @Test
    void cacheSeparatesInputAndOutputComponents() {
        var namedIron = new ItemStack(Items.IRON_INGOT);
        namedIron.set(DataComponents.CUSTOM_NAME, Component.literal("Required input"));
        var namedDiamond = new ItemStack(Items.DIAMOND);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Required output"));
        var recipe = recipe("components", DataComponentIngredient.of(true, namedIron), namedDiamond, 10);
        var index = new LargeWorkstationRecipeIndex(List.of(recipe));
        var inputs = amount(AEItemKey.of(namedIron), 1);
        var outputs = amount(AEItemKey.of(namedDiamond), 1);
        assertNotNull(index.find(inputs, outputs, true));
        assertNull(index.find(amount(AEItemKey.of(Items.IRON_INGOT), 1), outputs, true));
        assertNull(index.find(inputs, amount(AEItemKey.of(Items.DIAMOND), 1), true));
        assertNotNull(index.find(inputs, outputs, true));
    }

    @Test
    void unknownInputPredicatesRemainLiveEvenWhenAnotherRecipeWasCached() {
        var enabled = new AtomicBoolean();
        var dynamic = mock(Ingredient.class);
        when(dynamic.test(any(ItemStack.class))).thenAnswer(ignored -> enabled.get());
        var cheaper = recipe("dynamic", dynamic, new ItemStack(Items.DIAMOND), 1);
        var fallback = recipe("fallback", Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), 10);
        var index = new LargeWorkstationRecipeIndex(List.of(fallback, cheaper));
        var inputs = amount(AEItemKey.of(Items.IRON_INGOT), 1);
        var outputs = amount(AEItemKey.of(Items.DIAMOND), 1);
        assertSame(fallback, index.find(inputs, outputs, true).recipe());
        enabled.set(true);
        assertSame(cheaper, index.find(inputs, outputs, true).recipe());
        enabled.set(false);
        assertSame(fallback, index.find(inputs, outputs, true).recipe());
        var onlyDynamic = new LargeWorkstationRecipeIndex(List.of(cheaper));
        assertNull(onlyDynamic.find(inputs, outputs, true));
        enabled.set(true);
        assertNotNull(onlyDynamic.find(inputs, outputs, true), "An unknown predicate's rejection is not memoized");
    }

    @Test
    void scaledOverflowDoesNotHideALegalMoreExpensiveRecipe() {
        var overflow = recipe("overflow", Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), 1)
            .processing(200, 0, false);
        var legal = recipe("legal", Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), 2)
            .processing(1, 0, false);
        var index = new LargeWorkstationRecipeIndex(List.of(overflow, legal));
        var inputs = amount(AEItemKey.of(Items.IRON_INGOT), 100_000_000);
        var outputs = amount(AEItemKey.of(Items.DIAMOND), 100_000_000);
        assertSame(legal, index.find(inputs, outputs, true).recipe());
        assertNull(index.find(inputs, outputs, false), "Exact matching does not reuse a multiplied result");
    }

    @Test
    void cachedMissAndMatchAreReplacedOnRecipeReloadAndTagInvalidation() {
        var manager = mock(RecipeManager.class);
        var level = mock(Level.class);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(registries);
        var inputs = amount(AEItemKey.of(Items.IRON_INGOT), 1);
        var outputs = amount(AEItemKey.of(Items.DIAMOND), 1);
        when(manager.getRecipes()).thenReturn(List.of());
        assertNull(LargeWorkstationRecipes.findScaled(level, inputs, outputs));
        var firstRecipe = recipe("reload", Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), 10);
        when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(firstRecipe.id(), firstRecipe.display())));
        var first = LargeWorkstationRecipes.findScaled(level, inputs, outputs);
        assertNotNull(first);
        assertSame(first, LargeWorkstationRecipes.findScaled(level, inputs, outputs));
        var replacement = recipe("reload", Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), 20);
        when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(replacement.id(), replacement.display())));
        var second = LargeWorkstationRecipes.findScaled(level, inputs, outputs);
        assertEquals(20, second.recipe().energy());
        assertNotSame(first, second);
        LargeWorkstationRecipes.invalidate();
        assertNotSame(second, LargeWorkstationRecipes.findScaled(level, inputs, outputs));
    }

    @Test
    void cacheIsBoundedAndEvictedContractsCanBeMatchedAgain() throws Exception {
        var recipe = recipe("bounded", Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), 10)
            .processing(1, 0, false);
        var index = new LargeWorkstationRecipeIndex(List.of(recipe));
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var diamond = AEItemKey.of(Items.DIAMOND);
        for (int amount = 1; amount <= 700; amount++) {
            assertEquals(amount, index.find(amount(iron, amount), amount(diamond, amount), true).multiplier());
        }
        for (String name : List.of("candidates", "matches")) {
            var field = LargeWorkstationRecipeIndex.class.getDeclaredField(name);
            field.setAccessible(true);
            assertTrue(((Map<?, ?>) field.get(index)).size() <= 512);
        }
        assertEquals(1, index.find(amount(iron, 1), amount(diamond, 1), true).multiplier());
    }

    private static LargeWorkstationRecipe recipe(String path, Ingredient input, ItemStack output, long energy) {
        var display = new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(input, 1)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), output, FluidStack.EMPTY, Math.toIntExact(energy));
        return new LargeWorkstationRecipe(ResourceLocation.fromNamespaceAndPath("index_test", path), display, energy, List.of());
    }

    private static KeyCounter amount(AEKey key, long amount) {
        var counter = new KeyCounter();
        counter.add(key, amount);
        return counter;
    }
}
