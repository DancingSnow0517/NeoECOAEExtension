package cn.dancingsnow.neoecoae.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.storage.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.storage.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.storage.StorageListingDelta;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOInfiniteStorageIncrementalListingTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    /** Counts full listings of the wrapped domain view; every other call is delegated unchanged. */
    private static final class Counting implements MEStorage, StorageListingDelta {
        final ECOInfiniteStorage delegate;
        int listings;

        Counting(ECOInfiniteStorage delegate) { this.delegate = delegate; }

        @Override public void getAvailableStacks(KeyCounter out) { listings++; delegate.getAvailableStacks(out); }
        @Override public Component getDescription() { return Component.empty(); }
        @Override public long neoecoae$listingVersion() { return delegate.neoecoae$listingVersion(); }
        @Override public boolean neoecoae$visitListingChanges(long since, long current, Consumer<AEKey> changed) {
            return delegate.neoecoae$visitListingChanges(since, current, changed);
        }
        @Override public long neoecoae$listedAmount(AEKey key) { return delegate.neoecoae$listedAmount(key); }
    }

    /** Ordinary storage without a version, sharing keys with the domain. */
    private static final class Plain implements MEStorage {
        final Map<AEKey, Long> stock = new HashMap<>();
        @Override public void getAvailableStacks(KeyCounter out) { stock.forEach(out::add); }
        @Override public Component getDescription() { return Component.empty(); }
    }

    private static final class Network implements NetworkInventoryCache.Mounts {
        final List<MEStorage> list = new ArrayList<>();
        @Override public long neoecoae$mountGeneration() { return 0; }
        @Override public boolean neoecoae$withMounts(Consumer<List<MEStorage>> action) { action.accept(list); return true; }
    }

    private static final class Reader {
        final NetworkInventoryCache cache = new NetworkInventoryCache();
        final KeyCounter published = new KeyCounter();
        final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
        final Map<AEKey, Long> notified = new HashMap<>();

        void refresh(Network network) {
            assertTrue(cache.refresh(network, published, amounts, notified::put));
        }

        void assertMatches(Network network, String context) {
            var expected = new KeyCounter();
            for (MEStorage storage : network.list) {
                var contribution = new KeyCounter();
                // The reference listing must not count as a cache listing.
                (storage instanceof Counting counting ? counting.delegate : storage).getAvailableStacks(contribution);
                for (var entry : contribution) SaturatingStackAccumulator.add(expected, entry.getKey(), entry.getLongValue());
            }
            expected.removeZeros();
            assertEquals(expected.size(), published.size(), context);
            assertEquals(expected.size(), amounts.size(), context);
            for (var entry : expected) {
                assertEquals(entry.getLongValue(), published.get(entry.getKey()), context + " " + entry.getKey());
                assertEquals(entry.getLongValue(), amounts.getLong(entry.getKey()), context + " " + entry.getKey());
                assertEquals(entry.getLongValue(), notified.getOrDefault(entry.getKey(), 0L), context);
            }
            notified.forEach((key, value) -> assertEquals(expected.get(key), value.longValue(), context + " notified " + key));
        }
    }

    private static List<AEKey> keys(int count) {
        List<AEKey> keys = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            keys.add(AEItemKey.of(item));
            if (keys.size() == count) break;
        }
        return keys;
    }

    @Test void quantityChangesAreAppliedWithoutListingTheDomain() {
        var data = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(data);
        var domain = new Counting(new ECOInfiniteStorage(engine, Component.empty(), () -> true));
        var network = new Network();
        network.list.add(domain);
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        engine.insert(stone, 10, Actionable.MODULATE);
        var reader = new Reader();
        reader.refresh(network);
        int listings = domain.listings;
        for (int i = 0; i < 50; i++) {
            engine.insert(stone, 3, Actionable.MODULATE);
            engine.extract(stone, 1, Actionable.MODULATE);
            reader.refresh(network);
        }
        // A new key and a removed key are also only quantity changes for the log.
        engine.insert(dirt, 5, Actionable.MODULATE);
        reader.refresh(network);
        engine.extract(dirt, 5, Actionable.MODULATE);
        reader.refresh(network);
        assertEquals(listings, domain.listings, "no full listing for plain quantity changes");
        assertEquals(110, reader.published.get(stone));
        reader.assertMatches(network, "after transfers");

        // A structural change (restore lock) requires one full listing.
        UUID transaction = UUID.randomUUID(), target = UUID.randomUUID();
        assertTrue(engine.reserveRestores(Map.of(stone, transaction), Set.of(target), Map.of(stone,
            Map.of(target, new ECOInfiniteStorageEngine.RestoreTargetAmounts(0, 110, 110)))));
        reader.refresh(network);
        assertEquals(listings + 1, domain.listings);
        assertEquals(0, reader.published.get(stone));
        reader.assertMatches(network, "after restore lock");
    }

    @Test void randomOperationsMatchAFullListingForSeveralReaders() {
        var data = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(data);
        boolean[] access = {true};
        var domain = new Counting(new ECOInfiniteStorage(engine, Component.empty(), () -> access[0]));
        var plain = new Plain();
        var network = new Network();
        network.list.add(domain);
        network.list.add(plain);
        List<AEKey> keys = keys(24);
        Random random = new Random(20261028L);
        Reader every = new Reader(), sometimes = new Reader(), rarely = new Reader();
        Map<AEKey, UUID> locks = new HashMap<>();
        UUID target = UUID.randomUUID();
        for (int step = 0; step < 6_000; step++) {
            AEKey key = keys.get(random.nextInt(keys.size()));
            int op = random.nextInt(100);
            if (op < 40) {
                engine.insert(key, 1 + random.nextInt(1_000), Actionable.MODULATE);
            } else if (op < 70) {
                engine.extract(key, 1 + random.nextInt(1_500), Actionable.MODULATE);
            } else if (op < 73) {
                engine.insert(key, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN), Actionable.MODULATE);
            } else if (op < 76) {
                engine.extract(key, Long.MAX_VALUE, Actionable.MODULATE);
            } else if (op < 80) {
                plain.stock.merge(key, 1L + random.nextInt(50), Long::sum);
            } else if (op < 82) {
                plain.stock.remove(key);
            } else if (op < 84) {
                HugeAmount amount = engine.getAmount(key);
                if (!locks.containsKey(key) && !amount.isZero() && !amount.isBig() && locks.size() < 3) {
                    long value = amount.toLongSaturated();
                    UUID transaction = UUID.randomUUID();
                    if (engine.reserveRestores(Map.of(key, transaction), Set.of(target), Map.of(key,
                            Map.of(target, new ECOInfiniteStorageEngine.RestoreTargetAmounts(0, value, value))))) {
                        locks.put(key, transaction);
                    }
                }
            } else if (op < 86) {
                if (!locks.isEmpty()) {
                    var lock = locks.entrySet().iterator().next();
                    assertTrue(engine.finishRestore(lock.getKey(), lock.getValue()));
                    locks.remove(lock.getKey());
                }
            } else if (op < 88) {
                data.setDirty();
            } else if (op < 90) {
                access[0] = !access[0];
            } else if (op < 91) {
                // Overflow the change log between two refreshes of the slower readers.
                for (int i = 0; i < InfiniteStorageAmounts.CHANGE_LOG_CAPACITY + 10; i++) {
                    engine.insert(keys.get(i % 2), 1, Actionable.MODULATE);
                }
            }
            every.refresh(network);
            every.assertMatches(network, "every " + step);
            if (step % 7 == 0) {
                sometimes.refresh(network);
                sometimes.assertMatches(network, "sometimes " + step);
            }
            if (step % 97 == 0) {
                rarely.refresh(network);
                rarely.assertMatches(network, "rarely " + step);
            }
        }
        assertTrue(domain.listings < 2_000, "most refreshes are incremental: " + domain.listings);
    }

    @Test void changeLogServesOnlyCoveredVersions() {
        var amounts = new InfiniteStorageAmounts();
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        amounts.add(stone, 1);
        long first = amounts.changes();
        for (int i = 0; i < 100; i++) amounts.add(stone, 1);
        List<AEKey> seen = new ArrayList<>();
        assertTrue(amounts.visitChangesSince(first, amounts.changes(), seen::add));
        assertEquals(List.of(stone), seen, "consecutive changes of one key share an entry");
        assertTrue(amounts.visitChangesSince(amounts.changes(), amounts.changes(), seen::add));
        assertFalse(amounts.visitChangesSince(first, amounts.changes() + 1, seen::add), "stale current version");
        amounts.touch();
        assertFalse(amounts.visitChangesSince(first, amounts.changes(), seen::add), "structural change");
        long afterTouch = amounts.changes();
        for (int i = 0; i < InfiniteStorageAmounts.CHANGE_LOG_CAPACITY + 1; i++) {
            amounts.add(i % 2 == 0 ? stone : dirt, 1);
        }
        assertFalse(amounts.visitChangesSince(afterTouch, amounts.changes(), seen::add), "evicted changes");
        seen.clear();
        assertTrue(amounts.visitChangesSince(amounts.changes() - 2, amounts.changes(), seen::add));
        assertEquals(2, seen.size());
    }
}
