package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.BitSet;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatternPreviewRowsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void searchRetainsPhysicalPositionsAndDimsNonMatchingNeighbors() {
        var entries = new PatternPreviewEntry[18];
        for (int i = 0; i < entries.length; i++) entries[i] = entry(10, i, i == 4 ? "铁锭 iron" : "copper", (byte) 0);
        var rows = new PatternPreviewRows();
        rows.reset(entries);
        rows.filter("铁锭 iron", true, true, true);
        assertEquals(1, rows.size());
        assertEquals(new PatternPreviewRows.Cell(4, -1), rows.cell(4));
        assertEquals(new PatternPreviewRows.Cell(0, -1), rows.cell(0));
        assertTrue(rows.matches(4));
        assertFalse(rows.matches(0));
        entries[4] = entry(10, 4, "gold", (byte) 0);
        BitSet changed = new BitSet();
        changed.set(4);
        rows.changed(changed, false);
        assertEquals(0, rows.size());
    }

    @Test void separateBusesAndDiskRecipesNeverReaddressPhysicalSlots() {
        var disk = new PatternPreviewEntry(10, 1, ItemStack.EMPTY, "", (byte) 0,
                List.of(new PatternPreviewEntry.DiskPattern(new ItemStack(Items.STONE), "iron", (byte) 0)), true);
        var rows = new PatternPreviewRows();
        rows.reset(new PatternPreviewEntry[]{entry(10, 0, "gold", (byte) 0), disk, entry(20, 0, "copper", (byte) 0)});
        assertEquals(3, rows.size());
        assertEquals(new PatternPreviewRows.Cell(1, -1), rows.cell(1));
        assertNull(rows.cell(2));
        assertEquals(new PatternPreviewRows.Cell(1, 0), rows.cell(9));
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(18));
        rows.filter("iron", true, true, true);
        assertEquals(1, rows.size());
        assertEquals(new PatternPreviewRows.Cell(1, 0), rows.cell(0));
    }

    @Test void emptyRowsAndSubstitutionsCanBeFilteredWithoutMovingNeighbors() {
        var rows = new PatternPreviewRows();
        rows.reset(new PatternPreviewEntry[]{
                new PatternPreviewEntry(10, 0, ItemStack.EMPTY, "", (byte) 0, List.of(), false),
                entry(20, 0, "iron", (byte) 1), entry(20, 1, "gold", (byte) 0)});
        rows.filter("", false, true, false);
        assertEquals(1, rows.size());
        assertFalse(rows.matches(0));
        assertTrue(rows.matches(1));
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(1));
    }

    private static PatternPreviewEntry entry(long bus, int slot, String keywords, byte flags) {
        return new PatternPreviewEntry(bus, slot, new ItemStack(Items.STONE), keywords, flags, List.of(), false);
    }
}
