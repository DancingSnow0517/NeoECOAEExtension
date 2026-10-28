package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiggerAE2BulkStorageCellTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void legacyExactQuantitiesMergeByChainAndSurviveReloadWithoutReimporting() {
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
            assertEquals(amount.add(BigInteger.valueOf(18)), cell.getExactUsedBytes());
            assertEquals(1, cell.getStoredItemTypes());
            assertEquals(1, cell.getEffectiveConfiguredFilters().size());
            var restored = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            assertEquals(cell.getExactUsedBytes(), restored.getExactUsedBytes());
            assertEquals(9, restored.extract(ingot, 9, Actionable.SIMULATE, null));
            assertEquals(1, restored.extract(block, 1, Actionable.MODULATE, null));
            var reloaded = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            assertEquals(amount.add(BigInteger.valueOf(9)), reloaded.getExactUsedBytes());
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
            assertEquals(25, cell.getTotalItemTypes());
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
        doReturn(new ECOCellType(Component.literal("Test"), 25)).when(item).getCellType();
        doReturn(25).when(item).getTotalTypes();
        doReturn(Long.MAX_VALUE).when(item).getBytes();
        doReturn(65_536).when(item).getBytesPerType();
        doReturn(mock(IUpgradeInventory.class)).when(item).getUpgrades(any());
        var stack = spy(new ItemStack(Items.PAPER));
        doReturn(item).when(stack).getItem();
        return stack;
    }
}
