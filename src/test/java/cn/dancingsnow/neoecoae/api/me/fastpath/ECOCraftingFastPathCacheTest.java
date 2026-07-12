package cn.dancingsnow.neoecoae.api.me.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.api.Test;

class ECOCraftingFastPathCacheTest {
    @Test
    void expiresNegativeEntriesFromCreationTimeEvenWhenTheyRemainHot() {
        ECOCraftingFastPathCache cache = new ECOCraftingFastPathCache(16);
        ECOFastPathKey key = key("negative-ttl");

        cache.putNegative(key, 10L);

        assertNotNull(cache.get(key, 1_209L));
        assertNull(cache.get(key, 1_210L));
    }

    @Test
    void clampsCacheSizeBeforeConstructingTheBackingMap() {
        ECOCraftingFastPathCache cache = new ECOCraftingFastPathCache(Integer.MAX_VALUE);

        assertEquals(ECOCraftingFastPathCache.MAX_CACHE_SIZE, cache.limit());
        cache.putNegative(key("bounded-cache"), 0L);
        assertEquals(1, cache.size());
    }

    @Test
    void handlesInitialAndRegressedStatsTicksWithoutOverflow() {
        assertTrue(ECOCraftingFastPathCache.isStatsLogDue(Long.MIN_VALUE, 0L));
        assertFalse(ECOCraftingFastPathCache.isStatsLogDue(1_000L, 1_099L));
        assertTrue(ECOCraftingFastPathCache.isStatsLogDue(1_000L, 1_100L));
        assertTrue(ECOCraftingFastPathCache.isStatsLogDue(1_000L, 10L));
    }

    private static ECOFastPathKey key(String identity) {
        return ECOFastPathKey.of(identity, new KeyCounter[0], null, 0L).orElseThrow();
    }
}
