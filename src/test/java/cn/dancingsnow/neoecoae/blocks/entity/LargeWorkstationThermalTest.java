package cn.dancingsnow.neoecoae.blocks.entity;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LargeWorkstationThermalTest {
    private static LargeWorkstationThermal.State at(double heat) {
        return new LargeWorkstationThermal.State(heat, false, 0);
    }

    @Test
    void increasingTemperatureAcceleratesHeatingAndActualWorkScalesIt() {
        assertEquals(0.232, LargeWorkstationThermal.heatingPerSecond(20), 1e-12);
        assertEquals(0.4, LargeWorkstationThermal.heatingPerSecond(50), 1e-12);
        assertEquals(0.712, LargeWorkstationThermal.heatingPerSecond(80), 1e-12);
        assertEquals(0.2, LargeWorkstationThermal.heatingPerSecond(0), 1e-12);
        assertEquals(1, LargeWorkstationThermal.heatingPerSecond(100), 1e-12);
        assertEquals(80 + 0.712 / 40, LargeWorkstationThermal.process(at(80), 0.5).temperature(), 1e-12);
        assertEquals(at(80), LargeWorkstationThermal.process(at(80), 0));
    }

    @Test
    void advancedCoolantReallyLowersLowTemperatureAndRemovesParallelEligibility() {
        var low = LargeWorkstationThermal.process(at(20), 1);
        low = LargeWorkstationThermal.tick(low, 0, 9, true);
        assertEquals(20 + (0.232 - 0.6) / 20, low.temperature(), 1e-12);
        assertFalse(LargeWorkstationOverclock.forCoolingTier(2).isTemperatureReady(low.temperature()));
        var middle = LargeWorkstationThermal.tick(LargeWorkstationThermal.process(at(50), 1), 0, 9, true);
        assertTrue(middle.temperature() < 50);
        var high = LargeWorkstationThermal.tick(LargeWorkstationThermal.process(at(80), 1), 0, 9, true);
        assertTrue(high.temperature() > 80);
    }

    @Test
    void pausedMachinesStillCoolWithoutAutomaticallyReheating() {
        var state = at(20);
        for (int i = 0; i < 20; i++) state = LargeWorkstationThermal.tick(state, 0, 9, true);
        assertEquals(19.4, state.temperature(), 1e-10);
        assertEquals(at(0), LargeWorkstationThermal.tick(at(0), 0, 9, true));
        assertEquals(20.05, LargeWorkstationThermal.tick(at(20), 0.5, 0, false).temperature(), 1e-12);
    }

    @Test
    void reachingOneHundredLatchesProtectionAndStopsFurtherHeating() {
        var locked = LargeWorkstationThermal.process(at(99.999), 1);
        assertTrue(locked.overheated());
        assertEquals(100, locked.temperature());
        assertEquals(locked, LargeWorkstationThermal.process(locked, 1));
        assertEquals(locked, LargeWorkstationThermal.tick(locked, 1, 0, false));
    }

    @Test
    void onlyThreeHundredSuccessfulConsecutiveCryotheumTicksUnlockProtection() {
        var locked = at(100);
        for (int i = 0; i < 299; i++) {
            locked = LargeWorkstationThermal.tick(locked, 1, 9, true);
            assertTrue(locked.overheated());
        }
        assertEquals(299, locked.recoveryTicks());
        assertEquals(at(0), LargeWorkstationThermal.tick(locked, 0, 9, true));
    }

    @Test
    void wrongCoolantOrInterruptedDeliveryResetsRecoveryWithoutNaturalUnlock() {
        var locked = at(100);
        for (int i = 0; i < 100; i++) locked = LargeWorkstationThermal.tick(locked, 0, 9, true);
        assertEquals(at(100), LargeWorkstationThermal.tick(locked, 0, 9, false));
        assertEquals(at(100), LargeWorkstationThermal.tick(locked, 0, 6, true));
        for (int i = 0; i < 1000; i++) locked = LargeWorkstationThermal.tick(locked, 0, 2, true);
        assertTrue(locked.overheated());
        assertEquals(0, locked.recoveryTicks());
    }

    @Test
    void savedProtectionCannotBeBypassedByReloadingOrAStoredLowTemperature() {
        var tag = new CompoundTag();
        LargeWorkstationThermal.save(at(42.125), tag);
        assertEquals(at(42.125), LargeWorkstationThermal.load(tag));
        var locked = at(100);
        for (int i = 0; i < 299; i++) locked = LargeWorkstationThermal.tick(locked, 0, 9, true);
        LargeWorkstationThermal.save(locked, tag);
        assertEquals(at(100), LargeWorkstationThermal.load(tag), "Reload interrupts continuous delivery");
        tag.putDouble("temperature", 0);
        assertTrue(LargeWorkstationThermal.load(tag).overheated());
        tag.putDouble("temperature", Double.NaN);
        assertTrue(LargeWorkstationThermal.load(tag).overheated());
    }
}
