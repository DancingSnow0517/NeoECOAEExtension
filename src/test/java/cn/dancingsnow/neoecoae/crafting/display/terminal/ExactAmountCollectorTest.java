package cn.dancingsnow.neoecoae.crafting.display.terminal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.network.MapDelta;
import java.math.BigInteger;
import java.util.Map;
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

    private void observe(MEStorage storage, long amount) {
        var counter = new KeyCounter();
        counter.set(key, amount);
        ExactAmountCollector.observe(storage, counter);
    }
}
