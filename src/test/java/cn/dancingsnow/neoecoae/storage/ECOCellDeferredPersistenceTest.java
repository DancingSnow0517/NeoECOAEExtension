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

    @Test void driveFlushesBeforeItsStackIsReadAndCoalescesWorkerNotifications() throws Exception {
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
        field(drive, "persistenceNotifyQueued", new java.util.concurrent.atomic.AtomicBoolean());
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
        verify(drive, atLeastOnce()).setChanged();
        verify(server, never()).executeIfPossible(any());

        when(server.isSameThread()).thenReturn(false);
        drive.notifyPersistence();
        drive.notifyPersistence();
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(server, times(1)).executeIfPossible(task.capture());
        when(server.isSameThread()).thenReturn(true);
        task.getValue().run();
        verify(drive, atMost(1)).markForUpdate();
        when(server.isSameThread()).thenReturn(false);
        drive.notifyPersistence();
        verify(server, times(2)).executeIfPossible(any());
    }

    @Test void stoppedServerStillMarksFinalCellChangesDirtyOnItsOwnThread() throws Exception {
        var drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        var level = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        field(drive, "level", level);
        when(level.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(server.isStopped()).thenReturn(true);
        doNothing().when(drive).setChanged();
        drive.notifyPersistence();
        verify(drive).setChanged();
        verify(drive, never()).getCellInventory();
        verify(drive, never()).markForUpdate();
        verify(server, never()).executeIfPossible(any());
    }

    @Test void drivePublishesOnlyAppearanceAndNeverPollsTheLiveCell() throws Exception {
        InventoryTestBootstrap.initializeUi();
        var drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        var level = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        field(drive, "level", level);
        field(drive, "worldPosition", net.minecraft.core.BlockPos.ZERO);
        field(drive, "blockState", net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        field(drive, "renderedCell", ItemStack.EMPTY);
        var full = new ItemStack(Items.PAPER);
        full.set(AEComponents.STORAGE_CELL_INV, List.of(new GenericStack(AEItemKey.of(Items.STONE), 100_000L)));
        var custom = new net.minecraft.nbt.CompoundTag();
        custom.putBoolean("neoecoae_infinite_member", true);
        full.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(custom));
        field(drive, "cellStack", full);
        drive.setChanged();
        var published = ECODriveBlockEntity.class.getDeclaredField("renderedCell");
        published.setAccessible(true);
        var rendered = (ItemStack) published.get(drive);
        assertTrue(rendered.is(Items.PAPER));
        assertFalse(rendered.has(AEComponents.STORAGE_CELL_INV));
        assertFalse(rendered.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
        assertTrue(drive.isInfiniteMember());
        field(drive, "syncStorage", new com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage(drive));
        assertTrue(java.util.Arrays.stream(drive.getRootStorage().getNonLazyFields()).noneMatch(ref -> ref.readRaw() == full));
        assertTrue(java.util.Arrays.stream(drive.getRootStorage().getSyncFields()).noneMatch(ref -> ref.readRaw() == full));
        assertEquals(100_000L, stored(full, AEItemKey.of(Items.STONE)));
    }
}
