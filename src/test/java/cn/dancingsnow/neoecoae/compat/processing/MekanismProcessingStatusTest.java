package cn.dancingsnow.neoecoae.compat.processing;

import static cn.dancingsnow.neoecoae.compat.processing.ProcessingTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader.Context;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.lookup.monitor.FactoryRecipeCacheLookupMonitor;
import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.machine.TileEntityEnergizedSmelter;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MekanismProcessingStatusTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    private final MekanismProcessingStatus reader = new MekanismProcessingStatus();
    private final Context context = new Context(AEItemKey.of(Items.STONE), UUID.randomUUID());

    @Test void ordinaryMachineReadsExistingCachedRecipeWithoutProcessingIt() {
        var machine = machine(TileEntityEnergizedSmelter.class);
        var monitor = mock(RecipeCacheLookupMonitor.class);
        var cached = cached(Items.STONE);
        when(monitor.getCachedRecipe(0)).thenReturn(cached);
        set(machine, "recipeCacheLookupMonitor", monitor);
        when(machine.getTicksRequired()).thenReturn(200);
        when(machine.getOperatingTicks()).thenReturn(50);
        when(machine.getActive()).thenReturn(true);
        assertTrue(reader.supports(machine));
        var sample = reader.read(machine, context).sample();
        assertEquals(State.RUNNING, sample.state());
        assertEquals(0.25, sample.progressTicks() / (double) sample.durationTicks(), 0.00001);
        verify(cached, never()).process();
        verify(monitor, never()).getRecipe(anyInt());
    }

    @Test void originalFactorySelectsOnlyMatchingProcessLanes() {
        var factory = machine(TileEntityFactory.class);
        var first = mock(FactoryRecipeCacheLookupMonitor.class);
        var other = mock(FactoryRecipeCacheLookupMonitor.class);
        var last = mock(FactoryRecipeCacheLookupMonitor.class);
        var stone = cached(Items.STONE);
        var dirt = cached(Items.DIRT);
        when(first.getCachedRecipe(0)).thenReturn(stone);
        when(other.getCachedRecipe(1)).thenReturn(dirt);
        when(last.getCachedRecipe(2)).thenReturn(stone);
        set(factory, "recipeCacheLookupMonitors", new FactoryRecipeCacheLookupMonitor[]{first, other, last});
        set(factory, "activeStates", new boolean[]{true, true, true});
        when(factory.getTicksRequired()).thenReturn(200);
        when(factory.getProgress(0)).thenReturn(50);
        when(factory.getProgress(1)).thenReturn(200);
        when(factory.getProgress(2)).thenReturn(150);
        var sample = reader.read(factory, context).sample();
        assertEquals(2, sample.parallel());
        assertEquals(0.5, sample.progressTicks() / (double) sample.durationTicks(), 0.00001);
        assertEquals(State.RUNNING, sample.state());
    }

    @Test void extrasAndMoreMachineFactoriesWorkWithoutInheritingTheOriginalFactory() {
        for (var factory : List.of(new com.jerry.mekextras.common.tile.factory.TileEntityExtraFactory(),
                new com.jerry.mekmm.common.tile.factory.TileEntityMoreMachineFactory())) {
            var monitor = new Monitor(cached(Items.STONE));
            set(factory, "recipeCacheLookupMonitors", new Object[]{monitor});
            set(factory, "activeStates", new boolean[]{true});
            set(factory, "progress", new int[]{80});
            set(factory, "ticksRequired", 100);
            assertTrue(reader.supports(factory), factory.getClass().getName());
            var observation = reader.read(factory, context);
            assertNotNull(observation.sample());
            assertEquals(State.RUNNING, observation.sample().state());
            assertEquals(1, observation.sample().parallel());
            assertEquals(0.8, observation.sample().progressTicks() / (double) observation.sample().durationTicks(), 0.00001);
        }
    }

    @Test void powerAndOutputErrorsAreReadFromServerCacheAndInvalidOldRecipesAreIgnored() {
        var machine = machine(TileEntityEnergizedSmelter.class);
        var monitor = mock(RecipeCacheLookupMonitor.class);
        var cached = cached(Items.STONE);
        when(monitor.getCachedRecipe(0)).thenReturn(cached);
        set(machine, "recipeCacheLookupMonitor", monitor);
        when(machine.getTicksRequired()).thenReturn(200);
        set(cached, "errors", Set.of(RecipeError.NOT_ENOUGH_ENERGY));
        var sample = reader.read(machine, context).sample();
        assertEquals(State.PAUSED, sample.state());
        assertTrue(sample.reason().getString().contains("processing.energy"));
        set(cached, "errors", Set.of(RecipeError.NOT_ENOUGH_OUTPUT_SPACE));
        assertEquals(State.WAITING, reader.read(machine, context).sample().state());
        when(cached.isInputValid()).thenReturn(false);
        assertNull(reader.read(machine, context).sample());
    }

    @Test void recipeDefinitionsMatchComponentsAndCompositeFluidOutputs() {
        assertTrue(MekanismProcessingStatus.matchesRecipe(new CompositeRecipe(), AEItemKey.of(Items.STONE)));
        assertTrue(MekanismProcessingStatus.matchesRecipe(new CompositeRecipe(), AEFluidKey.of(Fluids.WATER)));
        assertFalse(MekanismProcessingStatus.matchesRecipe(new CompositeRecipe(), AEItemKey.of(Items.DIRT)));
        var named = new ItemStack(Items.STONE);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("other"));
        assertFalse(MekanismProcessingStatus.matchesRecipe(new ItemRecipe(named), AEItemKey.of(Items.STONE)));
    }

    private static CachedRecipe cached(net.minecraft.world.item.Item output) {
        var recipe = mock(ItemStackToItemStackRecipe.class);
        when(recipe.getOutputDefinition()).thenReturn(List.of(new ItemStack(output)));
        var cached = mock(CachedRecipe.class);
        doReturn(recipe).when(cached).getRecipe();
        when(cached.isInputValid()).thenReturn(true);
        return cached;
    }
    public record Monitor(Object cached) { public Object getCachedRecipe(int index) { return cached; } }
    public record Output(ItemStack item, FluidStack fluid) {}
    public static class CompositeRecipe { public List<Output> getOutputDefinition() {
        return List.of(new Output(new ItemStack(Items.STONE), new FluidStack(Fluids.WATER, 1000))); } }
    public record ItemRecipe(ItemStack output) { public List<ItemStack> getOutputDefinition() { return List.of(output); } }
}
