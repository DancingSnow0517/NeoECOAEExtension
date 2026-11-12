package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LargeWorkstationOutputCacheTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName());
        }
    }

    @Test
    void unchangedLedgerIsNotRescannedAndEachBatchRetainsItsOwnEntryCount() throws Exception {
        var fixture = fixture();
        var first = batch();
        var second = batch();
        var ledger = spy((KeyCounter) field(first, "pendingOutput").get(first));
        field(first, "pendingOutput").set(first, ledger);
        enqueue(fixture.controller, first);
        enqueue(fixture.controller, second);
        AEKey diamond = AEItemKey.of(Items.DIAMOND);
        invoke(fixture.controller, "addBatchOutputs", first, amount(diamond, 5));
        invoke(fixture.controller, "addBatchOutputs", second, amount(diamond, 7));
        assertEquals(2, count(fixture.controller), "Two batch ledgers are not merged by resource key");
        for (int i = 0; i < 100; i++) assertEquals(2, count(fixture.controller));
        verify(ledger, times(1)).iterator();
        invoke(fixture.controller, "removeBatchOutput", first, diamond, 2L);
        assertEquals(2, count(fixture.controller));
        invoke(fixture.controller, "removeBatchOutput", first, diamond, 3L);
        assertEquals(1, count(fixture.controller));
        invoke(fixture.controller, "addBatchOutputs", first, amount(diamond, 1));
        assertEquals(2, count(fixture.controller), "Zero-to-positive transitions invalidate the count");
    }

    @Test
    void partialDeliveryAndRetryRecountOnlyActuallyAcceptedOutputs() throws Exception {
        var fixture = fixture();
        var batch = batch();
        enqueue(fixture.controller, batch);
        AEKey diamond = AEItemKey.of(Items.DIAMOND);
        invoke(fixture.controller, "addBatchOutputs", batch, amount(diamond, 10));
        assertEquals(1, count(fixture.controller));
        when(fixture.storage.insert(eq(diamond), anyLong(), eq(Actionable.MODULATE), any()))
            .thenReturn(4L).thenReturn(6L);
        assertEquals(false, invoke(fixture.controller, "deliverBatchOutputs", batch));
        assertEquals(6, ((KeyCounter) field(batch, "pendingOutput").get(batch)).get(diamond));
        assertEquals(1, count(fixture.controller));
        assertEquals(true, invoke(fixture.controller, "deliverBatchOutputs", batch));
        assertEquals(0, count(fixture.controller));
        verify(fixture.storage).insert(eq(diamond), eq(10L), eq(Actionable.MODULATE), any());
        verify(fixture.storage).insert(eq(diamond), eq(6L), eq(Actionable.MODULATE), any());
    }

    @Test
    @SuppressWarnings("deprecation")
    void cancellationInvalidatesAnAlreadyReadOutputCount() throws Exception {
        var fixture = fixture();
        var batch = batch();
        enqueue(fixture.controller, batch);
        invoke(fixture.controller, "addBatchOutputs", batch, amount(AEItemKey.of(Items.DIAMOND), 5));
        assertEquals(1, count(fixture.controller));
        fixture.controller.returnStoredInputs();
        assertEquals(0, count(fixture.controller));
        assertTrue(((java.util.Deque<?>) field(fixture.controller, "pendingBatches").get(fixture.controller)).isEmpty());
        verify(fixture.storage).insert(eq(AEItemKey.of(Items.DIAMOND)), eq(5L), eq(Actionable.MODULATE), any());
        verify(fixture.storage).insert(eq(AEItemKey.of(Items.IRON_INGOT)), eq(3L), eq(Actionable.MODULATE), any());
    }

    @Test
    void tickCombinesProviderUpdatesAndFlushesThemOnExceptionalExit() throws Exception {
        var fixture = fixture();
        var controller = fixture.controller;
        field(controller, "level").set(controller, null);
        field(controller, "formed").setBoolean(controller, true);
        var cluster = mock(NEIntegratedWorkingStationCluster.class);
        var communication = mock(ECOLargeIntegratedWorkingStationInterfaceBlockEntity.class);
        doReturn(communication).when(cluster).getCommunication();
        var node = mock(IManagedGridNode.class);
        when(communication.getMainNode()).thenReturn(node);
        field(controller, "cluster").set(controller, cluster);
        doAnswer(ignored -> {
            invoke(controller, "requestCommunicationProviderUpdate");
            invoke(controller, "requestCommunicationProviderUpdate");
            throw new IllegalStateException("interrupted transfer");
        }).when(controller).getInputTank();
        try (var updates = mockStatic(ICraftingProvider.class)) {
            assertThrows(IllegalStateException.class, () -> controller.tickingRequest(mock(IGridNode.class), 1));
            updates.verify(() -> ICraftingProvider.requestUpdate(node), times(1));
            invoke(controller, "requestCommunicationProviderUpdate");
            updates.verify(() -> ICraftingProvider.requestUpdate(node), times(2));
        }
        assertEquals(0, field(controller, "communicationUpdateDepth").getInt(controller));
    }

    private static Fixture fixture() throws Exception {
        var controller = mock(ECOLargeIntegratedWorkingStationBlockEntity.class, CALLS_REAL_METHODS);
        field(controller, "pendingBatches").set(controller, new ArrayDeque<>());
        field(controller, "level").set(controller, mock(Level.class));
        var node = mock(IManagedGridNode.class);
        var grid = mock(IGrid.class);
        var service = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        doReturn(node).when(controller).getMainNode();
        doNothing().when(controller).setChanged();
        doNothing().when(controller).setProcessingTime(anyInt());
        doNothing().when(controller).setWorking(anyBoolean());
        when(node.getGrid()).thenReturn(grid);
        when(grid.getStorageService()).thenReturn(service);
        when(service.getInventory()).thenReturn(storage);
        when(storage.insert(any(), anyLong(), eq(Actionable.MODULATE), any()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        return new Fixture(controller, storage);
    }

    private static Object batch() throws Exception {
        var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(1L, 1L, 0, 1,
            amount(AEItemKey.of(Items.IRON_INGOT), 3), amount(AEItemKey.of(Items.DIAMOND), 10),
            null, Identifier.parse("test:output_cache"), null, null, 0L);
    }

    private static void enqueue(ECOLargeIntegratedWorkingStationBlockEntity controller, Object batch) throws Exception {
        invoke(controller, "enqueueBatch", batch, null);
    }

    private static long count(ECOLargeIntegratedWorkingStationBlockEntity controller) throws Exception {
        return (long) invoke(controller, "getPendingOutputStackCount");
    }

    private static Object invoke(Object target, String name, Object... arguments) throws Exception {
        for (var method : ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                method.setAccessible(true);
                return method.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Field field(Object target, String name) throws Exception {
        for (var type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static KeyCounter amount(AEKey key, long amount) {
        var result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private record Fixture(ECOLargeIntegratedWorkingStationBlockEntity controller, MEStorage storage) {}
}
