package cn.dancingsnow.neoecoae.api.me.network;

import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostPanelUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class CraftingCapabilitySnapshotTest {
    @Test
    void screenshotUsesActualCapacityAndRetainsOneTickExecution() {
        var snapshot = calculate(11, 0, 0, 512, 14_080, true, false, false, 9);

        assertEquals(5_632, snapshot.totalBatchCapacity().finiteValue());
        assertEquals(14_080, snapshot.ftParallelCapacity());
        assertEquals(8_448, snapshot.overflowCapacity());
        assertEquals(9, snapshot.theoreticalOverclock());
        assertEquals(1, CraftingHostPanelUI.formatRecipeTimeTicks(snapshot.effectiveOverclock()));
    }

    @ParameterizedTest
    @CsvSource({"128,112,0,0,10", "256,336,80,5,2", "512,1280,768,9,1"})
    void standaloneTiersUseActualOverclockedCapacity(
            long batchPerFx, long rawFt, long overflow, int overclock, int ticks) {
        var snapshot = calculate(1, 0, 0, batchPerFx, rawFt, true, false, false, 9);

        assertEquals(batchPerFx, snapshot.totalBatchCapacity().finiteValue());
        assertEquals(rawFt, snapshot.ftParallelCapacity());
        assertEquals(overflow, snapshot.overflowCapacity());
        assertEquals(overclock, snapshot.theoreticalOverclock());
        assertEquals(overclock, snapshot.effectiveOverclock());
        assertEquals(ticks, CraftingHostPanelUI.formatRecipeTimeTicks(snapshot.effectiveOverclock()));
    }

    @ParameterizedTest
    @CsvSource({
        "2,0,112640,45056,67584",
        "1,1,281600,112640,168960",
        "0,2,450560,180224,270336",
        "4,0,450560,180224,270336",
        "0,4,1802240,720896,1081344",
        "8,0,1802240,720896,1081344",
        "0,8,7208960,2883584,4325376"
    })
    void finiteNetworksScaleParallelAndBatchCapacityByTheSameMultiplier(
            int normal, int highEnergy, long ft, long capacity, long overflow) {
        int hosts = normal + highEnergy;
        var snapshot = calculate(11 * hosts, normal, highEnergy, 512,
            14_080L * hosts, true, false, false, 9);

        assertEquals(normal * 2 + highEnergy * 8, snapshot.networkMultiplier());
        assertEquals(ft, snapshot.ftParallelCapacity());
        assertEquals(capacity, snapshot.totalBatchCapacity().finiteValue());
        assertEquals(overflow, snapshot.overflowCapacity());
        assertEquals(0.6D, (double) overflow / ft, 1.0E-12D);
        assertEquals(9, snapshot.effectiveOverclock());
        assertEquals(1, CraftingHostPanelUI.formatRecipeTimeTicks(snapshot.effectiveOverclock()));
    }

    @ParameterizedTest
    @CsvSource({"1,0", "0,1"})
    void oneSwitchDoesNotActivateAnExchangeMultiplier(int normal, int highEnergy) {
        var snapshot = calculate(11, normal, highEnergy, 512, 14_080,
            true, false, false, 9);

        assertEquals(14_080, snapshot.ftParallelCapacity());
        assertEquals(5_632, snapshot.totalBatchCapacity().finiteValue());
        assertEquals(8_448, snapshot.overflowCapacity());
    }

    @ParameterizedTest
    @CsvSource({"-1,0,10", "2,2,4", "6,6,2", "9,9,1"})
    void coolingStillCapsEffectiveOverclock(int coolantLimit, int effective, int ticks) {
        var snapshot = calculate(22, 0, 2, 512, 28_160,
            true, true, false, coolantLimit);

        assertEquals(9, snapshot.theoreticalOverclock());
        assertEquals(effective, snapshot.effectiveOverclock());
        assertEquals(ticks, CraftingHostPanelUI.formatRecipeTimeTicks(snapshot.effectiveOverclock()));
    }

    @Test
    void disablingOverclockRestoresStandaloneBaseCapacityAndTenTickExecution() {
        var snapshot = calculate(11, 0, 0, 512, 5_632, false, false, false, 9);

        assertEquals(352, snapshot.totalBatchCapacity().finiteValue());
        assertEquals(5_280, snapshot.overflowCapacity());
        assertEquals(0, snapshot.effectiveOverclock());
        assertEquals(10, CraftingHostPanelUI.formatRecipeTimeTicks(snapshot.effectiveOverclock()));
    }

    @Test
    void exchangeScalingDoesNotEnableOverclockWhenTheSwitchIsOff() {
        var snapshot = calculate(22, 0, 2, 512, 11_264, false, false, false, 9);

        assertEquals(180_224, snapshot.ftParallelCapacity());
        assertEquals(180_224, snapshot.totalBatchCapacity().finiteValue());
        assertEquals(0, snapshot.overflowCapacity());
        assertEquals(0, snapshot.theoreticalOverclock());
        assertEquals(0, snapshot.effectiveOverclock());
    }

    @Test
    void unlimitedVirtualCapacityDoesNotEnterTheOrdinaryOverflowFormula() {
        var snapshot = calculate(88, 0, 8, 512, 112_640, true, true, true, 9);

        assertTrue(snapshot.virtualMode());
        assertTrue(snapshot.batchPerFx().unlimited());
        assertTrue(snapshot.totalBatchCapacity().unlimited());
        assertEquals(0, snapshot.overflowCapacity());
        assertEquals(0, snapshot.theoreticalOverclock());
        assertEquals(10_000_000, snapshot.energyUsage());
    }

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    void missingParallelCapacityCannotProduceOverflowOrOverclock(long rawFt) {
        var snapshot = calculate(22, 0, 2, 512, rawFt, true, false, false, 9);

        assertEquals(0, snapshot.ftParallelCapacity());
        assertEquals(0, snapshot.overflowCapacity());
        assertEquals(0, snapshot.effectiveOverclock());
    }

    @Test
    void multiplyingParallelCapacitySaturatesInsteadOfWrappingNegative() {
        var snapshot = calculate(22, 0, 2, 512, Long.MAX_VALUE,
            true, false, false, 9);

        assertEquals(Long.MAX_VALUE, snapshot.ftParallelCapacity());
        assertEquals(Long.MAX_VALUE - 180_224, snapshot.overflowCapacity());
        assertEquals(9, snapshot.theoreticalOverclock());
    }

    private static CraftingCapabilitySnapshot calculate(
            int fx, int normal, int highEnergy, long standaloneBatchPerFx, long rawFt,
            boolean overclocked, boolean cooling, boolean virtualEligible, int coolantLimit) {
        return CraftingCapabilitySnapshot.calculate(new CraftingCapabilitySnapshot.Input(
            fx, 0, normal, highEnergy, standaloneBatchPerFx, rawFt, 0,
            overclocked, cooling, 16, virtualEligible,
            new CraftingCapabilitySnapshot.CoolantState(cooling, 1_000_000, 1_000_000, coolantLimit)));
    }
}
