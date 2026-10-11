package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.crafting.display.CraftingOutputDisplay;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingThread;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOExactVirtualLedger;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;

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
        var sameItem = mock(ECOCraftingThread.class);
        var threads = ECOCraftingWorkerBlockEntity.class.getDeclaredField("craftingThreads");
        threads.setAccessible(true);
        threads.set(worker, new ArrayList<>(List.of(idle, active, later, sameItem)));
        when(active.getDisplayedOutputKey()).thenReturn(AEItemKey.of(Items.DIAMOND));
        when(later.getDisplayedOutputKey()).thenReturn(AEItemKey.of(Items.EMERALD));
        for (var thread : List.of(idle, active, later, sameItem)) {
            lenient().when(thread.getDisplayedItemAmount(any())).thenAnswer(call -> {
                var key = call.getArgument(0);
                if (thread == active && key.equals(AEItemKey.of(Items.DIAMOND))) return BigInteger.valueOf(512);
                if (thread == sameItem && key.equals(AEItemKey.of(Items.DIAMOND))) return BigInteger.valueOf(32);
                if (thread == later && key.equals(AEItemKey.of(Items.EMERALD))) return BigInteger.valueOf(2000);
                return BigInteger.ZERO;
            });
        }
        when(worker.getCraftingDisplayItem()).thenCallRealMethod();
        when(worker.getCraftingDisplayOutput()).thenCallRealMethod();

        assertTrue(worker.getCraftingDisplayItem().is(Items.DIAMOND));
        assertEquals(1, worker.getCraftingDisplayItem().getCount());
        assertEquals(BigInteger.valueOf(544), worker.getCraftingDisplayOutput().amount());
        verify(worker, never()).isMonitor();

        when(active.getDisplayedOutputKey()).thenReturn(null);
        assertTrue(worker.getCraftingDisplayItem().is(Items.EMERALD));
        assertEquals(BigInteger.valueOf(2000), worker.getCraftingDisplayOutput().amount());
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
        when(host.getCraftingControllerWorkerOutputs()).thenCallRealMethod();

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
        assertEquals(BigInteger.valueOf(512), host.getCraftingControllerWorkerOutputs().get(10).amount());
        formedField.setBoolean(host, false);
        assertTrue(host.getCraftingControllerWorkerItems().stream().allMatch(ItemStack::isEmpty));
    }

    private static ECOCraftingWorkerBlockEntity worker(int distance, ItemStack output) {
        var worker = mock(ECOCraftingWorkerBlockEntity.class);
        when(worker.getBlockPos()).thenReturn(new BlockPos(distance, 0, 0));
        when(worker.getCraftingDisplayOutput()).thenReturn(new CraftingOutputDisplay(output, BigInteger.valueOf(512)));
        return worker;
    }

    @Test
    void displayedQuantityExcludesOtherOutputsAndKeepsExactVirtualAmounts() throws ReflectiveOperationException {
        var thread = mock(ECOCraftingThread.class);
        var diamond = AEItemKey.of(Items.DIAMOND);
        setField(thread, "isBusy", true);
        setField(thread, "outputItems", List.of(new ItemStack(Items.DIAMOND, 32), new ItemStack(Items.EMERALD, 64)));
        setField(thread, "batchOutputItems", List.of(new GenericStack(diamond, 512),
            new GenericStack(AEFluidKey.of(Fluids.WATER), 1000), new GenericStack(AEItemKey.of(Items.EMERALD), 2000)));
        when(thread.getDisplayedItemAmount(any())).thenCallRealMethod();
        assertEquals(BigInteger.valueOf(544), thread.getDisplayedItemAmount(diamond));

        var crafts = BigInteger.TEN.pow(30);
        setField(thread, "exactVirtual", new ECOExactVirtualLedger(crafts, List.of(),
            List.of(new GenericStack(diamond, 4)), List.of()));
        assertEquals(crafts.multiply(BigInteger.valueOf(4)), thread.getDisplayedItemAmount(diamond));
        setField(thread, "isBusy", false);
        assertEquals(BigInteger.ZERO, thread.getDisplayedItemAmount(diamond));
    }

    private static void setField(ECOCraftingThread thread, String name, Object value) throws ReflectiveOperationException {
        var field = ECOCraftingThread.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(thread, value);
    }
}
