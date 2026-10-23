package cn.dancingsnow.neoecoae.grid;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.EncodedCraftingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import cn.dancingsnow.neoecoae.api.ECOPatternSourceSlot;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.PatternStorageHost;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternCatalogScanTest {
    abstract static class GenericContainer implements PatternContainer {}
    abstract static class PatternProvider implements PatternContainer {}
    abstract static class CraftingMatrix implements PatternContainer {}
    abstract static class StorageHostContainer implements PatternContainer, PatternStorageHost {}
    abstract static class IndexedStorageHostContainer extends StorageHostContainer implements IECOPatternStorage {}

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void excludesStorageHostsByDefault() {
        var grid = mock(IGrid.class);
        var host = mock(StorageHostContainer.class);
        when(host.allowsPatternMigrationSource()).thenCallRealMethod();
        assertFalse(host.allowsPatternMigrationSource());
        when(grid.getMachineClasses()).thenReturn(List.of(StorageHostContainer.class));
        when(grid.getActiveMachines(StorageHostContainer.class)).thenReturn(Set.of(host));

        var catalog = new PatternCatalog();
        finish(catalog, grid);

        var index = catalog.getExternalPatternIndex(grid);
        assertTrue(index.candidates().isEmpty());
        assertEquals(0, index.totalSlots());
        assertEquals(0, index.scannedSlots());
        verify(host, never()).getPatternSlotInventory();
        verify(host, never()).getTerminalPatternInventory();
    }

    @Test void optedInStorageHostsExposePhysicalSlotsAsClaimableSources() {
        var grid = mock(IGrid.class);
        var host = mock(StorageHostContainer.class);
        var physical = mock(InternalInventory.class);
        var terminal = mock(InternalInventory.class);
        when(host.allowsPatternMigrationSource()).thenReturn(true);
        when(host.getGrid()).thenReturn(grid);
        when(host.getPatternSlotInventory()).thenReturn(physical);
        when(host.getTerminalPatternInventory()).thenReturn(terminal);
        when(physical.size()).thenReturn(2);
        when(physical.getStackInSlot(anyInt())).thenReturn(ItemStack.EMPTY);
        ItemStack pattern = encodedPattern();
        when(physical.getStackInSlot(1)).thenReturn(pattern);
        when(grid.getMachineClasses()).thenReturn(List.of(StorageHostContainer.class));
        when(grid.getActiveMachines(StorageHostContainer.class)).thenReturn(Set.of(host));

        try (var patterns = mockStatic(PatternDetailsHelper.class)) {
            patterns.when(() -> PatternDetailsHelper.isEncodedPattern(pattern)).thenReturn(true);
            var catalog = new PatternCatalog();
            finish(catalog, grid);

            var candidate = new ECOPatternSourceSlot(host, 1);
            var index = catalog.getExternalPatternIndex(grid);
            assertEquals(2, index.totalSlots());
            assertEquals(2, index.scannedSlots());
            assertEquals(List.of(candidate), index.candidates());
            assertEquals(List.of(candidate),
                    catalog.claimExternalPatternCandidates(grid, UUID.randomUUID(), 64).candidates());
            assertTrue(catalog.claimExternalPatternCandidates(grid, UUID.randomUUID(), 64).candidates().isEmpty());
            verify(host, never()).getTerminalPatternInventory();
            verifyNoInteractions(terminal);
        }
    }

    @Test void migrationSourcePermissionIsPerHostAndRepeatedMachinesAreScannedOnce() {
        var grid = mock(IGrid.class);
        var reads = new ArrayList<String>();
        var provider = source(PatternProvider.class, grid, 1, "provider", reads);
        var allowed = mock(StorageHostContainer.class);
        var excluded = mock(StorageHostContainer.class);
        when(allowed.allowsPatternMigrationSource()).thenReturn(true);
        when(excluded.allowsPatternMigrationSource()).thenCallRealMethod();
        when(allowed.getGrid()).thenReturn(grid);
        var physical = mock(InternalInventory.class);
        when(allowed.getPatternSlotInventory()).thenReturn(physical);
        when(physical.size()).thenReturn(1);
        when(physical.getStackInSlot(0)).thenReturn(ItemStack.EMPTY);
        when(grid.getMachineClasses()).thenReturn(
                List.of(StorageHostContainer.class, PatternProvider.class, PatternContainer.class));
        when(grid.getActiveMachines(StorageHostContainer.class)).thenReturn(Set.of(allowed, excluded));
        when(grid.getActiveMachines(PatternProvider.class)).thenReturn(Set.of(provider));
        when(grid.getActiveMachines(PatternContainer.class)).thenReturn(Set.of(allowed, excluded, provider));

        var catalog = new PatternCatalog();
        finish(catalog, grid);

        var index = catalog.getExternalPatternIndex(grid);
        assertEquals(2, index.totalSlots());
        assertEquals(2, index.scannedSlots());
        assertEquals(List.of("provider:0"), reads);
        verify(physical).getStackInSlot(0);
        verify(excluded, never()).getPatternSlotInventory();
    }

    @Test void rebuildingMigrationIndexPreservesOptedInHostsCatalogLocations() {
        var grid = mock(IGrid.class);
        var host = mock(IndexedStorageHostContainer.class);
        var physical = mock(InternalInventory.class);
        ItemStack pattern = encodedPattern();
        when(host.allowsPatternMigrationSource()).thenReturn(true);
        when(host.shouldIndexPattern(pattern)).thenCallRealMethod();
        when(host.getGrid()).thenReturn(grid);
        when(host.getPatternSlotInventory()).thenReturn(physical);
        when(host.getAuxiliaryEncodedPatterns()).thenReturn(List.of());
        when(physical.size()).thenReturn(1);
        when(physical.getStackInSlot(0)).thenReturn(pattern);
        when(grid.getMachineClasses()).thenReturn(List.of(IndexedStorageHostContainer.class));
        when(grid.getActiveMachines(IndexedStorageHostContainer.class)).thenReturn(Set.of(host));
        var node = mock(IGridNode.class);
        when(node.getService(IECOPatternStorage.class)).thenReturn(host);

        try (var patterns = mockStatic(PatternDetailsHelper.class)) {
            patterns.when(() -> PatternDetailsHelper.isEncodedPattern(pattern)).thenReturn(true);
            var catalog = new PatternCatalog();
            catalog.addNode(node, null);
            AEItemKey key = AEItemKey.of(pattern);
            var location = new PatternCatalog.PatternLocation(host, 0);
            assertEquals(Set.of(location), catalog.locationsForKey(key));
            finish(catalog, grid);
            assertEquals(List.of(new ECOPatternSourceSlot(host, 0)),
                    catalog.getExternalPatternIndex(grid).candidates());

            for (int i = 0; i < 100; i++) catalog.onServerEndTick();
            catalog.getExternalPatternIndex(grid);
            assertEquals(Set.of(location), catalog.locationsForKey(key));
            assertNotNull(catalog.getPatternRecord(host, 0));
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
        ItemStack pattern = encodedPattern();
        when(source.getTerminalPatternInventory().getStackInSlot(19)).thenAnswer(call -> { reads.add("source:19"); return pattern; });
        // Discovery accepts integration-defined encoded items; no mod registry is installed in this JVM test.
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
            assertEquals("source:19", reads.getFirst());
            assertEquals(20, reads.size());
            assertEquals(20, catalog.getExternalPatternIndex(grid).scannedSlots());
        }
    }

    private static ItemStack encodedPattern() {
        ItemStack pattern = new ItemStack(Items.STONE);
        pattern.set(AEComponents.ENCODED_CRAFTING_PATTERN, new EncodedCraftingPattern(List.of(),
                new ItemStack(Items.DIAMOND), ResourceLocation.withDefaultNamespace("test"), false, false));
        return pattern;
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
