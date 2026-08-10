package cn.dancingsnow.neoecoae.gui.common;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostTextTest {
    @Test
    void formatsStorageBytesWithBinaryCarry() {
        assertEquals("1023", HostText.compactStorageBytes(BigInteger.valueOf(1023)));
        assertEquals("1K", HostText.compactStorageBytes(BigInteger.valueOf(1024)));
        assertEquals("1023.999K", HostText.compactStorageBytes(BigInteger.valueOf(1024L * 1024L - 1L)));
        assertEquals("1M", HostText.compactStorageBytes(BigInteger.valueOf(1024L * 1024L)));
    }

    @Test
    void usesTheSameFormatForNormalStorageProgress() {
        HostText.UsedTotal progress = HostText.byteProgress(1024L, 1024L * 1024L);

        assertEquals("1K", progress.usedText());
        assertEquals("1M", progress.maxText());
    }
}
