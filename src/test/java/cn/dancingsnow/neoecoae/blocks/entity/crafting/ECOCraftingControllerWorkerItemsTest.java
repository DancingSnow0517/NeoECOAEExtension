package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingThread;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOCraftingControllerWorkerItemsTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordinaryWorkerShowsFirstCurrentThreadOutputAndClearsWhenIdle() throws ReflectiveOperationException {
        var worker = mock(ECOCraftingWorkerBlockEntity.class);
        var idle = mock(ECOCraftingThread.class);
        var active = mock(ECOCraftingThread.class);
        var later = mock(ECOCraftingThread.class);
        var threads = ECOCraftingWorkerBlockEntity.class.getDeclaredField("craftingThreads");
        threads.setAccessible(true);
        threads.set(worker, new ArrayList<>(List.of(idle, active, later)));
        when(active.getDisplayedOutputKey()).thenReturn(AEItemKey.of(Items.DIAMOND));
        when(later.getDisplayedOutputKey()).thenReturn(AEItemKey.of(Items.EMERALD));
        when(worker.getCraftingDisplayItem()).thenCallRealMethod();

        assertTrue(worker.getCraftingDisplayItem().is(Items.DIAMOND));
        assertEquals(1, worker.getCraftingDisplayItem().getCount());
        verify(worker, never()).isMonitor();

        when(active.getDisplayedOutputKey()).thenReturn(null);
        assertTrue(worker.getCraftingDisplayItem().is(Items.EMERALD));
        when(later.getDisplayedOutputKey()).thenReturn(null);
        assertTrue(worker.getCraftingDisplayItem().isEmpty());
    }

    @Test
    void localWorkersKeepPhysicalOrderAndIdleCellsWhenMirrored() throws ReflectiveOperationException {
        var host = mock(ECOCraftingSystemBlockEntity.class);
        var cluster = mock(NECraftingCluster.class);
        var nearest = worker(1, new ItemStack(Items.DIAMOND));
        var middle = worker(2, ItemStack.EMPTY);
        var furthest = worker(3, new ItemStack(Items.EMERALD));
        when(cluster.getWorkers()).thenReturn(List.of(furthest, nearest, middle));
        when(host.getBlockPos()).thenReturn(BlockPos.ZERO);
        var clusterField = NEBlockEntity.class.getDeclaredField("cluster");
        clusterField.setAccessible(true);
        clusterField.set(host, cluster);
        var formedField = NEBlockEntity.class.getDeclaredField("formed");
        formedField.setAccessible(true);
        formedField.setBoolean(host, true);
        doCallRealMethod().when(host).setMirrored(anyBoolean());
        when(host.getCraftingControllerWorkerItems()).thenCallRealMethod();

        List<ItemStack> normal = host.getCraftingControllerWorkerItems();
        assertEquals(11, normal.size());
        assertTrue(normal.get(0).is(Items.DIAMOND));
        assertTrue(normal.get(1).isEmpty());
        assertTrue(normal.get(2).is(Items.EMERALD));
        host.setMirrored(true);
        List<ItemStack> mirrored = host.getCraftingControllerWorkerItems();
        assertTrue(mirrored.get(10).is(Items.DIAMOND));
        assertTrue(mirrored.get(9).isEmpty());
        assertTrue(mirrored.get(8).is(Items.EMERALD));
        formedField.setBoolean(host, false);
        assertTrue(host.getCraftingControllerWorkerItems().stream().allMatch(ItemStack::isEmpty));
    }

    private static ECOCraftingWorkerBlockEntity worker(int distance, ItemStack output) {
        var worker = mock(ECOCraftingWorkerBlockEntity.class);
        when(worker.getBlockPos()).thenReturn(new BlockPos(distance, 0, 0));
        when(worker.getCraftingDisplayItem()).thenReturn(output);
        return worker;
    }
}
