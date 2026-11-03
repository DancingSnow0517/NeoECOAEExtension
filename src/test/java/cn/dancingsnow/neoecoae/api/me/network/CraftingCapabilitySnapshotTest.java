package cn.dancingsnow.neoecoae.api.me.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftingCapabilitySnapshotTest {
    private static CraftingCapabilitySnapshot snapshot(int normalHosts, int highHosts, long ft, boolean overclocked) {
        return CraftingCapabilitySnapshot.calculate(new CraftingCapabilitySnapshot.Input(
            11, 0, normalHosts, highHosts, CraftingCapabilitySnapshot.F9_OVERCLOCKED_BATCH_PER_FX, ft, 0,
            overclocked, false, 1, false, new CraftingCapabilitySnapshot.CoolantState(false, 0, 0, -1)));
    }

    @Test void overflowIsMeasuredAgainstTheCurrentBatchLimit() {
        var standalone = snapshot(0, 0, 14_080L, true);
        assertEquals(5_632L, standalone.parallelLimit());
        assertEquals(14_080L, standalone.ftParallelCapacity());
        assertEquals(8_448L, standalone.overflowCapacity());
        assertEquals(CraftingCapabilitySnapshot.MAX_OVERCLOCK, standalone.theoreticalOverclock());
    }

    @Test void baseBatchLimitAppliesWithoutOverclock() {
        var base = snapshot(0, 0, 704L, false);
        assertEquals(352L, base.parallelLimit());
        assertEquals(352L, base.overflowCapacity());
        assertEquals(0, base.effectiveOverclock());
        assertEquals(0, base.executionOverclock());
    }

    @Test void exchangeMultiplierScalesBothValues() {
        var exchange = snapshot(1, 1, 14_080L, true);
        assertTrue(exchange.exchangeNetwork());
        assertEquals(10, exchange.networkMultiplier());
        assertEquals(140_800L, exchange.ftParallelCapacity());
        assertEquals(56_320L, exchange.parallelLimit());
        assertEquals(84_480L, exchange.overflowCapacity());
    }

    @Test void exchangeNetworkAlwaysCraftsInOneTick() {
        var exchange = snapshot(2, 0, 0L, false);
        assertTrue(exchange.exchangeNetwork());
        assertEquals(0, exchange.theoreticalOverclock());
        assertEquals(0, exchange.effectiveOverclock());
        assertEquals(CraftingCapabilitySnapshot.MAX_OVERCLOCK, exchange.executionOverclock());
        assertFalse(snapshot(0, 0, 0L, false).exchangeNetwork());
    }
}
