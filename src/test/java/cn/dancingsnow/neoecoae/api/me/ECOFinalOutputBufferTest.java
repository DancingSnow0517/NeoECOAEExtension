package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
