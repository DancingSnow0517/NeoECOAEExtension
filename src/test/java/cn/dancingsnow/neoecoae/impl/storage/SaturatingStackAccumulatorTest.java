package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SaturatingStackAccumulatorTest {
    private AEKey key;

    @BeforeEach
    void setUp() {
        key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(new Object());
        when(key.getFuzzySearchMaxValue()).thenReturn(0);
    }

    @Test
    void infiniteThenNormalAmountDoesNotWrap() {
        KeyCounter total = counter(Long.MAX_VALUE);

        SaturatingStackAccumulator.addAll(total, counter(64L));

        assertEquals(Long.MAX_VALUE, total.get(key));
    }

    @Test
    void normalThenInfiniteAmountDoesNotWrap() {
        KeyCounter total = counter(64L);

        SaturatingStackAccumulator.addAll(total, counter(Long.MAX_VALUE));

        assertEquals(Long.MAX_VALUE, total.get(key));
    }

    @Test
    void ordinaryAmountsStillAddExactly() {
        KeyCounter total = counter(64L);

        SaturatingStackAccumulator.addAll(total, counter(128L));

        assertEquals(192L, total.get(key));
    }

    private KeyCounter counter(long amount) {
        KeyCounter counter = new KeyCounter();
        counter.set(key, amount);
        return counter;
    }
}
