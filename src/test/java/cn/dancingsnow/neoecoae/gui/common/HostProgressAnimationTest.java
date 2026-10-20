package cn.dancingsnow.neoecoae.gui.common;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostProgressAnimationTest {
    @Test
    void startsAtFirstVisibleValueAndStopsAtTheActualRatio() {
        AtomicLong now = new AtomicLong();
        HostProgressAnimation animation = new HostProgressAnimation(now::get);
        now.set(10_000);
        assertEquals(0, animation.value(0.37));
        now.addAndGet(250);
        assertEquals(0.185F, animation.value(0.37), 0.00001F);
        now.addAndGet(250);
        assertEquals(0.37F, animation.value(0.37));
        now.addAndGet(10_000);
        assertEquals(0.37F, animation.value(0.37));
    }

    @Test
    void waitsForNonzeroSyncedDataAndClampsInvalidRatios() {
        AtomicLong now = new AtomicLong();
        HostProgressAnimation animation = new HostProgressAnimation(now::get);
        assertEquals(0, animation.value(0));
        assertEquals(0, animation.value(-1));
        assertEquals(0, animation.value(Double.NaN));
        assertEquals(0, animation.value(Double.POSITIVE_INFINITY));
        now.set(5_000);
        assertEquals(0, animation.value(2));
        now.addAndGet(500);
        assertEquals(1, animation.value(2));
    }

    @Test
    void changingLiveValuesDoesNotRestartTheOpening() {
        AtomicLong now = new AtomicLong();
        HostProgressAnimation animation = new HostProgressAnimation(now::get);
        animation.value(0.75);
        now.set(250);
        assertEquals(0.125F, animation.value(0.25));
        now.set(500);
        assertEquals(0.6F, animation.value(0.6));
        assertEquals(0.2F, animation.value(0.2));
        assertEquals(0, animation.value(0));
        assertEquals(0.4F, animation.value(0.4));
    }

    @Test
    void resetReplaysTheOpeningFromZero() {
        AtomicLong now = new AtomicLong();
        HostProgressAnimation animation = new HostProgressAnimation(now::get);
        animation.value(0.5);
        now.set(500);
        assertEquals(0.5F, animation.value(0.5));
        animation.reset();
        assertEquals(0, animation.value(0.5));
        now.set(750);
        assertEquals(0.25F, animation.value(0.5));
        now.set(1_000);
        assertEquals(0.5F, animation.value(0.5));
    }

    @Test
    void animationIsMonotonicAndIndependentOfFrameRate() {
        AtomicLong now = new AtomicLong();
        HostProgressAnimation frequent = new HostProgressAnimation(now::get);
        HostProgressAnimation sparse = new HostProgressAnimation(now::get);
        frequent.value(0.8);
        sparse.value(0.8);
        float previous = 0;
        for (int time = 1; time <= 500; time++) {
            now.set(time);
            float value = frequent.value(0.8);
            assertTrue(value >= previous && value <= 0.8F);
            previous = value;
            if (time % 125 == 0) assertEquals(value, sparse.value(0.8));
        }
        assertEquals(0.8F, previous);
    }
}
