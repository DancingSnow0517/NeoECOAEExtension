package cn.dancingsnow.neoecoae.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NEConfigTest {
    @Test
    void capacityEntryPointsStayAtDefaultMultipliers() {
        assertEquals(NEConfig.CRAFTING_WORKER_BASE_CRAFTS, NEConfig.getCraftingWorkerBaseCrafts());
        assertEquals(576, NEConfig.getCraftingParallelCoreCount(576));
        assertEquals(144, NEConfig.getComputationParallelCoreCount(144));
    }

    @Test
    void multipliesBaseValueByConfiguredPowerOfTwo() {
        assertEquals(32, NEConfig.multiplyByPowerOfTwo(32, 0));
        assertEquals(64, NEConfig.multiplyByPowerOfTwo(64, 0));
        assertEquals(128, NEConfig.multiplyByPowerOfTwo(32, 2));
        assertEquals(256, NEConfig.multiplyByPowerOfTwo(64, 2));
        assertEquals(2_097_152, NEConfig.multiplyByPowerOfTwo(32, 16));
        assertEquals(37_748_736, NEConfig.multiplyByPowerOfTwo(576, 16));
    }

    @Test
    void clampsPowerAndSaturatesOverflow() {
        assertEquals(32, NEConfig.multiplyByPowerOfTwo(32, -1));
        assertEquals(2_097_152, NEConfig.multiplyByPowerOfTwo(32, 20));
        assertEquals(Integer.MAX_VALUE, NEConfig.multiplyByPowerOfTwo(Integer.MAX_VALUE, 16));
    }
}
