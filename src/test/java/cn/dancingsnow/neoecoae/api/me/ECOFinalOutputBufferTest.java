package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import appeng.api.config.Actionable;
import org.junit.jupiter.api.Test;

class ECOFinalOutputBufferTest {
    @Test
    void simulationDoesNotTransferOwnership() {
        ECOFinalOutputBuffer buffer = new ECOFinalOutputBuffer();

        assertEquals(7L, buffer.accept(7L, Actionable.SIMULATE));
        assertEquals(0L, buffer.amount());
        assertEquals(7L, buffer.accept(7L, Actionable.MODULATE));
        assertEquals(7L, buffer.amount());

        buffer.removeDelivered(3L);
        assertEquals(4L, buffer.amount());
    }

    @Test
    void requestedEightIsFullyDeliveredAfterPartialAcceptanceAndRetry() {
        ECOFinalOutputBuffer buffer = new ECOFinalOutputBuffer();
        assertEquals(8L, buffer.accept(8L, Actionable.MODULATE));

        var firstAttempt = buffer.attemptDelivery(8L, requested -> Math.min(requested, 4L));
        buffer.completeDelivery(firstAttempt);

        assertEquals(4L, firstAttempt.delivered());
        assertEquals(4L, firstAttempt.remainingAmount());
        assertEquals(4L, buffer.amount());

        var retry = buffer.attemptDelivery(firstAttempt.remainingAmount(), requested -> requested);
        buffer.completeDelivery(retry);

        assertEquals(4L, retry.delivered());
        assertEquals(0L, retry.remainingAmount());
        assertEquals(0L, buffer.amount());
    }

    @Test
    void rejectedDeliveryKeepsAllEightBufferedForRetry() {
        ECOFinalOutputBuffer buffer = new ECOFinalOutputBuffer(8L);

        var rejected = buffer.attemptDelivery(8L, requested -> 0L);

        assertEquals(0L, rejected.delivered());
        assertEquals(8L, rejected.remainingAmount());
        assertEquals(8L, buffer.amount());
    }

    @Test
    void invalidTargetResultDoesNotConsumeBufferedOutput() {
        ECOFinalOutputBuffer buffer = new ECOFinalOutputBuffer(8L);

        assertThrows(IllegalStateException.class, () -> buffer.attemptDelivery(8L, requested -> requested + 1L));
        assertEquals(8L, buffer.amount());
    }
}
