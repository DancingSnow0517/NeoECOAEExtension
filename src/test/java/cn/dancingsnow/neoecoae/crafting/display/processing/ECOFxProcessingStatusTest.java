package cn.dancingsnow.neoecoae.crafting.display.processing;

import static cn.dancingsnow.neoecoae.compat.processing.ProcessingTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader.Context;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingThread.Snapshot;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOFxProcessingStatusTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    private final ECOFxProcessingStatus reader = new ECOFxProcessingStatus();
    private final UUID job = UUID.randomUUID();
    private final Context context = new Context(AEItemKey.of(Items.STONE), job);

    @Test void sameOutputFromAnotherOrderNeverContributesAndParallelWorkIsWeighted() {
        var worker = worker();
        when(worker.getThreadSnapshots()).thenReturn(List.of(
            snapshot(job, Items.STONE, 0, 3, false, ""), snapshot(job, Items.STONE, 100, 1, false, ""),
            snapshot(UUID.randomUUID(), Items.STONE, 100, 1000, false, ""), snapshot(job, Items.DIRT, 100, 1000, false, "")));
        var sample = reader.read(worker, context).sample();
        assertEquals(4, sample.parallel());
        // 100 progress units at the base 10 units/tick: a cycle is 10 ticks, one of four lanes done.
        assertEquals(3, sample.progressTicks());
        assertEquals(10, sample.durationTicks());
        assertEquals(State.RUNNING, sample.state());
        assertNull(reader.read(worker, new Context(context.output(), UUID.randomUUID())).sample());
    }

    @Test void pausedPowerCoolantAndOutputAreReportedWithoutAttemptingWork() {
        var worker = worker();
        for (String reason : List.of("energy-unavailable", "coolant-unavailable", "virtual-energy-unavailable")) {
            when(worker.getThreadSnapshots()).thenReturn(List.of(snapshot(job, Items.STONE, 30, 1, false, reason)));
            assertEquals(State.PAUSED, reader.read(worker, context).sample().state());
        }
        when(worker.getThreadSnapshots()).thenReturn(List.of(snapshot(job, Items.STONE, 30, 1, true, "")));
        var sample = reader.read(worker, context).sample();
        assertEquals(State.WAITING, sample.state());
        assertEquals(sample.durationTicks(), sample.progressTicks());
        verify(worker, never()).tickingRequest(any(), anyInt());
        verify(worker, never()).pushPattern(any(), any());
    }

    @Test void detachedWorkerPreservesProgressButShowsPaused() {
        var worker = worker();
        when(worker.getThreadSnapshots()).thenReturn(List.of(snapshot(job, Items.STONE, 30, 1, false, "")));
        when(worker.getCluster()).thenReturn(null);
        var sample = reader.read(worker, context).sample();
        assertEquals(State.PAUSED, sample.state());
        assertEquals(3, sample.progressTicks());
        assertEquals(10, sample.durationTicks());
    }

    @Test void overclockedCycleIsReportedInRealTicks() {
        var worker = worker();
        when(worker.getCluster().getController().getExecutionOverclockTimes()).thenReturn(4);
        when(worker.getThreadSnapshots()).thenReturn(List.of(snapshot(job, Items.STONE, 50, 1, false, "")));
        var sample = reader.read(worker, context).sample();
        assertEquals(2, sample.durationTicks());
        assertEquals(1, sample.progressTicks());

        when(worker.getCluster().getController().getExecutionOverclockTimes()).thenReturn(9);
        when(worker.getThreadSnapshots()).thenReturn(List.of(snapshot(job, Items.STONE, 0, 1, false, "")));
        assertEquals(1, reader.read(worker, context).sample().durationTicks());
    }

    @Test void fullVirtualBatchIsOneTickWithoutOverclock() {
        var worker = worker();
        when(worker.getCluster().getController().isFullVirtualCraftingMode()).thenReturn(true);
        when(worker.getThreadSnapshots()).thenReturn(List.of(new Snapshot(true, 0, 100, 1, 512, true,
            new ItemStack(Items.STONE), 512, List.of(), false, job, "TEST", "")));
        var sample = reader.read(worker, context).sample();
        assertEquals(1, sample.durationTicks());
        assertEquals(0, sample.progressTicks());
    }

    private static ECOCraftingWorkerBlockEntity worker() {
        var worker = machine(ECOCraftingWorkerBlockEntity.class);
        var cluster = mock(NECraftingCluster.class);
        var controller = mock(ECOCraftingSystemBlockEntity.class);
        when(cluster.getController()).thenReturn(controller);
        when(worker.getCluster()).thenReturn(cluster);
        var node = mock(IManagedGridNode.class);
        when(worker.getMainNode()).thenReturn(node);
        when(node.isActive()).thenReturn(true);
        return worker;
    }

    private static Snapshot snapshot(UUID job, net.minecraft.world.item.Item item, int progress,
            long crafts, boolean outputsReady, String reason) {
        return new Snapshot(true, progress, 100, 1, crafts, false, new ItemStack(item), crafts, List.of(), outputsReady,
            job, "TEST", reason);
    }
}
