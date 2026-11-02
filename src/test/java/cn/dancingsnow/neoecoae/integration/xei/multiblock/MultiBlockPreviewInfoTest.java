package cn.dancingsnow.neoecoae.integration.xei.multiblock;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.network.CraftingCapabilitySnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultiBlockPreviewInfoTest {
    @Test void f9UsesPerWorkerBatchAndActualExecutionTiming() {
        var single = MultiBlockPreviewInfo.craftingCapacity(ECOTier.L9, 1, 2 * (256 + 384));
        var full = MultiBlockPreviewInfo.craftingCapacity(ECOTier.L9, 11, 22 * (256 + 384));
        assertEquals(512, single.batchPerFx().finiteValue());
        assertEquals(512, single.totalBatchCapacity().finiteValue());
        assertEquals(5632, full.totalBatchCapacity().finiteValue());
        assertEquals(1, MultiBlockPreviewInfo.craftingTicks(full));
        assertFalse(full.virtualMode());
        assertEquals(0, full.networkMultiplier());
    }

    @Test void lowFtCannotAdvertiseOneTickCrafting() {
        var capacity = MultiBlockPreviewInfo.craftingCapacity(ECOTier.L9, 11, 0);
        assertEquals(0, capacity.effectiveOverclock());
        assertEquals(10, MultiBlockPreviewInfo.craftingTicks(capacity));
    }

    @Test void computationCapacityAndThreadsFollowThePreviewLength() {
        var one = MultiBlockPreviewInfo.computation(ECOTier.L9, 1152, 2, 64);
        var full = MultiBlockPreviewInfo.computation(ECOTier.L9, 12672, 22, 704);
        assertEquals("1,152", one.get(0).value().getString());
        assertEquals("2 GB", one.get(1).value().getString());
        assertEquals("12,672", full.get(0).value().getString());
        assertEquals("22 GB", full.get(1).value().getString());
        assertEquals("704", full.get(2).value().getString());
    }
}
