package cn.dancingsnow.neoecoae.client.craftinggraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.dancingsnow.neoecoae.util.NEByteFormatter;
import org.junit.jupiter.api.Test;

class NEByteFormatterTest {
    @Test
    void formatsLargeCpuStorageWithoutCallingAe2Formatter() {
        assertEquals("1B", NEByteFormatter.format(1L));
        assertEquals("1KB", NEByteFormatter.format(1L << 10));
        assertEquals("1MB", NEByteFormatter.format(1L << 20));
        assertEquals("1GB", NEByteFormatter.formatCpuStorage(1L << 30));
        assertEquals("1TB", NEByteFormatter.formatCpuStorage(1L << 40));
        assertEquals("1PB", NEByteFormatter.formatCpuStorage(1L << 50));
        assertEquals("8EB", NEByteFormatter.formatCpuStorage(Long.MAX_VALUE));
    }

    @Test
    void preservesAe2CpuDisplayForOrdinaryStorage() {
        assertEquals("0k", NEByteFormatter.formatCpuStorage(512L));
        assertEquals("1M", NEByteFormatter.formatCpuStorage(1L << 20));
        assertEquals("1023M", NEByteFormatter.formatCpuStorage((1L << 30) - 1));
    }

    @Test
    void abbreviatesLargeCpuCoProcessorCounts() {
        assertEquals("999", NEByteFormatter.formatCpuCoProcessors(999L));
        assertEquals("1k", NEByteFormatter.formatCpuCoProcessors(1_000L));
        assertEquals("2.147G", NEByteFormatter.formatCpuCoProcessors(Integer.MAX_VALUE));
    }
}
