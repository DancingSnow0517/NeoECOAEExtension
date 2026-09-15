package cn.dancingsnow.neoecoae.api.me.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import org.junit.jupiter.api.Test;

class CraftingCapabilitySnapshotTest {
    private CraftingCapabilitySnapshot snapshot(int normal, int high, boolean cooling, boolean virtual) {
        return CraftingCapabilitySnapshot.calculate(new CraftingCapabilitySnapshot.Input(
                22,
                22,
                normal,
                high,
                512,
                14080,
                22,
                false,
                cooling,
                16,
                virtual,
                new CraftingCapabilitySnapshot.CoolantState(cooling, 0, 0, -1)));
    }

    @Test
    void twoHighEnergyHostsProvide8192CraftsPerPhysicalFxWithoutCooling() {
        var state = snapshot(0, 2, false, false);
        assertEquals(16, state.networkMultiplier());
        assertEquals(8192, state.batchPerFx().finiteValue());
        assertEquals(180224, state.totalBatchCapacity().finiteValue());
        assertEquals(22, state.activeFxCount());
        assertEquals(22, state.runningBatchCount());
        assertEquals(18022400, state.energyUsage());
    }

    @Test
    void mixedSwitchContributionsAddAndCoolingDoesNotChangeCapacity() {
        assertEquals(5120, snapshot(1, 1, false, false).batchPerFx().finiteValue());
        assertEquals(
                snapshot(1, 1, false, false).batchPerFx(),
                snapshot(1, 1, true, false).batchPerFx());
        assertEquals(2048, snapshot(2, 0, false, false).batchPerFx().finiteValue());
        assertEquals(32, snapshot(0, 1, false, false).batchPerFx().finiteValue());
    }

    @Test
    void fullVirtualTopologyNeedsEightFullF9HostsButNoCooling() {
        var full = new CraftingCapabilitySnapshot.VirtualHost(true, true, 11, 11);
        assertTrue(CraftingCapabilitySnapshot.isVirtualTopologyEligible(Collections.nCopies(8, full)));
        assertFalse(CraftingCapabilitySnapshot.isVirtualTopologyEligible(Collections.nCopies(7, full)));
        assertFalse(CraftingCapabilitySnapshot.isVirtualTopologyEligible(
                Collections.nCopies(8, new CraftingCapabilitySnapshot.VirtualHost(true, true, 10, 11))));
        var state = snapshot(0, 8, false, true);
        assertTrue(state.batchPerFx().unlimited());
        assertEquals(100, state.energyUsage());
    }

    @Test
    void exchangeDoesNotMultiplyFtOrChangeItsOverclockCalculation() {
        var state = snapshot(0, 2, false, false);
        assertEquals(14080, state.ftParallelCapacity());
        assertEquals(13376, state.overflowCapacity());
        assertEquals(9, state.theoreticalOverclock());
        assertEquals(0, state.effectiveOverclock());
    }
}
