package cn.dancingsnow.neoecoae.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JdkCompatTest {
    @Test
    void clampsBeforeNarrowingLongToInteger() {
        assertEquals(Integer.MAX_VALUE, JdkCompat.clamp(Long.MAX_VALUE, 0, Integer.MAX_VALUE));
        assertEquals(Integer.MIN_VALUE, JdkCompat.clamp(Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertEquals(7, JdkCompat.clamp(7L, -10, 10));
    }

    @Test
    void preservesLongExtremesAndRejectsInvertedBounds() {
        assertEquals(Long.MAX_VALUE, JdkCompat.clamp(Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(-3L, JdkCompat.clamp(Long.MIN_VALUE, -3L, 5L));
        assertThrows(IllegalArgumentException.class, () -> JdkCompat.clamp(0L, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> JdkCompat.clamp(0L, Long.MAX_VALUE, Long.MIN_VALUE));
    }

    @Test
    void floatingPointValuesKeepNanAndClampInfinities() {
        assertTrue(Double.isNaN(JdkCompat.clamp(Double.NaN, 0.0, 1.0)));
        assertTrue(Float.isNaN(JdkCompat.clamp(Float.NaN, 0.0f, 1.0f)));
        assertEquals(1.0, JdkCompat.clamp(Double.POSITIVE_INFINITY, 0.0, 1.0));
        assertEquals(0.0f, JdkCompat.clamp(Float.NEGATIVE_INFINITY, 0.0f, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> JdkCompat.clamp(0.0, Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> JdkCompat.clamp(0.0f, 0.0f, Float.NaN));
    }

    @Test
    void signedZeroUsesTotalOrderingForBoundsAndPreservesValueSign() {
        assertThrows(IllegalArgumentException.class, () -> JdkCompat.clamp(0.0, +0.0, -0.0));
        assertThrows(IllegalArgumentException.class, () -> JdkCompat.clamp(0.0f, +0.0f, -0.0f));
        assertEquals(Double.doubleToRawLongBits(-0.0),
            Double.doubleToRawLongBits(JdkCompat.clamp(-0.0, -0.0, +0.0)));
        assertEquals(Float.floatToRawIntBits(+0.0f),
            Float.floatToRawIntBits(JdkCompat.clamp(+0.0f, -0.0f, +0.0f)));
    }
}
