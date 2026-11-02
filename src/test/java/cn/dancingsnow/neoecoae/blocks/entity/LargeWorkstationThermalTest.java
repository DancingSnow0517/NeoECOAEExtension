package cn.dancingsnow.neoecoae.blocks.entity;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LargeWorkstationThermalTest {
    private static LargeWorkstationThermal.State at(double heat) {
        return new LargeWorkstationThermal.State(heat, false, 0);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,2", "0,6", "0,9", "20,2", "50,6", "80,2", "80,9", "99,9"})
    void paidRegulationHoldsAnyTargetAgainstIdleCoolingAndProcessingHeat(int target, int tier) {
        var state = at(target);
        for (int i = 0; i < 2000; i++) {
            double load = switch (i % 4) { case 0 -> 0; case 1 -> 0.5; case 2 -> 1; default -> 4; };
            var plan = LargeWorkstationThermal.regulate(state, load, target, tier, 300);
            assertTrue(plan.coolantAmount() >= 300);
            assertEquals(target, plan.result().state().temperature());
            assertFalse(plan.result().state().overheated());
            double accounted = state.temperature() + plan.result().processingHeat() + plan.result().preheatHeat()
                - plan.result().cooling() - plan.result().naturalCooling();
            assertEquals(plan.result().state().temperature(), accounted, 1e-10);
            state = plan.result().state();
        }
    }

    @Test
    void regulationApproachesChangedTargetsWithoutOvershootingOrBypassingRecovery() {
        var state = at(0);
        for (int i = 0; i < 2000; i++) {
            state = LargeWorkstationThermal.regulate(state, 0, 80, 9, 300).result().state();
            assertTrue(state.temperature() <= 80);
        }
        assertEquals(at(80), state);
        for (int i = 0; i < 2000; i++) {
            state = LargeWorkstationThermal.regulate(state, 1, 0, 9, 300).result().state();
            assertTrue(state.temperature() >= 0);
        }
        assertEquals(at(0), state);
        var locked = LargeWorkstationThermal.regulate(at(100), 0, 80, 6, 300).result().state();
        assertTrue(locked.overheated());
        assertEquals(0, locked.recoveryTicks());
        for (int i = 0; i < 299; i++) {
            locked = LargeWorkstationThermal.regulate(locked, 0, 80, 9, 300).result().state();
            assertTrue(locked.overheated());
        }
        assertEquals(at(0), LargeWorkstationThermal.regulate(locked, 0, 80, 9, 300).result().state());
    }

    @Test
    void increasingTemperatureSlowsHeatingAndActualWorkScalesIt() {
        assertEquals(2.794, LargeWorkstationThermal.heatingPerSecond(20), 1e-12);
        assertEquals(1.4875, LargeWorkstationThermal.heatingPerSecond(50), 1e-12);
        assertEquals(0.784, LargeWorkstationThermal.heatingPerSecond(80), 1e-12);
        assertEquals(4, LargeWorkstationThermal.heatingPerSecond(0), 1e-12);
        assertEquals(0.65, LargeWorkstationThermal.heatingPerSecond(100), 1e-12);
        assertEquals(80 + 0.784 / 40, LargeWorkstationThermal.process(at(80), 0.5).temperature(), 1e-12);
        assertEquals(at(80), LargeWorkstationThermal.process(at(80), 0));
        for (int i = 0; i < 10_000; i++) {
            assertTrue(LargeWorkstationThermal.heatingPerSecond(i / 100.0)
                > LargeWorkstationThermal.heatingPerSecond((i + 1) / 100.0));
        }
    }

    @Test
    void advancedCoolantReallyLowersLowTemperatureAndRemovesParallelEligibility() {
        var low = LargeWorkstationThermal.tick(at(20), 0.1, 0, 9, true);
        assertEquals(20 + (0.2794 - 0.6) / 20, low.temperature(), 1e-12);
        assertFalse(LargeWorkstationOverclock.forCoolingTier(2).isTemperatureReady(low.temperature()));
        var middle = LargeWorkstationThermal.tick(at(50), 0.1, 0, 9, true);
        assertTrue(middle.temperature() < 50);
        var high = LargeWorkstationThermal.tick(at(80), 1, 0, 9, true);
        assertTrue(high.temperature() > 80);
    }

    @Test
    void pausedMachinesStillCoolWithoutAutomaticallyReheating() {
        var state = at(20);
        for (int i = 0; i < 20; i++) state = LargeWorkstationThermal.tick(state, 0, 9, true);
        assertEquals(19.3, state.temperature(), 1e-10);
        assertEquals(at(0), LargeWorkstationThermal.tick(at(0), 0, 9, true));
        assertEquals(20.1397, LargeWorkstationThermal.tick(at(20), 0.5, 0, false).temperature(), 1e-12);
    }

    @Test
    void idleTemperatureNaturallyFallsWithoutCoolantOrPower() {
        var state = at(20);
        for (int i = 0; i < 20; i++) state = LargeWorkstationThermal.tick(state, 0, 0, false);
        assertEquals(19.9, state.temperature(), 1e-10);
        assertEquals(at(0), LargeWorkstationThermal.tick(at(0.001), 0, 0, false));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "20,2,2.694", "20,6,2.494", "20,9,2.194",
        "50,2,1.3875", "50,6,1.1875", "50,9,0.8875",
        "80,2,0.684", "80,6,0.484", "80,9,0.184"
    })
    void fullLoadNetChangeMatchesTheReferenceTable(double temperature, int tier, double net) {
        assertEquals(temperature + net / 20,
            LargeWorkstationThermal.tick(at(temperature), 1, 0, tier, true).temperature(), 1e-12);
    }

    @Test
    void coolingIsAppliedBeforeCheckingForOverheat() {
        var state = LargeWorkstationThermal.tick(at(99.99), 0.5, 0, 9, true);
        assertFalse(state.overheated());
        assertTrue(state.temperature() < 99.99);
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"20,6", "50,20.8", "80,49.45", "100,78.3"})
    void continuousFullLoadReachesTheDesignedTemperatureInFiniteTime(double target, double seconds) {
        var state = at(0);
        int ticks = 0;
        while (state.temperature() < target && ticks < 2_000) {
            state = LargeWorkstationThermal.tick(state, 1, 0, 0, false);
            ticks++;
        }
        assertEquals(seconds, ticks / 20.0, 0.1);
        assertTrue(state.temperature() >= target);
    }

    @Test
    void cryotheumExtendsTheHighestBandButCannotPreventFullLoadOverheatingForever() {
        var state = at(80);
        int ticks = 0;
        while (!state.overheated() && ticks < 6_000) {
            state = LargeWorkstationThermal.tick(state, 1, 0, 9, true);
            assertTrue(state.temperature() >= 80);
            ticks++;
        }
        assertTrue(state.overheated());
        assertEquals(249.8, ticks / 20.0, 0.1);
        assertTrue(LargeWorkstationThermal.tick(at(80), 0.5, 0, 9, true).temperature() < 80,
            "Half load still permits overcooling and pauses a high-tier batch");
    }

    @Test
    void preheatSlowsWithTemperatureAndScalesWithActuallyPaidPower() {
        assertEquals(8, LargeWorkstationThermal.preheatPerSecond(0), 1e-12);
        assertEquals(2.975, LargeWorkstationThermal.preheatPerSecond(50), 1e-12);
        assertEquals(1.568, LargeWorkstationThermal.preheatPerSecond(80), 1e-12);
        assertEquals(80.0392, LargeWorkstationThermal.tick(at(80), 0.5, 0, false).temperature(), 1e-12);
        assertEquals(79.995, LargeWorkstationThermal.tick(at(80), 0, 0, false).temperature(), 1e-12);
    }
}
