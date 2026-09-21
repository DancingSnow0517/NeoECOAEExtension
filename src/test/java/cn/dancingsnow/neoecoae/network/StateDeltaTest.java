package cn.dancingsnow.neoecoae.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StateDeltaTest {
    @Test
    void changingEnergyDoesNotResendMatrixOrNbtBytes() {
        byte[] state = new byte[128 * 1024];
        new Random(41).nextBytes(state);
        byte[] next = state.clone();
        Arrays.fill(next, 0, 8, (byte) 7);
        byte[] delta = StateDelta.encode(state, next);
        assertTrue(delta.length < 128, "One counter must not resend the matrix");
        assertArrayEquals(next, StateDelta.apply(state, delta));
        System.out.println("UI scalar update: " + state.length + " B snapshot -> " + delta.length + " B delta");
    }

    @Test
    void variableLengthFieldsInsertionsDeletionsAndReorderingRoundTrip() {
        Random random = new Random(19);
        byte[] state = new byte[40_000];
        random.nextBytes(state);
        for (int round = 0; round < 100; round++) {
            int start = random.nextInt(state.length);
            int removed = Math.min(32, state.length - start);
            int added = random.nextInt(64);
            byte[] next = new byte[state.length - removed + added];
            System.arraycopy(state, 0, next, 0, start);
            byte[] inserted = new byte[added];
            random.nextBytes(inserted);
            System.arraycopy(inserted, 0, next, start, added);
            System.arraycopy(state, start + removed, next, start + added, state.length - start - removed);
            byte[] delta = StateDelta.encode(state, next);
            assertTrue(delta.length < 512);
            assertArrayEquals(next, StateDelta.apply(state, delta));
            state = next;
        }
        assertArrayEquals(state, StateDelta.apply(new byte[0], StateDelta.encode(new byte[0], state)));
        assertArrayEquals(new byte[0], StateDelta.apply(state, StateDelta.encode(state, new byte[0])));
    }

    @Test
    void malformedCopyCannotReadOutsideThePreviousSnapshot() {
        byte[] bad = BoundedData.encode(100, out -> {
            out.writeVarInt(10);
            out.writeBoolean(true);
            out.writeVarInt(10);
            out.writeVarInt(1);
        });
        assertThrows(IllegalArgumentException.class, () -> StateDelta.apply(new byte[10], bad));
    }
}
