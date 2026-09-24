package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LargeWorkstationOverclockTest {
    @Test
    void coolingTiersDefineBatchLimitAndEnergyMultiplier() {
        assertEquals(new LargeWorkstationOverclock(2, 16_384, 8), LargeWorkstationOverclock.forCoolingTier(2));
        assertEquals(new LargeWorkstationOverclock(6, 65_536, 32), LargeWorkstationOverclock.forCoolingTier(6));
        assertEquals(new LargeWorkstationOverclock(9, 262_144, 64), LargeWorkstationOverclock.forCoolingTier(9));
        assertNull(LargeWorkstationOverclock.forCoolingTier(4));
    }

    @Test
    void overclockNeedsBothSwitchesAndRecognizedCooling() {
        assertEquals(LargeWorkstationOverclock.NORMAL, LargeWorkstationOverclock.forCurrentSettings(false, true, 9));
        assertEquals(LargeWorkstationOverclock.NORMAL, LargeWorkstationOverclock.forCurrentSettings(true, false, 9));
        assertEquals(LargeWorkstationOverclock.NORMAL, LargeWorkstationOverclock.forCurrentSettings(true, true, -1));
        assertEquals(new LargeWorkstationOverclock(6, 65_536, 32),
                LargeWorkstationOverclock.forCurrentSettings(true, true, 6));
    }

    @Test
    void persistedProfileLocksBatchLimit() {
        assertEquals(LargeWorkstationOverclock.NORMAL, LargeWorkstationOverclock.fromPersisted(0, 1));
        assertNull(LargeWorkstationOverclock.fromPersisted(6, 8));
        assertTrue(LargeWorkstationOverclock.NORMAL.acceptsCraftCount(1_024));
        assertFalse(LargeWorkstationOverclock.NORMAL.acceptsCraftCount(1_025));
        LargeWorkstationOverclock sodium = LargeWorkstationOverclock.fromPersisted(6, 32);
        assertTrue(sodium.acceptsCraftCount(65_536));
        assertFalse(sodium.acceptsCraftCount(65_537));
    }
}
