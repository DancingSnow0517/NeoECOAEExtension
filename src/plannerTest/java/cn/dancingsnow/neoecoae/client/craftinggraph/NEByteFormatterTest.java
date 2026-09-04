package cn.dancingsnow.neoecoae.client.craftinggraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.dancingsnow.neoecoae.util.NEByteFormatter;
import org.junit.jupiter.api.Test;

class NEByteFormatterTest {
    @Test
    void formatsLargeCpuStorageWithoutCallingAe2Formatter() {
        assertEquals("1B", NEByteFormatter.format(1L));
        assertEquals("1KiB", NEByteFormatter.format(1L << 10));
        assertEquals("1MiB", NEByteFormatter.format(1L << 20));
        assertEquals("1GiB", NEByteFormatter.formatCpuStorage(1L << 30));
        assertEquals("1TiB", NEByteFormatter.formatCpuStorage(1L << 40));
        assertEquals("1PiB", NEByteFormatter.formatCpuStorage(1L << 50));
        assertEquals("8EiB", NEByteFormatter.formatCpuStorage(Long.MAX_VALUE));
    }

    @Test
    void preservesAe2CpuDisplayForOrdinaryStorage() {
        assertEquals("0k", NEByteFormatter.formatCpuStorage(512L));
        assertEquals("1M", NEByteFormatter.formatCpuStorage(1L << 20));
        assertEquals("1023M", NEByteFormatter.formatCpuStorage((1L << 30) - 1));
    }
}
