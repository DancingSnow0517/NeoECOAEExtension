package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationOutputRoutingTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName());
        }
    }

    @Test
    void completionDuringDeliveryReturnsRemainingOutputsToStorageInTheSameTick() throws Exception {
        var fixture = fixture();
        var completed = new AtomicBoolean();
        when(fixture.router.neoecoae$insertIntoCpuForJob(eq(fixture.jobId), any(), anyLong(), any()))
            .thenAnswer(call -> completed.getAndSet(true) ? 0L : call.getArgument(2));
        var storage = fixture.grid.getStorageService().getInventory();
        when(storage.insert(any(), anyLong(), any(), any())).thenAnswer(call -> call.getArgument(1));

        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            lifecycle.when(() -> ECOCraftingJobLifecycle.isTerminated(fixture.level, fixture.jobId))
                .thenAnswer(call -> completed.get());
            assertTrue(deliver(fixture));
        }
        assertEquals(0, fixture.pending.get(AEItemKey.of(Items.DIAMOND)));
        assertEquals(0, fixture.pending.get(AEItemKey.of(Items.BUCKET)));
        verify(fixture.router).neoecoae$insertIntoCpuForJob(eq(fixture.jobId), any(), anyLong(), any());
        verify(storage).insert(any(), anyLong(), eq(Actionable.MODULATE), any());
    }

    @Test
    void completionAfterPartialCpuReceiptReturnsTheRestOfThatStackAndOtherOutputs() throws Exception {
        var fixture = fixture();
        var completed = new AtomicBoolean();
        var delivered = new AtomicLong();
        when(fixture.router.neoecoae$insertIntoCpuForJob(eq(fixture.jobId), any(), anyLong(), any()))
            .thenAnswer(call -> {
                assertFalse(completed.getAndSet(true));
                long accepted = (long) call.getArgument(2) - 1;
                delivered.addAndGet(accepted);
                return accepted;
            });
        var storage = fixture.grid.getStorageService().getInventory();
        when(storage.insert(any(), anyLong(), any(), any())).thenAnswer(call -> {
            long accepted = call.getArgument(1);
            delivered.addAndGet(accepted);
            return accepted;
        });
        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            lifecycle.when(() -> ECOCraftingJobLifecycle.isTerminated(fixture.level, fixture.jobId))
                .thenAnswer(call -> completed.get());
            assertTrue(deliver(fixture));
        }
        assertEquals(6, delivered.get());
        assertEquals(0, fixture.pending.get(AEItemKey.of(Items.DIAMOND)));
        assertEquals(0, fixture.pending.get(AEItemKey.of(Items.BUCKET)));
        verify(storage, times(2)).insert(any(), anyLong(), eq(Actionable.MODULATE), any());
    }

    @Test
    void unavailableActiveCpuKeepsOutputsOwnedInsteadOfGivingThemToAnotherJob() throws Exception {
        var fixture = fixture();
        assertFalse(deliver(fixture));
        assertEquals(4, fixture.pending.get(AEItemKey.of(Items.DIAMOND)));
        assertEquals(2, fixture.pending.get(AEItemKey.of(Items.BUCKET)));
        verify(fixture.grid.getStorageService().getInventory(), never()).insert(any(), anyLong(), any(), any());
    }

    @Test
    void partialActiveCpuReceiptRetainsItsRemainderWithoutUsingGeneralStorage() throws Exception {
        var fixture = fixture();
        when(fixture.router.neoecoae$insertIntoCpuForJob(eq(fixture.jobId), any(), anyLong(), any()))
            .thenReturn(1L);
        assertFalse(deliver(fixture));
        assertEquals(5, fixture.pending.get(AEItemKey.of(Items.DIAMOND))
            + fixture.pending.get(AEItemKey.of(Items.BUCKET)));
        verify(fixture.grid.getStorageService().getInventory(), never()).insert(any(), anyLong(), any(), any());
    }

    @Test
    void partialStorageAfterCompletionRetainsOnlyTheUndeliveredAmountForRetry() throws Exception {
        var fixture = fixture();
        var storage = fixture.grid.getStorageService().getInventory();
        when(storage.insert(any(), anyLong(), any(), any())).thenAnswer(call -> (long) call.getArgument(1) - 1);
        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            lifecycle.when(() -> ECOCraftingJobLifecycle.isTerminated(fixture.level, fixture.jobId)).thenReturn(true);
            assertFalse(deliver(fixture));
            long remaining = fixture.pending.get(AEItemKey.of(Items.DIAMOND))
                + fixture.pending.get(AEItemKey.of(Items.BUCKET));
            assertTrue(remaining == 3 || remaining == 5);
            when(storage.insert(any(), anyLong(), any(), any())).thenAnswer(call -> call.getArgument(1));
            assertTrue(deliver(fixture));
        }
        assertEquals(0, fixture.pending.get(AEItemKey.of(Items.DIAMOND)));
        assertEquals(0, fixture.pending.get(AEItemKey.of(Items.BUCKET)));
        verifyNoInteractions(fixture.router);
    }

    private static Fixture fixture() throws Exception {
        var host = mock(ECOLargeIntegratedWorkingStationBlockEntity.class, CALLS_REAL_METHODS);
        var level = mock(Level.class);
        field(host, "level", level);
        var node = mock(IManagedGridNode.class);
        doReturn(node).when(host).getMainNode();
        doNothing().when(host).markForUpdate();
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(node.getGrid()).thenReturn(grid);
        var crafting = mock(CraftingService.class, withSettings().extraInterfaces(ECOCraftingOutputRouter.class));
        when(grid.getCraftingService()).thenReturn(crafting);
        var jobId = UUID.randomUUID();
        var outputs = new KeyCounter();
        outputs.add(AEItemKey.of(Items.DIAMOND), 4);
        outputs.add(AEItemKey.of(Items.BUCKET), 2);
        var batchType = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
        var constructor = batchType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var batch = constructor.newInstance(2L, 1L, 0, 1, new KeyCounter(), outputs, jobId,
            new ResourceLocation("test:output_routing"), null, null, 0L);
        var pendingField = batchType.getDeclaredField("pendingOutput");
        pendingField.setAccessible(true);
        var pending = (KeyCounter) pendingField.get(batch);
        pending.addAll(outputs);
        field(batch, "progress", 200);
        return new Fixture(host, level, grid, (ECOCraftingOutputRouter) crafting, jobId, batch, pending);
    }

    private static boolean deliver(Fixture fixture) throws Exception {
        var method = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethod(
            "deliverBatchOutputs", fixture.batch.getClass());
        method.setAccessible(true);
        return (boolean) method.invoke(fixture.host, fixture.batch);
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

    private record Fixture(ECOLargeIntegratedWorkingStationBlockEntity host, Level level, IGrid grid,
                           ECOCraftingOutputRouter router, UUID jobId, Object batch, KeyCounter pending) {}
}
