package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.dancingsnow.neoecoae.api.IECOTier;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
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
    void standaloneF9UsesOneTaskThreadPerFxWorker() {
        assertEquals(11, ECOCraftingSystemBlockEntity.calculateWorkerThreadCount(11, 1));
    }

    @Test
    void f9FastPathCapacityAppliesTheExchangeMultiplierPerSlot() {
        int singleHostBatch = ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 16, true, 1);
        int normalExchangeBatch = ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 16, true, 2);
        int highEnergyExchangeBatch = ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 16, true, 8);

        assertEquals(512, singleHostBatch);
        assertEquals(1_024, normalExchangeBatch);
        assertEquals(4_096, highEnergyExchangeBatch);
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
    void parallelCapacityUsesEachInstalledCoreTier() {
        var coreTiers = List.of(new TestCoreTier(24, 32), new TestCoreTier(72, 96), new TestCoreTier(256, 384));

        assertEquals(352, ECOCraftingSystemBlockEntity.calculateParallelCapacity(coreTiers, false, 1));
        assertEquals(704, ECOCraftingSystemBlockEntity.calculateParallelCapacity(coreTiers, false, 2));
        assertEquals(864, ECOCraftingSystemBlockEntity.calculateParallelCapacity(coreTiers, true, 1));
        assertEquals(6_912, ECOCraftingSystemBlockEntity.calculateParallelCapacity(coreTiers, true, 8));
    }

    private record TestCoreTier(int crafterParallel, int overclockedCrafterParallel) implements IECOTier {
        @Override
        public int getTier() {
            return 0;
        }

        @Override
        public int getCrafterParallel() {
            return crafterParallel;
        }

        @Override
        public int getOverclockedCrafterParallel() {
            return overclockedCrafterParallel;
        }

        @Override
        public int getCPUAccelerators() {
            return 0;
        }

        @Override
        public int getCPUThreads() {
            return 0;
        }

        @Override
        public long getCPUTotalBytes() {
            return 0L;
        }

        @Override
        public long getStorageTotalBytes() {
            return 0L;
        }

        @Override
        public long getPowerStorageSize() {
            return 0L;
        }

        @Override
        public ResourceLocation getCPUOverlayTexture() {
            return null;
        }
    }

    @Test
    void timeRatioDoesNotIncludeParallelOrNetworkHostCount() {
        assertEquals(1.0D, ECOCraftingSystemBlockEntity.calculateTimeMultiplier(10), 0.000001D);
        assertEquals(0.5D, ECOCraftingSystemBlockEntity.calculateTimeMultiplier(5), 0.000001D);
        assertEquals(1.0D, ECOCraftingSystemBlockEntity.calculateTimeMultiplier(0), 0.000001D);
    }
}
