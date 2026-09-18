package cn.dancingsnow.neoecoae.compat.thunderbolt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.CraftingServiceMixin;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import java.lang.reflect.Method;
import java.util.Set;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class ForeignPlannerEcoCpuSubmissionContractTest {
    @Test
    void automaticSelectionAcceptsForeignPlanWithOnlyEcoCpu() throws Exception {
        var fixture = new Fixture();
        var expected = CraftingSubmitResult.successful(null);
        when(fixture.cluster.submitJob(null, fixture.plan, fixture.source, null)).thenReturn(expected);
        var callback = fixture.submit(null);
        assertTrue(callback.isCancelled());
        assertSame(expected, callback.getReturnValue());
    }

    @Test
    void explicitOtherCpuDoesNotSubmitToEco() throws Exception {
        var fixture = new Fixture();
        assertFalse(fixture.submit(mock(ICraftingCPU.class)).isCancelled());
        verify(fixture.cluster, never()).submitJob(any(), any(), any(), any());
    }

    @Test
    void fullEcoClusterFallsThroughAndPreservesOtherRejections() throws Exception {
        var fixture = new Fixture();
        when(fixture.cluster.getActiveCPUCount()).thenReturn(1);
        fixture.rejections.setValue(new UnsuitableCpus(1, 2, 3, 4));
        assertFalse(fixture.submit(null).isCancelled());
        assertEquals(new UnsuitableCpus(1, 3, 3, 4), fixture.rejections.getValue());
        verify(fixture.cluster, never()).submitJob(any(), any(), any(), any());
    }

    @Test
    void simulationIsRejectedBeforeSubmission() throws Exception {
        var fixture = new Fixture();
        when(fixture.plan.simulation()).thenReturn(true);
        assertSame(CraftingSubmitResult.INCOMPLETE_PLAN, fixture.submit(null).getReturnValue());
    }

    private static final class Fixture {
        final CraftingServiceMixin service = mock(CraftingServiceMixin.class,
            withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
        final NEComputationCluster cluster = mock(NEComputationCluster.class);
        final ICraftingPlan plan = mock(ICraftingPlan.class);
        final IActionSource source = mock(IActionSource.class);
        final MutableObject<UnsuitableCpus> rejections = new MutableObject<>();

        @SuppressWarnings("unchecked")
        Fixture() throws Exception {
            var field = CraftingServiceMixin.class.getDeclaredField("neoecoae$computationClusters");
            field.setAccessible(true);
            ((Set<NEComputationCluster>) field.get(service)).add(cluster);
            when(cluster.isNetworkRepresentative()).thenReturn(true);
            when(cluster.isActive()).thenReturn(true);
            when(cluster.getMaxThreads()).thenReturn(1);
            when(cluster.getAvailableStorage()).thenReturn(1024L);
            when(cluster.canBeAutoSelectedFor(source)).thenReturn(true);
            when(plan.bytes()).thenReturn(128L);
        }

        CallbackInfoReturnable<ICraftingSubmitResult> submit(ICraftingCPU target) throws Exception {
            Method method = CraftingServiceMixin.class.getDeclaredMethod("onSubmitJob",
                ICraftingPlan.class, ICraftingRequester.class, ICraftingCPU.class, boolean.class,
                IActionSource.class, CallbackInfoReturnable.class, MutableObject.class);
            method.setAccessible(true);
            var callback = new CallbackInfoReturnable<ICraftingSubmitResult>("submitJob", true);
            method.invoke(service, plan, null, target, true, source, callback, rejections);
            return callback;
        }
    }
}
