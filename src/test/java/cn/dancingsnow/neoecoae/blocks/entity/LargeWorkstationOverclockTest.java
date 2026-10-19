package cn.dancingsnow.neoecoae.blocks.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LargeWorkstationOverclockTest {
    @Test
    void mapsCoolingRecipeTiersToBatchLimitsAndEnergyMultipliers() {
        assertEquals(new LargeWorkstationOverclock(2, 16_384, 1), LargeWorkstationOverclock.forCoolingTier(2));
        assertEquals(new LargeWorkstationOverclock(6, 65_536, 1), LargeWorkstationOverclock.forCoolingTier(6));
        assertEquals(new LargeWorkstationOverclock(9, 262_144, 1), LargeWorkstationOverclock.forCoolingTier(9));
        assertNull(LargeWorkstationOverclock.forCoolingTier(4));
    }

    @Test
    void temperatureContinuouslyControlsCapacityWithinTheSelectedMaximum() {
        assertEquals(256, LargeWorkstationOverclock.NORMAL.maxParallelism());
        assertEquals(256, LargeWorkstationOverclock.forTemperature(false, 9, 90).maxParallelism());
        assertEquals(256, LargeWorkstationOverclock.forTemperature(true, 9, 19.999).maxParallelism());
        assertEquals(16_384, LargeWorkstationOverclock.forTemperature(true, 9, 20).maxParallelism());
        assertEquals(16_384, LargeWorkstationOverclock.forTemperature(true, 9, 49.999).maxParallelism());
        assertEquals(65_536, LargeWorkstationOverclock.forTemperature(true, 9, 50).maxParallelism());
        assertEquals(65_536, LargeWorkstationOverclock.forTemperature(true, 9, 79.999).maxParallelism());
        assertEquals(262_144, LargeWorkstationOverclock.forTemperature(true, 9, 80).maxParallelism());
        assertEquals(16_384, LargeWorkstationOverclock.forTemperature(true, 2, 90).maxParallelism());
        assertEquals(256, LargeWorkstationOverclock.forTemperature(true, 9, 100).maxParallelism());
    }

    @Test
    void normalLegacyAndOverclockedBatchLimitsAreValidatedByTheirLockedProfile() {
        assertEquals(LargeWorkstationOverclock.NORMAL, LargeWorkstationOverclock.fromPersisted(0, 1));
        assertNull(LargeWorkstationOverclock.fromPersisted(6, 8));

        LargeWorkstationOverclock legacy = LargeWorkstationOverclock.NORMAL;
        assertTrue(legacy.acceptsCraftCount(256));
        assertFalse(legacy.acceptsCraftCount(257));

        LargeWorkstationOverclock sodium = LargeWorkstationOverclock.fromPersisted(6, 32);
        assertTrue(sodium.acceptsCraftCount(65_536));
        assertFalse(sodium.acceptsCraftCount(65_537));
        assertEquals(1, sodium.energyMultiplier(), "Legacy x32 batches migrate to x1 without dropping owned inputs");
        assertEquals(sodium, LargeWorkstationOverclock.fromPersisted(6, 1));
    }
}
