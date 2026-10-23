package cn.dancingsnow.neoecoae.compat.ae2lt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import appeng.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import java.lang.reflect.Field;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DimensionDataStorage;

class ECOAe2LtCancellationBridgeTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void actualTimeWheelJobPublishesCancellationBeforeRecoveringLoadedWorkers() throws Exception {
        var id = UUID.randomUUID();
        var decisions = new ECOCraftingJobLifecycle();
        var server = mock(MinecraftServer.class);
        var level = mock(ServerLevel.class);
        var storage = mock(DimensionDataStorage.class);
        when(level.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(storage);
        doReturn(decisions).when(storage).computeIfAbsent(any(), eq("neoecoae_crafting_job_lifecycle"));
        var cpu = mock(TimeWheelCraftingCPU.class);
        when(cpu.getLevel()).thenReturn(level);
        var logic = mock(Ae2LtTimeWheelCraftingCpuLogic.class);
        field(Ae2LtTimeWheelCraftingCpuLogic.class, "cpu").set(logic, cpu);
        var jobField = field(Ae2LtTimeWheelCraftingCpuLogic.class, "job");
        var job = mock(jobField.getType());
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(id);
        field(jobField.getType(), "link").set(job, link);
        jobField.set(logic, job);
        var worker = mock(ECOCraftingWorkerBlockEntity.class);
        when(worker.getLevel()).thenReturn(level);
        doAnswer(call -> {
            assertTrue(ECOCraftingJobLifecycle.isTerminated(level, id));
            assertSame(job, jobField.get(logic), "notification must precede CPU job clearing");
            return null;
        }).when(worker).recoverTerminatedJob(id);
        try (var workers = mockStatic(ECOCraftingWorkerBlockEntity.class)) {
            workers.when(ECOCraftingWorkerBlockEntity::getLoadedServerWorkers).thenReturn(Set.of(worker));
            ECOAe2LtCancellationBridge.cancel(logic);
        }
        assertTrue(ECOCraftingJobLifecycle.isTerminated(level, id));
        verify(worker).recoverTerminatedJob(id);

        // The workstation observes the same durable decision on its next tick. An unfinished
        // batch returns its owned inputs; a finished one returns its real outputs without CPU routing.
        var stationType = cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity.class;
        var station = mock(stationType, RETURNS_DEEP_STUBS);
        field(net.minecraft.world.level.block.entity.BlockEntity.class, "level").set(station, level);
        var grid = mock(appeng.api.networking.IGrid.class, RETURNS_DEEP_STUBS);
        when(station.getMainNode().getGrid()).thenReturn(grid);
        var network = grid.getStorageService().getInventory();
        when(network.insert(any(), anyLong(), any(), any())).thenAnswer(call -> call.getArgument(1));
        var input = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT);
        var output = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.DIAMOND);
        var inputs = new appeng.api.stacks.KeyCounter();
        inputs.add(input, 4);
        var outputs = new appeng.api.stacks.KeyCounter();
        outputs.add(output, 1);
        var batchType = Class.forName(stationType.getName() + "$PendingBatch");
        var constructor = batchType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var unfinished = constructor.newInstance(1L, 1L, 0, 1, inputs, outputs, id,
                net.minecraft.resources.ResourceLocation.parse("test:cancel"), null, null, 0L);
        var finished = constructor.newInstance(1L, 1L, 0, 1, new appeng.api.stacks.KeyCounter(), outputs, id,
                net.minecraft.resources.ResourceLocation.parse("test:cancel"), null, null, 0L);
        ((appeng.api.stacks.KeyCounter) field(batchType, "pendingOutput").get(finished)).add(output, 1);
        var queue = new java.util.ArrayDeque<>();
        queue.add(unfinished);
        queue.add(finished);
        field(stationType, "pendingBatches").set(station, queue);
        var tick = stationType.getDeclaredMethod("tickPendingBatch");
        tick.setAccessible(true);
        tick.invoke(station);
        tick.invoke(station);
        assertTrue(queue.isEmpty(), "cancelled workstation batches must stop blocking the queue");
        verify(network).insert(eq(input), eq(4L), any(), any());
        verify(network).insert(eq(output), eq(1L), any(), any());
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Test
    void completionPublishesACompletedDecisionWithoutCancellingWorkers() {
        var level = mock(Level.class);
        var jobId = UUID.randomUUID();
        var logic = new FakeLogic(new FakeCpu(level), new FakeJob(new FakeLink(jobId)));
        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            ECOAe2LtCancellationBridge.complete(logic);
            lifecycle.verify(() -> ECOCraftingJobLifecycle.finish(level, jobId, true));
            lifecycle.verifyNoMoreInteractions();
        }
    }

    @Test
    void anAlreadyClearedJobIsAQuietNoOp() {
        assertDoesNotThrow(() -> ECOAe2LtCancellationBridge.cancel(new FakeLogic(new FakeCpu(null), null)));
        assertDoesNotThrow(() -> ECOAe2LtCancellationBridge.complete(new FakeLogic(new FakeCpu(null), null)));
    }

    private static final class FakeLogic {
        private final FakeCpu cpu;
        private final FakeJob job;

        private FakeLogic(FakeCpu cpu, FakeJob job) {
            this.cpu = cpu;
            this.job = job;
        }
    }

    private static final class FakeCpu {
        private final Level level;

        private FakeCpu(Level level) {
            this.level = level;
        }

        private Level getLevel() {
            return level;
        }
    }

    private static final class FakeJob {
        private final FakeLink link;

        private FakeJob(FakeLink link) {
            this.link = link;
        }
    }

    private static final class FakeLink {
        private final UUID id;

        private FakeLink(UUID id) {
            this.id = id;
        }

        private UUID getCraftingID() {
            return id;
        }
    }
}
