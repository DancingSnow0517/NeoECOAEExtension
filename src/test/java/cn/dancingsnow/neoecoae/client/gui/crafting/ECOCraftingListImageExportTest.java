package cn.dancingsnow.neoecoae.client.gui.crafting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ECOCraftingListImageExportTest {
    @Test
    void exactStripBoundaryKeepsTheLastPartialRowWithoutRepeatingTheHeader() {
        assertEquals(new ECOCraftingListImageExport.Layout(100,
                List.of(new ECOCraftingListImageExport.Strip(0, 2, 0, 100))),
            ECOCraftingListImageExport.layout(6, 45, 100));
        assertEquals(new ECOCraftingListImageExport.Layout(123,
                List.of(new ECOCraftingListImageExport.Strip(0, 2, 0, 91),
                    new ECOCraftingListImageExport.Strip(2, 3, 91, 32))),
            ECOCraftingListImageExport.layout(7, 45, 100));
    }

    @Test
    void largeReportFitsOneImageBeyondTheGpuLimitAndContainsEveryMaterialOnce() {
        // The reported export had 2,728 materials split across 53 separate PNGs.
        var layout = ECOCraftingListImageExport.layout(2728, 89, 1024);
        assertEquals(21028, layout.height());
        int nextRow = 0;
        int nextY = 0;
        int materials = 0;
        for (var strip : layout.strips()) {
            assertEquals(nextRow, strip.firstRow());
            assertEquals(nextY, strip.y());
            assertTrue(strip.endRow() > strip.firstRow());
            assertTrue(strip.height() <= 1024);
            materials += Math.min(2728, strip.endRow() * 3) - strip.firstRow() * 3;
            nextRow = strip.endRow();
            nextY += strip.height();
        }
        assertEquals(910, nextRow);
        assertEquals(2728, materials);
        assertEquals(layout.height(), nextY);
    }
}
