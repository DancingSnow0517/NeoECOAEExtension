package cn.dancingsnow.neoecoae.network;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuStreamAssemblerTest {
    @Test void largeSnapshotIsInvisibleUntilCompleteAndHasNoLostBytes() {
        byte[] original = new byte[200_003];
        new Random(12).nextBytes(original);
        var receiver = new MenuStreamAssembler();
        for (int offset = 0; offset < original.length; offset += 16_384) {
            int end = Math.min(original.length, offset + 16_384);
            byte[] result = receiver.accept(7, original.length, offset, Arrays.copyOfRange(original, offset, end));
            if (end == original.length) assertArrayEquals(original, result);
            else assertNull(result);
        }
    }

    @Test void replacementDiscardsIncompleteOldSnapshot() {
        var receiver = new MenuStreamAssembler();
        assertNull(receiver.accept(1, 100, 0, new byte[20]));
        assertArrayEquals(new byte[]{4, 5}, receiver.accept(2, 2, 0, new byte[]{4, 5}));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(1, 100, 20, new byte[20]));
    }

    @Test void invalidLengthsOffsetsAndMissingFirstChunkAreRejectedBeforeAllocation() {
        var receiver = new MenuStreamAssembler();
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(1, Integer.MAX_VALUE, 0, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(1, 5, -1, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(1, 5, 4, new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(1, 5, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(1, 5, 1, new byte[1]));
    }
}
