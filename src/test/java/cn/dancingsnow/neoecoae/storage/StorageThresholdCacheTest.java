package cn.dancingsnow.neoecoae.storage;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageThresholdCacheTest {
    private static AEKey key(Object primary) {
        AEKey key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(primary);
        return key;
    }

    @Test void identicalQueriesShareResultsAndRefreshInvalidatesThem() {
        var primary = new Object();
        var first = key(primary);
        var second = key(primary);
        var inventory = spy(new KeyCounter());
        inventory.set(first, 10);
        inventory.set(second, 20);
        var cache = new StorageThresholdCache();
        var query = new StorageThresholdCache.Query(first, FuzzyMode.IGNORE_ALL, 100);
        assertEquals(30, cache.amount(inventory, query));
        assertEquals(30, cache.amount(inventory, new StorageThresholdCache.Query(first, FuzzyMode.IGNORE_ALL, 100)));
        verify(inventory, times(1)).findFuzzy(first, FuzzyMode.IGNORE_ALL);
        inventory.set(second, 40);
        cache.invalidate();
        assertEquals(50, cache.amount(inventory, query));
        verify(inventory, times(2)).findFuzzy(first, FuzzyMode.IGNORE_ALL);
        assertEquals(40, cache.amount(inventory, new StorageThresholdCache.Query(second, null, 100)));
    }

    @Test void changesInAnotherResourceCannotConsumeAFuzzyObserversTick() {
        var item = new Object();
        assertTrue(StorageWatcherScope.samePrimary(key(item), key(item)));
        assertFalse(StorageWatcherScope.samePrimary(key(item), key(new Object())));
        assertTrue(StorageWatcherScope.samePrimary(null, key(new Object())));
        // AE2's primary directory uses identity even when two primary objects are value-equal.
        assertFalse(StorageWatcherScope.samePrimary(key(new String("same")), key(new String("same"))));
    }

    @Test void thresholdChangesAndOverflowNeverReuseAnIncorrectAnswer() {
        var inventory = new KeyCounter();
        var first = key(new Object());
        var second = key(new Object());
        inventory.set(first, Long.MAX_VALUE - 2);
        inventory.set(second, 10);
        var cache = new StorageThresholdCache();
        assertEquals(Long.MAX_VALUE, cache.amount(inventory, new StorageThresholdCache.Query(null, null, Long.MAX_VALUE)));
        assertTrue(cache.amount(inventory, new StorageThresholdCache.Query(null, null, 5)) > 5);
        inventory.clear();
        cache.invalidate();
        assertEquals(0, cache.amount(inventory, new StorageThresholdCache.Query(null, null, 5)));
    }
}
