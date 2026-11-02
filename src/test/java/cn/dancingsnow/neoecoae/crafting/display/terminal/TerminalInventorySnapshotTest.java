package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.SupplierStorageAccessor;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TerminalInventorySnapshotTest {
    private final MEStorage storage = mock(MEStorage.class);
    private final AtomicInteger listings = new AtomicInteger();
    private final AtomicInteger exactListings = new AtomicInteger();
    private AEKey key;
    private long amount = Long.MAX_VALUE;
    private long extra = 64;

    @BeforeEach void setUp() {
        key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(new Object());
    }

    @AfterEach void clear() {
        ExactAmountCollector.abort();
        TerminalInventorySnapshot.clear();
    }

    @Test void viewersShareOneListingPerTickAndOneExactCollectionPerInterval() {
        var first = TerminalInventorySnapshot.sample(storage, 100, true, this::list);
        assertSame(first, TerminalInventorySnapshot.sample(storage, 100, true, this::list));
        assertEquals(1, listings.get());
        assertEquals(1, exactListings.get());
        assertEquals(ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(64))),
            first.amounts().get(key));
        extra = 128;
        var next = TerminalInventorySnapshot.sample(storage, 101, true, this::list);
        assertSame(first.amounts(), next.amounts());
        assertEquals(2, listings.get());
        assertEquals(1, exactListings.get());
        var updated = TerminalInventorySnapshot.sample(storage, 105, true, this::list);
        assertEquals(ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(128))),
            updated.amounts().get(key));
        assertEquals(3, listings.get());
        assertEquals(2, exactListings.get());
    }

    @Test void noExactDemandRunsOnlyTheOrdinaryListing() {
        var snapshot = TerminalInventorySnapshot.sample(storage, 10, false, this::list);
        assertTrue(snapshot.amounts().isEmpty());
        assertEquals(0, exactListings.get());
        var exact = TerminalInventorySnapshot.sample(storage, 10, true, this::list);
        assertFalse(exact.amounts().isEmpty());
        assertEquals(1, exactListings.get());
    }

    @Test void finiteLongsAreLeftToAe2AndOldSnapshotsAreNeverMutated() {
        var huge = TerminalInventorySnapshot.sample(storage, 0, true, this::list);
        amount = 42;
        extra = 1;
        var reduced = TerminalInventorySnapshot.sample(storage, 5, true, this::list);
        assertTrue(reduced.amounts().isEmpty());
        assertEquals(Long.MAX_VALUE, huge.inventory().get(key));
        assertEquals(43, reduced.inventory().get(key));
        assertFalse(huge.amounts().isEmpty());
    }

    @Test void exactSourceWithALossyLongSizedProjectionStillUsesTheSideChannel() {
        var source = mock(MEStorage.class, withSettings().extraInterfaces(ExactAmountSource.class));
        var exact = ExactAmount.finite(BigInteger.TEN.pow(10));
        doAnswer(call -> {
            BiConsumer<AEKey, ExactAmount> visitor = call.getArgument(0);
            visitor.accept(key, exact);
            return null;
        }).when((ExactAmountSource) source).neoecoae$visitExactAmounts(any());
        var snapshot = TerminalInventorySnapshot.sample(source, 0, true, () -> {
            var output = new KeyCounter();
            new StorageListingAccumulator().append(source, output, counter -> counter.add(key, Integer.MAX_VALUE));
            return output;
        });
        assertEquals(Integer.MAX_VALUE, snapshot.inventory().get(key));
        assertEquals(Map.of(key, exact), snapshot.amounts());
    }

    @Test void unchangedTotalsReuseTheSameExactMapAndClockRollbackResamples() {
        var first = TerminalInventorySnapshot.sample(storage, 50, true, this::list);
        var unchanged = TerminalInventorySnapshot.sample(storage, 55, true, this::list);
        assertSame(first.amounts(), unchanged.amounts());
        extra = 99;
        var rollback = TerminalInventorySnapshot.sample(storage, 1, true, this::list);
        assertNotEquals(first.amounts(), rollback.amounts());
        assertEquals(3, exactListings.get());
    }

    @Test void failedSampleRestoresCollectionAndCanBeRetriedInTheSameTick() {
        TerminalInventorySnapshot.sample(storage, 0, true, this::list);
        assertThrows(IllegalStateException.class, () -> TerminalInventorySnapshot.sample(storage, 5, true, () -> {
            throw new IllegalStateException("failed storage");
        }));
        assertFalse(ExactAmountCollector.isActive());
        extra = 256;
        assertEquals(ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(256))),
            TerminalInventorySnapshot.sample(storage, 5, true, this::list).amounts().get(key));
        assertEquals(2, exactListings.get());
    }

    @Test void onlyTransparentSuppliersShareTheirUnderlyingStorage() {
        var wrapper = mock(MEStorage.class, withSettings().extraInterfaces(SupplierStorageAccessor.class));
        var filtered = mock(MEStorage.class);
        when(((SupplierStorageAccessor) wrapper).neoecoae$getDelegate()).thenReturn(storage, filtered);
        assertSame(storage, TerminalInventorySnapshot.resolve(wrapper));
        assertSame(filtered, TerminalInventorySnapshot.resolve(wrapper));
        assertSame(filtered, TerminalInventorySnapshot.resolve(filtered));
        TerminalInventorySnapshot.sample(storage, 0, true, this::list);
        var separate = TerminalInventorySnapshot.sample(filtered, 0, true, KeyCounter::new);
        assertTrue(separate.inventory().isEmpty());
        assertTrue(separate.amounts().isEmpty());
    }

    @Test void serverCleanupDropsSharedSnapshots() {
        TerminalInventorySnapshot.sample(storage, 0, true, this::list);
        TerminalInventorySnapshot.clear();
        TerminalInventorySnapshot.sample(storage, 0, true, this::list);
        assertEquals(2, listings.get());
        assertEquals(2, exactListings.get());
    }

    private KeyCounter list() {
        listings.incrementAndGet();
        if (ExactAmountCollector.isActive()) exactListings.incrementAndGet();
        var out = new KeyCounter();
        var accumulator = new StorageListingAccumulator();
        accumulator.append(storage, out, counter -> counter.add(key, amount));
        accumulator.append(storage, out, counter -> counter.add(key, extra));
        return out;
    }
}
