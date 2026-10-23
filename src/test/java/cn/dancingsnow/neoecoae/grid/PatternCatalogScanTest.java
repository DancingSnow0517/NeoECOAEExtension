package cn.dancingsnow.neoecoae.grid;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternCatalogScanTest {
    abstract static class GenericContainer implements PatternContainer {}
    abstract static class PatternProvider implements PatternContainer {}
    abstract static class CraftingMatrix implements PatternContainer {}

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        try (var config = mockStatic(appeng.core.AEConfig.class)) {
            config.when(appeng.core.AEConfig::instance).thenReturn(mock(appeng.core.AEConfig.class));
            var item = appeng.core.definitions.AEItems.CRAFTING_PATTERN.asItem();
            if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.containsValue(item))
                net.minecraftforge.registries.ForgeRegistries.ITEMS.register(new ResourceLocation("ae2", "crafting_pattern"), item);
        }
    }

    @Test void prioritizesMatrixThenProvidersButStillScansUnknownContainers() {
        var grid = mock(IGrid.class);
        var reads = new ArrayList<String>();
        var generic = source(GenericContainer.class, grid, 2, "generic", reads);
        var provider = source(PatternProvider.class, grid, 2, "provider", reads);
        var matrix = source(CraftingMatrix.class, grid, 2, "matrix", reads);
        when(grid.getMachineClasses()).thenReturn(List.of(GenericContainer.class, PatternProvider.class, CraftingMatrix.class));
        when(grid.getActiveMachines(GenericContainer.class)).thenReturn(Set.of(generic));
        when(grid.getActiveMachines(PatternProvider.class)).thenReturn(Set.of(provider));
        when(grid.getActiveMachines(CraftingMatrix.class)).thenReturn(Set.of(matrix));
        var catalog = new PatternCatalog();
        finish(catalog, grid);
        assertEquals(List.of("matrix:0", "matrix:1", "provider:0", "provider:1", "generic:0", "generic:1"), reads);
        assertEquals(6, catalog.getExternalPatternIndex(grid).scannedSlots());
    }

    @Test void warmScanChecksKnownCraftingSlotsBeforeEmptyCapacityAndClaimsAreExclusive() {
        var grid = mock(IGrid.class);
        var reads = new ArrayList<String>();
        var source = source(GenericContainer.class, grid, 20, "source", reads);
        when(grid.getMachineClasses()).thenReturn(List.of(GenericContainer.class));
        when(grid.getActiveMachines(GenericContainer.class)).thenReturn(Set.of(source));
        ItemStack pattern = appeng.core.definitions.AEItems.CRAFTING_PATTERN.stack();
        pattern.getOrCreateTag().putString("recipe", "test:discovery");
        when(source.getTerminalPatternInventory().getStackInSlot(19)).thenAnswer(call -> { reads.add("source:19"); return pattern; });
        // Warm scans prioritize slots holding an actual AE2 crafting pattern.
        try (var patterns = mockStatic(PatternDetailsHelper.class)) {
            patterns.when(() -> PatternDetailsHelper.isEncodedPattern(pattern)).thenReturn(true);
            var catalog = new PatternCatalog();
            finish(catalog, grid);
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            assertEquals(1, catalog.claimExternalPatternCandidates(grid, first, 64).candidates().size());
            assertTrue(catalog.claimExternalPatternCandidates(grid, second, 64).candidates().isEmpty());
            catalog.releaseExternalPatternCandidates(first);
            assertEquals(1, catalog.claimExternalPatternCandidates(grid, second, 64).candidates().size());
            catalog.releaseExternalPatternCandidates(second);
            for (int i = 0; i < 100; i++) catalog.onServerEndTick();
            reads.clear();
            finish(catalog, grid);
            assertEquals("source:19", cn.dancingsnow.neoecoae.platform.JdkCompat.first(reads));
            assertEquals(20, reads.size());
            assertEquals(20, catalog.getExternalPatternIndex(grid).scannedSlots());
        }
    }

    private static <T extends PatternContainer> T source(Class<T> type, IGrid grid, int size, String name, List<String> reads) {
        T source = mock(type);
        var inventory = mock(InternalInventory.class);
        when(source.getGrid()).thenReturn(grid);
        when(source.getTerminalPatternInventory()).thenReturn(inventory);
        when(inventory.size()).thenReturn(size);
        when(inventory.getStackInSlot(anyInt())).thenAnswer(call -> {
            reads.add(name + ":" + call.getArgument(0));
            return ItemStack.EMPTY;
        });
        return source;
    }

    private static void finish(PatternCatalog catalog, IGrid grid) {
        catalog.getExternalPatternIndex(grid);
        for (int i = 0; i < 1000; i++) {
            catalog.onServerEndTick();
            if (catalog.getExternalPatternIndex(grid).ready()) return;
        }
        fail("External scan did not finish");
    }
}
