package cn.dancingsnow.neoecoae.crafting.adapter.ae2;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOCraftingServiceClustersTest {
    @Test void onlyARepresentativeWithAFreeThreadAdvertisesItsPlaceholder() {
        var representative = mock(NEComputationCluster.class);
        var member = mock(NEComputationCluster.class);
        var full = mock(NEComputationCluster.class);
        var active = mock(ECOCraftingCPU.class);
        var placeholder = mock(ECOCraftingCPU.class);
        when(representative.isNetworkRepresentative()).thenReturn(true);
        when(representative.getMaxThreads()).thenReturn(2);
        when(representative.getActiveCPUCount()).thenReturn(1);
        when(representative.getActiveCPUs()).thenReturn(List.of(active));
        when(representative.getFakeCPU()).thenReturn(placeholder);
        when(member.getActiveCPUs()).thenReturn(List.of());
        when(member.getMaxThreads()).thenReturn(2);
        when(full.isNetworkRepresentative()).thenReturn(true);
        when(full.getActiveCPUCount()).thenReturn(2);
        when(full.getMaxThreads()).thenReturn(2);
        when(full.getActiveCPUs()).thenReturn(List.of());
        var cpus = new ArrayList<ICraftingCPU>();
        var clusters = Set.of(representative, member, full);
        ECOCraftingServiceClusters.addCpus(clusters, cpus::add);
        assertEquals(Set.of(active, placeholder), Set.copyOf(cpus));
        assertEquals(2, cpus.size());
        assertTrue(ECOCraftingServiceClusters.hasCpu(clusters, active));
        assertTrue(ECOCraftingServiceClusters.hasCpu(clusters, placeholder));
        assertFalse(ECOCraftingServiceClusters.hasCpu(clusters, mock(ICraftingCPU.class)));
        verify(member, never()).getFakeCPU();
        verify(full, never()).getFakeCPU();
    }

    @Test void rejectionCountersAreAddedToTheOtherProvidersCounters() {
        var offline = candidate();
        var busy = candidate();
        var small = candidate();
        var excluded = candidate();
        var nonRepresentative = candidate();
        when(offline.isActive()).thenReturn(false);
        when(busy.getActiveCPUCount()).thenReturn(1);
        when(small.getAvailableStorage()).thenReturn(1L);
        when(excluded.canBeAutoSelectedFor(any())).thenReturn(false);
        when(nonRepresentative.isNetworkRepresentative()).thenReturn(false);
        var plan = mock(ICraftingPlan.class);
        when(plan.bytes()).thenReturn(10L);
        var previous = new MutableObject<>(new UnsuitableCpus(2, 3, 4, 5));
        assertNull(ECOCraftingServiceClusters.findSuitable(Set.of(offline, busy, small, excluded, nonRepresentative),
            plan, mock(IActionSource.class), previous));
        assertEquals(new UnsuitableCpus(3, 4, 5, 6), previous.getValue());
    }

    @Test void preferredClusterWinsBeforeParallelismTieBreak() {
        var fast = candidate();
        var preferred = candidate();
        when(fast.getPooledParallelism()).thenReturn(100);
        when(preferred.getPooledParallelism()).thenReturn(1);
        when(preferred.isPreferredFor(any())).thenReturn(true);
        var plan = mock(ICraftingPlan.class);
        when(plan.bytes()).thenReturn(10L);
        assertSame(preferred, ECOCraftingServiceClusters.findSuitable(Set.of(fast, preferred), plan,
            mock(IActionSource.class), new MutableObject<>()));
    }

    private static NEComputationCluster candidate() {
        var cluster = mock(NEComputationCluster.class);
        when(cluster.isNetworkRepresentative()).thenReturn(true);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getMaxThreads()).thenReturn(1);
        when(cluster.getAvailableStorage()).thenReturn(100L);
        when(cluster.canBeAutoSelectedFor(any())).thenReturn(true);
        return cluster;
    }
}
