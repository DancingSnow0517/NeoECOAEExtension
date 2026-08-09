package cn.dancingsnow.neoecoae.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NEConfigTest {
    private final int originalPages = NEConfig.craftingPatternBusPages;

    @AfterEach
    void restorePages() {
        NEConfig.craftingPatternBusPages = originalPages;
    }

    @Test
    void defaultsToTwoPagesAnd126Slots() {
        assertEquals(2, NEConfig.getCraftingPatternBusPages());
        assertEquals(126, NEConfig.getCraftingPatternBusSlotCount());
    }

    @Test
    void aggressiveFastPathDefaultsToEnabled() {
        assertTrue(NEConfig.enableEcoAggressiveFastPath);
    }

    @Test
    void supportsConfiguredPageCounts() {
        NEConfig.craftingPatternBusPages = 1;
        assertEquals(1, NEConfig.getCraftingPatternBusPages());
        assertEquals(63, NEConfig.getCraftingPatternBusSlotCount());

        NEConfig.craftingPatternBusPages = 2;
        assertEquals(2, NEConfig.getCraftingPatternBusPages());
        assertEquals(126, NEConfig.getCraftingPatternBusSlotCount());

        NEConfig.craftingPatternBusPages = 4;
        assertEquals(4, NEConfig.getCraftingPatternBusPages());
        assertEquals(252, NEConfig.getCraftingPatternBusSlotCount());
    }

    @Test
    void expandsEveryStorageTierAndPreservesDerivedCellMultipliers() {
        assertEquals(256L << 20, NEConfig.getExpandedEcoStorageCellCapacity(tier(1, 1L << 24), 1L << 24));
        assertEquals(4L << 30, NEConfig.getExpandedEcoStorageCellCapacity(tier(2, 1L << 26), 1L << 26));
        assertEquals(64L << 30, NEConfig.getExpandedEcoStorageCellCapacity(tier(3, 1L << 28), 1L << 28));
        assertEquals(256L << 30, NEConfig.getExpandedEcoStorageCellCapacity(tier(3, 1L << 28), 1L << 30));
    }

    private static IECOTier tier(int tier, long storageBytes) {
        return new IECOTier() {
            @Override
            public int getTier() {
                return tier;
            }

            @Override
            public int getCrafterParallel() {
                return 0;
            }

            @Override
            public int getOverclockedCrafterParallel() {
                return 0;
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
                return storageBytes;
            }

            @Override
            public long getPowerStorageSize() {
                return 0L;
            }

            @Override
            public ResourceLocation getCPUOverlayTexture() {
                return null;
            }
        };
    }
}
