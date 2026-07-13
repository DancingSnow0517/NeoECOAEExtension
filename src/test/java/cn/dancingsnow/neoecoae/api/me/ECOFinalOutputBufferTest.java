package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import appeng.api.config.Actionable;
import org.junit.jupiter.api.Test;

class ECOFinalOutputBufferTest {
    @Test
    void simulationDoesNotTakeOwnership() {
        ECOFinalOutputBuffer buffer = new ECOFinalOutputBuffer();

        assertEquals(3L, buffer.accept(3L, Actionable.SIMULATE));
        assertEquals(0L, buffer.amount());
    }

    @Test
    void oneProducedUnitHasExactlyOneBufferedOwner() {
        ECOFinalOutputBuffer buffer = new ECOFinalOutputBuffer();

        assertEquals(1L, buffer.accept(1L, Actionable.MODULATE));
        assertEquals(1L, buffer.amount());
        buffer.removeDelivered(1L);
        assertEquals(0L, buffer.amount());
    }

    @Test
    void partialDeliveryPreservesOnlyTheRemainder() {
        ECOFinalOutputBuffer buffer = new ECOFinalOutputBuffer(3L);

        buffer.removeDelivered(2L);
        assertEquals(1L, buffer.amount());
        assertThrows(IllegalArgumentException.class, () -> buffer.removeDelivered(2L));
    }

    @Test
    void persistedAmountCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new ECOFinalOutputBuffer(-1L));
    }
}
