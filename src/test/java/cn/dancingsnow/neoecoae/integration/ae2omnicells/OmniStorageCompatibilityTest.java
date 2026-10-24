package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OmniStorageCompatibilityTest {
    private static int nextItem;
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static ECOUniversalStorageCellItem item(long bytes) {
        var item = new ECOUniversalStorageCellItem(new Item.Properties(), ECOTier.L9,
            () -> new ECOCellType(Component.literal("Quantum Omni"), 0), 59049, -1, bytes);
        net.minecraftforge.registries.ForgeRegistries.ITEMS.register(
            new net.minecraft.resources.ResourceLocation("neoecoae_test", "omni_" + nextItem++), item);
        return item;
    }

    @Test void quantumDisksKeepLongCapacityAndUnlimitedTypes() {
        for (int power : new int[] {28, 30, 32}) {
            var item = item(1L << power);
            assertEquals(1L << power, item.getOmniTotalBytes());
            var cell = new ECOUniversalStorageCell(mock(StorageCell.class), new ItemStack(item), item);
            assertTrue(cell.hasInfiniteTypeCapacity());
            assertEquals(1L << power, cell.getTotalBytes());
        }
    }

    @Test void legacyStringIdentityIsRetainedAndInvalidIdentityIsRejected() {
        UUID id = UUID.randomUUID(); CompoundTag tag = new CompoundTag();
        tag.putString("ae_universal_cell_uuid", id.toString());
        assertEquals(id, ECOUniversalCellHandler.resolveStorageId(null, tag));
        tag.putString("ae_universal_cell_uuid", "broken");
        assertNull(ECOUniversalCellHandler.resolveStorageId(null, tag));
        assertNull(ECOUniversalCellHandler.resolveStorageId(null, new CompoundTag()));
    }

    @Test void aNonemptyOmniDiskCannotBeNestedEvenIfTheDelegateReportsItCan() {
        StorageCell delegate = mock(StorageCell.class); KeyCounter contents = new KeyCounter();
        contents.add(AEItemKey.of(Items.DIAMOND), 64);
        when(delegate.getAvailableStacks()).thenReturn(contents);
        when(delegate.canFitInsideCell()).thenReturn(true);
        var item = item(1L << 32);
        var cell = new ECOUniversalStorageCell(delegate, new ItemStack(item), item);
        assertFalse(cell.canFitInsideCell());
        contents.clear(); assertTrue(cell.canFitInsideCell());
    }

    @Test void restorePreflightUsesCallerSnapshotWithoutChangingLiveInventory() {
        StorageCell delegate = mock(StorageCell.class); var key = AEItemKey.of(Items.DIAMOND);
        when(delegate.insert(eq(key), eq(1L), eq(Actionable.SIMULATE), any())).thenReturn(1L);
        var item = item(64); var cell = new ECOUniversalStorageCell(delegate, new ItemStack(item), item);
        KeyCounter snapshot = new KeyCounter(); snapshot.add(key, 8);
        long accepted = cell.simulateInsertForMigration(key, 1000, snapshot, 1, 8);
        assertEquals(64L * key.getType().getAmountPerByte() - 8, accepted);
        assertEquals(8, snapshot.get(key));
        verify(delegate, never()).extract(any(), anyLong(), any(), any());
        verify(delegate, never()).insert(any(), anyLong(), eq(Actionable.MODULATE), any());
    }

    @Test void normalIoDelegatesTheExactKeyAndAmount() {
        StorageCell delegate = mock(StorageCell.class); var key = AEItemKey.of(Items.DIAMOND);
        var source = IActionSource.empty(); when(delegate.extract(key, 64, Actionable.MODULATE, source)).thenReturn(64L);
        var item = item(1L << 32); var cell = new ECOUniversalStorageCell(delegate, new ItemStack(item), item);
        assertEquals(64, cell.extract(key, 64, Actionable.MODULATE, source));
        verify(delegate).extract(key, 64, Actionable.MODULATE, source);
    }
}
