package cn.dancingsnow.neoecoae.crafting.display.terminal;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.storage.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.storage.StorageListingDelta;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TerminalNetworkSnapshotTest {
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @AfterEach void clear() { TerminalInventorySnapshot.clear(); }

    @Test void changedKeysAndUnchangedSnapshotsAreSharedWithoutRelistingTheDomain() {
        var key = AEItemKey.of(Items.STONE);
        var source = new Domain();
        source.engine.insert(key, 10, Actionable.MODULATE);
        var network = new Network(source);
        var first = network.sample(0, true);
        source.engine.insert(key, 5, Actionable.MODULATE);
        var second = network.sample(1, false);
        assertEquals(10, first.inventory().get(key), "published baseline stays immutable");
        assertEquals(15, second.inventory().get(key));
        var viewer = new TerminalInventoryChanges();
        var other = new TerminalInventoryChanges();
        Set<AEKey> delta = viewer.between(first.inventory(), second.inventory());
        assertEquals(Set.of(key), delta);
        assertSame(delta, other.between(first.inventory(), second.inventory()));
        assertThrows(UnsupportedOperationException.class, () -> delta.clear());
        for (int tick = 2; tick <= 220; tick++) {
            var next = network.sample(tick, tick % 5 == 0);
            assertSame(second.inventory(), next.inventory());
            assertTrue(viewer.between(second.inventory(), next.inventory()).isEmpty());
        }
        assertEquals(1, source.listings);
        assertEquals(0, source.exactListings, "ordinary long stock does not build exact quantities");
        assertEquals(0, network.fullListings);
    }

    @Test void exactOnlyChangesUseTheJournalAndDoNotReplaceTheSaturatedCounter() {
        var key = AEItemKey.of(Items.STONE);
        var source = new Domain();
        source.engine.insert(key, MAX.add(BigInteger.valueOf(3)), Actionable.MODULATE);
        var network = new Network(source);
        var first = network.sample(0, true);
        source.engine.insert(key, 7, Actionable.MODULATE);
        var waiting = network.sample(1, false);
        assertSame(first.inventory(), waiting.inventory());
        var exact = network.sample(5, true);
        assertSame(first.inventory(), exact.inventory());
        assertEquals(MAX.add(BigInteger.TEN), exact.amounts().get(key).value());
        assertEquals(MAX.add(BigInteger.valueOf(3)), first.amounts().get(key).value());
        assertEquals(1, source.listings);
        assertEquals(1, source.exactListings);
        assertEquals(1, source.exactReads);
        source.engine.extract(key, Long.MAX_VALUE, Actionable.MODULATE);
        var ordinary = network.sample(10, true);
        assertEquals(10, ordinary.inventory().get(key));
        assertTrue(ordinary.amounts().isEmpty());
        assertEquals(1, source.listings);
    }

    @Test void ordinaryContributionsOverflowExactlyAndUnmountingRemovesTheSideChannel() {
        var key = AEItemKey.of(Items.STONE);
        var first = new Domain();
        var second = new Domain();
        first.engine.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        second.engine.insert(key, 64, Actionable.MODULATE);
        var network = new Network(first, second);
        var overflow = network.sample(0, true);
        assertEquals(Long.MAX_VALUE, overflow.inventory().get(key));
        assertEquals(MAX.add(BigInteger.valueOf(64)), overflow.amounts().get(key).value());
        network.mounts.remove(second);
        network.generation++;
        var remaining = network.sample(5, true);
        assertEquals(Long.MAX_VALUE, remaining.inventory().get(key));
        assertTrue(remaining.amounts().isEmpty());
        assertEquals(0, first.exactListings + second.exactListings);
    }

    @Test void unversionedSourcesRefreshEveryTickAndExactSamplingDoesNotListThemTwice() {
        var source = new Legacy();
        var network = new Network(source);
        for (int tick = 0; tick <= 10; tick++) {
            source.amount++;
            var snapshot = network.sample(tick, tick % 5 == 0);
            assertEquals(Math.min(source.amount, Integer.MAX_VALUE), snapshot.inventory().get(source.key));
            if (tick % 5 == 0) assertEquals(BigInteger.valueOf(source.amount), snapshot.amounts().get(source.key).value());
            assertEquals(tick + 1, source.listings);
        }
        assertEquals(3, source.exactListings);
    }

    @Test void nestedNetworksKeepExactValuesAndTheirFilteredVisibleKeys() {
        var key = AEItemKey.of(Items.STONE);
        var hidden = AEItemKey.of(Items.DIRT);
        var source = new Domain();
        source.engine.insert(key, MAX.add(BigInteger.valueOf(9)), Actionable.MODULATE);
        source.engine.insert(hidden, MAX.add(BigInteger.TEN), Actionable.MODULATE);
        var child = new Network(source);
        var filtered = new appeng.me.storage.MEInventoryHandler(child);
        var filter = new KeyCounter();
        filter.set(key, 1);
        filtered.setPartitionList(new appeng.util.prioritylist.PrecisePriorityList(filter));
        filtered.setExtractFiltering(true, true);
        var parent = new Network(filtered);
        var result = parent.sample(0, true);
        assertEquals(Set.of(key), result.amounts().keySet());
        assertEquals(MAX.add(BigInteger.valueOf(9)), result.amounts().get(key).value());
        assertEquals(0, result.inventory().get(hidden));
    }

    @Test void slowViewersReceiveNetChangesAndFallBackWhenHistoryExpires() {
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var source = new Domain();
        source.engine.insert(stone, 10, Actionable.MODULATE);
        var network = new Network(source);
        var first = network.sample(0, false);
        source.engine.insert(stone, 3, Actionable.MODULATE);
        network.sample(1, false);
        source.engine.insert(dirt, 2, Actionable.MODULATE);
        network.sample(2, false);
        source.engine.extract(stone, 3, Actionable.MODULATE);
        var third = network.sample(3, false);
        var viewer = new TerminalInventoryChanges();
        assertEquals(Set.of(dirt), viewer.between(first.inventory(), third.inventory()));
        for (int tick = 4; tick < 45; tick++) {
            source.engine.insert(stone, 1, Actionable.MODULATE);
            network.sample(tick, false);
        }
        var latest = network.sample(45, false);
        assertNull(TerminalInventorySnapshot.changesBetween(first.inventory(), latest.inventory()));
        assertEquals(Set.of(stone, dirt), viewer.between(first.inventory(), latest.inventory()));
        assertEquals(10, first.inventory().get(stone));
        assertEquals(1, source.listings);
    }

    @Test void accessTogglesAndClockRollbackInvalidateAllRelevantViews() {
        var key = AEItemKey.of(Items.STONE);
        var source = new Domain();
        source.engine.insert(key, MAX.add(BigInteger.TEN), Actionable.MODULATE);
        var network = new Network(source);
        var first = network.sample(100, true);
        source.access = false;
        var hidden = network.sample(105, true);
        assertTrue(hidden.inventory().isEmpty());
        assertTrue(hidden.amounts().isEmpty());
        source.access = true;
        var restored = network.sample(110, true);
        assertEquals(first.amounts(), restored.amounts());
        source.engine.insert(key, 1, Actionable.MODULATE);
        var rollback = network.sample(1, true);
        assertEquals(MAX.add(BigInteger.valueOf(11)), rollback.amounts().get(key).value());
    }

    @Test void duplicateMountsUseTheNativeListingAndRemainCountedTwice() {
        var source = new Domain();
        var key = AEItemKey.of(Items.STONE);
        source.engine.insert(key, 9, Actionable.MODULATE);
        var network = new Network(source, source);
        var snapshot = network.sample(0, true);
        assertEquals(18, snapshot.inventory().get(key));
        assertEquals(1, network.fullListings);
    }

    @Test void aFailedListingCanBeRetriedWithoutPublishingPartialChanges() {
        var source = new Legacy();
        var network = new Network(source);
        var initial = network.sample(0, true);
        source.fail = true;
        assertThrows(IllegalStateException.class, () -> network.sample(5, true));
        assertFalse(ExactAmountCollector.isActive());
        source.fail = false;
        source.amount += 7;
        var retry = network.sample(5, true);
        assertEquals(BigInteger.valueOf(source.amount), retry.amounts().get(source.key).value());
        assertNotEquals(initial.amounts(), retry.amounts());
    }

    @Test void scopedBusFilteringNeitherReadsNorReplacesAnUnfilteredSameTickSnapshot() {
        var key = AEItemKey.of(Items.STONE);
        var domain = new Domain();
        domain.engine.insert(key, 5, Actionable.MODULATE);
        var external = new MEStorage() {
            @Override public void getAvailableStacks(KeyCounter out) { out.add(key, 7); }
            @Override public Component getDescription() { return Component.empty(); }
        };
        var bus = new cn.dancingsnow.neoecoae.mixins.ae2.storage.StorageBusInventoryMixin(external) {};
        var network = new Network(domain, bus);
        var ordinary = network.sample(0, false);
        assertEquals(12, ordinary.inventory().get(key));
        long filtered = cn.dancingsnow.neoecoae.storage.transfer.StorageBusTransferFilter.transfer(true,
                () -> network.sample(0, false).inventory().get(key));
        assertEquals(5, filtered);
        assertSame(ordinary, network.sample(0, false));
        assertEquals(12, ordinary.inventory().get(key));
    }

    @Test void anAmbientExactCollectorStillReceivesValuesWhenTheNetworkAlreadyHasASnapshot() {
        var key = AEItemKey.of(Items.STONE);
        var domain = new Domain();
        BigInteger amount = MAX.add(BigInteger.TEN);
        domain.engine.insert(key, amount, Actionable.MODULATE);
        var network = new Network(domain);
        var ordinary = network.sample(0, true);
        ExactAmountCollector.begin();
        try {
            assertEquals(Long.MAX_VALUE, network.sample(0, true).inventory().get(key));
            assertEquals(Map.of(key, ExactAmount.finite(amount)), ExactAmountCollector.finish());
        } finally { ExactAmountCollector.abort(); }
        assertSame(ordinary, network.sample(0, false));
    }

    private static final class Network implements MEStorage, NetworkInventoryCache.Mounts, TerminalInventorySnapshot.Source {
        final List<MEStorage> mounts = new ArrayList<>();
        final TerminalInventorySnapshot.Cache cache = new TerminalInventorySnapshot.Cache();
        final StorageListingAccumulator listing = new StorageListingAccumulator();
        long generation;
        int fullListings;
        boolean locked;

        Network(MEStorage... sources) { mounts.addAll(List.of(sources)); }
        TerminalInventorySnapshot sample(long tick, boolean exact) {
            return TerminalInventorySnapshot.sample(this, tick, exact, this::getAvailableStacks);
        }
        @Override public TerminalInventorySnapshot.Cache neoecoae$terminalCache() { return cache; }
        @Override public long neoecoae$mountGeneration() { return generation; }
        @Override public boolean neoecoae$withMounts(Consumer<List<MEStorage>> action) {
            if (locked) return false;
            locked = true;
            try { action.accept(mounts); } finally { locked = false; }
            return true;
        }
        @Override public void getAvailableStacks(KeyCounter out) {
            fullListings++;
            for (MEStorage source : mounts) listing.append(source, out, source::getAvailableStacks);
        }
        @Override public Component getDescription() { return Component.empty(); }
    }

    private static final class Domain implements MEStorage, StorageListingDelta, SparseExactAmountSource {
        final SavedDataInfiniteStorageEngine engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        boolean access = true;
        final ECOInfiniteStorage storage = new ECOInfiniteStorage(engine, Component.empty(), () -> access);
        int listings;
        int exactListings;
        int exactReads;
        @Override public void getAvailableStacks(KeyCounter out) { listings++; storage.getAvailableStacks(out); }
        @Override public long neoecoae$listingVersion() { return storage.neoecoae$listingVersion(); }
        @Override public boolean neoecoae$visitListingChanges(long since, long current, java.util.function.ObjLongConsumer<AEKey> visitor) {
            return storage.neoecoae$visitListingChanges(since, current, visitor);
        }
        @Override public boolean neoecoae$hasSaturatingListing() { return true; }
        @Override public boolean neoecoae$mayHaveExceptionalAmounts() { return storage.neoecoae$mayHaveExceptionalAmounts(); }
        @Override public void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
            storage.neoecoae$visitExactAmounts(visitor);
        }
        @Override public void neoecoae$visitExceptionalAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
            exactListings++;
            storage.neoecoae$visitExceptionalAmounts(visitor);
        }
        @Override public ExactAmount neoecoae$exceptionalAmount(AEKey key) {
            exactReads++;
            return storage.neoecoae$exceptionalAmount(key);
        }
        @Override public Component getDescription() { return Component.empty(); }
    }

    private static final class Legacy implements MEStorage, ExactAmountSource {
        final AEKey key = AEItemKey.of(Items.STONE);
        long amount = 10_000_000_000L;
        int listings;
        int exactListings;
        boolean fail;
        @Override public void getAvailableStacks(KeyCounter out) {
            listings++;
            out.set(key, Math.min(amount, Integer.MAX_VALUE));
            if (fail) throw new IllegalStateException("fixture");
        }
        @Override public void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
            exactListings++;
            visitor.accept(key, ExactAmount.finite(BigInteger.valueOf(amount)));
        }
        @Override public Component getDescription() { return Component.empty(); }
    }
}
