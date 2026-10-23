package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.HashSet;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class CraftingSubmissionSelectionTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void explicitEcoCpuReceivesTheUnchangedPlanRequesterAndSource() throws Exception {
        var grid = mock(IGrid.class);
        var service = service(grid);
        var cpu = mock(ECOCraftingCPU.class);
        var cluster = mock(NEComputationCluster.class);
        when(cpu.getCluster()).thenReturn(cluster);
        var job = mock(ICraftingPlan.class);
        var requester = mock(ICraftingRequester.class);
        var source = mock(IActionSource.class);
        var success = mock(ICraftingSubmitResult.class);
        when(cluster.submitJob(grid, job, source, requester)).thenReturn(success);
        var result = callback();
        service.neoecoae$earlySubmitJob(job, requester, cpu, false, source, result, new MutableObject<>());
        assertTrue(result.isCancelled());
        assertSame(success, result.getReturnValue());
        verify(cluster).submitJob(same(grid), same(job), same(source), same(requester));
    }

    @Test
    void selectedEcoCpuFailureDoesNotFallThroughToAnotherCpu() throws Exception {
        var grid = mock(IGrid.class);
        var service = service(grid);
        var cpu = mock(ECOCraftingCPU.class);
        var cluster = mock(NEComputationCluster.class);
        when(cpu.getCluster()).thenReturn(cluster);
        var job = mock(ICraftingPlan.class);
        var source = mock(IActionSource.class);
        when(cluster.submitJob(grid, job, source, null)).thenReturn(CraftingSubmitResult.CPU_BUSY);
        var result = callback();
        service.neoecoae$earlySubmitJob(job, null, cpu, false, source, result, new MutableObject<>());
        assertTrue(result.isCancelled());
        assertSame(CraftingSubmitResult.CPU_BUSY, result.getReturnValue());
        verify(cluster, times(1)).submitJob(grid, job, source, null);
    }

    @Test
    void explicitNativeOrIndependentCpuKeepsItsOwnSubmissionPath() throws Exception {
        var service = service(mock(IGrid.class));
        for (var target : new ICraftingCPU[] {mock(CraftingCPUCluster.class), mock(ICraftingCPU.class)}) {
            var result = callback();
            service.neoecoae$earlySubmitJob(mock(ICraftingPlan.class), null, target, false,
                mock(IActionSource.class), result, new MutableObject<>());
            assertFalse(result.isCancelled());
            verifyNoInteractions(target);
        }
    }

    @Test
    void simulationIsRejectedBeforeSelectedCpuSubmission() throws Exception {
        var service = service(mock(IGrid.class));
        var job = mock(ICraftingPlan.class);
        when(job.simulation()).thenReturn(true);
        var cpu = mock(ECOCraftingCPU.class);
        var result = callback();
        service.neoecoae$earlySubmitJob(job, null, cpu, false, mock(IActionSource.class), result, new MutableObject<>());
        assertSame(CraftingSubmitResult.INCOMPLETE_PLAN, result.getReturnValue());
        verifyNoInteractions(cpu);
    }

    private static CraftingServiceMixin service(IGrid grid) throws Exception {
        var service = mock(CraftingServiceMixin.class, CALLS_REAL_METHODS);
        var gridField = CraftingServiceMixin.class.getDeclaredField("grid");
        gridField.setAccessible(true);
        gridField.set(service, grid);
        var clusters = CraftingServiceMixin.class.getDeclaredField("neoecoae$computationClusters");
        clusters.setAccessible(true);
        clusters.set(service, new HashSet<NEComputationCluster>());
        return service;
    }

    private static CallbackInfoReturnable<ICraftingSubmitResult> callback() {
        return new CallbackInfoReturnable<>("submitJob", true);
    }
}
