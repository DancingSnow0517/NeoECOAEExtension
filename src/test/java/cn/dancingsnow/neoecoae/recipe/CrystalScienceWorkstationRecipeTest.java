package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.recipes.entropy.EntropyMode;
import appeng.recipes.entropy.EntropyRecipe;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CrystalScienceWorkstationRecipeTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    /** Public accessor boundary supplied by each upstream AE2CS recipe implementation. */
    public interface CrystalRecipe extends Recipe<Container> {
        SizedIngredient input();
        List<SizedIngredient> required();
        ItemStack result();
        int energyCost();
    }

    @ParameterizedTest
    @ValueSource(strings = {"circuit_etcher_recipe", "crystal_aggregator_recipe", "crystal_pulverizer_recipe"})
    void optionalRecipesAreDiscoveredWithExactCountsAndOnlyUnlockedByTheCrystalCard(String name) {
        var source = mock(CrystalRecipe.class);
        var ingredient = new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 4);
        when(source.input()).thenReturn(ingredient);
        when(source.required()).thenReturn(List.of(ingredient));
        when(source.result()).thenReturn(new ItemStack(Items.DIAMOND, 2));
        when(source.energyCost()).thenReturn(345);
        when(source.getId()).thenReturn(new ResourceLocation("test:" + name));
        doReturn(RecipeType.simple(new ResourceLocation("ae2cs:" + name))).when(source).getType();
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of(source));
        var recipes = LargeWorkstationRecipes.getAll(manager, RegistryAccess.EMPTY);
        assertEquals(1, recipes.size());
        var recipe = recipes.get(0);
        assertEquals(345, recipe.energy());
        assertSame(recipe, LargeWorkstationRecipes.getById(manager, RegistryAccess.EMPTY, source.getId()));
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.IRON_INGOT), 8);
        var outputs = new KeyCounter();
        outputs.add(AEItemKey.of(Items.DIAMOND), 4);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
        assertTrue(LargeWorkstationModule.CRYSTAL_SCIENCE.unlocks(recipe.sourceType()));
        assertFalse(LargeWorkstationModule.REGULAR.unlocks(recipe.sourceType()));
        assertFalse(LargeWorkstationModule.EXTENDED_AE.unlocks(recipe.sourceType()));
        when(manager.getRecipes()).thenReturn(List.of());
        assertNull(LargeWorkstationRecipes.getById(manager, RegistryAccess.EMPTY, source.getId()));
    }

    @Test void entropyPreservesTheBlockAndEveryDrop() {
        var source = new EntropyRecipe(new ResourceLocation("test:entropy"), EntropyMode.HEAT, Blocks.STONE, List.of(),
            null, List.of(), Blocks.COBBLESTONE, List.of(), false, null, List.of(), false,
            List.of(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.EMERALD)));
        var recipe = CrystalScienceWorkstationRecipes.entropy(source.getId(), source);
        assertNotNull(recipe);
        assertEquals(1600, recipe.energy());
        assertEquals(3, recipe.outputs().size());
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.STONE), 1);
        var outputs = new KeyCounter();
        for (var output : recipe.outputs()) outputs.add(output.what(), output.amount());
        assertTrue(recipe.matches(inputs, outputs));
        outputs.remove(AEItemKey.of(Items.EMERALD), 1);
        assertFalse(recipe.matches(inputs, outputs));
        assertTrue(LargeWorkstationModule.CRYSTAL_SCIENCE.unlocks(recipe.sourceType()));
    }

    @Test void entropyFluidsUseTheForgeMachineBucketCost() {
        var source = new EntropyRecipe(new ResourceLocation("test:fluid_entropy"), EntropyMode.COOL, null, List.of(),
            Fluids.WATER, List.of(), Blocks.ICE, List.of(), false, null, List.of(), false, List.of());
        var recipe = CrystalScienceWorkstationRecipes.entropy(source.getId(), source);
        assertNotNull(recipe);
        var inputs = new KeyCounter();
        inputs.add(AEFluidKey.of(Fluids.WATER), 1000);
        assertTrue(recipe.matchesInputs(inputs));
        inputs.remove(AEFluidKey.of(Fluids.WATER), 1);
        assertFalse(recipe.matchesInputs(inputs));
    }

    @Test void ae2AloneDoesNotUnlockEntropyRecipes() {
        var manager = mock(RecipeManager.class);
        var source = new EntropyRecipe(new ResourceLocation("test:entropy_absent"), EntropyMode.HEAT, Blocks.STONE, List.of(),
            null, List.of(), Blocks.COBBLESTONE, List.of(), false, null, List.of(), false, List.of());
        when(manager.getRecipes()).thenReturn(List.of(source));
        assertTrue(LargeWorkstationRecipes.getAll(manager, RegistryAccess.EMPTY).isEmpty());
    }
}
