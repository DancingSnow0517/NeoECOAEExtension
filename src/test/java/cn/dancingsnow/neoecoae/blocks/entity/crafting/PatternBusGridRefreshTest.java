package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.me.service.helpers.NetworkCraftingProviders;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECORecipeClassifier;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.PatternSearchKeywords;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternBusGridRefreshTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void all176BusesIgnoreBootAndPowerChangesForPublication() {
        var f = new Fixture();
        for (int i = 0; i < 176; i++) {
            var bus = bus(f.level, f.node);
            for (var reason : IGridNodeListener.State.values()) {
                bus.onMainNodeStateChanged(reason);
                bus.onMainNodeStateChanged(reason);
            }
        }
        verify(f.server, never()).execute(any(TickTask.class));
        verifyNoInteractions(f.crafting, f.grid);
    }

    @Test
    void nativeAe2RegistrationSurvivesRebootsAndMovesBetweenGrids() {
        var f = new Fixture();
        var provider = bus(f.level, f.node);
        var pattern = pattern(Items.IRON_INGOT);
        when(provider.getAvailablePatterns()).thenReturn(List.of(pattern));
        when(f.rawNode.getService(ICraftingProvider.class)).thenReturn(provider);
        var originalGrid = new NetworkCraftingProviders();
        originalGrid.addProvider(f.rawNode);

        provider.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
        provider.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
        when(f.node.isOnline()).thenReturn(false);
        provider.onMainNodeStateChanged(IGridNodeListener.State.POWER);
        when(f.node.isOnline()).thenReturn(true);
        provider.onMainNodeStateChanged(IGridNodeListener.State.POWER);
        assertEquals(List.of(pattern), originalGrid.getCraftingFor(AEItemKey.of(Items.IRON_INGOT)));
        verify(f.crafting, never()).refreshNodeCraftingProvider(any());

        // CraftingService owns remove/add on a real grid move, independently of the bus state callbacks.
        originalGrid.removeProvider(f.rawNode);
        IGrid nextGrid = mock(IGrid.class);
        when(f.rawNode.getGrid()).thenReturn(nextGrid);
        var destinationGrid = new NetworkCraftingProviders();
        destinationGrid.addProvider(f.rawNode);
        assertTrue(originalGrid.getCraftingFor(AEItemKey.of(Items.IRON_INGOT)).isEmpty());
        assertEquals(List.of(pattern), destinationGrid.getCraftingFor(AEItemKey.of(Items.IRON_INGOT)));
    }

    @Test
    void startupAndActualPatternChangesStillRepublishTheNativeAe2Index() {
        var f = new Fixture();
        var provider = bus(f.level, f.node);
        var inventory = mock(AppEngInternalInventory.class);
        when(inventory.size()).thenReturn(1);
        when(provider.getPatternSlotCount()).thenReturn(1);
        AtomicReference<ItemStack> stored = new AtomicReference<>(new ItemStack(Items.PAPER));
        when(inventory.getStackInSlot(0)).thenAnswer(invocation -> stored.get());
        var catalog = new ECOCraftingPatternBusCatalog(provider, inventory);
        when(provider.getAvailablePatterns()).thenAnswer(invocation -> catalog.availablePatterns());
        when(f.rawNode.getService(ICraftingProvider.class)).thenReturn(provider);
        var index = new NetworkCraftingProviders();
        index.addProvider(f.rawNode);
        doAnswer(invocation -> {
            index.removeProvider(f.rawNode);
            index.addProvider(f.rawNode);
            return null;
        }).when(f.crafting).refreshNodeCraftingProvider(f.rawNode);

        AtomicReference<IPatternDetails> decoded = new AtomicReference<>(pattern(Items.IRON_INGOT));
        try (var patterns = mockStatic(PatternDetailsHelper.class);
             var keywords = mockStatic(PatternSearchKeywords.class);
             var classifier = mockStatic(ECORecipeClassifier.class)) {
            patterns.when(() -> PatternDetailsHelper.decodePattern(any(ItemStack.class), eq(f.level)))
                .thenAnswer(invocation -> invocation.getArgument(0, ItemStack.class).isEmpty() ? null : decoded.get());
            catalog.onReady();
            assertEquals(List.of(decoded.get()), index.getCraftingFor(AEItemKey.of(Items.IRON_INGOT)));

            provider.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
            provider.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
            verify(f.crafting, times(1)).refreshNodeCraftingProvider(f.rawNode);

            decoded.set(pattern(Items.GOLD_INGOT));
            stored.set(new ItemStack(Items.BOOK));
            catalog.onChangeInventory(0);
            assertTrue(index.getCraftingFor(AEItemKey.of(Items.IRON_INGOT)).isEmpty());
            assertEquals(List.of(decoded.get()), index.getCraftingFor(AEItemKey.of(Items.GOLD_INGOT)));

            stored.set(ItemStack.EMPTY);
            catalog.onChangeInventory(0);
            assertTrue(index.getCraftingFor(AEItemKey.of(Items.GOLD_INGOT)).isEmpty());
            verify(f.crafting, times(3)).refreshNodeCraftingProvider(f.rawNode);
        }
    }

    private static ECOCraftingPatternBusBlockEntity bus(ServerLevel level, IManagedGridNode node) {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class, invocation ->
            invocation.getMethod().getName().equals("onMainNodeStateChanged")
                ? invocation.callRealMethod() : RETURNS_DEFAULTS.answer(invocation));
        when(bus.getLevel()).thenReturn(level);
        when(bus.getMainNode()).thenReturn(node);
        return bus;
    }

    private static IPatternDetails pattern(Item output) {
        var pattern = mock(IPatternDetails.class, withSettings().extraInterfaces(IMolecularAssemblerSupportedPattern.class));
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(AEItemKey.of(output), 1));
        return pattern;
    }

    private static final class Fixture {
        final ServerLevel level = mock(ServerLevel.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final IGrid grid = mock(IGrid.class);
        final ICraftingService crafting = mock(ICraftingService.class);
        final IManagedGridNode node = mock(IManagedGridNode.class);
        final IGridNode rawNode = mock(IGridNode.class);

        Fixture() {
            when(level.getServer()).thenReturn(server);
            when(node.getGrid()).thenReturn(grid);
            when(node.getNode()).thenReturn(rawNode);
            when(node.isOnline()).thenReturn(true);
            when(rawNode.getGrid()).thenReturn(grid);
            when(grid.getCraftingService()).thenReturn(crafting);
        }
    }
}
