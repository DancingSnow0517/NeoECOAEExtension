package cn.dancingsnow.neoecoae.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NEMathTest {
    @Test
    void saturatingAddClampsNegativeInputsAndOverflow() {
        assertEquals(0L, NEMath.saturatingAdd(-1L, -1L));
        assertEquals(5L, NEMath.saturatingAdd(-1L, 5L));
        assertEquals(5L, NEMath.saturatingAdd(5L, -1L));
        assertEquals(Long.MAX_VALUE, NEMath.saturatingAdd(Long.MAX_VALUE, 1L));
    }

    @Test
    void saturatingMultiplyClampsNegativeInputsZeroAndOverflow() {
        assertEquals(0L, NEMath.saturatingMultiply(-1L, 5L));
        assertEquals(0L, NEMath.saturatingMultiply(5L, 0L));
        assertEquals(42L, NEMath.saturatingMultiply(6L, 7L));
        assertEquals(Long.MAX_VALUE, NEMath.saturatingMultiply(Long.MAX_VALUE, 2L));
    }
}
