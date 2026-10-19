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
    void temperatureAutomaticallyControlsCapacityWithoutASelectedMaximum() {
        assertEquals(256, LargeWorkstationOverclock.NORMAL.maxParallelism());
        assertEquals(256, LargeWorkstationOverclock.forTemperature(false, 90).maxParallelism());
        assertEquals(256, LargeWorkstationOverclock.forTemperature(true, 19.999).maxParallelism());
        assertEquals(16_384, LargeWorkstationOverclock.forTemperature(true, 20).maxParallelism());
        assertEquals(16_384, LargeWorkstationOverclock.forTemperature(true, 49.999).maxParallelism());
        assertEquals(65_536, LargeWorkstationOverclock.forTemperature(true, 50).maxParallelism());
        assertEquals(65_536, LargeWorkstationOverclock.forTemperature(true, 79.999).maxParallelism());
        assertEquals(262_144, LargeWorkstationOverclock.forTemperature(true, 80).maxParallelism());
        assertEquals(262_144, LargeWorkstationOverclock.forTemperature(true, 90).maxParallelism());
        assertEquals(256, LargeWorkstationOverclock.forTemperature(true, 100).maxParallelism());
    }

    @Test
    void coolantModeUsesTheMatchingTierOnlyWhenBothSwitchesAreEnabled() {
        assertEquals(256, LargeWorkstationOverclock.forCoolant(true, true, 0).maxParallelism());
        assertEquals(256, LargeWorkstationOverclock.forCoolant(true, true, 4).maxParallelism());
        assertEquals(256, LargeWorkstationOverclock.forCoolant(false, true, 9).maxParallelism());
        assertEquals(256, LargeWorkstationOverclock.forCoolant(true, false, 9).maxParallelism());
        for (int tier : new int[] {2, 6, 9}) {
            assertEquals(LargeWorkstationOverclock.forCoolingTier(tier),
                LargeWorkstationOverclock.forCoolant(true, true, tier));
            assertEquals(1, LargeWorkstationOverclock.forCoolant(true, true, tier).energyMultiplier());
        }
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
