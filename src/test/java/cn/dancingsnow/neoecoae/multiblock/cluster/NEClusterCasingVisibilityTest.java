package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NEClusterCasingVisibilityTest {
    private static final BlockPos MIN = BlockPos.ZERO;
    private static final BlockPos MAX = new BlockPos(1, 2, 1);

    @Test
    void computationClusterHidesEveryCasingWhenFormed() {
        assertTrue(new NEComputationCluster(MIN, MAX).hideAllCasingsWhenFormed());
    }

    @Test
    void storageAndCraftingKeepLocalizedCasingHiding() {
        assertFalse(new NEStorageCluster(MIN, MAX).hideAllCasingsWhenFormed());
        assertFalse(new NECraftingCluster(MIN, MAX).hideAllCasingsWhenFormed());
    }

    @Test
    void computationReservationsMayExceedOneMemberWhenTheyFitInThePool() {
        TestComputationCluster first = new TestComputationCluster(100L, 150L, 12_672, 1);
        TestComputationCluster second = new TestComputationCluster(100L, 20L, 12_672, 1);
        NEComputationNetworkCluster network = configure(first, second);

        assertEquals(170L, network.getReservedStorage());
        assertEquals(30L, network.getPooledAvailableStorage());
        assertTrue(network.reservationsFit());
    }

    @Test
    void computationCpuAdvertisesPooledParallelism() {
        TestComputationCluster first = new TestComputationCluster(100L, 0L, 12_672, 8);
        TestComputationCluster second = new TestComputationCluster(100L, 0L, 12_672, 8);
        NEComputationNetworkCluster network = configure(first, second);

        assertEquals(202_752, network.getTotalParallelism());
        ECOCraftingCPU advertisedCpu = new ECOCraftingCPU(first, (IECOTier) null);
        assertEquals(network.getTotalParallelism(), advertisedCpu.getCoProcessors());
    }

    private static NEComputationNetworkCluster configure(TestComputationCluster... members) {
        NEComputationNetworkCluster network = new NEComputationNetworkCluster();
        network.configure(java.util.List.of(members));
        for (TestComputationCluster member : members) {
            member.setNetworkCluster(network);
        }
        return network;
    }

    private static final class TestComputationCluster extends NEComputationCluster {
        private final long storage;
        private final long reserved;
        private final int accelerators;
        private final int multiplier;

        private TestComputationCluster(long storage, long reserved, int accelerators, int multiplier) {
            super(MIN, MAX);
            this.storage = storage;
            this.reserved = reserved;
            this.accelerators = accelerators;
            this.multiplier = multiplier;
        }

        @Override
        public long getTotalStorage() {
            return storage;
        }

        @Override
        public long getOwnUsedStorage() {
            return reserved;
        }

        @Override
        public int getCPUAccelerators() {
            return accelerators;
        }

        @Override
        public int getNetworkMultiplier() {
            return multiplier;
        }
    }
}
