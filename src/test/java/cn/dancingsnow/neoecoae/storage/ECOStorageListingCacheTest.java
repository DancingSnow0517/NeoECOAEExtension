package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOStorageListingCacheTest {
    @Test void aDifferentSourceAtTheSameRevisionCannotReuseThePreviousListing() {
        var first = new Source();
        var second = new Source();
        var key = key(new Object());
        first.stock.put(key, 10L);
        second.stock.put(key, 20L);
        var cache = new ECOStorageListingCache();
        cache.append(first, new KeyCounter(), first::list);
        var current = new KeyCounter();
        cache.append(second, current, second::list);
        assertEquals(20, current.get(key));
    }

    private static AEKey key(Object primary) {
        AEKey key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(primary);
        return key;
    }

    @Test void callerMutationsCannotChangeCachedQuantitiesOrAnotherViewersVariants() {
        var source = new Source();
        var first = key(new Object());
        var variant = key(first.getPrimaryKey());
        source.stock.put(first, 10L);
        source.stock.put(variant, 20L);
        var cache = new ECOStorageListingCache();
        var previous = new KeyCounter();
        cache.append(source, previous, source::list);
        var other = new KeyCounter();
        cache.append(source, other, source::list);
        assertEquals(1, source.listings);
        previous.set(first, 999);
        previous.removeAll(other);
        previous.removeZeros();
        assertEquals(10, other.get(first));
        assertEquals(20, other.get(variant));
        source.stock.put(first, 30L);
        source.version++;
        var current = new KeyCounter();
        cache.append(source, current, source::list);
        assertEquals(1, source.listings);
        assertEquals(30, current.get(first));
        assertEquals(10, other.get(first));
        other.clear();
        var third = new KeyCounter();
        cache.append(source, third, source::list);
        assertEquals(30, third.get(first));
        assertEquals(20, third.get(variant));
    }

    @Test void failedOrUnavailableDeltasRebuildWithoutPublishingPartialResults() {
        var source = new Source();
        var key = key(new Object());
        source.stock.put(key, 10L);
        var cache = new ECOStorageListingCache();
        cache.append(source, new KeyCounter(), source::list);
        source.stock.put(key, 20L);
        source.version++;
        source.failDelta = true;
        var output = new KeyCounter();
        assertThrows(IllegalStateException.class, () -> cache.append(source, output, source::list));
        assertTrue(output.isEmpty());
        source.failDelta = false;
        cache.append(source, output, source::list);
        assertEquals(2, source.listings);
        assertEquals(20, output.get(key));
        source.stock.put(key, 30L);
        source.version++;
        source.completeDelta = false;
        var rebuilt = new KeyCounter();
        cache.append(source, rebuilt, source::list);
        assertEquals(3, source.listings);
        assertEquals(30, rebuilt.get(key));
    }

    @Test void aFailedFullListingIsNotReusedAndDoesNotChangeTheCallersInventory() {
        var source = new Source();
        var key = key(new Object());
        source.stock.put(key, 20L);
        var output = new KeyCounter();
        output.set(key, 3);
        var cache = new ECOStorageListingCache();
        assertThrows(IllegalStateException.class, () -> cache.append(source, output, partial -> {
            partial.set(key, 99);
            throw new IllegalStateException("listing failed");
        }));
        assertEquals(3, output.get(key));
        cache.append(source, output, source::list);
        assertEquals(23, output.get(key));
    }

    @Test void unknownVersionsAlwaysListAndCombiningAnEcoListingPreservesSaturation() {
        var source = new Source();
        var key = key(new Object());
        source.stock.put(key, 7L);
        source.version = StorageListingVersion.UNKNOWN;
        var cache = new ECOStorageListingCache();
        cache.append(source, new KeyCounter(), source::list);
        source.stock.put(key, 9L);
        var second = new KeyCounter();
        cache.append(source, second, source::list);
        assertEquals(2, source.listings);
        assertEquals(9, second.get(key));
        source.version = 1;
        var combined = new KeyCounter();
        combined.set(key, Long.MAX_VALUE - 1);
        cache.append(source, combined, source::list);
        assertEquals(Long.MAX_VALUE, combined.get(key));
    }

    private static final class Source implements StorageListingDelta {
        final Map<AEKey, Long> stock = new LinkedHashMap<>();
        long version;
        int listings;
        boolean failDelta;
        boolean completeDelta = true;

        void list(KeyCounter out) { listings++; stock.forEach(out::set); }
        @Override public long neoecoae$listingVersion() { return version; }
        @Override public boolean neoecoae$visitListingChanges(long since, long current, Consumer<AEKey> changed) {
            stock.keySet().forEach(changed);
            if (failDelta) throw new IllegalStateException("delta failed after reporting keys");
            return completeDelta;
        }
        @Override public long neoecoae$listedAmount(AEKey key) { return stock.getOrDefault(key, 0L); }
    }
}
