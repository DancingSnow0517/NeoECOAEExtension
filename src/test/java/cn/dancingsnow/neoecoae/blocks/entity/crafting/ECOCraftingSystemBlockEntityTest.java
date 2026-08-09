package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingSystemBlockEntityTest {
    @Test
    void virtualExchangeMatches1211CoolantRate() {
        assertEquals(8, ECOCraftingSystemBlockEntity.VIRTUAL_CRAFTING_REQUIRED_HOSTS);
        assertEquals(100, ECOCraftingSystemBlockEntity.VIRTUAL_CRAFTING_COOLANT_PER_TICK);
    }

    @Test
    void laneCapacityScalesBatchSize() {
        assertEquals(32, ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 4, false, 1));
        assertEquals(512, ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 16, true, 1));
        assertEquals(64, ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 4, false, 2));
        assertEquals(256, ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 4, true, 2));
    }

    @Test
    void networkExchangeGivesEachFxWorkerOneThreadPerParticipatingHost() {
        // Two F9 hosts: 11 FX workers per host, two participating hosts.
        int localThreadsPerHost = ECOCraftingSystemBlockEntity.calculateWorkerThreadCount(11, 2);
        assertEquals(22, localThreadsPerHost);
        assertEquals(44, localThreadsPerHost * 2);
    }

    @Test
    void f9FastPathCapacityKeepsThreadsAndBatchSizeIndependent() {
        int singleHostBatch = ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 16, true, 1);
        int exchangedBatch = ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 16, true, 8);

        assertEquals(512, singleHostBatch);
        assertEquals(5_632, ECOCraftingSystemBlockEntity.calculateWorkerThreadCount(11, 1) * singleHostBatch);
        assertEquals(4_096, exchangedBatch);
        assertEquals(180_224, ECOCraftingSystemBlockEntity.calculateWorkerThreadCount(11, 2) * 2 * exchangedBatch);
    }

    @Test
    void coolantBufferStaysFullSizedWhileAHostHasThreadCapacity() {
        assertEquals(0, ECOCraftingSystemBlockEntity.calculateCoolantBufferTarget(0));
        assertEquals(
                ECOCraftingSystemBlockEntity.MAX_COOLANT,
                ECOCraftingSystemBlockEntity.calculateCoolantBufferTarget(22));
    }

    @Test
    void laneCapacitySaturatesAndRejectsNegativeInputs() {
        assertEquals(
                Integer.MAX_VALUE,
                ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(
                        Integer.MAX_VALUE, Integer.MAX_VALUE, true, Integer.MAX_VALUE));
        assertEquals(0, ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(-1, 4, true, 2));
    }

    @Test
    void overflowUsesCompleteFxBatchEfficiency() {
        assertEquals(5_632, ECOCraftingSystemBlockEntity.calculateMaxSynthesisEfficiency(11, 512));
        assertEquals(1_024, ECOCraftingSystemBlockEntity.calculateParallelCapacity(1, 512, 0, false, 2));
        assertEquals(8_192, ECOCraftingSystemBlockEntity.calculateParallelCapacity(1, 512, 512, true, 8));
        assertEquals(8, ECOCraftingSystemBlockEntity.calculateOverclockTimes(1_000, 600));
        assertEquals(0, ECOCraftingSystemBlockEntity.calculateOverclockTimes(1_024, 1_024));
        assertEquals(5, ECOCraftingSystemBlockEntity.calculateOverclockTimes(1_024, 768));
    }

    @Test
    void timeRatioDoesNotIncludeParallelOrNetworkHostCount() {
        assertEquals(1.0D, ECOCraftingSystemBlockEntity.calculateTimeMultiplier(10), 0.000001D);
        assertEquals(0.5D, ECOCraftingSystemBlockEntity.calculateTimeMultiplier(5), 0.000001D);
        assertEquals(1.0D, ECOCraftingSystemBlockEntity.calculateTimeMultiplier(0), 0.000001D);
    }
}
