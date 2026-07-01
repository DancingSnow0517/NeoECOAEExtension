package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.dancingsnow.neoecoae.config.NEConfig;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class ECOCraftingCPULogicTest {
    @Test
    void batchBudgetTracksTheWholeTick() {
        var budget = new ECOCraftingCPULogic.FastPathBatchBudget(5632);

        budget.consume(512);
        budget.consume(4608);

        assertEquals(512, budget.remaining());
        assertThrows(IllegalArgumentException.class, () -> budget.consume(513));
        assertEquals(512, budget.remaining());
    }

    @Test
    void batchBudgetIsIndependentFromSlowOperationBudget() {
        assertEquals(64, NEConfig.ecoBatchFastPathLimit);
        assertEquals(256, NEConfig.ecoBatchFastPathTickLimit);
        assertEquals(256, ECOCraftingCPULogic.totalPatternBudget(64, 256));
    }

    @Test
    void remainingJobOutputAmountUsesRemainingAmount() throws Exception {
        ECOCraftingCPULogic logic = new ECOCraftingCPULogic(null);

        assertEquals(0L, logic.getRemainingJobOutputAmount());

        setJob(logic, jobWithRemainingAmount(42L));
        assertEquals(42L, logic.getRemainingJobOutputAmount());

        setJob(logic, jobWithRemainingAmount(-7L));
        assertEquals(0L, logic.getRemainingJobOutputAmount());
    }

    private static ExecutingCraftingJob jobWithRemainingAmount(long remainingAmount) throws Exception {
        ExecutingCraftingJob job = (ExecutingCraftingJob) unsafe().allocateInstance(ExecutingCraftingJob.class);
        job.remainingAmount = remainingAmount;
        return job;
    }

    private static void setJob(ECOCraftingCPULogic logic, ExecutingCraftingJob job) throws Exception {
        Field field = ECOCraftingCPULogic.class.getDeclaredField("job");
        field.setAccessible(true);
        field.set(logic, job);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
