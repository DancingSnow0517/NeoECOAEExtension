package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ECOCraftingThreadPowerTest {
    @Test
    void largeBatchRequestsPowerForEveryOccupiedThreadSlot() {
        double powerPerProgress = ECOCraftingThread.calculatePowerPerProgress(2_048.0D, 65_536);

        assertEquals(134_217_728.0D, powerPerProgress);
        assertEquals(100, ECOCraftingThread.calculatePoweredProgress(
            powerPerProgress * 100.0D,
            powerPerProgress,
            100
        ));
    }

    @Test
    void partialPowerOnlyAdvancesFundedProgress() {
        assertEquals(3, ECOCraftingThread.calculatePoweredProgress(30.0D, 10.0D, 100));
        assertEquals(0, ECOCraftingThread.calculatePoweredProgress(9.0D, 10.0D, 100));
    }

    @Test
    void requestedProgressIsBoundedByRemainingWork() {
        assertEquals(50, ECOCraftingThread.calculateRequestedProgress(20, 100, 50));
    }

    @Test
    void partialPowerAcrossTicksEventuallyCompletesLargeBatch() {
        double powerPerProgress = ECOCraftingThread.calculatePowerPerProgress(16.0D, 10_000_000);
        ECOCraftingThread.PowerProgress state = new ECOCraftingThread.PowerProgress(0, 0.0D);
        int totalProgress = 0;

        for (int tick = 0; tick < 400 && totalProgress < ECOCraftingThread.MAX_PROGRESS; tick++) {
            state = ECOCraftingThread.accumulatePoweredProgress(
                50_000_000.0D,
                powerPerProgress,
                ECOCraftingThread.MAX_PROGRESS - totalProgress,
                state.remainder()
            );
            totalProgress += state.completed();
        }

        assertEquals(ECOCraftingThread.MAX_PROGRESS, totalProgress);
        assertTrue(state.remainder() >= 0.0D && state.remainder() < 1.0D);
    }

    @Test
    void overclockReducesFullyPoweredCompletionTicks() {
        int normalTicks = fullyPoweredTicksToComplete(0, 10_000_000);
        int overclockedTicks = fullyPoweredTicksToComplete(4, 10_000_000);

        assertEquals(10, normalTicks);
        assertEquals(2, overclockedTicks);
        assertTrue(overclockedTicks < normalTicks);
    }

    @Test
    void tenMillionCraftLifecycleClearsFinalOutputWithoutATail() {
        long requestedOutput = 10_000_000L;
        int batchSize = ECOCraftingCPULogic.calculateBatchRequestSize(requestedOutput);
        assertEquals(requestedOutput, batchSize);

        int ticks = fullyPoweredTicksToComplete(4, batchSize);
        assertEquals(2, ticks);

        ECOFinalOutputBuffer output = new ECOFinalOutputBuffer();
        assertEquals(batchSize, output.accept(batchSize, Actionable.MODULATE));

        long delivered = Math.min(requestedOutput, output.amount());
        output.removeDelivered(delivered);
        long remainingOutput = requestedOutput - delivered;

        assertEquals(0L, remainingOutput);
        assertEquals(0L, output.amount());
        assertTrue(ECOCraftingCPULogic.isFinalOutputSatisfied(remainingOutput));
    }

    @Test
    void partialProgressSurvivesNbtAndInvalidValuesAreRejected() {
        CompoundTag tag = new CompoundTag();
        ECOCraftingThread.writeProgressRemainder(tag, 0.625D);

        assertEquals(0.625D, ECOCraftingThread.readProgressRemainder(tag));

        tag.putDouble("progressRemainder", Double.NaN);
        assertEquals(0.0D, ECOCraftingThread.readProgressRemainder(tag));
        tag.putDouble("progressRemainder", -0.1D);
        assertEquals(0.0D, ECOCraftingThread.readProgressRemainder(tag));
        tag.putDouble("progressRemainder", 1.0D);
        assertEquals(0.0D, ECOCraftingThread.readProgressRemainder(tag));
    }

    private static int fullyPoweredTicksToComplete(int overclockTimes, int occupiedThreadSlots) {
        int progress = 0;
        int ticks = 0;
        double remainder = 0.0D;
        double powerPerProgress = ECOCraftingThread.calculatePowerPerProgress(1.0D, occupiedThreadSlots);

        while (progress < ECOCraftingThread.MAX_PROGRESS) {
            int requestedProgress = ECOCraftingThread.calculateRequestedProgress(
                1,
                ECOCraftingThread.calculateProgressPerTick(overclockTimes),
                ECOCraftingThread.MAX_PROGRESS - progress
            );
            ECOCraftingThread.PowerProgress powered = ECOCraftingThread.accumulatePoweredProgress(
                requestedProgress * powerPerProgress,
                powerPerProgress,
                requestedProgress,
                remainder
            );
            progress += powered.completed();
            remainder = powered.remainder();
            ticks++;
        }
        return ticks;
    }
}
