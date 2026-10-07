package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationPatternScalingTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        // Controller textures initialize the mod's registrar even in isolated JVM tests.
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 16, 1024})
    void multipliedPatternIsRecognizedByTheInterfaceCompatibilityGate(long multiplier) throws Exception {
        var controller = controller();
        var recipe = recipe();
        try (var recipes = mockStatic(LargeWorkstationRecipes.class, CALLS_REAL_METHODS)) {
            recipes.when(() -> LargeWorkstationRecipes.getAll(controller.getLevel())).thenReturn(List.of(recipe));
            assertTrue(controller.acceptPattern(pattern(4 * multiplier, 2 * multiplier), null, false));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void batchOfDoubledPatternsOwnsAllMaterialsAndPaysScaledEnergyAndLightning(boolean suppliedLightning) throws Exception {
        var controller = controller();
        var base = recipe();
        var recipe = new LargeWorkstationRecipe(base.id(), base.display(), base.energy(),
            List.of(new GenericStack(LightningKey.EXTREME_HIGH_VOLTAGE, 4)));
        var pattern = pattern(8, 4);
        KeyCounter[] holders;
        if (suppliedLightning) {
            var lightningInput = mock(IPatternDetails.IInput.class);
            when(lightningInput.getPossibleInputs()).thenReturn(
                new GenericStack[]{new GenericStack(LightningKey.EXTREME_HIGH_VOLTAGE, 1)});
            when(lightningInput.getMultiplier()).thenReturn(8L);
            var ironInput = pattern.getInputs()[0];
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{ironInput, lightningInput});
            holders = new KeyCounter[]{counter(AEItemKey.of(Items.IRON_INGOT), 24),
                counter(LightningKey.EXTREME_HIGH_VOLTAGE, 24)};
        } else {
            holders = new KeyCounter[]{counter(AEItemKey.of(Items.IRON_INGOT), 24)};
        }
        UUID job = UUID.randomUUID();
        try (var recipes = mockStatic(LargeWorkstationRecipes.class, CALLS_REAL_METHODS)) {
            recipes.when(() -> LargeWorkstationRecipes.getAll(controller.getLevel())).thenReturn(List.of(recipe));
            assertTrue(controller.acceptPatternBatch(pattern, holders, 3, job));
        }
        for (var holder : holders) assertTrue(holder.isEmpty(), "Accepted inputs belong to the controller");
        var batch = ((Deque<?>) value(controller, "pendingBatches")).getFirst();
        assertEquals(3L, value(batch, "craftCount"));
        assertEquals(200L, value(batch, "energyPerCraft"));
        assertEquals(job, value(batch, "craftingJobId"));
        assertEquals(24L, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(12L, ((KeyCounter) value(batch, "outputTotal")).get(AEItemKey.of(Items.DIAMOND)));
        assertEquals(suppliedLightning ? 24L : 0L,
            ((KeyCounter) value(batch, "inputTotal")).get(LightningKey.EXTREME_HIGH_VOLTAGE));
        assertEquals(suppliedLightning ? 0L : 24L,
            ((KeyCounter) value(batch, "missingExtras")).get(LightningKey.EXTREME_HIGH_VOLTAGE));
        assertEquals(new GenericStack(AEItemKey.of(Items.DIAMOND), 4), value(batch, "unlockStack"));
    }

    @Test void mismatchedScaleLeavesCpuInputsUntouched() throws Exception {
        var controller = controller();
        var recipe = recipe();
        var holders = new KeyCounter[]{counter(AEItemKey.of(Items.IRON_INGOT), 7)};
        try (var recipes = mockStatic(LargeWorkstationRecipes.class, CALLS_REAL_METHODS)) {
            recipes.when(() -> LargeWorkstationRecipes.getAll(controller.getLevel())).thenReturn(List.of(recipe));
            assertFalse(controller.acceptPatternBatch(pattern(7, 4), holders, 1, null));
        }
        assertEquals(7L, holders[0].get(AEItemKey.of(Items.IRON_INGOT)));
        assertTrue(((Deque<?>) value(controller, "pendingBatches")).isEmpty());
    }

    @Test void overflowingScaledEnergyIsRejectedBeforeTakingOwnership() throws Exception {
        var controller = controller();
        var base = recipe();
        var recipe = new LargeWorkstationRecipe(base.id(), base.display(), Long.MAX_VALUE, List.of());
        var holders = new KeyCounter[]{counter(AEItemKey.of(Items.IRON_INGOT), 8)};
        try (var recipes = mockStatic(LargeWorkstationRecipes.class, CALLS_REAL_METHODS)) {
            recipes.when(() -> LargeWorkstationRecipes.getAll(controller.getLevel())).thenReturn(List.of(recipe));
            assertFalse(controller.acceptPatternBatch(pattern(8, 4), holders, 1, null));
        }
        assertEquals(8L, holders[0].get(AEItemKey.of(Items.IRON_INGOT)));
        assertTrue(((Deque<?>) value(controller, "pendingBatches")).isEmpty());
    }

    @Test void exactRecipeKeepsItsOriginalEnergyWhenASmallerRecipeAlsoMatches() throws Exception {
        var controller = controller();
        var base = recipe();
        var exact = new LargeWorkstationRecipe(ResourceLocation.parse("test:exact"), new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 8)), base.display().inputFluid(),
            new ItemStack(Items.DIAMOND, 4), FluidStack.EMPTY, 150), 150, List.of());
        var holders = new KeyCounter[]{counter(AEItemKey.of(Items.IRON_INGOT), 8)};
        try (var recipes = mockStatic(LargeWorkstationRecipes.class, CALLS_REAL_METHODS)) {
            recipes.when(() -> LargeWorkstationRecipes.getAll(controller.getLevel())).thenReturn(List.of(base, exact));
            assertTrue(controller.acceptPatternBatch(pattern(8, 4), holders, 1, null));
        }
        var batch = ((Deque<?>) value(controller, "pendingBatches")).getFirst();
        assertEquals(150L, value(batch, "energyPerCraft"));
        assertEquals(exact.id(), value(batch, "recipeId"));
    }

    private static ECOLargeIntegratedWorkingStationBlockEntity controller() throws Exception {
        var controller = mock(ECOLargeIntegratedWorkingStationBlockEntity.class, CALLS_REAL_METHODS);
        field(controller, "formed", true);
        field(controller, "level", mock(Level.class));
        field(controller, "pendingBatches", new ArrayDeque<>());
        doReturn(new FluidTank(4000)).when(controller).getInputTank();
        doReturn(mock(IManagedGridNode.class)).when(controller).getMainNode();
        doReturn(true).when(controller).canAcceptPattern();
        doNothing().when(controller).setChanged();
        return controller;
    }

    private static LargeWorkstationRecipe recipe() {
        return new LargeWorkstationRecipe(ResourceLocation.parse("test:scaled"), new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 4)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.DIAMOND, 2),
            FluidStack.EMPTY, 100), 100, List.of());
    }

    private static IPatternDetails pattern(long inputAmount, long outputAmount) {
        var pattern = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)});
        when(input.getMultiplier()).thenReturn(inputAmount);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), outputAmount);
        when(pattern.getOutputs()).thenReturn(List.of(output));
        when(pattern.getPrimaryOutput()).thenReturn(output);
        return pattern;
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object value(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static KeyCounter counter(appeng.api.stacks.AEKey key, long amount) {
        var counter = new KeyCounter();
        counter.add(key, amount);
        return counter;
    }
}
