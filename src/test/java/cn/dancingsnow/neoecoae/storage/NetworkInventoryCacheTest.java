package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NetworkInventoryCacheTest {
    private final NetworkInventoryCache cache = new NetworkInventoryCache();
    private final Network network = new Network();
    private final KeyCounter published = new KeyCounter();
    private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
    private final Map<AEKey, Long> notified = new LinkedHashMap<>();
    private final AEKey a = key(), b = key(), c = key();

    @Test void unchangedVersionedMountsAreNotListedAgain() {
        var versioned = network.add(new Versioned());
        var external = network.add(new Fake());
        versioned.stock.put(a, 10L);
        external.stock.put(b, 5L);
        refresh();
        assertEquals(Map.of(a, 10L, b, 5L), notified);

        notified.clear();
        refresh();
        assertEquals(1, versioned.listings);
        assertEquals(2, external.listings, "third-party storage without a version is listed every time");
        assertTrue(notified.isEmpty());

        versioned.stock.put(a, 12L);
        versioned.stock.put(c, 1L);
        versioned.version++;
        external.stock.remove(b);
        refresh();
        assertEquals(2, versioned.listings);
        assertEquals(Map.of(a, 12L, b, 0L, c, 1L), notified);
        assertMatchesFullListing();
    }

    @Test void totalsBeyondLongSaturateAndRecoverExactly() {
        var infinite = network.add(new Versioned());
        var ordinary = network.add(new Versioned());
        infinite.stock.put(a, Long.MAX_VALUE);
        ordinary.stock.put(a, 5L);
        refresh();
        assertEquals(Long.MAX_VALUE, published.get(a));

        ordinary.stock.put(a, 7L);
        ordinary.version++;
        refresh();
        assertEquals(Long.MAX_VALUE, published.get(a));

        infinite.stock.clear();
        infinite.version++;
        refresh();
        assertEquals(7L, published.get(a));
        assertEquals(7L, amounts.getLong(a));
    }

    @Test void mountChangesAddAndRemoveContributions() {
        var first = network.add(new Versioned());
        first.stock.put(a, 3L);
        refresh();

        var second = network.add(new Versioned());
        second.stock.put(a, 4L);
        network.generation++;
        refresh();
        assertEquals(7L, published.get(a));

        network.list.remove(first);
        network.generation++;
        notified.clear();
        refresh();
        assertEquals(Map.of(a, 4L), notified);
        assertMatchesFullListing();
    }

    @Test void mountsInUseOrMountedTwiceUseAE2sOwnListing() {
        var storage = network.add(new Fake());
        storage.stock.put(a, 1L);
        network.inUse = true;
        assertFalse(cache.refresh(network, published, amounts, notified::put));

        network.inUse = false;
        network.list.add(storage);
        assertFalse(cache.refresh(network, published, amounts, notified::put));
        assertTrue(published.isEmpty());
    }

    @Test void periodicRebuildRepairsAModifiedPublishedCounter() {
        var storage = network.add(new Versioned());
        storage.stock.put(a, 2L);
        refresh();
        published.set(a, 99L);
        for (int i = 0; i < NetworkInventoryCache.FULL_REBUILD_INTERVAL; i++) refresh();
        assertEquals(2L, published.get(a));
    }

    private void refresh() {
        assertTrue(cache.refresh(network, published, amounts, notified::put));
    }

    private void assertMatchesFullListing() {
        var expected = new KeyCounter();
        for (MEStorage storage : network.list) {
            ((Fake) storage).stock.forEach((key, amount) -> SaturatingStackAccumulator.add(expected, key, amount));
        }
        expected.removeZeros();
        assertEquals(expected.size(), published.size());
        assertEquals(expected.size(), amounts.size());
        for (var entry : expected) {
            assertEquals(entry.getLongValue(), published.get(entry.getKey()));
            assertEquals(entry.getLongValue(), amounts.getLong(entry.getKey()));
        }
    }

    private static AEKey key() {
        var key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(new Object());
        return key;
    }

    private static class Fake implements MEStorage {
        final Map<AEKey, Long> stock = new LinkedHashMap<>();
        int listings;

        @Override
        public void getAvailableStacks(KeyCounter out) {
            listings++;
            stock.forEach(out::add);
        }

        @Override
        public Component getDescription() { return null; }
    }

    private static final class Versioned extends Fake implements StorageListingVersion {
        long version;

        @Override
        public long neoecoae$listingVersion() { return version; }
    }

    private static final class Network implements NetworkInventoryCache.Mounts {
        final List<MEStorage> list = new ArrayList<>();
        long generation;
        boolean inUse;

        <T extends MEStorage> T add(T storage) {
            list.add(storage);
            return storage;
        }

        @Override
        public long neoecoae$mountGeneration() { return generation; }

        @Override
        public boolean neoecoae$withMounts(Consumer<List<MEStorage>> action) {
            if (inUse) return false;
            action.accept(list);
            return true;
        }
    }
}
