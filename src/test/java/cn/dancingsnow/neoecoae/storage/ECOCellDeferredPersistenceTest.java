package cn.dancingsnow.neoecoae.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ECOCellDeferredPersistenceTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @AfterEach void drain() { ECOCellMutationBatch.flushDeferred(); }

    private static final class Host implements ISaveProvider, DeferredCellPersistence {
        boolean deferrable = true;
        int saves;

        @Override public void saveChanges() { saves++; }
        @Override public boolean neoecoae$canDeferCellPersistence() { return deferrable; }
    }

    static ItemStack stack() {
        var item = mock(ECOStorageCellItem.class);
        when(item.getTier()).thenReturn(mock(IECOTier.class));
        when(item.getKeyType()).thenReturn(AEKeyType.items());
        when(item.getTotalTypes()).thenReturn(63);
        when(item.getCellType()).thenReturn(new ECOCellType(Component.literal("Test"), 63));
        when(item.getBytes()).thenReturn(1L << 40);
        when(item.getBytesPerType()).thenReturn(8);
        when(item.getUpgrades(any())).thenReturn(mock(IUpgradeInventory.class));
        ConfigInventory config;
        try (var types = mockStatic(AEKeyTypes.class)) {
            types.when(AEKeyTypes::getAll).thenReturn(Set.of(AEKeyType.items()));
            config = ConfigInventory.configTypes(63).supportedTypes(Set.of(AEKeyType.items())).build();
        }
        when(item.getConfigInventory(any())).thenReturn(config);
        var stack = spy(new ItemStack(Items.PAPER));
        doReturn(item).when(stack).getItem();
        return stack;
    }

    private static long stored(ItemStack stack, AEItemKey key) {
        return stack.getOrDefault(AEComponents.STORAGE_CELL_INV, List.<GenericStack>of()).stream()
            .filter(entry -> entry.what().equals(key)).mapToLong(GenericStack::amount).sum();
    }

    @Test void transfersOutsideAControllerTickAreWrittenOnceAtTheEndOfTheTick() {
        var stack = stack();
        var host = new Host();
        var cell = new ECOStorageCell(stack, host);
        var stone = AEItemKey.of(Items.STONE);
        long expected = 0;
        try (var cells = mockStatic(StorageCells.class)) {
            for (int i = 0; i < 200; i++) {
                assertEquals(7, cell.insert(stone, 7, Actionable.MODULATE, null));
                expected += 7;
                if (i % 3 == 0) {
                    assertEquals(2, cell.extract(stone, 2, Actionable.MODULATE, null));
                    expected -= 2;
                }
            }
        }
        assertEquals(0, host.saves, "no component write or host notification per transfer");
        assertEquals(0, stored(stack, stone));
        assertTrue(ECOCellMutationBatch.hasDeferred(host));
        assertEquals(expected, cell.getMigrationAmount(stone), "live amounts stay authoritative");

        ECOCellMutationBatch.flushDeferred();
        assertEquals(1, host.saves);
        assertFalse(ECOCellMutationBatch.hasDeferred(host));
        assertEquals(expected, stored(stack, stone));
        // A reload from the stack sees exactly the accepted amount: nothing lost, nothing duplicated.
        assertEquals(expected, new ECOStorageCell(stack, null).getMigrationAmount(stone));
    }

    @Test void flushingOneHostLeavesOthersPendingAndUndeferrableHostsSaveImmediately() {
        var stone = AEItemKey.of(Items.STONE);
        var firstStack = stack();
        var secondStack = stack();
        var first = new Host();
        var second = new Host();
        var immediate = new Host();
        immediate.deferrable = false;
        var immediateStack = stack();
        try (var cells = mockStatic(StorageCells.class)) {
            new ECOStorageCell(firstStack, first).insert(stone, 5, Actionable.MODULATE, null);
            new ECOStorageCell(secondStack, second).insert(stone, 6, Actionable.MODULATE, null);
            var direct = new ECOStorageCell(immediateStack, immediate);
            direct.insert(stone, 1, Actionable.MODULATE, null);
            direct.insert(stone, 1, Actionable.MODULATE, null);
        }
        assertEquals(2, immediate.saves);
        assertEquals(2, stored(immediateStack, stone));

        ECOCellMutationBatch.flushDeferred(first);
        assertEquals(5, stored(firstStack, stone));
        assertEquals(0, stored(secondStack, stone));
        assertTrue(ECOCellMutationBatch.hasDeferred(second));
        ECOCellMutationBatch.flushDeferred();
        assertEquals(6, stored(secondStack, stone));
    }

    @Test void controllerBatchesStillCoalesceInsideTheirScope() {
        var stone = AEItemKey.of(Items.STONE);
        var stack = stack();
        var host = new Host();
        var cell = new ECOStorageCell(stack, host);
        try (var cells = mockStatic(StorageCells.class); var batch = ECOCellMutationBatch.open()) {
            for (int i = 0; i < 10; i++) cell.insert(stone, 1, Actionable.MODULATE, null);
            assertFalse(ECOCellMutationBatch.hasDeferred(host), "a controller tick flushes its own batch");
        }
        assertEquals(1, host.saves);
        assertEquals(10, stored(stack, stone));
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

    @Test void driveFlushesBeforeItsStackIsReadAndQueuesOneNotification() throws Exception {
        var level = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        var drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        doNothing().when(drive).setChanged();
        doNothing().when(drive).markForUpdate();
        var stack = stack();
        field(drive, "level", level);
        field(drive, "cellStack", stack);
        assertTrue(drive.neoecoae$canDeferCellPersistence());

        var stone = AEItemKey.of(Items.STONE);
        var cell = new ECOStorageCell(stack, drive);
        try (var cells = mockStatic(StorageCells.class)) {
            for (int i = 0; i < 50; i++) cell.insert(stone, 3, Actionable.MODULATE, null);
        }
        assertEquals(0, stored(stack, stone));
        verify(server, never()).executeIfPossible(any());

        // UI extraction, automation and every other reader goes through getCellStack().
        ItemStack visible = new CellHostItemHandler(drive).getStackInSlot(0);
        assertSame(stack, visible);
        assertEquals(150, stored(visible, stone));
        assertFalse(ECOCellMutationBatch.hasDeferred(drive));

        drive.notifyPersistence();
        drive.notifyPersistence();
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(server, times(1)).executeIfPossible(task.capture());
        task.getValue().run();
        task.getValue().run();
        verify(drive, atMost(1)).markForUpdate();
        drive.notifyPersistence();
        verify(server, times(2)).executeIfPossible(any());
    }
}
