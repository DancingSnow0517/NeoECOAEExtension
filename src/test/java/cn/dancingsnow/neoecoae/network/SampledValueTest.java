package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.gui.common.SampledValue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SampledValueTest {
    @Test void samplesImmediatelyThenOncePerIntervalAndSurvivesClockReset() {
        var tick = new AtomicLong(100);
        var count = new AtomicInteger();
        var value = new SampledValue<>(tick::get, count::incrementAndGet, 5);
        assertEquals(1, value.get());
        tick.set(104);
        assertEquals(1, value.get());
        tick.set(105);
        assertEquals(2, value.get());
        tick.set(0);
        assertEquals(3, value.get());
    }
}
