package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.CraftingServiceMixin;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECORequestedAmountTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void ecoOnlyNetworkReportsItsFourWaitingOutputs() throws Exception {
        var key = mock(AEKey.class);
        assertEquals(4, requestedAmount(key, 0, cluster(cpu(key, 4))));
    }

    @Test
    void vanillaDemandIncludesEveryEcoCpuAcrossClusters() throws Exception {
        var key = mock(AEKey.class);
        assertEquals(20, requestedAmount(key, 7,
                cluster(cpu(key, 4), cpu(key, 6)),
                cluster(cpu(key, 3), cpu(key, 0)), cluster()));
    }

    @Test
    void networkWithoutEcoCpusPreservesVanillaDemand() throws Exception {
        var key = mock(AEKey.class);
        assertEquals(7, requestedAmount(key, 7));
        assertEquals(7, requestedAmount(key, 7, cluster()));
    }

    @Test
    void waitingOutputsAreCountedOnlyForTheRequestedKey() throws Exception {
        var requestedKey = mock(AEKey.class);
        var otherKey = mock(AEKey.class);
        var cpu = cpu(requestedKey, 4);
        when(cpu.getLogic().getWaitingFor(otherKey)).thenReturn(9L);
        var cluster = cluster(cpu);
        assertEquals(4, requestedAmount(requestedKey, 0, cluster));
        assertEquals(9, requestedAmount(otherKey, 0, cluster));
    }

    private static long requestedAmount(AEKey key, long originalAmount,
            NEComputationCluster... clusters) throws Exception {
        var mixin = mock(CraftingServiceMixin.class, CALLS_REAL_METHODS);
        var clusterField = CraftingServiceMixin.class.getDeclaredField("neoecoae$computationClusters");
        clusterField.setAccessible(true);
        clusterField.set(mixin, new HashSet<>(List.of(clusters)));

        var calls = new AtomicInteger();
        Operation<Long> original = args -> {
            assertEquals(1, args.length);
            assertSame(key, args[0]);
            calls.incrementAndGet();
            return originalAmount;
        };
        var hook = CraftingServiceMixin.class.getDeclaredMethod("onGetRequestedAmount",
                AEKey.class, Operation.class);
        hook.setAccessible(true);
        long result = (long) hook.invoke(mixin, key, original);
        assertEquals(1, calls.get());
        return result;
    }

    private static NEComputationCluster cluster(ECOCraftingCPU... cpus) {
        var cluster = mock(NEComputationCluster.class);
        when(cluster.getActiveCPUs()).thenReturn(List.of(cpus));
        return cluster;
    }

    private static ECOCraftingCPU cpu(AEKey key, long waitingAmount) {
        var cpu = mock(ECOCraftingCPU.class);
        var logic = mock(ECOCraftingCPULogic.class);
        when(cpu.getLogic()).thenReturn(logic);
        when(logic.getWaitingFor(key)).thenReturn(waitingAmount);
        return cpu;
    }
}
