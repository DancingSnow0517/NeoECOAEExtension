package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import appeng.api.stacks.KeyCounter;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOCraftingFastPathCacheTest {
    @Test
    void equalKeysAndReadsAndReplacementsPreserveLruOrder() {
        var cache = new ECOCraftingFastPathCache(16);
        Object identity = new Object();
        var first = key(identity);
        var second = key(new Object());
        var third = key(new Object());
        cache.putNegative(first, 0);
        cache.putNegative(second, 0);
        cache.putNegative(third, 0);
        for (int i = 3; i < 16; i++) cache.putNegative(key(new Object()), 0);

        var equalFirst = key(identity);
        assertNotSame(first, equalFirst);
        assertEquals(first, equalFirst);
        assertNotNull(cache.get(equalFirst, 1));
        cache.putNegative(second, 1, "REPLACED");
        cache.putNegative(key(new Object()), 1);

        assertNull(cache.get(third, 1));
        assertNotNull(cache.get(first, 1));
        assertEquals("REPLACED", cache.get(second, 1).rejectReason());
    }

    @Test
    void expirationClearAndRepeatedEvictionKeepEntriesReachable() {
        var cache = new ECOCraftingFastPathCache(16);
        var keys = new java.util.ArrayList<ECOFastPathKey>();
        for (int i = 0; i < 256; i++) {
            var key = key(new Object());
            keys.add(key);
            cache.putNegative(key, 10);
        }
        for (int i = 0; i < 240; i++) assertNull(cache.get(keys.get(i), 11));
        for (int i = 240; i < 256; i++) assertNotNull(cache.get(keys.get(i), 1209));
        assertNull(cache.get(keys.get(240), 1210));
        assertNull(cache.get(keys.get(241), 9));
        cache.clear();
        for (var key : keys) assertNull(cache.get(key, 11));
        cache.putNegative(keys.getFirst(), 20);
        assertNotNull(cache.get(keys.getFirst(), 20));
    }

    @Test
    void patternEligibilityCachePromotesHitsAndEvictsLeastRecentlyUsed() {
        var cache = new ECOCraftingFastPathCache(16);
        var first = execution();
        var second = execution();
        cache.lookup(first, 0, 0);
        cache.lookup(second, 0, 0);
        for (int i = 2; i < 16; i++) cache.lookup(execution(), 0, 0);
        cache.lookup(first, 1, 0);
        cache.lookup(execution(), 1, 0);
        cache.lookup(first, 1, 0);
        verify(first, times(1)).patternEligibility();
        cache.lookup(second, 1, 0);
        verify(second, times(2)).patternEligibility();
        cache.clear();
        cache.lookup(first, 1, 0);
        verify(first, times(2)).patternEligibility();
    }

    private static ECOFastPathKey key(Object identity) {
        return ECOFastPathKey.of(identity, new KeyCounter[0], null, 0).orElseThrow();
    }

    private static ECOExtractedPatternExecution execution() {
        var execution = mock(ECOExtractedPatternExecution.class);
        when(execution.key()).thenReturn(key(new Object()));
        when(execution.patternEligibility()).thenReturn(new ECOPatternEligibility(false, List.of(), "UNSUPPORTED"));
        return execution;
    }
}
