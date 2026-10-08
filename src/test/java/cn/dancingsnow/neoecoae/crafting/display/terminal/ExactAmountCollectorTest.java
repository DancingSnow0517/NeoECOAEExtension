package cn.dancingsnow.neoecoae.crafting.display.terminal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.prioritylist.PrecisePriorityList;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.DelegatingMEInventoryAccessor;
import cn.dancingsnow.neoecoae.network.MapDelta;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExactAmountCollectorTest {
    private AEKey key;
    private final MEStorage ordinary = mock(MEStorage.class);

    @BeforeEach void setUp() {
        key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(new Object());
        when(key.getFuzzySearchMaxValue()).thenReturn(0);
    }

    @AfterEach void cleanup() { ExactAmountCollector.abort(); }

    @Test void combinedLongInventoriesSynchronizeBeyondLongMaxInEitherOrder() {
        for (boolean reversed : new boolean[] {false, true}) {
            ExactAmountCollector.begin();
            observe(ordinary, reversed ? 64 : Long.MAX_VALUE);
            observe(ordinary, reversed ? Long.MAX_VALUE : 64);
            assertEquals(Map.of(key, ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE)
                    .add(BigInteger.valueOf(64)))), ExactAmountCollector.finish());
        }
    }

    @Test void overflowChangesAndReturnToLongRangeProduceDeltas() {
        ExactAmountCollector.begin();
        observe(ordinary, Long.MAX_VALUE);
        observe(ordinary, 64);
        var previous = ExactAmountCollector.finish();
        assertFalse(previous.isEmpty());
        ExactAmountCollector.begin();
        observe(ordinary, Long.MAX_VALUE);
        observe(ordinary, 128);
        var current = ExactAmountCollector.finish();
        assertFalse(MapDelta.between(previous, current).isEmpty());
        assertEquals(current, MapDelta.between(previous, current).apply(previous));
        ExactAmountCollector.begin();
        observe(ordinary, Long.MAX_VALUE);
        var reduced = ExactAmountCollector.finish();
        assertTrue(reduced.isEmpty());
        assertEquals(reduced, MapDelta.between(current, reduced).apply(current));
    }

    @Test void exactSourceReplacesSaturatedContributionAndAddsOrdinaryStorage() {
        var exactStorage = mock(MEStorage.class, withSettings().extraInterfaces(ExactAmountSource.class));
        var huge = ExactAmount.finite(BigInteger.TEN.pow(100));
        doAnswer(invocation -> {
            java.util.function.BiConsumer<AEKey, ExactAmount> visitor = invocation.getArgument(0);
            visitor.accept(key, huge);
            return null;
        }).when((ExactAmountSource) exactStorage).neoecoae$visitExactAmounts(any());
        ExactAmountCollector.begin();
        observe(exactStorage, Long.MAX_VALUE);
        observe(ordinary, 64);
        assertEquals(Map.of(key, huge.add(64)), ExactAmountCollector.finish());
    }

    @Test void negativeInventoryEntryDoesNotCrashOrHideOtherResources() {
        AEKey other = mock(AEKey.class);
        when(other.getPrimaryKey()).thenReturn(new Object());
        when(other.getFuzzySearchMaxValue()).thenReturn(0);
        for (long invalid : new long[] {-1, Long.MIN_VALUE, Long.MIN_VALUE + 63}) {
            var contribution = new KeyCounter();
            contribution.set(key, invalid);
            contribution.set(other, 42);
            ExactAmountCollector.begin();
            assertDoesNotThrow(() -> ExactAmountCollector.observe(ordinary, contribution));
            assertEquals(0, contribution.get(key));
            assertEquals(42, contribution.get(other));
            assertTrue(ExactAmountCollector.finish().isEmpty());
        }
    }

    @Test void exactSourceRestoresWrappedNegativeProjection() {
        var storage = mock(MEStorage.class, withSettings().extraInterfaces(ExactAmountSource.class));
        for (ExactAmount amount : new ExactAmount[] {
                ExactAmount.unbounded(), ExactAmount.finite(BigInteger.TEN.pow(30))}) {
            doAnswer(call -> {
                java.util.function.BiConsumer<AEKey, ExactAmount> visitor = call.getArgument(0);
                visitor.accept(key, amount);
                return null;
            }).when((ExactAmountSource) storage).neoecoae$visitExactAmounts(any());
            var contribution = new KeyCounter();
            contribution.set(key, Long.MIN_VALUE + 63);
            ExactAmountCollector.begin();
            ExactAmountCollector.observe(storage, contribution);
            assertEquals(Long.MAX_VALUE, contribution.get(key));
            assertEquals(Map.of(key, amount), ExactAmountCollector.finish());
        }
    }

    @Test void invalidEntryRemovesThePreviousExactDisplayValue() {
        ExactAmountCollector.begin();
        observe(ordinary, Long.MAX_VALUE);
        observe(ordinary, 64);
        var previous = ExactAmountCollector.finish();
        ExactAmountCollector.begin();
        observe(ordinary, -1);
        var current = ExactAmountCollector.finish();
        assertTrue(current.isEmpty());
        assertEquals(current, MapDelta.between(previous, current).apply(previous));
    }

    @Test void nestedNetworkIsCountedOnceAndRetainsItsUnsaturatedTotal() {
        ExactAmountCollector.begin();
        var outer = new KeyCounter();
        ExactAmountCollector.collect(ordinary, outer, output -> {
            for (long amount : new long[] {Long.MAX_VALUE, 64}) {
                var child = new KeyCounter();
                ExactAmountCollector.collect(ordinary, child, counter -> counter.add(key, amount));
                SaturatingStackAccumulator.addAll(output, child);
            }
        });
        assertEquals(Long.MAX_VALUE, outer.get(key));
        assertEquals(Map.of(key, ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE)
            .add(BigInteger.valueOf(64)))), ExactAmountCollector.finish());
    }

    @Test void wrappedInfiniteSourceRespectsTheStorageBusFilter() {
        AEKey hidden = mock(AEKey.class);
        when(hidden.getPrimaryKey()).thenReturn(new Object());
        when(hidden.getFuzzySearchMaxValue()).thenReturn(0);
        var source = mock(MEStorage.class, withSettings().extraInterfaces(ExactAmountSource.class));
        when(source.getAvailableStacks()).thenAnswer(call -> {
            var counter = new KeyCounter();
            counter.set(key, Long.MAX_VALUE);
            counter.set(hidden, Long.MAX_VALUE);
            return counter;
        });
        doAnswer(call -> {
            java.util.function.BiConsumer<AEKey, ExactAmount> visitor = call.getArgument(0);
            visitor.accept(key, ExactAmount.unbounded());
            visitor.accept(hidden, ExactAmount.unbounded());
            return null;
        }).when((ExactAmountSource) source).neoecoae$visitExactAmounts(any());
        var wrapper = new TestInventoryHandler(new TestInventoryHandler(source));
        wrapper.setExtractFiltering(true, true);
        var whitelist = new KeyCounter();
        whitelist.set(key, 1);
        wrapper.setPartitionList(new PrecisePriorityList(whitelist));
        ExactAmountCollector.begin();
        var contribution = new KeyCounter();
        ExactAmountCollector.collect(wrapper, contribution, wrapper::getAvailableStacks);
        assertEquals(Set.of(key), contribution.keySet());
        assertEquals(Map.of(key, ExactAmount.unbounded()), ExactAmountCollector.finish());
    }

    @Test void nestedNetworkFilterDoesNotLeakHiddenExactAmounts() {
        ExactAmountCollector.begin();
        var contribution = new KeyCounter();
        ExactAmountCollector.collect(ordinary, contribution, output -> {
            ExactAmountCollector.collect(ordinary, output, counter -> counter.add(key, Long.MAX_VALUE));
            ExactAmountCollector.collect(ordinary, output, counter -> counter.add(key, 64));
            output.remove(key);
        });
        assertTrue(contribution.isEmpty());
        assertTrue(ExactAmountCollector.finish().isEmpty());
    }

    @Test void failedNestedListingRestoresTheParentCollection() {
        ExactAmountCollector.begin();
        observe(ordinary, Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, () -> ExactAmountCollector.collect(ordinary,
            new KeyCounter(), counter -> {
                observe(ordinary, 128);
                throw new IllegalStateException("failed inventory");
            }));
        observe(ordinary, 64);
        assertEquals(Map.of(key, ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE)
            .add(BigInteger.valueOf(64)))), ExactAmountCollector.finish());
    }

    @Test void ordinaryListingsBetweenExactSyncsAlsoDiscardNegativeEntries() {
        var contribution = new KeyCounter();
        ExactAmountCollector.collect(ordinary, contribution, counter -> counter.add(key, -1));
        assertTrue(contribution.isEmpty());
    }

    private static final class TestInventoryHandler extends MEInventoryHandler
            implements DelegatingMEInventoryAccessor {
        TestInventoryHandler(MEStorage delegate) { super(delegate); }
        @Override public MEStorage neoecoae$getDelegate() { return getDelegate(); }
    }

    private void observe(MEStorage storage, long amount) {
        var counter = new KeyCounter();
        counter.set(key, amount);
        ExactAmountCollector.observe(storage, counter);
    }
}
