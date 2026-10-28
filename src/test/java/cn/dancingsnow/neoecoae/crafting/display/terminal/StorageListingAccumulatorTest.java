package cn.dancingsnow.neoecoae.crafting.display.terminal;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageListingAccumulatorTest {
    private final StorageListingAccumulator accumulator = new StorageListingAccumulator();
    private final MEStorage ordinary = mock(MEStorage.class);
    private AEKey key;

    @BeforeEach void setUp() { key = key(); }
    @AfterEach void clear() { ExactAmountCollector.abort(); }

    @Test void ordinaryScansReuseTheirCounterWithoutReadingExactSources() {
        var source = mock(MEStorage.class, withSettings().extraInterfaces(CombinedExactAmountSource.class));
        var counters = new ArrayList<KeyCounter>();
        var output = new KeyCounter();
        for (int i = 0; i < 100; i++) {
            accumulator.append(source, output, counter -> {
                assertTrue(counter.isEmpty());
                counters.add(counter);
                counter.add(key, 64);
            });
        }
        assertEquals(6_400, output.get(key));
        assertTrue(counters.stream().allMatch(counter -> counter == JdkCompat.first(counters)));
        verify((ExactAmountSource) source, times(100)).neoecoae$hasSaturatingListing();
        verify((ExactAmountSource) source, never()).neoecoae$visitExactAmounts(any());
        verify((CombinedExactAmountSource) source, never()).neoecoae$listWithExactAmounts(any(), any());
    }

    @Test void trustedSaturatingSourceAppendsDirectlyWithoutAnIntermediateCounter() {
        var source = mock(MEStorage.class, withSettings().extraInterfaces(ExactAmountSource.class));
        when(((ExactAmountSource) source).neoecoae$hasSaturatingListing()).thenReturn(true);
        var output = new KeyCounter();
        output.add(key, 64);
        accumulator.append(source, output, counter -> {
            assertSame(output, counter);
            cn.dancingsnow.neoecoae.storage.SaturatingStackAccumulator.add(counter, key, Long.MAX_VALUE);
        });
        assertEquals(Long.MAX_VALUE, output.get(key));
        verify((ExactAmountSource) source, never()).neoecoae$visitExactAmounts(any());
    }

    @Test void ordinaryScansSaturateAndDiscardNegativeContributionsInEitherOrder() {
        for (boolean reverse : new boolean[] {false, true}) {
            var output = new KeyCounter();
            accumulator.append(ordinary, output, counter -> counter.set(key, reverse ? 64 : Long.MAX_VALUE));
            accumulator.append(ordinary, output, counter -> counter.set(key, -1));
            accumulator.append(ordinary, output, counter -> counter.set(key, reverse ? Long.MAX_VALUE : 64));
            assertEquals(Long.MAX_VALUE, output.get(key));
        }
    }

    @Test void malformedProjectionUsesItsExactSourceWithoutCancellingValidStock() {
        var source = mock(MEStorage.class, withSettings().extraInterfaces(ExactAmountSource.class));
        doAnswer(call -> {
            BiConsumer<AEKey, ExactAmount> visitor = call.getArgument(0);
            visitor.accept(key, ExactAmount.finite(BigInteger.TEN.pow(30)));
            return null;
        }).when((ExactAmountSource) source).neoecoae$visitExactAmounts(any());
        var output = new KeyCounter();
        accumulator.append(ordinary, output, counter -> counter.add(key, 64));
        accumulator.append(source, output, counter -> counter.add(key, Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, output.get(key));
        verify((ExactAmountSource) source).neoecoae$visitExactAmounts(any());
    }

    @Test void terminalCollectionKeepsExactOverflowAndUsesCombinedSourceOnce() {
        var source = mock(MEStorage.class, withSettings().extraInterfaces(CombinedExactAmountSource.class));
        var huge = ExactAmount.finite(BigInteger.TEN.pow(30));
        doAnswer(call -> {
            KeyCounter output = call.getArgument(0);
            BiConsumer<AEKey, ExactAmount> visitor = call.getArgument(1);
            output.set(key, Long.MAX_VALUE);
            visitor.accept(key, huge);
            return null;
        }).when((CombinedExactAmountSource) source).neoecoae$listWithExactAmounts(any(), any());
        ExactAmountCollector.begin();
        var output = new KeyCounter();
        accumulator.append(source, output, counter -> fail("Combined source must list only once"));
        accumulator.append(ordinary, output, counter -> counter.add(key, 64));
        assertEquals(Long.MAX_VALUE, output.get(key));
        assertEquals(Map.of(key, huge.add(64)), ExactAmountCollector.finish());
        verify((CombinedExactAmountSource) source).neoecoae$listWithExactAmounts(any(), any());
        verify((CombinedExactAmountSource) source, never()).neoecoae$visitExactAmounts(any());
    }

    @Test void failedListingDoesNotLeaveStockInTheNextContribution() {
        assertThrows(IllegalStateException.class, () -> accumulator.append(ordinary, new KeyCounter(), counter -> {
            counter.add(key, 123);
            throw new IllegalStateException("failed listing");
        }));
        var output = new KeyCounter();
        accumulator.append(ordinary, output, counter -> assertTrue(counter.isEmpty()));
        assertTrue(output.isEmpty());
    }

    private static AEKey key() {
        var key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(new Object());
        return key;
    }
}
