package cn.dancingsnow.neoecoae.dev;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class GeneratorSettingsTest {
    @Test void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorSettings(0, 1, 64, 20));
        assertThrows(IllegalArgumentException.class, () -> new GeneratorSettings(1, 0, 64, 20));
        assertThrows(IllegalArgumentException.class, () -> new GeneratorSettings(1, 65, 64, 20));
        assertThrows(IllegalArgumentException.class, () -> new GeneratorSettings(1, 1, 64, 0));
    }
    @Test void samplesInclusiveRangesWithoutOverflow() {
        var random = new Random(321);
        for (long[] bounds : new long[][] {{1, 1}, {1, 2}, {1, Long.MAX_VALUE}, {Long.MAX_VALUE - 1, Long.MAX_VALUE}}) {
            var settings = new GeneratorSettings(1, bounds[0], bounds[1], 1);
            boolean lower = false, upper = false;
            for (int i = 0; i < 10000; i++) {
                long amount = settings.nextAmount(random);
                assertTrue(amount >= bounds[0] && amount <= bounds[1]);
                lower |= amount == bounds[0];
                upper |= amount == bounds[1];
            }
            if (bounds[1] - bounds[0] <= 1) assertTrue(lower && upper);
        }
    }
}
