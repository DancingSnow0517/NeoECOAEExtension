package cn.dancingsnow.neoecoae.core.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class PanelContentTest {
    record Cell(boolean infinite, long usedBytes, long totalBytes) implements StorageCellView {}
    @Test void byteTotalsAndRatiosRemainExactBeyondLongCapacity() {
        var cells = List.of(new Cell(false, Long.MAX_VALUE / 2, Long.MAX_VALUE - 1),
            new Cell(false, Long.MAX_VALUE / 2, Long.MAX_VALUE - 1));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE - 1).multiply(BigInteger.TWO),
            StoragePanelState.totalCapacity(cells));
        assertEquals(0.5, StoragePanelState.usage(cells), 0.00001);
        assertEquals(1, StoragePanelState.usage(List.of(new Cell(true, 0, 0))));
    }
    @Test void pagingPreservesWholeThreeRowColumnsAndFillsGaps() {
        assertEquals(List.of(0, 1, 3, 4, 6, -1), StoragePanelState.cellPage(List.of(0, 1, 2, 3, 4, 5, 6), 0, 6, -1));
        assertEquals(6, StoragePanelState.pageOffset(99, 7, 6));
        assertEquals("N/A", HostTextContent.percent(Double.NaN));
        assertEquals(HostTextContent.UsageLevel.HIGH, HostTextContent.usageLevel(9, 10));
        assertEquals(HostTextContent.UsageLevel.WARNING, HostTextContent.usageLevel(3, 4));
    }
}
