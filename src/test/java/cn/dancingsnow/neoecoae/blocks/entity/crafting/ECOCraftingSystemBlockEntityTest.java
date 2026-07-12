package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ECOCraftingSystemBlockEntityTest {
    @Test
    void countsThreadsFromEachCoreTier() {
        IECOTier f4 = craftingTier(24, 32);
        IECOTier f6 = craftingTier(72, 96);
        int mixedOverclockedThreads = 15 * ECOCraftingSystemBlockEntity.getCoreThreadCount(f6, true)
            + 7 * ECOCraftingSystemBlockEntity.getCoreThreadCount(f4, true);

        assertEquals(2912, mixedOverclockedThreads);
        assertEquals(
            Integer.MAX_VALUE,
            ECOCraftingSystemBlockEntity.getCoreThreadCount(
                craftingTier(Integer.MAX_VALUE, Integer.MAX_VALUE), true
            )
        );
    }

    @Test
    void calculatesOverclockFromOverflowRatio() {
        assertEquals(0, ECOCraftingSystemBlockEntity.calculateOverclockTimes(0, 0));
        assertEquals(0, ECOCraftingSystemBlockEntity.calculateOverclockTimes(1000, 1000));
        assertEquals(1, ECOCraftingSystemBlockEntity.calculateOverclockTimes(1000, 950));
        assertEquals(5, ECOCraftingSystemBlockEntity.calculateOverclockTimes(3696, 2816));
        assertEquals(9, ECOCraftingSystemBlockEntity.calculateOverclockTimes(14080, 5632));
    }

    private static IECOTier craftingTier(int parallel, int overclockedParallel) {
        return new IECOTier() {
            @Override
            public int getTier() {
                return 0;
            }

            @Override
            public int getCrafterParallel() {
                return parallel;
            }

            @Override
            public int getOverclockedCrafterParallel() {
                return overclockedParallel;
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
                return 0;
            }

            @Override
            public long getStorageTotalBytes() {
                return 0;
            }

            @Override
            public long getPowerStorageSize() {
                return 0;
            }

            @Override
            public ResourceLocation getCPUOverlayTexture() {
                return null;
            }
        };
    }
}
