package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NEComputationRejectedInventoryTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test void rejectedSubmissionRetainsItsPhysicalCpuAndCannotBeOverwrittenByTheSamePlan() {
        var cluster = spy(new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO));
        doNothing().when(cluster).recalculateRemainingStorage();
        var owner = mock(ECOComputationThreadingCoreBlockEntity.class);
        cluster.getThreadingCores().add(owner);
        var cpu = mock(ECOCraftingCPU.class);
        var logic = mock(ECOCraftingCPULogic.class);
        var plan = mock(ICraftingPlan.class);
        var grid = mock(IGrid.class);
        when(owner.spawn(plan)).thenReturn(cpu);
        when(cpu.getLogic()).thenReturn(logic);
        when(cpu.hasRemainingItems()).thenReturn(true);
        var missing = CraftingSubmitResult.missingIngredient(new GenericStack(AEItemKey.of(Items.STONE), 1));
        when(logic.trySubmitJob(any(), any(), any(), any())).thenReturn(missing);
        assertSame(missing, cluster.trySpawnLocalJob(grid, plan, IActionSource.empty(), null));
        verify(owner, never()).deactivate(cpu);
        verify(logic).markForDeletion();
        assertTrue(cluster.getActiveCPUs().contains(cpu));
        assertSame(CraftingSubmitResult.CPU_BUSY, cluster.trySpawnLocalJob(grid, plan, IActionSource.empty(), null));
        verify(owner, times(1)).spawn(plan);
    }

    @Test void thrownSubmissionAlsoKeepsOwnedResourcesButEmptyRejectionsReleaseTheSlot() {
        var cluster = spy(new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO));
        doNothing().when(cluster).recalculateRemainingStorage();
        var owner = mock(ECOComputationThreadingCoreBlockEntity.class);
        cluster.getThreadingCores().add(owner);
        var cpu = mock(ECOCraftingCPU.class);
        var logic = mock(ECOCraftingCPULogic.class);
        var plan = mock(ICraftingPlan.class);
        when(owner.spawn(plan)).thenReturn(cpu);
        when(cpu.getLogic()).thenReturn(logic);
        when(cpu.hasRemainingItems()).thenReturn(true);
        when(logic.trySubmitJob(any(), any(), any(), any())).thenThrow(new IllegalStateException("external storage"));
        assertThrows(IllegalStateException.class, () -> cluster.trySpawnLocalJob(mock(IGrid.class), plan, IActionSource.empty(), null));
        verify(owner, never()).deactivate(cpu);
        assertTrue(cluster.getActiveCPUs().contains(cpu));
        var secondPlan = mock(ICraftingPlan.class);
        var emptyCpu = mock(ECOCraftingCPU.class);
        var emptyLogic = mock(ECOCraftingCPULogic.class);
        when(owner.spawn(secondPlan)).thenReturn(emptyCpu);
        when(emptyCpu.getLogic()).thenReturn(emptyLogic);
        when(emptyLogic.trySubmitJob(any(), eq(secondPlan), any(), any())).thenReturn(CraftingSubmitResult.CPU_OFFLINE);
        cluster.trySpawnLocalJob(mock(IGrid.class), secondPlan, IActionSource.empty(), null);
        verify(owner).deactivate(emptyCpu);
    }
}
