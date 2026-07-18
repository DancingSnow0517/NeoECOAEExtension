package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingCompatibilityTest {
    @Test
    void finishJobKeepsAddonMixinDescriptor() throws NoSuchMethodException {
        var method = ECOCraftingCPULogic.class.getDeclaredMethod("finishJob", boolean.class);

        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void batchRequestIsDrivenByTaskAndCraftingHostInsteadOfCpuTickBudget() {
        assertEquals(512, ECOCraftingCPULogic.calculateBatchRequestSize(512L));
        assertEquals(80, ECOCraftingCPULogic.calculateBatchRequestSize(80L));
        assertEquals(10_000_000, ECOCraftingCPULogic.calculateBatchRequestSize(10_000_000L));
        assertEquals(Integer.MAX_VALUE, ECOCraftingCPULogic.calculateBatchRequestSize(Long.MAX_VALUE));
        assertEquals(0, ECOCraftingCPULogic.calculateBatchRequestSize(-1L));
    }

    @Test
    void operationLimitSaturatesWhenCoProcessorCountIsMaxInt() {
        assertEquals(
            Integer.MAX_VALUE,
            ECOCraftingCPULogic.calculateOperationLimit(Integer.MAX_VALUE, Integer.MAX_VALUE)
        );
        assertEquals(64, ECOCraftingCPULogic.calculateOperationLimit(Integer.MAX_VALUE, 64));
    }
}
