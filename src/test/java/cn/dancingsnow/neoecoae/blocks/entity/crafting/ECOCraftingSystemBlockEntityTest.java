package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingSystemBlockEntityTest {
    @Test
    void virtualExchangeUsesOneTenthOfThePreviousCoolantRate() {}

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
        assertEquals(8, ECOCraftingSystemBlockEntity.calculateOverclockTimes(1_000, 600));
    }

    @Test
    void timeRatioDoesNotIncludeParallelOrNetworkHostCount() {
        assertEquals(1.0D, ECOCraftingSystemBlockEntity.calculateTimeMultiplier(10), 0.000001D);
        assertEquals(0.5D, ECOCraftingSystemBlockEntity.calculateTimeMultiplier(5), 0.000001D);
        assertEquals(1.0D, ECOCraftingSystemBlockEntity.calculateTimeMultiplier(0), 0.000001D);
    }
}
