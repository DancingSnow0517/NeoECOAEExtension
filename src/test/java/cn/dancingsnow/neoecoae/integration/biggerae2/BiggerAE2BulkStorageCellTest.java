package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
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
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.ChatFormatting;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import org.junit.jupiter.api.BeforeAll;
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
             var config = mockStatic(AEConfig.class)) {
            factory.when(() -> ECOStorageCellItem.getCellInventory(stack)).thenReturn(cell);
            var lines = new ArrayList<Component>();
            item.appendHoverText(stack, null, lines, TooltipFlag.NORMAL);
            assertEquals(2, lines.size());
            var line = (TranslatableContents) lines.get(0).getContents();
            assertEquals("tooltip.neoecoae.bulk.used_types", line.getKey());
            assertEquals(Tooltips.ofUnformattedNumberWithRatioColor(0, 0, false), line.getArgs()[0]);
            assertEquals(Tooltips.ofUnformattedNumber(5), line.getArgs()[1]);
            var compression = (TranslatableContents) lines.get(1).getContents();
            assertEquals("tooltip.neoecoae.bulk.compression", compression.getKey());
            var enabled = (Component) compression.getArgs()[0];
            assertEquals("tooltip.neoecoae.bulk.compression_enabled", ((TranslatableContents) enabled.getContents()).getKey());
            assertEquals(ChatFormatting.GREEN.getColor(), enabled.getStyle().getColor().getValue());
            assertTrue(item.getTooltipImage(stack).isEmpty());

            var aeConfig = mock(AEConfig.class);
            config.when(AEConfig::instance).thenReturn(aeConfig);
            when(aeConfig.getTooltipMaxCellContentShown()).thenReturn(5);
            var key = AEItemKey.of(Items.IRON_INGOT);
            cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem.writeConfiguredStacks(stack, List.of(new GenericStack(key, 0)));
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
            preview = (StorageCellTooltipComponent) item.getTooltipImage(stack).orElseThrow();
            assertEquals(List.of(new GenericStack(key, 42)), preview.content());
        }
    }

    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        // AE2 item definitions read AEConfig while initializing; load it before any test mocks AEConfig.
        if (AEConfig.instance() == null) {
            AEConfig.load(java.nio.file.Files.createTempDirectory("neoecoae-bigger-ae2-bulk-config"));
        }
        Class.forName(appeng.core.definitions.AEItems.class.getName());
    }

    @Test
    void legacyExactQuantitiesMergeByChainAndSurviveReloadWithoutReimporting() {
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        ECOCompressionService.install(List.of(new BulkUnits(List.of(ingot, block),
            List.of(BigInteger.ONE, BigInteger.valueOf(9)))));
        var stack = stack();
        var item = (BiggerAE2BulkStorageCellItem) stack.getItem();
        BigInteger amount = BigInteger.ONE.shiftLeft(160).add(BigInteger.TEN);
        BiggerAE2SingularityContents.write(stack, new BiggerAE2SingularityContents(List.of(
            new BiggerAE2SingularityContents.Entry(ingot, amount),
            new BiggerAE2SingularityContents.Entry(block, BigInteger.valueOf(2)))));
        {
            var cell = (ECOBulkStorageCell) item.createCellInventory(stack, null);
            assertTrue(BiggerAE2SingularityContents.read(stack).entries().isEmpty());
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
        cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem.writeConfiguredStacks(stack, List.of(new GenericStack(ingot, 0)));
        {
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
