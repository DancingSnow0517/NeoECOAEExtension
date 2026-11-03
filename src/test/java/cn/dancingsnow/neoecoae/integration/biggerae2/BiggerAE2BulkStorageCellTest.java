package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.storage.cells.CellState;
import appeng.core.AEConfig;
import appeng.core.localization.Tooltips;
import appeng.items.storage.StorageCellTooltipComponent;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.storage.bulk.BulkUnits;
import cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell;
import cn.dancingsnow.neoecoae.storage.bulk.ECOCompressionService;
import cn.dancingsnow.neoecoae.storage.transfer.StorageExtractionExclusions;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.ChatFormatting;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiggerAE2BulkStorageCellTest {
    @Test
    void tooltipHasNativeColoredTypeCountCompressionStatusAndOptionalNativeContentPreview() {
        var stack = stack();
        var item = (BiggerAE2BulkStorageCellItem) stack.getItem();
        var cell = mock(ECOBulkStorageCell.class);
        when(cell.getTotalItemTypes()).thenReturn(5L);
        when(cell.getAvailableStacks()).thenReturn(new appeng.api.stacks.KeyCounter());
        try (var factory = mockStatic(ECOStorageCellItem.class);
             var types = mockStatic(AEKeyTypesInternal.class);
             var config = mockStatic(AEConfig.class)) {
            factory.when(() -> ECOStorageCellItem.getCellInventory(stack)).thenReturn(cell);
            types.when(AEKeyTypesInternal::getAllTypes).thenReturn(Set.of(AEKeyType.items()));
            var lines = new ArrayList<Component>();
            item.appendHoverText(stack, null, lines, TooltipFlag.NORMAL);
            assertEquals(2, lines.size());
            var line = (TranslatableContents) lines.getFirst().getContents();
            assertEquals("tooltip.neoecoae.bulk.used_types", line.getKey());
            assertEquals(Tooltips.ofUnformattedNumberWithRatioColor(0, 0, false), line.getArgs()[0]);
            assertEquals(Tooltips.ofUnformattedNumber(5), line.getArgs()[1]);
            var compression = (TranslatableContents) lines.get(1).getContents();
            assertEquals("tooltip.neoecoae.bulk.compression", compression.getKey());
            var enabled = (Component) compression.getArgs()[0];
            assertEquals("tooltip.neoecoae.bulk.compression_requires_marker", ((TranslatableContents) enabled.getContents()).getKey());
            assertEquals(ChatFormatting.GRAY.getColor(), enabled.getStyle().getColor().getValue());
            assertTrue(item.getTooltipImage(stack).isEmpty());

            var aeConfig = mock(AEConfig.class);
            config.when(AEConfig::instance).thenReturn(aeConfig);
            when(aeConfig.getTooltipMaxCellContentShown()).thenReturn(5);
            var key = AEItemKey.of(Items.IRON_INGOT);
            stack.set(AEComponents.STORAGE_CELL_CONFIG_INV, List.of(new GenericStack(key, 0)));
            when(cell.getEffectiveConfiguredFilters()).thenReturn(List.of(key));
            var preview = (StorageCellTooltipComponent) item.getTooltipImage(stack).orElseThrow();
            assertEquals(List.of(new GenericStack(key, 0)), preview.content());
            assertTrue(preview.upgrades().isEmpty());
            assertFalse(preview.hasMoreContent());

            var available = new appeng.api.stacks.KeyCounter();
            available.add(key, 42);
            when(cell.getAvailableStacks()).thenReturn(available);
            when(cell.getStoredItemTypes()).thenReturn(1L);
            lines.clear();
            item.appendHoverText(stack, null, lines, TooltipFlag.NORMAL);
            assertEquals(2, lines.size());
            compression = (TranslatableContents) lines.get(1).getContents();
            enabled = (Component) compression.getArgs()[0];
            assertEquals("tooltip.neoecoae.bulk.compression_enabled", ((TranslatableContents) enabled.getContents()).getKey());
            assertEquals(ChatFormatting.GREEN.getColor(), enabled.getStyle().getColor().getValue());
            preview = (StorageCellTooltipComponent) item.getTooltipImage(stack).orElseThrow();
            assertEquals(List.of(new GenericStack(key, 42)), preview.content());
        }
    }

    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @BeforeEach
    void compressionRecipes() {
        ECOCompressionService.install(List.of(new BulkUnits(List.of(AEItemKey.of(Items.IRON_NUGGET),
            AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(Items.IRON_BLOCK)),
            List.of(BigInteger.ONE, BigInteger.valueOf(9), BigInteger.valueOf(81)))));
    }

    @Test
    void unmarkedDiskAcceptsItemsAndSimulationDoesNotCreateStockOrMarkers() {
        var stack = stack();
        var before = stack.copy();
        try (var types = mockStatic(AEKeyTypesInternal.class)) {
            types.when(AEKeyTypesInternal::getAllTypes).thenReturn(Set.of(AEKeyType.items()));
            var item = (BiggerAE2BulkStorageCellItem) stack.getItem();
            var cell = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            var nugget = AEItemKey.of(Items.IRON_NUGGET);
            assertEquals(Long.MAX_VALUE, cell.getRemainingItemCount());
            assertEquals(82, cell.insert(nugget, 82, Actionable.SIMULATE, null));
            assertEquals(0, cell.insert(AEFluidKey.of(Fluids.WATER), 1, Actionable.MODULATE, null));
            assertEquals(before.getComponents(), stack.getComponents());
            assertEquals(0, cell.getStoredItemTypes());
            assertTrue(cell.getAvailableStacks().isEmpty());
            assertEquals(82, cell.insert(nugget, 82, Actionable.MODULATE, null));
            cell.setCompressionCutoff(AEItemKey.of(Items.IRON_BLOCK));
            assertEquals(0, cell.getAvailableStacks().get(AEItemKey.of(Items.IRON_BLOCK)));
            assertEquals(82, cell.getAvailableStacks().get(nugget));
            assertEquals(0, cell.extract(AEItemKey.of(Items.IRON_INGOT), 1, Actionable.MODULATE, null));
            assertEquals(1, cell.getStoredItemTypes());
            assertTrue(cell.getEffectiveConfiguredFilters().isEmpty());
            assertTrue(cell.getConversionPatterns().isEmpty());
        }
    }

    @Test
    void unmarkedExactBalancesSurviveReloadAndRecipeChangesWithoutRepricing() {
        var stack = stack();
        try (var types = mockStatic(AEKeyTypesInternal.class)) {
            types.when(AEKeyTypesInternal::getAllTypes).thenReturn(Set.of(AEKeyType.items()));
            var item = (BiggerAE2BulkStorageCellItem) stack.getItem();
            var cell = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            var ingot = AEItemKey.of(Items.IRON_INGOT);
            var nugget = AEItemKey.of(Items.IRON_NUGGET);
            var amount = BigInteger.ONE.shiftLeft(160).add(BigInteger.TEN);
            assertEquals(amount, cell.insertBigInteger(ingot, amount, Actionable.MODULATE, null));
            var restored = (ECOBulkStorageCell) item.createCellInventory(stack.copy(), null);
            assertEquals(amount, restored.getExactUsedBytes());
            assertTrue(restored.getEffectiveConfiguredFilters().isEmpty());
            assertEquals(0, restored.extract(AEItemKey.of(Items.IRON_BLOCK), 1, Actionable.MODULATE, null));
            var balance = restored.getExactUsedBytes();
            ECOCompressionService.install(List.of());
            assertEquals(0, restored.getRecoveringEntryCount());
            assertEquals(amount, restored.insertBigInteger(ingot, amount, Actionable.MODULATE, null));
            assertEquals(0, restored.extract(nugget, 1, Actionable.MODULATE, null));
            assertEquals(1, restored.extract(ingot, 1, Actionable.MODULATE, null));
            assertEquals(balance.add(amount).subtract(BigInteger.ONE), restored.getExactUsedBytes());
        }
    }

    @Test
    void unmarkedDiskCountsPhysicalItemsAndRejectsOnlyNewTypesWhenFull() {
        var stack = stack();
        try (var types = mockStatic(AEKeyTypesInternal.class)) {
            types.when(AEKeyTypesInternal::getAllTypes).thenReturn(Set.of(AEKeyType.items()));
            var item = (BiggerAE2BulkStorageCellItem) stack.getItem();
            var cell = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            var ingot = AEItemKey.of(Items.IRON_INGOT);
            assertEquals(9, cell.insert(ingot, 9, Actionable.MODULATE, null));
            assertEquals(1, cell.insert(AEItemKey.of(Items.IRON_BLOCK), 1, Actionable.MODULATE, null));
            assertEquals(2, cell.getStoredItemTypes());
            for (var resource : List.of(Items.PAPER, Items.STICK, Items.DIAMOND)) {
                assertEquals(1, cell.insert(AEItemKey.of(resource), 1, Actionable.MODULATE, null));
            }
            assertEquals(5, cell.getStoredItemTypes());
            assertEquals(0, cell.insert(AEItemKey.of(Items.COAL), 1, Actionable.SIMULATE, null));
            assertEquals(0, cell.insert(AEItemKey.of(Items.COAL), 1, Actionable.MODULATE, null));
            var restored = (ECOBulkStorageCell) item.createCellInventory(stack.copy(), null);
            assertEquals(Long.MAX_VALUE, restored.getRemainingItemCount());
            assertEquals(CellState.NOT_EMPTY, restored.getStatus());
            assertEquals(1, restored.insert(ingot, 1, Actionable.MODULATE, null));
            assertEquals(1, restored.extract(AEItemKey.of(Items.PAPER), 1, Actionable.MODULATE, null));
            assertEquals(1, restored.insert(AEItemKey.of(Items.COAL), 1, Actionable.MODULATE, null));
            assertEquals(5, restored.getStoredItemTypes());
        }
    }

    @Test
    void addingMarkerConvertsExistingPhysicalStockWithoutSimulationWritesAndPreservesRemainders() {
        var stack = stack();
        try (var types = mockStatic(AEKeyTypesInternal.class)) {
            types.when(AEKeyTypesInternal::getAllTypes).thenReturn(Set.of(AEKeyType.items()));
            var item = (BiggerAE2BulkStorageCellItem) stack.getItem();
            var cell = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            var nugget = AEItemKey.of(Items.IRON_NUGGET);
            var ingot = AEItemKey.of(Items.IRON_INGOT);
            var block = AEItemKey.of(Items.IRON_BLOCK);
            assertEquals(82, cell.insert(nugget, 82, Actionable.MODULATE, null));
            assertEquals(9, cell.insert(ingot, 9, Actionable.MODULATE, null));
            assertEquals(1, cell.insert(block, 1, Actionable.MODULATE, null));
            assertEquals(3, cell.getStoredItemTypes());
            assertEquals(82, cell.getAvailableStacks().get(nugget));
            assertEquals(9, cell.getAvailableStacks().get(ingot));
            assertEquals(1, cell.getAvailableStacks().get(block));
            assertTrue(cell.getConversionPatterns().isEmpty());
            var config = item.getConfigInventory(stack);
            config.setStack(0, new GenericStack(block, 0));
            var before = stack.copy();
            assertEquals(1, cell.getStoredItemTypes());
            assertEquals(3, cell.getAvailableStacks().get(block));
            assertEquals(1, cell.getAvailableStacks().get(nugget));
            assertEquals(3, cell.extract(block, 3, Actionable.SIMULATE, null));
            assertEquals(1, cell.insert(ingot, 1, Actionable.SIMULATE, null));
            assertFalse(cell.getConversionPatterns().isEmpty());
            assertEquals(before.getComponents(), stack.getComponents());
            var restored = (ECOBulkStorageCell) item.createCellInventory(stack.copy(), null);
            assertEquals(3, restored.extract(block, 3, Actionable.SIMULATE, null));
            config.clear();
            assertEquals(block, config.getKey(0));
            assertEquals(3, cell.extract(block, 3, Actionable.MODULATE, null));
            var restoredStack = stack.copy();
            restored = (ECOBulkStorageCell) item.createCellInventory(restoredStack, null);
            assertEquals(BigInteger.ONE, restored.getExactUsedBytes());
            assertEquals(1, restored.extract(nugget, 1, Actionable.MODULATE, null));
            item.getConfigInventory(restoredStack).clear();
            assertTrue(restored.getEffectiveConfiguredFilters().isEmpty());
            assertEquals(9, restored.insert(ingot, 9, Actionable.MODULATE, null));
            assertEquals(9, restored.getAvailableStacks().get(ingot));
            assertEquals(0, restored.extract(block, 1, Actionable.MODULATE, null));
            assertTrue(restored.getConversionPatterns().isEmpty());
        }
    }

    @Test
    void markedHistoricalBalancesCannotBeRepricedWhenRecipesChange() {
        var stack = stack();
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        stack.set(AEComponents.STORAGE_CELL_CONFIG_INV, List.of(new GenericStack(block, 0)));
        try (var types = mockStatic(AEKeyTypesInternal.class)) {
            types.when(AEKeyTypesInternal::getAllTypes).thenReturn(Set.of(AEKeyType.items()));
            var item = (BiggerAE2BulkStorageCellItem) stack.getItem();
            var cell = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            assertEquals(9, cell.insert(ingot, 9, Actionable.MODULATE, null));
            ECOCompressionService.install(List.of(new BulkUnits(List.of(nugget, ingot, block),
                List.of(BigInteger.ONE, BigInteger.valueOf(4), BigInteger.valueOf(36)))));
            var restored = (ECOBulkStorageCell) item.createCellInventory(stack.copy(), null);
            assertEquals(1, restored.getRecoveringEntryCount());
            assertEquals(BigInteger.valueOf(81), restored.getExactUsedBytes());
            assertEquals(81, restored.getAvailableStacks().get(nugget));
            assertEquals(0, restored.insert(ingot, 1, Actionable.MODULATE, null));
            assertEquals(0, restored.extract(block, 1, Actionable.MODULATE, null));
            assertEquals(81, restored.extract(nugget, 81, Actionable.MODULATE, null));
            assertTrue(restored.canFitInsideCell());
        }
    }

    @Test
    void configuredMarkersRestrictInsertsAndClearingThemRestoresUnfilteredStorage() {
        var stack = stack();
        try (var types = mockStatic(AEKeyTypesInternal.class)) {
            types.when(AEKeyTypesInternal::getAllTypes).thenReturn(Set.of(AEKeyType.items()));
            var item = (BiggerAE2BulkStorageCellItem) stack.getItem();
            var cell = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            var paper = AEItemKey.of(Items.PAPER);
            assertEquals(2, cell.insert(paper, 2, Actionable.MODULATE, null));
            var config = item.getConfigInventory(stack);
            config.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 0));
            assertEquals(0, cell.insert(paper, 1, Actionable.MODULATE, null));
            assertEquals(1, cell.insert(AEItemKey.of(Items.IRON_BLOCK), 1, Actionable.SIMULATE, null));
            assertEquals(1, cell.extract(paper, 1, Actionable.MODULATE, null));
            config.clear();
            assertEquals(1, cell.insert(paper, 1, Actionable.MODULATE, null));
            assertEquals(2, cell.getAvailableStacks().get(paper));
        }
    }

    @Test
    void legacyExactQuantitiesRemainPhysicalWithoutCreatingMarkersAndSurviveReload() {
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        ECOCompressionService.install(List.of(new BulkUnits(List.of(ingot, block),
            List.of(BigInteger.ONE, BigInteger.valueOf(9)))));
        var stack = stack();
        var item = (BiggerAE2BulkStorageCellItem) stack.getItem();
        BigInteger amount = BigInteger.ONE.shiftLeft(160).add(BigInteger.TEN);
        stack.set(BiggerAE2SingularityContents.TYPE, new BiggerAE2SingularityContents(List.of(
            new BiggerAE2SingularityContents.Entry(ingot, amount),
            new BiggerAE2SingularityContents.Entry(block, BigInteger.valueOf(2)))));
        try (var types = mockStatic(AEKeyTypesInternal.class)) {
            types.when(AEKeyTypesInternal::getAllTypes).thenReturn(Set.of(AEKeyType.items()));
            var cell = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            assertFalse(stack.has(BiggerAE2SingularityContents.TYPE));
            assertEquals(amount.add(BigInteger.valueOf(2)), cell.getExactUsedBytes());
            assertEquals(2, cell.getStoredItemTypes());
            assertTrue(cell.getEffectiveConfiguredFilters().isEmpty());
            assertTrue(cell.getConversionPatterns().isEmpty());
            var restored = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            assertEquals(cell.getExactUsedBytes(), restored.getExactUsedBytes());
            assertEquals(9, restored.extract(ingot, 9, Actionable.SIMULATE, null));
            assertEquals(1, restored.extract(block, 1, Actionable.MODULATE, null));
            var reloaded = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            assertEquals(amount.add(BigInteger.ONE), reloaded.getExactUsedBytes());
        }
    }

    @Test
    void compressionWorksAndMigrationCannotExtractAnotherMatrix() {
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        ECOCompressionService.install(List.of(new BulkUnits(List.of(ingot, block),
            List.of(BigInteger.ONE, BigInteger.valueOf(9)))));
        var stack = stack();
        stack.set(AEComponents.STORAGE_CELL_CONFIG_INV, List.of(new GenericStack(ingot, 0)));
        try (var types = mockStatic(AEKeyTypesInternal.class)) {
            types.when(AEKeyTypesInternal::getAllTypes).thenReturn(Set.of(AEKeyType.items()));
            var item = (BiggerAE2BulkStorageCellItem) stack.getItem();
            var cell = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            assertEquals(5, cell.getTotalItemTypes());
            assertEquals(25, item.upgradedTypeLimit());
            assertEquals(18, cell.insert(ingot, 18, Actionable.MODULATE, null));
            try (var scope = StorageExtractionExclusions.openBulkMigration()) {
                assertEquals(0, cell.extract(block, 2, Actionable.SIMULATE, null));
                assertEquals(0, cell.extract(block, 2, Actionable.MODULATE, null));
            }
            assertEquals(2, cell.extract(block, 2, Actionable.MODULATE, null));
            assertEquals(BigInteger.ZERO, cell.getExactUsedBytes());
            assertTrue(BiggerAE2SingularityPowerService.isSingularity(stack));
        }
    }

    private static ItemStack stack() {
        var item = mock(BiggerAE2BulkStorageCellItem.class, CALLS_REAL_METHODS);
        doReturn(ECOTier.L9).when(item).getTier();
        doReturn(AEKeyType.items()).when(item).getKeyType();
        doReturn(new ECOCellType(Component.literal("Test"), 5)).when(item).getCellType();
        doReturn(5).when(item).getTotalTypes();
        doReturn(Long.MAX_VALUE).when(item).getBytes();
        doReturn(65_536).when(item).getBytesPerType();
        doReturn(mock(IUpgradeInventory.class)).when(item).getUpgrades(any());
        var stack = spy(new ItemStack(Items.PAPER));
        doReturn(item).when(stack).getItem();
        return stack;
    }
}
