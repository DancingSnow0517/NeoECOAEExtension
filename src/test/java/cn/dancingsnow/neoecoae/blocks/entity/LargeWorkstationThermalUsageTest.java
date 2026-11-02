package cn.dancingsnow.neoecoae.blocks.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LargeWorkstationThermalUsageTest {
    @Test
    void allSourcesAndNetTemperatureUseTheSameActualOneSecondWindow() {
        var usage = new LargeWorkstationThermalUsage();
        var before = new LargeWorkstationThermal.State(80, false, 0);
        var step = LargeWorkstationThermal.step(before, 0.5, 0, 9, true);
        for (int tick = 1; tick <= 20; tick++) usage.record(tick, before, step);
        var rates = usage.rates(20);
        assertEquals(0.392, rates.processing(), 1e-12);
        assertEquals(0, rates.preheat());
        assertEquals(0.6, rates.cooling(), 1e-12);
        assertEquals(0, rates.naturalCooling());
        assertEquals(-0.208, rates.net(), 1e-10);
        assertEquals(rates.net(), rates.processing() - rates.cooling(), 1e-10);
        assertEquals(LargeWorkstationThermalUsage.Rates.ZERO, usage.rates(40));
    }

    @Test
    void idleAndUnpoweredTicksDoNotReportTheoreticalProcessingOrPreheat() {
        var usage = new LargeWorkstationThermalUsage();
        var before = new LargeWorkstationThermal.State(50, false, 0);
        for (int tick = 1; tick <= 20; tick++) {
            usage.record(tick, before, LargeWorkstationThermal.step(before, 0, 0, 0, false));
        }
        var rates = usage.rates(20);
        assertEquals(0, rates.processing());
        assertEquals(0, rates.preheat());
        assertEquals(0, rates.cooling());
        assertEquals(0.1, rates.naturalCooling(), 1e-12);
        assertEquals(-0.1, rates.net(), 1e-10);
        usage.clear();
        assertEquals(LargeWorkstationThermalUsage.Rates.ZERO, usage.rates(20));
    }

    @Test
    void coolingAtZeroIsClampedToTheHeatActuallyRemoved() {
        var before = new LargeWorkstationThermal.State(0.001, false, 0);
        var step = LargeWorkstationThermal.step(before, 0, 0, 9, true);
        assertEquals(0, step.state().temperature());
        assertEquals(0.001, step.cooling(), 1e-12);
        assertEquals(0, step.naturalCooling());
    }

    @Test
    void repeatedSamplesForTheSameServerTickAreNotDoubleCountedAndFutureTicksAreIgnored() {
        var usage = new LargeWorkstationThermalUsage();
        var before = new LargeWorkstationThermal.State(0, false, 0);
        var step = LargeWorkstationThermal.step(before, 0, 1, 0, false);
        usage.record(12, before, step);
        usage.record(12, before, step);
        assertEquals(0.4, usage.rates(12).preheat(), 1e-12);
        assertEquals(0.4, usage.rates(12).net(), 1e-12);
        assertEquals(LargeWorkstationThermalUsage.Rates.ZERO, usage.rates(11));
        assertEquals(LargeWorkstationThermalUsage.Rates.ZERO, usage.rates(32));
    }
}
