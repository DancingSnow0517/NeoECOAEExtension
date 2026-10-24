package cn.dancingsnow.neoecoae.blocks.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LargeWorkstationProcessingTest {
    @Test void fractionalFeIsCarriedAndTheCompleteBatchPaysExactlyOnce() {
        long paid = 0, remainder = 0;
        for (int tick = 0; tick < 7; tick++) {
            var payment = LargeWorkstationProcessing.fluxPayment(11, 3, 7, remainder);
            paid += payment.amount();
            remainder = payment.remainder();
        }
        assertEquals(33, paid);
        assertEquals(0, remainder);
    }

    @Test void longAmountsDoNotLosePrecisionOrOverflowDuringMultiplication() {
        var payment = LargeWorkstationProcessing.fluxPayment(Long.MAX_VALUE, 200, 200, 0);
        assertEquals(Long.MAX_VALUE, payment.amount());
        assertEquals(0, payment.remainder());
        assertThrows(ArithmeticException.class, () -> LargeWorkstationProcessing.fluxPayment(Long.MAX_VALUE, 201, 200, 0));
    }

    @Test void interruptedAndRebatchedWorkKeepsTheSameFeeAsAnUninterruptedBatch() {
        var first = LargeWorkstationProcessing.fluxPayment(101, 17, 100, 0);
        var second = LargeWorkstationProcessing.fluxPayment(101, 983, 100, first.remainder());
        assertEquals(1010, first.amount() + second.amount());
        assertEquals(0, second.remainder());
    }

    @Test void patternMultiplierConsumesProportionalTimeAndRejectsOverflow() {
        assertEquals(400, LargeWorkstationProcessing.duration(100, 4));
        assertThrows(ArithmeticException.class, () -> LargeWorkstationProcessing.duration(200, Long.MAX_VALUE));
    }
}
