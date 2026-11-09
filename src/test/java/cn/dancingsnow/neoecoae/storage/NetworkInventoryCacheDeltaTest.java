package cn.dancingsnow.neoecoae.storage;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NetworkInventoryCacheDeltaTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void wovenPreciseFiltersPropagateDeltaAndObserveInPlaceRuleChanges() {
        var fixture = new Fixture();
        var a = AEItemKey.of(Items.STONE);
        var b = AEItemKey.of(Items.DIRT);
        var c = AEItemKey.of(Items.IRON_INGOT);
        fixture.engine.insert(a, 10, Actionable.MODULATE);
        fixture.engine.insert(b, 20, Actionable.MODULATE);
        fixture.engine.insert(c, 30, Actionable.MODULATE);
        var handler = new appeng.me.storage.MEInventoryHandler(fixture.storage);
        org.junit.jupiter.api.Assumptions.assumeTrue(handler instanceof StorageListingDelta);
        KeyCounter rules = new KeyCounter(); rules.set(a, 1); rules.set(b, 1);
        handler.setPartitionList(new appeng.util.prioritylist.PrecisePriorityList(rules));
        handler.setExtractFiltering(true, true);
        Reader first = new Reader(handler), second = new Reader(handler);
        first.refresh(); second.refresh();
        int listings = fixture.listings[0];
        fixture.engine.insert(a, 5, Actionable.MODULATE);
        fixture.engine.extract(b, 20, Actionable.MODULATE);
        fixture.engine.insert(c, 7, Actionable.MODULATE);
        first.refresh(); second.refresh();
        assertEquals(listings, fixture.listings[0], "both readers must consume the same filtered delta independently");
        assertEquals(15, first.published.get(a)); assertEquals(0, first.published.get(b));
        assertEquals(0, first.published.get(c)); assertEquals(15, second.published.get(a));
        // Mutate the original counter without the wrapper's setter, including a retained zero entry.
        rules.set(a, 0); rules.set(c, 1);
        first.refresh(); second.refresh();
        assertEquals(0, first.published.get(a)); assertEquals(37, first.published.get(c));
        assertEquals(37, second.published.get(c));
        assertEquals(listings + 2, fixture.listings[0]);
        handler.setWhitelist(appeng.api.config.IncludeExclude.BLACKLIST);
        first.refresh(); assertEquals(15, first.published.get(a)); assertEquals(0, first.published.get(c));
        handler.setAllowExtraction(false);
        first.refresh(); assertTrue(first.published.isEmpty());
        handler.setAllowExtraction(true);
        first.refresh(); assertEquals(15, first.published.get(a));
        rules.clear();
        first.refresh(); assertEquals(37, first.published.get(c), "an empty filter allows all keys");
        assertEquals(0, first.published.get(b));
    }

    @Test void wovenBridgeKeepsCustomAndLargeFiltersOnTheNativePath() {
        var fixture = new Fixture();
        var handler = new appeng.me.storage.MEInventoryHandler(fixture.storage);
        org.junit.jupiter.api.Assumptions.assumeTrue(handler instanceof StorageListingDelta);
        KeyCounter rules = new KeyCounter();
        for (AEKey key : keys(70, "filter")) {
            rules.set(key, 1); fixture.engine.insert(key, 10, Actionable.MODULATE);
        }
        handler.setExtractFiltering(true, true);
        handler.setPartitionList(new appeng.util.prioritylist.PrecisePriorityList(rules));
        assertEquals(StorageListingVersion.UNKNOWN, ((StorageListingVersion) handler).neoecoae$listingVersion());
        Reader reader = new Reader(handler); reader.refresh();
        assertEquals(70, reader.published.size());
        KeyCounter small = new KeyCounter(); small.set(AEItemKey.of(Items.STONE), 1);
        handler.setPartitionList(new appeng.util.prioritylist.PrecisePriorityList(small) {
            @Override public boolean isListed(AEKey key) { return false; }
        });
        assertEquals(StorageListingVersion.UNKNOWN, ((StorageListingVersion) handler).neoecoae$listingVersion());
        reader.refresh(); assertTrue(reader.published.isEmpty());
        // A filtered handler uses its delegate's no-arg overload. Nested wrappers do not promise
        // that this overload equals their consumer listing, so their delta cannot substitute it.
        var nested = new appeng.me.storage.MEInventoryHandler(handler);
        nested.setExtractFiltering(true, true);
        assertEquals(StorageListingVersion.UNKNOWN, ((StorageListingVersion) nested).neoecoae$listingVersion());
    }

    @Test void frequentTransfersOnOneKeyDoNotListTheWholeDomain() {
        var fixture = new Fixture();
        List<AEKey> keys = keys(300, "fe");
        for (AEKey key : keys) fixture.engine.insert(key, 1_000, Actionable.MODULATE);
        Reader reader = new Reader(fixture.storage);
        reader.refresh();
        int listings = fixture.listings[0];
        for (int tick = 0; tick < 50; tick++) {
            fixture.engine.insert(keys.get(0), 100, Actionable.MODULATE);
            fixture.engine.extract(keys.get(0), 60, Actionable.MODULATE);
            reader.refresh();
            reader.assertMatches(fixture);
        }
        assertEquals(listings, fixture.listings[0], "quantity changes must be applied incrementally");
        assertEquals(1_000 + 50 * 40, reader.published.get(keys.get(0)));
    }

    @Test void randomOperationsMatchFullListingForSeveralReaders() {
        var fixture = new Fixture();
        List<AEKey> keys = keys(40, "random");
        Random random = new Random(20261028L);
        Reader every = new Reader(fixture.storage), sometimes = new Reader(fixture.storage), rarely = new Reader(fixture.storage);
        Map<AEKey, UUID> locks = new HashMap<>();
        for (int step = 0; step < 3_000; step++) {
            AEKey key = keys.get(random.nextInt(keys.size()));
            int operation = random.nextInt(100);
            if (operation < 45) {
                long amount = random.nextInt(50) == 0 ? Long.MAX_VALUE : random.nextInt(10_000) + 1;
                fixture.engine.insert(key, amount, Actionable.MODULATE);
            } else if (operation < 85) {
                fixture.engine.extract(key, random.nextBoolean() ? Long.MAX_VALUE : random.nextInt(10_000) + 1, Actionable.MODULATE);
            } else if (operation < 90) {
                UUID transaction = UUID.randomUUID();
                if (!locks.containsKey(key) && fixture.engine.reserveRestore(key, transaction, Set.of(UUID.randomUUID()))) {
                    locks.put(key, transaction);
                }
            } else if (operation < 95) {
                if (!locks.isEmpty()) {
                    var entry = locks.entrySet().iterator().next();
                    locks.remove(entry.getKey());
                    // A failed restore keeps its key hidden; a finished one reveals it again.
                    if (random.nextInt(4) == 0) fixture.engine.failRestore(entry.getKey(), "test");
                    else assertTrue(fixture.engine.finishRestore(entry.getKey(), entry.getValue()));
                }
            } else if (operation < 97) {
                fixture.allow[0] = !fixture.allow[0];
            } else {
                fixture.engine.commit();
            }
            every.refresh();
            every.assertMatches(fixture);
            if (step % 7 == 0) {
                sometimes.refresh();
                sometimes.assertMatches(fixture);
            }
            if (step % 97 == 0) {
                rarely.refresh();
                rarely.assertMatches(fixture);
            }
        }
    }

    @Test void readersBehindAnOverflowingChangeLogListAgain() {
        var fixture = new Fixture();
        List<AEKey> keys = keys(4_200, "overflow");
        Reader early = new Reader(fixture.storage), current = new Reader(fixture.storage);
        fixture.engine.insert(keys.get(0), 5, Actionable.MODULATE);
        early.refresh();
        current.refresh();
        for (AEKey key : keys) {
            fixture.engine.insert(key, 3, Actionable.MODULATE);
            if (key == keys.get(10)) current.refresh();
        }
        // Keys that only existed before the log window are removed again to prove the fallback is complete.
        fixture.engine.extract(keys.get(1), Long.MAX_VALUE, Actionable.MODULATE);
        int listings = fixture.listings[0];
        early.refresh();
        early.assertMatches(fixture);
        assertTrue(fixture.listings[0] > listings, "a reader older than the change log must list in full");
        current.refresh();
        current.assertMatches(fixture);
    }

    private static List<AEKey> keys(int count, String salt) {
        List<AEKey> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemStack stack = new ItemStack(Items.REDSTONE);
            stack.getOrCreateTag().putString("salt", salt);
            stack.getOrCreateTag().putInt("index", i);
            result.add(AEItemKey.of(stack));
        }
        return result;
    }

    private static final class Fixture {
        final SavedDataInfiniteStorageEngine engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        final int[] listings = {0};
        final boolean[] allow = {true};
        final ECOInfiniteStorage storage;

        Fixture() {
            // Counts full listings; every other call reaches the real engine unchanged.
            var counted = (ECOInfiniteStorageEngine) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ECOInfiniteStorageEngine.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAvailableStacks")) listings[0]++;
                    try {
                        return method.invoke(engine, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
            storage = new ECOInfiniteStorage(counted, Component.empty(), () -> allow[0]);
        }
    }

    private static final class Reader implements NetworkInventoryCache.Mounts {
        final NetworkInventoryCache cache = new NetworkInventoryCache();
        final KeyCounter published = new KeyCounter();
        final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
        final List<MEStorage> list;

        Reader(MEStorage storage) {
            list = List.of(storage);
        }

        void refresh() {
            assertTrue(cache.refresh(this, published, amounts, (key, amount) -> {}));
        }

        void assertMatches(Fixture fixture) {
            // The full listing, read from the real engine so it is not counted as a cache listing.
            KeyCounter expected = new KeyCounter();
            if (fixture.allow[0]) fixture.engine.getAvailableStacks(expected);
            expected.removeZeros();
            assertEquals(expected.size(), published.size());
            assertEquals(expected.size(), amounts.size());
            for (var entry : expected) {
                assertEquals(entry.getLongValue(), published.get(entry.getKey()));
                assertEquals(entry.getLongValue(), amounts.getLong(entry.getKey()));
            }
        }

        @Override public long neoecoae$mountGeneration() { return 0; }

        @Override public boolean neoecoae$withMounts(Consumer<List<MEStorage>> action) {
            action.accept(list);
            return true;
        }
    }
}
