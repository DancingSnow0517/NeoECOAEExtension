package cn.dancingsnow.neoecoae.recipe;

import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkstationEnergyTest {
    private double oldAe, oldFe, oldMultiplier;

    @BeforeEach void configure() {
        oldAe = PowerUnits.AE.conversionRatio;
        oldFe = PowerUnits.FE.conversionRatio;
        oldMultiplier = PowerMultiplier.CONFIG.multiplier;
        PowerUnits.AE.conversionRatio = 1;
        PowerUnits.FE.conversionRatio = 0.5;
        PowerMultiplier.CONFIG.multiplier = 1;
    }

    @AfterEach void restore() {
        PowerUnits.AE.conversionRatio = oldAe;
        PowerUnits.FE.conversionRatio = oldFe;
        PowerMultiplier.CONFIG.multiplier = oldMultiplier;
    }

    @Test void fallbackUsesTheConfiguredRateAndDoesNotRoundSmallOrLongBudgetsToZero() {
        assertEquals(0, new BigDecimal("0.5").compareTo(WorkstationEnergy.aeEquivalent(1)));
        assertEquals(0, new BigDecimal("4503599627370496.5").compareTo(WorkstationEnergy.aeEquivalent(9_007_199_254_740_993L)));
        assertEquals(2000, WorkstationEnergy.feEquivalent(1000));
        PowerUnits.FE.conversionRatio = 0.25;
        assertEquals(0, new BigDecimal("250").compareTo(WorkstationEnergy.aeEquivalent(1000)));
    }

    @Test void configuredPowerMultiplierIsAppliedExactlyOnceDuringPayment() {
        PowerMultiplier.CONFIG.multiplier = 2;
        var cost = WorkstationEnergy.aeEquivalent(2000);
        assertEquals(500, cost.doubleValue());
        assertEquals(PowerUnits.FE.convertTo(PowerUnits.AE, 2000), PowerMultiplier.CONFIG.multiply(cost.doubleValue()));
        assertEquals(2000, WorkstationEnergy.feEquivalent(cost.longValueExact()));
    }
}
